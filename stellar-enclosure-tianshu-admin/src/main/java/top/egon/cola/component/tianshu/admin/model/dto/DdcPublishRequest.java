package top.egon.cola.component.tianshu.admin.model.dto;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DdcPublishRequest {

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.changeId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private String changeId;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private String bizCode;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private String env;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private String appCode;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.resourceName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private String resourceName;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.content", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.content")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private String content;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.format", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.format")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private String format;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.expectedVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.expectedVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private Long expectedVersion;

    @PermissionResource(key = "tianshu.field.DdcPublishRequest.timeoutMs", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.timeoutMs")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
    private Long timeoutMs;
}
