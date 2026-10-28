package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListDefinitionBO;

@Mapper
public interface DdcListDefinitionMapper {
    DdcListDefinitionBO find(@Param("tenantId") Long tenantId, @Param("scopeId") Long scopeId,
            @Param("activationVersion") Long activationVersion, @Param("pageKey") String pageKey, @Param("listKey") String listKey);
    int insert(@Param("row") DdcListDefinitionBO row);
}
