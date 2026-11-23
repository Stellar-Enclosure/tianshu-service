package top.egon.cola.component.tianshu.admin.integration;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.annotation.MapperScan;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusAutoConfiguration;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaShardingAutoConfiguration;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.operationlog.EgonOperationLogAutoConfiguration;
import top.egon.cola.component.common.mybatis.operationlog.EgonOperationLogProperties;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogContextDTO;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogEvent;
import top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO;
import top.egon.cola.component.common.mybatis.routing.EgonColaRouteQuery;
import top.egon.cola.component.common.mybatis.routing.EgonColaRoutingProfileBO;
import top.egon.cola.component.common.mybatis.routing.EgonColaTwoLevelRouteStrategy;
import top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties;
import top.egon.cola.component.common.mybatis.sharding.bootstrap.EgonColaPhysicalDataSourceFactory;
import top.egon.cola.component.common.mybatis.sharding.bootstrap.EgonColaShardingDataSourceBootstrapper;
import top.egon.cola.component.outbox.autoconfigure.OutboxMybatisPlusAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxStateMachineAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.outbox.dispatch.OutboxDispatcher;
import top.egon.cola.component.outbox.dispatch.OutboxPoller;
import top.egon.cola.component.outbox.event.OutboxCommittedEvent;
import top.egon.cola.component.outbox.event.OutboxCommittedEventListener;
import top.egon.cola.component.tianshu.admin.config.DdcAdminOutboxDispatchConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.config.DdcConfigDeliveryPersistenceConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigBootBindingMapper;
import top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigDispatchEventConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigItemConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigVersionConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcOperationLogDetailViewConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcOperationLogSummaryViewConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskConverterImpl;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishRequest;
import top.egon.cola.component.tianshu.admin.model.query.DdcOperationLogPageQuery;
import top.egon.cola.component.tianshu.admin.outbox.DdcOperationLogOutboxAdapter;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigItemRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigVersionRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcPublishTaskRepositoryImpl;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.audit.DdcOperationLogQueryService;
import top.egon.cola.component.tianshu.admin.service.notification.DdcConfigDispatchDeliveryHandler;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPendingPublishDispatcher;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;
import top.egon.cola.component.tianshu.admin.service.publish.PublishFailureRecorder;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One real Spring/MP/Outbox runtime; only named commit/connection fault seams decorate real JDBC resources. */
@EnabledIfEnvironmentVariable(named = "TIANSHU_OPERATION_LOG_IT_ENABLED", matches = "true")
class DdcOperationLogCutoverIT {
    private static final String MP = "egon.cola.component.mybatis-plus.";
    private static final String AUDIT = MP + "operation-log.";
    private static final String OUTBOX = "egon.cola.component.transactional-outbox.";
    private static final String TENANT_PROPERTIES = "egon.cola.component.tianshu.admin.tenancy-top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path scratch;

    @Test
    void physicalAuditAndFailureRecovery() throws Exception {
        verifyReplicationGroups();
        verifyRuntimePrivileges();
        // The current source YAML/history is paired with the pre-coordination Outbox artifact, not silently widened.
        try (var manifest = new ClassPathResource("db/egon-outbox-mp/manifest.json").getInputStream()) {
            assertThat(new String(manifest.readAllBytes(), StandardCharsets.UTF_8)).doesNotContain("20261122_001");
        }
        var ownedIds = new ArrayList<AcceptedEventVO>();
        try (var source = sourceDatabase()) {
            long sourceSystem = count(source.physical(), "SELECT system_identifier FROM pg_control_system()");
            for (int group = 0; group < 2; group++) {
                assertThat(sourceSystem).as("business source is a different physical PostgreSQL cluster")
                        .isNotEqualTo(count(audit(group, false), "SELECT system_identifier FROM pg_control_system()"));
            }
            initializeSource(source);
            SnowflakeIdGenerator.initialize(0L, Duration.ofMillis(5));
            long tenantBase = Math.max(100_000L, System.nanoTime() / 1000);
            var tenants = new DdcTenantAdminProperties().setBindings(java.util.stream.LongStream.range(tenantBase, tenantBase + 64)
                    .mapToObj(id -> new DdcTenantBindingDTO(id, "cutover-" + id, true)).toList());
            var faults = new FaultControls();
            try {
                runner(source, tenants, faults).run(context -> {
                    assertThat(context).hasNotFailed();
                    try { runRuntime(context, source, ownedIds, tenantBase, faults); }
                    catch (Exception failure) { throw new IllegalStateException("OPERATION_LOG_CUTOVER_RUNTIME_FAILED", failure); }
                    finally { faults.replicaConnectionFailure.set(false); }
                });
            } finally {
                if (source.createdHere()) cleanOwnTargetRows(ownedIds);
            }
        }
    }

