package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.bindings", key = "actions.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Add binding"),
                @LocalizedText(locale = "zh-CN", text = "新增绑定")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "confirm.delete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete binding {{namespaceCode}}/{{env}}/{{appCode}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除 {{namespaceCode}}/{{env}}/{{appCode}} 绑定？")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage visibility bindings between namespaces, environments, and applications."),
                @LocalizedText(locale = "zh-CN", text = "维护命名空间、环境与应用之间的可见性绑定。")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "dialog.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Add scope binding"),
                @LocalizedText(locale = "zh-CN", text = "新增作用域绑定")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "dialog.edit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit scope binding"),
                @LocalizedText(locale = "zh-CN", text = "编辑作用域绑定")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "feedback.deleted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scope binding deleted"),
                @LocalizedText(locale = "zh-CN", text = "作用域绑定删除完成")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "feedback.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scope binding saved"),
                @LocalizedText(locale = "zh-CN", text = "作用域绑定保存成功")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "fields.app", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application"),
                @LocalizedText(locale = "zh-CN", text = "应用")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "fields.appCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application code"),
                @LocalizedText(locale = "zh-CN", text = "应用编码")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "fields.appName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application name"),
                @LocalizedText(locale = "zh-CN", text = "应用名称")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "fields.bizCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain"),
                @LocalizedText(locale = "zh-CN", text = "业务域")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "fields.env", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environment"),
                @LocalizedText(locale = "zh-CN", text = "环境")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "fields.namespace", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespace"),
                @LocalizedText(locale = "zh-CN", text = "命名空间")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "fields.namespaceCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespace code"),
                @LocalizedText(locale = "zh-CN", text = "命名空间编码")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Bindings"),
                @LocalizedText(locale = "zh-CN", text = "绑定列表")})
@PermissionMessage(pageCode = "tianshu.bindings", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scope bindings"),
                @LocalizedText(locale = "zh-CN", text = "作用域绑定管理")})
final class DdcTianshuBindingsMessages {
    private DdcTianshuBindingsMessages() { }
}
