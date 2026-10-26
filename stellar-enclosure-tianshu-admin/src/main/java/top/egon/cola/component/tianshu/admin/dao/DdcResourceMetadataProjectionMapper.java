package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcResourceMetadataProjectionPO;
import java.util.List;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;

@Mapper
public interface DdcResourceMetadataProjectionMapper extends EgonColaMapper<DdcResourceMetadataProjectionPO> {
    List<DdcResourceMetadataProjectionPO> search(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("filter") DdcResourceMetadataQuery filter, @Param("offset") long offset, @Param("limit") int limit);
    long countSearch(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("filter") DdcResourceMetadataQuery filter);
    List<DdcResourceMetadataProjectionPO> findBySource(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("sourceId") Long sourceId);
}
