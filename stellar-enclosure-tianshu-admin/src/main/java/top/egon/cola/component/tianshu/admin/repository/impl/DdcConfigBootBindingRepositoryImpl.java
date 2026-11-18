package top.egon.cola.component.tianshu.admin.repository.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationUtils;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigBootBindingMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigBootBindingConverter;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigBootBindingPO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns boot registration commit; a returned fence is always read from committed authoritative facts. */
@Slf4j
@Validated
@Repository("ddcConfigBootBindingRepository")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigBootBindingRepositoryImpl
        extends EgonColaRepository<DdcConfigBootBindingMapper, DdcConfigBootBindingPO>
        implements DdcConfigBootBindingRepository {

    @Qualifier("ddcConfigBootBindingMapper") private final DdcConfigBootBindingMapper mapper;
    @Qualifier("ddcConfigBootBindingConverterImpl") private final DdcConfigBootBindingConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor contexts;
    @Qualifier("ddcConfigBootBindingTransactionTemplate") private final TransactionTemplate transactions;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifiers;
    @Qualifier("ddcPublishClock") private final Clock clock;

    @Override public DdcConfigBootBindingMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @PostConstruct
    public void validateTransactionConfiguration() {
        if (transactions.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRES_NEW
                || transactions.getIsolationLevel() != TransactionDefinition.ISOLATION_READ_COMMITTED
                || transactions.isReadOnly() || transactions.getTimeout() <= 0) {
            throw new IllegalStateException("TIANSHU_CONFIG_BOOT_BINDING_TRANSACTION_CONFIGURATION_INVALID");
        }
    }

    @Override
    public DdcConfigBootBindingBO createOrConfirm(DdcTenantContextDTO trustedContext, DdcConfigBootBindingBO verified) {
        validateTransactionConfiguration();
        DdcConfigBootBindingPO expected = snapshot(trustedContext, verified);
        expected.setId(null); // A proposed row/fence from the caller is never installed.
        return contexts.withinConfigBootBinding(trustedContext, () -> createCommitted(trustedContext, expected));
    }

    @Override
    public Optional<DdcConfigBootBindingBO> findConfirmed(DdcTenantContextDTO trustedContext, DdcConfigBootBindingBO verified) {
        validateTransactionConfiguration();
        DdcConfigBootBindingPO expected = snapshot(trustedContext, verified);
        return contexts.withinConfigBootBinding(trustedContext, () -> {
            try {
                return Objects.requireNonNull(transactions.execute(status -> {
                    requireTransaction();
                    return Optional.ofNullable(find(expected)).map(row -> confirmed(row, expected, expected.getId() != null));
                }));
            } catch (BindingConflictException conflict) { throw conflict; }
            catch (RuntimeException unavailable) { throw unavailable(unavailable); }
        });
    }

    /** Runs in the receipt transaction, reading immutable binding facts without an extra transaction or lock. */
    @Override
    public DdcConfigBootBindingBO assertConfirmedFence(DdcTenantContextDTO trustedContext, DdcConfigBootBindingBO verified) {
        DdcConfigBootBindingPO expected = snapshot(trustedContext, verified);
        if (expected.getId() == null || expected.getId() <= 0) { throw new BindingConflictException(); }
        requireTransaction();
        return contexts.withinConfigBootBinding(trustedContext, () -> {
            try {
                DdcConfigBootBindingPO row = find(expected);
                if (row == null) { throw new BindingConflictException(); }
                return confirmed(row, expected, true);
            } catch (BindingConflictException conflict) { throw conflict; }
            catch (RuntimeException unavailable) { throw unavailable(unavailable); }
        });
    }

    private DdcConfigBootBindingBO createCommitted(DdcTenantContextDTO trustedContext, DdcConfigBootBindingPO expected) {
        for (int attempt = 0; attempt < 2; attempt++) {
            AtomicBoolean callbackCompleted = new AtomicBoolean();
            try {
                DdcConfigBootBindingBO result = transactions.execute(status -> {
                    requireTransaction();
                    DdcConfigBootBindingPO existing = find(expected);
                    if (existing != null) {
                        DdcConfigBootBindingBO confirmed = confirmed(existing, expected, false);
                        callbackCompleted.set(true);
                        return confirmed;
                    }
                    DdcConfigBootBindingPO candidate = converter.toSource(converter.toTarget(expected));
                    candidate.setId(identifiers.nextId(candidate));
                    candidate.setCreateTime(clock.instant());
                    candidate.setUpdateTime(candidate.getCreateTime());
                    candidate.setCreateUserId(trustedContext.actor());
                    candidate.setUpdateUserId(trustedContext.actor());
                    EgonColaModelValidationUtils.validate(candidate, EgonColaModelValidationGroups.Operation.INSERT);
                    int affected = mapper.insertIfAbsent(candidate);
                    if (affected < 0 || affected > 1) { throw new BindingUnavailableException(); }
                    // This separate command receives a fresh READ_COMMITTED snapshot after DO NOTHING waits.
                    DdcConfigBootBindingPO row = find(expected);
                    if (row == null) { throw new BindingUnavailableException(); }
                    DdcConfigBootBindingBO confirmed = confirmed(row, expected, false);
                    callbackCompleted.set(true);
                    return confirmed;
                });
                if (result == null) { throw new BindingUnavailableException(); }
                return result; // TransactionTemplate has finished commit before exposing the fence.
            } catch (BindingConflictException conflict) { throw conflict; }
            catch (RuntimeException failure) {
                if (callbackCompleted.get() && !(failure instanceof UnexpectedRollbackException)) {
                    return confirmUnknownCommit(expected);
                }
                if (attempt == 0 && transactionRetryable(failure)) { continue; }
                throw unavailable(failure);
            }
        }
        throw new BindingUnavailableException();
    }

    private DdcConfigBootBindingBO confirmUnknownCommit(DdcConfigBootBindingPO expected) {
        try {
            DdcConfigBootBindingBO confirmed = transactions.execute(status -> {
                requireTransaction();
                DdcConfigBootBindingPO row = find(expected);
                if (row == null) { throw new BindingUnavailableException(); }
                return confirmed(row, expected, false);
            });
            if (confirmed == null) { throw new BindingUnavailableException(); }
            return confirmed;
        } catch (BindingConflictException conflict) { throw conflict; }
        catch (RuntimeException failure) { throw unavailable(failure); }
    }

    private DdcConfigBootBindingPO snapshot(DdcTenantContextDTO trustedContext, DdcConfigBootBindingBO verified) {
        Objects.requireNonNull(trustedContext, "trustedContext");
        DdcConfigBootBindingPO snapshot = converter.toSource(Objects.requireNonNull(verified, "verified"));
        snapshot.setTenantId(0L);
        snapshot.setDeletedAt(null);
        snapshot.setVersion(0L);
        snapshot.setCreateUserId(trustedContext.actor());
        snapshot.setUpdateUserId(trustedContext.actor());
        snapshot.setCreateTime(clock.instant());
        snapshot.setUpdateTime(snapshot.getCreateTime());
        EgonColaModelValidationUtils.validateBusiness(snapshot, EgonColaModelValidationGroups.Operation.INSERT);
        return snapshot;
    }

    private DdcConfigBootBindingPO find(DdcConfigBootBindingPO expected) {
        DdcConfigBootBindingPO row = mapper.findByIncarnation(expected.getClusterId(), expected.getPodUid(),
                expected.getContainerName(), expected.getContainerId());
        return row == null ? null : EgonColaModelValidationUtils.validate(row, EgonColaModelValidationGroups.Operation.LOADED);
    }

    private DdcConfigBootBindingBO confirmed(DdcConfigBootBindingPO row, DdcConfigBootBindingPO expected, boolean exactFence) {
        if (exactFence && !Objects.equals(row.getId(), expected.getId())
                || !Objects.equals(row.getClusterId(), expected.getClusterId())
                || !Objects.equals(row.getPodUid(), expected.getPodUid())
                || !Objects.equals(row.getContainerName(), expected.getContainerName())
                || !Objects.equals(row.getContainerId(), expected.getContainerId())
                || !Objects.equals(row.getBootId(), expected.getBootId())
                || !Objects.equals(row.getWorkloadUid(), expected.getWorkloadUid())
                || !Objects.equals(row.getK8sNamespace(), expected.getK8sNamespace())) {
            throw new BindingConflictException();
        }
        return converter.toTarget(row);
    }

    private void requireTransaction() {
        var environment = getSqlSessionFactory().getConfiguration().getEnvironment();
        Object resource = TransactionSynchronizationManager.getResource(environment.getDataSource());
        if (!(environment.getTransactionFactory() instanceof SpringManagedTransactionFactory)
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !(resource instanceof ConnectionHolder holder)) {
            throw new IllegalStateException("TIANSHU_CONFIG_BOOT_BINDING_TRANSACTION_REQUIRED");
        }
        try {
            if (holder.getConnection().getAutoCommit()
                    || holder.getConnection().getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("TIANSHU_CONFIG_BOOT_BINDING_READ_COMMITTED_REQUIRED");
            }
        } catch (SQLException failure) { throw new BindingUnavailableException(); }
    }

    private static boolean transactionRetryable(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++, current = current.getCause()) {
            if (current instanceof SQLException sql && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    private BindingUnavailableException unavailable(RuntimeException failure) {
        log.warn("Configuration boot binding authority unavailable; failureType={}", failure.getClass().getSimpleName());
        return new BindingUnavailableException();
    }
}
