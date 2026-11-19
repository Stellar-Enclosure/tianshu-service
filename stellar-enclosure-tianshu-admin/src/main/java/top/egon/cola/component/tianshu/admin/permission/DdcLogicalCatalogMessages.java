package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionDefinition;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source labels and scope for the logical catalog permission producer. */
@PermissionDefinition(code = DdcLogicalCatalogContract.SCOPE,
        meaning = "Read authorized tenant-scoped logical business and application metadata",
        labelPage = "tianshu.permissions", labelKey = "permission.logicalCatalogRead")
@PermissionMessage(pageCode = "tianshu.permissions", key = "permission.logicalCatalogRead", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Read authorized tenant-scoped logical business and application metadata"),
                @LocalizedText(locale = "zh-CN", text = "读取已授权租户的逻辑业务与应用元数据")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.logicalCatalog.findBusiness", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Read a logical business by identifier"),
                @LocalizedText(locale = "zh-CN", text = "按标识查询逻辑业务")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.logicalCatalog.listBusinesses", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "List logical businesses"),
                @LocalizedText(locale = "zh-CN", text = "列出逻辑业务")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.logicalCatalog.findApplication", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Read a logical application by identifier"),
                @LocalizedText(locale = "zh-CN", text = "按标识查询逻辑应用")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.logicalCatalog.listApplications", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "List logical applications"),
                @LocalizedText(locale = "zh-CN", text = "列出逻辑应用")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.business.id", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Business identifier"),
                @LocalizedText(locale = "zh-CN", text = "业务标识")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.business.bizCode", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Business code"),
                @LocalizedText(locale = "zh-CN", text = "业务编码")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.business.bizName", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Business name"),
                @LocalizedText(locale = "zh-CN", text = "业务名称")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.business.enabled", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Business enabled"),
                @LocalizedText(locale = "zh-CN", text = "业务启用状态")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.application.id", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Application identifier"),
                @LocalizedText(locale = "zh-CN", text = "应用标识")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.application.businessId", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Owning business identifier"),
                @LocalizedText(locale = "zh-CN", text = "所属业务标识")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.application.bizCode", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Owning business code"),
                @LocalizedText(locale = "zh-CN", text = "所属业务编码")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.application.appCode", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Application code"),
                @LocalizedText(locale = "zh-CN", text = "应用编码")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.application.appName", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Application name"),
                @LocalizedText(locale = "zh-CN", text = "应用名称")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.application.enabled", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Application enabled"),
                @LocalizedText(locale = "zh-CN", text = "应用启用状态")
        })
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.logicalCatalog.application.businessEnabled", defaultLocale = "en-US",
        defaults = {
                @LocalizedText(locale = "en-US", text = "Owning business enabled"),
                @LocalizedText(locale = "zh-CN", text = "所属业务启用状态")
        })
public final class DdcLogicalCatalogMessages {
    private DdcLogicalCatalogMessages() {
    }
}
