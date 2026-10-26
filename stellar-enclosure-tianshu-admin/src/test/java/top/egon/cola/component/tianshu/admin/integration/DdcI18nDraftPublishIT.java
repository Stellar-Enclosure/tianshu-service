package top.egon.cola.component.tianshu.admin.integration;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static top.egon.cola.component.tianshu.admin.integration.DdcPermissionMetadataPostgresFixture.*;

/** Executes the real history-selection SQL against an explicitly enabled existing PostgreSQL target. */
class DdcI18nDraftPublishIT {
    @Test void newerActivationRemovingAKeyDoesNotRemoveAnOlderRunningVersionsHumanTranslation() throws Exception {
        try (Connection connection = open()) {
            try {
                var scope = scope(connection, id());
                long oldSource = source(connection, scope);
                message(connection, scope, oldSource, SOURCE, "title", "d".repeat(64));
                long oldProjection = descriptor(connection, scope, oldSource);
                long page = page(connection, scope);
                publish(connection, scope, page, publication(connection, scope, page, 0L));
                long nextSource = nextSource(connection, scope);
                message(connection, scope, nextSource, "e".repeat(64), "next", "f".repeat(64));
                long nextProjection = nextDescriptor(connection, scope, nextSource);
                String nextHashes = "{\"next\":\"" + "f".repeat(64) + "\"}";
                String nextTranslations = "{\"en-US\":{\"next\":\"New human\"}}";
                assertThat(execute(connection, "UPDATE ddc_i18n_page_t0 SET draft_translations=?,draft_source_hashes=?,draft_activation_version=2,version=version+1 WHERE id=? AND version=1",
                        nextTranslations, nextHashes, page)).isEqualTo(1);
                long snapshot = snapshot(connection, scope, page, 2, 2, 2, nextTranslations, nextHashes);
                publish(connection, scope, page, snapshot);
                var oldEntries = entries(connection, scope, oldProjection);
                assertThat(oldEntries).hasSize(2).allSatisfy(entry -> assertThat(entry).contains("title", "订单"));
                var currentEntries = entries(connection, scope, nextProjection);
                assertThat(currentEntries).hasSize(2).allSatisfy(entry -> assertThat(entry).contains("next", "New human").doesNotContain("title"));
            } finally { connection.rollback(); }
        }
    }

    @Test void emptyPublicationInTheReviewedDomainClearsOverridesAndStaleDraftCasCannotChangeIt() throws Exception {
        try (Connection connection = open()) {
            try {
                var scope = scope(connection, id()); long source = source(connection, scope);
                message(connection, scope, source, SOURCE, "title", "d".repeat(64));
                long projection = descriptor(connection, scope, source); long page = page(connection, scope);
                publish(connection, scope, page, publication(connection, scope, page, 0L));
                assertThat(execute(connection, "UPDATE ddc_i18n_page_t0 SET draft_translations='{}',version=version+1 WHERE id=? AND version=1", page)).isEqualTo(1);
                publish(connection, scope, page, snapshot(connection, scope, page, 2, 2, 1, "{}", SOURCE_HASHES));
                assertThat(entries(connection, scope, projection)).hasSize(2).allSatisfy(entry -> assertThat(entry).endsWith("|{}"));
                assertThat(execute(connection, "UPDATE ddc_i18n_page_t0 SET draft_translations=?,version=version+1 WHERE id=? AND version=1", TRANSLATIONS, page)).isZero();
                assertThat(entries(connection, scope, projection)).allSatisfy(entry -> assertThat(entry).endsWith("|{}"));
            } finally { connection.rollback(); }
        }
    }

    @Test void anInsertedSnapshotCannotAffectRuntimeUntilItsPublicationHeadCommits() throws Exception {
        try (Connection connection = open()) {
            try {
                var scope = scope(connection, id()); long source = source(connection, scope);
                message(connection, scope, source, SOURCE, "title", "d".repeat(64));
                long projection = descriptor(connection, scope, source); long page = page(connection, scope);
                publication(connection, scope, page, 0L);
                assertThat(entries(connection, scope, projection)).isEmpty();
            } finally { connection.rollback(); }
        }
    }

