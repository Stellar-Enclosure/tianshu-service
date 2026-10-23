package top.egon.cola.component.tianshu.admin.service.i18n;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import java.nio.charset.StandardCharsets;
import java.util.IllformedLocaleException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Component("ddcI18nValidation")
public class DdcI18nValidation {
    public static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*$");
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");
    private static final Set<String> UNSAFE_SEGMENTS = Set.of("__proto__", "constructor", "prototype");
    private final ObjectMapper mapper = new ObjectMapper();

    public void key(String key) {
        if (key == null || key.length() > 128 || !KEY.matcher(key).matches()
                || java.util.Arrays.stream(key.split("\\.")).anyMatch(UNSAFE_SEGMENTS::contains)) {
            throw invalid("Invalid page or message key");
        }
    }

    public void name(String name) {
        if (name == null || name.isBlank() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Invalid page name");
        }
    }

    public String locale(String value) {
        if (value == null || value.isBlank() || value.length() > 64) throw invalid("Invalid locale");
        try {
            Locale locale = new Locale.Builder().setLanguageTag(value).build();
            if (locale.getLanguage().isEmpty() || "und".equals(locale.getLanguage())
                    || !locale.toLanguageTag().equals(value)) throw invalid("Locale must be a canonical BCP 47 tag");
            return value;
        } catch (IllformedLocaleException failure) {
            throw invalid("Invalid locale");
        }
    }

    public String translations(String defaultLocale, Map<String, Map<String, String>> translations, boolean publishing) {
        locale(defaultLocale);
        if (translations == null || translations.isEmpty() || translations.size() > 32
                || !translations.containsKey(defaultLocale) || translations.get(defaultLocale) == null) {
            throw invalid("Translations require a default language and at most 32 languages");
        }
        Map<String, String> defaults = translations.get(defaultLocale);
        if (publishing && defaults.isEmpty()) throw invalid("At least one message is required to publish");
        for (var language : translations.entrySet()) {
            locale(language.getKey());
            Map<String, String> messages = language.getValue();
            if (messages == null || messages.size() > 2000 || !defaults.keySet().containsAll(messages.keySet())) {
                throw invalid("The default language must cover all keys; maximum 2000 keys per language");
            }
            for (var message : messages.entrySet()) {
                key(message.getKey());
                String value = message.getValue();
                if (value == null || value.isBlank() || value.length() > 8192
                        || value.indexOf('\u0000') >= 0) throw invalid("Message must contain 1..8192 characters");
                String defaultValue = defaults.get(message.getKey());
                if (defaultValue == null || !variables(value).equals(variables(defaultValue))) {
                    throw invalid("Interpolation variables must match the default language");
                }
            }
        }
        try {
            String json = mapper.writeValueAsString(translations);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
                throw new CommonException(56412, "TIANSHU_I18N_REQUEST_TOO_LARGE", "Translation content exceeds 2 MiB");
            }
            return json;
        } catch (JsonProcessingException failure) {
            throw invalid("Invalid translation content");
        }
    }

    private Set<String> variables(String value) {
        TreeSet<String> result = new TreeSet<>();
        var matcher = VARIABLE.matcher(value);
        while (matcher.find()) result.add(matcher.group(1));
        if (matcher.replaceAll("").contains("{{") || VARIABLE.matcher(value).replaceAll("").contains("}}")) {
            throw invalid("Only {{name}} interpolation is supported");
        }
        return result;
    }

    public Map<String, Map<String, String>> read(String json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Map<String, String>>>() { });
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("I18N_STORED_TRANSLATIONS_INVALID", failure);
        }
    }

    public static CommonException invalid(String message) {
        return new CommonException(56401, "TIANSHU_I18N_VALIDATION_ERROR", message);
    }
}
