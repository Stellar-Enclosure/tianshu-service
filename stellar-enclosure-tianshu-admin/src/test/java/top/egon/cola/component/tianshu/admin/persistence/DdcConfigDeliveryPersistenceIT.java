package top.egon.cola.component.tianshu.admin.persistence;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.annotation.MapperScan;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusAutoConfiguration;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaShardingAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxMybatisPlusAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxStateMachineAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxAutoConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcAdminOutboxDispatchConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.config.DdcConfigDeliveryPersistenceConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigBootBindingMapper;
import top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigProcessReceiptBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigBootBindingConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigDispatchEventConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigDispatchEventConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigItemConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigProcessReceiptConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigVersionConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcOperationLogConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskConverterImpl;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishRequest;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.AcceptanceEnum;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigBootBindingRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigItemRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigProcessReceiptRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigVersionRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcOperationLogRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcPublishTaskRepositoryImpl;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.config.DdcYamlConfigValidator;
import top.egon.cola.component.tianshu.admin.service.notification.DdcConfigDispatchDeliveryHandler;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyResolver;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyVO;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPendingPublishDispatcher;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;
import top.egon.cola.component.tianshu.admin.service.publish.PublishFailureRecorder;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.AcceptanceEnum.ADVANCED;
import static top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT;
import static top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.AcceptanceEnum.SAME;
import static top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.AcceptanceEnum.STALE;

/** Real Spring, ShardingSphere, Egon MP, and PostgreSQL proof for replica-independent delivery state. */
class DdcConfigDeliveryPersistenceIT {
    private static final String DATABASE = "tianshu_step19_delivery";
    private static final String ATOMIC_DATABASE = "tianshu_step27_atomic";
    private static final String PERSISTENCE_YAML = "egon-tianshu-persistence.yml";
    private static final String ENABLED = "TIANSHU_CONFIG_DELIVERY_PERSISTENCE_IT_ENABLED";
    private static final Instant REPORTED = Instant.parse("2026-11-18T18:00:00Z");
    private static final String CONTENT = "a".repeat(64);
    private static final String RESTART = "b".repeat(64);
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(1901L, "tenant-delivery", "service:config-reader");
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path scratch;

