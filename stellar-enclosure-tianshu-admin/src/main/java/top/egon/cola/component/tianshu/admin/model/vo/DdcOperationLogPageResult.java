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
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(name = "DdcOperationLogPageResult")
public class DdcOperationLogPageResult {
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageResult.from", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.from")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant from;

    @PermissionResource(key = "tianshu.field.DdcOperationLogPageResult.to", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.to")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant to;

    @PermissionResource(key = "tianshu.field.DdcOperationLogPageResult.records", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "title")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    private List<DdcOperationLogSummaryVO> records;

    @PermissionResource(key = "tianshu.field.DdcOperationLogPageResult.nextCursor", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "pagination.next")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            plainRead = @PermissionGroup(mode = PermissionMode.ALL, codes = "TIANSHU_OPERATION_LOG_READ"))
    @Schema(nullable = true)
    private CursorVO nextCursor;

    /** Exact last tuple; never round through client milliseconds. */
    @Schema(name = "DdcOperationLogCursorVO")
    public record CursorVO(
            @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
            @Schema(type = "string", format = "date-time")
            @PermissionResource(key = "tianshu.field.DdcOperationLogCursorVO.beforeTime", type = ResourceType.FIELD,
                    labelPage = "tianshu.operationLogs", labelKey = "table.occurredAt")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ")) Instant beforeTime,
            @PermissionResource(key = "tianshu.field.DdcOperationLogCursorVO.beforeId", type = ResourceType.FIELD,
                    labelPage = "tianshu.operationLogs", labelKey = "detail.eventId")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ")) String beforeId) { }
}
