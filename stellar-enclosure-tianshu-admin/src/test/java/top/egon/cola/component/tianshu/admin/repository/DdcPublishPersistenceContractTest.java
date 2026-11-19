package top.egon.cola.component.tianshu.admin.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor;
import top.egon.cola.component.tianshu.admin.dao.DdcPublishAckMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishAckConverter;
import top.egon.cola.component.tianshu.admin.model.po.DdcPublishAckPO;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcPublishAckRepositoryImpl;
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
import java.util.Locale;
import java.time.Instant;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
            assertThat(EgonColaMapper.class.isAssignableFrom(mapper)).isEqualTo(!name.equals("DdcInstance"));
            if (name.equals("DdcPublishAck") || name.equals("DdcInstance")) {
                assertThat(EgonColaRepository.class.isAssignableFrom(implementation)).isFalse();
                assertThat(Arrays.stream(implementation.getMethods()).map(java.lang.reflect.Method::getName))
                        .doesNotContain("save", "saveBatch", "updateById");
            } else {
                assertThat(EgonColaRepository.class.isAssignableFrom(implementation)).isTrue();
            }
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
    void operationalMapperSqlKeepsTenantReadsAndRetiresAllLegacyAckAndTaskWriters() throws Exception {
        for (String name : TABLES) {
            String xml = mapper(name + "Mapper.xml").toLowerCase(Locale.ROOT);
            assertThat(xml).contains("from " + table(name), "tenant_id", "deleted_at is null")
                    .doesNotContain("from " + table(name) + "_t0", "update " + table(name) + "_t0", "<delete");
            if (name.equals("DdcInstance")) {
                assertThat(xml).contains("selectbyinstanceid", "selectbyscope", "selectpage", "countpage")
                        .doesNotContain("selectbystatusandleaseexpireatlessthanequal", "selectbyscopeandstatus");
            } else {
                assertThat(xml).contains("selectactivebyid", "selectactivebyids");
            }
            if (name.equals("DdcPublishAck") || name.equals("DdcInstance")) {
                assertThat(xml).doesNotContain("<update", "<insert");
            }
        }
        var configuration = operationalMappers();
        for (String method : List.of("fenceObservedVersion", "fenceDispatchTargets", "updateAckSummary", "claimStaleForRecovery",
                "transitionToPublishing", "updateCounters", "transitionToTerminal", "resetForRetry", "markRetryLeaseExpired")) {
            assertThat(configuration.hasStatement(BASE + "dao.DdcPublishTaskMapper." + method)).as(method).isFalse();
        }
        for (String method : List.of("selectForUpdateByChangeIdAndInstanceIdAndLeaseId", "fenceObservedVersion",
                "markIncompleteTimeout", "resetTargets", "resetTarget", "deleteVersionedById")) {
            assertThat(configuration.hasStatement(BASE + "dao.DdcPublishAckMapper." + method)).as(method).isFalse();
        }
        assertThat(configuration.hasStatement(BASE + "dao.DdcPublishTaskMapper.updateNativeEvidence")).isTrue();
        assertThat(configuration.hasStatement(BASE + "dao.DdcPublishTaskMapper.resetNativeForRetry")).isTrue();
    }

    @Test
    void historicalAckReadKeepsTenantAndChangeSelectorsWithoutRowLocksOrStateMutation() throws Exception {
        var configuration = operationalMappers();
        var read = configuration.getMappedStatement(BASE + "dao.DdcPublishAckMapper.selectByChangeId");
        var parameters = Map.<String, Object>of("tenantId", 101L, "changeId", "change");
        assertThat(read.getBoundSql(parameters).getSql().toLowerCase(Locale.ROOT))
                .contains("tenant_id = ?", "change_id = ?", "deleted_at is null").doesNotContain("for update", "for share");
        var properties = new EgonColaMybatisPlusProperties();
        var guard = new EgonColaTenantIdGuardInnerInterceptor(() -> "history-reader", properties);
        new CommonAnnotationBeanPostProcessor().postProcessBeforeInitialization(guard, "sqlGuard");
        MDC.put("tenantId", "101");
        try {
            assertThatCode(() -> guard.beforeQuery(mock(Executor.class), read, parameters, RowBounds.DEFAULT,
                    null, read.getBoundSql(parameters))).doesNotThrowAnyException();
        } finally {
            MDC.remove("tenantId");
        }
    }

    @Test
    void nativeTaskUpdatesRetainExactVersionRevisionAttemptAndNativeEvidenceFences() throws Exception {
        var configuration = operationalMappers();
        var task = DdcPublishTaskBO.builder().id(9L).version(7L).changeId("change")
                .configItemId(5L).targetVersion(2L).attemptCount(1).build();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", 101L);
        parameters.put("task", task);
        parameters.put("evidence", JsonNodeFactory.instance.objectNode());
        parameters.put("nativeStatus", "PLATFORM_APPLIED");
        parameters.put("failureStage", null);
        parameters.put("changedAt", LocalDateTime.of(2026, 11, 19, 0, 0));
        parameters.put("actor", "native-publisher");
        parameters.put("now", Instant.parse("2026-11-19T00:00:00Z"));
        parameters.put("operator", "operator");
        for (String operation : List.of("updateNativeEvidence", "resetNativeForRetry")) {
            String sql = configuration.getMappedStatement(BASE + "dao.DdcPublishTaskMapper." + operation)
                    .getBoundSql(parameters).getSql().toLowerCase(Locale.ROOT);
            assertThat(sql).contains("tenant_id = ?", "id = ?", "change_id = ?", "config_id = ?", "target_version = ?",
                    "version = ?", "attempt_count = ?", "deleted_at is null", "platform_evidence is not null", "version = version + 1");
        }
    }

    @Test
    void historyRepositoryUsesOnlyTheMappedTenantAndCannotObtainAnIdOrWriteCapability() {
        var mapper = mock(DdcPublishAckMapper.class);
        var converter = mock(DdcPublishAckConverter.class);
        var row = DdcPublishAckPO.builder().id(9L).tenantId(101L).changeId("change").build();
        var archived = DdcPublishAckBO.builder().id(9L).changeId("change").ackStatus("SUCCESS").build();
        when(mapper.selectByChangeId(101L, "change")).thenReturn(List.of(row));
        when(converter.toTargetList(List.of(row))).thenReturn(List.of(archived));
        var repository = new DdcPublishAckRepositoryImpl(mapper, converter);
        MDC.put("tenantId", "101");
        try {
            assertThat(repository.findByChangeId("change")).containsExactly(archived);
        } finally {
            MDC.remove("tenantId");
        }
        verify(mapper).selectByChangeId(101L, "change");
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
