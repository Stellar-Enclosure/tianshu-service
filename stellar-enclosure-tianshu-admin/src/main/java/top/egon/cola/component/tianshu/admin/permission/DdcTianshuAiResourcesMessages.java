package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.confirmDelete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm deletion"),
                @LocalizedText(locale = "zh-CN", text = "确认删除")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.createDenied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to create resources in this scope"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权在此范围创建资源")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.createDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create draft"),
                @LocalizedText(locale = "zh-CN", text = "创建草稿")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.createKind", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create {{kind}}"),
                @LocalizedText(locale = "zh-CN", text = "新建 {{kind}}")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.createKindDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create {{kind}} draft"),
                @LocalizedText(locale = "zh-CN", text = "新建 {{kind}} 草稿")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.created", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft resource created"),
                @LocalizedText(locale = "zh-CN", text = "草稿资源已创建")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.currentVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Current version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "当前版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.deleteDenied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to delete this resource"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权删除此资源")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.deleteTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete {{name}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除 {{name}}？")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.deleteWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Runtime reads will become unavailable immediately. Historical versions are retained; reusing this key will not restore the deleted resource."),
                @LocalizedText(locale = "zh-CN", text = "运行读取将立即不可用，历史版本仍会保留；该资源键不会复活为旧资源。")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.deleted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource deleted; historical versions are retained"),
                @LocalizedText(locale = "zh-CN", text = "资源已删除；历史版本仍保留")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage editable drafts within the selected four-part scope. Saving a draft does not publish a runtime version."),
                @LocalizedText(locale = "zh-CN", text = "管理当前四维范围内的可编辑草稿。保存草稿不会发布运行版本。")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.displayName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Display name"),
                @LocalizedText(locale = "zh-CN", text = "显示名称")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.emptyKind", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No {{kind}} resources in this scope"),
                @LocalizedText(locale = "zh-CN", text = "当前范围暂无 {{kind}} 资源")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.initialFileHint", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The initial file can be renamed or deleted in the editor."),
                @LocalizedText(locale = "zh-CN", text = "初始文件名可在编辑器中修改或删除。")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.keyPrefix", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource key prefix"),
                @LocalizedText(locale = "zh-CN", text = "资源键前缀")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.kind.PROMPT", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Prompt"),
                @LocalizedText(locale = "zh-CN", text = "Prompt")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.kind.SKILL", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Skill"),
                @LocalizedText(locale = "zh-CN", text = "Skill")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.lastModified", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Last modified"),
                @LocalizedText(locale = "zh-CN", text = "最后修改")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.promptContent", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Prompt content"),
                @LocalizedText(locale = "zh-CN", text = "Prompt 正文")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.promptDrafts", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Prompt drafts"),
                @LocalizedText(locale = "zh-CN", text = "Prompt 草稿资源")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.promptTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Prompt management"),
                @LocalizedText(locale = "zh-CN", text = "Prompt 管理")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.publicationStatus", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publication status"),
                @LocalizedText(locale = "zh-CN", text = "发布状态")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.readDenied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to read this scope"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权读取此范围")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.readFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to load resources; the result has not been treated as an empty list"),
                @LocalizedText(locale = "zh-CN", text = "读取资源失败；结果未被当作空列表处理")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.resourceKey", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource key"),
                @LocalizedText(locale = "zh-CN", text = "资源键")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.scopeRequired", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select a business domain, namespace, environment, and application before searching."),
                @LocalizedText(locale = "zh-CN", text = "请选择完整的业务域、命名空间、环境和应用后查询。")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.skillDrafts", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Skill drafts"),
                @LocalizedText(locale = "zh-CN", text = "Skill 草稿资源")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.skillInitialContent", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Initial SKILL.md content"),
                @LocalizedText(locale = "zh-CN", text = "SKILL.md 初始正文")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.skillTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Skill management"),
                @LocalizedText(locale = "zh-CN", text = "Skill 管理")})
@PermissionMessage(pageCode = "tianshu.ai.resources", key = "resources.unpublished", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unpublished"),
                @LocalizedText(locale = "zh-CN", text = "未发布")})
final class DdcTianshuAiResourcesMessages {
    private DdcTianshuAiResourcesMessages() { }
}
