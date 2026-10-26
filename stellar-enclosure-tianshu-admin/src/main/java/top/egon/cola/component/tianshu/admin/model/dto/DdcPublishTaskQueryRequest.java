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
public class DdcPublishTaskQueryRequest {

    @PermissionResource(key = "tianshu.field.DdcPublishTaskQueryRequest.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    private String bizCode;

    @PermissionResource(key = "tianshu.field.DdcPublishTaskQueryRequest.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    private String env;

    @PermissionResource(key = "tianshu.field.DdcPublishTaskQueryRequest.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    private String appCode;

    @PermissionResource(key = "tianshu.field.DdcPublishTaskQueryRequest.status", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.status")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    private String status;

    @PermissionResource(key = "tianshu.field.DdcPublishTaskQueryRequest.changeId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    private String changeId;
}
