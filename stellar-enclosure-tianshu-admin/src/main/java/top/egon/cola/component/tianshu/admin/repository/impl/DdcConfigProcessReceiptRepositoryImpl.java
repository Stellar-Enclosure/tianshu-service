package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.HeuristicCompletionException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validator;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.MDC;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigProcessReceiptMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigProcessReceiptPO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigProcessReceiptBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigProcessReceiptConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;

@Slf4j
@Repository("ddcConfigProcessReceiptRepository")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Validated
public class DdcConfigProcessReceiptRepositoryImpl extends EgonColaRepository<DdcConfigProcessReceiptMapper,DdcConfigProcessReceiptPO>
        implements DdcConfigProcessReceiptRepository {
    @Qualifier("ddcConfigProcessReceiptMapper") private final DdcConfigProcessReceiptMapper mapper;
    @Qualifier("ddcConfigProcessReceiptConverterImpl") private final DdcConfigProcessReceiptConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;
    @Qualifier("ddcConfigBootBindingRepository") private final DdcConfigBootBindingRepository bootBindingRepository;
    @Qualifier("ddcConfigReceiptTransactionTemplate") private final TransactionTemplate receiptTransactionTemplate;
    @Qualifier("defaultValidator") private final Validator validator;
    @Qualifier("ddcPublishClock") private final Clock clock;
    @Override public DdcConfigProcessReceiptMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }
    @Override public AcceptanceEnum accept(DdcTenantContextDTO context,DdcConfigProcessReceiptBO input) {
        requireContext(context);
        validate(input);
        if (!context.tenantId().equals(input.getTenantId())) throw invalid();
        requireTransactionContract();
        // Freeze mutable caller data before entering a lock-holding transaction.
        var receipt = converter.copyAuthorized(input);
        receipt.setBootFence(input.getBootFence()).setAuthorizedManifest(input.getAuthorizedManifest().deepCopy())
                .setScopeJson(input.getScopeJson().deepCopy());
        verifyReport(receipt);
        var row = converter.toSource(receipt);
        row.setTenantId(context.tenantId());
        var callbackFailure = new AtomicReference<RuntimeException>();
        var completedResult = new AtomicReference<AcceptanceEnum>();
        try {
            return Objects.requireNonNull(receiptTransactionTemplate.execute(status -> {
                try {
                    var result = acceptLocked(context, receipt, row);
                    if (status.isRollbackOnly()) throw new UnexpectedRollbackException("CONFIG_RECEIPT_ROLLBACK_ONLY");
                    completedResult.set(result);
                    return result;
                } catch (RuntimeException failure) {
                    callbackFailure.set(failure);
                    throw failure;
                }
            }));
        } catch (RuntimeException transactionFailure) {
            if (callbackFailure.get() != null) throw stableFailure(callbackFailure.get());
            if (!(transactionFailure instanceof TransactionException)
                    || completedResult.get() != AcceptanceEnum.ADVANCED || isKnownRollback(transactionFailure)) {
                throw stableFailure(transactionFailure);
            }
            // The old transaction has completed its cleanup. This template always starts a new
            // LOCAL transaction/connection; absence or a later row cannot prove our old write.
            try {
                return Objects.requireNonNull(receiptTransactionTemplate.execute(status -> {
                    if (!verifyAuthority(context, receipt)) return AcceptanceEnum.CONFLICT;
                    var committed = mapper.selectExact(row);
                    if (committed != null && sameIdentity(committed, row)
                            && Objects.equals(committed.getReportSeq(), row.getReportSeq())
                            && Objects.equals(committed.getReceiptDigest(), row.getReceiptDigest())) return AcceptanceEnum.SAME;
                    throw new ReceiptUnavailableException();
                }));
            } catch (IllegalArgumentException | DdcConfigBootBindingRepository.BindingConflictException knownDenial) {
                throw knownDenial;
            } catch (RuntimeException unresolved) {
                log.warn("Configuration receipt commit outcome unresolved; failureType={}", unresolved.getClass().getSimpleName());
                throw new ReceiptUnavailableException();
            }
        }
    }
    @Override public List<DdcConfigProcessReceiptBO> readCurrentByTarget(DdcTenantContextDTO context,TargetQueryVO target,List<IncarnationVO> incarnations,int limit) {
        requireContext(context);
        validate(target);
        requireLimit(limit);
        if (incarnations == null || incarnations.size() > 500) throw invalid();
        if (incarnations.isEmpty()) return List.of();
        var current = List.copyOf(incarnations);
        current.forEach(this::validate);
        try { return converter.toTargetList(mapper.selectCurrentByTarget(context.tenantId(), target, current, limit)); }
        catch (DataAccessException unavailable) { throw new ReceiptStoreUnavailableException(); }
    }
    @Override public List<DdcConfigProcessReceiptBO> readHistory(DdcTenantContextDTO context,Instant afterReceivedAt,Long afterId,int limit) {
        requireContext(context);
        requireLimit(limit);
        if (afterReceivedAt == null || afterId == null || afterId < 0) throw invalid();
        try { return converter.toTargetList(mapper.selectHistory(context.tenantId(), afterReceivedAt, afterId, limit)); }
        catch (DataAccessException unavailable) { throw new ReceiptStoreUnavailableException(); }
    }

    private AcceptanceEnum acceptLocked(DdcTenantContextDTO context,DdcConfigProcessReceiptBO receipt,DdcConfigProcessReceiptPO incoming) {
        if (!verifyAuthority(context, receipt)) return AcceptanceEnum.CONFLICT;
        Instant arrival = clock.instant().truncatedTo(ChronoUnit.MICROS);
        incoming.setUpdateUserId(context.actor()).setUpdateTime(arrival);
        incoming.setLastReceivedAt(arrival);
        for (int contention = 0; contention < 3; contention++) {
            var stored = mapper.selectExactForUpdate(incoming);
            if (stored == null) {
                incoming.setId(identifierGenerator.nextId(incoming)).setCreateUserId(context.actor())
                        .setCreateTime(arrival).setVersion(0L).setDeletedAt(null);
                new ValidationUtils(validator).validate(incoming, EgonColaModelValidationGroups.Persisted.class);
                int inserted = mapper.insertIfAbsent(incoming);
                if (inserted == 1) return AcceptanceEnum.ADVANCED;
                if (inserted != 0) throw new IllegalStateException("CONFIG_RECEIPT_INSERT_COUNT_INVALID");
                // READ_COMMITTED supplies a fresh statement snapshot after a unique-key winner commits.
                continue;
            }
            if (!sameIdentity(stored, incoming)) return AcceptanceEnum.CONFLICT;
            int sequence = Long.compare(incoming.getReportSeq(), stored.getReportSeq());
            if (sequence < 0) return AcceptanceEnum.STALE;
            if (sequence == 0) return Objects.equals(incoming.getReceiptDigest(), stored.getReceiptDigest())
                    ? AcceptanceEnum.SAME : AcceptanceEnum.CONFLICT;
            if (!canAdvance(stored, incoming)) return AcceptanceEnum.CONFLICT;
            // A later report may arrive at a replica whose wall clock is behind the writer.
            // SQL repeats this floor against the final MP-filled timestamp parameters.
            Instant monotonicArrival = latest(arrival, stored.getCreateTime(), stored.getUpdateTime(), stored.getLastReceivedAt());
            incoming.setUpdateTime(monotonicArrival);
            incoming.setLastReceivedAt(monotonicArrival);
            incoming.setId(stored.getId()).setVersion(stored.getVersion());
            int advanced = mapper.advance(incoming, stored.getVersion(), stored.getReportSeq());
            if (advanced == 1) return AcceptanceEnum.ADVANCED;
            if (advanced != 0) throw new IllegalStateException("CONFIG_RECEIPT_ADVANCE_COUNT_INVALID");
        }
        throw new org.springframework.dao.OptimisticLockingFailureException("CONFIG_RECEIPT_CONCURRENT_UPDATE");
    }

    private boolean verifyAuthority(DdcTenantContextDTO context,DdcConfigProcessReceiptBO receipt) {
        requireContext(context);
        var confirmed = bootBindingRepository.assertConfirmedFence(context, converter.toBootBinding(receipt));
        if (confirmed == null) throw new DdcConfigBootBindingRepository.BindingUnavailableException();
        // The mapper locks the current item before any receipt row. Its published pointer must
        // still select this immutable revision, including when an uncertain commit is confirmed.
        String fixed = mapper.selectManifest(context.tenantId(), receipt.getConfigId(), receipt.getConfigRevision());
        if (fixed == null) return false;
        JsonNode manifest = DdcConfigCanonicalJson.parse(fixed.getBytes(StandardCharsets.UTF_8));
        if (!manifest.equals(receipt.getAuthorizedManifest())) throw invalid();
        return true;
    }

    private void verifyReport(DdcConfigProcessReceiptBO receipt) {
        JsonNode manifest = receipt.getAuthorizedManifest();
        if (!manifest.isObject() || !receipt.getScopeJson().isObject()
                || DdcConfigCanonicalJson.canonicalBytes(manifest).length > 1048576
                || DdcConfigCanonicalJson.canonicalBytes(receipt.getScopeJson()).length > 8192
                || !textEquals(manifest, "schemaVersion", "1")
                || !manifest.path("source").isObject()
                || !textEquals(manifest.path("source"), "configId", Long.toString(receipt.getConfigId()))
                || !textEquals(manifest.path("source"), "revision", Long.toString(receipt.getConfigRevision()))
                || !textEquals(manifest, "contentSha256", "sha256:" + receipt.getContentDigest())
                || !Objects.equals(manifest.get("scope"), receipt.getScopeJson())
                || !manifest.path("enabled").isBoolean()
                || !manifest.path("restartDigest").isTextual()
                || !manifest.path("restartDigest").textValue().matches("sha256:[0-9a-f]{64}")) throw invalid();
        var command = converter.toCommand(receipt);
        if (!command.isReceiptShapeValid()
                || !DdcConfigCanonicalJson.receiptDigest(command).equals("sha256:" + receipt.getReceiptDigest())) throw invalid();
        // The exact received text remains bound by receiptDigest, but is never stored or
        // displayed. Stable reasonCode is sufficient until the owning API adds diagnostics.
        receipt.setReasonCode(command.getErrorCode()).setReasonMessage(null);
        var result = DdcConfigReceiptStatusEnum.fromCode(receipt.getResult());
        if (result.isSuccessful() && (!textEquals(manifest, "restartDigest", "sha256:" + receipt.getRestartDigest())
                || (manifest.get("enabled").booleanValue() ? result != DdcConfigReceiptStatusEnum.APPLIED
                : result != DdcConfigReceiptStatusEnum.DISABLED_APPLIED))) throw invalid();
    }

    private boolean sameIdentity(DdcConfigProcessReceiptPO stored,DdcConfigProcessReceiptPO incoming) {
        return Objects.equals(stored.getTenantId(), incoming.getTenantId())
                && Objects.equals(stored.getConfigId(), incoming.getConfigId())
                && Objects.equals(stored.getConfigRevision(), incoming.getConfigRevision())
                && Objects.equals(stored.getTargetBindingId(), incoming.getTargetBindingId())
                && Objects.equals(stored.getTargetRevision(), incoming.getTargetRevision())
                && Objects.equals(stored.getClusterId(), incoming.getClusterId())
                && Objects.equals(stored.getK8sNamespace(), incoming.getK8sNamespace())
                && Objects.equals(stored.getWorkloadUid(), incoming.getWorkloadUid())
                && Objects.equals(stored.getPodUid(), incoming.getPodUid())
                && Objects.equals(stored.getContainerName(), incoming.getContainerName())
                && Objects.equals(stored.getContainerId(), incoming.getContainerId())
                && Objects.equals(stored.getBootId(), incoming.getBootId())
                && Objects.equals(stored.getContentDigest(), incoming.getContentDigest())
                && Objects.equals(stored.getRestartDigest(), incoming.getRestartDigest())
                && Objects.equals(stored.getScopeJson(), incoming.getScopeJson()) && stored.getDeletedAt() == null;
    }

    private boolean canAdvance(DdcConfigProcessReceiptPO stored,DdcConfigProcessReceiptPO incoming) {
        var prior = DdcConfigReceiptStatusEnum.fromCode(stored.getResult());
        if (prior == DdcConfigReceiptStatusEnum.RESTART_REQUIRED) return prior.getCode().equals(incoming.getResult());
        return !prior.isSuccessful() || prior.getCode().equals(incoming.getResult())
                && Objects.equals(stored.getAppliedRevision(), incoming.getAppliedRevision())
                && Objects.equals(stored.getAppliedDigest(), incoming.getAppliedDigest());
    }

    private boolean textEquals(JsonNode object,String field,String value) {
        return object.path(field).isTextual() && value.equals(object.path(field).textValue());
    }

    private void requireContext(DdcTenantContextDTO context) {
        validate(context);
        if (!context.tenantId().equals(EgonColaTenantIdProvider.currentTenantId())
                || !context.actor().equals(MDC.get("userId"))) throw invalid();
    }

    private void requireTransactionContract() {
        if (receiptTransactionTemplate.getIsolationLevel() != TransactionDefinition.ISOLATION_READ_COMMITTED
                || receiptTransactionTemplate.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRES_NEW
                || receiptTransactionTemplate.isReadOnly()) throw new IllegalStateException("CONFIG_RECEIPT_TRANSACTION_CONTRACT_REQUIRED");
    }

    private void validate(Object value) {
        if (value == null) throw invalid();
        try { new ValidationUtils(validator).validate(value); }
        catch (RuntimeException invalidInput) { throw invalid(); }
    }

    private void requireLimit(int limit) { if (limit < 1 || limit > 500) throw invalid(); }
    private Instant latest(Instant initial, Instant... values) {
        Instant result = initial;
        for (Instant value : values) if (value != null && value.isAfter(result)) result = value;
        return result;
    }

    private boolean isKnownRollback(RuntimeException failure) {
        return failure instanceof UnexpectedRollbackException
                || failure instanceof HeuristicCompletionException heuristic
                && heuristic.getOutcomeState() == HeuristicCompletionException.STATE_ROLLED_BACK;
    }

    private RuntimeException stableFailure(RuntimeException failure) {
        return failure instanceof DataAccessException || failure instanceof TransactionException
                ? new ReceiptStoreUnavailableException() : failure;
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("CONFIG_RECEIPT_INVALID"); }
}
