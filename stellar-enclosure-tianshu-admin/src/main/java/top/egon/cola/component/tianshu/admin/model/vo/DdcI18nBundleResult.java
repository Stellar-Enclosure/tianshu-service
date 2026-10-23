package top.egon.cola.component.tianshu.admin.model.vo;

import java.util.Map;

public record DdcI18nBundleResult(String pageCode, String locale, String defaultLocale,
        String version, Map<String, String> messages) {
}
