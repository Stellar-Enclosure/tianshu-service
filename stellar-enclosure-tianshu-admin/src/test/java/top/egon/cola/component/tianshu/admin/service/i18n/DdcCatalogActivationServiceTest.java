package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProducerBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProjectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceVersionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import top.egon.cola.component.tianshu.admin.repository.DdcCatalogActivationProducerRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcCatalogActivationProjectionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

class DdcCatalogActivationServiceTest {
    private final DdcCatalogActivationClient client = mock(DdcCatalogActivationClient.class);
    private final DdcCatalogActivationProjectionRepository projections = mock(DdcCatalogActivationProjectionRepository.class);
    private final DdcCatalogActivationProducerRepository producers = mock(DdcCatalogActivationProducerRepository.class);
    private final DdcI18nSourceVersionRepository sources = mock(DdcI18nSourceVersionRepository.class);
    private final DdcMetadataScopeRepository scopes = mock(DdcMetadataScopeRepository.class);
    private final DdcMetadataTransactionTestFixture manager = new DdcMetadataTransactionTestFixture();
    private final AtomicReference<DdcCatalogActivationProjectionBO> stored = new AtomicReference<>();
    private DdcCatalogActivationService service;

    @BeforeEach void setup() {
        MDC.put("tenantId", "101");
        var descriptor = DdcI18nBundleAssemblerTest.descriptor();
        when(client.fetch(SCOPE, "9")).thenAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return descriptor; });
        when(client.authorityResource(SCOPE)).thenReturn("https://rbac.example.test");
        when(projections.findByVersion(501L, 9L)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(projections.insert(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            DdcCatalogActivationProjectionBO row = call.getArgument(0); row.setId(901L); stored.set(row); return row;
        });
        when(scopes.lockEnabledTarget(501L)).thenReturn(Optional.of(DdcMetadataScopeBO.builder().tenantId(101L).catalogScopeId(501L)
                .bizCode("trade").appCode("order").namespaceCode("main").envCode("test").build()));
        var request = request();
        var source = DdcI18nSourceVersionBO.builder().id(701L).manifestDigest(request.digests().manifestDigest())
                .policyDigest(request.digests().policyDigest()).metadataDigest(request.digests().metadataDigest()).build();
        when(sources.findByVersion(501L, "order-admin", request.sourceVersion())).thenReturn(Optional.of(source));
        var member = DdcCatalogActivationProducerBO.builder().producerId("order-admin").producerVersion(1L).sourceVersion(request.sourceVersion()).sourceId(701L)
                .policyDigest(request.digests().policyDigest()).metadataDigest(request.digests().metadataDigest()).build();
        when(producers.findByProjection(501L, 901L)).thenReturn(List.of(member));
        service = new DdcCatalogActivationService(client, new DdcCatalogActivationJson(), projections, producers, scopes, sources, new TransactionTemplate(manager));
    }
    @AfterEach void cleanup() { MDC.clear(); }

    @Test void fetchHappensBeforeSqlAndAllMembersCommitAsOneImmutableProjection() {
        var selection = service.resolve(SCOPE, "9");
        assertThat(selection.descriptor()).isEqualTo(DdcI18nBundleAssemblerTest.descriptor());
        assertThat(selection.members()).hasSize(1); assertThat(manager.commits).isEqualTo(1);
        verify(producers).insertAll(argThat(rows -> rows.size() == 1 && rows.getFirst().getActivationProjectionId().equals(901L)));
        assertThat(service.resolve(SCOPE, "9")).isEqualTo(selection); verify(client, times(1)).fetch(any(), any());
    }

    @Test void missingSourceAndConflictingOwnersRollbackWithoutInventingAnActivation() {
        when(sources.findByVersion(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve(SCOPE, "9")).isInstanceOf(CommonException.class);
        assertThat(manager.rollbacks).isEqualTo(1); verify(projections, never()).insert(any());
    }

    @Test void duplicateSourceKeysAbortProjectionTransaction() {
        when(producers.countSelectionConflicts(501L, 901L)).thenReturn(1L);
        assertThatThrownBy(() -> service.resolve(SCOPE, "9")).isInstanceOf(CommonException.class);
        assertThat(manager.rollbacks).isEqualTo(1); assertThat(manager.commits).isZero();
    }

    @Test void corruptedStoredDigestOrMemberCountNeverFallsBackToLatest() {
        service.resolve(SCOPE, "9");
        stored.get().setDescriptorDigest("a".repeat(64));
        assertThatThrownBy(() -> service.resolve(SCOPE, "9")).isInstanceOf(CommonException.class);
        verify(client, times(1)).fetch(any(), any());
    }

    @Test void wrongTrustedTenantFailsBeforeAnyRepositoryOrNetworkCall() {
        MDC.put("tenantId", "102");
        assertThatThrownBy(() -> service.resolve(SCOPE, "9")).isInstanceOf(CommonException.class);
        verifyNoInteractions(projections, producers, client);
    }
}
