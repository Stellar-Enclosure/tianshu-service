package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcResourceMetadataProjectionMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcResourceMetadataProjectionBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcResourceMetadataProjectionPO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcResourceMetadataProjectionConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcResourceMetadataProjectionRepository;
import java.util.List;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;

@Repository("ddcResourceMetadataProjectionRepository")
@RequiredArgsConstructor
public class DdcResourceMetadataProjectionRepositoryImpl extends EgonColaRepository<DdcResourceMetadataProjectionMapper, DdcResourceMetadataProjectionPO>
        implements DdcResourceMetadataProjectionRepository {
    @Qualifier("ddcResourceMetadataProjectionMapper") private final DdcResourceMetadataProjectionMapper mapper;
    @Qualifier("ddcResourceMetadataProjectionConverterImpl") private final DdcResourceMetadataProjectionConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator") private final EgonColaIdentifierGenerator identifierGenerator;

    @Override public DdcResourceMetadataProjectionMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }
    @Override public DdcPageResult<DdcResourceMetadataProjectionBO> search(Long catalogScopeId, Long projectionId, DdcResourceMetadataQuery filter, PageQuery page) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        long offset = Math.multiplyExact((long) page.pageNo() - 1, page.pageSize());
        return DdcPageResult.<DdcResourceMetadataProjectionBO>builder()
                .records(mapper.search(tenantId, catalogScopeId, projectionId, filter, offset, page.pageSize()).stream().map(converter::toTarget).toList())
                .total(mapper.countSearch(tenantId, catalogScopeId, projectionId, filter)).pageNo(page.pageNo()).pageSize(page.pageSize()).build();
    }

    @Override public List<DdcResourceMetadataProjectionBO> findBySource(Long catalogScopeId, Long sourceId) {
        return converter.toTargetList(mapper.findBySource(EgonColaTenantIdProvider.currentTenantId(), catalogScopeId, sourceId));
    }

    @Override public void insertAll(List<DdcResourceMetadataProjectionBO> commands) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        if (commands.isEmpty()) return;
        List<DdcResourceMetadataProjectionPO> rows = converter.toSourceList(commands);
        for (DdcResourceMetadataProjectionPO row : rows) row.setId(identifierGenerator.nextId(row)).setTenantId(tenantId);
        if (!saveBatch(rows, 500)) throw new IllegalStateException("TIANSHU_METADATA_INSERT_NOT_APPLIED");
    }
}
