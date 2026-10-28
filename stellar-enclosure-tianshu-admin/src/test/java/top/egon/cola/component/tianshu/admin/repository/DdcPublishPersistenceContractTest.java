package top.egon.cola.component.tianshu.admin.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcRuntimeMetadataTypeHandler;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonModel;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcPublishPersistenceContractTest {

    private static final String BASE = "top.egon.cola.component.tianshu.admin.";
    private static final List<String> TABLES = List.of("DdcPublishTask", "DdcPublishAck", "DdcInstance", "DdcOperationLog");

    @Test
    void operationalRowsUseGuardedMpModelsAndKeepExternalConfigIdsSeparate() throws Exception {
        for (String name : TABLES) {
            Class<?> po = requiredType(BASE + "model.po." + name + "PO");
            Class<?> bo = requiredType(BASE + "model.bo." + name + "BO");
            Class<?> mapper = requiredType(BASE + "dao." + name + "Mapper");
            Class<?> implementation = requiredType(BASE + "repository.impl." + name + "RepositoryImpl");

            assertThat(po.getSuperclass()).isEqualTo(EgonModel.class);
            assertThat(po.getAnnotation(TableName.class).value()).isEqualTo(table(name));
            assertThat(EgonModel.class.isAssignableFrom(bo)).isFalse();
            assertThat(EgonColaMapper.class.isAssignableFrom(mapper)).isTrue();
            assertThat(EgonColaRepository.class.isAssignableFrom(implementation)).isTrue();
        }
        assertThat(fieldNames(requiredType(BASE + "model.po.DdcPublishTaskPO")))
                .contains("changeId", "configId", "targetVersion", "attemptCount", "ackCount", "status");
        assertThat(fieldNames(requiredType(BASE + "model.bo.DdcPublishTaskBO")))
                .contains("configItemId", "configId", "targetVersion", "attemptCount", "ackCount", "status");
        assertThat(fieldNames(requiredType(BASE + "model.po.DdcPublishAckPO")))
                .contains("changeId", "instanceId", "leaseId", "targetVersion", "ackStatus");
        assertThat(requiredType(BASE + "model.po.DdcPublishAckPO").getDeclaredField("resourceChecksum")
                .getAnnotation(TableField.class).value()).isEqualTo("content_checksum");
        assertThat(fieldNames(requiredType(BASE + "model.po.DdcInstancePO")))
                .contains("instanceId", "leaseId", "leaseExpireAt", "resourceVersion", "runtimeMetadata");
        assertThat(fieldNames(requiredType(BASE + "model.po.DdcOperationLogPO")))
                .contains("operationType", "operator", "operatorIp", "operationContent");
    }

    @Test
    void runtimeMetadataUsesAFieldScopedMybatisTypeHandler() throws Exception {
        Class<?> po = requiredType(BASE + "model.po.DdcInstancePO");
        Field metadata = po.getDeclaredField("runtimeMetadata");
        TableField mapping = metadata.getAnnotation(TableField.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.typeHandler()).isEqualTo(requiredType(BASE + "model.convertor.DdcRuntimeMetadataTypeHandler"));
        assertThat(BaseTypeHandler.class.isAssignableFrom(mapping.typeHandler())).isTrue();
    }

    @Test
    void runtimeMetadataTypeHandlerStoresAndLoadsJsonWithoutChangingOtherColumns() throws Exception {
        DdcRuntimeMetadataTypeHandler handler = new DdcRuntimeMetadataTypeHandler();
        java.sql.PreparedStatement statement = mock(java.sql.PreparedStatement.class);
        handler.setParameter(statement, 1, Map.of("zone", "blue"), JdbcType.VARCHAR);
        verify(statement).setString(1, "{\"zone\":\"blue\"}");

        java.sql.ResultSet resultSet = mock(java.sql.ResultSet.class);
        when(resultSet.getString("runtime_metadata")).thenReturn("{\"zone\":\"blue\"}");
        assertThat(handler.getResult(resultSet, "runtime_metadata")).containsEntry("zone", "blue");
    }

    @Test
    void operationalMapperSqlUsesLogicalTablesAndPreservesLifecycleStatePredicates() throws Exception {
        for (String name : TABLES) {
            String mapperName = name + "Mapper";
            String xml = mapper(mapperName + ".xml").toLowerCase(java.util.Locale.ROOT);
            String table = table(name);
            assertThat(xml).contains("from " + table, "tenant_id", "deleted_at is null", "selectactivebyid",
                    "selectactivebyids", "deleteversionedbyid")
                    .doesNotContain("from " + table + "_t0", "update " + table + "_t0", "<delete");
        }
        String taskXml = mapper("DdcPublishTaskMapper.xml").toLowerCase(java.util.Locale.ROOT);
        assertThat(taskXml).contains("selectforupdatebychangeid", "for update", "claimstaleforrecovery",
                "updated_at &lt;", "attempt_count", "status", "failure_stage", "error_message");
        String ackXml = mapper("DdcPublishAckMapper.xml").toLowerCase(java.util.Locale.ROOT);
        assertThat(ackXml).contains("selectforupdatebychangeidandinstanceidandleaseid", "for update",
                "lease_id is null", "markincompletetimeout", "ack_status is null", "resettargets");
        String instanceXml = mapper("DdcInstanceMapper.xml").toLowerCase(java.util.Locale.ROOT);
        assertThat(instanceXml).contains("markofflineifleasematches", "markresourceadmissionoffline",
                "runtime_metadata", "resource_server_id", "resource_version", "admission_expires_at");
    }

    @Test
    void everyOperationalMapperXmlRegistersItsDeclaredCommands() throws Exception {
        Configuration configuration = operationalMappers();
        for (String name : TABLES) {
            String namespace = BASE + "dao." + name + "Mapper";
            assertThat(configuration.hasStatement(namespace + ".selectActiveById")).isTrue();
            assertThat(configuration.hasStatement(namespace + ".selectActiveByIds")).isTrue();
            assertThat(configuration.hasStatement(namespace + ".deleteVersionedById")).isTrue();
        }
        assertThat(configuration.hasStatement(BASE + "dao.DdcPublishTaskMapper.transitionToPublishing")).isTrue();
        assertThat(configuration.hasStatement(BASE + "dao.DdcPublishAckMapper.markIncompleteTimeout")).isTrue();
        assertThat(configuration.hasStatement(BASE + "dao.DdcInstanceMapper.markResourceAdmissionOffline")).isTrue();
    }

    @Test
    void mapperSqlRetainsTenantStateCasLeaseAndNullPredicates() throws Exception {
        Configuration configuration = operationalMappers();
        Map<String, Object> ackParameters = new HashMap<>();
        ackParameters.put("tenantId", 101L);
        ackParameters.put("changeId", "123");
        ackParameters.put("instanceId", "instance-1");
        ackParameters.put("leaseId", null);
        String ackSql = configuration.getMappedStatement(BASE
                        + "dao.DdcPublishAckMapper.selectForUpdateByChangeIdAndInstanceIdAndLeaseId")
                .getBoundSql(ackParameters).getSql().toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> taskParameters = new HashMap<>();
        taskParameters.put("tenantId", 101L);
        taskParameters.put("task", top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO.builder()
                .id(9L).version(7L).changeId("123").build());
        taskParameters.put("dispatchableStatuses", List.of("PENDING", "PUBLISHING"));
        taskParameters.put("pendingStatus", "PENDING");
        taskParameters.put("publishingStatus", "PUBLISHING");
        taskParameters.put("dispatchedAt", java.time.LocalDateTime.of(2026, 9, 28, 10, 0));
        taskParameters.put("actor", "system:test");
        String transitionSql = configuration.getMappedStatement(BASE + "dao.DdcPublishTaskMapper.transitionToPublishing")
                .getBoundSql(taskParameters).getSql().toLowerCase(java.util.Locale.ROOT);

        assertThat(ackSql).contains("tenant_id = ?", "change_id = ?", "instance_id = ?", "lease_id is null",
                "deleted_at is null", "for update");
        assertThat(transitionSql).contains("tenant_id = ?", "id = ?", "version = ?", "change_id = ?", "deleted_at is null",
                "status in", "attempt_count = case", "version = version + 1");
    }

    @Test
    void replicaAckLookupAndObservedVersionFencesPassTheStarterGuards() throws Exception {
        var configuration = operationalMappers();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), org.mockito.Mockito.mock(javax.sql.DataSource.class)));
        var properties = new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties();
        var tenantGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor(
                () -> "system:test", properties);
        new org.springframework.context.annotation.CommonAnnotationBeanPostProcessor()
                .postProcessBeforeInitialization(tenantGuard, "sqlGuard");
        var routeGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaLocalWriteGuardInnerInterceptor(
                query -> new top.egon.cola.component.common.mybatis.routing.EgonColaRouteResult(
                        List.of(new top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO(
                                "primary", "public", query.logicalTable())), "a".repeat(64)),
                tenantGuard, org.mockito.Mockito.mock(top.egon.cola.component.common.mybatis.routing.EgonColaTwoLevelRouteStrategy.class), Map.of());
        var readParameters = Map.<String, Object>of("tenantId", 101L, "changeId", "change", "instanceId", "instance", "leaseId", "lease");
        var read = configuration.getMappedStatement(BASE + "dao.DdcPublishAckMapper.selectByChangeIdAndInstanceIdAndLeaseId");
        assertThat(read.getBoundSql(readParameters).getSql().toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("for update", "for share");
        org.slf4j.MDC.put("tenantId", "101"); org.slf4j.MDC.put("userId", "system:test");
        try {
            tenantGuard.beforeQuery(org.mockito.Mockito.mock(org.apache.ibatis.executor.Executor.class), read,
                    readParameters, org.apache.ibatis.session.RowBounds.DEFAULT, null, read.getBoundSql(readParameters));
            for (String mapper : List.of("DdcPublishTaskMapper", "DdcPublishAckMapper")) {
                var statement = configuration.getMappedStatement(BASE + "dao." + mapper + ".fenceObservedVersion");
                var parameters = Map.<String, Object>of("tenantId", 101L, "id", 9L, "expectedVersion", 7L,
                        "actor", "system:test", "now", java.time.Instant.parse("2026-10-28T00:00:00Z"));
                String sql = statement.getBoundSql(parameters).getSql().toLowerCase(java.util.Locale.ROOT);
                assertThat(sql).contains("tenant_id = ?", "id = ?", "version = ?", "version = version + 1", "deleted_at is null");
                var handler = configuration.newStatementHandler(org.mockito.Mockito.mock(org.apache.ibatis.executor.Executor.class),
                        statement, parameters, org.apache.ibatis.session.RowBounds.DEFAULT, null, statement.getBoundSql(parameters));
                tenantGuard.beforePrepare(handler, null, null);
                routeGuard.beforePrepare(handler, null, null);
            }
            var reset = configuration.getMappedStatement(BASE + "dao.DdcPublishAckMapper.resetTarget");
            var resetParameters = Map.<String, Object>of("tenantId", 101L, "id", 9L, "expectedVersion", 7L,
                    "actor", "system:test", "now", java.time.Instant.parse("2026-10-28T00:00:00Z"));
            var resetHandler = configuration.newStatementHandler(org.mockito.Mockito.mock(org.apache.ibatis.executor.Executor.class),
                    reset, resetParameters, org.apache.ibatis.session.RowBounds.DEFAULT, null, reset.getBoundSql(resetParameters));
            routeGuard.beforePrepare(resetHandler, null, null);
        } finally {
            org.slf4j.MDC.remove("tenantId"); org.slf4j.MDC.remove("userId");
        }
    }

    @Test
    void ackSummaryConditionsUseOneTenantAndPrimaryGroupForEveryReferencedTable() throws Exception {
        var configuration = operationalMappers();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), mock(javax.sql.DataSource.class)));
        var properties = new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties();
        var tenantGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor(() -> "system:test", properties);
        new org.springframework.context.annotation.CommonAnnotationBeanPostProcessor().postProcessBeforeInitialization(tenantGuard, "sqlGuard");
        var routeGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaLocalWriteGuardInnerInterceptor(
                query -> new top.egon.cola.component.common.mybatis.routing.EgonColaRouteResult(
                        List.of(new top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO("primary", "public", query.logicalTable())), "a".repeat(64)),
                tenantGuard, mock(top.egon.cola.component.common.mybatis.routing.EgonColaTwoLevelRouteStrategy.class), Map.of());
        var task = top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO.builder()
                .id(9L).version(7L).changeId("change").status("PUBLISHING").configItemId(21L).targetVersion(2L).build();
        var statement = configuration.getMappedStatement(BASE + "dao.DdcPublishTaskMapper.updateAckSummary");
        var parser = new org.apache.shardingsphere.infra.parser.ShardingSphereSQLParserEngine(
                org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader.getService(
                        org.apache.shardingsphere.database.connector.core.type.DatabaseType.class, "PostgreSQL"),
                new org.apache.shardingsphere.sql.parser.engine.api.CacheOption(16, 64),
                new org.apache.shardingsphere.sql.parser.engine.api.CacheOption(16, 64));
        org.slf4j.MDC.put("tenantId", "101"); org.slf4j.MDC.put("userId", "system:test");
        try {
            for (String publication : List.of("ANY", "PUBLISHED", "UNPUBLISHED")) {
                var summary = new top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckSummaryBO(
                        2, publication.equals("ANY") ? 1 : 2, 0, 0, 0,
                        publication.equals("PUBLISHED") ? "SUCCESS" : null, null, null, publication);
                var parameters = Map.<String, Object>of("tenantId", 101L, "task", task, "summary", summary,
                        "updatedAt", java.time.LocalDateTime.of(2026, 10, 28, 0, 0), "actor", "system:test",
                        "now", java.time.Instant.parse("2026-10-28T00:00:00Z"));
                String sql = statement.getBoundSql(parameters).getSql();
                assertThat(parser.parse(sql, false)).isNotNull();
                assertThat(sql.toLowerCase(java.util.Locale.ROOT)).contains("t.tenant_id = ?", "t.id = ?", "t.version = ?",
                        "a.tenant_id = ?", "a.deleted_at is null").doesNotContain("for update");
                var executor = mock(org.apache.ibatis.executor.Executor.class);
                var invocation = new org.apache.ibatis.plugin.Invocation(executor,
                        org.apache.ibatis.executor.Executor.class.getMethod("update", org.apache.ibatis.mapping.MappedStatement.class, Object.class),
                        new Object[]{statement, parameters});
                org.assertj.core.api.Assertions.assertThatCode(() ->
                        new top.egon.cola.component.common.mybatis.interceptor.EgonColaOriginalSqlGuardInterceptor(properties).intercept(invocation))
                        .doesNotThrowAnyException();
                var handler = configuration.newStatementHandler(executor, statement, parameters,
                        org.apache.ibatis.session.RowBounds.DEFAULT, null, statement.getBoundSql(parameters));
                org.assertj.core.api.Assertions.assertThatCode(() -> routeGuard.beforePrepare(handler, null, null)).doesNotThrowAnyException();
            }
            var parameters = new HashMap<String, Object>();
            parameters.put("tenantId", 101L);
            parameters.put("task", task);
            parameters.put("targets", List.of(new top.egon.cola.component.tianshu.model.config.DdcPublishTarget("one", "lease"),
                    new top.egon.cola.component.tianshu.model.config.DdcPublishTarget("two", null)));
            parameters.put("activeStatuses", List.of("PENDING", "PUBLISHING"));
            parameters.put("dispatchableStatuses", List.of("PENDING", "PUBLISHING"));
            parameters.put("retryableStatuses", List.of("FAILED", "TIMEOUT"));
            parameters.put("actor", "system:test");
            parameters.put("now", java.time.Instant.parse("2026-10-28T00:00:00Z"));
            for (String operation : List.of("fenceDispatchTargets", "claimStaleForRecovery", "transitionToPublishing", "transitionToTerminal", "resetForRetry", "markRetryLeaseExpired")) {
                var update = configuration.getMappedStatement(BASE + "dao.DdcPublishTaskMapper." + operation);
                String sql = update.getBoundSql(parameters).getSql();
                assertThat(parser.parse(sql, false)).isNotNull();
                assertThat(sql.toLowerCase(java.util.Locale.ROOT)).contains("tenant_id = ?", "id = ?", "version = ?")
                        .doesNotContain("for update");
                if (operation.equals("fenceDispatchTargets")) {
                    assertThat(sql.toLowerCase(java.util.Locale.ROOT)).contains("count(*)", "a.instance_id = ?", "a.lease_id is null");
                }
                var executor = mock(org.apache.ibatis.executor.Executor.class);
                var invocation = new org.apache.ibatis.plugin.Invocation(executor,
                        org.apache.ibatis.executor.Executor.class.getMethod("update", org.apache.ibatis.mapping.MappedStatement.class, Object.class),
                        new Object[]{update, parameters});
                org.assertj.core.api.Assertions.assertThatCode(() ->
                        new top.egon.cola.component.common.mybatis.interceptor.EgonColaOriginalSqlGuardInterceptor(properties).intercept(invocation))
                        .doesNotThrowAnyException();
                var handler = configuration.newStatementHandler(executor, update, parameters,
                        org.apache.ibatis.session.RowBounds.DEFAULT, null, update.getBoundSql(parameters));
                org.assertj.core.api.Assertions.assertThatCode(() -> routeGuard.beforePrepare(handler, null, null)).doesNotThrowAnyException();
            }
        } finally {
            org.slf4j.MDC.remove("tenantId"); org.slf4j.MDC.remove("userId");
        }
    }

    @Test
    void twoWritersCannotFenceTheSameObservedAcknowledgementVersion() throws Exception {
        var mapper = org.mockito.Mockito.mock(top.egon.cola.component.tianshu.admin.dao.DdcPublishAckMapper.class);
        var won = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.when(mapper.fenceObservedVersion(org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("system:test"), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> won.compareAndSet(false, true) ? 1 : 0);
        var repository = new top.egon.cola.component.tianshu.admin.repository.impl.DdcPublishAckRepositoryImpl(mapper,
                org.mockito.Mockito.mock(top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishAckConverter.class),
                new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties(),
                org.mockito.Mockito.mock(top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator.class));
        var ack = top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckBO.builder().id(9L).version(7L).build();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> fence = () -> {
                org.slf4j.MDC.put("tenantId", "101"); org.slf4j.MDC.put("userId", "system:test");
                try { return repository.fenceObservedVersion(ack); }
                finally { org.slf4j.MDC.remove("tenantId"); org.slf4j.MDC.remove("userId"); }
            };
            var first = workers.submit(fence); var second = workers.submit(fence);
            assertThat(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }

    private Configuration operationalMappers() throws Exception {
        Configuration configuration = new Configuration();
        for (String name : TABLES) {
            String mapperName = name + "Mapper";
            String resource = "mybatis/mapper/tianshu/" + mapperName + ".xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private static String table(String modelName) {
        return switch (modelName) {
            case "DdcPublishTask" -> "ddc_publish_task";
            case "DdcPublishAck" -> "ddc_publish_ack";
            case "DdcInstance" -> "ddc_instance";
            case "DdcOperationLog" -> "ddc_operation_log";
            default -> throw new IllegalArgumentException(modelName);
        };
    }

    private Class<?> requiredType(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException failure) {
            throw new AssertionError("Missing operational persistence contract: " + name, failure);
        }
    }

    private Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet());
    }

    private String mapper(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("mybatis/mapper/tianshu/" + name)) {
            assertThat(input).as(name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
