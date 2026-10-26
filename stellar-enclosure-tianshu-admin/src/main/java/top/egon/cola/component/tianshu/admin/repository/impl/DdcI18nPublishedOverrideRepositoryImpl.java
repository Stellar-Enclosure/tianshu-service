package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nPublishedOverrideMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPublishedOverrideBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nOverrideEntryBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nPublishedOverridePO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nPublishedOverrideConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPublishedOverrideRepository;
import java.util.List;
import java.util.Optional;

@Repository("ddcI18nPublishedOverrideRepository")
@RequiredArgsConstructor
public class DdcI18nPublishedOverrideRepositoryImpl extends EgonColaRepository<DdcI18nPublishedOverrideMapper, DdcI18nPublishedOverridePO> implements DdcI18nPublishedOverrideRepository {
    @Qualifier("ddcI18nPublishedOverrideMapper") private final DdcI18nPublishedOverrideMapper mapper;
    @Qualifier("ddcI18nPublishedOverrideConverterImpl") private final DdcI18nPublishedOverrideConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;
    @Override public DdcI18nPublishedOverrideMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }
    @Override public List<DdcI18nOverrideEntryBO> findApplicableEntries(Long catalogScopeId, Long projectionId, String pageCode, long offset, int limit) {
        return mapper.findApplicableEntries(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, projectionId, pageCode, offset, limit);
    }
    @Override public Optional<DdcI18nPublishedOverrideBO> findByOperation(Long catalogScopeId, Long pageId, String operationId) { return Optional.ofNullable(mapper.findByOperation(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, pageId, operationId)).map(converter::toTarget); }
    @Override public DdcI18nPublishedOverrideBO insert(DdcI18nPublishedOverrideBO command) {
        DdcI18nPublishedOverridePO row = converter.toSource(command);
        row.setId(identifierGenerator.nextId(row)); row.setTenantId(EgonColaTenantIdProvider.currentTenantId());
        if (!save(row)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
        return converter.toTarget(row);
    }
}
