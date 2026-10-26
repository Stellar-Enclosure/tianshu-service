package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogSelectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceMessageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPublishedOverrideBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nPublishCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceMessageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPublishedOverrideRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

class DdcI18nPageServiceTest {
    private static final DdcI18nScope PAGE_SCOPE = new DdcI18nScope("trade", "main", "test", "order");
    private static final String OPERATION = "2dbecf98-a5e4-41a6-b805-9c2c5387be7c";
    private final DdcI18nPageRepository repository = mock(DdcI18nPageRepository.class);
    private final DdcI18nScopeValidator scopes = mock(DdcI18nScopeValidator.class);
    private final DdcOperationLogRepository audit = mock(DdcOperationLogRepository.class);
    private final DdcCatalogActivationService activations = mock(DdcCatalogActivationService.class);
    private final DdcI18nSourceMessageRepository messages = mock(DdcI18nSourceMessageRepository.class);
    private final DdcI18nPublishedOverrideRepository publications = mock(DdcI18nPublishedOverrideRepository.class);
    private final DdcMetadataScopeRepository metadataScopes = mock(DdcMetadataScopeRepository.class);
    private final DdcMetadataTransactionTestFixture manager = new DdcMetadataTransactionTestFixture();
    private final Map<String, DdcI18nPublishedOverrideBO> receipts = new HashMap<>();
    private DdcI18nPageService service;
    private DdcI18nPageBO page;
    private DdcI18nSourceMessageBO source;

