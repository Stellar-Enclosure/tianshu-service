package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.WorkloadViewVO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigProcessReceiptBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskResultConverter;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishTaskQueryRequest;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishTaskResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishTaskResult.ProcessResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishTaskResult.TargetResult;
import top.egon.cola.component.tianshu.admin.repository.*;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository.ObservedIncarnationVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.IncarnationVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.TargetQueryVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Read-only projection. Neither legacy ACKs, delivery success nor Pod Ready establishes process application. */
@Service
public class DdcPublishTaskQueryService {
    private static final int BATCH = 500;
    private static final int MAX_CURRENT = 10_000;
    private final DdcPublishTaskRepository tasks;
    private final DdcConfigItemRepository items;
    private final DdcConfigVersionRepository versions;
    private final DdcConfigProcessReceiptRepository receipts;
    private final DdcConfigBootBindingRepository boots;
    private final DdcPublishTaskResultConverter converter;
    private final DdcConfigManifestFactory manifests;
    private final DdcTenantRegistry tenants;
    private final ObjectProvider<WorkloadObservationReader> observations;
    private final Clock clock;
    private final ObjectMapper json;

    /** Source-compatible archive-only construction; it cannot establish native current success. */
    public DdcPublishTaskQueryService(DdcPublishTaskRepository tasks) {
        this(tasks, null, null, null, null, Mappers.getMapper(DdcPublishTaskResultConverter.class), null, null,
                new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(WorkloadObservationReader.class),
                Clock.systemUTC(), new ObjectMapper().findAndRegisterModules());
    }

    @Autowired
    public DdcPublishTaskQueryService(
            @Qualifier("ddcPublishTaskRepository") DdcPublishTaskRepository tasks,
            @Qualifier("ddcConfigItemRepository") DdcConfigItemRepository items,
            @Qualifier("ddcConfigVersionRepository") DdcConfigVersionRepository versions,
            @Qualifier("ddcConfigProcessReceiptRepository") DdcConfigProcessReceiptRepository receipts,
            @Qualifier("ddcConfigBootBindingRepository") DdcConfigBootBindingRepository boots,
            @Qualifier("ddcPublishTaskResultConverterImpl") DdcPublishTaskResultConverter converter,
            @Qualifier("ddcConfigManifestFactory") DdcConfigManifestFactory manifests,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants,
            @Qualifier("ddcWorkloadObservationReader") ObjectProvider<WorkloadObservationReader> observations,
            @Qualifier("ddcPublishClock") Clock clock,
            @Qualifier("jacksonObjectMapper") ObjectMapper json) {
        this.tasks = Objects.requireNonNull(tasks); this.items = items; this.versions = versions;
        this.receipts = receipts; this.boots = boots; this.converter = Objects.requireNonNull(converter);
        this.manifests = manifests; this.tenants = tenants; this.observations = Objects.requireNonNull(observations);
        this.clock = Objects.requireNonNull(clock); this.json = Objects.requireNonNull(json);
    }

    @FunctionalInterface
    public interface WorkloadObservationReader {
        WorkloadViewVO currentView(TargetBindingVO target);
    }

    /** Original internal page contract is retained. */
    public DdcPageResult<DdcPublishTaskBO> page(DdcPublishTaskQueryRequest request, PageQuery pageQuery) {
        var query = request == null ? new DdcPublishTaskQueryRequest() : request;
        return tasks.search(optional(query.getBizCode()), optional(query.getEnv()), optional(query.getAppCode()),
                optional(query.getStatus()), optional(query.getChangeId()), pageQuery);
    }

    public List<DdcPublishTaskResult> listResults() {
        var context = trustedContext();
        return tasks.findAll().stream().map(task -> project(task, context)).toList();
    }

    public DdcPageResult<DdcPublishTaskResult> pageResults(DdcPublishTaskQueryRequest request, PageQuery pageQuery) {
        var context = trustedContext(); var page = page(request, pageQuery);
        return DdcPageResult.<DdcPublishTaskResult>builder().records(page.getRecords().stream().map(task -> project(task, context)).toList())
                .total(page.getTotal()).pageNo(page.getPageNo()).pageSize(page.getPageSize()).build();
    }

    public Optional<DdcPublishTaskResult> detailResult(String changeId) {
        var context = trustedContext();
        return tasks.findByChangeId(changeId).map(task -> project(task, context));
    }

