package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nImportReceiptPO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DdcI18nImportReceiptMapper extends EgonColaMapper<DdcI18nImportReceiptPO> {
    /** Domain-key operations below are the supported persistence boundary. */
    @Override
    default DdcI18nImportReceiptPO selectActiveById(Serializable id) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default List<DdcI18nImportReceiptPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    @Override
    default int deleteVersionedById(DdcI18nImportReceiptPO entity) {
        throw new UnsupportedOperationException("METADATA_EXPLICIT_OPERATION_REQUIRED");
    }

    DdcI18nImportReceiptPO findByOperation(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("producerId") String producerId, @Param("operationId") String operationId);
}
