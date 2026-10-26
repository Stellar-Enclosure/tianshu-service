package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.cache", key = "actions.check", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Check cache"),
                @LocalizedText(locale = "zh-CN", text = "检查缓存")})
@PermissionMessage(pageCode = "tianshu.cache", key = "actions.rebuild", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rebuild cache"),
                @LocalizedText(locale = "zh-CN", text = "重建缓存")})
@PermissionMessage(pageCode = "tianshu.cache", key = "confirm.rebuild", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rebuild the cache for this scope?"),
                @LocalizedText(locale = "zh-CN", text = "确认重建该作用域下的缓存？")})
@PermissionMessage(pageCode = "tianshu.cache", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Check database and Redis cache consistency by physical scope and page."),
                @LocalizedText(locale = "zh-CN", text = "按物理作用域分页检查数据库与 Redis 的缓存一致性。")})
@PermissionMessage(pageCode = "tianshu.cache", key = "empty.selectScope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select a complete scope and run a check."),
                @LocalizedText(locale = "zh-CN", text = "请选择完整作用域并执行检查。")})
@PermissionMessage(pageCode = "tianshu.cache", key = "feedback.rebuilt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rebuilt {{count}} cache entries"),
                @LocalizedText(locale = "zh-CN", text = "已重建 {{count}} 项缓存")})
@PermissionMessage(pageCode = "tianshu.cache", key = "fields.databaseValue", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Database value"),
                @LocalizedText(locale = "zh-CN", text = "DB 值")})
@PermissionMessage(pageCode = "tianshu.cache", key = "fields.databaseVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Database version"),
                @LocalizedText(locale = "zh-CN", text = "DB 版本")})
@PermissionMessage(pageCode = "tianshu.cache", key = "fields.matched", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Consistency"),
                @LocalizedText(locale = "zh-CN", text = "一致性")})
@PermissionMessage(pageCode = "tianshu.cache", key = "fields.redisValue", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Redis value"),
                @LocalizedText(locale = "zh-CN", text = "Redis 值")})
@PermissionMessage(pageCode = "tianshu.cache", key = "fields.redisVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Redis version"),
                @LocalizedText(locale = "zh-CN", text = "Redis 版本")})
@PermissionMessage(pageCode = "tianshu.cache", key = "fields.resourceName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Configuration resource"),
                @LocalizedText(locale = "zh-CN", text = "配置资源")})
@PermissionMessage(pageCode = "tianshu.cache", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Cache check results"),
                @LocalizedText(locale = "zh-CN", text = "缓存检查结果")})
@PermissionMessage(pageCode = "tianshu.cache", key = "stats.matched", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Matched on this page"),
                @LocalizedText(locale = "zh-CN", text = "本页一致")})
@PermissionMessage(pageCode = "tianshu.cache", key = "stats.mismatched", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Mismatched on this page"),
                @LocalizedText(locale = "zh-CN", text = "本页不一致")})
@PermissionMessage(pageCode = "tianshu.cache", key = "status.matched", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Matched"),
                @LocalizedText(locale = "zh-CN", text = "一致")})
@PermissionMessage(pageCode = "tianshu.cache", key = "status.mismatched", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Mismatched"),
                @LocalizedText(locale = "zh-CN", text = "不一致")})
@PermissionMessage(pageCode = "tianshu.cache", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Cache management"),
                @LocalizedText(locale = "zh-CN", text = "缓存管理")})
@PermissionMessage(pageCode = "tianshu.cache", key = "validation.checkScope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Check a complete scope first"),
                @LocalizedText(locale = "zh-CN", text = "请先检查完整作用域")})
@PermissionMessage(pageCode = "tianshu.cache", key = "validation.scopeRequired", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enter bizCode / env / appCode"),
                @LocalizedText(locale = "zh-CN", text = "请填写 bizCode / env / appCode")})
final class DdcTianshuCacheMessages {
    private DdcTianshuCacheMessages() { }
}
