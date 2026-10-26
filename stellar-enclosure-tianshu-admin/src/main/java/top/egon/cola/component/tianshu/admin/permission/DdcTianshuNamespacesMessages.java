package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.namespaces", key = "actions.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create namespace"),
                @LocalizedText(locale = "zh-CN", text = "新建命名空间")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "actions.edit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit namespace"),
                @LocalizedText(locale = "zh-CN", text = "编辑命名空间")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "actions.manageBindings", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage bindings"),
                @LocalizedText(locale = "zh-CN", text = "管理绑定")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "actions.saveBindings", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Save bindings"),
                @LocalizedText(locale = "zh-CN", text = "保存绑定")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "bindings.appLabel", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{code}} ({{name}})"),
                @LocalizedText(locale = "zh-CN", text = "{{code}}（{{name}}）")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "bindings.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage bindings: {{bizCode}}/{{namespaceCode}}"),
                @LocalizedText(locale = "zh-CN", text = "管理绑定：{{bizCode}}/{{namespaceCode}}")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "confirm.delete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete namespace {{name}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除命名空间 {{name}}？")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage namespaces and their environment and application visibility bindings."),
                @LocalizedText(locale = "zh-CN", text = "维护命名空间及其环境与应用可见性绑定。")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "feedback.bindingsSaved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespace bindings saved"),
                @LocalizedText(locale = "zh-CN", text = "命名空间绑定保存成功")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "feedback.deleted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespace deleted"),
                @LocalizedText(locale = "zh-CN", text = "命名空间删除完成")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "feedback.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespace saved"),
                @LocalizedText(locale = "zh-CN", text = "命名空间保存成功")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "feedback.statusUpdated", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespace status updated"),
                @LocalizedText(locale = "zh-CN", text = "命名空间状态更新完成")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "fields.bizCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain"),
                @LocalizedText(locale = "zh-CN", text = "业务域")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "fields.env", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environment"),
                @LocalizedText(locale = "zh-CN", text = "环境")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "fields.namespaceCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Code"),
                @LocalizedText(locale = "zh-CN", text = "编码")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "fields.visibleApps", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Visible applications (multiple)"),
                @LocalizedText(locale = "zh-CN", text = "可见应用（多选）")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "filters.keyword", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Search namespaces"),
                @LocalizedText(locale = "zh-CN", text = "命名空间模糊查询")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespaces"),
                @LocalizedText(locale = "zh-CN", text = "命名空间列表")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "placeholders.namespaceCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Globally unique"),
                @LocalizedText(locale = "zh-CN", text = "全局唯一")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespaces"),
                @LocalizedText(locale = "zh-CN", text = "命名空间管理")})
final class DdcTianshuNamespacesMessages {
    private DdcTianshuNamespacesMessages() { }
}
