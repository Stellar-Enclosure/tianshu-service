package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcCatalogActivationProducerMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProducerBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProducerPO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcCatalogActivationProducerConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcCatalogActivationProducerRepository;
import java.util.List;
import java.util.Optional;

@Repository("ddcCatalogActivationProducerRepository")
@RequiredArgsConstructor
public class DdcCatalogActivationProducerRepositoryImpl extends EgonColaRepository<DdcCatalogActivationProducerMapper, DdcCatalogActivationProducerPO> implements DdcCatalogActivationProducerRepository {
    @Qualifier("ddcCatalogActivationProducerMapper") private final DdcCatalogActivationProducerMapper mapper;
    @Qualifier("ddcCatalogActivationProducerConverterImpl") private final DdcCatalogActivationProducerConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;
    @Override public DdcCatalogActivationProducerMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }
    @Override public long countSelectionConflicts(Long catalogScopeId, Long projectionId) {
        return mapper.countSelectionConflicts(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, projectionId);
    }
    @Override public List<DdcCatalogActivationProducerBO> findByProjection(Long catalogScopeId, Long projectionId) { return mapper.findByProjection(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, projectionId).stream().map(converter::toTarget).toList(); }
    @Override public void insertAll(List<DdcCatalogActivationProducerBO> commands) {
        List<DdcCatalogActivationProducerPO> rows = commands.stream().map(converter::toSource).toList();
        for (var row : rows) { row.setId(identifierGenerator.nextId(row)); row.setTenantId(EgonColaTenantIdProvider.currentTenantId()); }
        if (!rows.isEmpty() && !saveBatch(rows, 500)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
    }
}
