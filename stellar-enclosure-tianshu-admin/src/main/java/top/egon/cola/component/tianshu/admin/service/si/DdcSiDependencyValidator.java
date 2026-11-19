package top.egon.cola.component.tianshu.admin.service.si;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO.ExpectedIdentityVO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Freezes and verifies published dependency material independently of the editable reference graph. */
@Slf4j
@Component("ddcSiDependencyValidator")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiDependencyValidator {

    private static final int MAX_GRAPH_DEPTH = 32;
    private static final int CODE_CONFLICT = 56409;
    private static final int CODE_VALIDATION = 56422;

    @Qualifier("ddcSiResourceRepository")
    private final DdcSiResourceRepository repository;

    private static final int HISTORY_BATCH_SIZE = 100;

    @Qualifier("jacksonObjectMapper")
    private final ObjectMapper json;

    /**
     * Resolve direct references in the target scope, fence their pointers, then freeze the
     * immutable subgraphs they already published. Caller owns the LOCAL transaction.
     */
    public void freezePublication(@NotNull DdcSiResourceBO resource, @NotNull DdcSiVersionBO candidate,
                                   VersionIdentityVO source, @NotNull List<@Valid DdcSiReferenceBO> references,
                                   @NotNull List<DdcSiResourceBO> otherRoots) {
        var participants = new ArrayList<>(otherRoots);
        participants.add(resource);
        var selections = new ArrayList<DependencySelectionVO>();
        for (var reference : references) {
            DdcSiKindEnum kind = resolveTargetKind(reference);
            ExpectedIdentityVO expected = reference.getExpectedIdentity();
            if (kind == null) {
                if (expected != null) {
                    throw identityMismatch();
                }
                continue; // Tianji process references are governed outside the SI version graph.
            }
            boolean directSelf = expected != null ? Objects.equals(resource.getId(), expected.resourceId())
                    : kind == resource.getKind() && resource.getResourceKey().equals(reference.getTargetKey());
            if (directSelf) {
                throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_CYCLE",
                        "SI publication cannot depend on itself");
            }

            String targetOwner = resource.getAppCode();
            if (expected != null && expected.ownerPresent()) {
                targetOwner = expected.ownerAppCode() == null ? "" : expected.ownerAppCode();
            }
            var target = repository.findByBusinessKey(resource.getTenantId(), resource.getBizCode(),
                    resource.getNamespaceId(), resource.getEnvCode(), targetOwner, kind, reference.getTargetKey())
                    .orElseThrow(() -> new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_MISSING",
                            "SI dependency is missing from the publication scope"));
            if (expected != null) {
                requireExpectedTarget(resource, target, reference, targetOwner, kind, expected);
            }
            if (target.getLifecycleStatus() == null || !target.getLifecycleStatus().isQueryableByRuntime()) {
                throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_UNPUBLISHED",
                        "SI dependency is not published in the publication scope");
            }
            participants.add(target);
            selections.add(new DependencySelectionVO(target.getId(), target.getCurrentVersionId(),
                    expected == null ? reference.getPinnedVersionNo() : expected.versionNo(), kind, expected));
        }
        lockCurrentPointers(resource.getTenantId(), participants);
        var nodes = new HashMap<String, VersionIdentityVO>();
        var edges = new HashSet<EdgeVO>();
        var activeIds = selections.stream().filter(selection -> selection.pinnedVersionNo() == null)
                .map(DependencySelectionVO::activeVersionId).toList();
        if (activeIds.contains(null)) {
            throw unavailable();
        }
        var activeVersions = loadVersions(resource.getTenantId(), activeIds);
        var selectedManifests = new ArrayList<DdcSiDependencyManifestVO>();
        for (var selection : selections) {
            DdcSiVersionBO child = selection.pinnedVersionNo() == null
                    ? activeVersions.get(selection.activeVersionId())
                    : repository.findHistoryVersionByResourceAndNo(resource.getTenantId(), selection.resourceId(),
                            selection.pinnedVersionNo()).orElseThrow(DdcSiDependencyValidator::unavailable);
            var manifest = readManifest(child);
            if (!Long.toString(selection.resourceId()).equals(manifest.root().resourceId())
                    || selection.kind() != manifest.root().kind()
                    || selection.pinnedVersionNo() != null
                    && !Long.toString(selection.pinnedVersionNo()).equals(manifest.root().versionNo())
                    || !resource.getEnvCode().equals(manifest.root().envCode())) {
                throw unavailable();
            }
            ExpectedIdentityVO expected = selection.expectedIdentity();
            if (expected != null && (!Long.toString(resource.getTenantId()).equals(manifest.root().tenantId())
                    || !Long.toString(expected.resourceId()).equals(manifest.root().resourceId())
                    || !Long.toString(expected.versionId()).equals(manifest.root().versionId())
                    || !Long.toString(expected.versionNo()).equals(manifest.root().versionNo())
                    || !expected.digestSha256().equals(manifest.root().digestSha256()))) {
                throw identityMismatch();
            }
            selectedManifests.add(manifest);
        }
        // No new version identity is reserved until all approved direct tuples have been verified.
        allocateIdentity(candidate);
        VersionIdentityVO root = identity(candidate);
        for (var manifest : selectedManifests) {
            addNode(nodes, manifest.root());
            manifest.nodes().forEach(node -> addNode(nodes, node));
            edges.add(new EdgeVO(root.versionId(), manifest.root().versionId()));
            edges.addAll(manifest.edges());
        }
        if (nodes.values().stream().anyMatch(node -> root.resourceId().equals(node.resourceId()))) {
            throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_CYCLE",
                    "SI publication depends on an earlier version of the same resource");
        }
        DdcSiDependencyManifestVO manifest = create(root, source, List.copyOf(nodes.values()), List.copyOf(edges));
        verifyMaterial(candidate.getTenantId(), manifest);
        candidate.setDependencyManifestJson(json.valueToTree(manifest));
    }

    public void lockCurrentPointers(@NotNull Long tenantId, @NotNull List<DdcSiResourceBO> participants) {
        var expected = new TreeMap<Long, DdcSiResourceBO>();
        for (var resource : participants) {
            var previous = expected.putIfAbsent(resource.getId(), resource);
            if (previous != null && !samePointer(previous, resource)) {
                throw publicationChanged();
            }
        }
        for (var snapshot : expected.values()) {
            var locked = repository.findActiveForUpdateById(tenantId, snapshot.getId())
                    .orElseThrow(DdcSiDependencyValidator::publicationChanged);
            if (!Objects.equals(tenantId, snapshot.getTenantId()) || !samePointer(snapshot, locked)) {
                throw publicationChanged();
            }
        }
    }

    /** The caller has fenced the resource. Re-root the selected historical graph in a fresh version. */
    public void freezeRollback(@NotNull DdcSiVersionBO candidate, @NotNull DdcSiDependencyManifestVO previous) {
        allocateIdentity(candidate);
        VersionIdentityVO root = identity(candidate);
        List<EdgeVO> edges = previous.edges().stream()
                .map(edge -> edge.fromVersionId().equals(previous.root().versionId())
                        ? new EdgeVO(root.versionId(), edge.toVersionId()) : edge)
                .toList();
        candidate.setDependencyManifestJson(json.valueToTree(create(root, previous.root(), previous.nodes(), edges)));
    }

    public DdcSiDependencyManifestVO requireHistorical(@NotNull DdcSiVersionBO version) {
        var manifest = readManifest(version);
        verifyMaterial(version.getTenantId(), manifest);
        return manifest;
    }

    public static CommonException unavailable() {
        return new CommonException(CODE_CONFLICT, "SI_PINNED_HISTORY_UNAVAILABLE",
                "Exact SI execution history is unavailable");
    }

    private void allocateIdentity(DdcSiVersionBO candidate) {
        candidate.setVersionNo(repository.allocateNextVersionNo(candidate.getTenantId(), candidate.getResourceId()))
                .setId(repository.allocateVersionId());
    }

    private DdcSiDependencyManifestVO readManifest(DdcSiVersionBO version) {
        if (version == null || version.getDependencyManifestJson() == null) {
            throw unavailable();
        }
        try {
            var manifest = json.treeToValue(version.getDependencyManifestJson(), DdcSiDependencyManifestVO.class);
            if (manifest == null || !manifest.root().equals(identity(version))) {
                throw unavailable();
            }
            return manifest;
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw unavailable();
        }
    }

    private static VersionIdentityVO identity(DdcSiVersionBO version) {
        if (version.getSpecJson() == null
                || !DdcSiContentStrategy.sha256Hex(version.getSpecJson()).equals(version.getDigestSha256())) {
            throw unavailable();
        }
        try {
            return new VersionIdentityVO(String.valueOf(version.getTenantId()), String.valueOf(version.getResourceId()),
                    String.valueOf(version.getId()), String.valueOf(version.getVersionNo()), version.getKind(),
                    version.getEnvCode(), version.getDigestSha256());
        } catch (IllegalArgumentException invalid) {
            throw unavailable();
        }
    }

    private void verifyMaterial(Long tenantId, DdcSiDependencyManifestVO manifest) {
        var expected = new HashMap<String, VersionIdentityVO>();
        manifest.nodes().forEach(node -> addNode(expected, node));
        if (manifest.source() != null) {
            addNode(expected, manifest.source());
        }
        var stored = loadVersions(tenantId, expected.keySet().stream().map(Long::valueOf).toList());
        var historical = new HashMap<String, DdcSiDependencyManifestVO>();
        for (var node : expected.values()) {
            var child = readManifest(stored.get(Long.valueOf(node.versionId())));
            if (!node.equals(child.root())) {
                throw unavailable();
            }
            historical.put(node.versionId(), child);
        }

        // Reconcile each node's outgoing edges with its own immutable publication.
        // A well-formed hash alone would not detect a replaced or omitted historical edge.
        var outgoing = outgoingEdges(manifest.edges());
        for (var node : manifest.nodes()) {
            var child = historical.get(node.versionId());
            var edges = child.edges().stream().filter(edge -> edge.fromVersionId().equals(node.versionId()))
                    .collect(Collectors.toSet());
            if (!edges.equals(outgoing.getOrDefault(node.versionId(), Set.of()))) {
                throw unavailable();
            }
        }
        checkDepth(manifest, outgoing);
    }

    private Map<Long, DdcSiVersionBO> loadVersions(Long tenantId, List<Long> requestedIds) {
        var ids = requestedIds.stream().distinct().sorted().toList();
        var found = new HashMap<Long, DdcSiVersionBO>();
        for (int start = 0; start < ids.size(); start += HISTORY_BATCH_SIZE) {
            var batch = ids.subList(start, Math.min(start + HISTORY_BATCH_SIZE, ids.size()));
            for (var row : repository.findHistoryVersionsByIds(tenantId, batch)) {
                if (!Objects.equals(tenantId, row.getTenantId()) || !batch.contains(row.getId())
                        || found.putIfAbsent(row.getId(), row) != null) {
                    throw unavailable();
                }
            }
        }
        if (found.size() != ids.size()) {
            throw unavailable();
        }
        return found;
    }

    private static Map<String, Set<EdgeVO>> outgoingEdges(List<EdgeVO> edges) {
        return edges.stream().collect(Collectors.groupingBy(EdgeVO::fromVersionId, Collectors.toSet()));
    }

    private static void checkDepth(DdcSiDependencyManifestVO manifest, Map<String, Set<EdgeVO>> outgoing) {
        var depth = new HashMap<String, Integer>();
        var pending = new ArrayDeque<String>();
        depth.put(manifest.root().versionId(), 0);
        pending.add(manifest.root().versionId());
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            int nextDepth = depth.get(id) + 1;
            for (var edge : outgoing.getOrDefault(id, Set.of())) {
                if (nextDepth > MAX_GRAPH_DEPTH) {
                    throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_DEPTH_EXCEEDED",
                            "SI publication dependency graph exceeds " + MAX_GRAPH_DEPTH + " levels");
                }
                if (nextDepth > depth.getOrDefault(edge.toVersionId(), -1)) {
                    depth.put(edge.toVersionId(), nextDepth);
                    pending.add(edge.toVersionId());
                }
            }
        }
    }

    private static DdcSiDependencyManifestVO create(VersionIdentityVO root, VersionIdentityVO source,
                                                     List<VersionIdentityVO> nodes, List<EdgeVO> edges) {
        try {
            return DdcSiDependencyManifestVO.create(root, source, nodes, edges);
        } catch (IllegalArgumentException invalid) {
            throw unavailable();
        }
    }

    private static void addNode(Map<String, VersionIdentityVO> nodes, VersionIdentityVO node) {
        var previous = nodes.putIfAbsent(node.versionId(), node);
        if (previous != null && !previous.equals(node)) {
            throw unavailable();
        }
    }

    private static boolean samePointer(DdcSiResourceBO expected, DdcSiResourceBO current) {
        return Objects.equals(expected.getTenantId(), current.getTenantId())
                && Objects.equals(expected.getId(), current.getId())
                && Objects.equals(expected.getVersion(), current.getVersion())
                && Objects.equals(expected.getCurrentVersionId(), current.getCurrentVersionId())
                && expected.getLifecycleStatus() == current.getLifecycleStatus();
    }

    private static CommonException publicationChanged() {
        return new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT",
                "SI publication input changed before its version could be frozen");
    }

    private record DependencySelectionVO(Long resourceId, Long activeVersionId, Long pinnedVersionNo,
                                         DdcSiKindEnum kind, ExpectedIdentityVO expectedIdentity) { }

    private void requireExpectedTarget(DdcSiResourceBO source, DdcSiResourceBO target, DdcSiReferenceBO reference,
                                       String targetOwner, DdcSiKindEnum kind, ExpectedIdentityVO expected) {
        if (!Objects.equals(source.getTenantId(), target.getTenantId())
                || !Objects.equals(source.getBizCode(), target.getBizCode())
                || !Objects.equals(source.getNamespaceId(), target.getNamespaceId())
                || !Objects.equals(source.getEnvCode(), target.getEnvCode())
                || !Objects.equals(targetOwner, target.getAppCode())
                || target.getKind() != kind || !Objects.equals(reference.getTargetKey(), target.getResourceKey())
                || !Objects.equals(expected.resourceId(), target.getId()) || target.getDeletedAt() != null
                || (reference.getPinnedVersionNo() != null && !expected.versionNo().equals(reference.getPinnedVersionNo()))
                || (reference.getTargetVersionNo() != null && !expected.versionNo().equals(reference.getTargetVersionNo()))) {
            throw identityMismatch();
        }
        if (Objects.equals(source.getAppCode(), targetOwner)) {
            return;
        }
        // A business-owned source cannot invent an application caller for a cross-owner grant.
        if (source.getAppCode() == null || source.getAppCode().isBlank()) {
            throw dependencyScopeDenied();
        }
        var binding = repository.findActiveBinding(source.getTenantId(), source.getBizCode(), source.getNamespaceId(),
                source.getEnvCode(), source.getAppCode(), kind, reference.getTargetKey())
                .orElseThrow(DdcSiDependencyValidator::dependencyScopeDenied);
        if (binding.getStatus() != DdcSiBindingStatusEnum.ACTIVE || binding.getDeletedAt() != null
                || !Objects.equals(source.getTenantId(), binding.getTenantId())
                || !Objects.equals(source.getBizCode(), binding.getBizCode())
                || !Objects.equals(source.getNamespaceId(), binding.getNamespaceId())
                || !Objects.equals(source.getEnvCode(), binding.getEnvCode())
                || !Objects.equals(source.getAppCode(), binding.getAppCode())
                || binding.getKind() != kind || !Objects.equals(reference.getTargetKey(), binding.getResourceKey())
                || !Objects.equals(target.getId(), binding.getResourceId())
                || (binding.getPinnedVersionNo() != null && !expected.versionNo().equals(binding.getPinnedVersionNo()))) {
            throw dependencyScopeDenied();
        }
    }

    private static CommonException identityMismatch() {
        return new CommonException(CODE_VALIDATION, "TIANSHU_SI_DEPENDENCY_IDENTITY_MISMATCH",
                "SI dependency does not match its approved immutable identity");
    }

    private static CommonException dependencyScopeDenied() {
        return new CommonException(56403, "TIANSHU_SI_SCOPE_DENIED", "SI dependency owner is not authorized for the source application");
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
