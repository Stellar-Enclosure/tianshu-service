package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.instances", key = "actions.refreshPage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refresh current page"),
                @LocalizedText(locale = "zh-CN", text = "刷新当前页")})
@PermissionMessage(pageCode = "tianshu.instances", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View Kubernetes workload and pod observations for a configuration scope."),
                @LocalizedText(locale = "zh-CN", text = "按配置作用域查看 Kubernetes 工作负载和 Pod 观测。")})
@PermissionMessage(pageCode = "tianshu.instances", key = "empty.instances", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No configured workload targets in this scope"),
                @LocalizedText(locale = "zh-CN", text = "当前作用域没有配置工作负载目标")})
@PermissionMessage(pageCode = "tianshu.instances", key = "empty.selectScope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select a complete scope and search."),
                @LocalizedText(locale = "zh-CN", text = "请选择完整作用域并执行查询。")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.desiredReplicas", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Desired replicas"),
                @LocalizedText(locale = "zh-CN", text = "期望副本")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.location", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Cluster / namespace"),
                @LocalizedText(locale = "zh-CN", text = "集群 / 命名空间")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.observedAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Observed at"),
                @LocalizedText(locale = "zh-CN", text = "观察时间")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.pods", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Observed pods"),
                @LocalizedText(locale = "zh-CN", text = "已观测 Pod")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.target", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Workload target"),
                @LocalizedText(locale = "zh-CN", text = "工作负载目标")})
@PermissionMessage(pageCode = "tianshu.instances", key = "fields.validUntil", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Observation valid until"),
                @LocalizedText(locale = "zh-CN", text = "观察有效期")})
@PermissionMessage(pageCode = "tianshu.instances", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Workload observations"),
                @LocalizedText(locale = "zh-CN", text = "工作负载观测")})
@PermissionMessage(pageCode = "tianshu.instances", key = "pod.notReady", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Not ready"),
                @LocalizedText(locale = "zh-CN", text = "未就绪")})
@PermissionMessage(pageCode = "tianshu.instances", key = "pod.ready", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Ready"),
                @LocalizedText(locale = "zh-CN", text = "已就绪")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.CLOSED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Observer stopped"),
                @LocalizedText(locale = "zh-CN", text = "观察已停止")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.FRESH", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Current observation"),
                @LocalizedText(locale = "zh-CN", text = "观察有效")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.IDENTITY_CONFLICT", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Workload identity mismatch"),
                @LocalizedText(locale = "zh-CN", text = "工作负载身份不符")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.INITIALIZING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Initializing observation"),
                @LocalizedText(locale = "zh-CN", text = "正在初始化观察")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.RELISTING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refreshing observation"),
                @LocalizedText(locale = "zh-CN", text = "正在重新观察")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.STALE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Observation expired"),
                @LocalizedText(locale = "zh-CN", text = "观察已过期")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.UNAVAILABLE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Observation unavailable"),
                @LocalizedText(locale = "zh-CN", text = "观察不可用")})
@PermissionMessage(pageCode = "tianshu.instances", key = "status.UNKNOWN", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown observation"),
                @LocalizedText(locale = "zh-CN", text = "观察未知")})
@PermissionMessage(pageCode = "tianshu.instances", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration workload instances"),
                @LocalizedText(locale = "zh-CN", text = "配置工作负载实例")})
@PermissionMessage(pageCode = "tianshu.instances", key = "validation.scopeRequired", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enter bizCode, env, and appCode before searching"),
                @LocalizedText(locale = "zh-CN", text = "请填写 bizCode、env 和 appCode 后再查询")})
final class DdcTianshuInstancesMessages {
    private DdcTianshuInstancesMessages() { }
}
