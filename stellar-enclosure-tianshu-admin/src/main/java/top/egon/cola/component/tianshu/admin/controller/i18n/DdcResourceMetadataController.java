package top.egon.cola.component.tianshu.admin.controller.i18n;

import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.core.pojo.PageResultRecord;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcResourceMetadataResult;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcResourceMetadataService;
import top.egon.cola.component.tianshu.admin.support.DdcAdminPageSupport;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;

@RestController
@RequestMapping(path = "/api/v1/tianshu/resource-metadata", produces = MediaType.APPLICATION_JSON_VALUE)
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "tianshuBearerAuth")
@Tag(name = "Tianshu Resource Metadata", description = "指定激活版本的只读资源与权限来源索引")
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域", entityDomainCode = "tianshu-admin",
        entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@RequiredArgsConstructor
public class DdcResourceMetadataController {
    @Qualifier("ddcResourceMetadataService") private final DdcResourceMetadataService service;

    @PermissionResource(key = "tianshu.api.resourceMetadata.search", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.resourceMetadata.search")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping
    @Operation(operationId = "pageResourceMetadata", summary = "按来源与权限字符浏览只读资源元数据")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<PageResultRecord<DdcResourceMetadataResult>> search(@RequestParam String bizCode, @RequestParam String namespaceCode,
            @RequestParam String env, @RequestParam String appCode, @RequestParam String catalogScopeId, @RequestParam String catalogActivationVersion,
            @RequestParam(required = false) String producerId, @RequestParam(required = false) String metadataKind,
            @RequestParam(required = false) String resourceType, @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String permissionCode, @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(DdcAdminPageSupport.result(service.search(
                new DdcI18nScope(bizCode, namespaceCode, env, appCode), catalogScopeId, catalogActivationVersion,
                new DdcResourceMetadataQuery(producerId, metadataKind, resourceType, lifecycle, permissionCode, query), new PageQuery(pageNo, pageSize))));
    }
}
