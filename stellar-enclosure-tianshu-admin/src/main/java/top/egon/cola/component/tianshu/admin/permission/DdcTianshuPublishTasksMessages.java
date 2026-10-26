package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "actions.refreshPage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refresh current page"),
                @LocalizedText(locale = "zh-CN", text = "刷新当前页")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "confirm.retry", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Retry task {{changeId}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认重试任务 {{changeId}}？")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Browse publish results by page. The current filtered page refreshes every 15 seconds."),
                @LocalizedText(locale = "zh-CN", text = "分页查看发布结果，并每 15 秒刷新当前筛选页。")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "detail.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Task details: {{changeId}}"),
                @LocalizedText(locale = "zh-CN", text = "任务详情：{{changeId}}")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "feedback.retried", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Retry task {{changeId}}: {{status}}"),
                @LocalizedText(locale = "zh-CN", text = "重试任务 {{changeId}}：{{status}}")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.ackCount", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Acknowledgments"),
                @LocalizedText(locale = "zh-CN", text = "ack 数")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.attemptCount", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Attempts"),
                @LocalizedText(locale = "zh-CN", text = "尝试次数")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.changeId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Change ID"),
                @LocalizedText(locale = "zh-CN", text = "Change ID")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.completedAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Completed at"),
                @LocalizedText(locale = "zh-CN", text = "完成时间")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.counts", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "ACK / Failed / Timed out"),
                @LocalizedText(locale = "zh-CN", text = "ack/失败/超时")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.dispatchedAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Dispatched at"),
                @LocalizedText(locale = "zh-CN", text = "调度时间")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.errorMessage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Error message"),
                @LocalizedText(locale = "zh-CN", text = "错误信息")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.failedCount", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failures"),
                @LocalizedText(locale = "zh-CN", text = "失败数")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.failureStage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failure stage"),
                @LocalizedText(locale = "zh-CN", text = "失败阶段")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.ignoredCount", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Ignored"),
                @LocalizedText(locale = "zh-CN", text = "忽略数")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.operator", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Operator"),
                @LocalizedText(locale = "zh-CN", text = "操作人")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.publishMode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish mode"),
                @LocalizedText(locale = "zh-CN", text = "发布模式")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.resourceChecksum", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource checksum"),
                @LocalizedText(locale = "zh-CN", text = "资源校验和")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.resourceName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration resource"),
                @LocalizedText(locale = "zh-CN", text = "配置资源")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.scope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scope"),
                @LocalizedText(locale = "zh-CN", text = "作用域")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.targetCount", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Targets"),
                @LocalizedText(locale = "zh-CN", text = "目标数")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.targetVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target version"),
                @LocalizedText(locale = "zh-CN", text = "目标版本")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.timeoutCount", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Timed out"),
                @LocalizedText(locale = "zh-CN", text = "超时数")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "fields.timeoutMs", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Timeout (ms)"),
                @LocalizedText(locale = "zh-CN", text = "超时时间(ms)")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Tasks"),
                @LocalizedText(locale = "zh-CN", text = "任务列表")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "publishMode.ASYNC", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Asynchronous"),
                @LocalizedText(locale = "zh-CN", text = "异步")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "publishMode.STRONG_ALL_ACK", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Strong consistency, all acknowledgments"),
                @LocalizedText(locale = "zh-CN", text = "强一致全部确认")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "publishMode.STRONG_QUORUM_ACK", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Strong consistency, quorum acknowledgments"),
                @LocalizedText(locale = "zh-CN", text = "强一致多数确认")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "publishMode.SYNC_ALL_ACK", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Synchronous, all acknowledgments"),
                @LocalizedText(locale = "zh-CN", text = "同步全部确认")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.DISPATCHED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Dispatched"),
                @LocalizedText(locale = "zh-CN", text = "已调度")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.FAILED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed"),
                @LocalizedText(locale = "zh-CN", text = "失败")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.PARTIAL_SUCCESS", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Partially succeeded"),
                @LocalizedText(locale = "zh-CN", text = "部分成功")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.PENDING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Pending"),
                @LocalizedText(locale = "zh-CN", text = "待处理")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.PUBLISHING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publishing"),
                @LocalizedText(locale = "zh-CN", text = "发布中")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.SUCCESS", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Succeeded"),
                @LocalizedText(locale = "zh-CN", text = "成功")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.TIMEOUT", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Timed out"),
                @LocalizedText(locale = "zh-CN", text = "超时")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "status.UNKNOWN", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown"),
                @LocalizedText(locale = "zh-CN", text = "未知")})
@PermissionMessage(pageCode = "tianshu.publishTasks", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish tasks"),
                @LocalizedText(locale = "zh-CN", text = "发布任务")})
final class DdcTianshuPublishTasksMessages {
    private DdcTianshuPublishTasksMessages() { }
}
