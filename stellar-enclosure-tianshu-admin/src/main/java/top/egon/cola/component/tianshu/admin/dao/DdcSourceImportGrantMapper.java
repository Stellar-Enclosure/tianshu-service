package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcSourceImportGrantPO;

@Mapper
public interface DdcSourceImportGrantMapper extends EgonColaMapper<DdcSourceImportGrantPO> {
    DdcSourceImportGrantPO findEnabled(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId,
            @Param("producerId") String producerId, @Param("bindingDigest") String bindingDigest);
    int fenceEnabled(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion, @Param("actor") String actor, @Param("now") java.time.Instant now);
}
