package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionDefinition;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Stable permission meanings owned by the Tianshu Java producer. */
@PermissionDefinition(code = DdcPermissionCodes.READ, meaning = "Read tenant-scoped Tianshu configuration and metadata",
        labelPage = "tianshu.permissions", labelKey = "permission.read")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read tenant-scoped Tianshu configuration and metadata"))
@PermissionDefinition(code = DdcPermissionCodes.WRITE, meaning = "Edit tenant-scoped Tianshu configuration and metadata",
        labelPage = "tianshu.permissions", labelKey = "permission.write")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.write", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Edit tenant-scoped Tianshu configuration and metadata"))
@PermissionDefinition(code = DdcPermissionCodes.PUBLISH, meaning = "Publish configuration and reviewed translations",
        labelPage = "tianshu.permissions", labelKey = "permission.publish")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.publish", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Publish configuration and reviewed translations"))
@PermissionDefinition(code = DdcPermissionCodes.CACHE, meaning = "Inspect and rebuild tenant-scoped configuration caches",
        labelPage = "tianshu.permissions", labelKey = "permission.cache")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.cache", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Inspect and rebuild tenant-scoped configuration caches"))
@PermissionDefinition(code = DdcPermissionCodes.I18N_READ, meaning = "Read published tenant-scoped language bundles",
        labelPage = "tianshu.permissions", labelKey = "permission.i18n.read")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.i18n.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read published tenant-scoped language bundles"))
@PermissionDefinition(code = DdcPermissionCodes.AI_READ, meaning = "Read tenant-scoped AI resources and history",
        labelPage = "tianshu.permissions", labelKey = "permission.ai.read")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.ai.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read tenant-scoped AI resources and history"))
@PermissionDefinition(code = DdcPermissionCodes.AI_WRITE, meaning = "Edit AI resource drafts",
        labelPage = "tianshu.permissions", labelKey = "permission.ai.write")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.ai.write", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Edit AI resource drafts"))
@PermissionDefinition(code = DdcPermissionCodes.AI_PUBLISH, meaning = "Publish or roll back AI resource versions and control automatic optimization",
        labelPage = "tianshu.permissions", labelKey = "permission.ai.publish")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.ai.publish", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Publish or roll back AI resource versions and control automatic optimization"))
@PermissionDefinition(code = DdcPermissionCodes.AI_REPORT, meaning = "Report usage of authorized AI resources",
        labelPage = "tianshu.permissions", labelKey = "permission.ai.report")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.ai.report", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Report usage of authorized AI resources"))
public final class DdcPermissionCodes {

    public static final String READ = "TIANSHU_READ";
    public static final String WRITE = "TIANSHU_WRITE";
    public static final String PUBLISH = "TIANSHU_PUBLISH";
    public static final String CACHE = "TIANSHU_CACHE";
    public static final String I18N_READ = "TIANSHU_I18N_READ";
    public static final String AI_READ = "TIANSHU_AI_READ";
    public static final String AI_WRITE = "TIANSHU_AI_WRITE";
    public static final String AI_PUBLISH = "TIANSHU_AI_PUBLISH";
    public static final String AI_REPORT = "TIANSHU_AI_REPORT";

    private DdcPermissionCodes() { }
}
