package top.egon.cola.component.tianshu.admin.controller.si;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.core.pojo.PageResultRecord;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiBindingCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiLifecycleCommand;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiCqeConverter;
import top.egon.cola.component.tianshu.admin.model.query.DdcSiPageQuery;
import top.egon.cola.component.tianshu.admin.model.query.DdcSiSemanticSearchRequestQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiBindingResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiEnvMatrixResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiReferenceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiResourceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiSemanticSearchResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiVersionResult;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.admin.support.DdcAdminPageSupport;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.net.URI;
import java.util.List;

/** Thin REST controller exposing SI catalog, lifecycle, binding, references and hybrid search endpoints. */
@RestController
@RequestMapping(path = "/api/v1/tianshu/si/resources", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tianshu SI Resources", description = "Super-Intelligence (Agent, MCP, Prompt, Skill) Governance APIs")
@EgonApiCatalog(
        businessDomainCode = "xingyuan",
        businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin",
        entityDomainName = "Dynamic Config Center 管理实体域",
        interfaceGroupCode = "tianshu"
)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Validated
public class DdcSiResourceController {

    private static final String TENANT_HEADER = "X-Egon-Tianshu-Tenant";
    private static final String BASE_PATH = "/api/v1/tianshu/si/resources";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Qualifier("ddcSiResourceService")
    private final DdcSiResourceService siResourceService;

    @Qualifier("ddcSiCqeConverterImpl")
    private final DdcSiCqeConverter converter;

    @Operation(operationId = "searchSiResources", summary = "Browse and filter multi-env SI catalog",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged SI resources"),
            @ApiResponse(responseCode = "400", description = "Invalid query", content = @Content(mediaType = JSON, schema = @Schema(implementation = ResultRecord.class))),
            @ApiResponse(responseCode = "403", description = "Permission denied", content = @Content(mediaType = JSON, schema = @Schema(implementation = ResultRecord.class)))
    })
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.page", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.page")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_READ)
    @GetMapping
    public ResponseEntity<PageResultRecord<DdcSiResourceResult>> page(@Valid @ModelAttribute DdcSiPageQuery query) {
        Long tenantId = requireTenantId();
        DdcPageResult<DdcSiResourceBO> page = siResourceService.page(
                tenantId,
                query.getBizCode(),
                query.getNamespaceCode(),
                query.getEnvCode(),
                query.getAppCode(),
                query.getKind(),
                query.getLifecycleStatus(),
                query.getKeyword(),
                query.getTag(),
                converter.toPageQuery(query)
        );
        return noStore(DdcAdminPageSupport.result(converter.toResourcePage(page)));
    }

    @Operation(operationId = "getSiEnvMatrix", summary = "Compare active versions across environments",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.envMatrix", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.envMatrix")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_READ)
    @GetMapping("/env-matrix")
    public ResponseEntity<ResultRecord<List<DdcSiEnvMatrixResult>>> getEnvMatrix(
            @RequestParam("bizCode") @NotBlank @Size(max = 128) String bizCode,
            @RequestParam("namespaceCode") @NotBlank @Size(max = 128) String namespaceCode,
            @RequestParam("kind") @NotNull DdcSiKindEnum kind,
            @RequestParam("resourceKey") @NotBlank @Size(max = 128) String resourceKey) {
        Long tenantId = requireTenantId();
        List<DdcSiVersionBO> matrix = siResourceService.getEnvMatrix(
                tenantId, bizCode, namespaceCode, kind, resourceKey);
        return noStore(ResultRecord.success(converter.toEnvMatrixResults(matrix)));
    }

    @Operation(operationId = "semanticSearchSiResources", summary = "Hybrid PG full-text and pgvector semantic search",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.semanticSearch", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.semanticSearch")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_READ)
    @PostMapping("/semantic-search")
    public ResponseEntity<ResultRecord<DdcSiSemanticSearchResult>> semanticSearch(
            @Valid @RequestBody DdcSiSemanticSearchRequestQuery query) {
        Long tenantId = requireTenantId();
        List<DdcSiSearchHit> hits = siResourceService.hybridSearch(converter.toSemanticSearchQuery(query, tenantId));
        return noStore(ResultRecord.success(converter.toSearchResult(hits)));
    }

    @Operation(operationId = "getSiResource", summary = "Get single SI resource details",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.get", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.get")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ResultRecord<DdcSiResourceResult>> get(@PathVariable("id") @Positive Long id) {
        Long tenantId = requireTenantId();
        return noStore(ResultRecord.success(converter.toResourceResult(siResourceService.get(tenantId, id))));
    }

    @Operation(operationId = "createSiResource", summary = "Create draft SI resource",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.create", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.create")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_WRITE)
    @PostMapping
    public ResponseEntity<ResultRecord<DdcSiResourceResult>> create(
            @Valid @RequestBody DdcSiCreateCommand command,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        DdcSiResourceBO created = siResourceService.create(
                tenantId,
                command.getBizCode(),
                command.getNamespaceCode(),
                command.getEnvCode(),
                command.getAppCode(),
                command.getKind(),
                command.getResourceKey(),
                command.getDisplayName(),
                command.getDescription(),
                command.getTags(),
                command.getSourceType(),
                command.getSpecJson(),
                resolveActor(authentication)
        );
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.getId()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ResultRecord.success(converter.toResourceResult(created)));
    }

    @Operation(operationId = "replaceSiDraft", summary = "Update SI resource draft with optimistic locking",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.replaceDraft", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.replaceDraft")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_WRITE)
    @PutMapping("/{id}/draft")
    public ResponseEntity<ResultRecord<DdcSiResourceResult>> replaceDraft(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody DdcSiDraftCommand command,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        DdcSiResourceBO updated = siResourceService.updateDraft(
                tenantId,
                id,
                command.getExpectedVersion(),
                command.getDisplayName(),
                command.getDescription(),
                command.getTags(),
                command.getSpecJson(),
                resolveActor(authentication)
        );
        return noStore(ResultRecord.success(converter.toResourceResult(updated)));
    }

    @Operation(operationId = "publishSiDraft", summary = "Publish draft as an immutable version",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.publish", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.publish")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_PUBLISH)
    @PostMapping("/{id}/publications")
    public ResponseEntity<ResultRecord<DdcSiVersionResult>> publish(
            @PathVariable("id") @Positive Long id,
            @Validated(DdcSiLifecycleCommand.PublishGroup.class) @RequestBody DdcSiLifecycleCommand command,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        DdcSiVersionBO published = siResourceService.publish(
                tenantId, id, command.getExpectedVersion(), command.getChangeNote(), resolveActor(authentication));
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + id + "/versions/" + published.getVersionNo()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ResultRecord.success(converter.toVersionResult(published)));
    }

    @Operation(operationId = "promoteSiVersion", summary = "Promote active version across environments",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.promote", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.promote")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_PROMOTE)
    @PostMapping("/{id}/promotions")
    public ResponseEntity<ResultRecord<DdcSiVersionResult>> promote(
            @PathVariable("id") @Positive Long id,
            @Validated(DdcSiLifecycleCommand.PromoteGroup.class) @RequestBody DdcSiLifecycleCommand command,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        DdcSiVersionBO promoted = siResourceService.promote(
                tenantId, id, command.getTargetEnvCode(), command.getChangeNote(), resolveActor(authentication));
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + promoted.getResourceId() + "/versions/" + promoted.getVersionNo()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ResultRecord.success(converter.toVersionResult(promoted)));
    }

    @Operation(operationId = "rollbackSiResource", summary = "Rollback active version to a historical snapshot",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.rollback", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.rollback")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_PUBLISH)
    @PostMapping("/{id}/rollbacks")
    public ResponseEntity<ResultRecord<DdcSiVersionResult>> rollback(
            @PathVariable("id") @Positive Long id,
            @Validated(DdcSiLifecycleCommand.RollbackGroup.class) @RequestBody DdcSiLifecycleCommand command,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        DdcSiVersionBO rolled = siResourceService.rollback(
                tenantId, id, command.getExpectedVersion(), command.getTargetVersionNo(),
                command.getChangeNote(), resolveActor(authentication));
        return noStore(ResultRecord.success(converter.toVersionResult(rolled)));
    }

    @Operation(operationId = "changeSiLifecycle", summary = "Deprecate active SI resource",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.changeLifecycle", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.changeLifecycle")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_PUBLISH)
    @PostMapping("/{id}/lifecycle")
    public ResponseEntity<ResultRecord<DdcSiResourceResult>> changeLifecycle(
            @PathVariable("id") @Positive Long id,
            @Validated(DdcSiLifecycleCommand.LifecycleActionGroup.class) @RequestBody DdcSiLifecycleCommand command,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        DdcSiResourceBO updated = siResourceService.deprecate(
                tenantId, id, command.getExpectedVersion(), resolveActor(authentication));
        return noStore(ResultRecord.success(converter.toResourceResult(updated)));
    }

    @Operation(operationId = "deleteSiResource", summary = "Archive SI resource with active reference guard",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.delete", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.delete")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_ARCHIVE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(
            @PathVariable("id") @Positive Long id,
            @RequestParam("expectedVersion") @NotNull @Min(0) Long expectedVersion,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        siResourceService.archive(tenantId, id, expectedVersion, resolveActor(authentication));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @Operation(operationId = "pageSiResourceVersions", summary = "List immutable version history",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.pageVersions", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.pageVersions")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_READ)
    @GetMapping("/{id}/versions")
    public ResponseEntity<PageResultRecord<DdcSiVersionResult>> pageVersions(
            @PathVariable("id") @Positive Long id,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) int pageSize) {
        Long tenantId = requireTenantId();
        DdcPageResult<DdcSiVersionBO> page = siResourceService.pageVersions(
                tenantId, id, new PageQuery(pageNo, Math.min(pageSize, 100)));
        return noStore(DdcAdminPageSupport.result(converter.toVersionPage(page)));
    }

    @Operation(operationId = "getSiResourceVersion", summary = "Get specific immutable version snapshot",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.getVersion", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.getVersion")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_READ)
    @GetMapping("/{id}/versions/{versionNo}")
    public ResponseEntity<ResultRecord<DdcSiVersionResult>> getVersion(
            @PathVariable("id") @Positive Long id,
            @PathVariable("versionNo") @Positive Long versionNo) {
        Long tenantId = requireTenantId();
        return noStore(ResultRecord.success(
                converter.toVersionResult(siResourceService.getVersion(tenantId, id, versionNo))));
    }

    @Operation(operationId = "listSiVersionReferences", summary = "Inspect outbound or inbound dependency references",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.listVersionReferences", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.listVersionReferences")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_RESOURCE_READ)
    @GetMapping("/{id}/versions/{versionNo}/references")
    public ResponseEntity<ResultRecord<List<DdcSiReferenceResult>>> listVersionReferences(
            @PathVariable("id") @Positive Long id,
            @PathVariable("versionNo") @Positive Long versionNo,
            @RequestParam(value = "direction", defaultValue = "OUTBOUND")
            @Pattern(regexp = "^(OUTBOUND|INBOUND)$") String direction) {
        Long tenantId = requireTenantId();
        List<DdcSiReferenceBO> refs = siResourceService.listReferences(tenantId, id, direction);
        return noStore(ResultRecord.success(converter.toReferenceResults(refs)));
    }

    @Operation(operationId = "upsertSiServiceBinding", summary = "Pin or switch SI version binding for an application scope",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.upsertBinding", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.upsertBinding")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_BINDING_WRITE)
    @PutMapping("/{id}/service-binding")
    public ResponseEntity<ResultRecord<DdcSiBindingResult>> upsertServiceBinding(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody DdcSiBindingCommand command,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        DdcSiBindingBO saved = siResourceService.bind(
                tenantId,
                command.getBizCode(),
                command.getNamespaceCode(),
                command.getEnvCode(),
                command.getAppCode(),
                command.getKind(),
                command.getResourceKey(),
                command.getPinnedVersionNo(),
                resolveActor(authentication)
        );
        return noStore(ResultRecord.success(converter.toBindingResult(saved)));
    }

    @Operation(operationId = "unbindSiServiceBinding", summary = "Remove SI version pinning for an application scope",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.unbindBinding", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.unbindBinding")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_BINDING_WRITE)
    @DeleteMapping("/{id}/service-binding")
    public ResponseEntity<Void> unbindServiceBinding(
            @PathVariable("id") @Positive Long id,
            @RequestParam("bizCode") @NotBlank String bizCode,
            @RequestParam("namespaceCode") @NotBlank String namespaceCode,
            @RequestParam("envCode") @NotBlank String envCode,
            @RequestParam("appCode") @NotBlank String appCode,
            @RequestParam("kind") @NotNull DdcSiKindEnum kind,
            @RequestParam("resourceKey") @NotBlank String resourceKey,
            Authentication authentication) {
        Long tenantId = requireTenantId();
        siResourceService.unbind(tenantId, bizCode, namespaceCode, envCode, appCode,
                kind, resourceKey, resolveActor(authentication));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @Operation(operationId = "reindexSiResource", summary = "Trigger asynchronous semantic vector reindex",
            security = @SecurityRequirement(name = "tianshuBearerAuth"))
    @Parameters({@Parameter(name = TENANT_HEADER, in = ParameterIn.HEADER, required = true)})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.siResource.reindex", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.siResource.reindex")
    @RequiresPermissions(codes = DdcPermissionCodes.SI_INDEX_MANAGE)
    @PostMapping("/{id}/reindex")
    public ResponseEntity<Void> triggerReindex(
            @PathVariable("id") @Positive Long id,
            @RequestParam(value = "versionId", required = false) @Positive Long versionId) {
        Long tenantId = requireTenantId();
        siResourceService.triggerReindex(tenantId, id, versionId);
        return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private Long requireTenantId() {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        if (tenantId == null || tenantId <= 0L) {
            throw new CommonException(56403, "TIANSHU_SI_FORBIDDEN", "Authenticated Tianshu tenant context is required");
        }
        return tenantId;
    }

    private String resolveActor(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !authentication.getName().isBlank()) {
            return "user:" + authentication.getName().trim();
        }
        String mdcUser = MDC.get("userId");
        if (mdcUser != null && !mdcUser.isBlank()) {
            return mdcUser.trim();
        }
        throw new CommonException(56403, "TIANSHU_SI_FORBIDDEN", "Authenticated Tianshu operator is required");
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
