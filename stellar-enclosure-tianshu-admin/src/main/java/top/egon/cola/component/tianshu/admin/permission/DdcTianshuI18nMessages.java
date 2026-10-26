package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.addEntry", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Add entry"),
                @LocalizedText(locale = "zh-CN", text = "添加词条")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.addLocale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Add language"),
                @LocalizedText(locale = "zh-CN", text = "添加语言")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.createDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create draft"),
                @LocalizedText(locale = "zh-CN", text = "创建草稿")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.createPage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create page"),
                @LocalizedText(locale = "zh-CN", text = "新建页面")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.editPage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit {{pageCode}}"),
                @LocalizedText(locale = "zh-CN", text = "编辑 {{pageCode}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.publishDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish saved draft"),
                @LocalizedText(locale = "zh-CN", text = "发布已保存草稿")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.remove", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Remove"),
                @LocalizedText(locale = "zh-CN", text = "移除")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.removeLocale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Remove language {{locale}}"),
                @LocalizedText(locale = "zh-CN", text = "移除语言 {{locale}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.saveDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Save draft"),
                @LocalizedText(locale = "zh-CN", text = "保存草稿")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "actions.searchPages", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Search pages"),
                @LocalizedText(locale = "zh-CN", text = "查询页面")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "admin.description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manage navigation and page text in the fixed administration scope. Initialization creates and publishes missing bundles while preserving existing pages and drafts."),
                @LocalizedText(locale = "zh-CN", text = "在固定的管理台作用域中维护菜单与各页面文案。初始化只创建并发布缺失语言包，已有页面和草稿保持不变。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "admin.initialize", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Initialize default text"),
                @LocalizedText(locale = "zh-CN", text = "初始化默认文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "admin.initialized", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Created {{created}} bundles; skipped {{skipped}} existing pages"),
                @LocalizedText(locale = "zh-CN", text = "已创建 {{created}} 个语言包，跳过 {{skipped}} 个已有页面")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "admin.open", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View administration bundles"),
                @LocalizedText(locale = "zh-CN", text = "查看管理台语言包")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "admin.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Administration interface text"),
                @LocalizedText(locale = "zh-CN", text = "管理台界面文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "conflict.description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Compare the server draft, then choose how to proceed. Local content will not be overwritten automatically."),
                @LocalizedText(locale = "zh-CN", text = "请比较服务器草稿后选择如何继续；不会自动覆盖本地内容。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "conflict.keepLocal", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "I have compared the drafts; keep local content and continue editing"),
                @LocalizedText(locale = "zh-CN", text = "我已比较，保留本地内容继续编辑")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "conflict.loadServer", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Discard local edits and load the server draft"),
                @LocalizedText(locale = "zh-CN", text = "放弃本地修改，加载服务器草稿")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "conflict.serverRevision", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View server revision {{revision}}"),
                @LocalizedText(locale = "zh-CN", text = "查看服务器修订 {{revision}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "conflict.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Someone else modified this draft. Your local edits have been kept"),
                @LocalizedText(locale = "zh-CN", text = "草稿已被其他人修改，本地编辑已保留")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enterprise metadata center: manage page text by application, save drafts, and publish language bundles for frontend use."),
                @LocalizedText(locale = "zh-CN", text = "企业元数据中心：按应用维护页面文案，保存草稿并发布后供前端读取语言包。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "editor.create", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Create page language bundle"),
                @LocalizedText(locale = "zh-CN", text = "新建页面语言包")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "empty.entries", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No text yet. Add an entry"),
                @LocalizedText(locale = "zh-CN", text = "暂无文案，请添加词条")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "empty.pages", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No page language bundles for this application"),
                @LocalizedText(locale = "zh-CN", text = "当前应用暂无页面语言包")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "entries.deleteLabel", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete entry {{index}}"),
                @LocalizedText(locale = "zh-CN", text = "删除第 {{index}} 条文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "entries.keyLabel", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Message key for entry {{index}}"),
                @LocalizedText(locale = "zh-CN", text = "第 {{index}} 条文案 key")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "entries.label", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page translation entries"),
                @LocalizedText(locale = "zh-CN", text = "页面翻译词条")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "entries.textLabel", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{locale}} text for entry {{index}}"),
                @LocalizedText(locale = "zh-CN", text = "第 {{index}} 条 {{locale}} 文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "entries.total", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{total}} entries"),
                @LocalizedText(locale = "zh-CN", text = "共 {{total}} 条文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.draftFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to load page draft"),
                @LocalizedText(locale = "zh-CN", text = "读取页面草稿失败")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.forbidden", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to perform this action"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权执行此操作")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.latestDraftFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to load the latest draft: {{error}}"),
                @LocalizedText(locale = "zh-CN", text = "读取最新草稿失败：{{error}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.listFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to load pages"),
                @LocalizedText(locale = "zh-CN", text = "读取页面列表失败")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.pending", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Saving or publishing is in progress. Wait for it to finish"),
                @LocalizedText(locale = "zh-CN", text = "正在保存或发布，请等待操作完成")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.published", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Language bundle published"),
                @LocalizedText(locale = "zh-CN", text = "语言包已发布")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.refreshFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to refresh the page. Retry before continuing"),
                @LocalizedText(locale = "zh-CN", text = "无法刷新页面，请重试后继续操作")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "feedback.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft saved"),
                @LocalizedText(locale = "zh-CN", text = "草稿已保存")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "fields.defaultLocale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Default language"),
                @LocalizedText(locale = "zh-CN", text = "默认语言")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "fields.key", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Message key"),
                @LocalizedText(locale = "zh-CN", text = "文案 key")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "fields.name", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page name"),
                @LocalizedText(locale = "zh-CN", text = "页面名称")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "fields.newLocale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "New language code"),
                @LocalizedText(locale = "zh-CN", text = "新增语言代码")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "fields.pageCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page code"),
                @LocalizedText(locale = "zh-CN", text = "页面标识")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "fields.publishedVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publication status"),
                @LocalizedText(locale = "zh-CN", text = "发布状态")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "help.entries", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Provide every message in the default language. Blank translations fall back to the default language by key; placeholders such as {{example}} must match. Removing languages or entries affects consumers only after saving and publishing."),
                @LocalizedText(locale = "zh-CN", text = "默认语言须填写每条文案。其他语言留空会按 key 回退到默认语言；{{example}} 等占位符须保持一致。移除语言或删除文案后，保存草稿并发布才会影响页面读取。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "help.pageCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Cannot be changed after creation. common is the shared language bundle; page text can override shared keys with the same name."),
                @LocalizedText(locale = "zh-CN", text = "创建后不可修改。common 为公共语言包，页面文案可覆盖同名公共 key。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "help.publish", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Save before publishing. At least one entry in the default language is required."),
                @LocalizedText(locale = "zh-CN", text = "保存后可发布；至少需要一条默认语言文案。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "leave.continue", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Continue editing"),
                @LocalizedText(locale = "zh-CN", text = "继续编辑")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "leave.description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "This page has unsaved draft changes. Leaving will discard them."),
                @LocalizedText(locale = "zh-CN", text = "当前页面存在未保存的草稿，离开后这些修改将丢失。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "leave.discard", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Discard changes and leave"),
                @LocalizedText(locale = "zh-CN", text = "放弃修改并离开")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "leave.pendingDescription", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Wait for saving or publishing to finish before leaving the page."),
                @LocalizedText(locale = "zh-CN", text = "请等待保存或发布完成，再离开页面。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "leave.pendingTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Operation still in progress"),
                @LocalizedText(locale = "zh-CN", text = "操作尚未完成")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "leave.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Leave and discard unsaved changes?"),
                @LocalizedText(locale = "zh-CN", text = "离开并放弃未保存的修改？")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "list.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page language bundles"),
                @LocalizedText(locale = "zh-CN", text = "页面语言包")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "list.total", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{total}} pages"),
                @LocalizedText(locale = "zh-CN", text = "共 {{total}} 个页面")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "loading.draft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Loading page draft"),
                @LocalizedText(locale = "zh-CN", text = "正在加载页面草稿")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "locale.defaultSuffix", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = " (default)"),
                @LocalizedText(locale = "zh-CN", text = "（默认）")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "placeholders.defaultText", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Default language text (required)"),
                @LocalizedText(locale = "zh-CN", text = "默认语言文案（必填）")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "placeholders.key", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "actions.create"),
                @LocalizedText(locale = "zh-CN", text = "actions.create")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "placeholders.locale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "For example, en-US"),
                @LocalizedText(locale = "zh-CN", text = "例如 en-US")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "placeholders.pageCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "For example, order.list; use common for shared text"),
                @LocalizedText(locale = "zh-CN", text = "例如 order.list；公共文案使用 common")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "placeholders.translatedText", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Leave blank to use the default language"),
                @LocalizedText(locale = "zh-CN", text = "留空时使用默认语言")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Load published text and render it through the shared i18n SDK. Switch languages or refresh to inspect merged shared text and fallback translations."),
                @LocalizedText(locale = "zh-CN", text = "读取已发布文案并通过共享 i18n SDK 渲染。切换语言或刷新可查看公共文案合并和缺失翻译回退结果。")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.loadFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to load language bundle"),
                @LocalizedText(locale = "zh-CN", text = "语言包加载失败")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.loading", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Loading published language bundle"),
                @LocalizedText(locale = "zh-CN", text = "加载已发布语言包")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.locale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Preview language"),
                @LocalizedText(locale = "zh-CN", text = "预览语言")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.refresh", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refresh language bundle"),
                @LocalizedText(locale = "zh-CN", text = "刷新语言包")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.renderedText", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Frontend rendered text"),
                @LocalizedText(locale = "zh-CN", text = "前端渲染结果")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.showJson", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View language bundle JSON"),
                @LocalizedText(locale = "zh-CN", text = "查看语言包 JSON")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.stale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refresh failed. Showing the last successfully loaded published text"),
                @LocalizedText(locale = "zh-CN", text = "刷新失败，当前显示上次成功加载的已发布文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Published language bundle preview"),
                @LocalizedText(locale = "zh-CN", text = "已发布语言包预览")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "preview.unpublished", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish to preview the language bundle"),
                @LocalizedText(locale = "zh-CN", text = "发布后可预览语言包")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "scope.required", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select a business domain, namespace, environment, and application to search pages"),
                @LocalizedText(locale = "zh-CN", text = "请选择业务域、命名空间、环境和应用后查询页面")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "scope.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select application scope"),
                @LocalizedText(locale = "zh-CN", text = "选择应用范围")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "status.newDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "New draft"),
                @LocalizedText(locale = "zh-CN", text = "新草稿")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "status.notPublished", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Not yet published"),
                @LocalizedText(locale = "zh-CN", text = "尚未发布")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "status.publishedVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Published version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "已发布版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "status.revision", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft revision {{revision}}"),
                @LocalizedText(locale = "zh-CN", text = "草稿修订 {{revision}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "status.unpublished", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unpublished"),
                @LocalizedText(locale = "zh-CN", text = "未发布")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "status.unsaved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unsaved"),
                @LocalizedText(locale = "zh-CN", text = "未保存")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "status.version", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page translations"),
                @LocalizedText(locale = "zh-CN", text = "页面多语种")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.bundleSize", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The page language bundle exceeds 2 MiB. Reduce its text or split the page"),
                @LocalizedText(locale = "zh-CN", text = "页面语言包超过 2 MiB，请减少文案或拆分页面")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.defaultLocale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Languages must be unique and include the default language"),
                @LocalizedText(locale = "zh-CN", text = "语言不能重复，且必须包含默认语言")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.defaultText", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Default language {{locale}} is missing text for \"{{key}}\""),
                @LocalizedText(locale = "zh-CN", text = "默认语言 {{locale}} 缺少「{{key}}」的文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.duplicateKey", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Duplicate message key \"{{key}}\""),
                @LocalizedText(locale = "zh-CN", text = "文案 key「{{key}}」重复")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.entryLimit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Each page supports up to 2000 message entries"),
                @LocalizedText(locale = "zh-CN", text = "每个页面最多配置 2000 条文案")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.key", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Invalid message key \"{{key}}\": use letters, numbers, underscores, hyphens, or dot-separated segments, up to 128 characters"),
                @LocalizedText(locale = "zh-CN", text = "文案 key「{{key}}」无效：使用字母、数字、下划线、短横线或点分段，且不超过 128 字符")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.locale", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enter a valid language code, such as zh-CN or en-US"),
                @LocalizedText(locale = "zh-CN", text = "请输入有效的语言代码，例如 zh-CN 或 en-US")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.localeExists", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Language {{locale}} already exists"),
                @LocalizedText(locale = "zh-CN", text = "语言 {{locale}} 已存在")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.localeLimit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Each page supports up to 32 languages"),
                @LocalizedText(locale = "zh-CN", text = "每个页面最多配置 32 种语言")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.name", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page name is required and must not exceed 128 characters"),
                @LocalizedText(locale = "zh-CN", text = "页面名称不能为空，且不能超过 128 个字符")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.pageCode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Page code must use letters, numbers, underscores, hyphens, or dot-separated segments, up to 128 characters"),
                @LocalizedText(locale = "zh-CN", text = "页面标识须为不超过 128 字符的字母、数字、下划线、短横线或点分段")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.placeholderSyntax", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Invalid placeholder syntax. Use {{example}} or {{alternate}}"),
                @LocalizedText(locale = "zh-CN", text = "占位符格式无效，请使用 {{example}} 或 {{alternate}}")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.placeholders", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The {{locale}} placeholders for \"{{key}}\" must match the default language (for example, {{example}})"),
                @LocalizedText(locale = "zh-CN", text = "「{{key}}」的 {{locale}} 占位符必须与默认语言一致（例如 {{example}}）")})
@PermissionMessage(pageCode = "tianshu.i18n", key = "validation.textLimit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The {{locale}} text for \"{{key}}\" must not exceed 8192 characters"),
                @LocalizedText(locale = "zh-CN", text = "「{{key}}」的 {{locale}} 文案不能超过 8192 个字符")})
final class DdcTianshuI18nMessages {
    private DdcTianshuI18nMessages() { }
}
