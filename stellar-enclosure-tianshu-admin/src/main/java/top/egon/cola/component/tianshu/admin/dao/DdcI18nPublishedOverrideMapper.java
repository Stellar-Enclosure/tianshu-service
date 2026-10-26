package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nPublishedOverridePO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nOverrideEntryBO;
import java.util.List;

@Mapper
public interface DdcI18nPublishedOverrideMapper extends EgonColaMapper<DdcI18nPublishedOverridePO> {
    List<DdcI18nOverrideEntryBO> findApplicableEntries(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("pageCode") String pageCode, @Param("offset") long offset, @Param("limit") int limit);
    DdcI18nPublishedOverridePO findByOperation(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("pageId") Long pageId, @Param("operationId") String operationId);
}
