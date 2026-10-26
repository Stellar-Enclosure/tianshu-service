package top.egon.cola.component.tianshu.admin.bootstrap;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Privileged, explicit B0 transaction against the current single-primary t0 topology. Never registered as a Spring bean. */
final class DdcPermissionMetadataBootstrapRepository {
    private static final Set<String> TABLES = Set.of("ddc_biz", "ddc_env", "ddc_app", "ddc_namespace", "ddc_namespace_env_app",
            "ddc_metadata_scope", "ddc_source_import_grant", "ddc_operation_log");
    private final String schema;
    private final Clock clock;

    DdcPermissionMetadataBootstrapRepository(String schema, Clock clock) {
        if (schema == null || !schema.matches("[a-z][a-z0-9_]{0,62}")) DdcPermissionMetadataBootstrapCommand.invalid();
        this.schema = schema; this.clock = clock;
    }

    String initialize(Connection connection, DdcPermissionMetadataBootstrapCommand command, String digest, String expectedReceipt) throws SQLException {
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        try {
            // Serializes controlled initialization for a tenant; ordinary business writers still obey database uniqueness/CAS.
            try (var statement = connection.prepareStatement("SELECT pg_catalog.set_config('lock_timeout', '5s', true), pg_catalog.set_config('statement_timeout', '10s', true)")) {
                statement.execute();
            }
            try (var statement = connection.prepareStatement("SELECT pg_catalog.pg_advisory_xact_lock(?)")) {
                statement.setLong(1, -Long.parseLong(command.target().tenantId())); statement.execute();
            }
            String original = receipt(connection, command);
            if (original != null) {
                var parsed = DdcPermissionMetadataBootstrapCli.object(original);
                if (!digest.equals(parsed.get("commandDigest")) || !command.operationId().equals(parsed.get("operationId"))
                        || expectedReceipt != null && !expectedReceipt.equals(original)) conflict();
                connection.commit();
                return original;
            }
            if (expectedReceipt != null) conflict();
            Instant now = clock.instant();
            var target = command.target();
            var ids = command.ids();
            ensure(connection, command, "ddc_biz", ids.business(), Map.of("biz_code", target.businessCode()),
                    Map.of("public_id", command.ddcBusinessId(), "enabled", true), Map.of("biz_name", target.businessCode()), true, now);
            ensure(connection, command, "ddc_env", ids.environment(), Map.of("env_code", target.environment()),
                    Map.of("enabled", true), Map.of("public_id", publicId(command, "environment")), true, now);
            ensure(connection, command, "ddc_app", ids.application(), Map.of("biz_code", target.businessCode(), "app_code", target.applicationCode()),
                    Map.of("public_id", command.ddcApplicationId(), "enabled", true), Map.of("app_name", target.applicationCode()), true, now);
            ensure(connection, command, "ddc_namespace", ids.namespace(), Map.of("biz_code", target.businessCode(), "namespace_code", target.namespaceCode()),
                    Map.of("enabled", true), Map.of("public_id", publicId(command, "namespace"), "namespace", target.namespaceCode()), true, now);
            ensure(connection, command, "ddc_namespace_env_app", ids.binding(), Map.of("namespace_id", number(ids.namespace()),
                            "app_id", number(ids.application()), "env_code", target.environment()), Map.of("enabled", true),
                    Map.of("public_id", publicId(command, "binding")), true, now);
            ensure(connection, command, "ddc_metadata_scope", ids.metadataScope(), Map.of("catalog_scope_id", number(target.catalogScopeId())),
                    Map.of("biz_code", target.businessCode(), "namespace_id", number(ids.namespace()), "namespace_code", target.namespaceCode(),
                            "env_code", target.environment(), "app_id", number(ids.application()), "app_code", target.applicationCode(), "binding_id", number(ids.binding())),
                    Map.of(), false, now);
            for (var grant : command.sourceImportGrants()) {
                ensure(connection, command, "ddc_source_import_grant", grant.id(), Map.of("catalog_scope_id", number(target.catalogScopeId()),
                                "producer_id", grant.producerId(), "principal_binding_digest", grant.bindingDigest()),
                        Map.of("issuer_uri", grant.issuer(), "client_id", grant.clientId(), "resource_uri", grant.resourceUri(), "enabled", true),
                        Map.of(), false, now);
            }
            String result = result(command, digest, now);
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("public_id", "pac-b0:" + command.operationId()); audit.put("operation_type", "PAC_BOOTSTRAP_METADATA_B0");
            audit.put("operator", command.actorId()); audit.put("operation_content", result); audit.put("created_at", LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            audit.put("biz_code", target.businessCode()); audit.put("app_code", target.applicationCode());
            audit.put("env", target.environment()); audit.put("namespace", target.namespaceCode());
            insert(connection, command, "ddc_operation_log", ids.audit(), audit, now);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException failure) {
            try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    private String receipt(Connection connection, DdcPermissionMetadataBootstrapCommand command) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT operation_type, operation_content, deleted_at FROM " + table("ddc_operation_log")
                + " WHERE tenant_id=? AND public_id=? FOR UPDATE")) {
            statement.setLong(1, number(command.target().tenantId())); statement.setString(2, "pac-b0:" + command.operationId());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                if (!"PAC_BOOTSTRAP_METADATA_B0".equals(rows.getString(1)) || rows.getObject(3) != null) conflict();
                String result = rows.getString(2);
                if (result == null || result.length() > 1024 * 1024 || rows.next()) conflict();
                return result;
            }
        }
    }

    private void ensure(Connection connection, DdcPermissionMetadataBootstrapCommand command, String table, String id,
                        Map<String, Object> keys, Map<String, Object> immutable, Map<String, Object> initial,
                        boolean businessTimestamps, Instant now) throws SQLException {
        Map<String, Object> expected = new LinkedHashMap<>(keys); expected.putAll(immutable);
        List<String> columns = new ArrayList<>(expected.keySet());
        String predicate = String.join(" AND ", keys.keySet().stream().map(key -> key + "=?").toList());
        String sql = "SELECT id, deleted_at, " + String.join(", ", columns) + " FROM " + table(table)
                + " WHERE tenant_id=? AND " + predicate + " FOR UPDATE";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, number(command.target().tenantId()));
            int index = 2;
            for (Object value : keys.values()) statement.setObject(index++, value);
            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    if (rows.getLong(1) != number(id) || rows.getObject(2) != null) conflict();
                    for (int position = 0; position < columns.size(); position++) {
                        if (!expected.get(columns.get(position)).equals(rows.getObject(position + 3))) conflict();
                    }
                    if (rows.next()) conflict();
                    return;
                }
            }
        }
        Map<String, Object> values = new LinkedHashMap<>(expected); values.putAll(initial);
        if (businessTimestamps) {
            values.put("created_at", LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            values.put("updated_at", LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        }
        insert(connection, command, table, id, values, now);
    }

    private void insert(Connection connection, DdcPermissionMetadataBootstrapCommand command, String table, String id,
                        Map<String, Object> values, Instant now) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", number(id)); row.put("tenant_id", number(command.target().tenantId()));
        row.put("create_user_id", command.actorId()); row.put("create_time", Timestamp.from(now));
        row.put("update_user_id", command.actorId()); row.put("update_time", Timestamp.from(now));
        row.put("deleted_at", null); row.put("version", 0L); row.putAll(values);
        String sql = "INSERT INTO " + table(table) + " (" + String.join(", ", row.keySet()) + ") VALUES ("
                + String.join(",", java.util.Collections.nCopies(row.size(), "?")) + ")";
        try (var statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object value : row.values()) statement.setObject(index++, value);
            if (statement.executeUpdate() != 1) conflict();
        }
    }

    private String result(DdcPermissionMetadataBootstrapCommand command, String digest, Instant now) {
        var target = command.target(); var ids = command.ids();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("stage", "B0"); result.put("operationId", command.operationId());
        result.put("commandDigest", digest); result.put("completedAt", now.toString());
        result.put("target", Map.of("catalogScopeId", target.catalogScopeId(), "tenantId", target.tenantId(),
                "businessCode", target.businessCode(), "applicationCode", target.applicationCode(), "namespaceCode", target.namespaceCode(), "environment", target.environment()));
        result.put("ddcBusinessId", command.ddcBusinessId()); result.put("ddcApplicationId", command.ddcApplicationId());
        result.put("facts", Map.of("businessId", ids.business(), "environmentId", ids.environment(), "applicationId", ids.application(),
                "namespaceId", ids.namespace(), "bindingId", ids.binding(), "metadataScopeId", ids.metadataScope(),
                "sourceImportGrants", command.sourceImportGrants().stream().map(grant -> Map.of("id", grant.id(), "producerId", grant.producerId(), "bindingDigest", grant.bindingDigest())).toList()));
        return CanonicalJson.write(result);
    }

    private String table(String name) {
        if (!TABLES.contains(name)) throw new IllegalArgumentException("METADATA_BOOTSTRAP_TABLE_INVALID");
        return '"' + schema + '"' + "." + name + "_t0";
    }
    private long number(String value) { return DdcPermissionMetadataBootstrapCommand.id(value); }
    private String publicId(DdcPermissionMetadataBootstrapCommand command, String kind) {
        return java.util.UUID.nameUUIDFromBytes((command.target().tenantId() + ":" + command.target().catalogScopeId() + ":" + kind)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    private static void conflict() { throw new IllegalStateException("METADATA_BOOTSTRAP_EXISTING_FACT_CONFLICT"); }
}
