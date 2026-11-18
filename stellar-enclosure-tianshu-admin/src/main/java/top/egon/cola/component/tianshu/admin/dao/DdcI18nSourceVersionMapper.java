package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceVersionPO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DdcI18nSourceVersionMapper extends EgonColaMapper<DdcI18nSourceVersionPO> {
    /** Domain-key operations below are the supported persistence boundary. */
    @Override
    default DdcI18nSourceVersionPO selectActiveById(Serializable id) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default List<DdcI18nSourceVersionPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default int deleteVersionedById(DdcI18nSourceVersionPO entity) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    DdcI18nSourceVersionPO findByVersion(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("producerId") String producerId, @Param("sourceVersion") String sourceVersion);
}
