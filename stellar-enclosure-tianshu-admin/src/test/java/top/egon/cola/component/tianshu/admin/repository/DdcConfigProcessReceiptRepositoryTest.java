package top.egon.cola.component.tianshu.admin.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigProcessReceiptMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigProcessReceiptBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigProcessReceiptConverter;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigProcessReceiptPO;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigProcessReceiptRepositoryImpl;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DdcConfigProcessReceiptRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final DdcTenantContextDTO CONTEXT = new DdcTenantContextDTO(1001L, "1001", "config-sdk");
    private final ObjectMapper json = new ObjectMapper();
    private final DdcConfigProcessReceiptMapper mapper = mock(DdcConfigProcessReceiptMapper.class);
    private final DdcConfigBootBindingRepository boots = mock(DdcConfigBootBindingRepository.class);
    private final EgonColaIdentifierGenerator ids = mock(EgonColaIdentifierGenerator.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final AtomicReference<DdcConfigProcessReceiptPO> stored = new AtomicReference<>();
    private final AtomicLong desiredRevision = new AtomicLong(43L);
    private DdcConfigProcessReceiptConverter converter;
    private DdcConfigProcessReceiptRepositoryImpl repository;

    @BeforeEach
    void prepare() throws Exception {
        MDC.put("tenantId", "1001");
        MDC.put("userId", CONTEXT.actor());
        validator.afterPropertiesSet();
        converter = (DdcConfigProcessReceiptConverter) Class.forName(DdcConfigProcessReceiptConverter.class.getName() + "Impl")
                .getConstructor().newInstance();
        repository = new DdcConfigProcessReceiptRepositoryImpl(mapper, converter, new EgonColaMybatisPlusProperties(),
                ids, boots, transactions, validator, Clock.fixed(NOW, ZoneOffset.UTC));
        when(ids.nextId(any())).thenReturn(9001L);
        when(transactions.getIsolationLevel()).thenReturn(TransactionDefinition.ISOLATION_READ_COMMITTED);
        when(transactions.getPropagationBehavior()).thenReturn(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        when(mapper.selectManifest(1001L, 820001L, 43L)).thenAnswer(invocation -> desiredRevision.get() == 43L
                ? receipt(1L, "REJECTED").getAuthorizedManifest().toString() : null);
        when(boots.assertConfirmedFence(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(mapper.selectExactForUpdate(any())).thenAnswer(invocation -> stored.get());
        when(mapper.selectExact(any())).thenAnswer(invocation -> stored.get());
        when(mapper.insertIfAbsent(any())).thenAnswer(invocation -> {
            var row = (DdcConfigProcessReceiptPO) invocation.getArgument(0);
            return stored.compareAndSet(null, copy(row)) ? 1 : 0;
        });
        when(mapper.advance(any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            var row = (DdcConfigProcessReceiptPO) invocation.getArgument(0);
            var previous = stored.get();
            if (previous == null || !previous.getVersion().equals(invocation.getArgument(1))
                    || !previous.getReportSeq().equals(invocation.getArgument(2))) return 0;
            var next = copy(row);
            next.setVersion(previous.getVersion() + 1).setCreateTime(previous.getCreateTime()).setCreateUserId(previous.getCreateUserId());
            stored.set(next);
            return 1;
        });
    }

    @AfterEach
    void close() { validator.close(); MDC.clear(); }

    @Test
    void supersededDesiredRevisionReturnsConflictBeforeReadingOrMutatingReceiptState() {
        desiredRevision.set(44L);
        assertThat(repository.accept(CONTEXT, receipt(1L, "REJECTED")))
                .isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT);
        verify(mapper, never()).selectExactForUpdate(any());
        verify(mapper, never()).insertIfAbsent(any());
        verify(mapper, never()).advance(any(), anyLong(), anyLong());
        assertThat(stored.get()).isNull();
    }

    @Test
    void rejectedThenAppliedAdvancesSingleRowAndLateReportIsStale() {
        var rejected = receipt(1L, "REJECTED");
        assertThat(repository.accept(CONTEXT, rejected)).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.ADVANCED);
        assertThat(repository.accept(CONTEXT, receipt(2L, "APPLIED"))).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.ADVANCED);
        assertThat(repository.accept(CONTEXT, rejected)).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.STALE);
        assertThat(stored.get().getId()).isEqualTo(9001L);
        assertThat(stored.get().getReportSeq()).isEqualTo(2L);
        assertThat(stored.get().getResult()).isEqualTo("APPLIED");
        assertThat(stored.get().getAppliedRevision()).isEqualTo(43L);
        verify(mapper, times(1)).insertIfAbsent(any());
    }

    @Test
    void sameSequenceSameDigestIsReadOnlyButDifferentDigestConflicts() {
        var report = receipt(1L, "REJECTED");
        repository.accept(CONTEXT, report);
        assertThat(repository.accept(CONTEXT, report)).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.SAME);
        var changed = receipt(1L, "FAILED");
        assertThat(repository.accept(CONTEXT, changed)).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT);
        verify(mapper, never()).advance(any(), anyLong(), anyLong());
        assertThat(stored.get().getResult()).isEqualTo("REJECTED");
    }

    @Test
    void successCannotRegressAndRestartRequiredCannotBecomeSuccessInSameBoot() {
        repository.accept(CONTEXT, receipt(1L, "APPLIED"));
        assertThat(repository.accept(CONTEXT, receipt(2L, "FAILED"))).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT);
        stored.set(null);
        repository.accept(CONTEXT, receipt(1L, "RESTART_REQUIRED"));
        assertThat(repository.accept(CONTEXT, receipt(2L, "APPLIED"))).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT);
    }

    @Test
    void failedReportDoesNotInventAnAppliedVersionFromManifestOrExistingState() {
        repository.accept(CONTEXT, receipt(1L, "FAILED"));
        assertThat(stored.get().getAppliedRevision()).isNull();
        assertThat(stored.get().getAppliedDigest()).isNull();
        assertThat(stored.get().getAppliedAt()).isNull();
    }

    @Test
    void trustedTenantDigestManifestAndBootFenceAreRequiredBeforeMutation() {
        var crossTenant = receipt(1L, "REJECTED").setTenantId(2002L);
        assertThatThrownBy(() -> repository.accept(CONTEXT, crossTenant)).isInstanceOf(IllegalArgumentException.class);
        var altered = receipt(1L, "REJECTED").setReceiptDigest("f".repeat(64));
        assertThatThrownBy(() -> repository.accept(CONTEXT, altered)).isInstanceOf(IllegalArgumentException.class);
        var notFixed = receipt(1L, "REJECTED");
        when(mapper.selectManifest(anyLong(), anyLong(), anyLong())).thenReturn("{\"schemaVersion\":\"1\"}");
        assertThatThrownBy(() -> repository.accept(CONTEXT, notFixed)).isInstanceOf(IllegalArgumentException.class);
        verify(mapper, never()).insertIfAbsent(any());
        verify(mapper, never()).selectExact(any());
        verify(transactions, times(1)).execute(any());
    }

    @Test
    void conflictInsertRereadsWinnerInsteadOfTreatingZeroRowsAsSuccess() {
        var winner = converter.toSource(receipt(2L, "REJECTED"));
        winner.setId(9100L).setVersion(0L);
        when(mapper.selectExactForUpdate(any())).thenReturn(null, winner);
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        assertThat(repository.accept(CONTEXT, receipt(1L, "REJECTED"))).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.STALE);
    }

    @Test
    void commitUnknownUsesNewReadAndSameOriginalFingerprintWithoutSecondWrite() {
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = ((TransactionCallback<?>) invocation.getArgument(0))
                    .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
            if (calls.incrementAndGet() == 1) throw new TransactionSystemException("synthetic commit acknowledgement loss");
            return result;
        }).when(transactions).execute(any());
        assertThat(repository.accept(CONTEXT, receipt(1L, "REJECTED"))).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.SAME);
        assertThat(calls).hasValue(2);
        verify(mapper, times(1)).insertIfAbsent(any());
        verify(mapper).selectExact(any());
    }

    @Test
    void knownSupersessionDuringCommitConfirmationReturnsConflictWithoutASecondWrite() {
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = ((TransactionCallback<?>) invocation.getArgument(0))
                    .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
            if (calls.incrementAndGet() == 1) {
                desiredRevision.set(44L);
                throw new TransactionSystemException("synthetic lost commit reply followed by publication");
            }
            return result;
        }).when(transactions).execute(any());
        assertThat(repository.accept(CONTEXT, receipt(1L, "REJECTED")))
                .isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT);
        assertThat(calls).hasValue(2);
        verify(mapper, times(1)).insertIfAbsent(any());
        verify(mapper, never()).selectExact(any());
        assertThat(stored.get().getConfigRevision()).isEqualTo(43L);
    }

    @Test
    void authoritySelectionLocksTheCurrentPublishedItemBeforeTheReceiptRow() throws Exception {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        String resource = "mybatis/mapper/tianshu/DdcConfigProcessReceiptMapper.xml";
        try (var input = new org.springframework.core.io.ClassPathResource(resource).getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String statement = DdcConfigProcessReceiptMapper.class.getName() + ".selectManifest";
        String sql = configuration.getMappedStatement(statement).getBoundSql(java.util.Map.of(
                "tenantId", 1001L, "configId", 820001L, "configRevision", 43L)).getSql().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        assertThat(sql).contains("from ddc_config_item item", "join ddc_config_version v",
                "v.config_revision = item.published_version", "item.published_version = ?", "for update of item")
                .doesNotContain("_t0", "43", "820001");
        repository.accept(CONTEXT, receipt(1L, "REJECTED"));
        var order = inOrder(boots, mapper);
        order.verify(boots).assertConfirmedFence(any(), any());
        order.verify(mapper).selectManifest(1001L, 820001L, 43L);
        order.verify(mapper).selectExactForUpdate(any());
    }

    @Test
    void unresolvedCommitDoesNotInferEffectFromCurrentHighestSequence() {
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            int attempt = calls.incrementAndGet();
            Object result = ((TransactionCallback<?>) invocation.getArgument(0))
                    .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
            if (attempt == 1) {
                // A later report can win before the first writer learns its commit outcome.
                stored.set(converter.toSource(receipt(2L, "REJECTED")));
                throw new TransactionSystemException("synthetic uncertain commit");
            }
            return result;
        }).when(transactions).execute(any());
        assertThatThrownBy(() -> repository.accept(CONTEXT, receipt(1L, "REJECTED")))
                .isInstanceOf(DdcConfigProcessReceiptRepository.ReceiptUnavailableException.class)
                .hasMessage("CONFIG_RECEIPT_COMMIT_UNKNOWN");
        verify(mapper, times(1)).insertIfAbsent(any());
        assertThat(calls).hasValue(2);
    }

    @Test
    void slowerReplicaCannotMoveObservationTimeBeforeLockedRow() {
        repository.accept(CONTEXT, receipt(1L, "REJECTED"));
        var prior = stored.get();
        prior.setUpdateTime(NOW.plusSeconds(15));
        prior.setLastReceivedAt(NOW.plusSeconds(10));
        assertThat(repository.accept(CONTEXT, receipt(2L, "APPLIED")))
                .isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.ADVANCED);
        assertThat(stored.get().getLastReceivedAt()).isEqualTo(NOW.plusSeconds(15));
    }

    @Test
    void rawErrorTextBindsWireDigestButNeverEntersDurableDiagnostics() {
        var report = receipt(1L, "REJECTED").setReasonMessage("Bearer synthetic-secret and private configuration");
        report.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(converter.toCommand(report)).substring(7));
        String fingerprint = report.getReceiptDigest();
        repository.accept(CONTEXT, report);
        assertThat(stored.get().getReasonCode()).isEqualTo("APPLY_REJECTED");
        assertThat(stored.get().getReasonMessage()).isNull();
        assertThat(stored.get().getReceiptDigest()).isEqualTo(fingerprint);
        assertThat(report.getReasonMessage()).contains("synthetic-secret");
        assertThat(repository.accept(CONTEXT, report)).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.SAME);
        var changedText = receipt(1L, "REJECTED").setReasonMessage("different private details");
        changedText.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(converter.toCommand(changedText)).substring(7));
        assertThat(repository.accept(CONTEXT, changedText)).isEqualTo(DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT);
    }

    @Test
    void knownAuthorizationFailureDoesNotStartCommitResolution() {
        when(boots.assertConfirmedFence(any(), any())).thenThrow(new DdcConfigBootBindingRepository.BindingConflictException());
        assertThatThrownBy(() -> repository.accept(CONTEXT, receipt(1L, "REJECTED")))
                .isInstanceOf(DdcConfigBootBindingRepository.BindingConflictException.class);
        verify(transactions, times(1)).execute(any());
        verify(mapper, never()).selectExact(any());
        verify(mapper, never()).insertIfAbsent(any());
    }

    @Test
    void historicalProjectionNeverExposesPreviouslyStoredRawText() {
        var historical = converter.toSource(receipt(1L, "REJECTED"));
        historical.setReasonMessage("synthetic old credential text");
        when(mapper.selectHistory(1001L, NOW.minusSeconds(60), 0L, 10)).thenReturn(java.util.List.of(historical));
        var results = repository.readHistory(CONTEXT, NOW.minusSeconds(60), 0L, 10);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getReasonMessage()).isNull();
        assertThat(results.getFirst().getReasonCode()).isEqualTo("APPLY_REJECTED");
    }

    @Test
    void callbackStorageFailureIsStableAndDoesNotPretendCommitUncertainty() {
        when(mapper.selectExactForUpdate(any())).thenThrow(new DataAccessResourceFailureException("synthetic private SQL details"));
        assertThatThrownBy(() -> repository.accept(CONTEXT, receipt(1L, "REJECTED")))
                .isInstanceOf(DdcConfigProcessReceiptRepository.ReceiptStoreUnavailableException.class)
                .hasMessage("CONFIG_RECEIPT_STORE_UNAVAILABLE").hasNoCause();
        verify(transactions, times(1)).execute(any());
        verify(mapper, never()).selectExact(any());
    }

    @Test
    void knownRollbackAfterCallbackDoesNotProbeAsUnknownCommit() {
        doAnswer(invocation -> {
            ((TransactionCallback<?>) invocation.getArgument(0))
                    .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
            throw new UnexpectedRollbackException("synthetic definitive rollback");
        }).when(transactions).execute(any());
        assertThatThrownBy(() -> repository.accept(CONTEXT, receipt(1L, "REJECTED")))
                .isInstanceOf(DdcConfigProcessReceiptRepository.ReceiptStoreUnavailableException.class)
                .hasMessage("CONFIG_RECEIPT_STORE_UNAVAILABLE").hasNoCause();
        verify(transactions, times(1)).execute(any());
        verify(mapper, never()).selectExact(any());
    }

    private DdcConfigProcessReceiptPO copy(DdcConfigProcessReceiptPO row) { return converter.toSource(converter.toTarget(row)); }

    private DdcConfigProcessReceiptBO receipt(long sequence, String result) {
        var scope = json.createObjectNode().put("tenantId", "1001").put("bizCode", "core").put("env", "prod")
                .put("appCode", "reader").put("resourceName", "application.yml");
        var manifest = json.createObjectNode().put("schemaVersion", "1")
                .put("contentSha256", "sha256:" + "a".repeat(64)).put("restartDigest", "sha256:" + "b".repeat(64)).put("enabled", true);
        manifest.putObject("source").put("configId", "820001").put("revision", "43");
        manifest.set("scope", scope.deepCopy());
        boolean success = "APPLIED".equals(result);
        var receipt = DdcConfigProcessReceiptBO.builder().tenantId(1001L).configId(820001L).configRevision(43L).scopeJson(scope)
                .targetBindingId("reader-prod").targetRevision(6L).clusterId("prod-1").k8sNamespace("reader-prod")
                .workloadUid("workload-1").podUid("pod-1").containerName("application").containerId("containerd://a1")
                .bootId("boot-1").bootFence(8001L).contentDigest("a".repeat(64)).restartDigest("b".repeat(64))
                .result(result).reasonCode(success ? null : "APPLY_REJECTED").reasonMessage(null)
                .reportSeq(sequence).reportedAt(NOW.minusSeconds(1)).appliedRevision(success ? 43L : null)
                .appliedDigest(success ? "a".repeat(64) : null).appliedAt(success ? NOW.minusSeconds(1) : null)
                .authorizedManifest(manifest).build();
        return receipt.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(converter.toCommand(receipt)).substring(7));
    }
}