    private void runRuntime(ApplicationContext context, SourceDatabaseVO source, List<AcceptedEventVO> events,
            long tenantBase, FaultControls faults) throws Exception {
        var bootstrap = context.getBean("operationLogTargetBootstrapper", EgonColaShardingDataSourceBootstrapper.class);
        var profile = bootstrap.profiles().get("egon_cola_operation_log");
        var routes = context.getBean(EgonColaTwoLevelRouteStrategy.class);
        var contexts = context.getBean(DdcTenantContextExecutor.class);
        var queries = context.getBean(DdcOperationLogQueryService.class);
        var dispatcher = context.getBean(OutboxDispatcher.class);
        var producer = context.getBean(DdcPublishService.class);
        long initialAuditTasks = auditMessageCount(source);
        long initialOrdinaryRows = count(source.physical(), "SELECT count(*) FROM public.ddc_operation_log_t0");
        long tenantA = tenantFor(routes, profile, tenantBase, "tianshu_audit_0");
        long tenantB = tenantFor(routes, profile, tenantBase, "tianshu_audit_1");
        for (long tenant : List.of(tenantA, tenantB)) {
            try (var binding = contexts.bind(trusted(tenant))) {
                assertThat(queries.page(new DdcOperationLogPageQuery()).getRecords()).as("fresh authorized tenant starts empty").isEmpty();
            }
            for (int bucket = 0; bucket < 2; bucket++) {
                long id = idFor(routes, profile, tenant, bucket, Math.max(100_000L, System.nanoTime()));
                events.add(publish(context, source, producer, tenant, id, routes, profile));
            }
        }
        assertThat(events).extracting(event -> event.target().group() + '/' + event.target().table()).doesNotHaveDuplicates().hasSize(4);
        assertThat(auditMessageCount(source)).isEqualTo(initialAuditTasks + 4);
        for (var event : events) assertThat(sourceStatus(source, event.id())).isEqualTo("PENDING");
        var capture = context.getBean("egonOperationLogProperties", EgonOperationLogProperties.class);
        capture.setEnabled(false);
        long disabledId = Math.max(100_000L, System.nanoTime());
        publishWithoutAudit(context, source, producer, tenantA, disabledId);
        assertThat(auditMessageCount(source)).as("capture stop permits the original business command but creates no audit task").isEqualTo(initialAuditTasks + 4);
        dispatcher.submitDue();
        await("existing tasks drain while capture is disabled", () -> events.stream().allMatch(event -> succeeded(source, event.id())));
        for (var event : events) verifyAccepted(event, contexts, queries);
        try (var binding = contexts.bind(trusted(tenantA))) {
            var first = queries.page(new DdcOperationLogPageQuery().setLimit(1));
            assertThat(first.getNextCursor()).isNotNull();
            var second = queries.page(new DdcOperationLogPageQuery().setLimit(1).setFrom(first.getFrom().toString())
                    .setTo(first.getTo().toString()).setBeforeTime(first.getNextCursor().beforeTime().toString())
                    .setBeforeId(first.getNextCursor().beforeId()));
            assertThat(second.getRecords()).hasSize(1);
            assertThat(second.getNextCursor()).isNull();
            assertThat(first.getRecords().getFirst().getEventId()).isNotEqualTo(second.getRecords().getFirst().getEventId());
            var foreign = events.stream().filter(event -> event.tenant() == tenantB).findFirst().orElseThrow();
            assertThatThrownBy(() -> queries.detail(Long.toString(foreign.id()), foreign.event().getOccurredAt().toString()))
                    .isInstanceOfSatisfying(CommonException.class, failure -> assertThat(failure.getCode()).isEqualTo(404000));
        }
        assertThat(count(source.physical(), "SELECT count(*) FROM public.ddc_operation_log_t0")).as("ordinary historical rows remain unchanged, including a retained rollback fixture").isEqualTo(initialOrdinaryRows);
        capture.setEnabled(true);

        long failId = idFor(routes, profile, tenantA, 0, System.nanoTime());
        var failed = publish(context, source, producer, tenantA, failId, routes, profile); events.add(failed);
        long healthyId = idFor(routes, profile, tenantB, 1, failId + 100);
        var healthy = publish(context, source, producer, tenantB, healthyId, routes, profile); events.add(healthy);
        String constraint = "cutover_reject_" + Long.toHexString(failId);
        String fact = table(failed.target().table());
        execute(primary(failed), "ALTER TABLE public." + fact + " ADD CONSTRAINT " + constraint + " CHECK (id <> " + failId + ") NOT VALID");
        try {
            dispatcher.submitDue();
            await("failed target returns retry while another group accepts", () -> "RETRY_WAIT".equals(sourceStatus(source, failId)) && succeeded(source, healthyId));
            assertThat(physicalCount(failed, false, false)).isZero();
            assertThat(physicalCount(failed, false, true)).as("fact error rolls the target receipt back").isZero();
            assertThat(physicalCount(healthy, false, false)).isEqualTo(1);
        } finally { execute(primary(failed), "ALTER TABLE public." + fact + " DROP CONSTRAINT " + constraint); }
        awaitDispatch(dispatcher, source, failId);
        verifyAccepted(failed, contexts, queries);

        long uncertainId = idFor(routes, profile, tenantA, 1, healthyId + 100);
        var uncertain = publish(context, source, producer, tenantA, uncertainId, routes, profile); events.add(uncertain);
        faults.targetCommitReplyLost.set(true);
        dispatcher.submitDue();
        await("target committed but its reply is unknown", () -> "RETRY_WAIT".equals(sourceStatus(source, uncertainId)));
        assertThat(faults.targetCommitFaults.get()).isEqualTo(1);
        assertThat(physicalCount(uncertain, false, false)).isEqualTo(1);
        assertThat(physicalCount(uncertain, false, true)).isEqualTo(1);
        var worker = context.getBean("outboxWorkerTransactionTemplate", TransactionTemplate.class);
        PlatformTransactionManager actual = worker.getTransactionManager();
        AtomicBoolean ackArmed = new AtomicBoolean(true);
        AtomicInteger ackFaults = new AtomicInteger();
        worker.setTransactionManager(lostSourceAck(actual, context.getBean("dataSource", DataSource.class), uncertainId, ackArmed, ackFaults));
        try {
            await("SAME receipt followed by source ACK rollback", () -> { dispatcher.submitDue(); return ackFaults.get() == 1; });
            assertThat(sourceStatus(source, uncertainId)).isEqualTo("PROCESSING");
            assertThat(physicalCount(uncertain, false, false)).isEqualTo(1);
            assertThat(physicalCount(uncertain, false, true)).isEqualTo(1);
        } finally { worker.setTransactionManager(actual); }
        // The actual poller, rather than a direct Store state update, recovers the expired original lease.
        var props = context.getBean(TransactionalOutboxProperties.class);
        var poller = context.getBean(OutboxPoller.class);
        poller.stop(); props.getPolling().setEnabled(true); poller.start();
        await("actual poller recovers the original ID after ACK uncertainty", () -> succeeded(source, uncertainId));
        verifyAccepted(uncertain, contexts, queries);
        assertThat(physicalCount(uncertain, false, false)).isEqualTo(1);
        assertThat(physicalCount(uncertain, false, true)).isEqualTo(1);

        verifyReplicaLag(context, source, producer, contexts, queries, routes, profile, tenantA, events);
        faults.replicaConnectionFailure.set(true);
        try {
            try (var binding = contexts.bind(trusted(tenantA))) {
                assertThatThrownBy(() -> queries.page(new DdcOperationLogPageQuery()))
                        .isInstanceOfSatisfying(CommonException.class, failure -> assertThat(failure.getCode()).isEqualTo(56998));
            }
            try (var binding = contexts.bind(trusted(tenantB))) {
                assertThat(queries.page(new DdcOperationLogPageQuery()).getRecords()).isNotEmpty();
            }
            assertThat(faults.replicaConnectionFaults.get()).as("the real physical replica factory callback was hit").isGreaterThanOrEqualTo(1);
            assertThat(faults.primaryConnectionsDuringReplicaFault.get()).as("failed replica reads never fall back to that group's primary").isZero();
        } finally { faults.replicaConnectionFailure.set(false); }
        verifyCrossMonthConflict(context, uncertain);
        verifyLegalNextMonth(context, routes, profile, contexts, queries, events);
        var metrics = context.getBean(io.micrometer.core.instrument.MeterRegistry.class);
        assertThat(metrics.get("tianshu.operation.log.audit.accepted").counter().count()).isGreaterThanOrEqualTo(8);
        assertThat(metrics.get("tianshu.operation.log.duplicate.same").counter().count()).isGreaterThanOrEqualTo(2);
        assertThat(metrics.get("tianshu.operation.log.duplicate.conflict").counter().count()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.get("tianshu.operation.log.query.unavailable").counter().count()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.getMeters().stream().filter(meter -> meter.getId().getName().startsWith("tianshu.operation.log.")))
                .allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
        poller.stop();
    }

