package top.egon.cola.component.tianshu.admin.repository;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.model.config.DdcPublishTarget;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DdcPublishTargetBindingTest {
    @Test
    void targetFenceBindsTheCountAndTargetValues() throws Exception {
        var configuration = new Configuration();
        String resource = "mybatis/mapper/tianshu/DdcPublishTaskMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var task = DdcPublishTaskBO.builder().id(1L).version(0L).changeId("change").build();
        var parameters = Map.<String, Object>of("tenantId", 101L, "task", task,
                "targets", List.of(new DdcPublishTarget("one", "a"), new DdcPublishTarget("two", "b")),
                "targetCount", 2, "actor", "system:test", "now", Instant.parse("2026-10-28T00:00:00Z"));
        var statement = configuration.getMappedStatement("top.egon.cola.component.tianshu.admin.dao.DdcPublishTaskMapper.fenceDispatchTargets");
        var bound = statement.getBoundSql(parameters);
        var prepared = mock(PreparedStatement.class);
        assertThatCode(() -> new DefaultParameterHandler(statement, parameters, bound).setParameters(prepared)).doesNotThrowAnyException();
        verify(prepared, times(2)).setInt(anyInt(), eq(2));
        verify(prepared).setString(anyInt(), eq("one"));
        verify(prepared).setString(anyInt(), eq("two"));
    }
}
