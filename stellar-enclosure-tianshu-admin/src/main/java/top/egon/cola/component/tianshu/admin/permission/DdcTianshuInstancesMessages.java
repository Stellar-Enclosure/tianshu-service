package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.instances", key = "actions.refreshPage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refresh current page"),
                @LocalizedText(locale = "zh-CN", text = "刷新当前页")})
@PermissionMessage(pageCode = "tianshu.instances", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Browse configuration client leases, status, and latest heartbeats by physical scope."),
                @LocalizedText(locale = "zh-CN", text = "按物理作用域分页查看配置客户端租约、状态和最近心跳。")})
@PermissionMessage(pageCode = "tianshu.instances", key = "empty.instances", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No configuration client instances in this scope"),
                @LocalizedText(locale = "zh-CN", text = "当前作用域暂无配置客户端实例")})
@PermissionMessage(pageCode = "tianshu.instances", key = "empty.selectScope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select a complete scope and search."),
                @LocalizedText(locale = "zh-CN", text = "请选择完整作用域并执行查询。")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.address", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Address"),
                @LocalizedText(locale = "zh-CN", text = "地址")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.instanceId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Instance ID"),
                @LocalizedText(locale = "zh-CN", text = "实例 ID")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.lastHeartbeatAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Last heartbeat"),
                @LocalizedText(locale = "zh-CN", text = "最近心跳")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.leaseExpireAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Lease expires at"),
                @LocalizedText(locale = "zh-CN", text = "Lease 过期时间")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.leaseId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Lease ID"),
                @LocalizedText(locale = "zh-CN", text = "Lease ID")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.pid", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "PID"),
                @LocalizedText(locale = "zh-CN", text = "PID")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.scope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scope"),
                @LocalizedText(locale = "zh-CN", text = "作用域")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.sdkVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "SDK version"),
                @LocalizedText(locale = "zh-CN", text = "SDK 版本")})
@PermissionMessage(pageCode = "tianshu.instances", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Client instances"),
                @LocalizedText(locale = "zh-CN", text = "客户端实例列表")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.OFFLINE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Offline"),
                @LocalizedText(locale = "zh-CN", text = "离线")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.ONLINE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Online"),
                @LocalizedText(locale = "zh-CN", text = "在线")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.UNKNOWN", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown"),
                @LocalizedText(locale = "zh-CN", text = "未知")})
@PermissionMessage(pageCode = "tianshu.instances", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration client instances"),
                @LocalizedText(locale = "zh-CN", text = "配置客户端实例")})
@PermissionMessage(pageCode = "tianshu.instances", key = "validation.scopeRequired", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enter bizCode, env, and appCode before searching"),
                @LocalizedText(locale = "zh-CN", text = "请填写 bizCode、env 和 appCode 后再查询")})
final class DdcTianshuInstancesMessages {
    private DdcTianshuInstancesMessages() { }
}
