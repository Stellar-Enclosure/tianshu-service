package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceVersionPO;

@Mapper
public interface DdcI18nSourceVersionMapper extends EgonColaMapper<DdcI18nSourceVersionPO> {
    DdcI18nSourceVersionPO findByVersion(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("producerId") String producerId, @Param("sourceVersion") String sourceVersion);
}
