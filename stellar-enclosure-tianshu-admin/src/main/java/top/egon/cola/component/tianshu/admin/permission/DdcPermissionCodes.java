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
@PermissionDefinition(code = DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, meaning = "Manage system templates for the namespace list",
        labelPage = "tianshu.permissions", labelKey = "permission.namespace.template.system")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.namespace.template.system", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage namespace list system templates"),
                @LocalizedText(locale = "zh-CN", text = "管理命名空间列表系统模板")})
@PermissionDefinition(code = DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL, meaning = "Manage personal templates for the namespace list",
        labelPage = "tianshu.permissions", labelKey = "permission.namespace.template.personal")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.namespace.template.personal", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage personal namespace list templates"),
                @LocalizedText(locale = "zh-CN", text = "管理命名空间列表个人模板")})
@PermissionDefinition(code = DdcPermissionCodes.NAMESPACE_EXPORT, meaning = "Export authorized namespace list results",
        labelPage = "tianshu.permissions", labelKey = "permission.namespace.export")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.namespace.export", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Export namespace list results"),
                @LocalizedText(locale = "zh-CN", text = "导出命名空间列表")})
@PermissionDefinition(code = DdcPermissionCodes.SI_RESOURCE_READ, meaning = "Read Super-Intelligence catalog, versions, references and search",
        labelPage = "tianshu.permissions", labelKey = "permission.si.read")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.si.read", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Read SI resources and catalog"),
                @LocalizedText(locale = "zh-CN", text = "读取超级智能资产与目录")})
@PermissionDefinition(code = DdcPermissionCodes.SI_RESOURCE_WRITE, meaning = "Create and update Super-Intelligence drafts",
        labelPage = "tianshu.permissions", labelKey = "permission.si.write")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.si.write", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Create and edit SI drafts"),
                @LocalizedText(locale = "zh-CN", text = "创建和编辑超级智能草稿")})
@PermissionDefinition(code = DdcPermissionCodes.SI_RESOURCE_PUBLISH, meaning = "Publish, rollback and deprecate Super-Intelligence resources",
        labelPage = "tianshu.permissions", labelKey = "permission.si.publish")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.si.publish", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish and rollback SI resources"),
                @LocalizedText(locale = "zh-CN", text = "发布、回滚和标记废弃超级智能资产")})
@PermissionDefinition(code = DdcPermissionCodes.SI_RESOURCE_PROMOTE, meaning = "Promote Super-Intelligence versions across environments",
        labelPage = "tianshu.permissions", labelKey = "permission.si.promote")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.si.promote", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Promote SI versions across environments"),
                @LocalizedText(locale = "zh-CN", text = "跨环境推广超级智能版本")})
@PermissionDefinition(code = DdcPermissionCodes.SI_RESOURCE_ARCHIVE, meaning = "Archive Super-Intelligence resources",
        labelPage = "tianshu.permissions", labelKey = "permission.si.archive")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.si.archive", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Archive SI resources"),
                @LocalizedText(locale = "zh-CN", text = "归档删除超级智能资产")})
@PermissionDefinition(code = DdcPermissionCodes.SI_BINDING_WRITE, meaning = "Bind or unbind SI resource versions per application scope",
        labelPage = "tianshu.permissions", labelKey = "permission.si.binding.write")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.si.binding.write", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage SI service bindings"),
                @LocalizedText(locale = "zh-CN", text = "管理超级智能服务版本绑定")})
@PermissionDefinition(code = DdcPermissionCodes.SI_INDEX_MANAGE, meaning = "Trigger and manage SI semantic vector indexing",
        labelPage = "tianshu.permissions", labelKey = "permission.si.index.manage")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.si.index.manage", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage SI semantic index"),
                @LocalizedText(locale = "zh-CN", text = "管理超级智能语义向量索引")})
@PermissionDefinition(code = DdcPermissionCodes.OPERATION_LOG_READ, meaning = "List operation logs for the authenticated tenant",
        labelPage = "tianshu.permissions", labelKey = "permission.operationLog.read")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.operationLog.read", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "List tenant operation logs"),
                @LocalizedText(locale = "zh-CN", text = "查看租户操作日志列表")})
@PermissionDefinition(code = DdcPermissionCodes.OPERATION_LOG_DETAIL, meaning = "Read detailed fields of tenant operation logs",
        labelPage = "tianshu.permissions", labelKey = "permission.operationLog.detail")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.operationLog.detail", defaultLocale = "en-US",
        defaults = {@LocalizedText(locale = "en-US", text = "Read tenant operation log details"),
                @LocalizedText(locale = "zh-CN", text = "查看租户操作日志详情")})
public final class DdcPermissionCodes {

    public static final String READ = "TIANSHU_READ";
    public static final String WRITE = "TIANSHU_WRITE";
    public static final String PUBLISH = "TIANSHU_PUBLISH";
    public static final String CACHE = "TIANSHU_CACHE";
    public static final String OPERATION_LOG_READ = "TIANSHU_OPERATION_LOG_READ";
    public static final String OPERATION_LOG_DETAIL = "TIANSHU_OPERATION_LOG_DETAIL";
    public static final String I18N_READ = "TIANSHU_I18N_READ";
    public static final String AI_READ = "TIANSHU_AI_READ";
    public static final String AI_WRITE = "TIANSHU_AI_WRITE";
    public static final String AI_PUBLISH = "TIANSHU_AI_PUBLISH";
    public static final String AI_REPORT = "TIANSHU_AI_REPORT";
    public static final String NAMESPACE_TEMPLATE_SYSTEM = "TIANSHU_NAMESPACE_TEMPLATE_SYSTEM";
    public static final String NAMESPACE_TEMPLATE_PERSONAL = "TIANSHU_NAMESPACE_TEMPLATE_PERSONAL";
    public static final String NAMESPACE_EXPORT = "TIANSHU_NAMESPACE_EXPORT";
    public static final String SI_RESOURCE_READ = "TIANSHU_SI_READ";
    public static final String SI_RESOURCE_WRITE = "TIANSHU_SI_WRITE";
    public static final String SI_RESOURCE_PUBLISH = "TIANSHU_SI_PUBLISH";
    public static final String SI_RESOURCE_PROMOTE = "TIANSHU_SI_PROMOTE";
    public static final String SI_RESOURCE_ARCHIVE = "TIANSHU_SI_ARCHIVE";
    public static final String SI_BINDING_WRITE = "TIANSHU_SI_BINDING_WRITE";
    public static final String SI_INDEX_MANAGE = "TIANSHU_SI_INDEX_MANAGE";

    private DdcPermissionCodes() { }
}
