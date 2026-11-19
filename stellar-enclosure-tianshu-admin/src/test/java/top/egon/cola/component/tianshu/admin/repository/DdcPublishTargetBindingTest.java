package top.egon.cola.component.tianshu.admin.repository;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DdcPublishTargetBindingTest {

    private static final String MAPPER = "top.egon.cola.component.tianshu.admin.dao.DdcPublishTaskMapper.";

    @Test
    void nativeEvidenceAndRetryBindTheExactPublicationRevisionAndAttemptFence() throws Exception {
        var configuration = configuration();
        var task = DdcPublishTaskBO.builder().id(11L).version(23L).changeId("change-41")
                .configItemId(31L).targetVersion(43L).attemptCount(2).build();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", 101L);
        parameters.put("task", task);
        var evidence = JsonNodeFactory.instance.objectNode().put("schemaVersion", 1);
        evidence.putArray("targets");
        parameters.put("evidence", evidence);
        parameters.put("nativeStatus", "PLATFORM_APPLIED");
        parameters.put("failureStage", null);
        parameters.put("changedAt", LocalDateTime.of(2026, 11, 19, 0, 0));
        parameters.put("actor", "system:test");
        parameters.put("now", Instant.parse("2026-11-19T00:00:00Z"));
        parameters.put("operator", "operator");

        assertThat(configuration.hasStatement(MAPPER + "fenceDispatchTargets")).isFalse();
        for (String operation : List.of("updateNativeEvidence", "resetNativeForRetry")) {
            var statement = configuration.getMappedStatement(MAPPER + operation);
            var bound = statement.getBoundSql(parameters);
            String sql = bound.getSql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            assertThat(sql).contains("tenant_id = ? and id = ? and change_id = ?",
                    "config_id = ? and target_version = ?", "version = ? and attempt_count = ?",
                    "deleted_at is null", "platform_evidence is not null")
                    .doesNotContain("ddc_publish_ack", "lease_id", "target_count");
            assertThat(bound.getParameterMappings()).extracting(ParameterMapping::getProperty)
                    .containsSequence("tenantId", "task.id", "task.changeId", "task.configItemId",
                            "task.targetVersion", "task.version", "task.attemptCount");
            var prepared = mock(PreparedStatement.class);
            assertThatCode(() -> new DefaultParameterHandler(statement, parameters, bound).setParameters(prepared))
                    .doesNotThrowAnyException();
            verify(prepared).setLong(parameterIndex(bound, "tenantId"), 101L);
            verify(prepared).setLong(parameterIndex(bound, "task.id"), 11L);
            verify(prepared).setString(parameterIndex(bound, "task.changeId"), "change-41");
            verify(prepared).setLong(parameterIndex(bound, "task.configItemId"), 31L);
            verify(prepared).setLong(parameterIndex(bound, "task.targetVersion"), 43L);
            verify(prepared).setLong(parameterIndex(bound, "task.version"), 23L);
            verify(prepared).setInt(parameterIndex(bound, "task.attemptCount"), 2);
        }
    }

    @Test
    void nativeTargetBindingEvidenceUsesTheJsonbHandlerWithoutLosingBindingRevisions() throws Exception {
        var configuration = configuration();
        var evidence = JsonNodeFactory.instance.objectNode().put("schemaVersion", 1);
        var targets = evidence.putArray("targets");
        targets.addObject().put("bindingId", "reader-primary").put("bindingRevision", "7");
        targets.addObject().put("bindingId", "reader-secondary").put("bindingRevision", "9");
        var task = DdcPublishTaskBO.builder().id(11L).version(23L).changeId("change-41")
                .configItemId(31L).targetVersion(43L).attemptCount(2).build();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", 101L);
        parameters.put("task", task);
        parameters.put("evidence", evidence);
        parameters.put("nativeStatus", "PLATFORM_APPLIED");
        parameters.put("failureStage", null);
        parameters.put("changedAt", LocalDateTime.of(2026, 11, 19, 0, 0));
        parameters.put("actor", "system:test");
        parameters.put("now", Instant.parse("2026-11-19T00:00:00Z"));
        var statement = configuration.getMappedStatement(MAPPER + "updateNativeEvidence");
        var bound = statement.getBoundSql(parameters);
        var prepared = mock(PreparedStatement.class);

        assertThatCode(() -> new DefaultParameterHandler(statement, parameters, bound).setParameters(prepared))
                .doesNotThrowAnyException();
        var jsonb = ArgumentCaptor.forClass(PGobject.class);
        verify(prepared).setObject(eq(parameterIndex(bound, "evidence")), jsonb.capture(), eq(Types.OTHER));
        assertThat(jsonb.getValue().getType()).isEqualTo("jsonb");
        assertThat(jsonb.getValue().getValue()).isEqualTo(evidence.toString());
    }

    private Configuration configuration() throws Exception {
        var configuration = new Configuration();
        String resource = "mybatis/mapper/tianshu/DdcPublishTaskMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private int parameterIndex(BoundSql bound, String property) {
        var properties = bound.getParameterMappings().stream().map(ParameterMapping::getProperty).toList();
        assertThat(properties).containsOnlyOnce(property);
        return properties.indexOf(property) + 1;
    }
}
