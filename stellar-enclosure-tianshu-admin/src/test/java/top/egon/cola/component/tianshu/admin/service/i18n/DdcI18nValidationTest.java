package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DdcI18nValidationTest {
    @Test
    void completeHumanReviewHashesHaveTheirOwnTwoMiBLimitWithoutReducingTheSourceMessageDomain() {
        var sources = new java.util.TreeMap<String, PermissionManifest.Message>();
        var hashes = new java.util.TreeMap<String, String>();
        for (int index = 0; index < 12_000; index++) {
            String key = "key".repeat(40) + index;
            var message = new PermissionManifest.Message("orders", key, "en-US", Map.of("en-US", "Source"));
            sources.put(key, message);
            hashes.put(key, I18nSourceRules.sourceHash(message));
        }
        assertThat(sources).hasSize(12_000);
        assertThatThrownBy(() -> new DdcI18nValidation().reviewedSources(hashes, sources))
                .isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56412));
    }
    private final DdcI18nValidation validation = new DdcI18nValidation();

    @Test void emptyDraftCanBeSavedButCannotBePublished() {
        var draft = Map.<String, Map<String, String>>of("zh-CN", Map.of());
        assertThat(validation.translations("zh-CN", draft, false)).isEqualTo("{\"zh-CN\":{}}");
        assertThatThrownBy(() -> validation.translations("zh-CN", draft, true)).isInstanceOf(CommonException.class);
    }

    @Test void partialTranslationUsesDefaultKeysAndSameInterpolationSet() {
        var translations = Map.of("zh-CN", Map.of("title", "订单", "welcome", "你好 {{ name }}"),
                "en-US", Map.of("welcome", "Hello {{name}}"));
        assertThat(validation.read(validation.translations("zh-CN", translations, true))).isEqualTo(translations);
    }

    @Test void defaultMustCoverAllTranslationKeys() {
        assertThatThrownBy(() -> validation.translations("zh-CN", Map.of("zh-CN", Map.of(),
                "en-US", Map.of("title", "Orders")), false)).isInstanceOf(CommonException.class);
    }

    @Test void interpolationsCannotBeDroppedAddedOrMalformed() {
        for (String value : new String[]{"Hello", "Hello {{other}}", "Hello {{name", "Hello {{name}} {{}}"}) {
            assertThatThrownBy(() -> validation.translations("zh-CN", Map.of("zh-CN", Map.of("title", "你好 {{name}}"),
                    "en-US", Map.of("title", value)), true)).isInstanceOf(CommonException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a..b", "a.__proto__.b", "constructor", "prototype.name", "a/b", "标题"})
    void rejectsUnsafeOrUnstableKeys(String value) {
        assertThatThrownBy(() -> validation.key(value)).isInstanceOf(CommonException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "en_US", "en-us", "zh-cn", "und", "x-private", "en--US", "en US"})
    void rejectsNoncanonicalLocales(String value) {
        assertThatThrownBy(() -> validation.locale(value)).isInstanceOf(CommonException.class);
    }

    @Test void rejectsEmptyAndOversizedMessages() {
        for (String value : new String[]{"", " ", "x".repeat(8193), "a\u0000b"}) {
            assertThatThrownBy(() -> validation.translations("en-US", Map.of("en-US", Map.of("title", value)), false))
                    .isInstanceOf(CommonException.class);
        }
    }

    @Test void enforcesTotalContentSize() {
        Map<String, String> messages = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 300; i++) messages.put("key" + i, "x".repeat(8192));
        assertThatThrownBy(() -> validation.translations("en-US", Map.of("en-US", messages), false))
                .isInstanceOfSatisfying(CommonException.class, failure -> assertThat(failure.getCode()).isEqualTo(56412));
    }
}
