package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.envs", key = "actions.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create environment"),
                @LocalizedText(locale = "zh-CN", text = "新建环境")})
@PermissionMessage(pageCode = "tianshu.envs", key = "actions.edit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit environment"),
                @LocalizedText(locale = "zh-CN", text = "编辑环境")})
@PermissionMessage(pageCode = "tianshu.envs", key = "confirm.delete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete environment {{code}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除环境 {{code}}？")})
@PermissionMessage(pageCode = "tianshu.envs", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage Tianshu environment codes, sort order, and enabled status."),
                @LocalizedText(locale = "zh-CN", text = "维护 Tianshu 环境编码、排序和启用状态。")})
@PermissionMessage(pageCode = "tianshu.envs", key = "feedback.deleted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environment deleted"),
                @LocalizedText(locale = "zh-CN", text = "环境删除完成")})
@PermissionMessage(pageCode = "tianshu.envs", key = "feedback.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environment saved"),
                @LocalizedText(locale = "zh-CN", text = "环境保存成功")})
@PermissionMessage(pageCode = "tianshu.envs", key = "feedback.statusUpdated", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environment status updated"),
                @LocalizedText(locale = "zh-CN", text = "环境状态更新完成")})
@PermissionMessage(pageCode = "tianshu.envs", key = "fields.envCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environment code"),
                @LocalizedText(locale = "zh-CN", text = "环境编码")})
@PermissionMessage(pageCode = "tianshu.envs", key = "fields.sortOrder", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Sort order"),
                @LocalizedText(locale = "zh-CN", text = "排序")})
@PermissionMessage(pageCode = "tianshu.envs", key = "filters.keyword", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Search by name"),
                @LocalizedText(locale = "zh-CN", text = "名称模糊查询")})
@PermissionMessage(pageCode = "tianshu.envs", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environments"),
                @LocalizedText(locale = "zh-CN", text = "环境列表")})
@PermissionMessage(pageCode = "tianshu.envs", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environments"),
                @LocalizedText(locale = "zh-CN", text = "环境管理")})
final class DdcTianshuEnvsMessages {
    private DdcTianshuEnvsMessages() { }
}
