package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProjectionPO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DdcCatalogActivationProjectionMapper extends EgonColaMapper<DdcCatalogActivationProjectionPO> {
    /** Domain-key operations below are the supported persistence boundary. */
    @Override
    default DdcCatalogActivationProjectionPO selectActiveById(Serializable id) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default List<DdcCatalogActivationProjectionPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default int deleteVersionedById(DdcCatalogActivationProjectionPO entity) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    DdcCatalogActivationProjectionPO findByVersion(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("activationVersion") Long activationVersion);
}
