package top.egon.cola.component.tianshu.admin.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import java.util.Map;

/** Read-only login text; it cannot carry tenant catalog identity or translation overrides. */
public record DdcPublicI18nBundleResult(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @PermissionResource(key = "tianshu.field.DdcPublicI18nBundleResult.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode", access = ResourceAccess.PUBLIC)
        String pageCode,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @PermissionResource(key = "tianshu.field.DdcPublicI18nBundleResult.locale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.locale", access = ResourceAccess.PUBLIC)
        String locale,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @PermissionResource(key = "tianshu.field.DdcPublicI18nBundleResult.defaultLocale", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.defaultLocale", access = ResourceAccess.PUBLIC)
        String defaultLocale,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @PermissionResource(key = "tianshu.field.DdcPublicI18nBundleResult.version", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.version", access = ResourceAccess.PUBLIC)
        String version,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @PermissionResource(key = "tianshu.field.DdcPublicI18nBundleResult.messages", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.messages", access = ResourceAccess.PUBLIC)
        Map<String, String> messages) {
    public DdcPublicI18nBundleResult {
        if (!"tianshu.auth".equals(pageCode)) throw new IllegalArgumentException("Only login source messages may be public");
        messages = Map.copyOf(messages);
    }
}
