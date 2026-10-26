package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

public record DdcI18nPageSummary(
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.id", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.id")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String id,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.bizCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String bizCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.namespaceCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.namespaceCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String namespaceCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.env", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.env")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String env,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.appCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String appCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String pageCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.name", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.name")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String name,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.defaultLocale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.defaultLocale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String defaultLocale,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.revision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.revision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String revision,
        @PermissionResource(key = "tianshu.field.DdcI18nPageSummary.publishedVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publishedVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String publishedVersion) {
}
