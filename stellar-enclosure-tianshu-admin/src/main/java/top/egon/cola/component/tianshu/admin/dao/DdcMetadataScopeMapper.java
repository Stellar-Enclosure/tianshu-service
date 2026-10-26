package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcMetadataScopePO;

@Mapper
public interface DdcMetadataScopeMapper extends EgonColaMapper<DdcMetadataScopePO> {
    DdcMetadataScopePO selectByCatalogScopeId(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId);
    DdcMetadataScopePO lockByCatalogScopeId(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId);
    DdcMetadataScopePO lockEnabledTarget(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId);
}
