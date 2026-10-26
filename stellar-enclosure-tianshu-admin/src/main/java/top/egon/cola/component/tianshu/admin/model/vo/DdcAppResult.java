package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DdcAppResult {
    @PermissionResource(key = "tianshu.field.DdcAppResult.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @JsonProperty("id") private String publicId;
    @PermissionResource(key = "tianshu.field.DdcAppResult.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String appCode;
    @PermissionResource(key = "tianshu.field.DdcAppResult.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String bizCode;
    @PermissionResource(key = "tianshu.field.DdcAppResult.appName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String appName;
    @PermissionResource(key = "tianshu.field.DdcAppResult.owner", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.owner")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String owner;
    @PermissionResource(key = "tianshu.field.DdcAppResult.description", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.description")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String description;
    @PermissionResource(key = "tianshu.field.DdcAppResult.enabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.enabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Boolean enabled;
    @PermissionResource(key = "tianshu.field.DdcAppResult.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @PermissionResource(key = "tianshu.field.DdcAppResult.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
}
