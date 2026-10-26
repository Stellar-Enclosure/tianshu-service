package top.egon.cola.component.tianshu.admin.permission;

import java.util.List;

/** Local login and page metadata read the same declarations exported into the producer manifest. */
public final class DdcAdminMessageSources {
    public static final List<SourcePage> PAGES = List.of(
            new SourcePage("common", "管理台公共文案", DdcCommonMessages.class),
            new SourcePage("tianshu.auth", "登录与启动", DdcTianshuAuthMessages.class),
            new SourcePage("tianshu.bizs", "业务域", DdcTianshuBizsMessages.class),
            new SourcePage("tianshu.envs", "环境", DdcTianshuEnvsMessages.class),
            new SourcePage("tianshu.apps", "应用", DdcTianshuAppsMessages.class),
            new SourcePage("tianshu.namespaces", "命名空间", DdcTianshuNamespacesMessages.class),
            new SourcePage("tianshu.bindings", "作用域绑定", DdcTianshuBindingsMessages.class),
            new SourcePage("tianshu.registry", "服务注册目录", DdcTianshuRegistryMessages.class),
            new SourcePage("tianshu.instances", "配置客户端实例", DdcTianshuInstancesMessages.class),
            new SourcePage("tianshu.publishTasks", "发布任务", DdcTianshuPublishTasksMessages.class),
            new SourcePage("tianshu.cache", "缓存管理", DdcTianshuCacheMessages.class),
            new SourcePage("tianshu.configs", "配置管理", DdcTianshuConfigsMessages.class),
            new SourcePage("tianshu.ai.resources", "AI资源管理", DdcTianshuAiResourcesMessages.class),
            new SourcePage("tianshu.ai.editor", "AI资源编辑", DdcTianshuAiEditorMessages.class),
            new SourcePage("tianshu.i18n", "页面多语种", DdcTianshuI18nMessages.class));

    private DdcAdminMessageSources() { }

    public record SourcePage(String pageCode, String name, Class<?> declaration) { }
}
