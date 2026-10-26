package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nSourceVersionMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceVersionBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceVersionPO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nSourceVersionConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceVersionRepository;
import java.util.Optional;

@Repository("ddcI18nSourceVersionRepository")
@RequiredArgsConstructor
public class DdcI18nSourceVersionRepositoryImpl extends EgonColaRepository<DdcI18nSourceVersionMapper, DdcI18nSourceVersionPO>
        implements DdcI18nSourceVersionRepository {
    @Qualifier("ddcI18nSourceVersionMapper") private final DdcI18nSourceVersionMapper mapper;
    @Qualifier("ddcI18nSourceVersionConverterImpl") private final DdcI18nSourceVersionConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;

    @Override public DdcI18nSourceVersionMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @Override public Optional<DdcI18nSourceVersionBO> findByVersion(Long catalogScopeId, String producerId, String sourceVersion) {
        return Optional.ofNullable(mapper.findByVersion(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, producerId, sourceVersion)).map(converter::toTarget);
    }

    @Override public DdcI18nSourceVersionBO insert(DdcI18nSourceVersionBO command) {
        DdcI18nSourceVersionPO row = converter.toSource(command);
        row.setId(identifierGenerator.nextId(row));
        row.setTenantId(EgonColaTenantIdProvider.currentTenantId());
        if (!save(row)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
        return converter.toTarget(row);
    }
}
