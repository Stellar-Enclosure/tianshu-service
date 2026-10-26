package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.registry", key = "actions.viewInstances", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View instances"),
                @LocalizedText(locale = "zh-CN", text = "查看实例")})
@PermissionMessage(pageCode = "tianshu.registry", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Browse service keys by page and load instances for a selected service."),
                @LocalizedText(locale = "zh-CN", text = "分页查看服务键，并按需加载单个服务的实例列表。")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.address", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Address"),
                @LocalizedText(locale = "zh-CN", text = "地址")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.expireAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Expires at"),
                @LocalizedText(locale = "zh-CN", text = "过期时间")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.instanceId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Instance ID"),
                @LocalizedText(locale = "zh-CN", text = "实例 ID")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.lastHeartbeatAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Last heartbeat"),
                @LocalizedText(locale = "zh-CN", text = "最近心跳")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.scope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business domain / Environment / Application"),
                @LocalizedText(locale = "zh-CN", text = "业务域 / 环境 / 应用")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.serviceId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Service ID"),
                @LocalizedText(locale = "zh-CN", text = "Service ID")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.serviceName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Service name"),
                @LocalizedText(locale = "zh-CN", text = "服务名")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.transport", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Type / Protocol"),
                @LocalizedText(locale = "zh-CN", text = "类型 / 协议")})
@PermissionMessage(pageCode = "tianshu.registry", key = "fields.version", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Group / Version"),
                @LocalizedText(locale = "zh-CN", text = "分组 / 版本")})
@PermissionMessage(pageCode = "tianshu.registry", key = "instances.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{name}} instances"),
                @LocalizedText(locale = "zh-CN", text = "{{name}} 实例")})
@PermissionMessage(pageCode = "tianshu.registry", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Service directory"),
                @LocalizedText(locale = "zh-CN", text = "服务目录")})
@PermissionMessage(pageCode = "tianshu.registry", key = "stats.onlineOnPage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Online on current instance page"),
                @LocalizedText(locale = "zh-CN", text = "当前实例页在线")})
@PermissionMessage(pageCode = "tianshu.registry", key = "stats.services", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Total services"),
                @LocalizedText(locale = "zh-CN", text = "服务总数")})
@PermissionMessage(pageCode = "tianshu.registry", key = "status.OFFLINE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Offline"),
                @LocalizedText(locale = "zh-CN", text = "离线")})
@PermissionMessage(pageCode = "tianshu.registry", key = "status.ONLINE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Online"),
                @LocalizedText(locale = "zh-CN", text = "在线")})
@PermissionMessage(pageCode = "tianshu.registry", key = "status.UNKNOWN", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown"),
                @LocalizedText(locale = "zh-CN", text = "未知")})
@PermissionMessage(pageCode = "tianshu.registry", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Service registry"),
                @LocalizedText(locale = "zh-CN", text = "服务注册目录")})
final class DdcTianshuRegistryMessages {
    private DdcTianshuRegistryMessages() { }
}
