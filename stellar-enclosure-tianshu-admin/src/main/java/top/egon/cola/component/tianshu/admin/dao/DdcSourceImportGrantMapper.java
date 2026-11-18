package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcSourceImportGrantPO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DdcSourceImportGrantMapper extends EgonColaMapper<DdcSourceImportGrantPO> {
    /** Domain-key operations below are the supported persistence boundary. */
    @Override
    default DdcSourceImportGrantPO selectActiveById(Serializable id) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default List<DdcSourceImportGrantPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default int deleteVersionedById(DdcSourceImportGrantPO entity) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    DdcSourceImportGrantPO findEnabled(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("producerId") String producerId, @Param("bindingDigest") String bindingDigest);
    int fenceEnabled(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion, @Param("actor") String actor, @Param("now") java.time.Instant now);
}
