package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nPublishCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcI18nPageServiceTest {
    private static final DdcI18nScope SCOPE = new DdcI18nScope("retail", "main", "prod", "shop");
    private final DdcI18nPageRepository repository = mock(DdcI18nPageRepository.class);
    private final DdcI18nScopeValidator scopes = mock(DdcI18nScopeValidator.class);
    private final DdcOperationLogRepository audit = mock(DdcOperationLogRepository.class);
    private final DdcI18nValidation validation = new DdcI18nValidation();
    private final DdcI18nPageService service = new DdcI18nPageService(repository, scopes, validation, audit);

    @BeforeEach void setup() {
        MDC.put("tenantId", "101");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", ""));
        when(scopes.require(eq(SCOPE), any())).thenReturn(7L);
        when(repository.update(any())).thenAnswer(call -> Optional.of(call.<DdcI18nPageBO>getArgument(0)
                .setVersion(call.<DdcI18nPageBO>getArgument(0).getVersion() + 1)));
    }
    @AfterEach void cleanup() { MDC.clear(); SecurityContextHolder.clearContext(); }

    @Test void draftChangesDoNotLeakIntoPublishedBundle() {
        var page = page("orders", "zh-CN", Map.of("zh-CN", Map.of("title", "Published")));
        page.setDefaultLocale("en-US").setDraftTranslations("{\"en-US\":{\"title\":\"Draft\"}}");
        when(repository.findByPageCode(SCOPE, "orders")).thenReturn(Optional.of(page));
        var bundle = service.bundle(SCOPE, "orders", "en-US");
        assertThat(bundle.messages()).containsExactly(entry("title", "Published"));
        assertThat(bundle.defaultLocale()).isEqualTo("zh-CN");
    }

    @Test void commonDefaultsCommonLocalePageDefaultsAndPageLocaleMergeInOrder() {
        var common = page("common", "en-US", Map.of("en-US", Map.of("save", "Save", "title", "Common default", "shared", "Shared"),
                "fr-FR", Map.of("save", "Sauver", "title", "Commun")));
        var page = page("orders", "zh-CN", Map.of("zh-CN", Map.of("title", "订单", "count", "数量"),
                "fr-FR", Map.of("count", "Nombre")));
        when(repository.findByPageCode(SCOPE, "common")).thenReturn(Optional.of(common));
        when(repository.findByPageCode(SCOPE, "orders")).thenReturn(Optional.of(page));
        assertThat(service.bundle(SCOPE, "orders", "fr-FR").messages()).containsExactlyInAnyOrderEntriesOf(
                Map.of("save", "Sauver", "shared", "Shared", "title", "订单", "count", "Nombre"));
        assertThat(service.bundle(SCOPE, "orders", "de-DE").messages()).containsEntry("save", "Save").containsEntry("title", "订单");
    }

    @Test void commonPublishAndTenantChangeInvalidateBundleVersionButDraftDoesNot() {
        var page = page("orders", "en-US", Map.of("en-US", Map.of("title", "Orders")));
        var common = page("common", "en-US", Map.of("en-US", Map.of("save", "Save")));
        when(repository.findByPageCode(SCOPE, "orders")).thenReturn(Optional.of(page));
        when(repository.findByPageCode(SCOPE, "common")).thenReturn(Optional.of(common));
        String first = service.bundle(SCOPE, "orders", "en-US").version();
        page.setVersion(9L).setDraftTranslations("{}");
        assertThat(service.bundle(SCOPE, "orders", "en-US").version()).isEqualTo(first);
        common.setPublishedVersion(2L);
        String second = service.bundle(SCOPE, "orders", "en-US").version();
        assertThat(second).isNotEqualTo(first);
        MDC.put("tenantId", "102");
        assertThat(service.bundle(SCOPE, "orders", "en-US").version()).isNotEqualTo(second);
    }

    @Test void unpublishedPageReturnsNotFoundAndUnpublishedCommonIsIgnored() {
        var page = page("orders", "en-US", Map.of("en-US", Map.of("title", "Orders")));
        var common = page("common", "en-US", Map.of("en-US", Map.of("save", "Save"))).setPublishedVersion(null);
        when(repository.findByPageCode(SCOPE, "orders")).thenReturn(Optional.of(page));
        when(repository.findByPageCode(SCOPE, "common")).thenReturn(Optional.of(common));
        assertThat(service.bundle(SCOPE, "orders", "en-US").messages()).containsOnlyKeys("title");
        page.setPublishedVersion(null);
        assertThatThrownBy(() -> service.bundle(SCOPE, "orders", "en-US")).isInstanceOfSatisfying(CommonException.class,
                error -> assertThat(error.getCode()).isEqualTo(56402));
    }

    @Test void publishCopiesDraftAndDefaultLocaleAtomicallyWithRevisionAndAudit() {
        var page = page("orders", "en-US", Map.of("en-US", Map.of("title", "Old")));
        page.setDefaultLocale("zh-CN").setDraftTranslations("{\"zh-CN\":{\"title\":\"订单\"}}");
        when(repository.findById(1L)).thenReturn(Optional.of(page));
        var result = service.publish(1L, new DdcI18nPublishCommand("4"));
        assertThat(result.revision()).isEqualTo("5");
        assertThat(result.publishedVersion()).isEqualTo("2");
        assertThat(page.getPublishedDefaultLocale()).isEqualTo("zh-CN");
        assertThat(page.getPublishedTranslations()).isEqualTo(page.getDraftTranslations());
        verify(scopes).require(SCOPE, DdcAdminCapability.PUBLISH);
        verify(audit).insert(argThat(log -> log.getOperationType().equals("I18N_PUBLISH")
                && !log.getOperationContent().contains("订单")));
    }

    @Test void staleRevisionAndLostCompareAndSwapDoNotAuditSuccessfulPublication() {
        var page = page("orders", "en-US", Map.of("en-US", Map.of("title", "Orders")));
        when(repository.findById(1L)).thenReturn(Optional.of(page));
        assertThatThrownBy(() -> service.publish(1L, new DdcI18nPublishCommand("3"))).isInstanceOf(CommonException.class);
        verify(repository, never()).update(any());
        doReturn(Optional.empty()).when(repository).update(any());
        assertThatThrownBy(() -> service.publish(1L, new DdcI18nPublishCommand("4"))).isInstanceOfSatisfying(CommonException.class,
                error -> assertThat(error.getCode()).isEqualTo(56404));
        verifyNoInteractions(audit);
    }

    @Test void saveDraftPreservesPublishedSnapshot() {
        var page = page("orders", "en-US", Map.of("en-US", Map.of("title", "Published")));
        when(repository.findById(1L)).thenReturn(Optional.of(page));
        service.replaceDraft(1L, new DdcI18nDraftCommand("4", "Orders", "en-US", Map.of("en-US", Map.of("title", "Draft"))));
        assertThat(page.getPublishedTranslations()).contains("Published").doesNotContain("Draft");
        assertThat(page.getPublishedVersion()).isEqualTo(1L);
    }

    @Test void missingPermissionPrecedesBundleLookup() {
        when(scopes.require(SCOPE, DdcAdminCapability.I18N_READ)).thenThrow(new CommonException(56407, "denied", "denied"));
        assertThatThrownBy(() -> service.bundle(SCOPE, "orders", "en-US")).isInstanceOf(CommonException.class);
        verifyNoInteractions(repository);
    }

    @Test void emptyDraftCannotReplacePublishedSnapshot() {
        var page = page("orders", "en-US", Map.of("en-US", Map.of("title", "Published")));
        page.setDraftTranslations("{\"en-US\":{}}");
        when(repository.findById(1L)).thenReturn(Optional.of(page));
        assertThatThrownBy(() -> service.publish(1L, new DdcI18nPublishCommand("4"))).isInstanceOf(CommonException.class);
        verify(repository, never()).update(any());
    }

    private DdcI18nPageBO page(String code, String locale, Map<String, Map<String, String>> messages) {
        String json = validation.translations(locale, messages, false);
        return DdcI18nPageBO.builder().id(1L).tenantId(101L).namespaceId(7L).namespaceCode("main").bizCode("retail")
                .envCode("prod").appCode("shop").pageCode(code).name(code).version(4L).defaultLocale(locale)
                .draftTranslations(json).publishedDefaultLocale(locale).publishedTranslations(json).publishedVersion(1L).build();
    }
}
