package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceMessagePO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DdcI18nSourceMessageMapper extends EgonColaMapper<DdcI18nSourceMessagePO> {
    /** Domain-key operations below are the supported persistence boundary. */
    @Override
    default DdcI18nSourceMessagePO selectActiveById(Serializable id) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default List<DdcI18nSourceMessagePO> selectActiveByIds(Collection<? extends Serializable> ids) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default int deleteVersionedById(DdcI18nSourceMessagePO entity) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    List<DdcI18nSourceMessagePO> findByActivationPage(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("pageCode") String pageCode, @Param("includeCommon") boolean includeCommon, @Param("offset") long offset, @Param("limit") int limit);
    List<DdcI18nSourceMessagePO> findBySource(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("sourceId") Long sourceId);
}
