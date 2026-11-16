package top.egon.cola.component.tianshu.admin.service.si;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** SI 资源依赖图 DAG 环检测、目标环境已发布检查及归档前入度阻断校验器。 */
@Slf4j
@Component("ddcSiDependencyValidator")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiDependencyValidator {

    private static final int MAX_GRAPH_DEPTH = 32;
    private static final int CODE_CONFLICT = 56409;
    private static final int CODE_VALIDATION = 56422;

    @Qualifier("ddcSiResourceRepository")
    private final DdcSiResourceRepository repository;

    /**
     * 校验出度依赖无环且均已在目标环境发布就绪。
     */
    public void validateAcyclicAndPublishedTargets(Long tenantId, String bizCode, Long namespaceId,
                                                   String envCode, String appCode,
                                                   DdcSiKindEnum sourceKind, String sourceKey,
                                                   List<DdcSiReferenceBO> outgoingRefs) {
        if (outgoingRefs == null || outgoingRefs.isEmpty()) {
            return;
        }
        if (sourceKind == DdcSiKindEnum.AGENT) {
            validateAgentDag(tenantId, envCode, sourceKey, outgoingRefs);
        }
        for (DdcSiReferenceBO ref : outgoingRefs) {
            DdcSiKindEnum targetKind = resolveTargetKind(ref);
            if (targetKind == null) {
                continue;
            }
            if (targetKind == sourceKind && sourceKey.equals(ref.getTargetKey())) {
                throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_CYCLE",
                        "self-reference dependency cycle on " + sourceKey);
            }
            DdcSiResourceBO target = repository.findByBusinessKey(
                            tenantId, bizCode, namespaceId, envCode, appCode, targetKind, ref.getTargetKey())
                    .orElseThrow(() -> new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_MISSING",
                            "referenced resource not found in target env " + envCode + ": " + targetKind + "/" + ref.getTargetKey()));
            if (target.getLifecycleStatus() != DdcSiLifecycleStatusEnum.PUBLISHED
                    && target.getLifecycleStatus() != DdcSiLifecycleStatusEnum.DEPRECATED) {
                throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_UNPUBLISHED",
                        "referenced resource is not published in env " + envCode + ": " + ref.getTargetKey());
            }
        }
    }

    /**
     * 归档前拦截：若存在活跃上游资源、活跃 {@code TIANJI_PROCESS} 引用或活跃应用绑定，抛出 {@code 409 Conflict}。
     */
    public void ensureSafeToArchive(DdcSiResourceBO resource) {
        List<DdcSiReferenceBO> inbound = repository.findBlockingInboundReferences(
                resource.getTenantId(), resource.getEnvCode(), resource.getKind(), resource.getResourceKey());
        List<DdcSiBindingBO> activeBindings = repository.findBindingsByResourceId(
                        resource.getTenantId(), resource.getId()).stream()
                .filter(b -> b.getStatus() == DdcSiBindingStatusEnum.ACTIVE)
                .toList();

        if (inbound.isEmpty() && activeBindings.isEmpty()) {
            return;
        }
        String blockingRefs = inbound.stream()
                .map(ref -> ref.getSourceType() + ":" + ref.getSourceKey())
                .collect(Collectors.joining(", "));
        String blockingApps = activeBindings.stream()
                .map(DdcSiBindingBO::getAppCode)
                .collect(Collectors.joining(", "));
        throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_ARCHIVE_BLOCKED",
                "cannot archive referenced SI resource; references=[" + blockingRefs + "] bindings=[" + blockingApps + "]");
    }

    private void validateAgentDag(Long tenantId, String envCode, String sourceKey, List<DdcSiReferenceBO> outgoingRefs) {
        Map<String, List<String>> adjacency = new HashMap<>();
        List<DdcSiReferenceBO> existingEdges = repository.findAgentGraphEdges(tenantId, envCode);
        for (DdcSiReferenceBO edge : existingEdges) {
            if (!sourceKey.equals(edge.getSourceKey())) {
                adjacency.computeIfAbsent(edge.getSourceKey(), k -> new ArrayList<>()).add(edge.getTargetKey());
            }
        }
        List<String> pendingAgentTargets = outgoingRefs.stream()
                .filter(ref -> resolveTargetKind(ref) == DdcSiKindEnum.AGENT)
                .map(DdcSiReferenceBO::getTargetKey)
                .toList();
        adjacency.put(sourceKey, pendingAgentTargets);

        // 0 = UNVISITED, 1 = VISITING (gray), 2 = VISITED (black)
        Map<String, Integer> color = new HashMap<>();
        Set<String> trail = new LinkedHashSet<>();
        dfs(sourceKey, adjacency, color, trail, 0);
    }

    private void dfs(String current, Map<String, List<String>> adjacency, Map<String, Integer> color,
                     Set<String> trail, int depth) {
        if (depth > MAX_GRAPH_DEPTH) {
            throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_DEPTH_EXCEEDED",
                    "agent dependency nesting exceeds max depth " + MAX_GRAPH_DEPTH + " at " + current);
        }
        int state = color.getOrDefault(current, 0);
        if (state == 1) {
            throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_CYCLE",
                    "agent dependency cycle detected involving " + String.join(" -> ", trail) + " -> " + current);
        }
        if (state == 2) {
            return;
        }
        color.put(current, 1);
        trail.add(current);
        for (String next : adjacency.getOrDefault(current, List.of())) {
            dfs(next, adjacency, color, trail, depth + 1);
        }
        trail.remove(current);
        color.put(current, 2);
    }

    private static DdcSiKindEnum resolveTargetKind(DdcSiReferenceBO ref) {
        if (ref.getTargetKind() != null) {
            return ref.getTargetKind();
        }
        if (ref.getTargetType() == null || ref.getTargetType() == DdcSiReferenceTargetTypeEnum.TIANJI_PROCESS) {
            return null;
        }
        return switch (ref.getTargetType()) {
            case AGENT -> DdcSiKindEnum.AGENT;
            case MCP -> DdcSiKindEnum.MCP;
            case PROMPT -> DdcSiKindEnum.PROMPT;
            case SKILL -> DdcSiKindEnum.SKILL;
            case TIANJI_PROCESS -> null;
        };
    }
}
