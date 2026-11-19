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
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.dao.DdcServiceObservationMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcServiceObservationMapper.TargetKeyVO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcServiceObservationBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcServiceObservationConverter;
import top.egon.cola.component.tianshu.admin.model.po.DdcServiceObservationPO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum;
import top.egon.cola.component.tianshu.admin.repository.DdcServiceObservationRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** PRIMARY-only Service facts. Kubernetes I/O and authorization decisions stay outside this local transaction. */
@Slf4j
@Validated
@Repository("ddcServiceObservationRepository")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcServiceObservationRepositoryImpl
        extends EgonColaRepository<DdcServiceObservationMapper, DdcServiceObservationPO>
        implements DdcServiceObservationRepository {
    private static final Set<String> ENVELOPE_FIELDS = Set.of("schemaVersion", "bindingFingerprint", "writerEpoch",
            "reportSeq", "capturedAt", "view", "payloadDigest", "invalidationFloor");
    @NonNull @Qualifier("ddcServiceObservationMapper") private final DdcServiceObservationMapper mapper;
    @NonNull @Qualifier("ddcServiceObservationConverterImpl") private final DdcServiceObservationConverter converter;
    @NonNull @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @NonNull @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifiers;
    @NonNull @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor tenants;
    @NonNull @Qualifier("ddcServiceObservationTransactionTemplate") private final TransactionTemplate transactions;
    @NonNull @Qualifier("jacksonObjectMapper") private final ObjectMapper json;
    @NonNull @Qualifier("ddcPublishClock") private final Clock clock;
    @NonNull @Qualifier("defaultValidator") private final Validator validator;
    @NonNull @Qualifier("dataSource") private final DataSource dataSource;
    private volatile ObjectMapper observationJson;

    @Override public DdcServiceObservationMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @PostConstruct
    public void validateTransactionConfiguration() {
        if (transactions.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRES_NEW
                || transactions.getIsolationLevel() != TransactionDefinition.ISOLATION_READ_COMMITTED
                || transactions.isReadOnly() || transactions.getTimeout() <= 0) {
            throw new IllegalStateException("TIANSHU_SERVICE_OBSERVATION_PRIMARY_TRANSACTION_REQUIRED");
        }
    }

    @Override
    public long nextWriterEpoch(DdcTenantContextDTO trusted) {
        requireContext(trusted);
        return within(trusted, () -> {
            Long epoch = transactions.execute(status -> {
                var connection = DataSourceUtils.getConnection(dataSource);
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(Math.min(transactions.getTimeout(), 8));
                    // Fixed technical sequence on the validated logical single PRIMARY, with no caller input.
                    try (var rows = statement.executeQuery("SELECT nextval('public.ddc_service_observation_epoch')")) {
                        if (!rows.next()) throw unavailable();
                        long value = rows.getLong(1);
                        if (rows.wasNull() || value <= 0 || rows.next()) throw unavailable();
                        return value;
                    }
                } catch (java.sql.SQLException failure) {
                    throw unavailable();
                } finally {
                    DataSourceUtils.releaseConnection(connection, dataSource);
                }
            });
            if (epoch == null || epoch <= 0) throw unavailable();
            return epoch;
        });
    }

    @Override
    public AcceptanceEnum upsert(DdcTenantContextDTO trusted, DdcServiceObservationBindingVO binding,
            long writerEpoch, long reportSeq, Instant capturedAt, DdcServiceObservationViewVO view) {
        requireBinding(trusted, binding);
        if (writerEpoch <= 0 || reportSeq <= 0 || capturedAt == null || capturedAt.isAfter(clock.instant())) throw invalid();
        validateView(view, capturedAt);
        String fingerprint = fingerprint(binding);
        var payload = envelope(fingerprint, writerEpoch, reportSeq, capturedAt, view);
        var input = converter.toSource(DdcServiceObservationBO.builder().tenantId(trusted.tenantId())
                .identityTenantId(binding.identityTenantId()).bindingId(binding.bindingId()).bindingRevision(binding.bindingRevision())
                .bindingFingerprint(fingerprint).clusterId(binding.clusterId()).k8sNamespace(binding.kubernetesNamespace())
                .serviceName(binding.serviceName()).serviceUid(binding.serviceUid()).portName(binding.portName())
                .servicePort(binding.servicePort()).writerEpoch(writerEpoch).reportSeq(reportSeq)
                .observedAt(micros(view.observedAt())).validUntil(micros(view.validUntil()))
                .recordedAt(micros(capturedAt)).viewJson(payload).build());
        return within(trusted, () -> Objects.requireNonNull(transactions.execute(status -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                var stored = mapper.selectActiveForUpdate(new TargetKeyVO(trusted.tenantId(), binding.bindingId(), binding.bindingRevision()));
                if (stored == null) {
                    input.setId(identifiers.nextId(input)).setVersion(0L).setDeletedAt(null);
                    input.setCreateUserId(trusted.actor()).setUpdateUserId(trusted.actor());
                    input.setCreateTime(micros(clock.instant())).setUpdateTime(input.getCreateTime());
                    validate(input);
                    if (mapper.insertIfAbsent(input) == 1) return AcceptanceEnum.ADVANCED;
                    continue;
                }
                if (!fingerprint.equals(stored.getBindingFingerprint())) return AcceptanceEnum.CONFLICT;
                var previous = decode(stored, binding);
                long epoch = stored.getWriterEpoch();
                long seq = stored.getReportSeq();
                if (writerEpoch < epoch) return AcceptanceEnum.CONFLICT;
                if (writerEpoch == epoch && reportSeq < seq) return AcceptanceEnum.STALE;
                if (writerEpoch == epoch && reportSeq == seq) {
                    return payload.path("payloadDigest").equals(stored.getViewJson().path("payloadDigest"))
                            ? AcceptanceEnum.SAME : AcceptanceEnum.CONFLICT;
                }
                Instant floor = instant(stored.getViewJson(), "invalidationFloor");
                if (view.fresh() && (floor != null && !view.observedAt().isAfter(floor)
                        || previous.observedAt() != null && view.observedAt().isBefore(previous.observedAt()))) {
                    return AcceptanceEnum.STALE;
                }
                var next = payload.deepCopy();
                Instant proposedFloor = instant(next, "invalidationFloor");
                if (floor != null && (proposedFloor == null || floor.isAfter(proposedFloor))) {
                    next.put("invalidationFloor", floor.toString());
                }
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

    @Override
    public Optional<DdcServiceObservationViewVO> findCurrent(DdcTenantContextDTO trusted, DdcServiceObservationBindingVO binding) {
        requireBinding(trusted, binding);
        var key = new TargetKeyVO(trusted.tenantId(), binding.bindingId(), binding.bindingRevision());
        validate(key);
        return within(trusted, () -> transactions.execute(status -> {
            var stored = mapper.selectActive(key);
            if (stored == null || !fingerprint(binding).equals(stored.getBindingFingerprint())) return Optional.empty();
            var view = decode(stored, binding);
            Instant now = clock.instant();
            if (view.validUntil() != null && !now.isBefore(view.validUntil())
                    || view.observedAt() != null && now.isBefore(view.observedAt())) return Optional.empty();
            return Optional.of(view);
        }));
    }

    private ObjectNode envelope(String bindingFingerprint, long epoch, long seq, Instant captured, DdcServiceObservationViewVO view) {
        var node = json.createObjectNode().put("schemaVersion", "1").put("bindingFingerprint", bindingFingerprint)
                .put("writerEpoch", Long.toString(epoch)).put("reportSeq", Long.toString(seq)).put("capturedAt", captured.toString());
        node.set("view", codec().valueToTree(view));
        node.put("payloadDigest", digest(node));
        if (view.fresh()) node.putNull("invalidationFloor");
        else node.put("invalidationFloor", captured.toString());
        if (DdcConfigCanonicalJson.canonicalBytes(node).length > 1_048_576) throw invalid();
        return node;
    }

    private DdcServiceObservationViewVO decode(DdcServiceObservationPO row, DdcServiceObservationBindingVO binding) {
        validate(row);
        if (row.getDeletedAt() != null || row.getTenantId() != binding.tenantId()
                || !binding.identityTenantId().equals(row.getIdentityTenantId()) || !binding.bindingId().equals(row.getBindingId())
                || binding.bindingRevision() != row.getBindingRevision() || !fingerprint(binding).equals(row.getBindingFingerprint())
                || !binding.clusterId().equals(row.getClusterId()) || !binding.kubernetesNamespace().equals(row.getK8sNamespace())
                || !binding.serviceName().equals(row.getServiceName()) || !binding.serviceUid().equals(row.getServiceUid())
                || !binding.portName().equals(row.getPortName()) || binding.servicePort() != row.getServicePort()
                || !Long.toString(row.getTenantId()).equals(MDC.get("tenantId"))) throw invalid();
        try {
            JsonNode envelope = row.getViewJson();
            var fields = new HashSet<String>(); envelope.fieldNames().forEachRemaining(fields::add);
            if (!envelope.isObject() || !ENVELOPE_FIELDS.equals(fields) || !"1".equals(envelope.path("schemaVersion").textValue())
                    || !row.getBindingFingerprint().equals(envelope.path("bindingFingerprint").textValue())
                    || decimal(envelope, "writerEpoch") != row.getWriterEpoch() || decimal(envelope, "reportSeq") != row.getReportSeq()
                    || DdcConfigCanonicalJson.canonicalBytes(envelope).length > 1_048_576) throw invalid();
            var payload = ((ObjectNode) envelope).deepCopy();
            payload.remove(Set.of("payloadDigest", "invalidationFloor"));
            if (!digest(payload).equals(envelope.path("payloadDigest").textValue())) throw invalid();
            var view = codec().treeToValue(envelope.get("view"), DdcServiceObservationViewVO.class);
            Instant captured = Objects.requireNonNull(instant(envelope, "capturedAt"));
            validateView(view, captured);
            if (!Objects.equals(row.getObservedAt(), micros(view.observedAt()))
                    || !Objects.equals(row.getValidUntil(), micros(view.validUntil()))
                    || !row.getRecordedAt().equals(micros(captured))) throw invalid();
            Instant floor = instant(envelope, "invalidationFloor");
            if (view.fresh() && floor != null && !view.observedAt().isAfter(floor)
                    || !view.fresh() && floor == null) throw invalid();
            return view;
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw invalid();
        }
    }

    private void requireBinding(DdcTenantContextDTO trusted, DdcServiceObservationBindingVO binding) {
        requireContext(trusted);
        validate(binding);
        if (trusted.tenantId() != binding.tenantId() || !trusted.identityTenantId().equals(binding.identityTenantId())) throw invalid();
        fingerprint(binding);
    }

    private void validateView(DdcServiceObservationViewVO view, Instant captured) {
        validate(view);
        if (view.status() == null || view.endpoints() == null
                || view.reasonCode() != null && !view.reasonCode().matches("[A-Z][A-Z0-9_]{0,127}")
                || view.observedAt() != null && view.observedAt().isAfter(captured)
                || view.observedAt() == null && view.validUntil() != null
                || !view.fresh() && !view.endpoints().isEmpty()) throw invalid();
        if (view.fresh() && (view.observedAt() == null || view.validUntil() == null || !view.validUntil().isAfter(captured)
                || !ascii(view.serviceResourceVersion(), 128))) throw invalid();
        if (view.observedAt() != null && view.validUntil() != null && (!view.validUntil().isAfter(view.observedAt())
                || Duration.between(view.observedAt(), view.validUntil()).compareTo(Duration.ofMinutes(5)) > 0)) throw invalid();
        var pods = new HashSet<String>();
        for (var endpoint : view.endpoints()) {
            if (endpoint == null || !ascii(endpoint.podUid(), 128) || !pods.add(endpoint.podUid())
                    || endpoint.podName() == null || !endpoint.podName().matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")
                    || endpoint.targetPort() < 1 || endpoint.targetPort() > 65535) throw invalid();
        }
    }

    private String fingerprint(DdcServiceObservationBindingVO binding) {
        var node = json.createObjectNode().put("tenantId", Long.toString(binding.tenantId()))
                .put("identityTenantId", binding.identityTenantId()).put("bizCode", binding.bizCode())
                .put("namespaceCode", binding.namespaceCode()).put("env", binding.env()).put("appCode", binding.appCode())
                .put("bindingId", binding.bindingId()).put("bindingRevision", Long.toString(binding.bindingRevision()))
                .put("clusterId", binding.clusterId()).put("kubernetesNamespace", binding.kubernetesNamespace())
                .put("serviceName", binding.serviceName()).put("serviceUid", binding.serviceUid()).put("portName", binding.portName())
                .put("servicePort", binding.servicePort()).put("protocol", binding.protocol())
                .put("logicalServiceName", binding.logicalServiceName()).put("group", binding.group()).put("version", binding.version());
        var workloads = node.putArray("workloadUids");
        if (binding.workloadUids() == null || binding.workloadUids().isEmpty()) throw invalid();
        binding.workloadUids().stream().sorted().forEach(uid -> {
            if (!ascii(uid, 128)) throw invalid();
            workloads.add(uid);
        });
        return digest(node);
    }

    private <T> T within(DdcTenantContextDTO trusted, Supplier<T> action) {
        try { return tenants.call(trusted, action); }
        catch (IllegalArgumentException | StoreUnavailableException failure) { throw failure; }
        catch (RuntimeException failure) {
            log.warn("Service observation store unavailable; failureType={}", failure.getClass().getSimpleName());
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
                || value instanceof DdcServiceObservationPO row
                    && !validator.validate(row, Default.class, EgonColaModelValidationGroups.Persisted.class).isEmpty()) throw invalid();
    }
    private static long decimal(JsonNode node, String field) {
        String value = node.path(field).textValue();
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(value); } catch (NumberFormatException invalid) { throw invalid(); }
    }
    private static Instant instant(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || !value.textValue().endsWith("Z")) throw invalid();
        try { return Instant.parse(value.textValue()); } catch (RuntimeException invalid) { throw invalid(); }
    }
    private static Instant micros(Instant value) { return value == null ? null : value.truncatedTo(ChronoUnit.MICROS); }
    private static Instant latest(Instant left, Instant right) { return right != null && right.isAfter(left) ? right : left; }
    private static String digest(JsonNode value) {
        try { return "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(DdcConfigCanonicalJson.canonicalBytes(value))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    private static boolean ascii(String value, int max) { return value != null && value.matches("[!-~]{1," + max + "}"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANSHU_SERVICE_OBSERVATION_INVALID"); }
    private static StoreUnavailableException unavailable() { return new StoreUnavailableException(); }
}
