package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nPagePO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import java.util.List;

@Mapper
public interface DdcI18nPageMapper extends EgonColaMapper<DdcI18nPagePO> {
    DdcI18nPagePO selectPageById(@Param("tenantId") Long tenantId, @Param("id") Long id);
    DdcI18nPagePO selectByPageCode(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope,
            @Param("pageCode") String pageCode);
    List<DdcI18nPagePO> selectPage(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope,
            @Param("offset") long offset, @Param("limit") int limit);
    long countPage(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope);
    Long lockBiz(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope);
    Long lockNamespace(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope);
    Long lockEnv(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope);
    Long lockApp(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope);
    Long lockBinding(@Param("tenantId") Long tenantId, @Param("scope") DdcI18nScope scope);
}
