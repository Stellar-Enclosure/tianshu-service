package top.egon.cola.component.tianshu.admin.controller.config;

import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.core.pojo.PageResultRecord;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishTaskQueryRequest;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskResultConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishResultVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishTaskResult;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishTaskQueryService;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tianshu/publish-tasks")
@Tag(name = "tianshu-admin-tianshu-publish-task-controller", description = "DdcPublishTaskController 管理接口组")
@EgonApiCatalog(
        businessDomainCode = "xingyuan",
        businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin",
        entityDomainName = "Dynamic Config Center 管理实体域",
        interfaceGroupCode = "tianshu"
)
public class DdcPublishTaskController {

    private final DdcPublishTaskRepository publishTaskRepository;

    private final DdcPublishService publishService;

    private final DdcPublishTaskQueryService publishTaskQueryService;
    private final DdcPublishTaskResultConverter resultConverter;

    public DdcPublishTaskController(
            DdcPublishTaskRepository publishTaskRepository,
            DdcPublishService publishService,
            DdcPublishTaskQueryService publishTaskQueryService,
            DdcPublishTaskResultConverter resultConverter) {
        this.publishTaskRepository = publishTaskRepository;
        this.publishService = publishService;
        this.publishTaskQueryService = publishTaskQueryService;
        this.resultConverter = resultConverter;
    }

    @Operation(operationId = "tianshu.ddcPublishTaskController.list")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.publishTask.list", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.publishTask.list")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping
    public ResultRecord<List<DdcPublishTaskResult>> list() {
        return ResultRecord.success(resultConverter.toTargetList(publishTaskRepository.findAll()));
    }

    @Operation(operationId = "tianshu.ddcPublishTaskController.page")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.publishTask.page", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.publishTask.page")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping("/page")
    public PageResultRecord<DdcPublishTaskResult> page(
            DdcPublishTaskQueryRequest request,
            PageQuery pageQuery
    ) {
        DdcPageResult<DdcPublishTaskBO> page = publishTaskQueryService.page(request, pageQuery);
        return PageResultRecord.success(resultConverter.toTargetList(page.getRecords()), page.getTotal(),
                page.getPageNo(), page.getPageSize());
    }

    @Operation(operationId = "tianshu.ddcPublishTaskController.detail")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.publishTask.detail", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.publishTask.detail")
    @RequiresPermissions(codes = DdcPermissionCodes.READ)
    @GetMapping("/{changeId}")
    public ResultRecord<DdcPublishTaskResult> detail(@PathVariable("changeId") String changeId) {
        return ResultRecord.success(publishTaskRepository.findByChangeId(changeId)
                .map(resultConverter::toTarget).orElse(null));
    }

    @Operation(operationId = "tianshu.ddcPublishTaskController.retry")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    @PermissionResource(key = "tianshu.api.publishTask.retry", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.publishTask.retry")
    @RequiresPermissions(codes = DdcPermissionCodes.PUBLISH)
    @PostMapping("/{changeId}/retry")
    public ResultRecord<DdcPublishResultVO> retry(
            @PathVariable("changeId") String changeId) {
        return ResultRecord.success(publishService.retry(changeId));
    }
}
