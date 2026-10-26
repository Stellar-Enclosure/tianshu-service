package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProducerPO;
import java.util.List;

@Mapper
public interface DdcCatalogActivationProducerMapper extends EgonColaMapper<DdcCatalogActivationProducerPO> {
    long countSelectionConflicts(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("projectionId") Long projectionId);
    List<DdcCatalogActivationProducerPO> findByProjection(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("projectionId") Long projectionId);
}
