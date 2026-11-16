package top.egon.cola.component.tianshu.admin.dao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiKindPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiReferenceTargetTypePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiReferencePO;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** SI 有向依赖边持久化 Mapper（含 DAG 环检查边与天机流程引用维护）。 */
@Mapper
@Validated
public interface DdcSiReferenceMapper extends EgonColaMapper<DdcSiReferencePO> {

    @Override
    default DdcSiReferencePO selectActiveById(Serializable id) {
        return selectActiveById(EgonColaTenantIdProvider.currentTenantId(), requireInternalId(id));
    }

    @Override
    default List<DdcSiReferencePO> selectActiveByIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> internalIds = new ArrayList<>(ids.size());
        ids.forEach(id -> internalIds.add(requireInternalId(id)));
        return selectActiveByIds(EgonColaTenantIdProvider.currentTenantId(), internalIds);
    }

    @Override
    default int restoreVersionedById(DdcSiReferencePO reference) {
        throw new UnsupportedOperationException("SI_REFERENCE_RESTORE_DISABLED");
    }

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcSiReferencePO selectActiveById(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("id") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long id);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiReferencePO> selectActiveByIds(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("ids") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            Collection<@Positive(groups = EgonColaModelValidationGroups.Query.class) Long> ids);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiReferencePO> selectBlockingInboundReferences(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("envCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 32, groups = EgonColaModelValidationGroups.Query.class) String envCode,
            @Param("targetKind") @NotNull(groups = EgonColaModelValidationGroups.Query.class) DdcAiKindPersistenceEnum targetKind,
            @Param("targetKey") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 128, groups = EgonColaModelValidationGroups.Query.class) String targetKey);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiReferencePO> selectAgentGraphEdges(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("envCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 32, groups = EgonColaModelValidationGroups.Query.class) String envCode);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiReferencePO> selectBySource(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("envCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 32, groups = EgonColaModelValidationGroups.Query.class) String envCode,
            @Param("sourceType") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            DdcSiReferenceTargetTypePersistenceEnum sourceType,
            @Param("sourceKey") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 128, groups = EgonColaModelValidationGroups.Query.class) String sourceKey);

    int deleteBySource(
            @Param("tenantId") @NotNull @Positive Long tenantId,
            @Param("envCode") @NotNull @Size(max = 32) String envCode,
            @Param("sourceType") @NotNull DdcSiReferenceTargetTypePersistenceEnum sourceType,
            @Param("sourceKey") @NotNull @Size(max = 128) String sourceKey,
            @Param("updateUserId") @NotNull String updateUserId,
            @Param("updateTime") @NotNull Instant updateTime);

    int deleteProcessReferences(
            @Param("tenantId") @NotNull @Positive Long tenantId,
            @Param("envCode") @NotNull @Size(max = 32) String envCode,
            @Param("processKey") @NotNull @Size(max = 128) String processKey,
            @Param("updateUserId") @NotNull String updateUserId,
            @Param("updateTime") @NotNull Instant updateTime);

    private static Long requireInternalId(Serializable id) {
        if (!(id instanceof Long value) || value <= 0) {
            throw new IllegalArgumentException("ID_MUST_BE_POSITIVE_LONG");
        }
        return value;
    }
}
