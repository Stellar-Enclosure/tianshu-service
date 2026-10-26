package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nSourceMessageMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceMessageBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceMessagePO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nSourceMessageConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceMessageRepository;
import java.util.List;

@Repository("ddcI18nSourceMessageRepository")
@RequiredArgsConstructor
public class DdcI18nSourceMessageRepositoryImpl extends EgonColaRepository<DdcI18nSourceMessageMapper, DdcI18nSourceMessagePO>
        implements DdcI18nSourceMessageRepository {
    @Qualifier("ddcI18nSourceMessageMapper") private final DdcI18nSourceMessageMapper mapper;
    @Qualifier("ddcI18nSourceMessageConverterImpl") private final DdcI18nSourceMessageConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;

    @Override public DdcI18nSourceMessageMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }
    @Override public List<DdcI18nSourceMessageBO> findByActivationPage(Long catalogScopeId, Long projectionId, String pageCode, boolean includeCommon, long offset, int limit) {
        return mapper.findByActivationPage(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, projectionId, pageCode, includeCommon, offset, limit).stream().map(converter::toTarget).toList();
    }

    @Override public List<DdcI18nSourceMessageBO> findBySource(Long catalogScopeId, Long sourceId) {
        return converter.toTargetList(mapper.findBySource(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, sourceId));
    }

    @Override public void insertAll(List<DdcI18nSourceMessageBO> commands) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        if (commands.isEmpty()) return;
        List<DdcI18nSourceMessagePO> rows = converter.toSourceList(commands);
        for (DdcI18nSourceMessagePO row : rows) row.setId(identifierGenerator.nextId(row)).setTenantId(tenantId);
        if (!saveBatch(rows, 500)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
    }
}
