package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceMessagePO;
import java.util.List;

@Mapper
public interface DdcI18nSourceMessageMapper extends EgonColaMapper<DdcI18nSourceMessagePO> {
    List<DdcI18nSourceMessagePO> findByActivationPage(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("projectionId") Long projectionId, @Param("pageCode") String pageCode, @Param("includeCommon") boolean includeCommon, @Param("offset") long offset, @Param("limit") int limit);
    List<DdcI18nSourceMessagePO> findBySource(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId, @Param("sourceId") Long sourceId);
}
