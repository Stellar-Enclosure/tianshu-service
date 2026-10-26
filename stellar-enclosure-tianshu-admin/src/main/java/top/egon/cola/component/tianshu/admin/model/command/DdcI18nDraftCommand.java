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

public record DdcI18nDraftCommand(
        @PermissionResource(key = "tianshu.field.DdcI18nDraftCommand.expectedRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.expectedRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String expectedRevision,
        @PermissionResource(key = "tianshu.field.DdcI18nDraftCommand.name", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.name")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 128) String name,
        @PermissionResource(key = "tianshu.field.DdcI18nDraftCommand.defaultLocale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.defaultLocale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Size(max = 64) String defaultLocale,
        @PermissionResource(key = "tianshu.field.DdcI18nDraftCommand.translations", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.translations")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotNull Map<String, Map<String, String>> translations,
        @PermissionResource(key = "tianshu.field.DdcI18nDraftCommand.catalogActivationVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String catalogActivationVersion,
        @PermissionResource(key = "tianshu.field.DdcI18nDraftCommand.reviewedSourceHashes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.reviewedSourceHashes")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        @NotNull Map<String, String> reviewedSourceHashes) {
    public DdcI18nDraftCommand(String expectedRevision, String name, String defaultLocale, Map<String, Map<String, String>> translations) {
        this(expectedRevision, name, defaultLocale, translations, null, null);
    }
}
