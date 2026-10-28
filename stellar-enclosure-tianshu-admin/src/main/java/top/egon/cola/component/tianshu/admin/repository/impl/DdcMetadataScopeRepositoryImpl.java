package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcMetadataScopeMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataFenceSnapshotBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcMetadataScopePO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcMetadataScopeConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import java.util.Optional;

@Repository("ddcMetadataScopeRepository")
@RequiredArgsConstructor
public class DdcMetadataScopeRepositoryImpl extends EgonColaRepository<DdcMetadataScopeMapper, DdcMetadataScopePO>
        implements DdcMetadataScopeRepository {
    @Qualifier("ddcMetadataScopeMapper") private final DdcMetadataScopeMapper mapper;
    @Qualifier("ddcMetadataScopeConverterImpl") private final DdcMetadataScopeConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;

    @Override public DdcMetadataScopeMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @Override public Optional<DdcMetadataScopeBO> findByCatalogScopeId(Long catalogScopeId) {
        return Optional.ofNullable(mapper.selectByCatalogScopeId(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId)).map(converter::toTarget);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<DdcMetadataScopeBO> lockEnabledTarget(Long catalogScopeId) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        DdcMetadataScopePO scope = mapper.selectByCatalogScopeId(tenantId, catalogScopeId);
        if (scope == null) return Optional.empty();
        DdcMetadataFenceSnapshotBO snapshot = mapper.selectEnabledFence(tenantId, catalogScopeId);
        if (snapshot == null || !java.util.Objects.equals(scope.getId(), snapshot.getScopeId())) return Optional.empty();
        String actor = org.slf4j.MDC.get("userId");
        if (actor == null || actor.isBlank()) throw new IllegalStateException("TIANSHU_AUDIT_ACTOR_REQUIRED");
        var now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        // This order matches the existing i18n scope lock order and serializes shared bindings.
        if (mapper.fenceBiz(tenantId, snapshot.getBizId(), snapshot.getBizVersion(), actor, now) != 1
                || mapper.fenceNamespace(tenantId, snapshot.getNamespaceId(), snapshot.getNamespaceVersion(), actor, now) != 1
                || mapper.fenceEnv(tenantId, snapshot.getEnvId(), snapshot.getEnvVersion(), actor, now) != 1
                || mapper.fenceApp(tenantId, snapshot.getAppId(), snapshot.getAppVersion(), actor, now) != 1
                || mapper.fenceBinding(tenantId, snapshot.getBindingId(), snapshot.getBindingVersion(), actor, now) != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("metadata scope changed after the replica read");
        }
        return Optional.of(converter.toTarget(scope));
    }

    @Override public DdcMetadataScopeBO insert(DdcMetadataScopeBO command) {
        DdcMetadataScopePO row = converter.toSource(command);
        row.setId(identifierGenerator.nextId(row));
        row.setTenantId(EgonColaTenantIdProvider.currentTenantId());
        if (!save(row)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
        return converter.toTarget(row);
    }
}
