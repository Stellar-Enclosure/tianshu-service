package top.egon.cola.component.tianshu.admin.controller.i18n;

import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAdminUiI18nBootstrap;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAdminUiI18nInitializeResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublicI18nBundleResult;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nCatalog;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nService;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;

@RestController
@RequestMapping(path = "/api/v1/tianshu/i18n", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tianshu Console Languages", description = "管理台固定作用域语言包与显式初始化")
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin", entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@RequiredArgsConstructor
public class DdcAdminUiI18nController {
    @Qualifier("ddcAdminUiI18nService") private final DdcAdminUiI18nService service;
    @Qualifier("ddcAdminUiI18nCatalog") private final DdcAdminUiI18nCatalog catalog;

    @PermissionResource(key = "tianshu.api.adminUiI18n.bootstrap", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.adminUiI18n.bootstrap")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping("/admin/bootstrap")
    @Operation(operationId = "getAdminUiI18nBootstrap", summary = "获取管理台语言目录与固定作用域")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "tianshuBearerAuth")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcAdminUiI18nBootstrap>> bootstrap() {
        return noStore(service.bootstrap());
    }

    @PermissionResource(key = "tianshu.api.adminUiI18n.bundle", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.adminUiI18n.bundle")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping("/admin/bundle")
    @Operation(operationId = "getAdminUiI18nBundle", summary = "读取企业已发布管理台语言包")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "tianshuBearerAuth")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcI18nBundleResult>> bundle(@RequestParam String catalogScopeId, @RequestParam String catalogActivationVersion,
            @RequestParam String pageCode, @RequestParam String locale) {
        return noStore(service.bundle(catalogScopeId, catalogActivationVersion, pageCode, locale));
    }

    @PermissionResource(key = "tianshu.api.adminUiI18n.initialize", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.adminUiI18n.initialize")
    @RequiresPermissions(codes = {DdcPermissionCodes.READ, DdcPermissionCodes.WRITE, DdcPermissionCodes.PUBLISH})
    @PostMapping("/admin/initialize")
    @Operation(operationId = "initializeAdminUiI18n", summary = "已停用的管理台特殊初始化入口")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "tianshuBearerAuth")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcAdminUiI18nInitializeResult>> initialize() {
        return noStore(service.initialize());
    }

    @PermissionResource(key = "tianshu.api.adminUiI18n.publicBundle", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.adminUiI18n.publicBundle", access = ResourceAccess.PUBLIC)
    @GetMapping("/public/bundle")
    @Operation(operationId = "getPublicUiI18nBundle", summary = "读取公开登录启动文案")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcPublicI18nBundleResult>> publicBundle(@RequestParam String locale) {
        // This endpoint reads only the packaged auth catalog and never enters a tenant persistence context.
        return noStore(catalog.publicBundle(locale));
    }

    private <T> ResponseEntity<ResultRecord<T>> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(ResultRecord.success(body));
    }
}
