package top.egon.cola.component.tianshu.admin.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.component.tianshu.admin.dao.DdcAiUsageFeedbackMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAiScoreAggregateBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcAiUsageFeedbackConverterImpl;
import top.egon.cola.component.tianshu.admin.model.po.DdcAiScoreAggregatePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcAiUsageFeedbackPO;
import top.egon.cola.component.tianshu.admin.model.query.DdcAiOptimizationRunPageQuery;
import top.egon.cola.component.tianshu.admin.model.query.DdcAiUsageFeedbackPageQuery;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcAiUsageFeedbackRepositoryImpl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcAiFeedbackOptimizationRepositoryContractTest {

    private static jakarta.validation.ValidatorFactory validators;
    private static top.egon.cola.component.common.mybatis.model.EgonColaModelValidationUtils.Binding validationBinding;

    private static final String FEEDBACK = "top.egon.cola.component.tianshu.admin.dao.DdcAiUsageFeedbackMapper";
    private static final String POLICY = "top.egon.cola.component.tianshu.admin.dao.DdcAiOptimizationPolicyMapper";
    private static final String RUN = "top.egon.cola.component.tianshu.admin.dao.DdcAiOptimizationRunMapper";
    private static final Instant NOW = Instant.parse("2026-10-17T05:00:00Z");

    private final DdcAiUsageFeedbackMapper feedbackMapper = mock(DdcAiUsageFeedbackMapper.class);
    private final DdcAiUsageFeedbackRepositoryImpl feedbackRepository =
            new DdcAiUsageFeedbackRepositoryImpl(feedbackMapper, new DdcAiUsageFeedbackConverterImpl(),
                    new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties());

    @BeforeAll
    static void bindValidation() {
        validators = jakarta.validation.Validation.buildDefaultValidatorFactory();
        validationBinding = top.egon.cola.component.common.mybatis.model.EgonColaModelValidationUtils.initialize(
                new top.egon.cola.component.common.core.validation.ValidationUtils(validators.getValidator()),
                DdcAiFeedbackOptimizationRepositoryContractTest.class);
    }

    @AfterAll
    static void releaseValidation() {
        validationBinding.close();
        validators.close();
    }

    @BeforeEach
    void bindTenant() {
        MDC.put("tenantId", "42");
    }

    @AfterEach
    void clearTenant() {
        MDC.clear();
    }

    @Test
    void mapperXmlDeclaresEveryStatementOnLogicalTables() throws Exception {
        Configuration configuration = parse();

        for (String statement : List.of(FEEDBACK + ".selectActiveById", FEEDBACK + ".selectActiveByIds",
                FEEDBACK + ".selectClaimableForJudge", FEEDBACK + ".selectScoreAggregate", FEEDBACK + ".selectWorst",
                FEEDBACK + ".selectPage", FEEDBACK + ".countPage", FEEDBACK + ".selectExpiredIdsForUpdate",
                FEEDBACK + ".deleteVersionedById", FEEDBACK + ".completeJudgeIfOwned", FEEDBACK + ".failJudgeIfOwned",
                POLICY + ".selectActiveById", POLICY + ".selectActiveByIds", POLICY + ".selectActiveByResourceId",
                POLICY + ".selectActiveByResourceIdForUpdate", POLICY + ".selectEvaluableForUpdate",
                POLICY + ".selectJudgeEnabledResourceIds", POLICY + ".deleteVersionedById",
                RUN + ".selectActiveById", RUN + ".selectActiveByIds", RUN + ".selectByRequestId",
                RUN + ".selectMaxRunNo", RUN + ".selectActiveRunForResource", RUN + ".selectLatestFinished",
                RUN + ".selectPendingIds", RUN + ".selectExpiredRunning", RUN + ".selectPage", RUN + ".countPage")) {
            assertThat(configuration.hasStatement(statement)).as(statement).isTrue();
        }
    }

    @Test
    void claimAndRetentionReadBoundedCandidatesWithoutLocks() throws Exception {
        Configuration configuration = parse();

        String claim = sql(configuration, FEEDBACK + ".selectClaimableForJudge", Map.of("tenantId", 42L,
                "resourceIds", List.of(7L, 8L), "now", NOW, "maxAttempts", 3, "limit", 50));
        String evaluable = sql(configuration, POLICY + ".selectEvaluableForUpdate", Map.of("tenantId", 42L, "limit", 50));
        String expired = sql(configuration, FEEDBACK + ".selectExpiredIdsForUpdate",
                Map.of("tenantId", 42L, "before", NOW, "limit", 1000));

        assertThat(claim).contains("from ddc_ai_usage_feedback f", "f.tenant_id = ?", "f.explicit_score is null",
                "f.judge_attempts < ?", "f.judge_state = 'judging' and f.judge_lease_until < ?")
                .doesNotContain("_t0", "for update", "for share");
        assertThat(evaluable).contains("p.active_run_id is null", "nulls first").doesNotContain("for update", "for share");
        assertThat(expired).contains("f.occurred_at < ?").doesNotContain("for update", "for share");
    }

    @Test
    void judgeCompletionAndFailureAreVersionedPrimaryCasWrites() throws Exception {
        Configuration configuration = parse();
        var row = DdcAiUsageFeedbackPO.builder().id(1L).tenantId(42L).version(7L)
                .updateUserId("worker").updateTime(NOW).build();
        String complete = sql(configuration, FEEDBACK + ".completeJudgeIfOwned",
                Map.of("et", row, "score", (short) 80, "judgedAt", NOW));
        String fail = sql(configuration, FEEDBACK + ".failJudgeIfOwned",
                Map.of("et", row, "maxAttempts", 3, "error", "MODEL_TIMEOUT"));
        for (String statement : List.of(complete, fail)) {
            assertThat(statement).contains("tenant_id = ?", "id = ?", "version = ?", "version = version + 1",
                    "judge_state = 'judging'", "deleted_at is null", "update_user_id = ?", "update_time = ?")
                    .doesNotContain("select ", "for update", "for share");
        }
        assertThat(complete).contains("judge_state = 'done'", "judge_lease_until = null");
        assertThat(fail).contains("case when judge_attempts >= ?", "then 'failed' else 'pending' end");
    }

    @Test
    void judgeWritesPassTheActualTenantAndLocalRouteGuards() throws Exception {
        var configuration = parse();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(),
                org.mockito.Mockito.mock(javax.sql.DataSource.class)));
        var properties = new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties();
        var sqlGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor(
                () -> "service:judge", properties);
        new org.springframework.context.annotation.CommonAnnotationBeanPostProcessor()
                .postProcessBeforeInitialization(sqlGuard, "sqlGuard");
        var routeGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaLocalWriteGuardInnerInterceptor(
                query -> new top.egon.cola.component.common.mybatis.routing.EgonColaRouteResult(
                        List.of(new top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO(
                                "shard_0", "public", "ddc_ai_usage_feedback_t0")), "a".repeat(64)),
                sqlGuard, org.mockito.Mockito.mock(top.egon.cola.component.common.mybatis.routing.EgonColaTwoLevelRouteStrategy.class),
                Map.of());
        var row = DdcAiUsageFeedbackPO.builder().id(1L).tenantId(42L).version(7L)
                .updateUserId("service:judge").updateTime(NOW).build();
        for (var entry : Map.of("completeJudgeIfOwned", Map.<String, Object>of("et", row, "score", (short) 80, "judgedAt", NOW),
                "failJudgeIfOwned", Map.<String, Object>of("et", row, "maxAttempts", 3, "error", "MODEL_TIMEOUT")).entrySet()) {
            var statement = configuration.getMappedStatement(FEEDBACK + "." + entry.getKey());
            var handler = configuration.newStatementHandler(org.mockito.Mockito.mock(org.apache.ibatis.executor.Executor.class),
                    statement, entry.getValue(), org.apache.ibatis.session.RowBounds.DEFAULT, null,
                    statement.getBoundSql(entry.getValue()));
            org.assertj.core.api.Assertions.assertThatCode(() -> {
                sqlGuard.beforePrepare(handler, null, null);
                routeGuard.beforePrepare(handler, null, null);
            }).as(entry.getKey()).doesNotThrowAnyException();
        }
    }

    @Test
    void judgeWritesCarryTrustedTenantActorAndClaimVersion() {
        MDC.put("userId", "service:judge");
        when(feedbackMapper.completeJudgeIfOwned(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq((short) 80), org.mockito.ArgumentMatchers.eq(NOW))).thenReturn(1);
        assertThat(feedbackRepository.completeJudgeIfOwned(1L, 7L, (short) 80, NOW)).isTrue();
        var captured = org.mockito.ArgumentCaptor.forClass(DdcAiUsageFeedbackPO.class);
        verify(feedbackMapper).completeJudgeIfOwned(captured.capture(), org.mockito.ArgumentMatchers.eq((short) 80),
                org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(captured.getValue().getId()).isEqualTo(1L);
        assertThat(captured.getValue().getTenantId()).isEqualTo(42L);
        assertThat(captured.getValue().getVersion()).isEqualTo(7L);
        assertThat(captured.getValue().getUpdateUserId()).isEqualTo("service:judge");
        assertThat(captured.getValue().getUpdateTime()).isEqualTo(NOW);
        assertThat(feedbackRepository.failJudgeIfOwned(1L, 7L, 3, "MODEL_TIMEOUT", NOW)).isFalse();
        MDC.remove("userId");
        assertThatThrownBy(() -> feedbackRepository.completeJudgeIfOwned(1L, 7L, (short) 80, NOW))
                .hasMessageContaining("AUDIT_USER_REQUIRED");
    }

    @Test
    void pageFiltersTranslateScoreSourceAndMaxScore() throws Exception {
        Configuration configuration = parse();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", 42L);
        parameters.put("offset", 0L);
        parameters.put("limit", 20);
        parameters.put("query", DdcAiUsageFeedbackPageQuery.builder().resourceId(7L).versionNo(4L)
                .scoreSource("JUDGE").maxScore(60).pageNo(1).pageSize(20).build());

        String page = sql(configuration, FEEDBACK + ".selectPage", parameters);
        parameters.put("query", DdcAiOptimizationRunPageQuery.builder().resourceId(7L).pageNo(1).pageSize(20).build());
        String runs = sql(configuration, RUN + ".selectPage", parameters);

        assertThat(page).contains("v.version_no = ?", "f.explicit_score is null and f.judge_score is not null",
                "coalesce(f.explicit_score, f.judge_score) <= ?", "order by f.occurred_at desc, f.id desc");
        assertThat(runs).contains("r.\"trigger\"", "left join ddc_ai_version bv", "left join ddc_ai_version pv",
                "order by r.id desc").doesNotContain("r.state = ?");
    }

    @Test
    void aggregateAvoidsShardingAvgRewriteAndRoundsInJava() throws Exception {
        String aggregateSql = sql(parse(), FEEDBACK + ".selectScoreAggregate", Map.of("tenantId", 42L,
                "resourceId", 7L, "versionId", 9L, "windowStart", NOW.minusSeconds(86400), "windowEnd", NOW));
        assertThat(aggregateSql).doesNotContain("avg(", "filter (").contains("sum(coalesce(f.explicit_score, f.judge_score))");

        when(feedbackMapper.selectScoreAggregate(42L, 7L, 9L, NOW.minusSeconds(86400), NOW))
                .thenReturn(DdcAiScoreAggregatePO.builder().sampleCount(3L).scoreSum(215L).pendingJudgeCount(2L).build());
        DdcAiScoreAggregateBO aggregate = feedbackRepository.aggregate(7L, 9L, NOW.minusSeconds(86400), NOW);

        assertThat(aggregate.getSampleCount()).isEqualTo(3);
        assertThat(aggregate.getAverageScore()).isEqualByComparingTo(new BigDecimal("71.67"));
        assertThat(aggregate.getPendingJudgeCount()).isEqualTo(2);

        when(feedbackMapper.selectScoreAggregate(42L, 7L, 10L, NOW.minusSeconds(86400), NOW))
                .thenReturn(DdcAiScoreAggregatePO.builder().sampleCount(0L).build());
        assertThat(feedbackRepository.aggregate(7L, 10L, NOW.minusSeconds(86400), NOW).getAverageScore()).isNull();
    }

    @Test
    void retentionCountsOnlySuccessfulVersionedDeletesOfStillExpiredRows() {
        DdcAiUsageFeedbackPO first = DdcAiUsageFeedbackPO.builder().id(1L).tenantId(42L).version(7L).occurredAt(NOW.minusSeconds(1)).build();
        DdcAiUsageFeedbackPO stale = DdcAiUsageFeedbackPO.builder().id(2L).tenantId(42L).version(8L).occurredAt(NOW.minusSeconds(1)).build();
        DdcAiUsageFeedbackPO recent = DdcAiUsageFeedbackPO.builder().id(3L).tenantId(42L).version(9L).occurredAt(NOW.plusSeconds(1)).build();
        when(feedbackMapper.selectExpiredIdsForUpdate(42L, NOW, 1000)).thenReturn(List.of(1L, 2L, 3L));
        when(feedbackMapper.selectActiveById(42L, 1L)).thenReturn(first);
        when(feedbackMapper.selectActiveById(42L, 2L)).thenReturn(stale);
        when(feedbackMapper.selectActiveById(42L, 3L)).thenReturn(recent);
        when(feedbackMapper.deleteVersionedById(first)).thenReturn(1);
        when(feedbackMapper.deleteVersionedById(stale)).thenReturn(0);

        assertThat(feedbackRepository.softDeleteExpired(NOW, 1000)).isEqualTo(1);
        verify(feedbackMapper).deleteVersionedById(first);
        verify(feedbackMapper).deleteVersionedById(stale);
        verify(feedbackMapper, never()).deleteVersionedById(recent);
        assertThat(first.getVersion()).isEqualTo(7L);
        assertThat(first.getUpdateUserId()).isEqualTo("system:ai-retention");
        assertThat(first.getUpdateTime()).isNotNull();

        when(feedbackMapper.selectExpiredIdsForUpdate(42L, NOW, 10)).thenReturn(List.of());
        assertThat(feedbackRepository.softDeleteExpired(NOW, 10)).isZero();
        verify(feedbackMapper, never()).deleteVersionedById((DdcAiUsageFeedbackPO) null);
    }

    private Configuration parse() throws Exception {
        Configuration configuration = new Configuration();
        for (String mapper : List.of("DdcAiUsageFeedbackMapper.xml", "DdcAiOptimizationPolicyMapper.xml",
                "DdcAiOptimizationRunMapper.xml")) {
            String resource = "mybatis/mapper/tianshu/" + mapper;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private static String sql(Configuration configuration, String statement, Map<String, Object> parameters) {
        return configuration.getMappedStatement(statement).getBoundSql(parameters).getSql()
                .toLowerCase().replaceAll("\\s+", " ");
    }
}
