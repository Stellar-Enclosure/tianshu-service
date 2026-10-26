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
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcInstanceResult {

    @PermissionResource(key = "tianshu.field.DdcInstanceResult.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @JsonProperty("id")
    @NotNull
    private String publicId;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.instanceId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.instanceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String instanceId;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String bizCode;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String appCode;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String env;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.namespace", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.namespace")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String namespace;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.host", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.host")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String host;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.port", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.port")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer port;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.pid", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.pid")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String pid;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.sdkVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.sdkVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String sdkVersion;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.leaseId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.leaseId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String leaseId;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.leaseExpireAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.leaseExpireAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime leaseExpireAt;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.resourceServerId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceServerId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceServerId;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.resourceVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long resourceVersion;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.credentialId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.credentialId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String credentialId;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.admissionExpiresAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.admissionExpiresAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime admissionExpiresAt;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.status", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.status")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String status;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.lastHeartbeatAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.lastHeartbeatAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime lastHeartbeatAt;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
    @PermissionResource(key = "tianshu.field.DdcInstanceResult.runtimeMetadata", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.runtimeMetadata")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @Builder.Default private Map<String, String> runtimeMetadata = Map.of();
}