    @Test void perKeyProjectionPreservesLocaleAssociationsAndDoesNotReturnUnrelatedTranslations() throws Exception {
        try (Connection connection = open()) {
            try {
                var scope = scope(connection, id()); long source = source(connection, scope);
                String key = "title.primary";
                message(connection, scope, source, SOURCE, key, "d".repeat(64));
                long projection = descriptor(connection, scope, source); long page = page(connection, scope);
                var json = new com.fasterxml.jackson.databind.ObjectMapper();
                String translations = json.writeValueAsString(Map.of(
                        "en-US", Map.of(key, "Order [primary]", "unrelated", "not returned"),
                        "fr-FR", Map.of("other", "not returned"),
                        "zh-CN", Map.of(key, "订单\n主标题")));
                String hashes = json.writeValueAsString(Map.of(key, "d".repeat(64)));
                publish(connection, scope, page, snapshot(connection, scope, page, 1, 0, 1, translations, hashes));
                var results = entries(connection, scope, projection);
                assertThat(results).hasSize(2);
                for (String row : results) {
                    assertThat(json.readValue(row.split("\\|", 3)[2], Map.class))
                            .containsExactlyInAnyOrderEntriesOf(Map.of("en-US", "Order [primary]", "zh-CN", "订单\n主标题"));
                }
            } finally { connection.rollback(); }
        }
    }

    private void message(Connection connection, ScopeBO scope, long sourceId, String sourceVersion, String key, String hash) throws Exception {
        insert(connection, "ddc_i18n_source_message_t0", id(), scope.tenant(), values("catalog_scope_id", scope.id(), "source_id", sourceId,
                "producer_id", "metadata-it", "source_version", sourceVersion, "page_code", "orders", "message_key", key,
                "default_locale", "zh-CN", "source_hash", hash, "defaults_json", "{\"zh-CN\":\"源\"}", "placeholders_json", "[]"));
    }

    private long nextSource(Connection connection, ScopeBO scope) throws Exception {
        long id = id();
        insert(connection, "ddc_i18n_source_version_t0", id, scope.tenant(), values("catalog_scope_id", scope.id(), "producer_id", "metadata-it", "schema_version", "1.0",
                "source_version", "e".repeat(64), "manifest_digest", "e".repeat(64), "policy_digest", POLICY, "metadata_digest", METADATA,
                "projection_digest", "e".repeat(64), "canonical_manifest_json", "{}", "resource_id_map_json", "{}", "permission_id_map_json", "{}",
                "resource_count", 0, "permission_count", 0, "message_count", 1));
        return id;
    }

    private long nextDescriptor(Connection connection, ScopeBO scope, long sourceId) throws Exception {
        long id = id();
        insert(connection, "ddc_catalog_activation_projection_t0", id, scope.tenant(), values("catalog_scope_id", scope.id(), "activation_version", 2L,
                "descriptor_digest", "9".repeat(64), "descriptor_json", "{}", "producer_count", 1, "authority_resource_uri", "https://rbac.example.test"));
        insert(connection, "ddc_catalog_activation_producer_t0", id(), scope.tenant(), values("catalog_scope_id", scope.id(), "activation_projection_id", id,
                "producer_id", "metadata-it", "producer_version", 2L, "source_id", sourceId, "source_version", "e".repeat(64), "policy_digest", POLICY, "metadata_digest", METADATA));
        return id;
    }

    private long snapshot(Connection connection, ScopeBO scope, long pageId, long revision, long draftRevision, long activation,
            String translations, String hashes) throws Exception {
        long id = id();
        insert(connection, "ddc_i18n_published_override_t0", id, scope.tenant(), values("catalog_scope_id", scope.id(), "page_id", pageId, "page_code", "orders",
                "publication_revision", revision, "draft_revision", draftRevision, "based_on_activation_version", activation,
                "publish_operation_id", UUID.randomUUID().toString(), "request_digest", "9".repeat(64), "default_locale", "zh-CN",
                "translations_json", translations, "reviewed_source_hashes_json", hashes, "published_at", java.sql.Timestamp.from(java.time.Instant.now())));
        return id;
    }

    private List<String> entries(Connection connection, ScopeBO scope, long projectionId) throws Exception {
        String resource = "mybatis/mapper/tianshu/DdcI18nPublishedOverrideMapper.xml";
        var configuration = new Configuration();
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var parameters = Map.<String, Object>of("tenantId", scope.tenant(), "catalogScopeId", scope.id(), "projectionId", projectionId, "pageCode", "orders", "offset", 0L, "limit", 64);
        var bound = configuration.getMappedStatement("top.egon.cola.component.tianshu.admin.dao.DdcI18nPublishedOverrideMapper.findApplicableEntries").getBoundSql(parameters);
        // The fixture targets the migrated physical tables; application routing is tested separately.
        String sql = bound.getSql().replaceAll("\\b(ddc_[a-z_]+)\\b", "$1_t0");
        try (var query = connection.prepareStatement(sql)) {
            for (int index = 0; index < bound.getParameterMappings().size(); index++) {
                query.setObject(index + 1, parameters.get(bound.getParameterMappings().get(index).getProperty()));
            }
            List<String> entries = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) if (rows.getObject("publication_revision") != null) entries.add(rows.getString("message_key") + "|" + rows.getString("selection_kind") + "|" + rows.getString("translations_json"));
            }
            return entries;
        }
    }
}
