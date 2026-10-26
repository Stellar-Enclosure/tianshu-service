package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProjectionPO;
import java.util.List;

@Mapper
public interface DdcCatalogActivationProjectionMapper extends EgonColaMapper<DdcCatalogActivationProjectionPO> {
    DdcCatalogActivationProjectionPO findByVersion(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("activationVersion") Long activationVersion);
}
