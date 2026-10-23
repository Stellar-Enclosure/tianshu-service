package top.egon.cola.component.tianshu.admin.service.i18n;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Server-owned defaults keep the metadata console usable before its first language publication. */
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
        for (String code : PAGE_CODES) {
            try (var input = new ClassPathResource("i18n/tianshu-admin/" + code + ".json").getInputStream()) {
                byte[] bytes = input.readNBytes(DdcI18nValidation.MAX_CONTENT_BYTES + 1);
                if (bytes.length > DdcI18nValidation.MAX_CONTENT_BYTES) throw new IOException("Catalog page exceeds size limit");
                Page page = parse(code, bytes, validation);
                loaded.put(code, page);
                hashes.put(code, digest(new String(bytes, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException failure) {
                throw new IllegalStateException("Invalid Tianshu admin language catalog: " + code, failure);
            }
        }
        pages = Collections.unmodifiableMap(loaded);
        versions = Map.copyOf(hashes);
    }

    static Page parse(String expectedCode, byte[] bytes, DdcI18nValidation validation) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail).setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        Page page = mapper.readValue(bytes, Page.class);
        if (!expectedCode.equals(page.pageCode()) || !DEFAULT_LOCALE.equals(page.defaultLocale())
                || page.translations() == null || !page.translations().containsKey("en-US")) {
            throw DdcI18nValidation.invalid("Catalog must match its page code and provide zh-CN and en-US");
        }
        validation.key(page.pageCode());
        validation.name(page.name());
        validation.translations(page.defaultLocale(), page.translations(), true);
        Map<String, Map<String, String>> translations = new TreeMap<>();
        page.translations().forEach((locale, messages) -> translations.put(locale, Collections.unmodifiableMap(new TreeMap<>(messages))));
        return new Page(page.pageCode(), page.name(), page.defaultLocale(), Collections.unmodifiableMap(translations));
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

    public DdcI18nBundleResult publicBundle(String locale) {
        Map<String, String> messages = messages("tianshu.auth", locale, false);
        return new DdcI18nBundleResult("tianshu.auth", locale, DEFAULT_LOCALE,
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
