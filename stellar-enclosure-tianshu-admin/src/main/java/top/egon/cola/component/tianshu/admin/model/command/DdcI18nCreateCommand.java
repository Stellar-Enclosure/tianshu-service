package top.egon.cola.component.tianshu.admin.model.command;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record DdcI18nCreateCommand(
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.bizCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 128) String bizCode,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.namespaceCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.namespaceCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 128) String namespaceCode,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.env", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.env")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 32) String env,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.appCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 128) String appCode,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 128) String pageCode,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.name", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.name")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 128) String name,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.defaultLocale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.defaultLocale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 64) String defaultLocale,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.translations", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.translations")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotNull Map<String, Map<String, String>> translations,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.catalogScopeId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogScopeId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String catalogScopeId,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.catalogActivationVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String catalogActivationVersion,
        @PermissionResource(key = "tianshu.field.DdcI18nCreateCommand.reviewedSourceHashes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.reviewedSourceHashes")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotNull Map<String, String> reviewedSourceHashes) {
    public DdcI18nCreateCommand(String bizCode, String namespaceCode, String env, String appCode, String pageCode,
            String name, String defaultLocale, Map<String, Map<String, String>> translations) {
        this(bizCode, namespaceCode, env, appCode, pageCode, name, defaultLocale, translations, null, null, null);
    }
}
