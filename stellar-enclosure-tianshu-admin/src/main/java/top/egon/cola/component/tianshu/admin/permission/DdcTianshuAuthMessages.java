package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.checking", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Checking your session"),
                @LocalizedText(locale = "zh-CN", text = "校验统一登录态")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Sign in through the identity platform with OIDC. The browser only holds an Edge HttpOnly session cookie."),
                @LocalizedText(locale = "zh-CN", text = "跳转到统一身份平台完成 OIDC 登录；浏览器只持有 Edge 的 HttpOnly 会话 Cookie。")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.guardFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to check unsaved changes"),
                @LocalizedText(locale = "zh-CN", text = "无法确认未保存的编辑状态")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.login", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Sign in with the identity platform"),
                @LocalizedText(locale = "zh-CN", text = "使用统一身份登录")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.loginFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Sign-in failed"),
                @LocalizedText(locale = "zh-CN", text = "统一身份登录失败")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.logoutFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Sign-out failed"),
                @LocalizedText(locale = "zh-CN", text = "统一身份退出失败")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.remoteLogoutPending", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Signed out locally. Remote token revocation is pending."),
                @LocalizedText(locale = "zh-CN", text = "本地已退出，远端令牌撤销待恢复")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.tenantFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to refresh the current tenant identity"),
                @LocalizedText(locale = "zh-CN", text = "无法刷新当前租户身份")})
@PermissionMessage(pageCode = "tianshu.auth", key = "auth.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Tianshu administration"),
                @LocalizedText(locale = "zh-CN", text = "Tianshu 管理端")})
@PermissionMessage(pageCode = "tianshu.auth", key = "common.loading", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Loading"),
                @LocalizedText(locale = "zh-CN", text = "加载中")})
@PermissionMessage(pageCode = "tianshu.auth", key = "common.retry", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Retry"),
                @LocalizedText(locale = "zh-CN", text = "重试")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.cancelled", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Request cancelled"),
                @LocalizedText(locale = "zh-CN", text = "请求已取消")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.conflict", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The data has changed. Refresh and try again."),
                @LocalizedText(locale = "zh-CN", text = "数据已发生变化，请刷新后重试")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.csrf", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The request could not be verified. Refresh your session and try again."),
                @LocalizedText(locale = "zh-CN", text = "无法验证请求，请刷新登录状态后重试")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.forbidden", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to perform this action"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权执行此操作")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.invalidResponse", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Invalid paginated response"),
                @LocalizedText(locale = "zh-CN", text = "分页响应格式无效")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.network", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to connect to Tianshu"),
                @LocalizedText(locale = "zh-CN", text = "无法连接 Tianshu 管理端")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.partial", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Some data could not be loaded: {{message}}"),
                @LocalizedText(locale = "zh-CN", text = "部分数据加载失败：{{message}}")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.request", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Request failed ({{status}})"),
                @LocalizedText(locale = "zh-CN", text = "请求失败（{{status}}）")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.server", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The service is unavailable. Try again later."),
                @LocalizedText(locale = "zh-CN", text = "服务暂时不可用，请稍后重试")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.tenantChanged", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Your tenant has changed. Reload the page."),
                @LocalizedText(locale = "zh-CN", text = "当前身份租户已变化，请重新加载页面")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.unauthorized", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Your session has expired. Sign in again."),
                @LocalizedText(locale = "zh-CN", text = "统一身份登录已过期，请重新登录")})
@PermissionMessage(pageCode = "tianshu.auth", key = "error.unknown", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown error"),
                @LocalizedText(locale = "zh-CN", text = "未知错误")})
@PermissionMessage(pageCode = "tianshu.auth", key = "i18n.loadFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to load interface translations"),
                @LocalizedText(locale = "zh-CN", text = "界面语言包加载失败")})
@PermissionMessage(pageCode = "tianshu.auth", key = "i18n.refreshFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to refresh translations. Current text has been retained."),
                @LocalizedText(locale = "zh-CN", text = "文案刷新失败，已保留当前显示内容")})
@PermissionMessage(pageCode = "tianshu.auth", key = "language.label", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Interface language"),
                @LocalizedText(locale = "zh-CN", text = "界面语言")})
@PermissionMessage(pageCode = "tianshu.auth", key = "language.refresh", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refresh interface text"),
                @LocalizedText(locale = "zh-CN", text = "刷新界面文案")})
final class DdcTianshuAuthMessages {
    private DdcTianshuAuthMessages() { }
}
