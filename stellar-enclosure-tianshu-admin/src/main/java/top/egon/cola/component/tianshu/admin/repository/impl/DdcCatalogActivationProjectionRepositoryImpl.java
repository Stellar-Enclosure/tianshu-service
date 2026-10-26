package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcCatalogActivationProjectionMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProjectionBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProjectionPO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcCatalogActivationProjectionConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcCatalogActivationProjectionRepository;
import java.util.List;
import java.util.Optional;

@Repository("ddcCatalogActivationProjectionRepository")
@RequiredArgsConstructor
public class DdcCatalogActivationProjectionRepositoryImpl extends EgonColaRepository<DdcCatalogActivationProjectionMapper, DdcCatalogActivationProjectionPO> implements DdcCatalogActivationProjectionRepository {
    @Qualifier("ddcCatalogActivationProjectionMapper") private final DdcCatalogActivationProjectionMapper mapper;
    @Qualifier("ddcCatalogActivationProjectionConverterImpl") private final DdcCatalogActivationProjectionConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;
    @Override public DdcCatalogActivationProjectionMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }
    @Override public Optional<DdcCatalogActivationProjectionBO> findByVersion(Long catalogScopeId, Long activationVersion) { return Optional.ofNullable(mapper.findByVersion(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, activationVersion)).map(converter::toTarget); }
    @Override public DdcCatalogActivationProjectionBO insert(DdcCatalogActivationProjectionBO command) {
        DdcCatalogActivationProjectionPO row = converter.toSource(command);
        row.setId(identifierGenerator.nextId(row)); row.setTenantId(EgonColaTenantIdProvider.currentTenantId());
        if (!save(row)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
        return converter.toTarget(row);
    }
}
