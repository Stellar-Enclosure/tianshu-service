package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.bizs", key = "actions.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create business domain"),
                @LocalizedText(locale = "zh-CN", text = "新建业务域")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "actions.edit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit business domain"),
                @LocalizedText(locale = "zh-CN", text = "编辑业务域")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "confirm.delete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete business domain {{code}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除业务域 {{code}}？")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage Tianshu business domains and their enabled status."),
                @LocalizedText(locale = "zh-CN", text = "维护 Tianshu 业务域及其启用状态。")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "feedback.deleted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain deleted"),
                @LocalizedText(locale = "zh-CN", text = "业务域删除完成")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "feedback.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain saved"),
                @LocalizedText(locale = "zh-CN", text = "业务域保存成功")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "feedback.statusUpdated", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain status updated"),
                @LocalizedText(locale = "zh-CN", text = "业务域状态更新完成")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "fields.bizCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain code"),
                @LocalizedText(locale = "zh-CN", text = "业务域编码")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "filters.keyword", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Search by name"),
                @LocalizedText(locale = "zh-CN", text = "名称模糊查询")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domains"),
                @LocalizedText(locale = "zh-CN", text = "业务域列表")})
@PermissionMessage(pageCode = "tianshu.bizs", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domains"),
                @LocalizedText(locale = "zh-CN", text = "业务域管理")})
final class DdcTianshuBizsMessages {
    private DdcTianshuBizsMessages() { }
}
