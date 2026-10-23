package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DdcAdminUiI18nCatalogTest {
    private final DdcI18nValidation validation = new DdcI18nValidation();

    @Test void packagedCatalogContainsEveryConsolePageAndNoEmptyPublication() {
        var catalog = new DdcAdminUiI18nCatalog(validation);
        assertThat(catalog.pages()).extracting(DdcAdminUiI18nCatalog.Page::pageCode)
                .containsExactlyElementsOf(DdcAdminUiI18nCatalog.PAGE_CODES);
        for (var page : catalog.pages()) {
            assertThat(page.translations().get("zh-CN")).isNotEmpty();
            assertThat(page.translations().get("en-US").keySet()).containsExactlyInAnyOrderElementsOf(page.translations().get("zh-CN").keySet());
            assertThatCode(() -> validation.translations(page.defaultLocale(), page.translations(), true)).doesNotThrowAnyException();
        }
    }

    @Test void publicBundleContainsOnlyAuthMessagesAndDoesNotRequireTenantContext() {
        org.slf4j.MDC.clear();
        var catalog = new DdcAdminUiI18nCatalog(validation);
        var bundle = catalog.publicBundle("en-US");
        assertThat(bundle.pageCode()).isEqualTo("tianshu.auth");
        assertThat(bundle.messages()).containsExactlyInAnyOrderEntriesOf(catalog.page("tianshu.auth").translations().get("en-US"));
        assertThat(catalog.publicBundle("de-DE").messages()).containsExactlyInAnyOrderEntriesOf(catalog.page("tianshu.auth").translations().get("zh-CN"));
        assertThat(bundle.version()).isNotEqualTo(catalog.publicBundle("zh-CN").version());
        assertThatThrownBy(() -> catalog.page("../../private")).isInstanceOf(RuntimeException.class);
    }

    @Test void catalogRejectsDuplicateUnsafeOrNonTextKeysAndMismatchedVariables() {
        for (String messages : List.of("{\"title\":\"One\",\"title\":\"Two\"}", "{\"__proto__.x\":\"Unsafe\"}",
                "{\"constructor\":\"Unsafe\"}", "{\"title\":1}", "{\"title\":true}", "{\"title\":\"Hi {{name}}\"}")) {
            assertThatThrownBy(() -> parse("{\"zh-CN\":" + messages + ",\"en-US\":{\"title\":\"Title\"}}"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test void catalogChecksPageIdentityAndRequiresBothDefaultLanguages() {
        assertThatThrownBy(() -> parse("{\"zh-CN\":{\"title\":\"Title\"}}")) .isInstanceOf(RuntimeException.class);
        String json = "{\"pageCode\":\"common\",\"name\":\"Common\",\"defaultLocale\":\"zh-CN\","
                + "\"translations\":{\"zh-CN\":{\"title\":\"Title\"},\"en-US\":{\"title\":\"Title\"}}}";
        assertThatThrownBy(() -> DdcAdminUiI18nCatalog.parse("tianshu.auth", json.getBytes(StandardCharsets.UTF_8), validation))
                .isInstanceOf(RuntimeException.class);
    }

    private void parse(String translations) throws Exception {
        String json = "{\"pageCode\":\"common\",\"name\":\"Common\",\"defaultLocale\":\"zh-CN\",\"translations\":" + translations + "}";
        DdcAdminUiI18nCatalog.parse("common", json.getBytes(StandardCharsets.UTF_8), validation);
    }
}
