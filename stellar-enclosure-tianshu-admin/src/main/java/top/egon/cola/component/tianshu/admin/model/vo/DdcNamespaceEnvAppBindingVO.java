package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

public record DdcNamespaceEnvAppBindingVO(
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.id", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.id")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String id,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.bizCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String bizCode,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.namespaceId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.namespaceId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String namespaceId,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.namespaceCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.namespaceCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String namespaceCode,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.env", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.env")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String env,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.appId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String appId,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.appCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String appCode,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.appName", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appName")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String appName,
        @PermissionResource(key = "tianshu.field.DdcNamespaceEnvAppBindingVO.enabled", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.enabled")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        boolean enabled
) {
}
