package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import java.util.Map;
import java.util.List;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;

public record DdcI18nPageResult(
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.id", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.id")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String id,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.bizCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String bizCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.namespaceCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.namespaceCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String namespaceCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.env", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.env")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String env,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.appCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String appCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String pageCode,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.name", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.name")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String name,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.defaultLocale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.defaultLocale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String defaultLocale,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.translations", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.translations")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, Map<String, String>> translations,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.revision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.revision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String revision,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.publishedVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publishedVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String publishedVersion,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.catalogScope", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogScope")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        CatalogScope catalogScope,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.catalogActivationVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String catalogActivationVersion,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.draftActivationVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.draftActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String draftActivationVersion,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.sourceDefaults", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.sourceDefaults")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, Map<String, String>> sourceDefaults,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.sourceHashes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.sourceHashes")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, String> sourceHashes,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.draftSourceHashes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.draftSourceHashes")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, String> draftSourceHashes,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.publishedTranslations", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publishedTranslations")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, Map<String, String>> publishedTranslations,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.publishedSourceHashes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publishedSourceHashes")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, String> publishedSourceHashes,
        @PermissionResource(key = "tianshu.field.DdcI18nPageResult.diagnostics", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.diagnostics")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        List<DdcI18nBundleResult.Diagnostic> diagnostics) {
    public DdcI18nPageResult(String id, String bizCode, String namespaceCode, String env, String appCode, String pageCode,
            String name, String defaultLocale, Map<String, Map<String, String>> translations, String revision, String publishedVersion) {
        this(id, bizCode, namespaceCode, env, appCode, pageCode, name, defaultLocale, translations, revision, publishedVersion,
                null, null, null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
    }
}
