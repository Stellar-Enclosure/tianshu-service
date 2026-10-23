package top.egon.cola.component.tianshu.admin.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nPageMapper;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nPageConverterImpl;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcI18nPageRepositoryImpl;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcI18nPageRepositoryContractTest {
    private static final DdcI18nScope SCOPE = new DdcI18nScope("retail", "main", "prod", "shop");
    private static final String MAPPER = "top.egon.cola.component.tianshu.admin.dao.DdcI18nPageMapper.";

    @AfterEach void cleanup() { MDC.clear(); }

    @Test void everyQueryUsesExplicitTenantAndActiveRowFiltersOnLogicalTables() throws Exception {
        var configuration = parse();
        var parameters = Map.<String, Object>of("tenantId", 101L, "id", 9L, "scope", SCOPE,
                "pageCode", "orders", "offset", 0L, "limit", 20);
        for (String statement : new String[]{"selectPageById", "selectByPageCode", "selectPage", "countPage",
                "lockBiz", "lockNamespace", "lockEnv", "lockApp", "lockBinding"}) {
            String sql = configuration.getMappedStatement(MAPPER + statement).getBoundSql(parameters).getSql()
                    .replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
            assertThat(sql).as(statement).contains("tenant_id = ?", "deleted_at is null").doesNotContain("_t0", "101", "retail");
        }
        String summary = configuration.getMappedStatement(MAPPER + "selectPage").getBoundSql(parameters).getSql();
        assertThat(summary).doesNotContain("draft_translations", "published_translations").contains("LIMIT ? OFFSET ?");
    }

    @Test void repositoryBindsTrustedTenantRatherThanCallerDataAndRejectsMissingContext() {
        var mapper = mock(DdcI18nPageMapper.class);
        var repository = new DdcI18nPageRepositoryImpl(mapper, new DdcI18nPageConverterImpl(), null);
        MDC.put("tenantId", "101");
        repository.findById(9L);
        repository.findByPageCode(SCOPE, "orders");
        verify(mapper).selectPageById(101L, 9L);
        verify(mapper).selectByPageCode(101L, SCOPE, "orders");
        MDC.put("tenantId", "102");
        repository.findById(9L);
        verify(mapper).selectPageById(102L, 9L);
        MDC.remove("tenantId");
        assertThatThrownBy(() -> repository.findById(9L)).isInstanceOf(IllegalStateException.class);
    }

    private Configuration parse() throws Exception {
        String resource = "mybatis/mapper/tianshu/DdcI18nPageMapper.xml";
        var configuration = new Configuration();
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