    private DdcTenantContextDTO trustedContext() {
        if (tenants == null) return null;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Rbac3UserDetails user)) return null;
        var context = tenants.resolveUserContext(user.tenantId(), user.identitySub());
        if (!Long.toString(context.tenantId()).equals(MDC.get("tenantId")) || !context.actor().equals(MDC.get("userId"))) return null;
        return context;
    }

    private DdcPublishTaskResult project(DdcPublishTaskBO task, DdcTenantContextDTO context) {
        var result = converter.toTarget(task).setHistoryOnly(task.getPlatformEvidence() == null)
                .setPlatformState("UNKNOWN").setRolloutState("UNKNOWN").setProcessState("UNKNOWN").setTargets(List.of());
        if (task.getTargetVersion() != null) result.setDesiredRevision(task.getTargetVersion().toString());
        if (task.getPlatformEvidence() == null) return result;
        try {
            if (context == null || !Objects.equals(context.tenantId(), task.getTenantId()) || items == null || manifests == null
                    || task.getConfigItemId() == null || task.getTargetVersion() == null || task.getTargetVersion() <= 0) throw invalid();
            var item = items.findActiveById(task.getConfigItemId()).orElseThrow(DdcPublishTaskQueryService::invalid);
            if (!Objects.equals(item.getTenantId(), context.tenantId()) || !Objects.equals(item.getPublicId(), task.getConfigId())
                    || !Objects.equals(item.getBizCode(), task.getBizCode()) || !Objects.equals(item.getEnv(), task.getEnv())
                    || !Objects.equals(item.getAppCode(), task.getAppCode()) || !Objects.equals(item.getResourceName(), task.getResourceName())) throw invalid();
            if (!Objects.equals(item.getPublishedVersion(), task.getTargetVersion())) {
                result.setDesiredRevision(item.getPublishedVersion() == null ? null : item.getPublishedVersion().toString());
                return result.setProcessState("SUPERSEDED").setRolloutState("SUPERSEDED").setReasonCode("CONFIG_PUBLISH_SUPERSEDED");
            }
            var version = versions.findByConfigItemIdAndRevision(task.getConfigItemId(), task.getTargetVersion()).orElseThrow(DdcPublishTaskQueryService::invalid);
            if (!Objects.equals(version.getTenantId(), context.tenantId()) || !Objects.equals(version.getConfigItemId(), task.getConfigItemId())
                    || !Objects.equals(version.getConfigRevision(), task.getTargetVersion())) throw invalid();
            var scope = new DdcConfigScopeVO(context.tenantId().toString(), task.getBizCode(), task.getEnv(), task.getAppCode(), task.getResourceName());
            var baseline = manifests.materialize(version, scope).baseline();
            if (!baseline.getSource().getConfigId().equals(task.getConfigItemId().toString())
                    || !baseline.getSource().getRevision().equals(task.getTargetVersion().toString())
                    || baseline.getTargetBindings().isEmpty() || baseline.getTargetBindings().size() > 32) throw invalid();
            var evidence = evidence(task.getPlatformEvidence(), baseline);
            var reader = observations.getIfAvailable();
            var projections = new ArrayList<TargetProjection>();
            for (var target : baseline.getTargetBindings()) projections.add(target(context, baseline, target, evidence.get(target.getBindingId()), reader));
            var targets = projections.stream().map(TargetProjection::view).toList();
            result.setTargets(List.copyOf(targets));
            result.setPlatformState(combine(targets.stream().map(TargetResult::getPlatformState).toList()));
            result.setRolloutState(combine(targets.stream().map(TargetResult::getRolloutState).toList()));
            result.setProcessState(combine(targets.stream().map(TargetResult::getProcessState).toList()));
            if (targets.stream().allMatch(target -> target.getObservedAt() != null)) result.setObservedAt(targets.stream().map(TargetResult::getObservedAt).min(Instant::compareTo).orElse(null));
            result.setObservationValidUntil(projections.stream().allMatch(p -> p.validUntil() != null)
                    ? projections.stream().map(TargetProjection::validUntil).min(Instant::compareTo).orElse(null) : null);
            result.setReasonCode(targets.stream().map(TargetResult::getReasonCode).filter(Objects::nonNull).findFirst().orElse(null));
            // A publish may move while read-only evidence is gathered; never return old-target success after observing that move.
            var current = items.findActiveById(task.getConfigItemId()).orElseThrow(DdcPublishTaskQueryService::invalid);
            if (!Objects.equals(current.getPublishedVersion(), task.getTargetVersion())) {
                result.setDesiredRevision(current.getPublishedVersion() == null ? null : current.getPublishedVersion().toString());
                result.setProcessState("SUPERSEDED").setRolloutState("SUPERSEDED").setReasonCode("CONFIG_PUBLISH_SUPERSEDED").setTargets(List.of());
            }
            return result;
        } catch (RuntimeException unavailable) {
            return result.setPlatformState("UNKNOWN").setRolloutState("UNKNOWN").setProcessState("UNKNOWN")
                    .setTargets(List.of()).setObservedAt(null).setObservationValidUntil(null).setReasonCode("CONFIG_CURRENT_EVIDENCE_UNAVAILABLE");
        }
    }

    private record TargetProjection(TargetResult view, Instant validUntil) { }

    private TargetProjection target(DdcTenantContextDTO context, DdcConfigBaselineVO baseline, TargetBindingVO target,
            JsonNode evidence, WorkloadObservationReader reader) {
        var result = TargetResult.builder().targetBindingId(target.getBindingId()).targetRevision(target.getBindingRevision())
                .clusterId(target.getClusterId()).k8sNamespace(target.getKubernetesNamespace()).workloadUid(target.getWorkloadUid())
                .desiredRevision(baseline.getSource().getRevision()).platformState("APPLIED".equals(evidence.path("status").asText()) ? "APPLIED"
                        : "FAILED".equals(evidence.path("status").asText()) ? "PARTIAL_FAILURE" : "PENDING")
                .rolloutState("UNKNOWN").processState("UNKNOWN").reasonCode(safeCode(evidence.path("errorCode").textValue())).currentProcesses(List.of()).build();
        try {
            if (reader == null) throw invalid();
            var view = Objects.requireNonNull(reader.currentView(target));
            var now = clock.instant();
            if (!target.equals(view.target()) || !target.getWorkloadUid().equals(view.workloadUid()) || !view.fresh()
                    || view.status() == null || !"FRESH".equals(view.status().name()) || view.observedAt() == null || view.validUntil() == null
                    || view.observedAt().isAfter(now) || !view.validUntil().isAfter(now) || view.validUntil().isBefore(view.observedAt())
                    || view.invalidatedAt() != null && !view.invalidatedAt().isBefore(view.observedAt())
                    || view.desiredReplicas() < 0 || view.workloadGeneration() <= 0 || view.observedGeneration() < 0
                    || view.currentPods() == null || view.currentPods().size() > MAX_CURRENT || view.currentIncarnations() == null) throw invalid();
            result.setObservedAt(view.observedAt()).setWorkloadGeneration(Long.toString(view.workloadGeneration()))
                    .setObservedGeneration(Long.toString(view.observedGeneration())).setDesiredReplicas(view.desiredReplicas())
                    .setCurrentCount(view.currentPods().size());
            long appliedGeneration = positive(evidence, "workloadGeneration");
            boolean rollout = "APPLIED".equals(result.getPlatformState()) && view.workloadGeneration() >= appliedGeneration
                    && view.observedGeneration() >= view.workloadGeneration()
                    && evidence.path("configMapName").asText().equals(view.configMapReferences().get("tianshu-baseline"))
                    && baseline.getRestartDigest().equals(view.templateAnnotations().get("stellar.io/config-restart-digest"))
                    && view.currentPods().size() == view.desiredReplicas() && view.currentPods().stream().allMatch(p -> p.ready());
            result.setRolloutState(rollout ? "APPLIED" : "PENDING");
            var current = new LinkedHashMap<ObservedIncarnationVO, ProcessResult>();
            var podKeys = new HashSet<String>(); var processes = new ArrayList<ProcessResult>();
            for (var pod : view.currentPods()) {
                if (pod == null || !target.getContainerName().equals(pod.containerName()) || !ascii(pod.podUid(), 64)
                        || !podKeys.add(pod.podUid() + ":" + pod.containerName()) || pod.restartCount() < 0
                        || pod.phase() == null || !Set.of("Pending", "Running", "Unknown").contains(pod.phase())
                        || pod.containerId() != null && !ascii(pod.containerId(), 256)) throw invalid();
                var process = ProcessResult.builder().podUid(pod.podUid()).podName(pod.podName()).containerName(pod.containerName())
                        .containerId(pod.containerId()).ready(pod.ready()).restartCount(pod.restartCount()).startedAt(pod.startedAt()).status("PENDING").build();
                processes.add(process);
                if (pod.containerId() != null && pod.startedAt() != null && "Running".equals(pod.phase())) {
                    current.put(new ObservedIncarnationVO(pod.podUid(), pod.containerName(), pod.containerId()), process);
                }
            }
            var declared = new HashSet<ObservedIncarnationVO>();
            for (var incarnation : view.currentIncarnations()) {
                var physical = new ObservedIncarnationVO(incarnation.podUid(), incarnation.containerName(), incarnation.containerId());
                if (!declared.add(physical)) throw invalid();
            }
            if (!declared.equals(current.keySet())) throw invalid();
            var actual = new ArrayList<>(current.keySet());
            var bound = new HashMap<IncarnationVO, ProcessResult>();
            for (int start = 0; start < actual.size(); start += BATCH) {
                var chunk = actual.subList(start, Math.min(start + BATCH, actual.size()));
                var seen = new HashSet<ObservedIncarnationVO>();
                for (var boot : boots.findCurrentBindings(context, target.getClusterId(), target.getKubernetesNamespace(), target.getWorkloadUid(), chunk)) {
                    var physical = physical(boot);
                    if (!chunk.contains(physical) || !seen.add(physical) || !target.getClusterId().equals(boot.getClusterId())
                            || !target.getKubernetesNamespace().equals(boot.getK8sNamespace()) || !target.getWorkloadUid().equals(boot.getWorkloadUid())
                            || boot.getId() == null || boot.getId() <= 0 || boot.getBootId() == null || !boot.getBootId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw invalid();
                    var process = current.get(physical).setBootId(boot.getBootId());
                    bound.put(new IncarnationVO(boot.getPodUid(), boot.getContainerName(), boot.getContainerId(), boot.getBootId()), process);
                }
            }
            var query = new TargetQueryVO(Long.valueOf(baseline.getSource().getConfigId()), Long.valueOf(baseline.getSource().getRevision()),
                    target.getBindingId(), Long.valueOf(target.getBindingRevision()), target.getClusterId(), target.getKubernetesNamespace(), target.getWorkloadUid());
            var incarnations = new ArrayList<>(bound.keySet());
            for (int start = 0; start < incarnations.size(); start += BATCH) {
                var chunk = incarnations.subList(start, Math.min(start + BATCH, incarnations.size())); var seen = new HashSet<IncarnationVO>();
                for (var receipt : receipts.readCurrentByTarget(context, query, chunk, BATCH)) {
                    var identity = new IncarnationVO(receipt.getPodUid(), receipt.getContainerName(), receipt.getContainerId(), receipt.getBootId());
                    if (!chunk.contains(identity) || !seen.add(identity) || !validReceipt(receipt, context, baseline, target, now)) throw invalid();
                    var process = bound.get(identity);
                    process.setAppliedRevision(receipt.getAppliedRevision() == null ? null : receipt.getAppliedRevision().toString())
                            .setReportSeq(receipt.getReportSeq().toString()).setStatus(receipt.getResult()).setErrorCode(safeCode(receipt.getReasonCode()))
                            .setLastReportedAt(receipt.getLastReceivedAt());
                }
            }
            int applied = (int) processes.stream().filter(p -> "APPLIED".equals(p.getStatus()) || "DISABLED_APPLIED".equals(p.getStatus())).count();
            int failed = (int) processes.stream().filter(p -> Set.of("REJECTED", "RESTART_REQUIRED", "FAILED").contains(p.getStatus())).count();
            int denominator = Math.max(processes.size(), view.desiredReplicas());
            String state = processes.isEmpty() && view.desiredReplicas() == 0 && "APPLIED".equals(result.getPlatformState())
                    ? "PUBLISHED_WAITING_FOR_CONSUMERS" : failed > 0 ? "PARTIAL_FAILURE"
                    : applied > 0 && applied == denominator ? "APPLIED" : "PENDING";
            if (!view.validUntil().isAfter(clock.instant())) throw invalid();
            result.setAppliedCount(applied).setFailedCount(failed).setWaitingCount(denominator - applied - failed)
                    .setCurrentProcesses(List.copyOf(processes)).setProcessState(state);
            return new TargetProjection(result, view.validUntil());
        } catch (RuntimeException unavailable) {
            result.setRolloutState("UNKNOWN").setProcessState("UNKNOWN").setCurrentProcesses(List.of())
                    .setReasonCode("CONFIG_OBSERVATION_UNAVAILABLE");
            return new TargetProjection(result, null);
        }
    }

    private boolean validReceipt(DdcConfigProcessReceiptBO row, DdcTenantContextDTO context, DdcConfigBaselineVO baseline,
            TargetBindingVO target, Instant now) {
        if (!Objects.equals(row.getTenantId(), context.tenantId()) || !Objects.equals(row.getConfigId(), Long.valueOf(baseline.getSource().getConfigId()))
                || !Objects.equals(row.getConfigRevision(), Long.valueOf(baseline.getSource().getRevision()))
                || !target.getBindingId().equals(row.getTargetBindingId()) || !Objects.equals(Long.valueOf(target.getBindingRevision()), row.getTargetRevision())
                || !target.getClusterId().equals(row.getClusterId()) || !target.getKubernetesNamespace().equals(row.getK8sNamespace())
                || !target.getWorkloadUid().equals(row.getWorkloadUid()) || !json.valueToTree(baseline.getScope()).equals(row.getScopeJson())
                || !baseline.getContentSha256().equals("sha256:" + row.getContentDigest()) || row.getReportSeq() == null || row.getReportSeq() <= 0
                || row.getReceiptDigest() == null || !row.getReceiptDigest().matches("[0-9a-f]{64}")
                || row.getLastReceivedAt() == null || row.getLastReceivedAt().isAfter(now)
                || row.getResult() == null || !Set.of("APPLIED", "DISABLED_APPLIED", "REJECTED", "RESTART_REQUIRED", "FAILED").contains(row.getResult())) return false;
        boolean success = "APPLIED".equals(row.getResult()) || "DISABLED_APPLIED".equals(row.getResult());
        return !success || (baseline.getEnabled() ? "APPLIED" : "DISABLED_APPLIED").equals(row.getResult())
                && Objects.equals(row.getAppliedRevision(), row.getConfigRevision()) && row.getAppliedAt() != null
                && baseline.getContentSha256().equals("sha256:" + row.getAppliedDigest())
                && baseline.getRestartDigest().equals("sha256:" + row.getRestartDigest());
    }

    private Map<String, JsonNode> evidence(JsonNode evidence, DdcConfigBaselineVO baseline) {
        if (!evidence.isObject() || !evidence.path("schemaVersion").isIntegralNumber() || evidence.path("schemaVersion").intValue() != 1
                || !evidence.path("targets").isArray() || evidence.path("targets").size() != baseline.getTargetBindings().size()) throw invalid();
        var result = new HashMap<String, JsonNode>();
        for (var target : baseline.getTargetBindings()) {
            JsonNode matched = null;
            for (var row : evidence.path("targets")) if (target.getBindingId().equals(row.path("bindingId").textValue())) {
                if (matched != null) throw invalid(); matched = row;
            }
            if (matched == null || !target.getBindingRevision().equals(matched.path("bindingRevision").textValue())
                    || !target.getClusterId().equals(matched.path("clusterId").textValue())
                    || !target.getKubernetesNamespace().equals(matched.path("namespace").textValue())
                    || !target.getWorkloadUid().equals(matched.path("workloadUid").textValue())) throw invalid();
            String status = matched.path("status").textValue();
            if (status == null || !Set.of("APPLIED", "PENDING", "FAILED", "SUPERSEDED").contains(status)) throw invalid();
            if ("APPLIED".equals(status)) {
                positive(matched, "workloadGeneration");
                if (!matched.path("configMapName").asText().matches("tianshu-baseline-[0-9a-f]{64}")
                        || !ascii(matched.path("configMapUid").textValue(), 128) || !ascii(matched.path("resourceVersion").textValue(), 128)
                        || !Instant.parse(matched.path("observedAt").textValue()).isBefore(clock.instant().plusNanos(1))) throw invalid();
            }
            result.put(target.getBindingId(), matched);
        }
        return result;
    }

    private static ObservedIncarnationVO physical(DdcConfigBootBindingBO row) {
        return new ObservedIncarnationVO(row.getPodUid(), row.getContainerName(), row.getContainerId());
    }
    private static String combine(List<String> values) {
        if (values.contains("UNKNOWN")) return "UNKNOWN";
        if (values.contains("SUPERSEDED")) return "SUPERSEDED";
        if (values.contains("PARTIAL_FAILURE")) return "PARTIAL_FAILURE";
        if (values.stream().allMatch("PUBLISHED_WAITING_FOR_CONSUMERS"::equals)) return "PUBLISHED_WAITING_FOR_CONSUMERS";
        return values.stream().allMatch("APPLIED"::equals) ? "APPLIED" : "PENDING";
    }
    private static long positive(JsonNode node, String field) {
        String text = node.path(field).textValue();
        if (text == null || !text.matches("[1-9][0-9]{0,18}")) throw invalid();
        return Long.parseLong(text);
    }
    private static boolean ascii(String value, int length) { return value != null && value.length() <= length && value.matches("[\\x21-\\x7e]+"); }
    private static String safeCode(String code) { return code != null && code.matches("[A-Z][A-Z0-9_]{0,63}") ? code : null; }
    private static IllegalStateException invalid() { return new IllegalStateException("CONFIG_CURRENT_EVIDENCE_INVALID"); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
