package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import java.util.List;

public record DdcAdminUiI18nBootstrap(
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nBootstrap.scope", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.scope")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        DdcI18nScope scope,
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nBootstrap.defaultLocale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.defaultLocale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String defaultLocale,
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nBootstrap.locales", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.locales")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        List<String> locales,
                                    @PermissionResource(key = "tianshu.field.DdcAdminUiI18nBootstrap.pages", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pages")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        List<Page> pages,
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nBootstrap.canInitialize", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.canInitialize")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        boolean canInitialize) {
    public record Page(
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nBootstrap.Page.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String pageCode,
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nBootstrap.Page.name", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.name")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String name) { }
}
