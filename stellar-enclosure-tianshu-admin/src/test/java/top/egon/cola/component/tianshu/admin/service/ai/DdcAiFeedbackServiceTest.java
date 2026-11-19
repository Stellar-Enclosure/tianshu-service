package top.egon.cola.component.tianshu.admin.service.ai;

import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.tianshu.admin.config.DdcAiOptimizationProperties;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAiUsageFeedbackBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAiVersionBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiUsageReportBatchCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiUsageReportCommand;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcAiEnumConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcAiUsageFeedbackResultConverterImpl;
import top.egon.cola.component.tianshu.admin.model.dto.DdcAiJudgeVerdictDTO;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiJudgeStatePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiOutcomePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiRunErrorCodeEnum;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiUsageReportReceiptResult;
import top.egon.cola.component.tianshu.admin.repository.DdcAiResourceRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcAiUsageFeedbackRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcAiVersionRepository;
import top.egon.cola.component.tianshu.admin.service.ai.impl.DdcAiFeedbackServiceImpl;
import top.egon.cola.component.tianshu.admin.service.ai.validation.DdcAiScopeValidator;
import top.egon.cola.component.tianshu.model.ai.DdcAiOutcomeEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportStatusEnum;

import top.egon.cola.component.tianshu.admin.security.ai.DdcAiServiceCallerTestFixture;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcAiFeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T05:00:00Z");

    private final DdcAiUsageFeedbackRepository feedbackRepository = mock(DdcAiUsageFeedbackRepository.class);
    private final DdcAiResourceRepository resourceRepository = mock(DdcAiResourceRepository.class);
    private final DdcAiVersionRepository versionRepository = mock(DdcAiVersionRepository.class);
    private final DdcAiScopeValidator scopeValidator = mock(DdcAiScopeValidator.class);
    private final DdcAiOptimizationProperties properties = new DdcAiOptimizationProperties();
    private final TransactionTemplate transactions = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final DdcAiFeedbackServiceImpl service = new DdcAiFeedbackServiceImpl(feedbackRepository,
            resourceRepository, versionRepository, scopeValidator, new DdcAiEnumConverterImpl(),
            new DdcAiUsageFeedbackResultConverterImpl(),
            new ValidationUtils(Validation.buildDefaultValidatorFactory().getValidator()),
            transactions, transactions, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void knownResourceAndVersion() {
        MDC.put("tenantId", "42");
        when(resourceRepository.findActiveById(7L)).thenReturn(Optional.of(DdcAiResourceBO.builder().id(7L)
                .resourceKey("refund").namespaceCode("prod-space").bizCode("retail").envCode("prod")
                .appCode("assistant-app").build()));
        when(versionRepository.findActiveById(9L)).thenReturn(Optional.of(
                DdcAiVersionBO.builder().id(9L).resourceId(7L).versionNo(4L).build()));
        when(feedbackRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void acceptStoresReporterIdentityTruncatesExcerptsAndMarksPendingJudge() {
        properties.getFeedback().setMaxInputExcerptBytes(256);
        String longInput = "退".repeat(200);

        DdcAiUsageReportReceiptResult receipt = accept(report("01JAX0M7Q9K2R5T8V1W4X7Y0ZB").toBuilder()
                .inputExcerpt(longInput).build());

        assertThat(receipt.getItems()).singleElement().extracting(DdcAiUsageReportReceiptResult.ItemResult::getStatus)
                .isEqualTo(DdcAiUsageReportStatusEnum.ACCEPTED);
        ArgumentCaptor<DdcAiUsageFeedbackBO> inserted = ArgumentCaptor.forClass(DdcAiUsageFeedbackBO.class);
        verify(feedbackRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getJudgeState()).isEqualTo(DdcAiJudgeStatePersistenceEnum.PENDING);
        assertThat(inserted.getValue().getOutcome()).isEqualTo(DdcAiOutcomePersistenceEnum.FAILURE);
        assertThat(inserted.getValue().getReporterCredentialId()).isEqualTo("runtime");
        assertThat(inserted.getValue().getAppCode()).isEqualTo("assistant-app");
        assertThat(inserted.getValue().getInputExcerpt()).hasSize(85);
        assertThat(inserted.getValue().getInputExcerpt().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(256);
    }

    @Test
    void attributionComesFromVerifiedSourceRatherThanTheResourceOwner() {
        DdcAiServiceCallerTestFixture.call(Operation.REPORT, scope(),
                new DdcAiScopeDTO("runtime-biz", "unused", "runtime-env", "runtime-app"),
                42L, "runtime", "rotated-secret", NOW, () -> service.accept(DdcAiUsageReportBatchCommand.builder()
                        .reports(List.of(report("01JAX0M7Q9K2R5T8V1W4X7Y0ZB"))).build()));
        ArgumentCaptor<DdcAiUsageFeedbackBO> inserted = ArgumentCaptor.forClass(DdcAiUsageFeedbackBO.class);
        verify(feedbackRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getBizCode()).isEqualTo("runtime-biz");
        assertThat(inserted.getValue().getEnvCode()).isEqualTo("runtime-env");
        assertThat(inserted.getValue().getAppCode()).isEqualTo("runtime-app");
        assertThat(inserted.getValue().getReporterCredentialId()).isEqualTo("runtime");
    }

    @Test
    void explicitScoreSkipsJudging() {
        accept(report("01JAX0M7Q9K2R5T8V1W4X7Y0ZB").toBuilder().explicitScore(30).build());

        ArgumentCaptor<DdcAiUsageFeedbackBO> inserted = ArgumentCaptor.forClass(DdcAiUsageFeedbackBO.class);
        verify(feedbackRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getJudgeState()).isEqualTo(DdcAiJudgeStatePersistenceEnum.NOT_REQUIRED);
        assertThat(inserted.getValue().getExplicitScore()).isEqualTo((short) 30);
    }

    @Test
    void acceptMapsUniqueViolationToDuplicateAndKeepsOrder() {
        when(feedbackRepository.insert(any()))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new DuplicateKeyException("usage duplicate", new org.postgresql.util.PSQLException(
                        new org.postgresql.util.ServerErrorMessage("SERROR\u0000C23505\u0000nuk_ai_usage_feedback_usage\u0000\u0000"))))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DdcAiUsageReportReceiptResult receipt = accept(report("01JAX0M7Q9K2R5T8V1W4X7Y0Z1"),
                report("01JAX0M7Q9K2R5T8V1W4X7Y0Z1"), report("01JAX0M7Q9K2R5T8V1W4X7Y0Z3"));

        assertThat(receipt.getItems()).extracting(DdcAiUsageReportReceiptResult.ItemResult::getStatus).containsExactly(
                DdcAiUsageReportStatusEnum.ACCEPTED, DdcAiUsageReportStatusEnum.ACCEPTED_DUPLICATE,
                DdcAiUsageReportStatusEnum.ACCEPTED);
    }

    @Test
    void unrelatedStorageIntegrityFailureIsNeverAcknowledgedAsAUsageDuplicate() {
        when(feedbackRepository.insert(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("foreign-key-storage-failure"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accept(report("01JAX0M7Q9K2R5T8V1W4X7Y0Z1")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void acceptRejectsClockSkewInvalidFieldsAndForeignVersionsPerItem() {
        DdcAiUsageReportReceiptResult receipt = accept(
                report("01JAX0M7Q9K2R5T8V1W4X7Y0Z1").toBuilder().occurredAt(NOW.plus(Duration.ofMinutes(10))).build(),
                report("01JAX0M7Q9K2R5T8V1W4X7Y0Z2").toBuilder().occurredAt(NOW.minus(Duration.ofDays(8))).build(),
                report("bad").toBuilder().build(),
                report("01JAX0M7Q9K2R5T8V1W4X7Y0Z4").toBuilder().versionNo(5L).build(),
                report("01JAX0M7Q9K2R5T8V1W4X7Y0Z5").toBuilder().resourceKey("other").build(),
                report("01JAX0M7Q9K2R5T8V1W4X7Y0Z6").toBuilder().outputExcerpt("a\u0000b").build());

        assertThat(receipt.getItems()).extracting(DdcAiUsageReportReceiptResult.ItemResult::getReason).containsExactly(
                "CLOCK_SKEW", "CLOCK_SKEW", "INVALID_FIELD", "VERSION_MISMATCH", "RESOURCE_NOT_FOUND", "INVALID_FIELD");
        assertThat(receipt.getItems()).extracting(DdcAiUsageReportReceiptResult.ItemResult::getUsageId)
                .contains("bad");
        verify(feedbackRepository, never()).insert(any());
    }

    @Test
    void claimForJudgeMovesRowsToJudgingWithLeaseAndAttempt() {
        DdcAiUsageFeedbackBO pending = sample(DdcAiJudgeStatePersistenceEnum.PENDING, 0);
        when(feedbackRepository.findClaimableForJudge(Set.of(7L), NOW, 3, 50)).thenReturn(List.of(pending));
        when(feedbackRepository.update(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        List<DdcAiUsageFeedbackBO> claimed = service.claimForJudge(Set.of(7L), 50);

        assertThat(claimed).singleElement().satisfies(row -> {
            assertThat(row.getJudgeState()).isEqualTo(DdcAiJudgeStatePersistenceEnum.JUDGING);
            assertThat(row.getJudgeAttempts()).isEqualTo((short) 1);
            assertThat(row.getJudgeLeaseUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        });
    }

    @Test
    void competingJudgeClaimsReturnOnlyTheSuccessfulVersionedWrite() throws Exception {
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var won = new java.util.concurrent.atomic.AtomicBoolean();
        when(feedbackRepository.findClaimableForJudge(Set.of(7L), NOW, 3, 1)).thenAnswer(call -> {
            barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return List.of(sample(DdcAiJudgeStatePersistenceEnum.PENDING, 0));
        });
        when(feedbackRepository.update(any())).thenAnswer(call -> {
            DdcAiUsageFeedbackBO row = call.getArgument(0);
            return won.compareAndSet(false, true) ? Optional.of(row.toBuilder().version(4L).build()) : Optional.empty();
        });
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.claimForJudge(Set.of(7L), 1));
            var second = workers.submit(() -> service.claimForJudge(Set.of(7L), 1));
            var results = java.util.stream.Stream.concat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).stream(),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS).stream()).toList();
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getVersion()).isEqualTo(4L);
            assertThat(results.getFirst().getJudgeState()).isEqualTo(DdcAiJudgeStatePersistenceEnum.JUDGING);
        }
    }

    @Test
    void completeJudgeUsesClaimVersionWithoutReadingALaggingReplica() {
        when(feedbackRepository.completeJudgeIfOwned(1L, 3L, (short) 66, NOW)).thenReturn(true);
        service.completeJudge(1L, 3L, new DdcAiJudgeVerdictDTO(66, "ok"));
        service.completeJudge(1L, 4L, new DdcAiJudgeVerdictDTO(66, "ok"));
        verify(feedbackRepository).completeJudgeIfOwned(1L, 3L, (short) 66, NOW);
        verify(feedbackRepository).completeJudgeIfOwned(1L, 4L, (short) 66, NOW);
        verify(feedbackRepository, never()).findActiveById(1L);
    }

    @Test
    void failJudgeUsesClaimVersionAndLetsThePrimaryCheckTheAttemptLimit() {
        service.failJudge(1L, 3L, DdcAiRunErrorCodeEnum.MODEL_TIMEOUT);
        service.failJudge(1L, 4L, DdcAiRunErrorCodeEnum.INVALID_OUTPUT);
        verify(feedbackRepository).failJudgeIfOwned(1L, 3L, 3, "MODEL_TIMEOUT", NOW);
        verify(feedbackRepository).failJudgeIfOwned(1L, 4L, 3, "INVALID_OUTPUT", NOW);
        verify(feedbackRepository, never()).findActiveById(1L);
    }

    @Test
    void purgeExpiredUsesTheConfiguredRetention() {
        when(feedbackRepository.softDeleteExpired(NOW.minus(Duration.ofDays(30)), 1000)).thenReturn(7);

        assertThat(service.purgeExpired(1000)).isEqualTo(7);
    }

    private DdcAiUsageReportReceiptResult accept(DdcAiUsageReportCommand... reports) {
        return DdcAiServiceCallerTestFixture.call(Operation.REPORT, scope(), 42L, "runtime", "secret-v1", NOW,
                () -> service.accept(DdcAiUsageReportBatchCommand.builder().reports(List.of(reports)).build()));
    }

    private static DdcAiScopeDTO scope() {
        return new DdcAiScopeDTO("retail", "prod-space", "prod", "assistant-app");
    }

    private static DdcAiUsageReportCommand report(String usageId) {
        return DdcAiUsageReportCommand.builder()
                .usageId(usageId).namespaceCode("prod-space").resourceKey("refund").resourceId(7L).versionId(9L)
                .versionNo(4L).outcome(DdcAiOutcomeEnum.FAILURE).occurredAt(NOW.minusSeconds(30)).build();
    }

    private static DdcAiUsageFeedbackBO sample(DdcAiJudgeStatePersistenceEnum state, int attempts) {
        return DdcAiUsageFeedbackBO.builder().id(1L).version(3L).resourceId(7L).versionId(9L)
                .usageId("01JAX0M7Q9K2R5T8V1W4X7Y0ZB").outcome(DdcAiOutcomePersistenceEnum.FAILURE)
                .judgeState(state).judgeAttempts((short) attempts)
                .judgeLeaseUntil(state == DdcAiJudgeStatePersistenceEnum.JUDGING ? NOW : null)
                .occurredAt(NOW).build();
    }
}
