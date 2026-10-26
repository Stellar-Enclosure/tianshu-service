package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nImportReceiptBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceVersionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcResourceMetadataProjectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportGrantBO;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nImportReceiptRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceMessageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcResourceMetadataProjectionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcSourceImportGrantRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

class DdcI18nSourceImportServiceTest {
    private final DdcMetadataScopeRepository scopes = mock(DdcMetadataScopeRepository.class);
    private final DdcSourceImportGrantRepository grants = mock(DdcSourceImportGrantRepository.class);
    private final DdcI18nSourceVersionRepository sources = mock(DdcI18nSourceVersionRepository.class);
    private final DdcResourceMetadataProjectionRepository projections = mock(DdcResourceMetadataProjectionRepository.class);
    private final DdcI18nSourceMessageRepository messages = mock(DdcI18nSourceMessageRepository.class);
    private final DdcI18nImportReceiptRepository receipts = mock(DdcI18nImportReceiptRepository.class);
    private final DdcOperationLogRepository audit = mock(DdcOperationLogRepository.class);
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final Map<String, DdcI18nImportReceiptBO> storedReceipts = new HashMap<>();
    private final Map<String, DdcI18nSourceVersionBO> storedSources = new HashMap<>();
    private DdcI18nSourceImportService service;
    private DdcSourceImportGrantBO grant;

