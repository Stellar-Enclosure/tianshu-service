package top.egon.cola.component.tianshu.admin.integration;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.assertj.core.api.Assertions.*;
import static top.egon.cola.component.tianshu.admin.integration.DdcPermissionMetadataPostgresFixture.*;

/** Concurrency needs an explicit existing B0 scope; fixture import rows are never committed. */
class DdcI18nImportConcurrencyIT {
    @Test void scopeLockSerializesCompetingImportsAndRollbackLeavesNoPartialSourceOrReceipt() throws Exception {
        try (Connection first = open(); Connection second = open(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                long tenant = configured("TIANSHU_PERMISSION_METADATA_CONCURRENCY_TENANT_ID");
                long catalogScopeId = configured("TIANSHU_PERMISSION_METADATA_CONCURRENCY_SCOPE_ID");
                for (Connection connection : new Connection[]{first, second}) {
                    execute(connection, "SET LOCAL statement_timeout='5s'");
                    execute(connection, "SET LOCAL lock_timeout='3s'");
                }
                String producer = "concurrency-" + UUID.randomUUID();
                String sourceVersion = CanonicalJson.digest(Map.of("producer", producer));
                String operation = UUID.randomUUID().toString();
                lock(first, tenant, catalogScopeId);
                importRows(first, tenant, catalogScopeId, producer, sourceVersion, operation);
                CountDownLatch attempting = new CountDownLatch(1);
                var competing = executor.submit(() -> {
                    try {
                        attempting.countDown();
                        lock(second, tenant, catalogScopeId);
                        assertThat(count(second, tenant, catalogScopeId, operation)).isZero();
                        importRows(second, tenant, catalogScopeId, producer, sourceVersion, operation);
                        return count(second, tenant, catalogScopeId, operation);
                    } finally { second.rollback(); }
                });
                assertThat(attempting.await(2, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> competing.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                first.rollback();
                assertThat(competing.get(10, TimeUnit.SECONDS)).isEqualTo(1L);
                assertThat(count(first, tenant, catalogScopeId, operation)).isZero();
                try (var query = first.prepareStatement("SELECT COUNT(*) FROM ddc_i18n_source_version_t0 WHERE tenant_id=? AND catalog_scope_id=? AND producer_id=?")) {
                    query.setLong(1, tenant); query.setLong(2, catalogScopeId); query.setString(3, producer);
                    try (var rows = query.executeQuery()) { rows.next(); assertThat(rows.getLong(1)).isZero(); }
                }
            } finally { first.rollback(); second.rollback(); }
        }
    }

    private long configured(String name) {
        String value = System.getenv(name);
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalStateException("Concurrency IT requires the explicitly configured existing B0 scope: " + name);
        return Long.parseLong(value);
    }

    private void lock(Connection connection, long tenant, long scope) throws Exception {
        try (var query = connection.prepareStatement("SELECT catalog_scope_id FROM ddc_metadata_scope_t0 WHERE tenant_id=? AND catalog_scope_id=? FOR UPDATE")) {
            query.setLong(1, tenant); query.setLong(2, scope);
            try (var rows = query.executeQuery()) { if (!rows.next()) throw new IllegalStateException("Concurrency IT target B0 scope is missing"); }
        }
    }

    private void importRows(Connection connection, long tenant, long scope, String producer, String sourceVersion, String operation) throws Exception {
        long source = id(); long grant = id();
        insert(connection, "ddc_source_import_grant_t0", grant, tenant, values("catalog_scope_id", scope, "producer_id", producer,
                "issuer_uri", "https://identity.example.test/tenants/" + tenant, "client_id", "concurrency-fixture", "resource_uri", "https://metadata.example.test",
                "principal_binding_digest", "f".repeat(64), "enabled", true));
        insert(connection, "ddc_i18n_source_version_t0", source, tenant, values("catalog_scope_id", scope, "producer_id", producer, "schema_version", "1.0",
                "source_version", sourceVersion, "manifest_digest", sourceVersion, "policy_digest", POLICY, "metadata_digest", METADATA,
                "projection_digest", "e".repeat(64), "canonical_manifest_json", "{}", "resource_id_map_json", "{}", "permission_id_map_json", "{}",
                "resource_count", 0, "permission_count", 0, "message_count", 0));
        insert(connection, "ddc_i18n_import_receipt_t0", id(), tenant, values("catalog_scope_id", scope, "operation_id", operation, "producer_id", producer,
                "producer_version", 1L, "source_id", source, "source_version", sourceVersion, "manifest_digest", sourceVersion, "policy_digest", POLICY,
                "metadata_digest", METADATA, "request_digest", "7".repeat(64), "grant_id", grant, "grant_version", 0L,
                "publisher_issuer", "https://identity.example.test/tenants/" + tenant, "publisher_client_id", "concurrency-fixture", "response_json", "{}"));
    }

    private long count(Connection connection, long tenant, long scope, String operation) throws Exception {
        try (var query = connection.prepareStatement("SELECT COUNT(*) FROM ddc_i18n_import_receipt_t0 WHERE tenant_id=? AND catalog_scope_id=? AND operation_id=?")) {
            query.setLong(1, tenant); query.setLong(2, scope); query.setString(3, operation);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }
}
