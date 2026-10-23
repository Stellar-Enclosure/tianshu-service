package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.config.DdcAdminUiI18nProperties;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nPublishCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcAdminUiI18nServiceTest {
    private final DdcAdminUiI18nProperties properties = new DdcAdminUiI18nProperties();
    private final DdcAdminUiI18nCatalog catalog = mock(DdcAdminUiI18nCatalog.class);
    private final DdcI18nPageRepository repository = mock(DdcI18nPageRepository.class);
    private final DdcI18nScopeValidator scopes = mock(DdcI18nScopeValidator.class);
    private final DdcI18nValidation validation = new DdcI18nValidation();
    private final DdcI18nPageService pages = mock(DdcI18nPageService.class);
    private final DdcAdminUiI18nService service = new DdcAdminUiI18nService(properties, catalog, repository, scopes, validation, pages);
    private final DdcI18nScope scope = properties.scope();

    @BeforeEach void setup() {
        MDC.put("tenantId", "101");
        when(catalog.messages(anyString(), anyString(), eq(true))).thenAnswer(call -> new TreeMap<>(Map.of(
                "title", "Built-in title", "common.save", "Save", "fallback", "Built-in fallback")));
        when(catalog.version(anyString())).thenAnswer(call -> "builtin-" + call.getArgument(0));
        when(catalog.pages()).thenReturn(List.of(catalogPage("common"), catalogPage("tianshu.bizs")));
        when(scopes.require(eq(scope), any())).thenReturn(7L);
        when(scopes.hasPermission(any())).thenReturn(true);
    }

    @AfterEach void cleanup() { MDC.clear(); }

    @Test void publishedCommonAppliesEvenWhenPageIsMissingAndDraftCannotLeak() {
        DdcI18nPageBO common = published("common", Map.of("zh-CN", Map.of("common.save", "保存企业版"),
                "en-US", Map.of("common.save", "Save company")));
        common.setDraftTranslations("{\"en-US\":{\"common.save\":\"Unpublished\"}}");
        when(repository.findByPageCode(scope, "common")).thenReturn(Optional.of(common));
        assertThat(service.bundle("tianshu.bizs", "en-US").messages()).containsEntry("common.save", "Save company")
                .containsEntry("title", "Built-in title").doesNotContainValue("Unpublished");
    }

    @Test void publishedPageOverridesCommonAndFallbackKeepsRemainingBuiltinKeys() {
        when(repository.findByPageCode(scope, "common")).thenReturn(Optional.of(published("common", Map.of(
                "zh-CN", Map.of("title", "公共标题"), "en-US", Map.of("title", "Company common")))));
        when(repository.findByPageCode(scope, "tianshu.bizs")).thenReturn(Optional.of(published("tianshu.bizs", Map.of(
                "zh-CN", Map.of("title", "企业业务域"), "fr-FR", Map.of("title", "Domaines")))));
        assertThat(service.bundle("tianshu.bizs", "en-US").messages()).containsEntry("title", "企业业务域")
                .containsEntry("fallback", "Built-in fallback");
        assertThat(service.bundle("tianshu.bizs", "fr-FR").messages()).containsEntry("title", "Domaines");
    }

    @Test void uninitializedOrDisabledScopeUsesBuiltinWithoutReadingPageStorage() {
        for (int code : List.of(56406, 56042)) {
            doThrow(new CommonException(code, "unavailable", "unavailable")).when(scopes).require(scope, DdcAdminCapability.READ);
            assertThat(service.bundle("tianshu.bizs", "en-US").messages()).containsEntry("title", "Built-in title");
            assertThat(service.bootstrap().canInitialize()).isFalse();
        }
        verifyNoInteractions(repository);
    }

    @Test void publishedDefaultLanguageIsReportedWithTheEffectiveFallback() {
        var page = published("tianshu.bizs", Map.of("en-US", Map.of("title", "Company domains"),
                "zh-CN", Map.of("title", "企业业务域")))
                .setPublishedDefaultLocale("en-US");
        when(repository.findByPageCode(scope, "tianshu.bizs")).thenReturn(Optional.of(page));
        var bundle = service.bundle("tianshu.bizs", "fr-FR");
        assertThat(bundle.defaultLocale()).isEqualTo("en-US");
        assertThat(bundle.messages()).containsEntry("title", "Company domains");
    }

    @Test void deniedPermissionAndPersistenceErrorsNeverFallback() {
        doThrow(new CommonException(56407, "denied", "denied")).when(scopes).requirePermission(DdcAdminCapability.READ);
        assertThatThrownBy(() -> service.bundle("tianshu.bizs", "en-US")).isInstanceOf(CommonException.class);
        verifyNoInteractions(repository, catalog);
        doNothing().when(scopes).requirePermission(DdcAdminCapability.READ);
        when(scopes.require(scope, DdcAdminCapability.READ)).thenThrow(new TransientDataAccessResourceException("offline"));
        assertThatThrownBy(() -> service.bundle("tianshu.bizs", "en-US")).isInstanceOf(TransientDataAccessResourceException.class);
        doThrow(new CommonException(56407, "denied", "denied")).when(scopes).require(scope, DdcAdminCapability.READ);
        assertThatThrownBy(() -> service.bootstrap()).isInstanceOf(CommonException.class);
        verifyNoInteractions(repository);
    }

    @Test void versionsFollowBuiltinPublishedAndTenantChangesButIgnoreDraftRevision() {
        DdcI18nPageBO page = published("tianshu.bizs", Map.of("zh-CN", Map.of("title", "企业业务域")));
        when(repository.findByPageCode(scope, "tianshu.bizs")).thenReturn(Optional.of(page));
        String first = service.bundle("tianshu.bizs", "en-US").version();
        page.setVersion(19L).setDraftTranslations("{}");
        assertThat(service.bundle("tianshu.bizs", "en-US").version()).isEqualTo(first);
        page.setPublishedVersion(2L);
        String publishedVersion = service.bundle("tianshu.bizs", "en-US").version();
        assertThat(publishedVersion).isNotEqualTo(first);
        MDC.put("tenantId", "102");
        assertThat(service.bundle("tianshu.bizs", "en-US").version()).isNotEqualTo(publishedVersion);
        MDC.put("tenantId", "101");
        when(catalog.version("common")).thenReturn("new-catalog-content");
        assertThat(service.bundle("tianshu.bizs", "en-US").version()).isNotEqualTo(publishedVersion);
    }

    @Test void bootstrapUsesServerScopeAndOnlyPublishedCustomLanguages() {
        DdcI18nPageBO common = published("common", Map.of("zh-CN", Map.of("title", "标题"), "ja-JP", Map.of("title", "見出し")));
        common.setDraftTranslations("{\"zh-CN\":{\"title\":\"标题\"},\"de-DE\":{\"title\":\"Titel\"}}");
        when(repository.findByPageCode(scope, "common")).thenReturn(Optional.of(common));
        when(repository.findByPageCode(scope, "tianshu.bizs")).thenReturn(Optional.of(
                published("tianshu.bizs", Map.of("fr-FR", Map.of("title", "Domaines"))).setPublishedVersion(null)));
        var bootstrap = service.bootstrap();
        assertThat(bootstrap.scope()).isEqualTo(scope);
        assertThat(bootstrap.locales()).containsExactly("zh-CN", "en-US", "ja-JP");
        assertThat(bootstrap.canInitialize()).isTrue();
        when(scopes.hasPermission(DdcAdminCapability.PUBLISH)).thenReturn(false);
        assertThat(service.bootstrap().canInitialize()).isFalse();
    }

    @Test void initializeSkipsExistingDraftAndPublishesOnlyMissingPage() {
        when(repository.findByPageCode(scope, "common")).thenReturn(Optional.of(new DdcI18nPageBO()));
        when(pages.create(any())).thenAnswer(call -> {
            var command = call.<top.egon.cola.component.tianshu.admin.model.command.DdcI18nCreateCommand>getArgument(0);
            assertThat(command.bizCode()).isEqualTo(scope.bizCode());
            assertThat(command.pageCode()).isEqualTo("tianshu.bizs");
            return created(command.pageCode());
        });
        var result = service.initialize();
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(pages).publish(1L, new DdcI18nPublishCommand("0"));
        verify(scopes).requirePermission(DdcAdminCapability.READ);
        verify(scopes).requirePermission(DdcAdminCapability.WRITE);
        verify(scopes).requirePermission(DdcAdminCapability.PUBLISH);
        verify(scopes).require(scope, DdcAdminCapability.WRITE);
        verify(scopes).require(scope, DdcAdminCapability.PUBLISH);
        verify(repository, never()).update(any());
    }

    @Test void initializeChecksAllPermissionsBeforeAnyWritesAndRejectsUnavailableScope() {
        doThrow(new CommonException(56407, "denied", "denied")).when(scopes).requirePermission(DdcAdminCapability.PUBLISH);
        assertThatThrownBy(service::initialize).isInstanceOf(CommonException.class);
        verifyNoInteractions(repository, pages);
        doNothing().when(scopes).requirePermission(DdcAdminCapability.PUBLISH);
        when(scopes.require(scope, DdcAdminCapability.WRITE)).thenThrow(new CommonException(56406, "unavailable", "unavailable"));
        assertThatThrownBy(service::initialize).isInstanceOf(CommonException.class);
        verifyNoInteractions(repository, pages);
    }

    @Test void laterPublishFailureRollsBackEntireInitializationTransaction() {
        when(pages.create(any())).thenReturn(created("common"), created("tianshu.bizs"));
        when(pages.publish(anyLong(), any())).thenReturn(created("common"))
                .thenThrow(new CommonException(56404, "conflict", "conflict"));
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var transactional = (DdcAdminUiI18nService) proxy.getProxy();
        assertThatThrownBy(transactional::initialize).isInstanceOf(CommonException.class);
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
        verify(pages, times(2)).publish(anyLong(), any());
    }

    private DdcAdminUiI18nCatalog.Page catalogPage(String code) {
        return new DdcAdminUiI18nCatalog.Page(code, code, "zh-CN", Map.of("zh-CN", Map.of("title", "标题"), "en-US", Map.of("title", "Title")));
    }

    private DdcI18nPageResult created(String pageCode) {
        return new DdcI18nPageResult("1", scope.bizCode(), scope.namespaceCode(), scope.env(), scope.appCode(),
                pageCode, pageCode, "zh-CN", catalogPage(pageCode).translations(), "0", null);
    }

    private DdcI18nPageBO published(String pageCode, Map<String, Map<String, String>> translations) {
        return DdcI18nPageBO.builder().id(1L).version(0L).pageCode(pageCode).publishedVersion(1L)
                .publishedDefaultLocale(translations.containsKey("zh-CN") ? "zh-CN" : "fr-FR")
                .publishedTranslations(validation.translations(translations.containsKey("zh-CN") ? "zh-CN" : "fr-FR", translations, true)).build();
    }
}
