package top.egon.cola.component.tianshu.admin.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.annotation.MapperScan;
import org.postgresql.ds.PGSimpleDataSource;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusAutoConfiguration;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaShardingAutoConfiguration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigWorkloadObservationMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigWorkloadObservationPO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigWorkloadObservationConverter;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigWorkloadObservationRepositoryImpl;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.*;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.util.Objects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum.*;

class DdcConfigWorkloadObservationRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(1001L, "tenant-1001", "observer:test");
    private static final TargetBindingVO TARGET = new TargetBindingVO("orders-target", "2", "cluster-a", "apps-prod", "Deployment", "orders", "workload-uid", "app");
    private final DdcConfigWorkloadObservationMapper mapper = mock(DdcConfigWorkloadObservationMapper.class);
    private final EgonColaIdentifierGenerator ids = mock(EgonColaIdentifierGenerator.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final AtomicReference<DdcConfigWorkloadObservationPO> stored = new AtomicReference<>();
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
    private final java.sql.Connection connection = mock(java.sql.Connection.class);
    private final java.sql.Statement statement = mock(java.sql.Statement.class);
    private final java.sql.ResultSet epochs = mock(java.sql.ResultSet.class);
    private DdcConfigWorkloadObservationConverter converter;
    private DdcConfigWorkloadObservationRepository repository;

    @BeforeEach void prepare() throws Exception {
        MDC.put("tenantId", TENANT.tenantId().toString()); MDC.put("userId", TENANT.actor());
        validator.afterPropertiesSet();
        converter = (DdcConfigWorkloadObservationConverter) Class.forName(DdcConfigWorkloadObservationConverter.class.getName() + "Impl").getConstructor().newInstance();
        repository = new DdcConfigWorkloadObservationRepositoryImpl(mapper, converter, new EgonColaMybatisPlusProperties(), ids,
                new DdcTenantContextExecutor(), transactions, new ObjectMapper().findAndRegisterModules(), clock, validator, dataSource);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(transactions.getPropagationBehavior()).thenReturn(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        when(transactions.getIsolationLevel()).thenReturn(TransactionDefinition.ISOLATION_READ_COMMITTED);
        when(transactions.getTimeout()).thenReturn(8);
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        var numbers = new AtomicLong(9000);
        when(ids.nextId(any())).thenAnswer(invocation -> numbers.incrementAndGet());
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT nextval('public.ddc_config_workload_observation_epoch')")).thenReturn(epochs);
        when(epochs.next()).thenReturn(true, false, true, false);
        when(epochs.getLong(1)).thenReturn(10L, 11L);
        when(mapper.selectActive(any())).thenAnswer(invocation -> stored.get());
        when(mapper.selectActiveForUpdate(any())).thenAnswer(invocation -> stored.get());
        when(mapper.insertIfAbsent(any())).thenAnswer(invocation -> stored.compareAndSet(null, copy(invocation.getArgument(0))) ? 1 : 0);
        when(mapper.advance(any(), anyLong(), anyLong(), anyLong())).thenAnswer(invocation -> {
            var current = stored.get();
            var row = copy(invocation.getArgument(0));
            if (current == null || current.getVersion() != (long) invocation.getArgument(1)
                    || current.getViewJson().path("writerEpoch").asLong() != (long) invocation.getArgument(2)
                    || current.getViewJson().path("reportSeq").asLong() != (long) invocation.getArgument(3)) return 0;
            row.setVersion(current.getVersion() + 1); stored.set(row); return 1;
        });
    }

    @AfterEach void close() { validator.close(); MDC.clear(); }
    @Test
    void exposesDurableEpochAndTypedMonotonicObservationBoundary() throws Exception {
        Class<?> repository = Class.forName("top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository");
        assertThat(repository.getMethods()).extracting(java.lang.reflect.Method::getName)
                .contains("nextWriterEpoch", "upsert", "findCurrent");
    }

    @Test void genericMapperOperationsCannotBypassEpochSequenceAndExactTargetPredicates() {
        var guarded = mock(DdcConfigWorkloadObservationMapper.class, CALLS_REAL_METHODS);
        var row = new DdcConfigWorkloadObservationPO();
        assertThatThrownBy(() -> guarded.insert(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guarded.updateById(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guarded.selectActiveById(1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guarded.selectActiveByIds(List.of(1L))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guarded.deleteVersionedById(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guarded.restoreVersionedById(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> guarded.insert(List.of(row), 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void observationSqlUsesNativePgJsonbBindingWithoutRewrittenCastExpressions() throws Exception {
        String xml;
        try (var input = new ClassPathResource("mybatis/mapper/tianshu/DdcConfigWorkloadObservationMapper.xml").getInputStream()) {
            xml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(xml).doesNotContain("CAST(", "AS jsonb");
        var handler = new top.egon.cola.component.tianshu.admin.dao.typehandler.DdcJsonbTypeHandler();
        var prepared = mock(java.sql.PreparedStatement.class);
        var value = new ObjectMapper().createObjectNode().put("writerEpoch", "1");
        handler.setNonNullParameter(prepared, 1, value, org.apache.ibatis.type.JdbcType.OTHER);
        var argument = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(prepared).setObject(eq(1), argument.capture(), eq(java.sql.Types.OTHER));
        assertThat(argument.getValue()).isInstanceOfSatisfying(org.postgresql.util.PGobject.class, pg -> {
            assertThat(pg.getType()).isEqualTo("jsonb");
            assertThat(pg.getValue()).isEqualTo("{\"writerEpoch\":\"1\"}");
        });
    }

    @Test void exactReadKeyDoesNotRequireThePersistedObservationPayload() {
        var key = new DdcConfigWorkloadObservationMapper.TargetKeyVO(1001L, TARGET.getBindingId(), 2,
                TARGET.getClusterId(), TARGET.getKubernetesNamespace(), TARGET.getWorkloadUid());
        assertThat(validator.validate(key)).isEmpty();
        assertThat(top.egon.cola.component.common.mybatis.model.EgonModel.class.isAssignableFrom(key.getClass())).isFalse();
        assertThatThrownBy(() -> new DdcConfigWorkloadObservationMapper.TargetKeyVO(0L, TARGET.getBindingId(), 2,
                TARGET.getClusterId(), TARGET.getKubernetesNamespace(), TARGET.getWorkloadUid())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DdcConfigWorkloadObservationMapper.TargetKeyVO(1001L, TARGET.getBindingId(), 2,
                TARGET.getClusterId(), "../other", TARGET.getWorkloadUid())).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findCurrent(TENANT, TARGET)).isEmpty();
        verify(mapper).selectActive(key);
    }

    @Test void sameObservationTimeCanAdvancePodStateWithinTheSameRolloutGroup() {
        var first = fresh("pod-a", 5, NOW.minusSeconds(1), "cm-a");
        assertThat(repository.upsert(TENANT, TARGET, 10, 1, NOW, first)).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, TARGET, 10, 1, NOW, first)).isEqualTo(SAME);
        assertThat(repository.upsert(TENANT, TARGET, 10, 1, NOW, fresh("pod-b", 5, NOW.minusSeconds(1), "cm-a"))).isEqualTo(CONFLICT);
        assertThat(repository.upsert(TENANT, TARGET, 10, 2, NOW, fresh("pod-b", 5, NOW.minusSeconds(1), "cm-a"))).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, TARGET, 10, 1, NOW, first)).isEqualTo(STALE);
        assertThat(repository.findCurrent(TENANT, TARGET).orElseThrow().currentPods().getFirst().podUid()).isEqualTo("pod-b");
        assertThat(stored.get().getViewJson().path("view").path("observedAt").textValue()).isEqualTo("2026-11-18T11:59:59Z");
    }

    @Test void newestDatabaseEpochFencesOldSourcesIncludingTheirShutdownInvalidation() throws Exception {
        assertThat(repository.nextWriterEpoch(TENANT)).isEqualTo(10);
        assertThat(repository.nextWriterEpoch(TENANT)).isEqualTo(11);
        repository.upsert(TENANT, TARGET, 10, 20, NOW, fresh("old", 5, NOW.minusSeconds(1), "cm-a"));
        assertThat(repository.upsert(TENANT, TARGET, 11, 1, NOW, fresh("new", 5, NOW.minusSeconds(1), "cm-a"))).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, TARGET, 10, 21, NOW, unknown(StatusEnum.CLOSED, NOW))).isEqualTo(CONFLICT);
        assertThat(repository.findCurrent(TENANT, TARGET).orElseThrow().fresh()).isTrue();
        verify(statement, times(2)).setQueryTimeout(8);
        verify(statement, times(2)).close();
        verify(epochs, times(2)).close();
        verify(connection, times(2)).close();
    }

    @Test void disconnectInvalidatesAndRequiresFreshRelistAfterItsPersistedFloor() {
        repository.upsert(TENANT, TARGET, 10, 1, NOW, fresh("pod-a", 5, NOW.minusSeconds(1), "cm-a"));
        assertThat(repository.upsert(TENANT, TARGET, 10, 2, NOW, unknown(StatusEnum.UNAVAILABLE, NOW))).isEqualTo(ADVANCED);
        assertThat(repository.findCurrent(TENANT, TARGET).orElseThrow().fresh()).isFalse();
        assertThat(repository.upsert(TENANT, TARGET, 10, 3, NOW, fresh("pod-a", 5, NOW.minusSeconds(1), "cm-a"))).isEqualTo(STALE);
        now.set(NOW.plusSeconds(2));
        assertThat(repository.upsert(TENANT, TARGET, 10, 4, now.get(), fresh("pod-b", 5, NOW.plusSeconds(1), "cm-a"))).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, TARGET, 10, 5, now.get(), fresh("pod-b", 5, NOW.plusSeconds(1), "cm-other"))).isEqualTo(CONFLICT);
        assertThat(repository.upsert(TENANT, TARGET, 10, 6, now.get(), fresh("pod-b", 4, NOW.plusSeconds(1), "cm-a"))).isEqualTo(CONFLICT);
    }

    @Test void expiredReadIsPureAndCrossTenantOrCorruptPayloadNeverCreatesEvidence() {
        repository.upsert(TENANT, TARGET, 10, 1, NOW, fresh("pod-a", 5, NOW.minusSeconds(1), "cm-a"));
        long version = stored.get().getVersion();
        now.set(NOW.plusSeconds(120));
        assertThat(repository.findCurrent(TENANT, TARGET).orElseThrow().status()).isEqualTo(StatusEnum.STALE);
        assertThat(stored.get().getVersion()).isEqualTo(version);
        clearInvocations(mapper);
        assertThatThrownBy(() -> repository.findCurrent(new DdcTenantContextDTO(2002L, "other", "observer:test"), TARGET)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
        ((com.fasterxml.jackson.databind.node.ObjectNode) stored.get().getViewJson().get("view")).put("desiredReplicas", 99);
        assertThatThrownBy(() -> repository.findCurrent(TENANT, TARGET)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void storeAndSequenceFailureRemainUnavailableWithoutFalseSuccessOrCredentialDetails() throws Exception {
        when(connection.createStatement()).thenThrow(new java.sql.SQLException("sensitive-db"));
        assertThatThrownBy(() -> repository.nextWriterEpoch(TENANT)).isInstanceOf(DdcConfigWorkloadObservationRepository.StoreUnavailableException.class)
                .hasMessage("TIANSHU_CONFIG_OBSERVATION_STORE_UNAVAILABLE").hasNoCause();
        verify(mapper, never()).insertIfAbsent(any());
    }

    @Test void postgresMapperContractPersistsFencesAcrossIndependentSpringShardingContexts() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("true".equals(System.getenv("TIANSHU_OBSERVATION_PERSISTENCE_IT_ENABLED")),
                "requires the explicitly authorized disposable observation PostgreSQL fixture");
        String url = required("TIANSHU_OBSERVATION_PERSISTENCE_IT_JDBC_URL");
        String user = required("TIANSHU_OBSERVATION_PERSISTENCE_IT_DB_USERNAME");
        String password = required("TIANSHU_OBSERVATION_PERSISTENCE_IT_DB_PASSWORD");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianshu_step27_observation")) {
            throw new IllegalStateException("TIANSHU_DISPOSABLE_OBSERVATION_DATABASE_REQUIRED");
        }
        var physical = new PGSimpleDataSource(); physical.setURL(url); physical.setUser(user); physical.setPassword(password); physical.setSocketTimeout(10);
        try (var connection = physical.getConnection(); var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT current_database(),inet_server_addr()::text,NOT pg_is_in_recovery(),current_setting('server_version_num')::int,to_regclass('public.ddc_config_workload_observation_t0'),to_regclass('public.ddc_config_workload_observation_epoch')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("tianshu_step27_observation");
            assertThat(result.getString(2)).startsWith("127.0.0.1");
            assertThat(result.getBoolean(3)).isTrue();
            assertThat(result.getInt(4)).isEqualTo(180003);
            assertThat(result.getObject(5)).isNotNull(); assertThat(result.getObject(6)).isNotNull();
        }
        var target = new TargetBindingVO("fixture-" + UUID.randomUUID(), "2", "cluster-a", "apps-prod", "Deployment", "orders", "workload-uid", "app");
        var epoch = new AtomicLong();
        var rowIds = new AtomicLong(System.currentTimeMillis() * 1000);
        pgRunner(url, user, password, rowIds).run(first -> {
            assertThat(first).hasNotFailed();
            var firstRepo = first.getBean(DdcConfigWorkloadObservationRepository.class);
            var contexts = first.getBean(DdcTenantContextExecutor.class);
            epoch.set(contexts.call(TENANT, () -> firstRepo.nextWriterEpoch(TENANT)));
            var candidate = forTarget(target, fresh("pod-a", 5, NOW.minusSeconds(1), "cm-a"));
            assertThat(contexts.call(TENANT, () -> firstRepo.upsert(TENANT, target, epoch.get(), 1, NOW, candidate))).isEqualTo(ADVANCED);
            assertThat(contexts.call(TENANT, () -> firstRepo.upsert(TENANT, target, epoch.get(), 1, NOW, candidate))).isEqualTo(SAME);
        });
        pgRunner(url, user, password, rowIds).run(second -> {
            assertThat(second).hasNotFailed();
            var secondRepo = second.getBean(DdcConfigWorkloadObservationRepository.class);
            var contexts = second.getBean(DdcTenantContextExecutor.class);
            long next = contexts.call(TENANT, () -> secondRepo.nextWriterEpoch(TENANT));
            assertThat(next).isGreaterThan(epoch.get());
            var newer = forTarget(target, fresh("pod-b", 5, NOW.minusSeconds(1), "cm-a"));
            assertThat(contexts.call(TENANT, () -> secondRepo.upsert(TENANT, target, next, 1, NOW, newer))).isEqualTo(ADVANCED);
            assertThat(contexts.call(TENANT, () -> secondRepo.upsert(TENANT, target, epoch.get(), 99, NOW,
                    forTarget(target, unknown(StatusEnum.CLOSED, NOW))))).isEqualTo(CONFLICT);
            assertThat(contexts.call(TENANT, () -> secondRepo.findCurrent(TENANT, target)).orElseThrow().currentPods().getFirst().podUid()).isEqualTo("pod-b");
            var changedGroup = forTarget(target, fresh("pod-b", 5, NOW.minusSeconds(1), "cm-other"));
            assertThat(contexts.call(TENANT, () -> secondRepo.upsert(TENANT, target, next, 2, NOW, changedGroup))).isEqualTo(CONFLICT);
            assertThat(contexts.call(TENANT, () -> secondRepo.upsert(TENANT, target, next, 2, NOW,
                    forTarget(target, unknown(StatusEnum.UNAVAILABLE, NOW))))).isEqualTo(ADVANCED);
            assertThat(contexts.call(TENANT, () -> secondRepo.findCurrent(TENANT, target)).orElseThrow().fresh()).isFalse();
            assertThat(contexts.call(TENANT, () -> secondRepo.upsert(TENANT, target, next, 3, NOW, newer))).isEqualTo(STALE);
        });
        try (var connection = physical.getConnection(); var query = connection.prepareStatement(
                "SELECT count(*),max(version) FROM public.ddc_config_workload_observation_t0 WHERE tenant_id=? AND target_binding_id=?")) {
            query.setLong(1, TENANT.tenantId()); query.setString(2, target.getBindingId());
            try (var result = query.executeQuery()) {
                assertThat(result.next()).isTrue(); assertThat(result.getLong(1)).isEqualTo(1); assertThat(result.getLong(2)).isEqualTo(2);
            }
        }
    }

    private ApplicationContextRunner pgRunner(String url, String user, String password, AtomicLong rowIds) {
        return new ApplicationContextRunner().withInitializer(context -> {
            try {
                new YamlPropertySourceLoader().load("tianshu-persistence", new ClassPathResource("egon-tianshu-persistence.yml"))
                        .forEach(context.getEnvironment().getPropertySources()::addLast);
            } catch (java.io.IOException invalid) { throw new IllegalStateException("OBSERVATION_FIXTURE_YAML_REQUIRED"); }
        }).withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class, EgonColaMybatisPlusAutoConfiguration.class,
                EgonColaShardingAutoConfiguration.class, MybatisPlusInnerInterceptorAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class))
                .withUserConfiguration(ObservationMapperConfiguration.class, DdcTenantContextExecutor.class,
                        DdcConfigWorkloadObservationRepositoryImpl.class)
                .withBean("ddcConfigWorkloadObservationConverterImpl", DdcConfigWorkloadObservationConverter.class, () -> converter)
                .withBean("jacksonObjectMapper", ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean("ddcPublishClock", Clock.class, () -> clock)
                .withBean("egonColaIdentifierGenerator", EgonColaIdentifierGenerator.class, () -> {
                    var generator = mock(EgonColaIdentifierGenerator.class);
                    when(generator.nextId(any())).thenAnswer(invocation -> rowIds.incrementAndGet()); return generator;
                })
                .withPropertyValues("TIANSHU_JDBC_URL=" + url, "TIANSHU_DB_USERNAME=" + user, "TIANSHU_DB_PASSWORD=" + password,
                        "mybatis-plus.mapper-locations=" + String.join(",", "classpath*:mybatis/mapper/tianshu/DdcConfigWorkloadObservationMapper.xml",
                                "classpath*:mybatis/mapper/tianshu/DdcConfigBootBindingMapper.xml",
                                "classpath*:mybatis/mapper/tianshu/DdcConfigProcessReceiptMapper.xml",
                                "classpath*:mybatis/mapper/tianshu/DdcAiResourceMapper.xml", "classpath*:mybatis/mapper/tianshu/DdcAiOptimizationRunMapper.xml"));
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = DdcConfigWorkloadObservationMapper.class, lazyInitialization = "true")
    static class ObservationMapperConfiguration {
        @org.springframework.context.annotation.Bean("ddcConfigWorkloadObservationTransactionTemplate")
        TransactionTemplate observationTransactions(@org.springframework.beans.factory.annotation.Qualifier("transactionManager") PlatformTransactionManager manager) {
            var template = new TransactionTemplate(manager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); template.setTimeout(8); return template;
        }
    }

    private static String required(String name) { return Objects.requireNonNull(System.getenv(name), name); }
    private static WorkloadViewVO forTarget(TargetBindingVO target, WorkloadViewVO view) {
        return new WorkloadViewVO(target, view.status(), view.reasonCode(), view.observedAt(), view.validUntil(), view.invalidatedAt(),
                view.workloadUid(), view.workloadGeneration(), view.observedGeneration(), view.desiredReplicas(), view.currentPods(),
                view.currentIncarnations(), view.templateAnnotations(), view.configMapReferences());
    }

    private DdcConfigWorkloadObservationPO copy(DdcConfigWorkloadObservationPO value) { return converter.toSource(converter.toTarget(value)); }
    private static WorkloadViewVO fresh(String pod, long generation, Instant observed, String cm) {
        return new WorkloadViewVO(TARGET, StatusEnum.FRESH, null, observed, observed.plusSeconds(60), null, "workload-uid", generation, generation, 1,
                List.of(new PodObservationVO(pod, "orders-1", "Running", true, "app", "containerd://" + pod, 0, observed)),
                List.of(new ContainerIncarnationVO(pod, "app", "containerd://" + pod)),
                Map.of("stellar.io/config-restart-digest", "sha256:" + "b".repeat(64)), Map.of("tianshu-baseline", cm));
    }
    private static WorkloadViewVO unknown(StatusEnum status, Instant invalidated) {
        return new WorkloadViewVO(TARGET, status, status.name(), null, null, invalidated, "workload-uid", 0, 0, 0, List.of(), List.of(), Map.of(), Map.of());
    }
}
