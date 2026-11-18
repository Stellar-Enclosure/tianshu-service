package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProducerPO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DdcCatalogActivationProducerMapper extends EgonColaMapper<DdcCatalogActivationProducerPO> {
    /** Domain-key operations below are the supported persistence boundary. */
    @Override
    default DdcCatalogActivationProducerPO selectActiveById(Serializable id) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default List<DdcCatalogActivationProducerPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default int deleteVersionedById(DdcCatalogActivationProducerPO entity) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    long countSelectionConflicts(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("projectionId") Long projectionId);
    List<DdcCatalogActivationProducerPO> findByProjection(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("projectionId") Long projectionId);
}
