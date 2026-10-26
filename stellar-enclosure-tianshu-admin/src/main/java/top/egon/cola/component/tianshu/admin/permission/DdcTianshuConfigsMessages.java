package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.configs", key = "actions.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create application.yml"),
                @LocalizedText(locale = "zh-CN", text = "新建 application.yml")})
@PermissionMessage(pageCode = "tianshu.configs", key = "actions.edit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit application.yml"),
                @LocalizedText(locale = "zh-CN", text = "编辑 application.yml")})
@PermissionMessage(pageCode = "tianshu.configs", key = "actions.more", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "More actions"),
                @LocalizedText(locale = "zh-CN", text = "更多操作")})
@PermissionMessage(pageCode = "tianshu.configs", key = "actions.publish", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish"),
                @LocalizedText(locale = "zh-CN", text = "发布")})
@PermissionMessage(pageCode = "tianshu.configs", key = "actions.rollback", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Roll back"),
                @LocalizedText(locale = "zh-CN", text = "回滚")})
@PermissionMessage(pageCode = "tianshu.configs", key = "actions.viewVersions", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View versions"),
                @LocalizedText(locale = "zh-CN", text = "查看版本")})
@PermissionMessage(pageCode = "tianshu.configs", key = "changeType.CREATE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Created"),
                @LocalizedText(locale = "zh-CN", text = "新建")})
@PermissionMessage(pageCode = "tianshu.configs", key = "changeType.DELETE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Deleted"),
                @LocalizedText(locale = "zh-CN", text = "删除")})
@PermissionMessage(pageCode = "tianshu.configs", key = "changeType.ROLLBACK", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rolled back"),
                @LocalizedText(locale = "zh-CN", text = "回滚")})
@PermissionMessage(pageCode = "tianshu.configs", key = "changeType.UPDATE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Updated"),
                @LocalizedText(locale = "zh-CN", text = "更新")})
@PermissionMessage(pageCode = "tianshu.configs", key = "confirm.delete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete {{name}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除 {{name}}？")})
@PermissionMessage(pageCode = "tianshu.configs", key = "confirm.publish", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish the current version of {{name}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认发布 {{name}} 当前版本？")})
@PermissionMessage(pageCode = "tianshu.configs", key = "confirm.rollback", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Roll back to version {{version}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认回滚到版本 {{version}}？")})
@PermissionMessage(pageCode = "tianshu.configs", key = "confirm.scope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scope: {{scope}}"),
                @LocalizedText(locale = "zh-CN", text = "作用域：{{scope}}")})
@PermissionMessage(pageCode = "tianshu.configs", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage application.yml configuration, publishing, and version history."),
                @LocalizedText(locale = "zh-CN", text = "维护 application.yml 配置、发布与版本历史。")})
@PermissionMessage(pageCode = "tianshu.configs", key = "feedback.deleted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration deleted"),
                @LocalizedText(locale = "zh-CN", text = "配置已删除")})
@PermissionMessage(pageCode = "tianshu.configs", key = "feedback.published", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish task {{changeId}}: {{status}}"),
                @LocalizedText(locale = "zh-CN", text = "发布任务 {{changeId}}：{{status}}")})
@PermissionMessage(pageCode = "tianshu.configs", key = "feedback.rolledBack", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rolled back"),
                @LocalizedText(locale = "zh-CN", text = "已回滚")})
@PermissionMessage(pageCode = "tianshu.configs", key = "feedback.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration saved"),
                @LocalizedText(locale = "zh-CN", text = "配置已保存")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.changeReason", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Change reason"),
                @LocalizedText(locale = "zh-CN", text = "变更原因")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.changeType", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Type"),
                @LocalizedText(locale = "zh-CN", text = "类型")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.content", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "YAML content"),
                @LocalizedText(locale = "zh-CN", text = "YAML 内容")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.currentVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Current version"),
                @LocalizedText(locale = "zh-CN", text = "当前版本")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.editorScope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain / Namespace / Environment / Application"),
                @LocalizedText(locale = "zh-CN", text = "业务域 / 命名空间 / 环境 / 应用")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.newContent", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "New value"),
                @LocalizedText(locale = "zh-CN", text = "新值")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.operator", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Operator"),
                @LocalizedText(locale = "zh-CN", text = "操作人")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.resourceName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration file"),
                @LocalizedText(locale = "zh-CN", text = "配置文件")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.scope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain / Environment / Application"),
                @LocalizedText(locale = "zh-CN", text = "业务域 / 环境 / 应用")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.time", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Time"),
                @LocalizedText(locale = "zh-CN", text = "时间")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.version", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version"),
                @LocalizedText(locale = "zh-CN", text = "版本")})
@PermissionMessage(pageCode = "tianshu.configs", key = "fields.visibleNamespaces", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Visible namespaces"),
                @LocalizedText(locale = "zh-CN", text = "可见命名空间")})
@PermissionMessage(pageCode = "tianshu.configs", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configurations"),
                @LocalizedText(locale = "zh-CN", text = "配置列表")})
@PermissionMessage(pageCode = "tianshu.configs", key = "placeholders.changeReason", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Tianshu Admin Web update"),
                @LocalizedText(locale = "zh-CN", text = "Tianshu Admin Web update")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.DISPATCHED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Dispatched"),
                @LocalizedText(locale = "zh-CN", text = "已调度")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.FAILED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed"),
                @LocalizedText(locale = "zh-CN", text = "失败")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.PARTIAL_SUCCESS", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Partially succeeded"),
                @LocalizedText(locale = "zh-CN", text = "部分成功")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.PENDING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Pending"),
                @LocalizedText(locale = "zh-CN", text = "待处理")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.PUBLISHING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publishing"),
                @LocalizedText(locale = "zh-CN", text = "发布中")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.SUCCESS", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Succeeded"),
                @LocalizedText(locale = "zh-CN", text = "成功")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.TIMEOUT", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Timed out"),
                @LocalizedText(locale = "zh-CN", text = "超时")})
@PermissionMessage(pageCode = "tianshu.configs", key = "status.UNKNOWN", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown"),
                @LocalizedText(locale = "zh-CN", text = "未知")})
@PermissionMessage(pageCode = "tianshu.configs", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration center"),
                @LocalizedText(locale = "zh-CN", text = "配置中心管理")})
@PermissionMessage(pageCode = "tianshu.configs", key = "validation.bindingRequired", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "This namespace, environment, and application are not bound. Configure the binding in namespace management first."),
                @LocalizedText(locale = "zh-CN", text = "当前命名空间、环境与应用尚未绑定，请先在命名空间管理中配置绑定")})
@PermissionMessage(pageCode = "tianshu.configs", key = "validation.scopeRequired", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enter business domain / namespace / environment / application"),
                @LocalizedText(locale = "zh-CN", text = "请填写业务域 / 命名空间 / 环境 / 应用")})
@PermissionMessage(pageCode = "tianshu.configs", key = "versions.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "application.yml version history"),
                @LocalizedText(locale = "zh-CN", text = "application.yml 版本历史")})
final class DdcTianshuConfigsMessages {
    private DdcTianshuConfigsMessages() { }
}
