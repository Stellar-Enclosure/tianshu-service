package top.egon.cola.component.tianshu.admin.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nImportReceiptMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcMetadataScopeMapper;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nImportReceiptConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcMetadataScopeConverterImpl;
import top.egon.cola.component.tianshu.admin.model.po.DdcMetadataScopePO;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcI18nImportReceiptRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcMetadataScopeRepositoryImpl;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DdcI18nSourceImportRepositoryContractTest {
    private static final String NAMESPACE = "top.egon.cola.component.tianshu.admin.dao.";

    @AfterEach void cleanup() { MDC.clear(); }

    @Test void everySelectUsesTrustedTenantAndCatalogScopeOnLogicalTables() throws Exception {
        var configuration = configuration();
        Map<String, Object> parameters = Map.of("tenantId", 101L, "catalogScopeId", 501L, "producerId", "order-admin",
                "bindingDigest", "a".repeat(64), "sourceVersion", "b".repeat(64), "sourceId", 701L,
                "operationId", "32fb3f12-7ce5-4e19-8665-a5db0d9c1baa");
        for (String statement : List.of("DdcMetadataScopeMapper.selectByCatalogScopeId", "DdcMetadataScopeMapper.lockByCatalogScopeId",
                "DdcMetadataScopeMapper.lockEnabledTarget", "DdcSourceImportGrantMapper.findEnabled", "DdcSourceImportGrantMapper.lockEnabled",
                "DdcI18nSourceVersionMapper.findByVersion", "DdcResourceMetadataProjectionMapper.findBySource",
                "DdcI18nSourceMessageMapper.findBySource", "DdcI18nImportReceiptMapper.findByOperation")) {
            var bound = configuration.getMappedStatement(NAMESPACE + statement).getBoundSql(parameters);
            String sql = bound.getSql().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
            assertThat(sql).as(statement).contains("tenant_id=?", "catalog_scope_id=?", "deleted_atisnull")
                    .doesNotContain("_t0", "order-admin", "32fb3f12");
            assertThat(bound.getParameterMappings()).extracting("property").contains("tenantId", "catalogScopeId");
        }
    }

    @Test void exactParentBindingsAreLockedSeparatelyFromScopeSerialization() throws Exception {
        var configuration = configuration();
        Map<String, Object> parameters = Map.of("tenantId", 101L, "catalogScopeId", 501L);
        String serialize = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper.lockByCatalogScopeId")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
        String parents = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper.lockEnabledTarget")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
        assertThat(serialize).contains("FROM ddc_metadata_scope", "FOR UPDATE").doesNotContain("JOIN");
        assertThat(parents).contains("a.id=s.app_id", "n.id=s.namespace_id", "nb.id=s.binding_id", "n.namespace_code=s.namespace_code",
                "b.enabled=true", "e.enabled=true", "a.enabled=true", "n.enabled=true", "nb.enabled=true", "FOR SHARE");
    }

    @Test void repositoriesUseTheCurrentTrustedContextAndCannotPerformNakedOperationLookup() {
        DdcMetadataScopeMapper scopes = mock(DdcMetadataScopeMapper.class);
        DdcI18nImportReceiptMapper receipts = mock(DdcI18nImportReceiptMapper.class);
        var scopeRepository = new DdcMetadataScopeRepositoryImpl(scopes, new DdcMetadataScopeConverterImpl(), null, null);
        var receiptRepository = new DdcI18nImportReceiptRepositoryImpl(receipts, new DdcI18nImportReceiptConverterImpl(), null, null);
        when(scopes.lockByCatalogScopeId(101L, 501L)).thenReturn(DdcMetadataScopePO.builder().id(1L).tenantId(101L).catalogScopeId(501L).build());
        MDC.put("tenantId", "101");
        scopeRepository.lockEnabledTarget(501L);
        receiptRepository.findByOperation(501L, "order-admin", "operation");
        var order = inOrder(scopes);
        order.verify(scopes).lockByCatalogScopeId(101L, 501L);
        order.verify(scopes).lockEnabledTarget(101L, 501L);
        verify(receipts).findByOperation(101L, 501L, "order-admin", "operation");
        MDC.remove("tenantId");
        assertThatThrownBy(() -> receiptRepository.findByOperation(501L, "order-admin", "operation")).isInstanceOf(IllegalStateException.class);
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        for (String mapper : List.of("DdcMetadataScope", "DdcSourceImportGrant", "DdcI18nSourceVersion", "DdcResourceMetadataProjection", "DdcI18nSourceMessage", "DdcI18nImportReceipt")) {
            String resource = "mybatis/mapper/tianshu/" + mapper + "Mapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).isNotNull();
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
