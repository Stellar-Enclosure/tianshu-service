package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcPublishTaskResult {

    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @JsonProperty("id")
    @NotNull
    private String publicId;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.changeId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String changeId;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.configId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.configId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String configId;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String bizCode;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String appCode;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String env;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.namespace", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.namespace")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String namespace;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.resourceName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceName;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.targetVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long targetVersion;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.publishMode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.publishMode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String publishMode;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.resourceChecksum", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceChecksum")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceChecksum;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.attemptCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.attemptCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer attemptCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.dispatchedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.dispatchedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime dispatchedAt;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.completedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.completedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime completedAt;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.failureStage", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.failureStage")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String failureStage;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.status", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.status")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String status;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.targetCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer targetCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ackCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.ackCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer ackCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.failedCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.failedCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer failedCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ignoredCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.ignoredCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer ignoredCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.timeoutCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.timeoutCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer timeoutCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.timeoutMs", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.timeoutMs")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long timeoutMs;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.operator", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.operator")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String operator;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.errorMessage", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.errorMessage")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String errorMessage;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
}
