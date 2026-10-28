package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcMetadataScopePO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataFenceSnapshotBO;

@Mapper
public interface DdcMetadataScopeMapper extends EgonColaMapper<DdcMetadataScopePO> {
    DdcMetadataScopePO selectByCatalogScopeId(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId);
    DdcMetadataFenceSnapshotBO selectEnabledFence(@Param("tenantId") Long tenantId, @Param("catalogScopeId") Long catalogScopeId);
    int fenceBiz(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("expectedVersion") Long version,
            @Param("actor") String actor, @Param("now") java.time.Instant now);
    int fenceNamespace(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("expectedVersion") Long version,
            @Param("actor") String actor, @Param("now") java.time.Instant now);
    int fenceEnv(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("expectedVersion") Long version,
            @Param("actor") String actor, @Param("now") java.time.Instant now);
    int fenceApp(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("expectedVersion") Long version,
            @Param("actor") String actor, @Param("now") java.time.Instant now);
    int fenceBinding(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("expectedVersion") Long version,
            @Param("actor") String actor, @Param("now") java.time.Instant now);
}
