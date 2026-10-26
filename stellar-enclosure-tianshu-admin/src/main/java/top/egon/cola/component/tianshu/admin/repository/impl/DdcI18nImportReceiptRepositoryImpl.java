package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nImportReceiptMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nImportReceiptBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nImportReceiptPO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nImportReceiptConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nImportReceiptRepository;
import java.util.Optional;

@Repository("ddcI18nImportReceiptRepository")
@RequiredArgsConstructor
public class DdcI18nImportReceiptRepositoryImpl extends EgonColaRepository<DdcI18nImportReceiptMapper, DdcI18nImportReceiptPO>
        implements DdcI18nImportReceiptRepository {
    @Qualifier("ddcI18nImportReceiptMapper") private final DdcI18nImportReceiptMapper mapper;
    @Qualifier("ddcI18nImportReceiptConverterImpl") private final DdcI18nImportReceiptConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;

    @Override public DdcI18nImportReceiptMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @Override public Optional<DdcI18nImportReceiptBO> findByOperation(Long catalogScopeId, String producerId, String operationId) {
        return Optional.ofNullable(mapper.findByOperation(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, producerId, operationId)).map(converter::toTarget);
    }

    @Override public Long nextReceiptId() {
        EgonColaTenantIdProvider.currentTenantId();
        return identifierGenerator.nextId(new DdcI18nImportReceiptPO());
    }

    @Override public DdcI18nImportReceiptBO insert(DdcI18nImportReceiptBO command) {
        DdcI18nImportReceiptPO row = converter.toSource(command);
        if (row.getId() == null || row.getId() <= 0) throw new IllegalArgumentException("Receipt identity must be allocated first");
        row.setTenantId(EgonColaTenantIdProvider.currentTenantId());
        if (!save(row)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
        return converter.toTarget(row);
    }
}
