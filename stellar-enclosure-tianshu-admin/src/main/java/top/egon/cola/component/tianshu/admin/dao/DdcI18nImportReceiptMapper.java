package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nImportReceiptPO;

@Mapper
public interface DdcI18nImportReceiptMapper extends EgonColaMapper<DdcI18nImportReceiptPO> {
    DdcI18nImportReceiptPO findByOperation(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("producerId") String producerId, @Param("operationId") String operationId);
}
