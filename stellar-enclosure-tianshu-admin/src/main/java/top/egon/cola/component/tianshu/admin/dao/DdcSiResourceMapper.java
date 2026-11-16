package top.egon.cola.component.tianshu.admin.dao;

import jakarta.validation.constraints.Min;
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
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiLifecycleStatusPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiResourcePO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** SI 统一治理主资源持久化 Mapper（严格租户隔离与乐观锁更新）。 */
@Mapper
@Validated
public interface DdcSiResourceMapper extends EgonColaMapper<DdcSiResourcePO> {

    @Override
    default DdcSiResourcePO selectActiveById(Serializable id) {
        return selectActiveById(EgonColaTenantIdProvider.currentTenantId(), requireInternalId(id));
    }

    @Override
    default List<DdcSiResourcePO> selectActiveByIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> internalIds = new ArrayList<>(ids.size());
        ids.forEach(id -> internalIds.add(requireInternalId(id)));
        return selectActiveByIds(EgonColaTenantIdProvider.currentTenantId(), internalIds);
    }

    @Override
    default int restoreVersionedById(DdcSiResourcePO resource) {
        throw new UnsupportedOperationException("SI_RESOURCE_RESTORE_DISABLED");
    }

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcSiResourcePO selectActiveById(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("id") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long id);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiResourcePO> selectActiveByIds(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("ids") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            Collection<@Positive(groups = EgonColaModelValidationGroups.Query.class) Long> ids);

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcSiResourcePO selectProjectedById(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("id") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long id);

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcSiResourcePO selectForUpdateById(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("id") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long id);

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcSiResourcePO selectByBusinessKey(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("bizCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 128, groups = EgonColaModelValidationGroups.Query.class) String bizCode,
            @Param("namespaceId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long namespaceId,
            @Param("envCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 32, groups = EgonColaModelValidationGroups.Query.class) String envCode,
            @Param("appCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 128, groups = EgonColaModelValidationGroups.Query.class) String appCode,
            @Param("kind") @NotNull(groups = EgonColaModelValidationGroups.Query.class) DdcAiKindPersistenceEnum kind,
            @Param("resourceKey") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 128, groups = EgonColaModelValidationGroups.Query.class) String resourceKey);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiResourcePO> selectPage(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("bizCode") String bizCode,
            @Param("namespaceId") Long namespaceId,
            @Param("envCode") String envCode,
            @Param("appCode") String appCode,
            @Param("kind") DdcAiKindPersistenceEnum kind,
            @Param("lifecycleStatus") DdcSiLifecycleStatusPersistenceEnum lifecycleStatus,
            @Param("keyword") String keyword,
            @Param("tag") String tag,
            @Param("offset") @Min(value = 0, groups = EgonColaModelValidationGroups.Query.class) long offset,
            @Param("limit") @Min(value = 1, groups = EgonColaModelValidationGroups.Query.class) int limit);

    @Validated(EgonColaModelValidationGroups.Query.class)
    long countPage(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("bizCode") String bizCode,
            @Param("namespaceId") Long namespaceId,
            @Param("envCode") String envCode,
            @Param("appCode") String appCode,
            @Param("kind") DdcAiKindPersistenceEnum kind,
            @Param("lifecycleStatus") DdcSiLifecycleStatusPersistenceEnum lifecycleStatus,
            @Param("keyword") String keyword,
            @Param("tag") String tag);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiResourcePO> selectCatalog(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("bizCode") String bizCode,
            @Param("namespaceId") Long namespaceId,
            @Param("envCode") String envCode,
            @Param("appCode") String appCode,
            @Param("kind") DdcAiKindPersistenceEnum kind,
            @Param("includeDeprecated") boolean includeDeprecated,
            @Param("keyword") String keyword,
            @Param("tag") String tag,
            @Param("offset") @Min(value = 0, groups = EgonColaModelValidationGroups.Query.class) long offset,
            @Param("limit") @Min(value = 1, groups = EgonColaModelValidationGroups.Query.class) int limit);

    @Validated(EgonColaModelValidationGroups.Query.class)
    long countCatalog(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("bizCode") String bizCode,
            @Param("namespaceId") Long namespaceId,
            @Param("envCode") String envCode,
            @Param("appCode") String appCode,
            @Param("kind") DdcAiKindPersistenceEnum kind,
            @Param("includeDeprecated") boolean includeDeprecated,
            @Param("keyword") String keyword,
            @Param("tag") String tag);

    int updateVersionedFields(@Param("et") @NotNull DdcSiResourcePO resource);

    int updateSearchTsv(
            @Param("tenantId") @NotNull @Positive Long tenantId,
            @Param("id") @NotNull @Positive Long id,
            @Param("tsvText") @NotNull String tsvText);

    private static Long requireInternalId(Serializable id) {
        if (!(id instanceof Long value) || value <= 0) {
            throw new IllegalArgumentException("ID_MUST_BE_POSITIVE_LONG");
        }
        return value;
    }
}
