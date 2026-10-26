package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class DdcConfigVO {

    @PermissionResource(key = "tianshu.field.DdcConfigVO.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String id;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String bizCode;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String env;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String appCode;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.visibleNamespaces", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.visibleNamespaces")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private List<String> visibleNamespaces = List.of();
    @PermissionResource(key = "tianshu.field.DdcConfigVO.resourceName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceName;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.content", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.content")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String content;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.format", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.format")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String format;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.currentVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.currentVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long currentVersion;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.description", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.description")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String description;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.enabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.enabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Boolean enabled;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.deleted", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.deleted")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Boolean deleted;
    @PermissionResource(key = "tianshu.field.DdcConfigVO.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
}
