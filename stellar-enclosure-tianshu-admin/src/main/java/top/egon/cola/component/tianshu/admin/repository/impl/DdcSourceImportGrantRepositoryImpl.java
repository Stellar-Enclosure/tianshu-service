package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcSourceImportGrantMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportGrantBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSourceImportGrantPO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSourceImportGrantConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcSourceImportGrantRepository;
import java.util.Optional;

@Repository("ddcSourceImportGrantRepository")
@RequiredArgsConstructor
public class DdcSourceImportGrantRepositoryImpl extends EgonColaRepository<DdcSourceImportGrantMapper, DdcSourceImportGrantPO>
        implements DdcSourceImportGrantRepository {
    @Qualifier("ddcSourceImportGrantMapper") private final DdcSourceImportGrantMapper mapper;
    @Qualifier("ddcSourceImportGrantConverterImpl") private final DdcSourceImportGrantConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;

    @Override public DdcSourceImportGrantMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @Override public Optional<DdcSourceImportGrantBO> findEnabled(Long catalogScopeId, String producerId, String bindingDigest) {
        return Optional.ofNullable(mapper.findEnabled(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, producerId, bindingDigest)).map(converter::toTarget);
    }

    @Override public Optional<DdcSourceImportGrantBO> lockEnabled(Long catalogScopeId, String producerId, String bindingDigest) {
        return Optional.ofNullable(mapper.lockEnabled(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, producerId, bindingDigest)).map(converter::toTarget);
    }

    @Override public DdcSourceImportGrantBO insert(DdcSourceImportGrantBO command) {
        DdcSourceImportGrantPO row = converter.toSource(command);
        row.setId(identifierGenerator.nextId(row));
        row.setTenantId(EgonColaTenantIdProvider.currentTenantId());
        if (!save(row)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
        return converter.toTarget(row);
    }
}
