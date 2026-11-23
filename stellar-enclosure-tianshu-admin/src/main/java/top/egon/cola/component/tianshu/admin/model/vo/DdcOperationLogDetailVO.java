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
import com.fasterxml.jackson.databind.node.ObjectNode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(name = "DdcOperationLogDetailVO")
public class DdcOperationLogDetailVO {
    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.eventId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.eventId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String eventId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.operationId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.operationId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String operationId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.schemaVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.schemaVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private Integer schemaVersion;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.sourceService", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.source")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String sourceService;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.moduleCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.moduleCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String moduleCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.occurredAt", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.occurredAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant occurredAt;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.recordedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.recordedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant recordedAt;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.actionCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.actionCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String actionCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.resourceType", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.resourceType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String resourceType;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String resourceId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String bizCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.namespaceCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.namespaceCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String namespaceCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.env", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String env;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String appCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.actorType", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.actorType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String actorType;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.actorId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.actorId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String actorId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.actorName", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.actorName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String actorName;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.triggerType", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.triggerType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String triggerType;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.phase", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.phase")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private OperationLogPhaseEnum phase;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.outcome", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "table.outcome")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private OperationLogOutcomeEnum outcome;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.reasonCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.reasonCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String reasonCode;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.httpMethod", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.httpMethod")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String httpMethod;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.requestUrl", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.requestUrl")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String requestUrl;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.routePattern", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.routePattern")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String routePattern;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.handlerMethod", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.handlerMethod")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private String handlerMethod;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.traceId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.traceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String traceId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.requestId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.requestId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    @Schema(nullable = true)
    private String requestId;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.durationMs", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.durationMs")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private Long durationMs;

    @PermissionResource(key = "tianshu.field.DdcOperationLogDetailVO.details", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "detail.details")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = {"TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL"}))
    private ObjectNode details;

}