    @BeforeEach void setup() {
        MDC.put("tenantId", "101");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", ""));
        when(scopes.require(eq(PAGE_SCOPE), any())).thenReturn(7L);
        var descriptor = DdcI18nBundleAssemblerTest.descriptor();
        var selection = new DdcCatalogSelectionBO(901L, descriptor, List.of(new DdcCatalogSelectionBO.Member("order-admin", "1", request().sourceVersion(), 701L)));
        when(activations.resolve(eq(SCOPE), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return selection;
        });
        source = DdcI18nBundleAssemblerTest.source("orders", "title", Map.of("zh-CN", "源 {{name}}", "en-US", "Source {{name}}"));
        when(messages.findByActivationPage(eq(501L), eq(901L), eq("orders"), anyBoolean(), eq(0L), eq(64))).thenReturn(List.of(source));
        when(metadataScopes.lockEnabledTarget(501L)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(DdcMetadataScopeBO.builder().tenantId(101L).catalogScopeId(501L).bizCode("trade")
                    .namespaceCode("main").envCode("test").appCode("order").build());
        });
        page = DdcI18nPageBO.builder().id(1L).tenantId(101L).namespaceId(7L).namespaceCode("main").bizCode("trade")
                .envCode("test").appCode("order").catalogScopeId(501L).draftActivationVersion(9L).pageCode("orders").name("Orders")
                .version(4L).defaultLocale("zh-CN").draftTranslations("{\"en-US\":{\"title\":\"Draft {{name}}\"}}")
                .draftSourceHashes(CanonicalJson.write(hashes())).publishedDefaultLocale("zh-CN").publishedTranslations("{\"en-US\":{\"title\":\"Published {{name}}\"}}")
                .publishedVersion(1L).publishedSourceHashes(CanonicalJson.write(hashes())).build();
        when(repository.findById(1L)).thenReturn(Optional.of(page));
        when(repository.lockById(1L)).thenReturn(Optional.of(page));
        when(repository.findByPageCode(PAGE_SCOPE, "orders")).thenReturn(Optional.of(page));
        when(repository.update(any())).thenAnswer(call -> Optional.of(call.<DdcI18nPageBO>getArgument(0)
                .setVersion(call.<DdcI18nPageBO>getArgument(0).getVersion() + 1)));
        when(publications.findByOperation(anyLong(), anyLong(), anyString())).thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(2))));
        when(publications.insert(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            DdcI18nPublishedOverrideBO snapshot = call.getArgument(0); snapshot.setId(991L); receipts.put(snapshot.getPublishOperationId(), snapshot); return snapshot;
        });
        service = new DdcI18nPageService(repository, scopes, new DdcI18nValidation(), audit, activations, messages, publications, metadataScopes,
                new DdcI18nBundleAssembler(), new TransactionTemplate(manager), new TransactionTemplate(manager), Clock.fixed(NOW, ZoneOffset.UTC));
    }
    @AfterEach void cleanup() { MDC.clear(); SecurityContextHolder.clearContext(); }

    @Test void draftChangesDoNotLeakIntoRuntimeOrInvalidateBundleVersion() {
        when(publications.findApplicableEntries(501L, 901L, "orders", 0L, 64)).thenReturn(List.of(DdcI18nBundleAssemblerTest.entry(source, "CURRENT", source.getSourceHash(), Map.of("en-US", "Published {{name}}"))));
        var first = service.bundle(PAGE_SCOPE, "501", "9", "orders", "en-US");
        page.setVersion(8L).setDraftTranslations("{}");
        var same = service.bundle(PAGE_SCOPE, "501", "9", "orders", "en-US");
        assertThat(first.messages()).containsEntry("title", "Published {{name}}");
        assertThat(same.version()).isEqualTo(first.version());
        page.setPublishedVersion(2L);
        assertThat(service.bundle(PAGE_SCOPE, "501", "9", "orders", "en-US").version()).isNotEqualTo(first.version());
    }

    @Test void publicationCopiesOnlySavedDraftAndReplaysOriginalReceiptWithoutAnotherWrite() {
        var command = new DdcI18nPublishCommand("4", OPERATION);
        var first = service.publish(1L, command);
        assertThat(first.draftRevision()).isEqualTo("4"); assertThat(first.publicationRevision()).isEqualTo("2");
        assertThat(first.publishedAt()).isEqualTo(NOW); assertThat(page.getVersion()).isEqualTo(5L);
        assertThat(page.getPublishedTranslations()).isEqualTo(page.getDraftTranslations());
        assertThat(service.publish(1L, command)).isEqualTo(first);
        verify(publications, times(1)).insert(any()); verify(repository, times(1)).update(any());
        verify(audit, times(1)).insert(argThat(log -> log.getOperationType().equals("I18N_PUBLISH") && !log.getOperationContent().contains("Draft")));
        assertThatThrownBy(() -> service.publish(1L, new DdcI18nPublishCommand("5", OPERATION))).isInstanceOf(CommonException.class);
    }

    @Test void staleRevisionAndLostCasNeverProduceSuccessAudit() {
        assertThatThrownBy(() -> service.publish(1L, new DdcI18nPublishCommand("3", OPERATION))).isInstanceOf(CommonException.class);
        verify(publications, never()).insert(any()); verify(repository, never()).update(any());
        doReturn(Optional.empty()).when(repository).update(any());
        assertThatThrownBy(() -> service.publish(1L, new DdcI18nPublishCommand("4", OPERATION))).isInstanceOf(CommonException.class);
        assertThat(manager.rollbacks).isEqualTo(2); verifyNoInteractions(audit);
    }

    @Test void savingSparseLocaleOverridesPreservesPublishedSnapshotAndChecksEverySourceHash() {
        var saved = service.replaceDraft(1L, new DdcI18nDraftCommand("4", "Orders", "zh-CN", Map.of("en-US", Map.of("title", "Changed {{name}}")), "9", hashes()));
        assertThat(saved.revision()).isEqualTo("5"); assertThat(saved.publishedTranslations()).isEqualTo(Map.of("en-US", Map.of("title", "Published {{name}}")));
        assertThat(page.getPublishedVersion()).isEqualTo(1L); verifyNoInteractions(publications);
        assertThatThrownBy(() -> service.replaceDraft(1L, new DdcI18nDraftCommand("5", "Orders", "zh-CN", Map.of(), "9", Map.of())))
                .isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56404));
    }

    @Test void placeholderChangesAreRejectedBeforeWriteAndEmptyPublicationIsExplicitClear() {
        assertThatThrownBy(() -> service.replaceDraft(1L, new DdcI18nDraftCommand("4", "Orders", "zh-CN", Map.of("en-US", Map.of("title", "{{amount}}")), "9", hashes())))
                .isInstanceOf(CommonException.class);
        verify(repository, never()).update(any());
        service.replaceDraft(1L, new DdcI18nDraftCommand("4", "Orders", "zh-CN", Map.of(), "9", hashes()));
        var cleared = service.publish(1L, new DdcI18nPublishCommand("5", OPERATION));
        assertThat(cleared.translations()).isEmpty(); assertThat(cleared.reviewedSourceHashes()).isEqualTo(hashes());
    }

    @Test void sourceOnlyPageHasNoArtificialHumanDraftAndCanCreateReviewedEmptyDraft() {
        when(repository.findByPageCode(PAGE_SCOPE, "orders")).thenReturn(Optional.empty());
        var sourcePage = service.sourcePage(PAGE_SCOPE, "501", "9", "orders");
        assertThat(sourcePage.id()).isNull(); assertThat(sourcePage.translations()).isEmpty(); assertThat(sourcePage.sourceHashes()).isEqualTo(hashes());
        when(repository.insert(any())).thenAnswer(call -> call.<DdcI18nPageBO>getArgument(0).setId(2L).setVersion(0L));
        var created = service.create(new DdcI18nCreateCommand("trade", "main", "test", "order", "orders", "Orders", "zh-CN", Map.of(), "501", "9", hashes()));
        assertThat(created.revision()).isEqualTo("0"); assertThat(created.publishedVersion()).isNull(); verifyNoInteractions(publications);
    }

    @Test void permissionFailurePrecedesDescriptorAndReceiptReadsAreReauthorized() {
        when(scopes.require(PAGE_SCOPE, DdcAdminCapability.I18N_READ)).thenThrow(new CommonException(56407, "denied", "denied"));
        assertThatThrownBy(() -> service.bundle(PAGE_SCOPE, "501", "9", "orders", "en-US")).isInstanceOf(CommonException.class);
        verifyNoInteractions(activations);
        when(scopes.require(PAGE_SCOPE, DdcAdminCapability.READ)).thenThrow(new CommonException(56407, "denied", "denied"));
        assertThatThrownBy(() -> service.publication(1L, OPERATION)).isInstanceOf(CommonException.class); verifyNoInteractions(publications);
    }

    @Test void oldUnversionedBundleAndPublishWithoutOperationAreRejected() {
        assertThatThrownBy(() -> service.bundle(PAGE_SCOPE, "orders", "en-US")).isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> service.publish(1L, new DdcI18nPublishCommand("4"))).isInstanceOf(CommonException.class);
        verifyNoInteractions(activations, publications, audit);
    }

    @Test void humanSnapshotAboveTwoMiBIsRejectedBeforeAnyWriteWhileTheSourcePageRemainsValid() {
        var rows = new java.util.ArrayList<DdcI18nSourceMessageBO>();
        Map<String, String> reviewed = new java.util.TreeMap<>();
        Map<String, String> translated = new java.util.TreeMap<>();
        for (int index = 0; index < 256; index++) {
            String key = "key" + index;
            var message = new top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest.Message("orders", key, "zh-CN", Map.of("zh-CN", "Source"));
            String hash = top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules.sourceHash(message);
            rows.add(DdcI18nSourceMessageBO.builder().tenantId(101L).catalogScopeId(501L).sourceId(701L).producerId("order-admin")
                    .sourceVersion(source.getSourceVersion()).pageCode("orders").messageKey(key).defaultLocale("zh-CN").sourceHash(hash)
                    .defaultsJson(CanonicalJson.write(message.defaults())).build());
            reviewed.put(key, hash); translated.put(key, "x".repeat(8192));
        }
        when(messages.findByActivationPage(eq(501L), eq(901L), eq("orders"), eq(false), anyLong(), eq(64))).thenAnswer(call -> {
            int offset = call.<Long>getArgument(4).intValue();
            return rows.subList(Math.min(offset, rows.size()), Math.min(offset + 64, rows.size()));
        });
        assertThat(CanonicalJson.write(Map.of("en-US", translated)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isGreaterThan(DdcI18nValidation.MAX_CONTENT_BYTES);
        assertThatThrownBy(() -> service.replaceDraft(1L, new DdcI18nDraftCommand("4", "Orders", "zh-CN", Map.of("en-US", translated), "9", reviewed)))
                .isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56412));
        verify(repository, never()).update(any()); verifyNoInteractions(publications, audit, metadataScopes);
        assertThat(manager.commits).isZero(); assertThat(page.getVersion()).isEqualTo(4L);
        String removed = rows.removeLast().getMessageKey(); reviewed.remove(removed); translated.remove(removed);
        assertThat(CanonicalJson.write(Map.of("en-US", translated)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThan(DdcI18nValidation.MAX_CONTENT_BYTES);
        assertThat(service.replaceDraft(1L, new DdcI18nDraftCommand("4", "Orders", "zh-CN", Map.of("en-US", translated), "9", reviewed)).revision()).isEqualTo("5");
    }

    @Test void sourceReadsArePagedAndResponseOverflowDoesNotTruncateOrReadHumanHistory() {
        var large = DdcI18nSourceMessageBO.builder().defaultsJson("x".repeat(262_144)).pageCode("orders").messageKey("title").build();
        when(messages.findByActivationPage(eq(501L), eq(901L), eq("orders"), anyBoolean(), eq(0L), eq(64))).thenReturn(java.util.Collections.nCopies(64, large));
        assertThatThrownBy(() -> service.bundle(PAGE_SCOPE, "501", "9", "orders", "en-US"))
                .isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56412));
        verifyNoInteractions(publications); verify(messages).findByActivationPage(501L, 901L, "orders", true, 0L, 64);
    }

    private Map<String, String> hashes() { return Map.of("title", source.getSourceHash()); }
}
