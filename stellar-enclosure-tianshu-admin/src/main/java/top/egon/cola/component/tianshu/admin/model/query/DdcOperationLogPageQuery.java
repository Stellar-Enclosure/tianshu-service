package top.egon.cola.component.tianshu.admin.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

/** Public query inputs deliberately contain no tenant or offset/count. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(name = "DdcOperationLogPageQuery")
public class DdcOperationLogPageQuery {
    /** Length checks run after outer whitespace normalization; raw control checks always run. */
    public interface Normalized { }

    @Schema(type = "string", format = "date-time", description = "Inclusive RFC3339 offset time, at most six fractional digits; paired with to")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.from", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.from")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String from;
    @Schema(type = "string", format = "date-time", description = "Exclusive RFC3339 offset time; maximum window 31 days")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.to", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.to")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String to;
    @Schema(type = "string", format = "date-time", description = "Cursor time inside the retained explicit from/to window; paired with beforeId")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.beforeTime", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.beforeTime")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String beforeTime;
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.beforeId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.beforeId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String beforeId;
    @NotNull @Min(1) @Max(100)
    @Builder.Default
    @Schema(defaultValue = "50", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.limit", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.limit")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private Integer limit = 50;
    @Size(max = 96, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.actionCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.actionCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String actionCode;
    @Size(max = 512, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.actorId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.actorId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String actorId;
    @Size(max = 64, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.resourceType", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.resourceType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String resourceType;
    @Size(max = 128, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String resourceId;
    @Size(max = 128, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String bizCode;
    @Size(max = 128, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.namespaceCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.namespaceCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String namespaceCode;
    @Size(max = 32, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.env", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String env;
    @Size(max = 128, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    private String appCode;
    @Size(max = 32, groups = Normalized.class)
    @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*")
    @PermissionResource(key = "tianshu.field.DdcOperationLogPageQuery.outcome", type = ResourceType.FIELD,
            labelPage = "tianshu.operationLogs", labelKey = "filter.outcome")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = "TIANSHU_OPERATION_LOG_READ"))
    @Schema(allowableValues = {"ACCEPTED", "SUCCEEDED", "FAILED", "PARTIAL_SUCCESS", "UNKNOWN", "REJECTED", "ROLLED_BACK"})
    private String outcome;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message = "window and cursor fields must be paired; a cursor requires explicit anchors")
    public boolean isPaired() {
        return (from == null) == (to == null) && (beforeTime == null) == (beforeId == null)
                && (beforeTime == null || from != null);
    }
}