    private static void verifyReplicaLag(ApplicationContext context, SourceDatabaseVO source, DdcPublishService producer,
            DdcTenantContextExecutor contexts, DdcOperationLogQueryService queries, EgonColaTwoLevelRouteStrategy routes,
            EgonColaRoutingProfileBO profile, long tenant, List<AcceptedEventVO> events) throws Exception {
        PGSimpleDataSource replica = audit(0, true);
        assertThat(count(replica, "SELECT CASE WHEN pg_is_wal_replay_paused() THEN 1 ELSE 0 END")).isZero();
        execute(replica, "SELECT pg_wal_replay_pause()");
        AcceptedEventVO delayed;
        try {
            await("replica replay actually reaches paused state", () -> {
                try { return count(replica, "SELECT CASE WHEN pg_get_wal_replay_pause_state()='paused' THEN 1 ELSE 0 END") == 1; }
                catch (SQLException failure) { throw new IllegalStateException(failure); }
            });
            long id = idFor(routes, profile, tenant, 0, System.nanoTime());
            delayed = publish(context, source, producer, tenant, id, routes, profile); events.add(delayed);
            await("target commit continues during replica replay pause", () -> succeeded(source, id));
            assertThat(physicalCount(delayed, false, false)).isEqualTo(1);
            assertThat(physicalCount(delayed, true, false)).as("paused replica is stale, not failed").isZero();
            try (var binding = contexts.bind(trusted(tenant))) {
                assertThat(queries.page(new DdcOperationLogPageQuery()).getRecords()).extracting(record -> record.getEventId())
                        .doesNotContain(Long.toString(id));
            }
        } finally { execute(replica, "SELECT pg_wal_replay_resume()"); }
        verifyAccepted(delayed, contexts, queries);
    }

