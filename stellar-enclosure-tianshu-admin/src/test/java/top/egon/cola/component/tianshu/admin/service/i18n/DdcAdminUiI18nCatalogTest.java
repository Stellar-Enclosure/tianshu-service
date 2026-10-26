package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.Test;
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

    @Test void catalogRequiresMatchingPageAndStandardSourceDeclarations() {
        assertThatThrownBy(() -> DdcAdminUiI18nCatalog.fromDeclarations("common", "Common", MissingMessages.class, validation))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> DdcAdminUiI18nCatalog.fromDeclarations("wrong", "Wrong", BadPlaceholders.class, validation))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> DdcAdminUiI18nCatalog.fromDeclarations("common", "Common", BadPlaceholders.class, validation))
                .isInstanceOf(RuntimeException.class);
    }

    private static final class MissingMessages { }

    @top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage(
            pageCode = "common", key = "hello", defaultLocale = "zh-CN", defaults = {
            @top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText(locale = "zh-CN", text = "Hi {{name}}"),
            @top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText(locale = "en-US", text = "Hello")})
    private static final class BadPlaceholders { }
}
