package top.egon.cola.component.tianshu.admin.model.dto;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

public record DdcNamespaceEnvAppBindingRequest(
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingRequest.bizCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        String bizCode,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingRequest.namespaceCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.namespaceCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        String namespaceCode,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingRequest.env", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.env")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        String env,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingRequest.appCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        String appCode,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingRequest.enabled", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.enabled")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        Boolean enabled
) {
}