    @BeforeEach void setup() {
        var actor = identity();
        grant = DdcSourceImportGrantBO.builder().id(301L).tenantId(101L).catalogScopeId(501L).producerId("order-admin")
                .issuerUri(ISSUER.toString()).clientId(actor.principal().clientId()).resourceUri(RESOURCE.toString())
                .principalBindingDigest(actor.bindingDigest()).enabled(true).version(0L).build();
        var scope = DdcMetadataScopeBO.builder().id(401L).tenantId(101L).catalogScopeId(501L).bizCode("trade")
                .appCode("order").namespaceCode("main").envCode("test").build();
        when(grants.findEnabled(anyLong(), anyString(), anyString())).thenReturn(Optional.of(grant));
        when(grants.lockEnabled(anyLong(), anyString(), anyString())).thenReturn(Optional.of(grant));
        when(scopes.lockEnabledTarget(anyLong())).thenAnswer(call -> {
            assertThat(EgonColaTenantIdProvider.currentTenantId()).isEqualTo(101L);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(scope);
        });
        when(receipts.findByOperation(anyLong(), anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(storedReceipts.get(call.getArgument(2))));
        when(sources.findByVersion(anyLong(), anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(storedSources.get(call.getArgument(2))));
        when(sources.insert(any())).thenAnswer(call -> {
            DdcI18nSourceVersionBO row = call.getArgument(0);
            row.setId(701L).setTenantId(101L);
            storedSources.put(row.getSourceVersion(), row);
            return row;
        });
        AtomicLong ids = new AtomicLong(900);
        when(receipts.nextReceiptId()).thenAnswer(call -> ids.incrementAndGet());
        when(receipts.insert(any())).thenAnswer(call -> {
            DdcI18nImportReceiptBO row = call.getArgument(0);
            storedReceipts.put(row.getOperationId(), row);
            return row;
        });
        service = new DdcI18nSourceImportService(scopes, grants, sources, projections, messages, receipts, audit,
                new DdcTenantContextExecutor(), new TransactionTemplate(transactions), Clock.fixed(NOW, ZoneOffset.UTC), new DdcI18nSourceImportJson());
    }

    @AfterEach void cleanup() { MDC.clear(); }

    @Test void persistsAllSevenResourceKindsAndPermissionsUnderTheVerifiedTenantTransaction() {
        var result = service.importSource(identity(), request());
        assertThat(result.matches(request())).isTrue();
        assertThat(result.resourceCount()).isEqualTo(7);
        assertThat(result.permissionCount()).isEqualTo(3);
        assertThat(result.messageCount()).isEqualTo(10);
        assertThat(result.importedAt()).isEqualTo(NOW);
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
        assertThat(MDC.get("tenantId")).isNull();
        @SuppressWarnings("unchecked") ArgumentCaptor<List<DdcResourceMetadataProjectionBO>> rows = ArgumentCaptor.forClass(List.class);
        verify(projections).insertAll(rows.capture());
        assertThat(rows.getValue()).hasSize(10);
        assertThat(rows.getValue().stream().filter(row -> "RESOURCE".equals(row.getMetadataKind())).map(DdcResourceMetadataProjectionBO::getResourceType))
                .containsExactlyInAnyOrder("APP", "MENU", "PAGE", "COMPONENT", "BUTTON", "API", "FIELD");
        assertThat(rows.getValue().stream().filter(row -> "FIELD".equals(row.getResourceType())).findFirst().orElseThrow().getReferencedPermissionCodesJson())
                .isEqualTo("[\"order:field:read\",\"order:read\",\"order:write\"]");
        verify(audit).insert(argThat(log -> "METADATA_SOURCE_IMPORT".equals(log.getOperationType()) && "service-grant:301".equals(log.getOperator())));
    }

    @Test void exactRetryReturnsTheOriginalReceiptWithoutWritingAgainButStillChecksTheGrant() {
        var original = service.importSource(identity(), request());
        clearInvocations(sources, projections, messages, receipts, audit, grants);
        assertThat(service.importSource(identity(), request())).isEqualTo(original);
        verify(grants).findEnabled(501L, "order-admin", identity().bindingDigest());
        verify(grants).lockEnabled(501L, "order-admin", identity().bindingDigest());
        verify(receipts).findByOperation(501L, "order-admin", request().operationId());
        verifyNoMoreInteractions(receipts);
        verifyNoInteractions(sources, projections, messages, audit);
    }

    @Test void reusingAnOperationWithChangedAssignedIdsIsAConflict() {
        var original = request();
        service.importSource(identity(), original);
        Map<String, String> changed = new LinkedHashMap<>(original.resourceIds());
        changed.put("order.api", "99999");
        var conflict = new SourceImportRequest(original.operationId(), original.scope(), original.producerId(), original.producerVersion(),
                original.sourceVersion(), original.manifest(), original.digests(), changed, original.permissionIds());
        assertThatThrownBy(() -> service.importSource(identity(), conflict)).isInstanceOfSatisfying(CommonException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(56421));
        assertThat(storedReceipts).hasSize(1);
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test void aNewProducerRevisionCanReuseTheSameImmutableSourceContent() {
        var original = request();
        var first = service.importSource(identity(), original);
        var next = next(original);
        var second = service.importSource(identity(), next);
        assertThat(second.matches(next)).isTrue();
        assertThat(second.receiptId()).isNotEqualTo(first.receiptId());
        verify(sources, times(1)).insert(any());
        verify(projections, times(1)).insertAll(anyList());
        verify(messages, times(1)).insertAll(anyList());
        assertThat(storedReceipts.values()).extracting(DdcI18nImportReceiptBO::getProducerVersion).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test void receiptReplayCannotBypassGrantRevocation() {
        service.importSource(identity(), request());
        clearInvocations(receipts, scopes);
        when(grants.findEnabled(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.receipt(identity(), SCOPE, "order-admin", request().operationId())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(receipts, scopes);
    }

    @Test void aGrantHashNeverSubstitutesForItsExactPrincipalBinding() {
        grant.setIssuerUri("https://another.example.test/tenants/101");
        assertThatThrownBy(() -> service.importSource(identity(), request())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(scopes, sources, receipts, projections, messages);
    }

    @Test void targetTenantAndNamespaceCannotBeChosenByTheRequest() {
        var original = request();
        var otherTenant = new CatalogScope("501", "102", "trade", "order", "main", "test");
        var wrongTenant = new SourceImportRequest(original.operationId(), otherTenant, original.producerId(), "1", original.sourceVersion(),
                original.manifest(), original.digests(), original.resourceIds(), original.permissionIds());
        assertThatThrownBy(() -> service.importSource(identity(), wrongTenant)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(grants, scopes, receipts);
        var otherNamespace = new CatalogScope("501", "101", "trade", "order", "other", "test");
        var wrongNamespace = new SourceImportRequest(original.operationId(), otherNamespace, original.producerId(), "1", original.sourceVersion(),
                original.manifest(), original.digests(), original.resourceIds(), original.permissionIds());
        assertThatThrownBy(() -> service.importSource(identity(), wrongNamespace)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sources, receipts);
    }

    @Test void failedProjectionPersistenceRollsBackTheTransactionAndRestoresThePreviousContext() {
        MDC.put("tenantId", "999");
        MDC.put("userId", "before");
        doThrow(new IllegalStateException("persistence failure")).when(messages).insertAll(anyList());
        assertThatThrownBy(() -> service.importSource(identity(), request())).hasMessage("persistence failure");
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
        verify(receipts, never()).insert(any());
        verifyNoInteractions(audit);
        assertThat(MDC.get("tenantId")).isEqualTo("999");
        assertThat(MDC.get("userId")).isEqualTo("before");
    }

    @Test void readsOnlyTheOriginalCommittedReceiptForTheFullTarget() {
        var imported = service.importSource(identity(), request());
        assertThat(service.receipt(identity(), SCOPE, "order-admin", request().operationId())).isEqualTo(imported);
        verify(receipts, times(2)).findByOperation(501L, "order-admin", request().operationId());
        assertThatThrownBy(() -> service.receipt(identity(), SCOPE, "order-admin", "09a849ae-f80c-444b-ae90-3709dc50f5ed"))
                .isInstanceOfSatisfying(CommonException.class, failure -> assertThat(failure.getCode()).isEqualTo(56422));
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;
        private int commits;
        private int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
