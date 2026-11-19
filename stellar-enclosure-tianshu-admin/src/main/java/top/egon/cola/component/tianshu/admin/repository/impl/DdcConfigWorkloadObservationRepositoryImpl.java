package top.egon.cola.component.tianshu.admin.repository.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigWorkloadObservationMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigWorkloadObservationMapper.TargetKeyVO;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.WorkloadViewVO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigWorkloadObservationBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigWorkloadObservationConverter;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigWorkloadObservationPO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

/** A PRIMARY transaction serializes one target; epoch/seq fences persist across separate source processes. */
@Slf4j @Validated @Repository("ddcConfigWorkloadObservationRepository")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigWorkloadObservationRepositoryImpl
        extends EgonColaRepository<DdcConfigWorkloadObservationMapper,DdcConfigWorkloadObservationPO>
        implements DdcConfigWorkloadObservationRepository {
    private static final Set<String> ENVELOPE_FIELDS = Set.of("schemaVersion", "writerEpoch", "reportSeq", "capturedAt", "view",
            "payloadDigest", "rolloutGeneration", "rolloutDigest", "invalidationFloor");
    @NonNull @Qualifier("ddcConfigWorkloadObservationMapper") private final DdcConfigWorkloadObservationMapper mapper;
    @NonNull @Qualifier("ddcConfigWorkloadObservationConverterImpl") private final DdcConfigWorkloadObservationConverter converter;
    @NonNull @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @NonNull @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifiers;
    @NonNull @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor tenants;
    @NonNull @Qualifier("ddcConfigWorkloadObservationTransactionTemplate") private final TransactionTemplate transactions;
    @NonNull @Qualifier("jacksonObjectMapper") private final ObjectMapper json;
    @NonNull @Qualifier("ddcPublishClock") private final Clock clock;
    @NonNull @Qualifier("defaultValidator") private final Validator validator;
    @NonNull @Qualifier("dataSource") private final DataSource dataSource;
    private volatile ObjectMapper observationJson;
    @Override public DdcConfigWorkloadObservationMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @PostConstruct public void validateTransactionConfiguration() {
        if (transactions.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRES_NEW
                || transactions.getIsolationLevel() != TransactionDefinition.ISOLATION_READ_COMMITTED
                || transactions.isReadOnly() || transactions.getTimeout() <= 0) {
            throw new IllegalStateException("TIANSHU_CONFIG_OBSERVATION_PRIMARY_TRANSACTION_REQUIRED");
        }
    }

    @Override public long nextWriterEpoch(DdcTenantContextDTO trusted) {
        requireContext(trusted);
        return within(trusted, () -> {
            // This single fixed technical sequence operation has no tenant table or caller-controlled SQL.
            // Business observation rows continue exclusively through the guarded Mapper statements.
            Long epoch = transactions.execute(status -> {
                var connection = DataSourceUtils.getConnection(dataSource);
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(Math.min(transactions.getTimeout(), 8));
                    try (var rows = statement.executeQuery("SELECT nextval('public.ddc_config_workload_observation_epoch')")) {
                        if (!rows.next()) throw unavailable();
                        long value = rows.getLong(1);
                        if (rows.wasNull() || value <= 0 || rows.next()) throw unavailable();
                        return value;
                    }
                } catch (java.sql.SQLException unavailable) { throw unavailable(); }
                finally { DataSourceUtils.releaseConnection(connection, dataSource); }
            });
            if (epoch == null || epoch <= 0) throw unavailable();
            return epoch;
        });
    }

    @Override public AcceptanceEnum upsert(DdcTenantContextDTO trusted, TargetBindingVO target, long writerEpoch,
            long reportSeq, Instant capturedAt, WorkloadViewVO view) {
        requireContext(trusted);
        validate(target);
        if (writerEpoch <= 0 || reportSeq <= 0 || capturedAt == null || capturedAt.isAfter(clock.instant())) throw invalid();
        validateView(target, view, capturedAt);
        ObjectNode payload = envelope(writerEpoch, reportSeq, capturedAt, view);
        var input = converter.toSource(DdcConfigWorkloadObservationBO.builder().tenantId(trusted.tenantId())
                .targetBindingId(target.getBindingId()).targetRevision(Long.parseLong(target.getBindingRevision()))
                .clusterId(target.getClusterId()).k8sNamespace(target.getKubernetesNamespace()).workloadUid(target.getWorkloadUid())
                .observedAt(micros(view.observedAt())).validUntil(micros(view.validUntil()))
                .recordedAt(micros(capturedAt)).viewJson(payload).build());
        return within(trusted, () -> Objects.requireNonNull(transactions.execute(status -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                var stored = mapper.selectActiveForUpdate(input);
                if (stored == null) {
                    input.setId(identifiers.nextId(input)).setVersion(0L).setDeletedAt(null);
                    input.setCreateUserId(trusted.actor()).setUpdateUserId(trusted.actor());
                    input.setCreateTime(micros(clock.instant())).setUpdateTime(input.getCreateTime());
                    validate(input);
                    if (mapper.insertIfAbsent(input) == 1) return AcceptanceEnum.ADVANCED;
                    continue;
                }
                var previous = decode(stored, target);
                long epoch = decimal(stored.getViewJson(), "writerEpoch");
                long seq = decimal(stored.getViewJson(), "reportSeq");
                if (writerEpoch < epoch) return AcceptanceEnum.CONFLICT;
                if (writerEpoch == epoch && reportSeq < seq) return AcceptanceEnum.STALE;
                if (writerEpoch == epoch && reportSeq == seq) {
                    return payload.path("payloadDigest").equals(stored.getViewJson().path("payloadDigest"))
                            ? AcceptanceEnum.SAME : AcceptanceEnum.CONFLICT;
                }
                ObjectNode next = payload.deepCopy();
                long groupGeneration = nonnegative(stored.getViewJson(), "rolloutGeneration");
                String groupDigest = nullableText(stored.getViewJson(), "rolloutDigest");
                Instant floor = instant(stored.getViewJson(), "invalidationFloor");
                if (view.fresh()) {
                    if (view.workloadGeneration() < groupGeneration
                            || view.workloadGeneration() == groupGeneration && groupDigest != null
                                && !groupDigest.equals(next.path("rolloutDigest").textValue())) return AcceptanceEnum.CONFLICT;
                    if (floor != null && !view.observedAt().isAfter(floor)
                            || previous.observedAt() != null && view.observedAt().isBefore(previous.observedAt())) return AcceptanceEnum.STALE;
                } else {
                    next.put("rolloutGeneration", Long.toString(groupGeneration));
                    if (groupDigest == null) next.putNull("rolloutDigest"); else next.put("rolloutDigest", groupDigest);
                }
                Instant candidateFloor = instant(next, "invalidationFloor");
                if (floor != null && (candidateFloor == null || floor.isAfter(candidateFloor))) next.put("invalidationFloor", floor.toString());
                input.setViewJson(next).setId(stored.getId()).setVersion(stored.getVersion());
                input.setCreateUserId(stored.getCreateUserId()).setCreateTime(stored.getCreateTime());
                input.setUpdateUserId(trusted.actor()).setUpdateTime(latest(micros(clock.instant()), stored.getUpdateTime()));
                validate(input);
                if (mapper.advance(input, stored.getVersion(), epoch, seq) != 1) throw unavailable();
                return AcceptanceEnum.ADVANCED;
            }
            throw unavailable();
        })));
    }

    @Override public Optional<WorkloadViewVO> findCurrent(DdcTenantContextDTO trusted, TargetBindingVO target) {
        requireContext(trusted);
        validate(target);
        var key = new TargetKeyVO(trusted.tenantId(), target.getBindingId(), Long.parseLong(target.getBindingRevision()),
                target.getClusterId(), target.getKubernetesNamespace(), target.getWorkloadUid());
        validate(key);
        return within(trusted, () -> transactions.execute(status -> {
            var stored = mapper.selectActive(key);
            if (stored == null) return Optional.empty();
            var view = decode(stored, target);
            if (view.fresh() && (!clock.instant().isBefore(view.validUntil()) || clock.instant().isBefore(view.observedAt()))) {
                return Optional.of(new WorkloadViewVO(view.target(), StatusEnum.STALE, "OBSERVATION_EXPIRED", view.observedAt(),
                        view.validUntil(), view.validUntil(), view.workloadUid(), view.workloadGeneration(), view.observedGeneration(),
                        view.desiredReplicas(), view.currentPods(), view.currentIncarnations(), view.templateAnnotations(), view.configMapReferences()));
            }
            return Optional.of(view);
        }));
    }

    private ObjectNode envelope(long epoch, long seq, Instant captured, WorkloadViewVO view) {
        var node = json.createObjectNode().put("schemaVersion", "1").put("writerEpoch", Long.toString(epoch))
                .put("reportSeq", Long.toString(seq)).put("capturedAt", captured.toString());
        node.set("view", codec().valueToTree(view));
        node.put("payloadDigest", digest(node));
        node.put("rolloutGeneration", Long.toString(view.fresh() ? view.workloadGeneration() : 0));
        if (view.fresh()) {
            var group = json.createObjectNode().put("workloadUid", view.workloadUid()).put("generation", Long.toString(view.workloadGeneration()));
            group.set("annotations", json.valueToTree(view.templateAnnotations()));
            group.set("configMaps", json.valueToTree(view.configMapReferences()));
            node.put("rolloutDigest", digest(group));
            node.putNull("invalidationFloor");
        } else {
            node.putNull("rolloutDigest");
            node.put("invalidationFloor", (view.invalidatedAt() == null ? captured : view.invalidatedAt()).toString());
        }
        if (DdcConfigCanonicalJson.canonicalBytes(node).length > 1_048_576) throw invalid();
        return node;
    }

    private WorkloadViewVO decode(DdcConfigWorkloadObservationPO row, TargetBindingVO target) {
        validate(row);
        if (row.getDeletedAt() != null || !target.getBindingId().equals(row.getTargetBindingId())
                || !target.getBindingRevision().equals(Long.toString(row.getTargetRevision()))
                || !target.getClusterId().equals(row.getClusterId()) || !target.getKubernetesNamespace().equals(row.getK8sNamespace())
                || !target.getWorkloadUid().equals(row.getWorkloadUid())
                || !Long.toString(row.getTenantId()).equals(MDC.get("tenantId"))) throw invalid();
        try {
            JsonNode envelope = row.getViewJson();
            var fields = new HashSet<String>(); envelope.fieldNames().forEachRemaining(fields::add);
            if (!envelope.isObject() || !ENVELOPE_FIELDS.equals(fields) || !"1".equals(envelope.path("schemaVersion").textValue())
                    || DdcConfigCanonicalJson.canonicalBytes(envelope).length > 1_048_576) throw invalid();
            decimal(envelope, "writerEpoch"); decimal(envelope, "reportSeq");
            nonnegative(envelope, "rolloutGeneration");
            var payload = ((ObjectNode) envelope).deepCopy();
            payload.remove(Set.of("payloadDigest", "rolloutGeneration", "rolloutDigest", "invalidationFloor"));
            if (!digest(payload).equals(envelope.path("payloadDigest").textValue())) throw invalid();
            WorkloadViewVO view = codec().treeToValue(envelope.get("view"), WorkloadViewVO.class);
            Instant captured = Objects.requireNonNull(instant(envelope, "capturedAt"));
            validateView(target, view, captured);
            if (!Objects.equals(row.getObservedAt(), micros(view.observedAt())) || !Objects.equals(row.getValidUntil(), micros(view.validUntil()))
                    || !row.getRecordedAt().equals(micros(captured))) throw invalid();
            Instant floor = instant(envelope, "invalidationFloor");
            if (view.fresh() && floor != null && !view.observedAt().isAfter(floor)) throw invalid();
            return view;
        } catch (IllegalArgumentException invalid) { throw invalid; }
        catch (Exception invalid) { throw invalid(); }
    }

    private void validateView(TargetBindingVO target, WorkloadViewVO view, Instant captured) {
        if (view == null || !target.equals(view.target()) || view.status() == null
                || view.workloadGeneration() < 0 || view.observedGeneration() < 0 || view.desiredReplicas() < 0
                || view.currentPods().size() > 500 || view.currentIncarnations().size() > 500
                || view.observedAt() != null && view.observedAt().isAfter(captured)
                || view.invalidatedAt() != null && view.invalidatedAt().isAfter(captured)
                || view.observedAt() == null && view.validUntil() != null) throw invalid();
        if (!ascii(view.workloadUid(), 128) || view.reasonCode() != null && !view.reasonCode().matches("[A-Z][A-Z0-9_]{0,127}")) throw invalid();
        if (!Set.of("Deployment", "StatefulSet").contains(target.getWorkloadKind())
                || !target.getKubernetesNamespace().matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
                || !target.getContainerName().matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw invalid();
        if (view.fresh() && (!target.getWorkloadUid().equals(view.workloadUid()) || view.workloadGeneration() <= 0
                || view.observedAt() == null || view.validUntil() == null || !view.validUntil().isAfter(captured)
                || view.invalidatedAt() != null && !view.invalidatedAt().isBefore(view.observedAt()))) throw invalid();
        if (view.observedAt() != null && view.validUntil() != null && !view.validUntil().isAfter(view.observedAt())) throw invalid();
        if (view.observedAt() != null && view.validUntil() != null
                && java.time.Duration.between(view.observedAt(), view.validUntil()).compareTo(java.time.Duration.ofMinutes(5)) > 0) throw invalid();
        var pods = new HashSet<String>();
        var physical = new HashSet<java.util.List<String>>();
        for (var pod : view.currentPods()) {
            if (pod == null || !ascii(pod.podUid(), 128) || !pods.add(pod.podUid())
                    || !ascii(pod.podName(), 253) || !target.getContainerName().equals(pod.containerName())
                    || !Set.of("Running", "Pending", "Unknown").contains(pod.phase()) || pod.restartCount() < 0
                    || pod.containerId() != null && !ascii(pod.containerId(), 256)
                    || pod.startedAt() != null && pod.startedAt().isAfter(captured)) throw invalid();
            if (pod.containerId() != null && pod.startedAt() != null && "Running".equals(pod.phase())) {
                physical.add(java.util.List.of(pod.podUid(), pod.containerName(), pod.containerId()));
            }
        }
        var declared = new HashSet<java.util.List<String>>();
        for (var incarnation : view.currentIncarnations()) {
            if (incarnation == null || !ascii(incarnation.podUid(), 128) || !ascii(incarnation.containerId(), 256)
                    || !target.getContainerName().equals(incarnation.containerName())
                    || !declared.add(java.util.List.of(incarnation.podUid(), incarnation.containerName(), incarnation.containerId()))) throw invalid();
        }
        if (!declared.equals(physical) || view.templateAnnotations().size() > 32 || view.configMapReferences().size() > 32) throw invalid();
        if (!Set.of("stellar.io/config-revision", "stellar.io/config-content-sha256", "stellar.io/config-restart-digest")
                .containsAll(view.templateAnnotations().keySet())) throw invalid();
        view.templateAnnotations().forEach((key, value) -> {
            if (key.equals("stellar.io/config-revision")) {
                if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
                try { Long.parseLong(value); } catch (NumberFormatException invalid) { throw invalid(); }
            } else if (key.equals("stellar.io/config-content-sha256")) {
                if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid();
            } else if (value == null || !value.matches("sha256:[0-9a-f]{64}")) throw invalid();
        });
        view.configMapReferences().forEach((key, value) -> { if (!ascii(key, 63) || !ascii(value, 253)) throw invalid(); });
    }

    private <T> T within(DdcTenantContextDTO trusted, java.util.function.Supplier<T> action) {
        validateTransactionConfiguration();
        try { return tenants.call(trusted, action); }
        catch (IllegalArgumentException invalid) { throw invalid; }
        catch (RuntimeException failure) {
            log.warn("Workload observation store unavailable; failureType={}", failure.getClass().getSimpleName());
            throw unavailable();
        }
    }
    private void requireContext(DdcTenantContextDTO trusted) {
        validate(trusted);
        if (!Long.toString(trusted.tenantId()).equals(MDC.get("tenantId")) || !trusted.actor().equals(MDC.get("userId"))
                || trusted.actor().length() > 128) throw invalid();
    }
    private ObjectMapper codec() {
        ObjectMapper current = observationJson;
        if (current == null) {
            synchronized (this) {
                if (observationJson == null) observationJson = json.copy()
                        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                current = observationJson;
            }
        }
        return current;
    }
    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()
                || value instanceof DdcConfigWorkloadObservationPO row
                    && !validator.validate(row, Default.class, EgonColaModelValidationGroups.Persisted.class).isEmpty()) throw invalid();
    }
    private static long decimal(JsonNode node, String field) {
        String text = node.path(field).textValue();
        if (text == null || !text.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(text); } catch (NumberFormatException invalid) { throw invalid(); }
    }
    private static long nonnegative(JsonNode node, String field) {
        return "0".equals(node.path(field).textValue()) ? 0 : decimal(node, field);
    }
    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }
    private static Instant instant(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null) return null;
        if (!value.endsWith("Z")) throw invalid();
        try { return Instant.parse(value); } catch (RuntimeException invalid) { throw invalid(); }
    }
    private static Instant micros(Instant value) { return value == null ? null : value.truncatedTo(ChronoUnit.MICROS); }
    private static Instant latest(Instant left, Instant right) { return right != null && right.isAfter(left) ? right : left; }
    private static String digest(JsonNode value) {
        try { return "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(DdcConfigCanonicalJson.canonicalBytes(value))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    private static boolean ascii(String value, int max) { return value != null && value.matches("[!-~]{1," + max + "}"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANSHU_CONFIG_OBSERVATION_INVALID"); }
    private static StoreUnavailableException unavailable() { return new StoreUnavailableException(); }
}
