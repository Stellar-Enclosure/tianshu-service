package top.egon.cola.component.tianshu.admin.model.vo;

import java.util.Map;

public record DdcI18nPageResult(String id, String bizCode, String namespaceCode, String env,
        String appCode, String pageCode, String name, String defaultLocale,
        Map<String, Map<String, String>> translations, String revision, String publishedVersion) {
}
