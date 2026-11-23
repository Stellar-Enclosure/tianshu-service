package top.egon.cola.component.tianshu.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import java.time.Instant;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPhaseEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(name = "DdcOperationLogSummaryVO")
public class DdcOperationLogSummaryVO {
    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.eventId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.eventId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String eventId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.operationId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.operationId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String operationId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.occurredAt", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.occurredAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant occurredAt;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.sourceService", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.source")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String sourceService;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.moduleCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.moduleCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String moduleCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.actionCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.actionCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String actionCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.resourceType", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.resourceType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String resourceType;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    @Schema(nullable = true)
    private String resourceId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.actorType", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.actorType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String actorType;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.actorId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.actorId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private String actorId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.actorName", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.actorName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    @Schema(nullable = true)
    private String actorName;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.phase", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.phase")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private OperationLogPhaseEnum phase;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.outcome", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.outcome")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private OperationLogOutcomeEnum outcome;

    @PermissionResource(key = "tianshu.field.DdcOperationLogSummaryVO.routePattern", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.routePattern")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    @Schema(nullable = true)
    private String routePattern;

}