    @Test
    void nativeIntakeCommitsVersionIntentTaskAuditAndOutboxOnOnePostgresqlTransaction() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("TIANSHU_CONFIG_NATIVE_PUBLISH_IT_ENABLED")),
                "requires the dedicated disposable native publication database");
        String url = required("TIANSHU_CONFIG_NATIVE_PUBLISH_IT_JDBC_URL");
        String user = required("TIANSHU_CONFIG_NATIVE_PUBLISH_IT_DB_USERNAME");
        String password = required("TIANSHU_CONFIG_NATIVE_PUBLISH_IT_DB_PASSWORD");
        var physical = ownedPrimary(url, user, password, ATOMIC_DATABASE);
        initializeSchema(url, user, password);
        try (var connection = physical.getConnection(); var command = connection.createStatement()) {
            command.execute("""
                    INSERT INTO public.ddc_config_item_t0
                        (id,tenant_id,create_user_id,create_time,update_user_id,update_time,public_id,
                         biz_code,app_code,env,config_key,config_value,value_type,enabled,current_version,created_at,updated_at)
                    VALUES (27001,1901,'fixture',current_timestamp,'fixture',current_timestamp,'native-27001',
                            'reading','atomic','staging','application.yml','app.limit: 1','yaml',true,1,
                            current_timestamp AT TIME ZONE 'UTC',current_timestamp AT TIME ZONE 'UTC')
                    """);
        }
        SnowflakeIdGenerator.initialize(0L, Duration.ofMillis(5));
        var registry = mock(DdcTenantRegistry.class);
        when(registry.requireEnabledTenant(TENANT.tenantId())).thenReturn(DdcTenantBindingDTO.builder()
                .tenantId(TENANT.tenantId()).identityTenantId(TENANT.identityTenantId()).enabled(true).build());
        String adminPropertiesName = "egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties";
        runner(url, user, password, false)
                .withConfiguration(AutoConfigurations.of(TransactionalOutboxAutoConfiguration.class,
                        OutboxStateMachineAutoConfiguration.class, OutboxMybatisPlusAutoConfiguration.class))
                .withUserConfiguration(DdcOutboxConfiguration.class, DdcAdminOutboxDispatchConfiguration.class,
                        DdcConfigItemRepositoryImpl.class, DdcConfigItemConverterImpl.class,
                        DdcConfigVersionRepositoryImpl.class, DdcConfigVersionConverterImpl.class,
                        DdcPublishTaskRepositoryImpl.class, DdcPublishTaskConverterImpl.class,
                        DdcOperationLogRepositoryImpl.class, DdcOperationLogConverterImpl.class,
                        DdcConfigDispatchEventConverterImpl.class, DdcConfigDispatchDeliveryHandler.class,
                        DdcConfigManifestFactory.class, DdcPublishService.class, DdcPendingPublishDispatcher.class,
                        PublishFailureRecorder.class)
                .withBean("jacksonObjectMapper", ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean("ddcTenantRegistry", DdcTenantRegistry.class, () -> registry)
                .withBean(adminPropertiesName, DdcAdminProperties.class, DdcAdminProperties::new)
                .withBean("ddcConfigPublicationPolicyResolver", PublicationPolicyResolver.class,
                        () -> (tenant, scope) -> new PublicationPolicyVO(scope, List.of(),
                                List.of(new TargetBindingVO("atomic-binding", "1", "isolated", "platform-prod",
                                        "Deployment", "tianshu-app-stable", "workload-atomic", "app")),
                                null, Map.of(), Map.of()))
                .withPropertyValues("mybatis-plus.mapper-locations=classpath*:mybatis/mapper/tianshu/**/*.xml,classpath*:mapper/outbox/**/*.xml",
                        "egon.cola.component.transactional-outbox.enabled=true",
                        "egon.cola.component.transactional-outbox.polling.enabled=false",
                        "egon.cola.component.transactional-outbox.cleanup.enabled=false",
                        "egon.cola.component.transactional-outbox.state-machine.execution-timeout=1s",
                        "egon.cola.component.transactional-outbox.node-id=atomic-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var executor = context.getBean(DdcTenantContextExecutor.class);
                    try (var binding = executor.bind(TENANT)) {
                        var producer = context.getBean(DdcPublishService.class);
                        String committed = "270010301";
                        assertThat(producer.publish(request(committed, 1, 2), TENANT.actor()).getStatus()).isEqualTo("PENDING");
                        assertThat(producer.publish(request(committed, 1, 2), TENANT.actor()).getStatus()).isEqualTo("PENDING");

                        var tasks = context.getBean(DdcPublishTaskRepository.class);
                        var failingDispatcher = new DdcPendingPublishDispatcher(
                                context.getBean(DdcConfigDispatchEventConverter.class),
                                message -> { throw new IllegalStateException("ATOMIC_FIXTURE_ENQUEUE_REJECTED"); },
                                context.getBean(DdcConfigRolloutOutboxRouteDTO.class), context.getBean(ValidationUtils.class));
                        var failureCase = new DdcPublishService(context.getBean(DdcConfigItemRepository.class),
                                context.getBean(DdcConfigVersionRepository.class), tasks,
                                context.getBean(DdcOperationLogRepository.class), failingDispatcher,
                                context.getBean(PublishFailureRecorder.class), context.getBean(DdcAdminProperties.class),
                                context.getBean(DdcYamlConfigValidator.class),
                                context.getBean("ddcPublishTransactionTemplate", TransactionTemplate.class),
                                context.getBean("ddcPublishClock", Clock.class), context.getBean(DdcConfigManifestFactory.class),
                                context.getBean(PublicationPolicyResolver.class), registry);
                        assertThatThrownBy(() -> failureCase.publish(request("270010302", 2, 3), TENANT.actor()))
                                .hasMessage("ATOMIC_FIXTURE_ENQUEUE_REJECTED");
                        verifyNativeAtomicRows(physical);
                        verifyNativeTaskFence(tasks, committed);
                    }
                });
    }

    private static void verifyNativeTaskFence(DdcPublishTaskRepository tasks, String changeId) {
        var initial = tasks.findByChangeId(changeId).orElseThrow();
        var now = LocalDateTime.parse("2026-11-18T15:00:00");
        var evidence = JSON.createObjectNode().put("schemaVersion", 1);
        evidence.putArray("targets").addObject().put("bindingId", "atomic-binding");
        assertThat(tasks.updateNativeEvidence(initial, evidence, "FAILED", "KUBERNETES", now)).isEqualTo(1);
        assertThat(tasks.updateNativeEvidence(initial, evidence, "FAILED", "KUBERNETES", now)).isZero();
        var failed = tasks.findByChangeId(changeId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getPlatformEvidence()).isEqualTo(evidence);
        assertThat(tasks.resetNativeForRetry(failed, TENANT.actor(), now.plusSeconds(1))).isEqualTo(1);
        assertThat(tasks.resetNativeForRetry(failed, TENANT.actor(), now.plusSeconds(1))).isZero();
        var retried = tasks.findByChangeId(changeId).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo("PENDING");
        assertThat(retried.getAttemptCount()).isEqualTo(failed.getAttemptCount() + 1);
        assertThat(retried.getPlatformEvidence().path("targets").isEmpty()).isTrue();
    }

    private static DdcPublishRequest request(String changeId, long expectedVersion, int limit) {
        var request = new DdcPublishRequest();
        request.setChangeId(changeId);
        request.setBizCode("reading");
        request.setEnv("staging");
        request.setAppCode("atomic");
        request.setResourceName("application.yml");
        request.setContent("app.limit: " + limit);
        request.setFormat("yaml");
        request.setExpectedVersion(expectedVersion);
        request.setTimeoutMs(5_000L);
        return request;
    }

    private static void verifyNativeAtomicRows(PGSimpleDataSource source) throws SQLException {
        try (var connection = source.getConnection(); var query = connection.createStatement();
             var rows = query.executeQuery("""
                     SELECT i.published_version, i.current_version, i.xmin::text,
                            v.xmin::text, t.xmin::text, a.xmin::text, o.xmin::text,
                            v.manifest_json ->> 'schemaVersion', t.platform_evidence ->> 'schemaVersion',
                            o.channel, o.status
                     FROM public.ddc_config_item_t0 i
                     JOIN public.ddc_config_version_t0 v ON v.tenant_id=i.tenant_id AND v.config_id=i.id
                     JOIN public.ddc_publish_task_t0 t ON t.tenant_id=i.tenant_id AND t.config_id=i.id
                     JOIN public.ddc_operation_log_t0 a ON a.tenant_id=i.tenant_id
                     CROSS JOIN egon_outbox.egon_cola_outbox_message o
                     WHERE i.id=27001
                     """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isEqualTo(2);
            assertThat(rows.getLong(2)).isEqualTo(2);
            String transaction = rows.getString(3);
            for (int column = 4; column <= 7; column++) {
                assertThat(rows.getString(column)).isEqualTo(transaction);
            }
            assertThat(rows.getString(8)).isEqualTo("1");
            assertThat(rows.getString(9)).isEqualTo("1");
            assertThat(rows.getString(10)).isEqualTo("tianshu.config-rollout");
            assertThat(rows.getString(11)).isEqualTo("PENDING");
            assertThat(rows.next()).isFalse();
        }
    }

    @Test
    void independentApplicationContextsShareCommittedBootAuthorityAndCoordinateReceiptReplay() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv(ENABLED)), "requires the dedicated disposable delivery database");
        String url = required("TIANSHU_CONFIG_DELIVERY_PERSISTENCE_IT_JDBC_URL");
        String user = required("TIANSHU_CONFIG_DELIVERY_PERSISTENCE_IT_DB_USERNAME");
        String password = required("TIANSHU_CONFIG_DELIVERY_PERSISTENCE_IT_DB_PASSWORD");
        PGSimpleDataSource physical = ownedPrimary(url, user, password);
        initializeSchema(url, user, password);
        ObjectNode manifest = manifest();
        seedConfigVersion(physical, manifest);
        // This suite tests authority persistence and uses the real facade with one isolated JVM slot.
        SnowflakeIdGenerator.initialize(0L, Duration.ofMillis(5));

        runner(url, user, password).run(first -> {
            assertThat(first).hasNotFailed();
            runner(url, user, password).run(second -> {
                assertThat(second).hasNotFailed();
                DdcConfigBootBindingRepository firstBoots = first.getBean(DdcConfigBootBindingRepository.class);
                DdcConfigBootBindingRepository secondBoots = second.getBean(DdcConfigBootBindingRepository.class);
                DdcConfigProcessReceiptRepository firstReceipts = first.getBean(DdcConfigProcessReceiptRepository.class);
                DdcConfigProcessReceiptRepository secondReceipts = second.getBean(DdcConfigProcessReceiptRepository.class);
                DdcTenantContextExecutor firstTenants = first.getBean(DdcTenantContextExecutor.class);
                DdcTenantContextExecutor secondTenants = second.getBean(DdcTenantContextExecutor.class);
                String pod = UUID.randomUUID().toString();
                DdcConfigBootBindingBO current = bindFromIndependentContexts(firstBoots, firstTenants, secondBoots, secondTenants, pod);
                var technicalMapper = second.getBean(DdcConfigBootBindingMapper.class);
                try (var tenant = MDC.putCloseable("tenantId", "0"); var actor = MDC.putCloseable("userId", TENANT.actor())) {
                    assertThatThrownBy(() -> technicalMapper.findByIncarnation("isolated-19", pod, "application", "containerd://one"))
                            .hasStackTraceContaining("TECHNICAL_TENANT_SCOPE_REQUIRED");
                }

                try (var trusted = firstTenants.bind(TENANT)) {
                    assertThat(secondBoots.findConfirmed(TENANT, boot(pod, "boot-1"))).hasValueSatisfying(row ->
                            assertThat(row.getId()).isEqualTo(current.getId()));
                    var observed = new DdcConfigBootBindingRepository.ObservedIncarnationVO(pod, "application", "containerd://one");
                    assertThat(secondBoots.findCurrentBindings(TENANT, "isolated-19", "runtime", "workload-19", List.of(observed)))
                            .singleElement().satisfies(row -> {
                                assertThat(row.getId()).isEqualTo(current.getId());
                                assertThat(row.getBootId()).isEqualTo("boot-1");
                            });
                    assertThat(secondBoots.findCurrentBindings(TENANT, "isolated-19", "runtime", "wrong-workload", List.of(observed))).isEmpty();
                    assertThat(secondBoots.findCurrentBindings(TENANT, "isolated-19", "another", "workload-19", List.of(observed))).isEmpty();
                    assertThat(secondBoots.findCurrentBindings(TENANT, "isolated-19", "runtime", "workload-19", List.of(
                            new DdcConfigBootBindingRepository.ObservedIncarnationVO(pod, "application", "containerd://previous")))).isEmpty();
                    assertThatThrownBy(() -> secondBoots.findCurrentBindings(TENANT, "isolated-19", "runtime", "workload-19",
                            java.util.Collections.nCopies(501, observed))).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
                    assertThat(MDC.get("tenantId")).isEqualTo(Long.toString(TENANT.tenantId()));
                    assertThat(MDC.get("userId")).isEqualTo(TENANT.actor());
                    assertThatThrownBy(() -> secondBoots.createOrConfirm(TENANT, boot(pod, "another-boot")))
                            .isInstanceOf(DdcConfigBootBindingRepository.BindingConflictException.class);

                    var rejected = receipt(pod, current.getId(), manifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED);
                    var conflicting = receipt(pod, current.getId(), manifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED.plusSeconds(1));
                    var applied = receipt(pod, current.getId(), manifest, 2, DdcConfigReceiptStatusEnum.APPLIED, REPORTED.plusSeconds(2));
                    var regression = receipt(pod, current.getId(), manifest, 3, DdcConfigReceiptStatusEnum.REJECTED, REPORTED.plusSeconds(3));
                    var forgedFence = receipt(pod, current.getId() + 1, manifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED);
                    assertThatThrownBy(() -> firstReceipts.accept(TENANT, forgedFence))
                            .isInstanceOf(DdcConfigBootBindingRepository.BindingConflictException.class);
                    assertThat(firstReceipts.accept(TENANT, rejected)).isEqualTo(ADVANCED);
                    assertThat(secondReceipts.accept(TENANT, rejected)).isEqualTo(SAME);
                    assertThat(secondReceipts.accept(TENANT, conflicting)).isEqualTo(CONFLICT);
                    assertThat(applyAfterExistingRowLock(physical, secondTenants, secondReceipts, applied)).isEqualTo(ADVANCED);
                    assertThat(firstReceipts.accept(TENANT, rejected)).isEqualTo(STALE);
                    assertThat(firstReceipts.accept(TENANT, regression)).isEqualTo(CONFLICT);
                    assertCurrentAndHistoricalReads(firstReceipts, pod);
                    var foreignTenant = new DdcTenantContextDTO(1902L, "tenant-another", "service:another-reader");
                    assertThat(firstTenants.call(foreignTenant, () -> firstReceipts.readHistory(foreignTenant, Instant.EPOCH, 0L, 10)))
                            .isEmpty();
                }
                assertPhysicalRows(physical, pod, current.getId());

                String newPod = UUID.randomUUID().toString();
                var newFence = firstTenants.call(TENANT, () -> firstBoots.createOrConfirm(TENANT, boot(newPod, "boot-1")));
                assertThat(concurrently(
                        () -> firstTenants.call(TENANT, () -> firstReceipts.accept(TENANT,
                                receipt(newPod, newFence.getId(), manifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED))),
                        () -> secondTenants.call(TENANT, () -> secondReceipts.accept(TENANT,
                                receipt(newPod, newFence.getId(), manifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED)))))
                        .containsExactlyInAnyOrder(ADVANCED, SAME);

                var bootTransactions = first.getBean("ddcConfigBootBindingTransactionTemplate", TransactionTemplate.class);
                var receiptTransactions = first.getBean("ddcConfigReceiptTransactionTemplate", TransactionTemplate.class);
                assertAmbiguousCommitRecovery(firstTenants, firstBoots, firstReceipts, bootTransactions, receiptTransactions, manifest);
                assertDesiredPublicationRace(physical, firstTenants, firstBoots, secondTenants, secondReceipts, manifest);
            });
        });
    }

    private static DdcConfigBootBindingBO bindFromIndependentContexts(DdcConfigBootBindingRepository first,
                                                                      DdcTenantContextExecutor firstTenants,
                                                                      DdcConfigBootBindingRepository second,
                                                                      DdcTenantContextExecutor secondTenants,
                                                                      String pod) throws Exception {
        var bindings = concurrently(
                () -> firstTenants.call(TENANT, () -> first.createOrConfirm(TENANT, boot(pod, "boot-1"))),
                () -> secondTenants.call(TENANT, () -> second.createOrConfirm(TENANT, boot(pod, "boot-1"))));
        assertThat(bindings.getFirst().getId()).isPositive().isEqualTo(bindings.getLast().getId());
        return bindings.getFirst();
    }

    private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(() -> {
                start.await();
                return first.call();
            });
            var two = workers.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        }
    }

    private static DdcConfigBootBindingBO boot(String pod, String bootId) {
        return new DdcConfigBootBindingBO().setClusterId("isolated-19").setPodUid(pod).setContainerName("application")
                .setContainerId("containerd://one").setBootId(bootId).setWorkloadUid("workload-19").setK8sNamespace("runtime");
    }

    private static void assertAmbiguousCommitRecovery(DdcTenantContextExecutor tenants, DdcConfigBootBindingRepository boots,
            DdcConfigProcessReceiptRepository receipts, TransactionTemplate bootTransactions,
            TransactionTemplate receiptTransactions, ObjectNode manifest) {
        String pod = UUID.randomUUID().toString();
        try (var trusted = tenants.bind(TENANT)) {
            assertThatThrownBy(() -> withLostCommitReply(bootTransactions, false,
                    () -> boots.createOrConfirm(TENANT, boot(pod, "boot-1"))))
                    .isInstanceOf(DdcConfigBootBindingRepository.BindingUnavailableException.class);
            assertThat(boots.findConfirmed(TENANT, boot(pod, "boot-1"))).isEmpty();

            var fence = withLostCommitReply(bootTransactions, true,
                    () -> boots.createOrConfirm(TENANT, boot(pod, "boot-1")));
            assertThat(boots.findConfirmed(TENANT, boot(pod, "boot-1"))).hasValueSatisfying(row ->
                    assertThat(row.getId()).isEqualTo(fence.getId()));

            var candidate = receipt(pod, fence.getId(), manifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED);
            assertThatThrownBy(() -> withLostCommitReply(receiptTransactions, false, () -> receipts.accept(TENANT, candidate)))
                    .isInstanceOf(DdcConfigProcessReceiptRepository.ReceiptUnavailableException.class);
            assertThat(withLostCommitReply(receiptTransactions, true, () -> receipts.accept(TENANT, candidate))).isEqualTo(SAME);
            assertThat(receipts.accept(TENANT, candidate)).isEqualTo(SAME);
        }
    }

    private static <T> T withLostCommitReply(TransactionTemplate template, boolean committed, Supplier<T> work) {
        var actual = Objects.requireNonNull(template.getTransactionManager());
        var interrupt = new AtomicBoolean(true);
        template.setTransactionManager(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return actual.getTransaction(definition);
            }

            @Override
            public void commit(TransactionStatus status) {
                if (!interrupt.compareAndSet(true, false)) {
                    actual.commit(status);
                    return;
                }
                if (committed) {
                    actual.commit(status);
                } else {
                    actual.rollback(status);
                }
                throw new TransactionSystemException("test commit reply lost", new SQLException("test connection lost", "08006"));
            }

            @Override
            public void rollback(TransactionStatus status) {
                actual.rollback(status);
            }
        });
        try {
            return work.get();
        } finally {
            template.setTransactionManager(actual);
        }
    }

    private static AcceptanceEnum applyAfterExistingRowLock(PGSimpleDataSource physical, DdcTenantContextExecutor tenants,
                                                            DdcConfigProcessReceiptRepository receipts,
                                                            DdcConfigProcessReceiptBO candidate) throws Exception {
        try (Connection blocker = physical.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("""
                    UPDATE public.ddc_config_process_receipt_t0
                    SET last_received_at=last_received_at + INTERVAL '5 minutes'
                    WHERE tenant_id=? AND pod_uid=? RETURNING pg_backend_pid(),last_received_at
                    """)) {
                lock.setLong(1, TENANT.tenantId());
                lock.setString(2, candidate.getPodUid());
                int blockingPid;
                Instant newerObservation;
                try (var rows = lock.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    blockingPid = rows.getInt(1);
                    newerObservation = rows.getObject(2, OffsetDateTime.class).toInstant();
                    assertThat(rows.next()).isFalse();
                }
                var advance = workers.submit(() -> tenants.call(TENANT, () -> receipts.accept(TENANT, candidate)));
                try {
                    assertThat(awaitPostgreSqlBlock(physical, blockingPid)).as("receipt uses its authoritative row lock").isTrue();
                    assertThat(advance.isDone()).isFalse();
                    blocker.commit();
                } catch (Exception | AssertionError failure) {
                    blocker.rollback();
                    throw failure;
                }
                var result = advance.get(10, TimeUnit.SECONDS);
                try (var verify = blocker.prepareStatement("SELECT last_received_at FROM public.ddc_config_process_receipt_t0 "
                        + "WHERE tenant_id=? AND pod_uid=?")) {
                    verify.setLong(1, TENANT.tenantId());
                    verify.setString(2, candidate.getPodUid());
                    try (var rows = verify.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getObject(1, OffsetDateTime.class).toInstant()).isAfterOrEqualTo(newerObservation);
                    }
                }
                return result;
            }
        }
    }

    private static void assertDesiredPublicationRace(PGSimpleDataSource physical,
            DdcTenantContextExecutor firstTenants, DdcConfigBootBindingRepository boots,
            DdcTenantContextExecutor secondTenants, DdcConfigProcessReceiptRepository receipts,
            ObjectNode manifest) throws Exception {
        var nextManifest = manifest.deepCopy();
        ((ObjectNode) nextManifest.get("source")).put("revision", "2");
        insertConfigVersion(physical, 19003L, 2L, nextManifest);
        String pod = UUID.randomUUID().toString();
        var fence = firstTenants.call(TENANT, () -> boots.createOrConfirm(TENANT, boot(pod, "boot-1")));
        var oldDesired = receipt(pod, fence.getId(), manifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED);
        try (var publisher = physical.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            publisher.setAutoCommit(false);
            try (var update = publisher.prepareStatement("UPDATE public.ddc_config_item_t0 SET current_version=2,published_version=2,version=version+1,"
                    + "update_user_id='fixture',update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? RETURNING pg_backend_pid()")) {
                update.setLong(1, TENANT.tenantId());
                update.setLong(2, 19001L);
                int blockingPid;
                try (var rows = update.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    blockingPid = rows.getInt(1);
                    assertThat(rows.next()).isFalse();
                }
                var acceptance = workers.submit(() -> secondTenants.call(TENANT, () -> receipts.accept(TENANT, oldDesired)));
                try {
                    assertThat(awaitPostgreSqlBlock(physical, blockingPid))
                            .as("receipt must wait for the current configuration item lock before writing").isTrue();
                    assertThat(acceptance.isDone()).isFalse();
                    publisher.commit();
                } catch (Exception | AssertionError failure) {
                    publisher.rollback();
                    throw failure;
                }
                assertThat(acceptance.get(10, TimeUnit.SECONDS)).isEqualTo(CONFLICT);
            }
        }
        try (var connection = physical.getConnection(); var check = connection.prepareStatement(
                "SELECT count(*) FROM public.ddc_config_process_receipt_t0 WHERE tenant_id=? AND pod_uid=?")) {
            check.setLong(1, TENANT.tenantId());
            check.setString(2, pod);
            try (var rows = check.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).as("superseded desired revision must not create receipt state").isZero();
            }
        }
        assertThat(secondTenants.call(TENANT, () -> receipts.accept(TENANT, oldDesired))).isEqualTo(CONFLICT);
        var currentDesired = receipt(pod, fence.getId(), nextManifest, 1, DdcConfigReceiptStatusEnum.REJECTED, REPORTED, 2L);
        assertThat(secondTenants.call(TENANT, () -> receipts.accept(TENANT, currentDesired))).isEqualTo(ADVANCED);
    }

    private static boolean awaitPostgreSqlBlock(PGSimpleDataSource physical, int blockingPid) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        try (var observer = physical.getConnection(); var probe = observer.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_stat_activity
                WHERE datname=current_database() AND ? = ANY(pg_catalog.pg_blocking_pids(pid)))
                """)) {
            probe.setInt(1, blockingPid);
            while (System.nanoTime() < deadline) {
                try (var rows = probe.executeQuery()) {
                    if (rows.next() && rows.getBoolean(1)) {
                        return true;
                    }
                }
                Thread.sleep(Duration.ofMillis(30));
            }
        }
        return false;
    }

    private static DdcConfigProcessReceiptBO receipt(String pod, Long fence, ObjectNode manifest, long sequence,
                                                     DdcConfigReceiptStatusEnum status, Instant reported) {
        return receipt(pod, fence, manifest, sequence, status, reported, 1L);
    }

    private static DdcConfigProcessReceiptBO receipt(String pod, Long fence, ObjectNode manifest, long sequence,
                                                     DdcConfigReceiptStatusEnum status, Instant reported, long revision) {
        boolean success = DdcConfigReceiptStatusEnum.APPLIED == status;
        var command = DdcConfigReceiptCommand.builder().schemaVersion("1").bootId("boot-1").releaseRevision(Long.toString(revision))
                .reportSeq(Long.toString(sequence)).contentSha256("sha256:" + CONTENT)
                .baselineRestartDigest("sha256:" + RESTART).status(status).reportedAt(reported)
                .appliedRevision(success ? Long.toString(revision) : null).appliedContentSha256(success ? "sha256:" + CONTENT : null)
                .appliedAt(success ? reported : null).errorCode(success ? null : "HOT_VALUE_REJECTED").build();
        return new DdcConfigProcessReceiptBO().setTenantId(TENANT.tenantId()).setConfigId(19001L).setConfigRevision(revision)
                .setScopeJson(manifest.get("scope").deepCopy()).setTargetBindingId("target-19").setTargetRevision(1L)
                .setClusterId("isolated-19").setK8sNamespace("runtime").setWorkloadUid("workload-19").setPodUid(pod)
                .setContainerName("application").setContainerId("containerd://one").setBootId("boot-1").setBootFence(fence)
                .setAuthorizedManifest(manifest.deepCopy()).setContentDigest(CONTENT).setRestartDigest(RESTART)
                .setAppliedRevision(success ? revision : null).setAppliedDigest(success ? CONTENT : null).setResult(status.getCode())
                .setReasonCode(command.getErrorCode()).setReportSeq(sequence)
                .setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(command).substring("sha256:".length()))
                .setReportedAt(reported).setAppliedAt(success ? reported : null);
    }

    private static void assertPhysicalRows(PGSimpleDataSource physical, String pod, long fence) throws Exception {
        try (var connection = physical.getConnection(); var query = connection.prepareStatement("""
                SELECT b.id,b.tenant_id,r.tenant_id,r.report_seq,r.result,r.create_user_id,r.update_user_id,r.version
                FROM public.ddc_config_boot_binding_t0 b JOIN public.ddc_config_process_receipt_t0 r
                ON b.cluster_id=r.cluster_id AND b.pod_uid=r.pod_uid AND b.container_id=r.container_id AND b.boot_id=r.boot_id
                WHERE b.pod_uid=?
                """)) {
            query.setString(1, pod);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(fence);
                assertThat(rows.getLong(2)).isZero();
                assertThat(rows.getLong(3)).isEqualTo(TENANT.tenantId());
                assertThat(rows.getLong(4)).isEqualTo(2L);
                assertThat(rows.getString(5)).isEqualTo("APPLIED");
                assertThat(rows.getString(6)).isEqualTo(TENANT.actor());
                assertThat(rows.getString(7)).isEqualTo(TENANT.actor());
                assertThat(rows.getLong(8)).isEqualTo(1L);
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void assertCurrentAndHistoricalReads(DdcConfigProcessReceiptRepository receipts, String pod) {
        var target = new DdcConfigProcessReceiptRepository.TargetQueryVO(19001L, 1L, "target-19", 1L,
                "isolated-19", "runtime", "workload-19");
        var current = new DdcConfigProcessReceiptRepository.IncarnationVO(pod, "application", "containerd://one", "boot-1");
        var expired = new DdcConfigProcessReceiptRepository.IncarnationVO(pod, "application", "containerd://one", "other-boot");
        assertThat(receipts.readCurrentByTarget(TENANT, target, List.of(expired), 10)).isEmpty();
        assertThat(receipts.readCurrentByTarget(TENANT, target, List.of(current), 10)).singleElement().satisfies(row -> {
            assertThat(row.getReportSeq()).isEqualTo(2L);
            assertThat(row.getResult()).isEqualTo("APPLIED");
            assertThat(row.getLastReceivedAt()).isAfterOrEqualTo(row.getCreateTime());
        });
        assertThat(receipts.readHistory(TENANT, Instant.EPOCH, 0L, 500)).anySatisfy(row ->
                assertThat(row.getPodUid()).isEqualTo(pod));
    }

    private static ObjectNode manifest() {
        var manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", "1").put("contentSha256", "sha256:" + CONTENT)
                .put("restartDigest", "sha256:" + RESTART).put("enabled", true);
        manifest.putObject("source").put("configId", "19001").put("revision", "1");
        manifest.putObject("scope").put("tenantId", "1901").put("bizCode", "reading").put("env", "prod")
                .put("appCode", "reader").put("namespaceCode", "runtime");
        manifest.putArray("rules");
        manifest.putArray("targetBindings").addObject().put("id", "target-19").put("revision", "1")
                .put("clusterId", "isolated-19").put("namespace", "runtime").put("workloadUid", "workload-19")
                .put("containerName", "application");
        return manifest;
    }

    private static void seedConfigVersion(PGSimpleDataSource physical, ObjectNode manifest) throws Exception {
        try (var connection = physical.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO public.ddc_config_item_t0
                    (id,tenant_id,create_user_id,create_time,update_user_id,update_time,public_id,app_code,env,config_key,
                     value_type,current_version,published_version,created_at,updated_at,biz_code)
                    VALUES (19001,1901,'fixture',CURRENT_TIMESTAMP,'fixture',CURRENT_TIMESTAMP,'item-19001','reader',
                            'prod','application.yml','YAML',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'reading')
                    ON CONFLICT (id) DO NOTHING
                    """);
        }
        insertConfigVersion(physical, 19002L, 1L, manifest);
    }

    private static void insertConfigVersion(PGSimpleDataSource physical, long id, long revision, ObjectNode manifest) throws Exception {
        try (var connection = physical.getConnection(); var insert = connection.prepareStatement("""
                INSERT INTO public.ddc_config_version_t0
                (id,tenant_id,create_user_id,create_time,update_user_id,update_time,public_id,config_id,
                 app_code,env,config_key,config_revision,created_at,manifest_json)
                VALUES (?,1901,'fixture',CURRENT_TIMESTAMP,'fixture',CURRENT_TIMESTAMP,?,
                        19001,'reader','prod','application.yml',?,CURRENT_TIMESTAMP,?::jsonb)
                ON CONFLICT (id) DO NOTHING
                """)) {
            insert.setLong(1, id);
            insert.setString(2, "version-" + id);
            insert.setLong(3, revision);
            insert.setString(4, JSON.writeValueAsString(manifest));
            insert.executeUpdate();
        }
    }

    private ApplicationContextRunner runner(String url, String user, String password) {
        return runner(url, user, password, true);
    }

    private ApplicationContextRunner runner(String url, String user, String password, boolean receiptClock) {
        var runner = new ApplicationContextRunner().withInitializer(context -> {
            try {
                var properties = new YamlPropertySourceLoader().load("tianshu-persistence", new ClassPathResource(PERSISTENCE_YAML));
                properties.forEach(context.getEnvironment().getPropertySources()::addLast);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("TIANSHU_TEST_PERSISTENCE_YAML_REQUIRED", error);
            }
        }).withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class,
                EgonColaMybatisPlusAutoConfiguration.class, EgonColaShardingAutoConfiguration.class,
                MybatisPlusInnerInterceptorAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class))
                .withUserConfiguration(MapperConfiguration.class, DdcConfigDeliveryPersistenceConfiguration.class,
                        DdcTenantContextExecutor.class, DdcConfigBootBindingRepositoryImpl.class,
                        DdcConfigProcessReceiptRepositoryImpl.class, DdcConfigBootBindingConverterImpl.class,
                        DdcConfigProcessReceiptConverterImpl.class)
                .withPropertyValues("TIANSHU_JDBC_URL=" + url, "TIANSHU_DB_USERNAME=" + user, "TIANSHU_DB_PASSWORD=" + password,
                        "mybatis-plus.mapper-locations=classpath*:mybatis/mapper/tianshu/*Mapper.xml");
        return receiptClock ? runner.withBean("ddcPublishClock", Clock.class, Clock::systemUTC) : runner;
    }

    private void initializeSchema(String url, String user, String password) throws Exception {
        var resource = new ClassPathResource(PERSISTENCE_YAML);
        String yaml;
        try (var input = resource.getInputStream()) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        yaml = yaml.replace("\"${TIANSHU_JDBC_URL:${spring.datasource.url}}\"", JSON.writeValueAsString(url))
                .replace("\"${TIANSHU_DB_USERNAME:${spring.datasource.username}}\"", JSON.writeValueAsString(user))
                .replace("\"${TIANSHU_DB_PASSWORD:${spring.datasource.password}}\"", JSON.writeValueAsString(password));
        var configuration = Files.createFile(scratch.resolve("target.yml"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(configuration, yaml);
        DdcPersistenceMigrationCli.main(new String[]{"initialize", "--target-config", configuration.toString()});
    }

    private static PGSimpleDataSource ownedPrimary(String url, String user, String password) throws Exception {
        return ownedPrimary(url, user, password, DATABASE);
    }

    private static PGSimpleDataSource ownedPrimary(String url, String user, String password, String database) throws Exception {
        // Fixed loopback and named database must both agree before schema or test data writes.
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/" + java.util.regex.Pattern.quote(database))) {
            throw new IllegalStateException("TIANSHU_DISPOSABLE_DELIVERY_DATABASE_REQUIRED");
        }
        var physical = new PGSimpleDataSource();
        physical.setURL(url);
        physical.setUser(user);
        physical.setPassword(password);
        physical.setSocketTimeout(15);
        try (Connection connection = physical.getConnection(); var query = connection.createStatement();
             var rows = query.executeQuery("SELECT current_database(), inet_server_addr()::text, NOT pg_is_in_recovery()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(database);
            assertThat(rows.getString(2)).startsWith("127.0.0.1");
            assertThat(rows.getBoolean(3)).isTrue();
        }
        return physical;
    }

    private static String required(String name) {
        var value = Objects.requireNonNull(System.getenv(name), name);
        if (value.isBlank()) {
            throw new IllegalStateException("TIANSHU_DELIVERY_PERSISTENCE_FIXTURE_CONFIGURATION_REQUIRED: " + name);
        }
        return value;
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = DdcConfigBootBindingMapper.class, lazyInitialization = "true")
    static class MapperConfiguration {
    }
}
