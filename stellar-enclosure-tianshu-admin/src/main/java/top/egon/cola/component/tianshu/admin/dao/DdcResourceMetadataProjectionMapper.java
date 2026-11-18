package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcResourceMetadataProjectionPO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DdcResourceMetadataProjectionMapper extends EgonColaMapper<DdcResourceMetadataProjectionPO> {
    /** Domain-key operations below are the supported persistence boundary. */
    @Override
    default DdcResourceMetadataProjectionPO selectActiveById(Serializable id) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default List<DdcResourceMetadataProjectionPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default int deleteVersionedById(DdcResourceMetadataProjectionPO entity) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    List<DdcResourceMetadataProjectionPO> search(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("filter") DdcResourceMetadataQuery filter, @Param("offset") long offset, @Param("limit") int limit);
    long countSearch(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("filter") DdcResourceMetadataQuery filter);
    List<DdcResourceMetadataProjectionPO> findBySource(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("sourceId") Long sourceId);
    List<DdcResourceMetadataProjectionPO> findActivatedResource(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("key") String key);
    List<DdcResourceMetadataProjectionPO> findActivatedResources(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("keys") List<String> keys);
}
