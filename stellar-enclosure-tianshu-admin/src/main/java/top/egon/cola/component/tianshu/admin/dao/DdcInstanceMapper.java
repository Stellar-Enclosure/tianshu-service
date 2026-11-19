package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.admin.model.po.DdcInstancePO;

import java.util.List;

@Mapper
@Validated
public interface DdcInstanceMapper {

    DdcInstancePO selectByInstanceId(@Param("tenantId") Long tenantId, @Param("instanceId") String instanceId);
    List<DdcInstancePO> selectByScope(@Param("tenantId") Long tenantId, @Param("bizCode") String bizCode,
            @Param("env") String env, @Param("appCode") String appCode);
    List<DdcInstancePO> selectPage(@Param("tenantId") Long tenantId, @Param("bizCode") String bizCode,
            @Param("env") String env, @Param("appCode") String appCode, @Param("offset") long offset,
            @Param("limit") int limit);
    long countPage(@Param("tenantId") Long tenantId, @Param("bizCode") String bizCode,
            @Param("env") String env, @Param("appCode") String appCode);
}
