package top.egon.cola.component.tianshu.admin.controller.i18n;

import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import io.swagger.v3.oas.annotations.Operation;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.security.management.DdcSourceImportAuthentication;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nSourceImportService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportReceipt;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest;

@RestController
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin", entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@RequestMapping(path = "/internal/v1/i18n/source-imports", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tianshu Permission Metadata", description = "受权 RBAC publisher 的不可变来源投影")
@RequiredArgsConstructor
public class DdcI18nSourceImportController {
    @Qualifier("ddcI18nSourceImportService") private final DdcI18nSourceImportService service;

    @PermissionResource(key = "tianshu.api.i18nSourceImport.importSource", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.i18nSourceImport.importSource",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = "TIANSHU_METADATA_IMPORT")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @Operation(operationId = "importPermissionMetadataSource", summary = "幂等导入 Permission Manifest 来源与中心 ID 映射")
    public ResponseEntity<ResultRecord<SourceImportReceipt>> importSource(Authentication authentication,
            @RequestBody SourceImportRequest request) {
        SourceImportReceipt result = service.importSource(DdcSourceImportAuthentication.identity(authentication), request);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ResultRecord.success(result));
    }

    @PermissionResource(key = "tianshu.api.i18nSourceImport.receipt", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.i18nSourceImport.receipt",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = "TIANSHU_METADATA_IMPORT")
    @GetMapping("/{operationId}")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @Operation(operationId = "getPermissionMetadataImportReceipt", summary = "重新鉴权后读取原始来源导入回执")
    public ResponseEntity<ResultRecord<SourceImportReceipt>> receipt(Authentication authentication, @PathVariable String operationId,
            @RequestParam String catalogScopeId, @RequestParam String tenantId, @RequestParam String businessCode,
            @RequestParam String applicationCode, @RequestParam String namespaceCode, @RequestParam String environment,
            @RequestParam String producerId) {
        CatalogScope scope = new CatalogScope(catalogScopeId, tenantId, businessCode, applicationCode, namespaceCode, environment);
        SourceImportReceipt result = service.receipt(DdcSourceImportAuthentication.identity(authentication), scope, producerId, operationId);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ResultRecord.success(result));
    }
}
