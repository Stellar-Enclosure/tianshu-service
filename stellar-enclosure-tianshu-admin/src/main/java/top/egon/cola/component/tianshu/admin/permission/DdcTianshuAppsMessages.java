package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.apps", key = "actions.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create application"),
                @LocalizedText(locale = "zh-CN", text = "新建应用")})
@PermissionMessage(pageCode = "tianshu.apps", key = "actions.edit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit application"),
                @LocalizedText(locale = "zh-CN", text = "编辑应用")})
@PermissionMessage(pageCode = "tianshu.apps", key = "confirm.delete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete application {{code}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除应用 {{code}}？")})
@PermissionMessage(pageCode = "tianshu.apps", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage applications within business domains and their scope visibility."),
                @LocalizedText(locale = "zh-CN", text = "维护业务域中的应用及其作用域可见性。")})
@PermissionMessage(pageCode = "tianshu.apps", key = "feedback.deleted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application deleted"),
                @LocalizedText(locale = "zh-CN", text = "应用删除完成")})
@PermissionMessage(pageCode = "tianshu.apps", key = "feedback.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application saved"),
                @LocalizedText(locale = "zh-CN", text = "应用保存成功")})
@PermissionMessage(pageCode = "tianshu.apps", key = "feedback.statusUpdated", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application status updated"),
                @LocalizedText(locale = "zh-CN", text = "应用状态更新完成")})
@PermissionMessage(pageCode = "tianshu.apps", key = "fields.appCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application code"),
                @LocalizedText(locale = "zh-CN", text = "应用编码")})
@PermissionMessage(pageCode = "tianshu.apps", key = "fields.appName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application name"),
                @LocalizedText(locale = "zh-CN", text = "应用名称")})
@PermissionMessage(pageCode = "tianshu.apps", key = "fields.bizCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain"),
                @LocalizedText(locale = "zh-CN", text = "业务域")})
@PermissionMessage(pageCode = "tianshu.apps", key = "fields.owner", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Owner"),
                @LocalizedText(locale = "zh-CN", text = "负责人")})
@PermissionMessage(pageCode = "tianshu.apps", key = "filters.keyword", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Search by appCode / name"),
                @LocalizedText(locale = "zh-CN", text = "appCode / 名称模糊查询")})
@PermissionMessage(pageCode = "tianshu.apps", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Applications"),
                @LocalizedText(locale = "zh-CN", text = "应用列表")})
@PermissionMessage(pageCode = "tianshu.apps", key = "placeholders.owner", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "local-admin"),
                @LocalizedText(locale = "zh-CN", text = "local-admin")})
@PermissionMessage(pageCode = "tianshu.apps", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Applications"),
                @LocalizedText(locale = "zh-CN", text = "应用管理")})
final class DdcTianshuAppsMessages {
    private DdcTianshuAppsMessages() { }
}
