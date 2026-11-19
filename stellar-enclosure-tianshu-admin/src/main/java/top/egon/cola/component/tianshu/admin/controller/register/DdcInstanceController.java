package top.egon.cola.component.tianshu.admin.controller.register;

import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.core.pojo.PageResultRecord;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcInstanceWorkloadConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcInstanceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcInstanceWorkloadVO;
import top.egon.cola.component.tianshu.admin.service.lease.DdcInstanceAdminService;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;

import java.util.List;

@RestController("ddcInstanceController")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RequestMapping("/api/v1/tianshu/instances")
@Tag(name = "tianshu-admin-tianshu-instance-controller", description = "DdcInstanceController 管理接口组")
@EgonApiCatalog(
        businessDomainCode = "xingyuan",
        businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin",
        entityDomainName = "Dynamic Config Center 管理实体域",
        interfaceGroupCode = "tianshu"
)
public class DdcInstanceController {

    @Qualifier("ddcInstanceAdminService")
    private final DdcInstanceAdminService instanceAdminService;

    @Qualifier("ddcInstanceWorkloadConverterImpl")
    private final DdcInstanceWorkloadConverter workloadConverter;

    @Operation(operationId = "tianshu.ddcInstanceController.list")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.instance.list", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.instance.list")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping
    public ResultRecord<List<DdcInstanceWorkloadVO>> list(
            @RequestParam("bizCode") @NotBlank String bizCode,
            @RequestParam("env") @NotBlank String env,
            @RequestParam("appCode") @NotBlank String appCode) {
        return ResultRecord.success(workloadConverter.toTargetList(instanceAdminService.list(bizCode, env, appCode)));
    }

    @Operation(operationId = "tianshu.ddcInstanceController.page")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.instance.page", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.instance.page")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping("/page")
    public PageResultRecord<DdcInstanceWorkloadVO> page(
            @RequestParam("bizCode") @NotBlank String bizCode,
            @RequestParam("env") @NotBlank String env,
            @RequestParam("appCode") @NotBlank String appCode,
            PageQuery pageQuery
    ) {
        var page = instanceAdminService.page(bizCode, env, appCode, pageQuery);
        return PageResultRecord.success(workloadConverter.toTargetList(page.getRecords()), page.getTotal(),
                page.getPageNo(), page.getPageSize());
    }

    @Operation(operationId = "tianshu.ddcInstanceController.historyList")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.instance.historyList", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.instance.historyList")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping("/history")
    public ResultRecord<List<DdcInstanceResult>> historyList(
            @RequestParam("bizCode") @NotBlank String bizCode,
            @RequestParam("env") @NotBlank String env,
            @RequestParam("appCode") @NotBlank String appCode) {
        return ResultRecord.success(instanceAdminService.historyList(bizCode, env, appCode));
    }

    @Operation(operationId = "tianshu.ddcInstanceController.historyPage")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.instance.historyPage", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.instance.historyPage")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping("/history/page")
    public PageResultRecord<DdcInstanceResult> historyPage(
            @RequestParam("bizCode") @NotBlank String bizCode,
            @RequestParam("env") @NotBlank String env,
            @RequestParam("appCode") @NotBlank String appCode,
            PageQuery pageQuery) {
        var page = instanceAdminService.historyPage(bizCode, env, appCode, pageQuery);
        return PageResultRecord.success(page.getRecords(), page.getTotal(), page.getPageNo(), page.getPageSize());
    }
}
