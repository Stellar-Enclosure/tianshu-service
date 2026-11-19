package top.egon.cola.component.tianshu.admin.dao;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.po.DdcPublishTaskPO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Mapper
@Validated
public interface DdcPublishTaskMapper extends EgonColaMapper<DdcPublishTaskPO> {

    @Override
    default DdcPublishTaskPO selectActiveById(Serializable id) {
        return selectActiveById(EgonColaTenantIdProvider.currentTenantId(), internalId(id));
    }

    @Override
    default List<DdcPublishTaskPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return selectActiveByIds(EgonColaTenantIdProvider.currentTenantId(), ids.stream().map(DdcPublishTaskMapper::internalId).toList());
    }

    private static Long internalId(Serializable id) {
        if (!(id instanceof Long value) || value <= 0) throw new IllegalArgumentException("ID_MUST_BE_POSITIVE_LONG");
        return value;
    }

    DdcPublishTaskPO selectActiveById(@Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("id") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long id);

    List<DdcPublishTaskPO> selectActiveByIds(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);
    DdcPublishTaskPO selectByChangeId(@Param("tenantId") Long tenantId, @Param("changeId") String changeId);
    DdcPublishTaskPO selectForUpdateByChangeId(@Param("tenantId") Long tenantId, @Param("changeId") String changeId);
    int updateNativeEvidence(@Param("tenantId") @NotNull @Positive Long tenantId,
            @Param("task") @NotNull DdcPublishTaskBO task, @Param("evidence") @NotNull JsonNode evidence,
            @Param("nativeStatus") @NotNull String nativeStatus, @Param("failureStage") String failureStage,
            @Param("changedAt") @NotNull LocalDateTime changedAt, @Param("actor") String actor, @Param("now") Instant now);

    int resetNativeForRetry(@Param("tenantId") @NotNull @Positive Long tenantId,
            @Param("task") @NotNull DdcPublishTaskBO task, @Param("operator") String operator,
            @Param("changedAt") @NotNull LocalDateTime changedAt, @Param("actor") String actor, @Param("now") Instant now);
    List<DdcPublishTaskPO> selectAll(@Param("tenantId") Long tenantId);
    List<DdcPublishTaskPO> selectSearch(@Param("tenantId") Long tenantId, @Param("bizCode") String bizCode,
            @Param("env") String env, @Param("appCode") String appCode, @Param("status") String status,
            @Param("changeId") String changeId, @Param("offset") long offset, @Param("limit") int limit);
    long countSearch(@Param("tenantId") Long tenantId, @Param("bizCode") String bizCode,
            @Param("env") String env, @Param("appCode") String appCode, @Param("status") String status,
            @Param("changeId") String changeId);

    DdcPublishTaskPO selectFirstByScopeAndResourceAndStatusIn(@Param("tenantId") Long tenantId,
            @Param("bizCode") String bizCode, @Param("env") String env, @Param("appCode") String appCode,
            @Param("resourceName") String resourceName, @Param("statuses") Collection<String> statuses);
}
