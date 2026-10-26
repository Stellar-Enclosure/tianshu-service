package top.egon.cola.component.tianshu.admin.service.i18n;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublicI18nBundleResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import top.egon.cola.component.tianshu.admin.permission.DdcAdminMessageSources;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Source declarations supply public login text; tenant bundles use imported, activated manifest versions. */
@Component("ddcAdminUiI18nCatalog")
public class DdcAdminUiI18nCatalog {
    public static final String DEFAULT_LOCALE = "zh-CN";
    public static final List<String> PAGE_CODES = List.of("common", "tianshu.auth", "tianshu.bizs", "tianshu.envs",
            "tianshu.apps", "tianshu.namespaces", "tianshu.bindings", "tianshu.registry", "tianshu.instances",
            "tianshu.publishTasks", "tianshu.cache", "tianshu.configs", "tianshu.ai.resources", "tianshu.ai.editor", "tianshu.i18n");

    private final DdcI18nValidation validation;
    private final Map<String, Page> pages;
    private final Map<String, String> versions;

    public DdcAdminUiI18nCatalog(@Qualifier("ddcI18nValidation") DdcI18nValidation validation) {
        this.validation = validation;
        Map<String, Page> loaded = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (var source : DdcAdminMessageSources.PAGES) {
            Page page = fromDeclarations(source.pageCode(), source.name(), source.declaration(), validation);
            loaded.put(page.pageCode(), page);
            hashes.put(page.pageCode(), digest(CanonicalJson.write(page.translations())));
        }
        pages = Collections.unmodifiableMap(loaded);
        versions = Map.copyOf(hashes);
    }

    static Page fromDeclarations(String pageCode, String name, Class<?> declaration, DdcI18nValidation validation) {
        Map<String, Map<String, String>> translations = new TreeMap<>();
        for (PermissionMessage annotation : declaration.getAnnotationsByType(PermissionMessage.class)) {
            if (!pageCode.equals(annotation.pageCode()) || !DEFAULT_LOCALE.equals(annotation.defaultLocale())) {
                throw DdcI18nValidation.invalid("Source declaration must match its page and default locale");
            }
            Map<String, String> defaults = new TreeMap<>();
            for (var localized : annotation.defaults()) {
                if (defaults.putIfAbsent(localized.locale(), localized.text()) != null) {
                    throw DdcI18nValidation.invalid("Duplicate source locale");
                }
            }
            var message = new PermissionManifest.Message(pageCode, annotation.key(), annotation.defaultLocale(), defaults);
            message.defaults().forEach((locale, text) -> {
                if (translations.computeIfAbsent(locale, ignored -> new TreeMap<>()).putIfAbsent(message.key(), text) != null) {
                    throw DdcI18nValidation.invalid("Duplicate source key");
                }
            });
        }
        if (!translations.containsKey("en-US")) throw DdcI18nValidation.invalid("Source pages require both default languages");
        validation.key(pageCode);
        validation.name(name);
        validation.translations(DEFAULT_LOCALE, translations, true);
        Map<String, Map<String, String>> immutable = new TreeMap<>();
        translations.forEach((locale, messages) -> immutable.put(locale, Collections.unmodifiableMap(messages)));
        return new Page(pageCode, name, DEFAULT_LOCALE, Collections.unmodifiableMap(immutable));
    }

    public List<Page> pages() { return List.copyOf(pages.values()); }

    public Page page(String pageCode) {
        Page page = pages.get(pageCode);
        if (page == null) throw DdcI18nValidation.invalid("Unknown admin language page");
        return page;
    }

    public Map<String, String> messages(String pageCode, String locale, boolean includeCommon) {
        validation.locale(locale);
        Page page = page(pageCode);
        Map<String, String> messages = new TreeMap<>();
        if (includeCommon && !"common".equals(pageCode)) merge(messages, page("common"), locale);
        merge(messages, page, locale);
        return messages;
    }

    public String version(String pageCode) { return versions.get(page(pageCode).pageCode()); }

    public DdcPublicI18nBundleResult publicBundle(String locale) {
        Map<String, String> messages = messages("tianshu.auth", locale, false);
        return new DdcPublicI18nBundleResult("tianshu.auth", locale, DEFAULT_LOCALE,
                digest(version("tianshu.auth") + ":" + locale), Collections.unmodifiableMap(messages));
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private void merge(Map<String, String> target, Page page, String locale) {
        target.putAll(page.translations().get(page.defaultLocale()));
        target.putAll(page.translations().getOrDefault(locale, Map.of()));
    }

    public record Page(String pageCode, String name, String defaultLocale, Map<String, Map<String, String>> translations) { }
}
