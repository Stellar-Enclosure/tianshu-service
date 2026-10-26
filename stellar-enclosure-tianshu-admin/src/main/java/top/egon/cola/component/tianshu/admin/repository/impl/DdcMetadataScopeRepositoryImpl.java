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

    @Override public Optional<DdcMetadataScopeBO> lockEnabledTarget(Long catalogScopeId) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        if (mapper.lockByCatalogScopeId(tenantId, catalogScopeId) == null) return Optional.empty();
        return Optional.ofNullable(mapper.lockEnabledTarget(tenantId, catalogScopeId)).map(converter::toTarget);
    }

    @Override public DdcMetadataScopeBO insert(DdcMetadataScopeBO command) {
        DdcMetadataScopePO row = converter.toSource(command);
        row.setId(identifierGenerator.nextId(row));
        row.setTenantId(EgonColaTenantIdProvider.currentTenantId());
        if (!save(row)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
        return converter.toTarget(row);
    }
}
