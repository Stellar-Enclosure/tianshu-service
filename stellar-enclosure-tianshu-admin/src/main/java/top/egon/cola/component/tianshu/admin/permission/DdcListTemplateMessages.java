package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

@PermissionMessage(pageCode = "tianshu.permissions", key = "api.listTemplate.load", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read list templates"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.listTemplate.saveSystem", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Save a system list template"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.listTemplate.savePersonal", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Save a personal list template"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.listTemplate.deleteSystem", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Delete a system list template"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.listTemplate.deletePersonal", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Delete a personal list template"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.listTemplate.select", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Select a list template"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.listTemplate.delegate", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Access list templates through an authorized business service"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.namespace.export", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Export the namespace list"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.columnKey", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Column key"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.columnKeys", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "List columns"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.definitionDigest", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "List definition digest"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.templateType", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Template type"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.defaultTemplate", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Default template"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.templates", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "List templates"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.mayManageSystem", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "May manage system templates"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.mayManagePersonal", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "May manage personal templates"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.selectedTemplateId", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Selected template identifier"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.keyword", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Search keyword"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.pageNo", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Page number"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.pageSize", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Page size"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "field.exportRange", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Export range"))
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.selected", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "当前字段模板"), @LocalizedText(locale = "en-US", text = "Current field template")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.defaultView", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "默认视图"), @LocalizedText(locale = "en-US", text = "Default view")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.system", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "系统模板"), @LocalizedText(locale = "en-US", text = "System template")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.personal", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "个人模板"), @LocalizedText(locale = "en-US", text = "Personal template")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.newPersonal", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "新建个人模板"), @LocalizedText(locale = "en-US", text = "New personal template")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.newSystem", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "新建系统模板"), @LocalizedText(locale = "en-US", text = "New system template")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.edit", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "编辑模板"), @LocalizedText(locale = "en-US", text = "Edit template")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.exportRange", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "导出范围"), @LocalizedText(locale = "en-US", text = "Export range")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.filtered", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "当前筛选结果"), @LocalizedText(locale = "en-US", text = "Filtered results")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.currentPage", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "当前页"), @LocalizedText(locale = "en-US", text = "Current page")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.export", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "导出 Excel"), @LocalizedText(locale = "en-US", text = "Export Excel")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.deleteConfirm", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "确认删除此模板？"), @LocalizedText(locale = "en-US", text = "Delete this template?")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.name", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "模板名称"), @LocalizedText(locale = "en-US", text = "Template name")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.setDefault", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "设为默认"), @LocalizedText(locale = "en-US", text = "Set as default")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.moveUp", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "上移"), @LocalizedText(locale = "en-US", text = "Move up")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.moveDown", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "下移"), @LocalizedText(locale = "en-US", text = "Move down")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.saved", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "模板已保存"), @LocalizedText(locale = "en-US", text = "Template saved")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.savedApplyFailed", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "模板已保存，但未能切换，请重新选择。"), @LocalizedText(locale = "en-US", text = "Template saved, but could not be selected. Please select it again.")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.deleted", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "模板已删除"), @LocalizedText(locale = "en-US", text = "Template deleted")})
@PermissionMessage(pageCode = "tianshu.namespaces", key = "templates.unavailable", defaultLocale = "zh-CN", defaults = {
        @LocalizedText(locale = "zh-CN", text = "{{count}} 个字段已不在当前列表定义中，保存后将移除。"),
        @LocalizedText(locale = "en-US", text = "{{count}} fields are no longer in the current list and will be removed on save.")})
public final class DdcListTemplateMessages {
    private DdcListTemplateMessages() { }
}
