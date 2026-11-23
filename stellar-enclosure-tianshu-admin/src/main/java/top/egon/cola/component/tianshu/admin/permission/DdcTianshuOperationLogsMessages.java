package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source catalogue for the audit console and its registered read APIs. */
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Operation logs"),
                @LocalizedText(locale = "zh-CN", text = "操作日志")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "api.page", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "List operation logs"),
                @LocalizedText(locale = "zh-CN", text = "分页查询操作日志")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "api.detail", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Read operation log details"),
                @LocalizedText(locale = "zh-CN", text = "查看操作日志详情")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.from", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Occurred from"),
                @LocalizedText(locale = "zh-CN", text = "发生时间起点")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.to", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Occurred before"),
                @LocalizedText(locale = "zh-CN", text = "发生时间终点")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.actionCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Action code"),
                @LocalizedText(locale = "zh-CN", text = "动作代码")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.actorId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Actor ID"),
                @LocalizedText(locale = "zh-CN", text = "操作者 ID")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.resourceType", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource type"),
                @LocalizedText(locale = "zh-CN", text = "对象类型")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.resourceId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource ID"),
                @LocalizedText(locale = "zh-CN", text = "对象 ID")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.bizCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Business"),
                @LocalizedText(locale = "zh-CN", text = "业务域")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.namespaceCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Namespace"),
                @LocalizedText(locale = "zh-CN", text = "命名空间")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.env", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Environment"),
                @LocalizedText(locale = "zh-CN", text = "环境")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.appCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Application"),
                @LocalizedText(locale = "zh-CN", text = "应用")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.outcome", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Outcome"),
                @LocalizedText(locale = "zh-CN", text = "结果")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.apply", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Search"),
                @LocalizedText(locale = "zh-CN", text = "查询")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.reset", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Reset"),
                @LocalizedText(locale = "zh-CN", text = "重置")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "table.occurredAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Occurred at"),
                @LocalizedText(locale = "zh-CN", text = "发生时间")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "table.actor", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Actor"),
                @LocalizedText(locale = "zh-CN", text = "操作者")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "table.action", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Action"),
                @LocalizedText(locale = "zh-CN", text = "动作")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "table.resource", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource"),
                @LocalizedText(locale = "zh-CN", text = "对象")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "table.source", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Source"),
                @LocalizedText(locale = "zh-CN", text = "来源")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "table.phase", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Phase"),
                @LocalizedText(locale = "zh-CN", text = "阶段")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "table.outcome", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Outcome"),
                @LocalizedText(locale = "zh-CN", text = "结果")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "pagination.previous", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Previous page"),
                @LocalizedText(locale = "zh-CN", text = "上一页")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "pagination.next", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Next page"),
                @LocalizedText(locale = "zh-CN", text = "下一页")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "empty", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No operation logs"),
                @LocalizedText(locale = "zh-CN", text = "暂无操作日志")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "loading", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Loading operation logs"),
                @LocalizedText(locale = "zh-CN", text = "正在加载操作日志")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "error.denied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to view operation logs"),
                @LocalizedText(locale = "zh-CN", text = "无权查看操作日志")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "error.notFound", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "That operation log could not be found"),
                @LocalizedText(locale = "zh-CN", text = "该操作日志不存在")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "error.unavailable", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Audit storage is temporarily unavailable. Your filters are preserved."),
                @LocalizedText(locale = "zh-CN", text = "审计存储暂不可用，筛选条件已保留")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "error.invalid", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Check the filters and time range"),
                @LocalizedText(locale = "zh-CN", text = "请检查筛选条件和时间范围")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "error.generic", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Could not load operation logs"),
                @LocalizedText(locale = "zh-CN", text = "无法加载操作日志")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "time.zone", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Times are shown in {{timeZone}}"),
                @LocalizedText(locale = "zh-CN", text = "时间按 {{timeZone}} 显示")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.open", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View details"),
                @LocalizedText(locale = "zh-CN", text = "查看详情")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Operation log details"),
                @LocalizedText(locale = "zh-CN", text = "操作日志详情")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.eventId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Event ID"),
                @LocalizedText(locale = "zh-CN", text = "事件 ID")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.operationId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Operation ID"),
                @LocalizedText(locale = "zh-CN", text = "关联操作 ID")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.recordedAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Recorded at"),
                @LocalizedText(locale = "zh-CN", text = "入库时间")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.triggerType", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Trigger"),
                @LocalizedText(locale = "zh-CN", text = "触发来源")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.httpMethod", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "HTTP method"),
                @LocalizedText(locale = "zh-CN", text = "HTTP 方法")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.requestUrl", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Request URL"),
                @LocalizedText(locale = "zh-CN", text = "请求 URL")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.routePattern", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Route pattern"),
                @LocalizedText(locale = "zh-CN", text = "路由模板")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.handlerMethod", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Handler method"),
                @LocalizedText(locale = "zh-CN", text = "执行方法")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.reasonCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Reason code"),
                @LocalizedText(locale = "zh-CN", text = "原因代码")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.durationMs", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Duration (ms)"),
                @LocalizedText(locale = "zh-CN", text = "耗时（毫秒）")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.traceId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Trace ID"),
                @LocalizedText(locale = "zh-CN", text = "链路 ID")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.requestId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Request ID"),
                @LocalizedText(locale = "zh-CN", text = "请求 ID")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.details", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Audit fields"),
                @LocalizedText(locale = "zh-CN", text = "审计字段")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.copy", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Copy"),
                @LocalizedText(locale = "zh-CN", text = "复制")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "phase.COMMAND", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Command"),
                @LocalizedText(locale = "zh-CN", text = "命令")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "phase.EXECUTION", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Execution"),
                @LocalizedText(locale = "zh-CN", text = "执行")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "outcome.ACCEPTED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Accepted"),
                @LocalizedText(locale = "zh-CN", text = "已受理")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "outcome.SUCCEEDED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Succeeded"),
                @LocalizedText(locale = "zh-CN", text = "成功")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "outcome.FAILED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed"),
                @LocalizedText(locale = "zh-CN", text = "失败")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "outcome.PARTIAL_SUCCESS", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Partial success"),
                @LocalizedText(locale = "zh-CN", text = "部分成功")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "outcome.UNKNOWN", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown"),
                @LocalizedText(locale = "zh-CN", text = "结果未知")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "outcome.REJECTED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rejected"),
                @LocalizedText(locale = "zh-CN", text = "已拒绝")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "outcome.ROLLED_BACK", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rolled back"),
                @LocalizedText(locale = "zh-CN", text = "已回滚")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.schemaVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Schema version"),
                @LocalizedText(locale = "zh-CN", text = "协议版本")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.moduleCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Module"),
                @LocalizedText(locale = "zh-CN", text = "模块")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.actorType", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Actor type"),
                @LocalizedText(locale = "zh-CN", text = "操作者类型")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "detail.actorName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Actor name"),
                @LocalizedText(locale = "zh-CN", text = "操作者名称")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.limit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page size"),
                @LocalizedText(locale = "zh-CN", text = "每页条数")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.beforeTime", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Cursor time"),
                @LocalizedText(locale = "zh-CN", text = "游标时间")})
@PermissionMessage(pageCode = "tianshu.operationLogs", key = "filter.beforeId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Cursor event ID"),
                @LocalizedText(locale = "zh-CN", text = "游标事件 ID")})
final class DdcTianshuOperationLogsMessages {

    private DdcTianshuOperationLogsMessages() {
    }
}
