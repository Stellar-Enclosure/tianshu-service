package top.egon.cola.component.tianshu.admin.integration;

import org.junit.jupiter.api.Assumptions;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Uses only an explicitly provided, already migrated PostgreSQL target; every test rolls back its writes. */
final class DdcPermissionMetadataPostgresFixture {
    static final String SOURCE = "a".repeat(64);
    static final String POLICY = "b".repeat(64);
    static final String METADATA = "c".repeat(64);
    static final String SOURCE_HASHES = "{\"title\":\"" + "d".repeat(64) + "\"}";
    static final String TRANSLATIONS = "{\"zh-CN\":{\"title\":\"订单\"}}";
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 4096);

    private DdcPermissionMetadataPostgresFixture() { }

    static long id() { return IDS.incrementAndGet(); }

    static Connection open() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("TIANSHU_PERMISSION_METADATA_IT_ENABLED")),
                "requires an explicitly authorised existing PostgreSQL metadata target");
        String location = System.getenv("TIANSHU_PERMISSION_METADATA_IT_CONFIG");
        if (location == null || !Path.of(location).isAbsolute()) throw new IllegalStateException("Metadata IT requires an absolute protected configuration path");
        Path path = Path.of(location);
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Metadata IT configuration must be a regular protected file");
        }
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_"))) {
            throw new IllegalStateException("Metadata IT configuration must be owner-only");
        }
        var sources = new YamlPropertySourceLoader().load("metadata-it", new FileSystemResource(path));
        if (sources.size() != 1) throw new IllegalStateException("Metadata IT requires one configuration document");
        var properties = sources.getFirst();
        if (!Boolean.TRUE.equals(properties.getProperty("allowTransactionalWrites"))) {
            throw new IllegalStateException("Metadata IT target must explicitly allow transactional writes");
        }
        String jdbcUrl = required(properties.getProperty("jdbcUrl"));
        if (!jdbcUrl.startsWith("jdbc:postgresql:")) throw new IllegalStateException("Metadata IT requires PostgreSQL");
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(jdbcUrl);
        dataSource.setUser(required(properties.getProperty("username")));
        dataSource.setPassword(required(properties.getProperty("password")));
        Connection connection = dataSource.getConnection();
        try {
            connection.setSchema("public");
            try (var statement = connection.prepareStatement("SELECT current_database(), current_schema()"); var rows = statement.executeQuery()) {
                if (!rows.next() || !required(properties.getProperty("expectedDatabase")).equals(rows.getString(1))
                        || !"public".equals(rows.getString(2))) throw new IllegalStateException("Metadata IT target identity mismatch");
            }
            connection.setAutoCommit(false);
            return connection;
        } catch (Exception failure) {
            connection.close();
            throw failure;
        }
    }

    static ScopeBO scope(Connection connection, long tenant) throws SQLException {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String business = "metadata-it-" + suffix;
        String appCode = "app-" + suffix;
        String namespace = "space-" + suffix;
        String environment = "test-" + suffix.substring(0, 12);
        long businessId = id();
        long environmentId = id();
        long appId = id();
        long namespaceId = id();
        long bindingId = id();
        long scopeId = id();
        insert(connection, "ddc_biz_t0", businessId, tenant, values("public_id", UUID.randomUUID().toString(),
                "biz_code", business, "biz_name", "Metadata IT", "enabled", true,
                "created_at", java.sql.Timestamp.from(java.time.Instant.now()), "updated_at", java.sql.Timestamp.from(java.time.Instant.now())));
        insert(connection, "ddc_env_t0", environmentId, tenant, values("public_id", UUID.randomUUID().toString(),
                "env_code", environment, "enabled", true, "created_at", java.sql.Timestamp.from(java.time.Instant.now()),
                "updated_at", java.sql.Timestamp.from(java.time.Instant.now())));
        insert(connection, "ddc_app_t0", appId, tenant, values("public_id", UUID.randomUUID().toString(), "biz_code", business,
                "app_code", appCode, "app_name", "Metadata IT", "enabled", true,
                "created_at", java.sql.Timestamp.from(java.time.Instant.now()), "updated_at", java.sql.Timestamp.from(java.time.Instant.now())));
        insert(connection, "ddc_namespace_t0", namespaceId, tenant, values("public_id", UUID.randomUUID().toString(),
                "biz_code", business, "namespace", namespace, "namespace_code", namespace, "enabled", true,
                "created_at", java.sql.Timestamp.from(java.time.Instant.now()), "updated_at", java.sql.Timestamp.from(java.time.Instant.now())));
        insert(connection, "ddc_namespace_env_app_t0", bindingId, tenant, values("public_id", UUID.randomUUID().toString(),
                "namespace_id", namespaceId, "app_id", appId, "env_code", environment, "enabled", true,
                "created_at", java.sql.Timestamp.from(java.time.Instant.now()), "updated_at", java.sql.Timestamp.from(java.time.Instant.now())));
        insert(connection, "ddc_metadata_scope_t0", id(), tenant, values("catalog_scope_id", scopeId, "biz_code", business,
                "namespace_id", namespaceId, "namespace_code", namespace, "env_code", environment,
                "app_id", appId, "app_code", appCode, "binding_id", bindingId));
        return new ScopeBO(tenant, scopeId, business, namespaceId, appCode, environment);
    }

    static long source(Connection connection, ScopeBO scope) throws SQLException {
        long id = id();
        insert(connection, "ddc_i18n_source_version_t0", id, scope.tenant(), values("catalog_scope_id", scope.id(),
                "producer_id", "metadata-it", "schema_version", "1.0", "source_version", SOURCE,
                "policy_digest", POLICY, "metadata_digest", METADATA, "manifest_digest", SOURCE,
                "projection_digest", "e".repeat(64), "canonical_manifest_json", "{}", "resource_id_map_json", "{}",
                "permission_id_map_json", "{}", "resource_count", 0, "permission_count", 0, "message_count", 0));
        return id;
    }

    static long grant(Connection connection, ScopeBO scope) throws SQLException {
        long id = id();
        insert(connection, "ddc_source_import_grant_t0", id, scope.tenant(), values("catalog_scope_id", scope.id(),
                "producer_id", "metadata-it", "issuer_uri", "https://identity.example.test/tenants/1", "client_id", "rbac-publisher",
                "resource_uri", "https://metadata.example.test", "principal_binding_digest", "f".repeat(64), "enabled", true));
        return id;
    }

    static void receipt(Connection connection, ScopeBO scope, long sourceId, long grantId, String metadataDigest) throws SQLException {
        insert(connection, "ddc_i18n_import_receipt_t0", id(), scope.tenant(), values("catalog_scope_id", scope.id(),
                "operation_id", UUID.randomUUID().toString(), "producer_id", "metadata-it", "producer_version", 1L,
                "source_id", sourceId, "source_version", SOURCE, "manifest_digest", SOURCE, "policy_digest", POLICY,
                "metadata_digest", metadataDigest, "request_digest", "7".repeat(64), "grant_id", grantId, "grant_version", 0L,
                "publisher_issuer", "https://identity.example.test/tenants/1", "publisher_client_id", "rbac-publisher", "response_json", "{}"));
    }

    static long descriptor(Connection connection, ScopeBO scope, long sourceId) throws SQLException {
        long id = id();
        insert(connection, "ddc_catalog_activation_projection_t0", id, scope.tenant(), values("catalog_scope_id", scope.id(),
                "activation_version", 1L, "descriptor_digest", "8".repeat(64), "descriptor_json", "{}",
                "producer_count", 1, "authority_resource_uri", "https://rbac.example.test"));
        insert(connection, "ddc_catalog_activation_producer_t0", id(), scope.tenant(), values("catalog_scope_id", scope.id(),
                "activation_projection_id", id, "producer_id", "metadata-it", "producer_version", 1L,
                "source_id", sourceId, "source_version", SOURCE, "policy_digest", POLICY, "metadata_digest", METADATA));
        return id;
    }

    static long page(Connection connection, ScopeBO scope) throws SQLException {
        long id = id();
        insert(connection, "ddc_i18n_page_t0", id, scope.tenant(), values("biz_code", scope.business(), "namespace_id", scope.namespaceId(),
                "env_code", scope.environment(), "app_code", scope.appCode(), "page_code", "orders", "name", "Orders",
                "default_locale", "zh-CN", "draft_translations", TRANSLATIONS, "catalog_scope_id", scope.id(),
                "draft_activation_version", 1L, "draft_source_hashes", SOURCE_HASHES));
        return id;
    }

    static long publication(Connection connection, ScopeBO scope, long pageId, long draftRevision) throws SQLException {
        long id = id();
        insert(connection, "ddc_i18n_published_override_t0", id, scope.tenant(), values("catalog_scope_id", scope.id(),
                "page_id", pageId, "page_code", "orders", "publication_revision", 1L, "draft_revision", draftRevision,
                "based_on_activation_version", 1L, "publish_operation_id", UUID.randomUUID().toString(),
                "request_digest", "9".repeat(64), "default_locale", "zh-CN", "translations_json", TRANSLATIONS,
                "reviewed_source_hashes_json", SOURCE_HASHES, "published_at", java.sql.Timestamp.from(java.time.Instant.now())));
        return id;
    }

    static int publish(Connection connection, ScopeBO scope, long pageId, long publicationId) throws SQLException {
        String sql = "UPDATE ddc_i18n_page_t0 p SET current_override_id=h.id,published_version=h.publication_revision, "
                + "published_translations=h.translations_json,published_default_locale=h.default_locale, "
                + "published_source_hashes=h.reviewed_source_hashes_json,published_draft_revision=h.draft_revision, "
                + "published_operation_id=h.publish_operation_id,published_at=h.published_at,version=p.version+1 "
                + "FROM ddc_i18n_published_override_t0 h WHERE p.tenant_id=? AND p.catalog_scope_id=? AND p.id=? "
                + "AND h.tenant_id=p.tenant_id AND h.catalog_scope_id=p.catalog_scope_id AND h.id=?";
        return execute(connection, sql, scope.tenant(), scope.id(), pageId, publicationId);
    }

    static int execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            return statement.executeUpdate();
        }
    }

    static void insert(Connection connection, String table, long id, long tenant, Map<String, Object> fields) throws SQLException {
        String sql = "INSERT INTO " + table + " (id,tenant_id,create_user_id,create_time,update_user_id,update_time,version,"
                + String.join(",", fields.keySet()) + ") VALUES (?,?,'metadata-it',CURRENT_TIMESTAMP,'metadata-it',CURRENT_TIMESTAMP,0,"
                + String.join(",", fields.keySet().stream().map(ignored -> "?").toList()) + ")";
        Object[] values = new Object[fields.size() + 2];
        values[0] = id;
        values[1] = tenant;
        int index = 2;
        for (Object value : fields.values()) values[index++] = value;
        execute(connection, sql, values);
    }

    static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        return values;
    }

    private static String required(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalStateException("Metadata IT configuration is incomplete");
        return text;
    }

    record ScopeBO(long tenant, long id, String business, long namespaceId, String appCode, String environment) { }
}
