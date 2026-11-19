package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SI 资源、版本、绑定及依赖边统一领域仓储契约（强制显式租户作用域隔离与参数分组校验）。 */
@Validated
public interface DdcSiResourceRepository {

    Long resolveNamespaceId(@NotNull @Positive Long tenantId, @NotBlank String bizCode, @NotBlank String namespaceCode);

    Optional<DdcSiResourceBO> findActiveById(@NotNull @Positive Long tenantId, @NotNull @Positive Long id);

    Optional<DdcSiResourceBO> findProjectedById(@NotNull @Positive Long tenantId, @NotNull @Positive Long id);

    Optional<DdcSiResourceBO> findActiveForUpdateById(@NotNull @Positive Long tenantId, @NotNull @Positive Long id);

    Optional<DdcSiResourceBO> findByBusinessKey(
            @NotNull @Positive Long tenantId,
            @NotBlank @Size(max = 128) String bizCode,
            @NotNull @Positive Long namespaceId,
            @NotBlank @Size(max = 32) String envCode,
            @NotNull @Size(max = 128) String appCode,
            @NotNull DdcSiKindEnum kind,
            @NotBlank @Size(max = 128) String resourceKey);

    DdcPageResult<DdcSiResourceBO> searchPage(
            @NotNull @Positive Long tenantId,
            String bizCode,
            Long namespaceId,
            String envCode,
            String appCode,
            DdcSiKindEnum kind,
            DdcSiLifecycleStatusEnum lifecycleStatus,
            String keyword,
            String tag,
            @NotNull @Valid PageQuery pageQuery);

    DdcPageResult<DdcSiResourceBO> searchCatalog(
            @NotNull @Positive Long tenantId,
            String bizCode,
            Long namespaceId,
            String envCode,
            String appCode,
            DdcSiKindEnum kind,
            boolean includeDeprecated,
            String keyword,
            String tag,
            @NotNull @Valid PageQuery pageQuery);

    @Validated(EgonColaModelValidationGroups.Insert.class)
    DdcSiResourceBO insert(@NotNull @Valid DdcSiResourceBO resource);

    Optional<DdcSiResourceBO> updateVersioned(@NotNull DdcSiResourceBO resource);

    void updateSearchTsv(@NotNull @Positive Long tenantId, @NotNull @Positive Long id, @NotNull String tsvText);

    boolean softDeleteVersioned(@NotNull DdcSiResourceBO resource);

    long allocateNextVersionNo(@NotNull @Positive Long tenantId, @NotNull @Positive Long resourceId);

    /** Allocates the immutable version identity before its dependency manifest is frozen. */
    long allocateVersionId();

    DdcSiVersionBO insertVersion(@NotNull DdcSiVersionBO version);

    Optional<DdcSiVersionBO> findVersionById(@NotNull @Positive Long tenantId, @NotNull @Positive Long versionId);

    Optional<DdcSiVersionBO> findVersionByResourceAndNo(
            @NotNull @Positive Long tenantId,
            @NotNull @Positive Long resourceId,
            @NotNull @Positive Long versionNo);

    /** Exact historical material; deleted versions remain resolvable only through this explicit port. */
    Optional<DdcSiVersionBO> findHistoryVersionByResourceAndNo(
            @NotNull @Positive Long tenantId,
            @NotNull @Positive Long resourceId,
            @NotNull @Positive Long versionNo);

    List<DdcSiVersionBO> findHistoryVersionsByIds(
            @NotNull @Positive Long tenantId,
            @NotNull @Size(max = 100) List<@NotNull @Positive Long> versionIds);

    DdcPageResult<DdcSiVersionBO> pageVersions(
            @NotNull @Positive Long tenantId,
            @NotNull @Positive Long resourceId,
            @NotNull @Valid PageQuery pageQuery);

    List<DdcSiVersionBO> findEnvMatrix(
            @NotNull @Positive Long tenantId,
            @NotBlank String bizCode,
            @NotNull @Positive Long namespaceId,
            @NotNull DdcSiKindEnum kind,
            @NotBlank String resourceKey);

    Optional<DdcSiBindingBO> findActiveBinding(
            @NotNull @Positive Long tenantId,
            @NotBlank String bizCode,
            @NotNull @Positive Long namespaceId,
            @NotBlank String envCode,
            @NotBlank String appCode,
            @NotNull DdcSiKindEnum kind,
            @NotBlank String resourceKey);

    List<DdcSiBindingBO> findBindingsByResourceId(@NotNull @Positive Long tenantId, @NotNull @Positive Long resourceId);

    DdcSiBindingBO upsertBinding(@NotNull DdcSiBindingBO binding, @NotBlank String actor, @NotNull Instant now);

    void deactivateBinding(
            @NotNull @Positive Long tenantId,
            @NotBlank String bizCode,
            @NotNull @Positive Long namespaceId,
            @NotBlank String envCode,
            @NotBlank String appCode,
            @NotNull DdcSiKindEnum kind,
            @NotBlank String resourceKey,
            @NotBlank String actor,
            @NotNull Instant now);

    List<DdcSiReferenceBO> findBlockingInboundReferences(
            @NotNull @Positive Long tenantId,
            @NotBlank String envCode,
            @NotNull DdcSiKindEnum targetKind,
            @NotBlank String targetKey);

    List<DdcSiReferenceBO> findAgentGraphEdges(@NotNull @Positive Long tenantId, @NotBlank String envCode);

    List<DdcSiReferenceBO> findReferencesBySource(
            @NotNull @Positive Long tenantId,
            @NotBlank String envCode,
            @NotNull DdcSiReferenceTargetTypeEnum sourceType,
            @NotBlank String sourceKey);

    void replaceReferences(
            @NotNull @Positive Long tenantId,
            @NotBlank String envCode,
            @NotNull DdcSiReferenceTargetTypeEnum sourceType,
            @NotBlank String sourceKey,
            Long sourceResourceId,
            Long sourceVersionNo,
            Long namespaceId,
            @NotNull List<DdcSiReferenceBO> references,
            @NotBlank String actor,
            @NotNull Instant now);

    void replaceProcessReferences(
            @NotNull @Positive Long tenantId,
            @NotBlank String envCode,
            @NotBlank String processKey,
            @NotNull List<DdcSiReferenceBO> references,
            @NotBlank String actor,
            @NotNull Instant now);

    void deleteProcessReferences(
            @NotNull @Positive Long tenantId,
            @NotBlank String envCode,
            @NotBlank String processKey,
            @NotBlank String actor,
            @NotNull Instant now);

    long countByScope(@NotNull @Positive Long tenantId, @NotBlank String envCode, @Min(1) long limit);
}
