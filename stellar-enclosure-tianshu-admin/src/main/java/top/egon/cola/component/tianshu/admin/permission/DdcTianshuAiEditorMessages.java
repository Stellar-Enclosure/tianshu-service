package top.egon.cola.component.tianshu.admin.permission;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source defaults exported by the standard permission manifest compiler. */
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.acknowledgeConflict", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "I have compared the copies; continue saving my changes"),
                @LocalizedText(locale = "zh-CN", text = "我已比较，继续保存本地修改")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.addDirectory", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Add directory"),
                @LocalizedText(locale = "zh-CN", text = "新增目录")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.addFile", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Add file"),
                @LocalizedText(locale = "zh-CN", text = "新增文件")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.addTextFile", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Add text file"),
                @LocalizedText(locale = "zh-CN", text = "新增文本文件")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.availableDirectories", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Available directories: {{directories}}"),
                @LocalizedText(locale = "zh-CN", text = "可选目录：{{directories}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.backToList", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Back to resources"),
                @LocalizedText(locale = "zh-CN", text = "返回资源列表")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.compareInstructions", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Compare the two copies manually. Local edits will be saved against the new revision only after you confirm."),
                @LocalizedText(locale = "zh-CN", text = "请手工比较两份内容。确认后才会使用新修订号保存本地编辑。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.confirmAdd", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm addition"),
                @LocalizedText(locale = "zh-CN", text = "确认新增")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.confirmDelete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm deletion"),
                @LocalizedText(locale = "zh-CN", text = "确认删除")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.confirmDeleteNode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm node deletion"),
                @LocalizedText(locale = "zh-CN", text = "确认删除节点")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.confirmMove", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm move"),
                @LocalizedText(locale = "zh-CN", text = "确认移动")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.confirmRename", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm rename"),
                @LocalizedText(locale = "zh-CN", text = "确认重命名")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.conflictLoaded", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Server revision {{revision}} loaded; your local draft is retained"),
                @LocalizedText(locale = "zh-CN", text = "服务器修订 {{revision}} 已加载；本地草稿仍保留")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.continueEditing", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Continue editing"),
                @LocalizedText(locale = "zh-CN", text = "继续编辑")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.currentVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Current published version"),
                @LocalizedText(locale = "zh-CN", text = "当前发布版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deleteDirtyWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Runtime reads will become unavailable immediately. Historical versions are retained; unsaved local changes will be discarded."),
                @LocalizedText(locale = "zh-CN", text = "运行读取将立即不可用，历史版本仍会保留；当前未保存的本地修改也会丢弃。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deleteNode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete node"),
                @LocalizedText(locale = "zh-CN", text = "删除节点")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deleteNodeTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete {{path}}?"),
                @LocalizedText(locale = "zh-CN", text = "删除 {{path}}？")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deleteNodeWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete {{count}} nodes and their draft text. This change takes effect after saving."),
                @LocalizedText(locale = "zh-CN", text = "将删除 {{count}} 个节点及其草稿文本；此操作需保存后生效。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deleteResource", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete resource"),
                @LocalizedText(locale = "zh-CN", text = "删除资源")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deleteTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Delete {{name}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认删除 {{name}}？")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deleteWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Runtime reads will become unavailable immediately. Historical versions are retained. You will return to the resource list after deletion."),
                @LocalizedText(locale = "zh-CN", text = "运行读取将立即不可用，历史版本仍会保留。删除后将返回资源列表。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.deletedNotice", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource deleted; historical versions are retained"),
                @LocalizedText(locale = "zh-CN", text = "资源已删除；历史版本仍会保留")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.description", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The editor saves plain-text drafts. Published versions are read-only; saving a draft does not change the runtime version."),
                @LocalizedText(locale = "zh-CN", text = "编辑区仅保存纯文本草稿；发布版本只读，草稿保存不会切换运行版本。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.directory", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Directory"),
                @LocalizedText(locale = "zh-CN", text = "目录")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.directoryHint", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Directories have no content. All files are edited as raw text."),
                @LocalizedText(locale = "zh-CN", text = "目录没有正文；所有文件均作为原始文本编辑。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.directoryPath", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Directory path"),
                @LocalizedText(locale = "zh-CN", text = "目录路径")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.directorySeparator", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = ", "),
                @LocalizedText(locale = "zh-CN", text = "、")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.discardAndContinue", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Discard changes and continue"),
                @LocalizedText(locale = "zh-CN", text = "放弃修改并继续")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.discardChanges", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Discard changes"),
                @LocalizedText(locale = "zh-CN", text = "放弃修改")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.discardTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Discard local draft changes?"),
                @LocalizedText(locale = "zh-CN", text = "放弃本地草稿修改？")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.discardWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unsaved local content will be replaced with the server draft."),
                @LocalizedText(locale = "zh-CN", text = "本地未保存的内容将被服务器草稿替换。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.draftRevision", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft revision"),
                @LocalizedText(locale = "zh-CN", text = "草稿修订")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.draftStatus", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft status"),
                @LocalizedText(locale = "zh-CN", text = "草稿状态")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.draftTab", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft editor"),
                @LocalizedText(locale = "zh-CN", text = "草稿编辑")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.draftUnsaved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The current draft has not been saved"),
                @LocalizedText(locale = "zh-CN", text = "当前草稿尚未保存")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.file", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "File"),
                @LocalizedText(locale = "zh-CN", text = "文件")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.fileContent", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "File content: {{path}}"),
                @LocalizedText(locale = "zh-CN", text = "文件正文: {{path}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.filePath", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "File path"),
                @LocalizedText(locale = "zh-CN", text = "文件路径")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.fullScope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Full scope"),
                @LocalizedText(locale = "zh-CN", text = "完整范围")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.historyPreservesDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Your local draft is retained. Switching historical versions will not overwrite unsaved edits."),
                @LocalizedText(locale = "zh-CN", text = "本地草稿仍保留；切换历史版本不会覆盖未保存编辑。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.invalidMoveTarget", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The target directory is invalid, the move would create a cycle, or the target path already exists"),
                @LocalizedText(locale = "zh-CN", text = "目标目录无效、移动会形成环或目标路径已存在")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.invalidNewPath", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The path is invalid, its parent directory does not exist, or the target path already exists"),
                @LocalizedText(locale = "zh-CN", text = "路径无效、父目录不存在或目标路径已存在")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.invalidRenamePath", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The new path is invalid, its parent directory does not exist, or it conflicts with an existing node"),
                @LocalizedText(locale = "zh-CN", text = "新路径无效、父目录不存在或会与现有节点冲突")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.keepAndSwitch", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Keep changes and switch"),
                @LocalizedText(locale = "zh-CN", text = "保留并切换")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.lastModified", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Last modified"),
                @LocalizedText(locale = "zh-CN", text = "最后修改")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.leaveAndDiscard", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Leave and discard local changes"),
                @LocalizedText(locale = "zh-CN", text = "离开并放弃本地修改")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.leaveSessionWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Continuing will switch or clear the current identity session and discard unsaved local content. Continue?"),
                @LocalizedText(locale = "zh-CN", text = "继续会切换或清除当前身份会话，本地未保存内容将丢失。是否确认？")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.leaveTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Leave the editor?"),
                @LocalizedText(locale = "zh-CN", text = "离开编辑页？")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.loading", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Loading AI draft"),
                @LocalizedText(locale = "zh-CN", text = "读取 AI 草稿")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.loadingConflict", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Save conflict; loading the latest server draft"),
                @LocalizedText(locale = "zh-CN", text = "保存冲突；正在读取服务器最新草稿")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.move", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Move"),
                @LocalizedText(locale = "zh-CN", text = "移动")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.moveNode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Move node"),
                @LocalizedText(locale = "zh-CN", text = "移动节点")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.newDirectoryDefaultName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "new-directory"),
                @LocalizedText(locale = "zh-CN", text = "新建目录")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.newFileDefaultName", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "new-file.txt"),
                @LocalizedText(locale = "zh-CN", text = "新建文件.txt")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.newPath", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "New path"),
                @LocalizedText(locale = "zh-CN", text = "新路径")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.operationDenied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to perform this operation"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权执行此操作")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.operationFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft operation failed"),
                @LocalizedText(locale = "zh-CN", text = "草稿操作失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.optimizationTab", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Feedback and optimization"),
                @LocalizedText(locale = "zh-CN", text = "反馈与优化")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.pendingLeaveWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The publication result is unknown. Verify the original receipt or retry with the same request ID first."),
                @LocalizedText(locale = "zh-CN", text = "发布结果未知；先核验原请求回执或使用原请求号重试。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.pendingSessionWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The publication result has not been confirmed. Verify the original request or retry with the same request ID before switching identity sessions."),
                @LocalizedText(locale = "zh-CN", text = "发布操作结果尚未确认。请先核验原请求或使用原请求号重试；此时不能切换身份会话。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.promptContent", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Prompt content"),
                @LocalizedText(locale = "zh-CN", text = "Prompt 正文")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.promptPlainText", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Prompt content (plain text)"),
                @LocalizedText(locale = "zh-CN", text = "Prompt 正文（纯文本）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.promptTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit Prompt draft"),
                @LocalizedText(locale = "zh-CN", text = "Prompt 草稿编辑")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.publicationUnverified", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publication has not been verified"),
                @LocalizedText(locale = "zh-CN", text = "发布操作尚未核验")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.readFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to load this resource"),
                @LocalizedText(locale = "zh-CN", text = "无法读取此资源")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.refreshUnavailable", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The current session cannot refresh the resource status; local edits are retained"),
                @LocalizedText(locale = "zh-CN", text = "当前会话无法刷新资源状态；本地编辑仍保留")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.rename", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rename"),
                @LocalizedText(locale = "zh-CN", text = "重命名")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.renameNode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rename node"),
                @LocalizedText(locale = "zh-CN", text = "重命名节点")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.resourceKey", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource key"),
                @LocalizedText(locale = "zh-CN", text = "资源键")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.returnToVerification", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Return and continue verification"),
                @LocalizedText(locale = "zh-CN", text = "返回并继续核验")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.rootDirectory", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "(root directory)"),
                @LocalizedText(locale = "zh-CN", text = "（根目录）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.saveDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Save draft"),
                @LocalizedText(locale = "zh-CN", text = "保存草稿")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Saved"),
                @LocalizedText(locale = "zh-CN", text = "已保存")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.savedNotice", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Draft saved; the current published version is unchanged"),
                @LocalizedText(locale = "zh-CN", text = "草稿已保存；当前发布版本未改变")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.selectFile", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select a text file"),
                @LocalizedText(locale = "zh-CN", text = "选择一个文本文件")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.sessionFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Identity session operation failed"),
                @LocalizedText(locale = "zh-CN", text = "身份会话操作失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.skillRoot", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Skill root (directory)"),
                @LocalizedText(locale = "zh-CN", text = "Skill 文件根目录（目录）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.skillTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Edit Skill draft"),
                @LocalizedText(locale = "zh-CN", text = "Skill 草稿编辑")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.skillTree", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Skill file tree"),
                @LocalizedText(locale = "zh-CN", text = "Skill 文件树")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.stayInFile", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Stay in this file"),
                @LocalizedText(locale = "zh-CN", text = "留在当前文件")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.stayInSession", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Stay in this session"),
                @LocalizedText(locale = "zh-CN", text = "留在当前会话")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.switchFileWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Switching files will retain the local tree and text. Confirm to select another node."),
                @LocalizedText(locale = "zh-CN", text = "继续切换文件会保留当前本地树和文本，确认后再选择其他节点。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.targetDirectory", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target directory path"),
                @LocalizedText(locale = "zh-CN", text = "目标目录路径")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.targetDirectoryHint", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Leave blank for the root directory. The target must be an existing directory."),
                @LocalizedText(locale = "zh-CN", text = "留空表示根目录；目标必须是现有目录。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.treeNode", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{name}} ({{type}})"),
                @LocalizedText(locale = "zh-CN", text = "{{name}}（{{type}}）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.unavailable", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource content is unavailable"),
                @LocalizedText(locale = "zh-CN", text = "资源内容不可用")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.unpublished", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Not published yet"),
                @LocalizedText(locale = "zh-CN", text = "尚未发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.unsaved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unsaved"),
                @LocalizedText(locale = "zh-CN", text = "未保存")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.unsavedChanges", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The current draft has unsaved changes"),
                @LocalizedText(locale = "zh-CN", text = "当前草稿有未保存修改")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.unsavedLeaveWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The current draft has not been saved. Leaving will discard local changes."),
                @LocalizedText(locale = "zh-CN", text = "当前草稿尚未保存，离开会丢弃本地修改。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.verifyBeforeLeaving", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Return to verification"),
                @LocalizedText(locale = "zh-CN", text = "返回核验")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "editor.versionsTab", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Versions and publication"),
                @LocalizedText(locale = "zh-CN", text = "版本与发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.allOutcomes", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "All outcomes"),
                @LocalizedText(locale = "zh-CN", text = "全部结果")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.allScoreSources", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "All sources"),
                @LocalizedText(locale = "zh-CN", text = "全部来源")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.caller", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Caller"),
                @LocalizedText(locale = "zh-CN", text = "调用方")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.filter", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Filter"),
                @LocalizedText(locale = "zh-CN", text = "筛选")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.inputExcerpt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Input excerpt"),
                @LocalizedText(locale = "zh-CN", text = "输入摘录")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.judgeState", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scoring status"),
                @LocalizedText(locale = "zh-CN", text = "评分状态")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.judgeState.DONE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scored"),
                @LocalizedText(locale = "zh-CN", text = "已评分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.judgeState.FAILED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scoring failed"),
                @LocalizedText(locale = "zh-CN", text = "评分失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.judgeState.JUDGING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scoring"),
                @LocalizedText(locale = "zh-CN", text = "评分中")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.judgeState.NOT_REQUIRED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Not required"),
                @LocalizedText(locale = "zh-CN", text = "无需评分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.judgeState.PENDING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Pending"),
                @LocalizedText(locale = "zh-CN", text = "待评分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.latency", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Latency (ms)"),
                @LocalizedText(locale = "zh-CN", text = "耗时(ms)")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.maxScore", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Maximum score"),
                @LocalizedText(locale = "zh-CN", text = "最高分数")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.maxScorePlaceholder", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Score ≤"),
                @LocalizedText(locale = "zh-CN", text = "分数 ≤")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.notReported", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "(not reported)"),
                @LocalizedText(locale = "zh-CN", text = "（未上报）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.occurredAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Occurred at"),
                @LocalizedText(locale = "zh-CN", text = "发生时间")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.outcome", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Outcome"),
                @LocalizedText(locale = "zh-CN", text = "结果")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.outcome.FAILURE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failure"),
                @LocalizedText(locale = "zh-CN", text = "失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.outcome.SUCCESS", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Success"),
                @LocalizedText(locale = "zh-CN", text = "成功")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.outcome.UNKNOWN", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unknown"),
                @LocalizedText(locale = "zh-CN", text = "未知")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.outcomeFilter", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Filter by outcome"),
                @LocalizedText(locale = "zh-CN", text = "结果过滤")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.outputExcerpt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Output excerpt"),
                @LocalizedText(locale = "zh-CN", text = "输出摘录")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.readDenied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to view usage feedback"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权查看使用反馈")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.score", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Score"),
                @LocalizedText(locale = "zh-CN", text = "分数")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.scoreSource", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Score source"),
                @LocalizedText(locale = "zh-CN", text = "分数来源")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.scoreSourceFilter", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Filter by score source"),
                @LocalizedText(locale = "zh-CN", text = "分数来源过滤")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.source.EXPLICIT", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Explicit score"),
                @LocalizedText(locale = "zh-CN", text = "显式评分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.source.JUDGE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "LLM score"),
                @LocalizedText(locale = "zh-CN", text = "LLM 评分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.source.NONE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No score"),
                @LocalizedText(locale = "zh-CN", text = "无分数")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Usage feedback"),
                @LocalizedText(locale = "zh-CN", text = "使用反馈")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.traceAndJudgedAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Trace ID: {{traceId}}; scored at: {{judgedAt}}"),
                @LocalizedText(locale = "zh-CN", text = "traceId：{{traceId}}；评分时间：{{judgedAt}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.usageId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Usage ID"),
                @LocalizedText(locale = "zh-CN", text = "usageId")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.utcTime", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "UTC {{value}}"),
                @LocalizedText(locale = "zh-CN", text = "UTC {{value}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.version", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version"),
                @LocalizedText(locale = "zh-CN", text = "版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "feedback.versionNo", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version number"),
                @LocalizedText(locale = "zh-CN", text = "版本号")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.all", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "All"),
                @LocalizedText(locale = "zh-CN", text = "全部")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.alreadyRunning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "An optimization run is already in progress"),
                @LocalizedText(locale = "zh-CN", text = "已有优化运行在进行中")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.autoPublish", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Automatically publish optimized results"),
                @LocalizedText(locale = "zh-CN", text = "自动发布优化结果")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.autoPublishedSuffix", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "(automatically published)"),
                @LocalizedText(locale = "zh-CN", text = "（已自动发布）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.averageScore", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Average score"),
                @LocalizedText(locale = "zh-CN", text = "平均分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.baseVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Baseline version"),
                @LocalizedText(locale = "zh-CN", text = "基线版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.channelDisabled", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The optimization channel is disabled: egon.cola.component.tianshu.admin.ai.optimization.enabled is not enabled on the admin server"),
                @LocalizedText(locale = "zh-CN", text = "优化通道未启用：管理端未开启 egon.cola.component.tianshu.admin.ai.optimization.enabled")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.conflict", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The policy was changed by someone else. Your input is retained; refresh the revision and save again."),
                @LocalizedText(locale = "zh-CN", text = "策略已被他人修改；已保留你的输入，请刷新修订后重新保存")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.consecutiveFailures", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Consecutive failures"),
                @LocalizedText(locale = "zh-CN", text = "连续失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.convergedAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Converged at"),
                @LocalizedText(locale = "zh-CN", text = "收敛时间")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.cooldown", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Cooldown"),
                @LocalizedText(locale = "zh-CN", text = "冷却时间")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.currentRun", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Current run"),
                @LocalizedText(locale = "zh-CN", text = "当前运行")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.draftSuffix", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "(draft)"),
                @LocalizedText(locale = "zh-CN", text = "（草稿）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.enable", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enable automatic optimization"),
                @LocalizedText(locale = "zh-CN", text = "启用自动优化")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.enableJudge", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Enable LLM scoring"),
                @LocalizedText(locale = "zh-CN", text = "启用 LLM 评分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.error", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Error"),
                @LocalizedText(locale = "zh-CN", text = "错误")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.evaluatedVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Evaluated version"),
                @LocalizedText(locale = "zh-CN", text = "评估版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.evaluationWindow", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Evaluation window"),
                @LocalizedText(locale = "zh-CN", text = "评估窗口")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.instruction", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimization instructions"),
                @LocalizedText(locale = "zh-CN", text = "优化指令")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.instructionPlaceholder", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Additional constraints for the optimizer model (optional)"),
                @LocalizedText(locale = "zh-CN", text = "附加给优化模型的约束（可选）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.iterations", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Iterations"),
                @LocalizedText(locale = "zh-CN", text = "迭代")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.judgeAlias", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scoring model alias"),
                @LocalizedText(locale = "zh-CN", text = "评分模型别名")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.judgeModel", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scoring model"),
                @LocalizedText(locale = "zh-CN", text = "评分模型")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.lastEvaluated", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Last evaluation"),
                @LocalizedText(locale = "zh-CN", text = "最近评估")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.manual", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manual"),
                @LocalizedText(locale = "zh-CN", text = "手动")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.manualOptimization", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Manual optimization"),
                @LocalizedText(locale = "zh-CN", text = "手动优化")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.maxIterations", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Maximum iterations"),
                @LocalizedText(locale = "zh-CN", text = "最多迭代")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.minSamples", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Minimum samples"),
                @LocalizedText(locale = "zh-CN", text = "最少样本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.notePlaceholder", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Notes (optional)"),
                @LocalizedText(locale = "zh-CN", text = "备注（可选）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.optimizerAlias", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimizer model alias"),
                @LocalizedText(locale = "zh-CN", text = "优化模型别名")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.optimizerModel", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimizer model"),
                @LocalizedText(locale = "zh-CN", text = "优化模型")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.policy", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimization policy"),
                @LocalizedText(locale = "zh-CN", text = "优化策略")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.policyState", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Policy status"),
                @LocalizedText(locale = "zh-CN", text = "策略状态")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.policyState.ARMED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Awaiting evaluation"),
                @LocalizedText(locale = "zh-CN", text = "待评估")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.policyState.AWAITING_PUBLISH", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Awaiting manual publication"),
                @LocalizedText(locale = "zh-CN", text = "待人工发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.policyState.CONVERGED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Converged"),
                @LocalizedText(locale = "zh-CN", text = "已收敛")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.policyState.EXHAUSTED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Iteration limit reached"),
                @LocalizedText(locale = "zh-CN", text = "已达迭代上限")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.policyState.PAUSED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Paused"),
                @LocalizedText(locale = "zh-CN", text = "已暂停")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.producedVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Produced version"),
                @LocalizedText(locale = "zh-CN", text = "产出版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.producedVersionLabel", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{version}} {{status}}"),
                @LocalizedText(locale = "zh-CN", text = "{{version}}{{status}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.rearmBlocked", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "An optimization run is in progress. Wait for it to finish before re-enabling."),
                @LocalizedText(locale = "zh-CN", text = "存在进行中的优化运行，结束后才能重新启用")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.refreshRevision", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Refresh revision"),
                @LocalizedText(locale = "zh-CN", text = "刷新修订")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.revision", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Policy revision"),
                @LocalizedText(locale = "zh-CN", text = "策略修订")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runNo", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Run number"),
                @LocalizedText(locale = "zh-CN", text = "运行号")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runNote", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Run notes"),
                @LocalizedText(locale = "zh-CN", text = "运行备注")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runNow", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimize now"),
                @LocalizedText(locale = "zh-CN", text = "立即优化一次")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runState.FAILED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed"),
                @LocalizedText(locale = "zh-CN", text = "失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runState.PENDING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Pending"),
                @LocalizedText(locale = "zh-CN", text = "待执行")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runState.RUNNING", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Running"),
                @LocalizedText(locale = "zh-CN", text = "执行中")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runState.SKIPPED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Skipped"),
                @LocalizedText(locale = "zh-CN", text = "已跳过")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runState.SUCCEEDED", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Succeeded"),
                @LocalizedText(locale = "zh-CN", text = "成功")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.runs", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimization runs"),
                @LocalizedText(locale = "zh-CN", text = "优化运行")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.sampleAndAverage", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Samples / average score"),
                @LocalizedText(locale = "zh-CN", text = "样本 / 均分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.samplesAndPending", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Samples / awaiting scoring"),
                @LocalizedText(locale = "zh-CN", text = "样本 / 待评分")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.saveAndRearm", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Save and re-enable"),
                @LocalizedText(locale = "zh-CN", text = "保存并重新启用")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.savePolicy", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Save policy"),
                @LocalizedText(locale = "zh-CN", text = "保存策略")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.saved", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimization policy saved"),
                @LocalizedText(locale = "zh-CN", text = "优化策略已保存")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.scheduled", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Scheduled"),
                @LocalizedText(locale = "zh-CN", text = "定时")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.scoreThreshold", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Score threshold"),
                @LocalizedText(locale = "zh-CN", text = "分数阈值")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.snapshot", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Evaluation snapshot"),
                @LocalizedText(locale = "zh-CN", text = "评估快照")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.trigger", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Trigger"),
                @LocalizedText(locale = "zh-CN", text = "触发")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.unavailable", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimization policy unavailable: {{error}}"),
                @LocalizedText(locale = "zh-CN", text = "优化策略不可用：{{error}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.unpublishedResource", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The resource has not been published and cannot be optimized"),
                @LocalizedText(locale = "zh-CN", text = "资源尚未发布，无法优化")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.unsavedDefaults", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unsaved (defaults)"),
                @LocalizedText(locale = "zh-CN", text = "未保存（默认值）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.validation.cooldown", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The cooldown must be an ISO-8601 duration from PT1M to P7D"),
                @LocalizedText(locale = "zh-CN", text = "冷却时间须为 PT1M 到 P7D 之间的 ISO-8601 时长")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.validation.evaluationWindow", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The evaluation window must be an ISO-8601 duration from PT5M to P30D"),
                @LocalizedText(locale = "zh-CN", text = "评估窗口须为 PT5M 到 P30D 之间的 ISO-8601 时长")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.validation.instruction", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optimization instructions must not exceed 4096 characters"),
                @LocalizedText(locale = "zh-CN", text = "优化指令最多 4096 个字符")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.validation.judgeAlias", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Invalid scoring model alias format"),
                @LocalizedText(locale = "zh-CN", text = "评分模型别名格式无效")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "optimization.validation.optimizerAlias", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Invalid optimizer model alias format"),
                @LocalizedText(locale = "zh-CN", text = "优化模型别名格式无效")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.changeNote", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Change notes"),
                @LocalizedText(locale = "zh-CN", text = "变更说明")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.copyAndPublish", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Copy and publish"),
                @LocalizedText(locale = "zh-CN", text = "复制并发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.defaultNote", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Promote version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "推广版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.fixedSourceVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Fixed source version"),
                @LocalizedText(locale = "zh-CN", text = "固定来源版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.immutableSourceHint", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The server copies content from the immutable source version. The page does not download and re-upload the content; the source resource and draft remain unchanged."),
                @LocalizedText(locale = "zh-CN", text = "服务端从不可变来源版本复制内容；页面不下载正文后再上传，来源资源与来源草稿保持不变。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.newTarget", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The target resource does not exist. It will be created with a null revision precondition, and the fixed source version will be published."),
                @LocalizedText(locale = "zh-CN", text = "目标资源不存在；将以空revision条件创建目标并发布固定来源版本。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.published", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target version {{version}} copied and published"),
                @LocalizedText(locale = "zh-CN", text = "已复制发布目标版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.receiptOrTargetMissing", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No publication receipt or target resource was found. Retry with the original request ID."),
                @LocalizedText(locale = "zh-CN", text = "未找到发布回执或目标资源；可使用原请求号重试。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.sameIdentity", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The source and target resource identities are identical. Copy and publish is not allowed."),
                @LocalizedText(locale = "zh-CN", text = "来源与目标完整资源身份相同，不能复制发布。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.scopeRequired", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select the target business domain, namespace, environment, and application."),
                @LocalizedText(locale = "zh-CN", text = "请选择完整的目标业务域、namespace、环境和应用。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.sourceResource", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Source resource"),
                @LocalizedText(locale = "zh-CN", text = "来源资源")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.status", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Copy and publish status"),
                @LocalizedText(locale = "zh-CN", text = "复制发布状态")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.targetDraftUnchanged", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The target draft matches its current published version. This promotion will not modify the draft."),
                @LocalizedText(locale = "zh-CN", text = "目标草稿与当前发布版本一致；本次推广不会改写草稿。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.targetHasDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The target has an unpublished draft. This promotion will retain it."),
                @LocalizedText(locale = "zh-CN", text = "目标存在未发布草稿；本次推广会保留该草稿。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.targetKey", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target resource key"),
                @LocalizedText(locale = "zh-CN", text = "目标资源键")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.targetReadFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Unable to read the target resource status"),
                @LocalizedText(locale = "zh-CN", text = "无法读取目标资源状态")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.targetRevision", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target revision {{revision}}; current version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "目标revision {{revision}}；当前版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.targetScope", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target four-part scope"),
                @LocalizedText(locale = "zh-CN", text = "目标四维范围")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.targetUnpublished", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The target has no published version. Promotion will not overwrite its existing draft."),
                @LocalizedText(locale = "zh-CN", text = "目标尚无已发布版本；推广不会覆盖现有草稿。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.title", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Copy and publish fixed source version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "复制发布固定来源版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.verified", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target version {{version}} verified"),
                @LocalizedText(locale = "zh-CN", text = "已核验目标版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.verifyFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Request verification failed: {{error}}"),
                @LocalizedText(locale = "zh-CN", text = "核验请求失败：{{error}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "promotion.verifyingTarget", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Verifying target resource"),
                @LocalizedText(locale = "zh-CN", text = "核验目标资源")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.audit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publication, rollback, and promotion audit"),
                @LocalizedText(locale = "zh-CN", text = "发布、回滚与推广审计")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.auditFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to load publication audit"),
                @LocalizedText(locale = "zh-CN", text = "发布审计读取失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.auditPlaceholder", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Optional: verify receipts by request ID"),
                @LocalizedText(locale = "zh-CN", text = "可选：按请求号核验回执")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.auditRequestId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Audit request ID"),
                @LocalizedText(locale = "zh-CN", text = "审计请求号")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.changeNote", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Notes"),
                @LocalizedText(locale = "zh-CN", text = "说明")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.closeSnapshot", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Close snapshot"),
                @LocalizedText(locale = "zh-CN", text = "关闭快照")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.commitUnknown", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The submission result is unknown. Verify the original receipt first; retries reuse the same request ID and content."),
                @LocalizedText(locale = "zh-CN", text = "提交结果未知。请先核验原请求回执；重试时会复用相同请求号和内容。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.confirmPublish", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm publication"),
                @LocalizedText(locale = "zh-CN", text = "确认发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.confirmPublishTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish the current draft?"),
                @LocalizedText(locale = "zh-CN", text = "确认发布当前草稿？")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.confirmRollback", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Confirm rollback"),
                @LocalizedText(locale = "zh-CN", text = "确认回滚")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.confirmRollbackTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Roll back to version {{version}}?"),
                @LocalizedText(locale = "zh-CN", text = "确认回滚到版本 {{version}}？")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.contentHash", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "SHA-256"),
                @LocalizedText(locale = "zh-CN", text = "SHA-256")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.createdByAndAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Created by / time"),
                @LocalizedText(locale = "zh-CN", text = "创建人 / 时间")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.currentPublication", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Currently published"),
                @LocalizedText(locale = "zh-CN", text = "当前发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.fixedVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Fixed version number"),
                @LocalizedText(locale = "zh-CN", text = "固定版本号")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.history", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Immutable version history"),
                @LocalizedText(locale = "zh-CN", text = "不可变版本历史")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.historyFile", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Historical file content: {{path}}"),
                @LocalizedText(locale = "zh-CN", text = "历史文件正文: {{path}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.historyPrompt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Historical Prompt content v{{version}}"),
                @LocalizedText(locale = "zh-CN", text = "历史 Prompt 正文 v{{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.historySkillTree", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Historical Skill file tree (read-only)"),
                @LocalizedText(locale = "zh-CN", text = "历史 Skill 文件树（只读）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.historyTree", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Historical Skill file tree v{{version}}"),
                @LocalizedText(locale = "zh-CN", text = "历史 Skill 文件树 v{{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.listFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to load version list"),
                @LocalizedText(locale = "zh-CN", text = "版本列表读取失败")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.loadingSnapshot", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Loading fixed historical version"),
                @LocalizedText(locale = "zh-CN", text = "读取固定历史版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.localPublication", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Local publication"),
                @LocalizedText(locale = "zh-CN", text = "本地发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.noPublications", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No publication records"),
                @LocalizedText(locale = "zh-CN", text = "暂无发布记录")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.noVersions", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No published versions"),
                @LocalizedText(locale = "zh-CN", text = "暂无已发布版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.nodeCount", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "File count"),
                @LocalizedText(locale = "zh-CN", text = "文件数")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.occurredAt", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Occurred at"),
                @LocalizedText(locale = "zh-CN", text = "发生时间")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.operation.PROMOTE", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Promote"),
                @LocalizedText(locale = "zh-CN", text = "推广")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.operation.PUBLISH", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish"),
                @LocalizedText(locale = "zh-CN", text = "发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.operation.ROLLBACK", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rollback"),
                @LocalizedText(locale = "zh-CN", text = "回滚")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.operationDenied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to perform this publication operation"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权执行此发布操作")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.operationIncomplete", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version operation incomplete"),
                @LocalizedText(locale = "zh-CN", text = "版本操作未完成")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.pageSummary", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version {{version}} · {{name}}"),
                @LocalizedText(locale = "zh-CN", text = "版本 {{version}} · {{name}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.pendingRequestRetained", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Submission result unknown; the original request ID is retained"),
                @LocalizedText(locale = "zh-CN", text = "提交结果未知，原请求号已保留")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.previousVersionId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Previous version ID"),
                @LocalizedText(locale = "zh-CN", text = "上一版本ID")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.promote", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Copy and publish"),
                @LocalizedText(locale = "zh-CN", text = "复制发布")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.publishDraft", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish current draft"),
                @LocalizedText(locale = "zh-CN", text = "发布当前草稿")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.publishWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publish the currently saved draft at revision {{revision}}. Unsaved content will not be published."),
                @LocalizedText(locale = "zh-CN", text = "将以修订 {{revision}} 发布当前已保存草稿。未保存内容不会发布。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.published", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version {{version}} published"),
                @LocalizedText(locale = "zh-CN", text = "已发布版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.readDenied", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "You do not have permission to read historical versions"),
                @LocalizedText(locale = "zh-CN", text = "当前身份无权读取历史版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.readOnlyHint", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Historical snapshots are read-only. File content is neither rendered nor executed."),
                @LocalizedText(locale = "zh-CN", text = "历史快照只读，不渲染或执行文件内容。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.readVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Read version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "读取版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.receiptDetails", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Receipt {{receipt}} · resource {{resource}} · revision {{revision}} · source version ID {{sourceVersion}}"),
                @LocalizedText(locale = "zh-CN", text = "receipt {{receipt}} · resource {{resource}} · revision {{revision}} · 来源版本ID {{sourceVersion}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.receiptMissing", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "No publication receipt was found. Retry with the same request ID and content."),
                @LocalizedText(locale = "zh-CN", text = "未找到发布回执；可使用原请求号和相同内容重试。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.refreshFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The operation was submitted, but refreshing the resource status failed: {{error}}"),
                @LocalizedText(locale = "zh-CN", text = "操作已提交，但资源状态刷新失败：{{error}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.resourceId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource ID"),
                @LocalizedText(locale = "zh-CN", text = "资源ID")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.resourceUnavailable", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Resource status cannot be refreshed; version changes are disabled"),
                @LocalizedText(locale = "zh-CN", text = "资源状态无法刷新；版本写操作已禁用")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.retryRequest", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Retry with original request ID"),
                @LocalizedText(locale = "zh-CN", text = "使用原请求号重试")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.rollbackTo", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Roll back to version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "回滚到版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.rollbackWarning", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "This operation only changes the current publication pointer. It does not modify unsaved drafts or historical versions. Expected revision: {{revision}}."),
                @LocalizedText(locale = "zh-CN", text = "本次操作只切换当前发布指针，不修改未保存草稿或历史版本。预期修订：{{revision}}。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.rolledBack", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Rolled back to version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "已回滚到版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.saveBeforePublishing", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The local draft has not been saved. Save it before publishing."),
                @LocalizedText(locale = "zh-CN", text = "本地草稿尚未保存；保存后才可发布。")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.searchAudit", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Search audit"),
                @LocalizedText(locale = "zh-CN", text = "查询审计")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.selectHistoryFile", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Select a historical text file"),
                @LocalizedText(locale = "zh-CN", text = "选择一个历史文本文件")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.snapshotTitle", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Historical snapshot v{{version}} (read-only)"),
                @LocalizedText(locale = "zh-CN", text = "历史快照 v{{version}}（只读）")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.snapshotUnavailable", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "The fixed historical version is unavailable"),
                @LocalizedText(locale = "zh-CN", text = "固定历史版本不可用")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.sourceVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Source version"),
                @LocalizedText(locale = "zh-CN", text = "来源版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.sourceVersionId", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Source version ID"),
                @LocalizedText(locale = "zh-CN", text = "来源版本ID")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.submitted", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{operation}} submitted; target version {{version}}"),
                @LocalizedText(locale = "zh-CN", text = "操作 {{operation}} 已提交，目标版本 {{version}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.targetCreated", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Independent target resource created"),
                @LocalizedText(locale = "zh-CN", text = "已创建独立目标资源")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.targetUpdated", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target publication pointer updated"),
                @LocalizedText(locale = "zh-CN", text = "已更新目标发布指针")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.targetVersion", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Target version"),
                @LocalizedText(locale = "zh-CN", text = "目标版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.totalPublications", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{total}} publication records"),
                @LocalizedText(locale = "zh-CN", text = "共 {{total}} 条发布记录")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.totalVersions", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "{{total}} versions"),
                @LocalizedText(locale = "zh-CN", text = "共 {{total}} 个版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.verifyFailed", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Failed to verify the original request: {{error}}"),
                @LocalizedText(locale = "zh-CN", text = "核验原请求失败：{{error}}")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.verifyRequest", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Verify original request"),
                @LocalizedText(locale = "zh-CN", text = "核验原请求")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.version", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version"),
                @LocalizedText(locale = "zh-CN", text = "版本")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.versionNote", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Version notes"),
                @LocalizedText(locale = "zh-CN", text = "版本说明")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.versionNotePlaceholder", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "Publication or rollback notes"),
                @LocalizedText(locale = "zh-CN", text = "发布或回滚说明")})
@PermissionMessage(pageCode = "tianshu.ai.editor", key = "versions.viewTarget", defaultLocale = "zh-CN",
        defaults = {@LocalizedText(locale = "en-US", text = "View target version"),
                @LocalizedText(locale = "zh-CN", text = "查看目标版本")})
final class DdcTianshuAiEditorMessages {
    private DdcTianshuAiEditorMessages() { }
}
