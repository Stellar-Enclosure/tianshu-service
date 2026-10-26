package top.egon.cola.component.tianshu.admin.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DdcCatalogMetadataRepositoryContractTest {
    @Test void activationAndHumanQueriesUseTheTrustedTenantAndExactScopeAndVersion() throws Exception {
        var parameters = Map.<String, Object>of("tenantId", 101L, "catalogScopeId", 501L, "activationVersion", 9L,
                "projectionId", 901L, "pageId", 701L, "operationId", "operation");
        for (var statement : Map.of("DdcCatalogActivationProjectionMapper", "findByVersion", "DdcCatalogActivationProducerMapper", "findByProjection",
                "DdcI18nPublishedOverrideMapper", "findByOperation").entrySet()) {
            String sql = sql(statement.getKey(), statement.getValue(), parameters);
            assertThat(sql).contains("tenant_id=?", "catalog_scope_id=?").doesNotContain("_t0", "501", "'operation'");
        }
    }

    @Test void humanHistoryIsSelectedOnlyForKeysInTheRequestedSourceAndItsReviewedDomain() throws Exception {
        String sql = sql("DdcI18nPublishedOverrideMapper", "findApplicableEntries", Map.of("tenantId", 101L, "catalogScopeId", 501L, "projectionId", 901L, "pageCode", "orders", "offset", 0L, "limit", 64));
        assertThat(sql).contains("a.activation_projection_id=?", "m.tenant_id=?", "m.catalog_scope_id=?", "jsonb_exists(previous.reviewed_source_hashes_json::jsonb, m.message_key)",
                "previous.publication_revision <= head.published_version", "max(previous.publication_revision)", "'matching'",
                "previous.reviewed_source_hashes_json::jsonb ->> m.message_key=m.source_hash",
                "m.deleted_at is null", "h.deleted_at is null", "head.deleted_at is null", "previous.deleted_at is null", "jsonb_path_query", "limit ? offset ?")
                .doesNotContain("draft_translations", "_t0", "orders");
    }

    @Test void resourceIndexNeverSelectsAnUnversionedOrCallerChosenTable() throws Exception {
        var filter = new DdcResourceMetadataQuery("backend", "RESOURCE", "API", "ACTIVE", "order:read", "orders");
        var parameters = Map.<String, Object>of("tenantId", 101L, "catalogScopeId", 501L, "projectionId", 901L, "filter", filter, "offset", 0L, "limit", 20);
        String sql = sql("DdcResourceMetadataProjectionMapper", "search", parameters);
        assertThat(sql).contains("r.tenant_id=?", "r.catalog_scope_id=?", "a.activation_projection_id=?", "r.producer_id=?", "limit ? offset ?")
                .doesNotContain("backend", "order:read", "orders", "_t0");
        assertThat(sql("DdcCatalogActivationProducerMapper", "countSelectionConflicts", parameters))
                .contains("group by r.metadata_kind, lower(r.definition_key)", "group by lower(page_code), lower(message_key)", "count(distinct default_locale)");
    }

    private String sql(String mapper, String statement, Map<String, Object> parameters) throws Exception {
        String resource = "mybatis/mapper/tianshu/" + mapper + ".xml";
        var configuration = new Configuration();
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement("top.egon.cola.component.tianshu.admin.dao." + mapper + "." + statement)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