    private static void verifyCrossMonthConflict(ApplicationContext context, AcceptedEventVO accepted) throws Exception {
        var changed = accepted.event().toBuilder().occurredAt(accepted.event().getOccurredAt().minus(Duration.ofDays(32))).build();
        var handler = context.getBean("ddcOperationLogDeliveryHandler", DeliveryHandler.class);
        var envelope = new DeliveryContext(Long.toString(accepted.id()), DdcOperationLogOutboxAdapter.CHANNEL,
                DdcOperationLogOutboxAdapter.DESTINATION, JSON.writeValueAsString(changed), "application/json", "1",
                Map.of("eventId", Long.toString(accepted.id()), "tenantId", Long.toString(accepted.tenant()), "sourceService", "tianshu-admin"),
                null, 2, 12, Instant.now().plusSeconds(12));
        assertThat(handler.deliver(envelope).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        assertThat(physicalCount(accepted, false, false)).isEqualTo(1);
        assertThat(physicalCount(accepted, false, true)).isEqualTo(1);
    }

    private static void verifyLegalNextMonth(ApplicationContext context, EgonColaTwoLevelRouteStrategy routes,
            EgonColaRoutingProfileBO profile, DdcTenantContextExecutor contexts, DdcOperationLogQueryService queries,
            List<AcceptedEventVO> events) throws Exception {
        Instant nextMonth = java.time.YearMonth.now(java.time.ZoneOffset.UTC).plusMonths(1).atDay(1)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        String suffix = java.time.format.DateTimeFormatter.ofPattern("yyyyMM").withZone(java.time.ZoneOffset.UTC).format(nextMonth);
        var handler = context.getBean("ddcOperationLogDeliveryHandler", DeliveryHandler.class);
        long candidate = events.stream().mapToLong(AcceptedEventVO::id).max().orElseThrow() + 100_000;
        var combinations = List.copyOf(events.subList(0, 4));
        for (var original : combinations) {
            int bucket = Integer.parseInt(original.target().table().substring(original.target().table().length() - 1));
            long id = idFor(routes, profile, original.tenant(), bucket, candidate); candidate = id + 100;
            var fact = original.event().toBuilder().eventId(id).occurredAt(nextMonth.plusNanos(1_000)).build();
            var envelope = new DeliveryContext(Long.toString(id), DdcOperationLogOutboxAdapter.CHANNEL,
                    DdcOperationLogOutboxAdapter.DESTINATION, JSON.writeValueAsString(fact), "application/json", "1",
                    Map.of("eventId", Long.toString(id), "tenantId", Long.toString(original.tenant()), "sourceService", "tianshu-admin"),
                    null, 1, 12, Instant.now().plusSeconds(12));
            assertThat(handler.deliver(envelope).kind()).isEqualTo(DeliveryResult.Kind.SUCCESS);
            var accepted = new AcceptedEventVO(original.tenant(), id, fact, node(routes, profile, original.tenant(), id));
            events.add(accepted);
            assertThat(physicalCount(accepted, false, false)).isEqualTo(1);
            await("next-month legal event streams to replica", () -> physicalCount(accepted, true, false) == 1);
            try (var connection = primary(accepted).getConnection(); var statement = connection.prepareStatement(
                    "SELECT tableoid::regclass::text FROM public." + table(accepted.target().table()) + " WHERE tenant_id=? AND id=?")) {
                statement.setLong(1, accepted.tenant()); statement.setLong(2, id);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).endsWith(accepted.target().table() + "_" + suffix);
                }
            }
            try (var binding = contexts.bind(trusted(accepted.tenant()))) {
                assertThat(queries.detail(Long.toString(id), fact.getOccurredAt().toString()).getOccurredAt()).isEqualTo(fact.getOccurredAt());
                assertThat(queries.page(new DdcOperationLogPageQuery().setFrom(nextMonth.toString()).setTo(nextMonth.plus(Duration.ofDays(1)).toString()))
                        .getRecords()).extracting(record -> record.getEventId()).contains(Long.toString(id));
            }
        }
    }

    private static AcceptedEventVO publish(ApplicationContext context, SourceDatabaseVO source, DdcPublishService producer,
            long tenant, long id, EgonColaTwoLevelRouteStrategy routes, EgonColaRoutingProfileBO profile) throws Exception {
        publishWithoutAudit(context, source, producer, tenant, id);
        try (var connection = source.physical().getConnection(); var query = connection.prepareStatement(
                "SELECT payload,i.xmin::text,o.xmin::text FROM egon_outbox.egon_cola_outbox_message o "
                + "JOIN public.ddc_config_item_t0 i ON i.app_code=? AND i.tenant_id=? WHERE o.message_id=? AND o.channel='tianshu.operation-log'")) {
            query.setString(1, "it_" + id); query.setLong(2, tenant); query.setString(3, Long.toString(id));
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(2)).as("business write and persisted audit task use the same source transaction").isEqualTo(rows.getString(3));
                OperationLogEvent event = JSON.readValue(rows.getString(1), OperationLogEvent.class);
                assertThat(event.getEventId()).isEqualTo(id);
                assertThat(event.getOccurredAt().getNano() % 1000).isZero();
                assertThat(rows.next()).isFalse();
                return new AcceptedEventVO(tenant, id, event, node(routes, profile, tenant, id));
            }
        }
    }

    private static void publishWithoutAudit(ApplicationContext context, SourceDatabaseVO source, DdcPublishService producer, long tenant, long id) throws Exception {
        try (var connection = source.physical().getConnection(); var insert = connection.prepareStatement("""
                INSERT INTO public.ddc_config_item_t0
                (id,tenant_id,create_user_id,create_time,update_user_id,update_time,public_id,biz_code,app_code,env,
                 config_key,config_value,value_type,enabled,current_version,created_at,updated_at)
                VALUES (?,?,'fixture',current_timestamp,'fixture',current_timestamp,?,'cutover',?,'isolated',
                        'application.yml','app.limit: 1','yaml',true,1,current_timestamp AT TIME ZONE 'UTC',current_timestamp AT TIME ZONE 'UTC')
                """)) {
            insert.setLong(1, id); insert.setLong(2, tenant); insert.setString(3, "cutover-" + id); insert.setString(4, "it_" + id); insert.executeUpdate();
        }
        context.getBean("cutoverEventIds", AtomicLong.class).set(id);
        var request = new DdcPublishRequest();
        request.setChangeId(Long.toString(id)); request.setBizCode("cutover"); request.setEnv("isolated"); request.setAppCode("it_" + id);
        request.setResourceName("application.yml"); request.setContent("app.limit: 2"); request.setFormat("yaml");
        request.setExpectedVersion(1L); request.setTimeoutMs(5000L);
        try (var binding = context.getBean(DdcTenantContextExecutor.class).bind(trusted(tenant))) {
            assertThat(producer.publish(request, trusted(tenant).actor()).getStatus()).isEqualTo("PENDING");
        }
    }

    private static void verifyAccepted(AcceptedEventVO event, DdcTenantContextExecutor contexts, DdcOperationLogQueryService queries) throws Exception {
        assertThat(physicalCount(event, false, false)).isEqualTo(1);
        assertThat(physicalCount(event, false, true)).isEqualTo(1);
        await("matching fact replays to actual streaming replica", () -> physicalCount(event, true, false) == 1);
        try (var binding = contexts.bind(trusted(event.tenant()))) {
            var detail = queries.detail(Long.toString(event.id()), event.event().getOccurredAt().toString());
            assertThat(detail.getOccurredAt()).isEqualTo(event.event().getOccurredAt());
            assertThat(detail.getEventId()).isEqualTo(Long.toString(event.id()));
            assertThat(detail.getDetails().path("changeId").textValue()).isEqualTo(Long.toString(event.id()));
            assertThat(queries.page(new DdcOperationLogPageQuery()).getRecords()).extracting(record -> record.getEventId())
                    .contains(Long.toString(event.id()));
        }
    }

    private ApplicationContextRunner runner(SourceDatabaseVO source, DdcTenantAdminProperties tenants, FaultControls faults) {
        String runtimeUser = System.getenv("TIANSHU_OPERATION_LOG_IT_RUNTIME_USER");
        boolean restrictedRuntime = runtimeUser != null && !runtimeUser.isBlank();
        String targetUser = restrictedRuntime ? runtimeUser : required("EGON_MP_PG_USER");
        String targetPassword = restrictedRuntime ? required("TIANSHU_OPERATION_LOG_IT_RUNTIME_PASSWORD") : required("EGON_MP_PG_PASSWORD");
        String suppliedRuntimePassword = System.getenv("TIANSHU_OPERATION_LOG_IT_RUNTIME_PASSWORD");
        if (!restrictedRuntime && suppliedRuntimePassword != null && !suppliedRuntimePassword.isBlank()) {
            throw new IllegalStateException("CUTOVER_RUNTIME_CREDENTIAL_PAIR_REQUIRED");
        }
        var properties = new ArrayList<>(List.of("TIANSHU_JDBC_URL=" + source.url(), "TIANSHU_DB_USERNAME=" + source.user(),
                "TIANSHU_DB_PASSWORD=" + source.password(), "mybatis-plus.mapper-locations=classpath*:mybatis/mapper/tianshu/**/*.xml,classpath*:mapper/outbox/**/*.xml",
                OUTBOX + "enabled=true", OUTBOX + "polling.enabled=false", OUTBOX + "cleanup.enabled=false", OUTBOX + "node-id=cutover-it",
                OUTBOX + "polling.fixed-delay=100ms", OUTBOX + "polling.jitter=0", OUTBOX + "retry.initial-delay=100ms",
                OUTBOX + "retry.max-delay=200ms", OUTBOX + "retry.jitter=0", OUTBOX + "delivery.timeout=12s", OUTBOX + "delivery.lease-duration=16s",
                OUTBOX + "state-machine.execution-timeout=1s", OUTBOX + "state-machine.business.timeout=3s", AUDIT + "enabled=true", AUDIT + "write-mode=OUTBOX", AUDIT + "source-service=tianshu-admin",
                AUDIT + "target.enabled=true", AUDIT + "target.max-age=P90D", AUDIT + "target.transaction-timeout=5s",
                AUDIT + "target.ddl.enabled=true", AUDIT + "target.ddl.auto-migrate=" + !restrictedRuntime));
        for (int group = 0; group < 2; group++) for (int role = 0; role < 2; role++) {
            String key = AUDIT + "target.data-sources[" + (group * 2 + role) + "].";
            properties.addAll(List.of(key + "name=cutover_audit_" + group + (role == 0 ? "_primary" : "_replica"),
                    key + "logical-name=tianshu_audit_" + group, key + "role=" + (role == 0 ? "PRIMARY" : "REPLICA"),
                    key + "driver-class-name=org.postgresql.Driver", key + "jdbc-url=" + url(group, role == 1),
                    key + "username=" + targetUser, key + "password=" + targetPassword));
        }
        return new ApplicationContextRunner().withInitializer(context -> {
            context.getBeanFactory().addBeanPostProcessor(Host.commitFault(faults));
            try { new YamlPropertySourceLoader().load("cutover-persistence", new ClassPathResource("egon-tianshu-persistence.yml"))
                    .forEach(value -> context.getEnvironment().getPropertySources().addLast(value)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }).withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class, EgonColaMybatisPlusAutoConfiguration.class,
                EgonColaShardingAutoConfiguration.class, MybatisPlusInnerInterceptorAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class,
                TransactionalOutboxAutoConfiguration.class, OutboxStateMachineAutoConfiguration.class, OutboxMybatisPlusAutoConfiguration.class,
                EgonOperationLogAutoConfiguration.class))
                .withUserConfiguration(Host.class, DdcOutboxConfiguration.class, DdcAdminOutboxDispatchConfiguration.class,
                        DdcConfigDeliveryPersistenceConfiguration.class, DdcConfigItemRepositoryImpl.class, DdcConfigItemConverterImpl.class,
                        DdcConfigVersionRepositoryImpl.class, DdcConfigVersionConverterImpl.class, DdcPublishTaskRepositoryImpl.class,
                        DdcPublishTaskConverterImpl.class, DdcConfigDispatchEventConverterImpl.class, DdcConfigDispatchDeliveryHandler.class,
                        DdcConfigManifestFactory.class, DdcPublishService.class, DdcPendingPublishDispatcher.class, PublishFailureRecorder.class,
                        DdcTenantRegistry.class, DdcTenantContextExecutor.class, DdcOperationLogQueryService.class,
                        DdcOperationLogSummaryViewConverterImpl.class, DdcOperationLogDetailViewConverterImpl.class,
                        DdcOperationLogOutboxAdapter.ProducerConfiguration.class, DdcOperationLogOutboxAdapter.AdminConsumptionConfiguration.class)
                .withBean("cutoverMeterRegistry", io.micrometer.core.instrument.simple.SimpleMeterRegistry.class)
                .withBean(TENANT_PROPERTIES, DdcTenantAdminProperties.class, () -> tenants)
                .withBean("egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties", DdcAdminProperties.class)
                .withBean("jacksonObjectMapper", ObjectMapper.class, () -> JSON)
                .withBean("operationLogContextProvider", Supplier.class, () -> (Supplier<OperationLogContextDTO>) () ->
                        new OperationLogContextDTO(EgonColaTenantIdProvider.currentTenantId(), "SERVICE", "cutover-authority", null,
                                "JOB", null, null, null, null, null, null))
                .withBean("ddcConfigPublicationPolicyResolver", DdcConfigManifestFactory.PublicationPolicyResolver.class,
                        () -> (tenant, scope) -> new DdcConfigManifestFactory.PublicationPolicyVO(scope, List.of(),
                                List.of(new TargetBindingVO("cutover-binding", "1", "isolated", "cutover-ns", "Deployment",
                                        "cutover-workload", "cutover-workload-uid", "app")), null, Map.of(), Map.of()))
                .withPropertyValues(properties.toArray(String[]::new));
    }

    @TestConfiguration(value = "cutoverFixtureConfiguration", proxyBeanMethods = false)
    @MapperScan(basePackageClasses = DdcConfigBootBindingMapper.class, lazyInitialization = "true")
    static class Host {
        @Bean("cutoverEventIds") AtomicLong ids() { return new AtomicLong(); }
        @Bean("operationLogIdGenerator") LongSupplier eventIds(@Qualifier("cutoverEventIds") AtomicLong ids) { return ids::get; }
        @Bean("outboxCommittedEventListener") OutboxCommittedEventListener deterministicWakeup(@Qualifier("outboxDispatcher") OutboxDispatcher dispatcher) {
            return new OutboxCommittedEventListener(dispatcher) {
                @Override public void onApplicationEvent(PayloadApplicationEvent<OutboxCommittedEvent> event) { /* explicitly tick real dispatcher below */ }
            };
        }
        static BeanPostProcessor commitFault(FaultControls faults) {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if ("egonColaPhysicalDataSourceFactory".equals(name)) {
                        var actual = (EgonColaPhysicalDataSourceFactory) bean;
                        return new EgonColaPhysicalDataSourceFactory() {
                            @Override public Map<String, DataSource> create(EgonColaShardingProperties properties) {
                                var pools = new LinkedHashMap<>(actual.create(properties));
                                pools.computeIfPresent("cutover_audit_0_replica", (key, pool) -> new FaultAwareDataSource(pool, faults, true));
                                pools.computeIfPresent("cutover_audit_0_primary", (key, pool) -> new FaultAwareDataSource(pool, faults, false));
                                return Map.copyOf(pools);
                            }
                        };
                    }
                    if (!"operationLogTransactionManager".equals(name)) return bean;
                    var actual = (DataSourceTransactionManager) bean;
                    return new DataSourceTransactionManager(actual.getDataSource()) {
                        @Override protected void doCommit(DefaultTransactionStatus status) {
                            super.doCommit(status);
                            if (faults.targetCommitReplyLost.compareAndSet(true, false)) {
                                faults.targetCommitFaults.incrementAndGet();
                                throw new TransactionSystemException("CUTOVER_TARGET_COMMIT_REPLY_LOST", new SQLException("connection reply lost", "08006"));
                            }
                        }
                    };
                }
            };
        }
    }

    private static PlatformTransactionManager lostSourceAck(PlatformTransactionManager actual, DataSource source, long eventId,
            AtomicBoolean armed, AtomicInteger injected) {
        return new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return actual.getTransaction(definition); }
            @Override public void commit(TransactionStatus status) {
                if (armed.get() && visibleSuccess(source, eventId) && armed.compareAndSet(true, false)) {
                    actual.rollback(status); injected.incrementAndGet();
                    throw new TransactionSystemException("CUTOVER_SOURCE_ACK_REPLY_LOST", new SQLException("connection reply lost", "08006"));
                }
                actual.commit(status);
            }
            @Override public void rollback(TransactionStatus status) { actual.rollback(status); }
        };
    }
    private static boolean visibleSuccess(DataSource source, long id) {
        try (var statement = DataSourceUtils.getConnection(source).prepareStatement(
                "SELECT status FROM egon_outbox.egon_cola_outbox_message WHERE message_id=?")) {
            statement.setString(1, Long.toString(id));
            try (var rows = statement.executeQuery()) { return rows.next() && "SUCCEEDED".equals(rows.getString(1)); }
        } catch (SQLException failure) { throw new IllegalStateException(failure); }
    }
    private static class FaultControls {
        private final AtomicBoolean targetCommitReplyLost = new AtomicBoolean();
        private final AtomicInteger targetCommitFaults = new AtomicInteger();
        private final AtomicBoolean replicaConnectionFailure = new AtomicBoolean();
        private final AtomicInteger primaryConnectionsDuringReplicaFault = new AtomicInteger();
        private final AtomicInteger replicaConnectionFaults = new AtomicInteger();
    }
    private static final class FaultAwareDataSource extends DelegatingDataSource implements AutoCloseable {
        private final FaultControls faults;
        private final boolean replica;
        FaultAwareDataSource(DataSource source, FaultControls faults, boolean replica) { super(source); this.faults = faults; this.replica = replica; }
        private void observe() throws SQLException {
            if (!faults.replicaConnectionFailure.get()) return;
            if (replica) {
                faults.replicaConnectionFaults.incrementAndGet();
                throw new SQLException("CUTOVER_REPLICA_CONNECTION_UNAVAILABLE", "08006");
            }
            faults.primaryConnectionsDuringReplicaFault.incrementAndGet();
        }
        @Override public Connection getConnection() throws SQLException { observe(); return super.getConnection(); }
        @Override public Connection getConnection(String user, String password) throws SQLException { observe(); return super.getConnection(user, password); }
        @Override public void close() throws Exception { if (getTargetDataSource() instanceof AutoCloseable owned) owned.close(); }
    }

    private void initializeSource(SourceDatabaseVO source) throws Exception {
        String yaml;
        try (var input = new ClassPathResource("egon-tianshu-persistence.yml").getInputStream()) { yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        yaml = yaml.replace("\"${TIANSHU_JDBC_URL:${spring.datasource.url}}\"", JSON.writeValueAsString(source.url()))
                .replace("\"${TIANSHU_DB_USERNAME:${spring.datasource.username}}\"", JSON.writeValueAsString(source.user()))
                .replace("\"${TIANSHU_DB_PASSWORD:${spring.datasource.password}}\"", JSON.writeValueAsString(source.password()));
        Path file = Files.createFile(scratch.resolve("source.yml"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file, yaml);
        DdcPersistenceMigrationCli.main(new String[]{"initialize", "--target-config", file.toString()});
    }
    private static SourceDatabaseVO sourceDatabase() throws SQLException {
        String url = required("TIANSHU_OPERATION_LOG_IT_SOURCE_URL"), user = required("TIANSHU_OPERATION_LOG_IT_SOURCE_USER"), password = required("TIANSHU_OPERATION_LOG_IT_SOURCE_PASSWORD");
        if (!loopbackDatabase(url, "postgres|tianshu_operation_log_cutover_[0-9a-f]{32}")) {
            throw new IllegalStateException("CUTOVER_OWNED_LOOPBACK_SOURCE_REQUIRED");
        }
        boolean create = url.endsWith("/postgres");
        var admin = dataSource(url, user, password);
        String database = create ? "tianshu_operation_log_cutover_" + UUID.randomUUID().toString().replace("-", "") : url.substring(url.lastIndexOf('/') + 1);
        assertThat(count(admin, "SELECT CASE WHEN pg_is_in_recovery() THEN 1 ELSE 0 END")).isZero();
        if (create) execute(admin, "CREATE DATABASE " + database);
        String selected = url.substring(0, url.lastIndexOf('/') + 1) + database;
        var physical = dataSource(selected, user, password);
        try (var connection = physical.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT current_database(),pg_get_userbyid(datdba)=current_user FROM pg_database WHERE datname=current_database()")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(database); assertThat(rows.getBoolean(2)).isTrue();
        }
        return new SourceDatabaseVO(physical, selected, user, password, create, database);
    }
    private record SourceDatabaseVO(PGSimpleDataSource physical, String url, String user, String password, boolean createdHere, String database) implements AutoCloseable {
        @Override public void close() throws SQLException {
            if (createdHere) execute(dataSource(url.substring(0, url.lastIndexOf('/') + 1) + "postgres", user, password), "DROP DATABASE " + database + " WITH (FORCE)");
        }
    }
    private record AcceptedEventVO(long tenant, long id, OperationLogEvent event, EgonColaPhysicalTargetBO target) { }
    private static DdcTenantContextDTO trusted(long tenant) { return new DdcTenantContextDTO(tenant, "cutover-" + tenant, "service:cutover-authority"); }
    private static long tenantFor(EgonColaTwoLevelRouteStrategy routes, EgonColaRoutingProfileBO profile, long base, String group) {
        return java.util.stream.LongStream.range(base, base + 64).filter(tenant -> node(routes, profile, tenant, 1).group().equals(group)).findFirst().orElseThrow();
    }
    private static long idFor(EgonColaTwoLevelRouteStrategy routes, EgonColaRoutingProfileBO profile, long tenant, int bucket, long first) {
        for (long id = first; id < first + 1024; id++) if (node(routes, profile, tenant, id).table().endsWith("_b" + bucket)) return id;
        throw new AssertionError("CUTOVER_BUCKET_ROUTE_NOT_FOUND");
    }
    private static EgonColaPhysicalTargetBO node(EgonColaTwoLevelRouteStrategy routes, EgonColaRoutingProfileBO profile, long tenant, long id) {
        return routes.route(profile, new EgonColaRouteQuery(profile.logicalTable(), tenant, List.of(id), EgonColaRouteQuery.OperationEnum.COMMAND, false)).targets().getFirst();
    }
    private static PGSimpleDataSource primary(AcceptedEventVO event) { return audit(event.target().group().endsWith("_0") ? 0 : 1, false); }
    private static String table(String table) {
        if (!table.matches("egon_cola_operation_log_(receipt_)?t[01]_b[01]")) throw new IllegalArgumentException("CUTOVER_TABLE_NOT_OWNED");
        return table;
    }
    private static long physicalCount(AcceptedEventVO event, boolean replica, boolean receipt) {
        String selected = table(event.target().table());
        if (receipt) selected = selected.replace("operation_log_", "operation_log_receipt_");
        try (var connection = audit(event.target().group().endsWith("_0") ? 0 : 1, replica).getConnection();
             var statement = connection.prepareStatement("SELECT count(*) FROM public." + selected + " WHERE tenant_id=? AND id=?")) {
            statement.setLong(1, event.tenant()); statement.setLong(2, event.id());
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        } catch (SQLException failure) { throw new IllegalStateException(failure); }
    }
    private static void cleanOwnTargetRows(List<AcceptedEventVO> events) throws SQLException {
        for (var event : events) for (boolean receipt : List.of(false, true)) {
            String selected = table(event.target().table());
            if (receipt) selected = selected.replace("operation_log_", "operation_log_receipt_");
            try (var connection = primary(event).getConnection(); var clean = connection.prepareStatement("DELETE FROM public." + selected + " WHERE tenant_id=? AND id=?")) {
                clean.setLong(1, event.tenant()); clean.setLong(2, event.id()); clean.executeUpdate();
            }
        }
    }
    private static void awaitDispatch(OutboxDispatcher dispatcher, SourceDatabaseVO source, long id) throws Exception {
        await("original task retries to success", () -> { dispatcher.submitDue(); return succeeded(source, id); });
    }
    private static boolean succeeded(SourceDatabaseVO source, long id) { return "SUCCEEDED".equals(sourceStatus(source, id)); }
    private static String sourceStatus(SourceDatabaseVO source, long id) {
        try (var connection = source.physical().getConnection(); var statement = connection.prepareStatement("SELECT status FROM egon_outbox.egon_cola_outbox_message WHERE message_id=?")) {
            statement.setString(1, Long.toString(id)); try (var rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : "MISSING"; }
        } catch (SQLException failure) { throw new IllegalStateException(failure); }
    }
    private static long auditMessageCount(SourceDatabaseVO source) throws SQLException { return count(source.physical(), "SELECT count(*) FROM egon_outbox.egon_cola_outbox_message WHERE channel='tianshu.operation-log'"); }
    private static void await(String description, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        do { if (condition.getAsBoolean()) return; Thread.sleep(100); } while (System.nanoTime() < deadline);
        throw new AssertionError(description);
    }
    /** Runtime app credentials are optional; when supplied, the same actual call path must work without owner privileges. */
    private static void verifyRuntimePrivileges() throws SQLException {
        String runtime = System.getenv("TIANSHU_OPERATION_LOG_IT_RUNTIME_USER");
        if (runtime == null || runtime.isBlank()) return;
        String password = required("TIANSHU_OPERATION_LOG_IT_RUNTIME_PASSWORD");
        assertThat(runtime).isNotEqualTo(required("EGON_MP_PG_USER"));
        for (int group = 0; group < 2; group++) {
            var application = dataSource(url(group, false), runtime, password);
            String fact = "egon_cola_operation_log_t" + group + "_b0";
            String receipt = "egon_cola_operation_log_receipt_t" + group + "_b0";
            assertThat(count(application, "SELECT CASE WHEN rolsuper THEN 1 ELSE 0 END FROM pg_roles WHERE rolname=current_user")).isZero();
            assertThat(count(application, "SELECT CASE WHEN has_schema_privilege(current_user,'public','CREATE') THEN 1 ELSE 0 END")).isZero();
            for (String table : List.of(fact, receipt)) {
                for (String operation : List.of("SELECT", "INSERT")) {
                    assertThat(count(application, "SELECT CASE WHEN has_table_privilege(current_user,'public." + table + "','" + operation + "') THEN 1 ELSE 0 END")).isEqualTo(1);
                }
                for (String operation : List.of("UPDATE", "DELETE")) {
                    assertThat(count(application, "SELECT CASE WHEN has_table_privilege(current_user,'public." + table + "','" + operation + "') THEN 1 ELSE 0 END")).isZero();
                }
                assertThatThrownBy(() -> execute(application, "UPDATE public." + table + " SET id=id WHERE false"))
                        .isInstanceOfSatisfying(SQLException.class, denied -> assertThat(denied.getSQLState()).isEqualTo("42501"));
                assertThatThrownBy(() -> execute(application, "DELETE FROM public." + table + " WHERE false"))
                        .isInstanceOfSatisfying(SQLException.class, denied -> assertThat(denied.getSQLState()).isEqualTo("42501"));
            }
        }
    }

    private static void verifyReplicationGroups() throws SQLException {
        var endpoints = new java.util.HashSet<String>();
        for (int group = 0; group < 2; group++) for (boolean replica : List.of(false, true)) {
            assertThat(endpoints.add(url(group, replica))).as("four separately supplied loopback endpoints").isTrue();
        }
        var identities = new java.util.HashSet<Long>();
        for (int group = 0; group < 2; group++) {
            var primary = audit(group, false); var replica = audit(group, true);
            assertThat(identities.add(count(primary, "SELECT system_identifier FROM pg_control_system()"))).as("two distinct PostgreSQL clusters").isTrue();
            assertThat(count(primary, "SELECT CASE WHEN pg_is_in_recovery() THEN 1 ELSE 0 END")).isZero();
            assertThat(count(replica, "SELECT CASE WHEN pg_is_in_recovery() THEN 1 ELSE 0 END")).isEqualTo(1);
            assertThat(count(primary, "SELECT system_identifier FROM pg_control_system()"))
                    .isEqualTo(count(replica, "SELECT system_identifier FROM pg_control_system()"));
            assertThat(count(primary, "SELECT count(*) FROM pg_stat_replication WHERE state='streaming'")).isGreaterThanOrEqualTo(1);
        }
    }
    private static PGSimpleDataSource audit(int group, boolean replica) { return dataSource(url(group, replica), required("EGON_MP_PG_USER"), required("EGON_MP_PG_PASSWORD")); }
    private static String url(int group, boolean replica) {
        String value = required("EGON_MP_PG_SHARD_" + group + (replica ? "_REPLICA_URL" : "_PRIMARY_URL"));
        if (!loopbackDatabase(value, "postgres|audit_[a-z0-9_]*[0-9a-f]{32}|tianshu_operation_log_cutover_[0-9a-f]{32}")) throw new IllegalStateException("CUTOVER_DEDICATED_AUDIT_TOPOLOGY_REQUIRED");
        return value;
    }
    private static boolean loopbackDatabase(String value, String allowedDatabase) {
        try {
            if (value == null || !value.startsWith("jdbc:postgresql://")) return false;
            var uri = java.net.URI.create(value.substring("jdbc:".length()));
            return "postgresql".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() > 0 && uri.getPort() <= 65535 && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getPath() != null && uri.getPath().matches("/(?:" + allowedDatabase + ")");
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static PGSimpleDataSource dataSource(String url, String user, String password) {
        var source = new PGSimpleDataSource(); source.setURL(url); source.setUser(user); source.setPassword(password); source.setSocketTimeout(10); return source;
    }
    private static long count(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }
    private static void execute(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String required(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException("Missing dedicated integration setting: " + name); return value;
    }
}
