package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import java.util.Map;
import java.util.List;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;

public record DdcI18nBundleResult(
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String pageCode,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.locale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.locale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String locale,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.defaultLocale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.defaultLocale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String defaultLocale,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.version", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.version")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String version,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.messages", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.messages")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        Map<String, String> messages,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.catalogScope", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogScope")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        CatalogScope catalogScope,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.catalogActivationVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String catalogActivationVersion,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.sourceVersions", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.sourceVersions")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        Map<String, String> sourceVersions,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.publishedOverrideRevisions", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publishedOverrideRevisions")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        Map<String, String> publishedOverrideRevisions,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.diagnostics", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.diagnostics")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        List<Diagnostic> diagnostics) {
    public DdcI18nBundleResult(String pageCode, String locale, String defaultLocale, String version, Map<String, String> messages) {
        this(pageCode, locale, defaultLocale, version, messages, null, null, Map.of(), Map.of(), List.of());
    }
    public record Diagnostic(
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.Diagnostic.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String pageCode,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.Diagnostic.key", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.key")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String key,
        @PermissionResource(key = "tianshu.field.DdcI18nBundleResult.Diagnostic.reason", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.reason")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_READ", "TIANSHU_I18N_READ"},
                        mode = top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode.ANY))
        String reason) { }
}
