package top.egon.cola.component.tianshu.admin.controller.list;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.model.command.DdcListSelectionCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcListTemplateCommand;
import top.egon.cola.component.tianshu.admin.model.vo.DdcListColumnResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcListTemplateResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcListViewResult;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.component.tianshu.admin.service.list.DdcListTemplateService;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

@RestController
@RequestMapping(path = "/api/v1/tianshu/list-views/{pageKey}/{listKey}", produces = MediaType.APPLICATION_JSON_VALUE)
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域", entityDomainCode = "tianshu-admin",
        entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@RequiredArgsConstructor
public class DdcListTemplateController {
    @Qualifier("ddcListTemplateService") private final DdcListTemplateService templates;

    @PermissionResource(key = "tianshu.api.listTemplate.load", type = ResourceType.API, labelPage = "tianshu.permissions", labelKey = "api.listTemplate.load")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcListViewResult>> load(@PathVariable String pageKey, @PathVariable String listKey,
            @RequestParam String catalogVersion, @RequestParam(defaultValue = "false") boolean includeSystemMetadata) {
        var view = templates.load(pageKey, listKey, catalogVersion, includeSystemMetadata);
        var result = new DdcListViewResult(view.definitionDigest(), view.catalogVersion(), view.columns().stream()
                .map(column -> new DdcListColumnResult(column.columnKey(), column.labelPage(), column.labelKey())).toList(),
                view.templates().stream().map(DdcListTemplateController::result).toList(), view.selectedTemplateId(), view.visibleColumns(),
                view.mayManageSystem(), view.mayManagePersonal());
        return noStore(result);
    }

    @PermissionResource(key = "tianshu.api.listTemplate.saveSystem", type = ResourceType.API, labelPage = "tianshu.permissions", labelKey = "api.listTemplate.saveSystem")
    @RequiresPermissions(codes = {DdcPermissionCodes.READ, DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM})
    @PostMapping(path = "/system", consumes = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcListTemplateResult>> saveSystem(@PathVariable String pageKey, @PathVariable String listKey,
            @Valid @RequestBody DdcListTemplateCommand command) { return save(pageKey, listKey, "SYSTEM", command); }

    @PermissionResource(key = "tianshu.api.listTemplate.savePersonal", type = ResourceType.API, labelPage = "tianshu.permissions", labelKey = "api.listTemplate.savePersonal")
    @RequiresPermissions(codes = {DdcPermissionCodes.READ, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})
    @PostMapping(path = "/personal", consumes = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcListTemplateResult>> savePersonal(@PathVariable String pageKey, @PathVariable String listKey,
            @Valid @RequestBody DdcListTemplateCommand command) { return save(pageKey, listKey, "PERSONAL", command); }

    @PermissionResource(key = "tianshu.api.listTemplate.deleteSystem", type = ResourceType.API, labelPage = "tianshu.permissions", labelKey = "api.listTemplate.deleteSystem")
    @RequiresPermissions(codes = {DdcPermissionCodes.READ, DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM})
    @DeleteMapping("/system/{id}")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<Boolean>> deleteSystem(@PathVariable String pageKey, @PathVariable String listKey,
            @PathVariable String id, @RequestParam String catalogVersion, @RequestParam String version) {
        templates.delete(pageKey, listKey, catalogVersion, "SYSTEM", id, version); return noStore(true);
    }

    @PermissionResource(key = "tianshu.api.listTemplate.deletePersonal", type = ResourceType.API, labelPage = "tianshu.permissions", labelKey = "api.listTemplate.deletePersonal")
    @RequiresPermissions(codes = {DdcPermissionCodes.READ, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})
    @DeleteMapping("/personal/{id}")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<Boolean>> deletePersonal(@PathVariable String pageKey, @PathVariable String listKey,
            @PathVariable String id, @RequestParam String catalogVersion, @RequestParam String version) {
        templates.delete(pageKey, listKey, catalogVersion, "PERSONAL", id, version); return noStore(true);
    }

    @PermissionResource(key = "tianshu.api.listTemplate.select", type = ResourceType.API, labelPage = "tianshu.permissions", labelKey = "api.listTemplate.select")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @PostMapping(path = "/selection", consumes = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<Boolean>> select(@PathVariable String pageKey, @PathVariable String listKey,
            @Valid @RequestBody DdcListSelectionCommand command) {
        templates.select(pageKey, listKey, command.catalogVersion(), command.definitionDigest(), command.templateId()); return noStore(true);
    }

    private ResponseEntity<ResultRecord<DdcListTemplateResult>> save(String pageKey, String listKey, String type, DdcListTemplateCommand command) {
        return noStore(result(templates.save(pageKey, listKey, command.catalogVersion(), command.definitionDigest(), type,
                command.id(), command.expectedVersion(), command.name(), command.columnKeys(), command.defaultTemplate())));
    }
    private static DdcListTemplateResult result(DdcListTemplateService.TemplateView view) {
        return new DdcListTemplateResult(view.id(), view.type(), view.name(), view.defaultTemplate(), view.version(), view.columnKeys());
    }
    private static <T> ResponseEntity<ResultRecord<T>> noStore(T value) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(ResultRecord.success(value));
    }
}
