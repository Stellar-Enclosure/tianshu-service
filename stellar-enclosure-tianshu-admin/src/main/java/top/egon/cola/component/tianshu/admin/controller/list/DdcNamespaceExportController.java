package top.egon.cola.component.tianshu.admin.controller.list;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.tianshu.admin.model.command.DdcNamespaceExportCommand;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.component.tianshu.admin.service.list.DdcNamespaceExportService;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/tianshu/namespaces")
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域", entityDomainCode = "tianshu-admin",
        entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@RequiredArgsConstructor
public class DdcNamespaceExportController {
    @Qualifier("ddcNamespaceExportService") private final DdcNamespaceExportService export;

    @PermissionResource(key = "tianshu.api.namespace.export", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.namespace.export")
    @RequiresPermissions(codes = {DdcPermissionCodes.READ, DdcPermissionCodes.NAMESPACE_EXPORT})
    @PostMapping(path = "/export", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @Operation(operationId = "tianshu.ddcNamespaceController.export", summary = "按当前列表模板导出命名空间")
    public ResponseEntity<byte[]> export(@Valid @RequestBody DdcNamespaceExportCommand command) throws IOException {
        byte[] file = export.export(command);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"namespaces.xlsx\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }
}
