package top.egon.cola.component.tianshu.admin.config;

import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlManifestBO;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only catalog checks for the cloud delivery migration on the physical PRIMARY. */
final class DdcCloudDeliveryReadyValidator {
    private static final String VERSION = "20261117_001";
    private static final String NATIVE_ROLLOUT_VERSION = "20261118_001";
    private static final String SCHEMA = "public";
    private static final String BOOT = "ddc_config_boot_binding_t0";
    private static final String RECEIPT = "ddc_config_process_receipt_t0";
    private static final String WORKLOAD_OBSERVATION = "ddc_config_workload_observation_t0";
    private static final String CONFIG_VERSION = "ddc_config_version_t0";
    private static final String SI_VERSION = "ddc_si_version_t0";
    private static final String VERSION_KEY = "tenant_id,config_id,config_revision";
    private static final String BOOT_KEY = "cluster_id,pod_uid,container_name,container_id";
    private static final Set<String> MODEL_COLUMNS = Set.of("id", "tenant_id", "create_user_id", "create_time",
            "update_user_id", "update_time", "deleted_at", "version");
    private static final Set<String> BOOT_COLUMNS = Set.of("cluster_id", "pod_uid", "container_name", "container_id",
            "boot_id", "workload_uid", "k8s_namespace");
    private static final Set<String> RECEIPT_COLUMNS = Set.of("config_id", "config_revision", "scope_json",
            "target_binding_id", "target_revision", "cluster_id", "k8s_namespace", "workload_uid", "pod_uid",
            "container_name", "container_id", "boot_id", "content_digest", "applied_revision", "applied_digest",
            "restart_digest", "result", "reason_code", "reason_message", "report_seq", "receipt_digest",
            "reported_at", "applied_at", "last_received_at");
    private static final Set<String> RECEIPT_NULLABLE = Set.of("deleted_at", "applied_revision", "applied_digest",
            "reason_code", "reason_message", "applied_at");
    private static final Map<String, String> RECEIPT_INDEXES = Map.of(
            "ix_config_process_receipt_target", VERSION_KEY
                    + ",target_binding_id,target_revision,cluster_id,k8s_namespace,workload_uid,pod_uid",
            "ix_config_process_receipt_retention", "tenant_id,last_received_at,id");
    private static final String BOOT_TRIGGER_FUNCTION = "ddc_config_boot_binding_reject_mutation";
    private static final String BOOT_TRIGGER_BODY = """
            BEGIN
                RAISE EXCEPTION 'TIANSHU_CONFIG_BOOT_BINDING_IMMUTABLE' USING ERRCODE = '55000';
            END;
            """;
    private static final Map<String, String> CHECKS = Map.ofEntries(
            Map.entry("ck_config_boot_binding_id_positive", "id > 0"),
            Map.entry("ck_config_boot_binding_technical_tenant", "tenant_id = 0"),
            Map.entry("ck_config_boot_binding_version_nonnegative", "version >= 0"),
            Map.entry("ck_config_boot_binding_not_deleted", "deleted_at IS NULL"),
            Map.entry("ck_config_process_receipt_id_positive", "id > 0"),
            Map.entry("ck_config_process_receipt_tenant_positive", "tenant_id > 0"),
            Map.entry("ck_config_process_receipt_version_nonnegative", "version >= 0"),
            Map.entry("ck_config_process_receipt_not_deleted", "deleted_at IS NULL"),
            Map.entry("ck_config_version_manifest_shape", manifestShape("manifest_json")),
            Map.entry("ck_si_version_dependency_manifest_shape", manifestShape("dependency_manifest_json")),
            Map.entry("ck_config_version_manifest_required", "manifest_json IS NOT NULL"),
            Map.entry("ck_si_version_dependency_manifest_required", "dependency_manifest_json IS NOT NULL"));

    private DdcCloudDeliveryReadyValidator() {
    }

    static boolean isIncludedIn(EgonColaDdlManifestBO manifest) {
        return manifest.scripts().getLast().version().compareTo(VERSION) >= 0;
    }

    static boolean includesNativeRollout(EgonColaDdlManifestBO manifest) {
        return manifest.scripts().getLast().version().compareTo(NATIVE_ROLLOUT_VERSION) >= 0;
    }

    static void validate(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        validateTable(metadata, BOOT, BOOT_COLUMNS, Set.of("deleted_at"));
        validateTable(metadata, RECEIPT, RECEIPT_COLUMNS, RECEIPT_NULLABLE);
        requireJsonb(metadata, CONFIG_VERSION, "manifest_json", true);
        requireJsonb(metadata, SI_VERSION, "dependency_manifest_json", true);
        requireJsonb(metadata, RECEIPT, "scope_json", false);
        validateConstraints(connection);
        validateReceiptIndexes(connection);
        validateBootTrigger(connection);
    }

    static void validateNativeRollout(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        validateTable(metadata, WORKLOAD_OBSERVATION, Set.of("target_binding_id", "target_revision", "cluster_id",
                "k8s_namespace", "workload_uid", "observed_at", "valid_until", "recorded_at", "view_json"),
                Set.of("deleted_at", "observed_at", "valid_until"));
        requireJsonb(metadata, WORKLOAD_OBSERVATION, "view_json", false);
        requireJsonb(metadata, "ddc_publish_task_t0", "platform_evidence", true);
        String constraints = """
                SELECT c.conname, c.contype, c.convalidated, c.condeferrable,
                       COALESCE((to_jsonb(c)->>'conenforced')::boolean, true),
                       COALESCE(i.indnullsnotdistinct, false)
                FROM pg_catalog.pg_constraint c
                LEFT JOIN pg_catalog.pg_index i ON i.indexrelid = c.conindid
                WHERE (c.conrelid = 'public.ddc_config_workload_observation_t0'::regclass
                        AND c.conname IN ('pk_ddc_config_workload_observation',
                            'uk_ddc_config_workload_observation_scope',
                            'ck_ddc_config_workload_observation_view',
                            'ck_ddc_config_workload_observation_expiry'))
                   OR (c.conrelid = 'public.ddc_publish_task_t0'::regclass
                        AND c.conname = 'ck_ddc_publish_task_platform_evidence')
                """;
        Map<String, String> requiredTypes = Map.of(
                "pk_ddc_config_workload_observation", "p",
                "uk_ddc_config_workload_observation_scope", "u",
                "ck_ddc_config_workload_observation_view", "c",
                "ck_ddc_config_workload_observation_expiry", "c",
                "ck_ddc_publish_task_platform_evidence", "c");
        Set<String> seen = new java.util.HashSet<>();
        try (var statement = connection.prepareStatement(constraints); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1);
                String type = rows.getString(2);
                require(type.equals(requiredTypes.get(name)) && rows.getBoolean(3) && !rows.getBoolean(4)
                        && rows.getBoolean(5) && (!"u".equals(type) || rows.getBoolean(6)) && seen.add(name), name);
            }
        }
        require(seen.equals(requiredTypes.keySet()), "native rollout constraints");
    }

    private static void validateTable(DatabaseMetaData metadata, String table, Set<String> fields, Set<String> nullable)
            throws SQLException {
        Map<String, Boolean> definitions = new HashMap<>();
        try (ResultSet columns = metadata.getColumns(null, SCHEMA, table, null)) {
            while (columns.next()) {
                String column = columns.getString("COLUMN_NAME");
                if (table.equals(columns.getString("TABLE_NAME"))) {
                    int nullability = columns.getInt("NULLABLE");
                    require(nullability != DatabaseMetaData.columnNullableUnknown, table + '.' + column);
                    definitions.put(column, nullability == DatabaseMetaData.columnNullable);
                }
            }
        }
        Map<String, Boolean> expected = new HashMap<>();
        MODEL_COLUMNS.forEach(column -> expected.put(column, nullable.contains(column)));
        fields.forEach(column -> expected.put(column, nullable.contains(column)));
        require(expected.equals(definitions), table + " columns");
    }

    private static void requireJsonb(DatabaseMetaData metadata, String table, String column, boolean nullable)
            throws SQLException {
        try (ResultSet rows = metadata.getColumns(null, SCHEMA, table, column)) {
            boolean matched = false;
            while (rows.next()) {
                if (table.equals(rows.getString("TABLE_NAME")) && column.equals(rows.getString("COLUMN_NAME"))) {
                    require(!matched && "jsonb".equals(rows.getString("TYPE_NAME")) && rows.getInt("NULLABLE")
                            == (nullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls), table + '.' + column);
                    matched = true;
                }
            }
            require(matched, table + '.' + column);
        }
    }

    private static void validateConstraints(Connection connection) throws SQLException {
        String sql = """
                /* tianshu cloud delivery constraints */
                SELECT t.relname,c.conname,c.contype,c.convalidated,c.condeferrable,
                       COALESCE((to_jsonb(c)->>'conenforced')::boolean,true),
                       (SELECT string_agg(a.attname,',' ORDER BY k.ordinality)
                        FROM unnest(c.conkey) WITH ORDINALITY k(attnum,ordinality)
                        JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum),
                       rn.nspname,rt.relname,
                       (SELECT string_agg(a.attname,',' ORDER BY k.ordinality)
                        FROM unnest(c.confkey) WITH ORDINALITY k(attnum,ordinality)
                        JOIN pg_catalog.pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.attnum),
                       c.confdeltype,pg_catalog.pg_get_expr(c.conbin,c.conrelid,true)
                FROM pg_catalog.pg_constraint c JOIN pg_catalog.pg_class t ON t.oid=c.conrelid
                JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_catalog.pg_class rt ON rt.oid=c.confrelid
                LEFT JOIN pg_catalog.pg_namespace rn ON rn.oid=rt.relnamespace
                WHERE n.nspname='public' AND t.relname IN (%s) AND c.contype IN ('p','u','f','c')
                """.formatted(quoted(Set.of(BOOT, RECEIPT, CONFIG_VERSION, SI_VERSION)));
        Map<String, ConstraintVO> definitions = new HashMap<>();
        try (var statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                definitions.put(rows.getString(1) + '.' + rows.getString(2), new ConstraintVO(rows.getString(3),
                        rows.getBoolean(4), rows.getBoolean(5), rows.getBoolean(6), rows.getString(7), rows.getString(8),
                        rows.getString(9), rows.getString(10), rows.getString(11), rows.getString(12)));
            }
        }
        requireKey(definitions, BOOT, "pk_config_boot_binding", "p", "id");
        requireKey(definitions, BOOT, "uk_config_boot_binding_incarnation", "u", BOOT_KEY);
        requireKey(definitions, RECEIPT, "pk_config_process_receipt", "p", "id");
        requireKey(definitions, RECEIPT, "uk_config_process_receipt_incarnation", "u", VERSION_KEY
                + ",target_binding_id,target_revision,cluster_id,pod_uid,container_id,boot_id");
        ConstraintVO foreign = requireKey(definitions, RECEIPT, "fk_config_process_receipt_version", "f", VERSION_KEY);
        require(SCHEMA.equals(foreign.targetSchema()) && CONFIG_VERSION.equals(foreign.targetTable())
                && VERSION_KEY.equals(foreign.targetColumns()) && "r".equals(foreign.deleteAction()),
                "fk_config_process_receipt_version");
        requireChecks(definitions, BOOT, "ck_config_boot_binding_", "id_positive", "technical_tenant",
                "version_nonnegative", "not_deleted", "identity_ascii", "namespace", "container_name");
        requireChecks(definitions, RECEIPT, "ck_config_process_receipt_", "id_positive", "tenant_positive",
                "version_nonnegative", "not_deleted", "revision_positive", "scope_shape", "identity_ascii",
                "namespace", "container_name", "digests", "result", "actual_pair", "result_shape", "receive_time");
        requireChecks(definitions, CONFIG_VERSION, "ck_config_version_manifest_", "shape", "required");
        requireChecks(definitions, SI_VERSION, "ck_si_version_dependency_manifest_", "shape", "required");
    }

    private static ConstraintVO requireKey(Map<String, ConstraintVO> definitions, String table, String name,
                                           String type, String columns) {
        ConstraintVO constraint = definitions.get(table + '.' + name);
        require(constraint != null && type.equals(constraint.type()) && constraint.validated() && constraint.enforced()
                && !constraint.deferrable() && columns.equals(constraint.columns()), name);
        return constraint;
    }

    private static void requireChecks(Map<String, ConstraintVO> definitions, String table, String prefix, String... suffixes) {
        for (String suffix : suffixes) {
            String name = prefix + suffix;
            ConstraintVO constraint = definitions.get(table + '.' + name);
            require(constraint != null && "c".equals(constraint.type()) && constraint.enforced() && !constraint.deferrable()
                    && (constraint.validated() || "required".equals(suffix)) && constraint.expression() != null
                    && constraint.columns() != null, name);
            String expected = CHECKS.get(name);
            if (expected != null) {
                require(normalizeSql(expected).equals(normalizeSql(constraint.expression())), name);
            }
        }
    }

    private static void validateReceiptIndexes(Connection connection) throws SQLException {
        String sql = """
                /* tianshu cloud delivery indexes */
                SELECT i.relname,a.amname,x.indisunique,x.indisvalid,x.indisready,x.indpred IS NULL,
                       (SELECT string_agg(pg_catalog.pg_get_indexdef(x.indexrelid,k,false),',' ORDER BY k)
                        FROM generate_series(1,x.indnkeyatts) k)
                FROM pg_catalog.pg_index x JOIN pg_catalog.pg_class i ON i.oid=x.indexrelid
                JOIN pg_catalog.pg_class t ON t.oid=x.indrelid JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_catalog.pg_am a ON a.oid=i.relam
                WHERE n.nspname='public' AND t.relname=? AND i.relname IN (%s)
                """.formatted(quoted(RECEIPT_INDEXES.keySet()));
        int matched = 0;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, RECEIPT);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString(1);
                    require("btree".equals(rows.getString(2)) && !rows.getBoolean(3) && rows.getBoolean(4)
                            && rows.getBoolean(5) && rows.getBoolean(6)
                            && RECEIPT_INDEXES.get(name).equals(rows.getString(7)), name);
                    matched++;
                }
            }
        }
        require(matched == RECEIPT_INDEXES.size(), RECEIPT + " indexes");
    }

    private static void validateBootTrigger(Connection connection) throws SQLException {
        String sql = """
                /* tianshu config boot binding write protection */
                SELECT tr.tgtype,tr.tgenabled,p.proname,pn.nspname,tr.tgqual IS NULL,tr.tgnargs,p.prosrc,p.prosecdef
                FROM pg_catalog.pg_trigger tr JOIN pg_catalog.pg_class t ON t.oid=tr.tgrelid
                JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace JOIN pg_catalog.pg_proc p ON p.oid=tr.tgfoid
                JOIN pg_catalog.pg_namespace pn ON pn.oid=p.pronamespace
                WHERE n.nspname='public' AND t.relname=? AND tr.tgname='tr_ddc_config_boot_binding_immutable'
                  AND NOT tr.tgisinternal
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, BOOT);
            try (ResultSet rows = statement.executeQuery()) {
                int beforeUpdateRowTrigger = 16 | 2 | 1;
                require(rows.next() && rows.getInt(1) == beforeUpdateRowTrigger && Set.of("O", "A").contains(rows.getString(2))
                        && BOOT_TRIGGER_FUNCTION.equals(rows.getString(3)) && SCHEMA.equals(rows.getString(4))
                        && rows.getBoolean(5) && rows.getInt(6) == 0
                        && normalizeSql(BOOT_TRIGGER_BODY).equals(normalizeSql(rows.getString(7))) && !rows.getBoolean(8),
                        "tr_ddc_config_boot_binding_immutable");
            }
        }
    }

    private static String manifestShape(String column) {
        // pg_get_expr renders type coercions and CASE branches in PostgreSQL catalog syntax.
        return """
                %1$s IS NULL OR COALESCE(jsonb_typeof(%1$s) = 'object'::text
                AND octet_length(convert_to(%1$s::text, 'UTF8'::name)) <= 1048576 AND
                CASE WHEN jsonb_typeof(%1$s -> 'schemaVersion'::text) = 'string'::text
                    AND (%1$s ->> 'schemaVersion'::text) ~ '^[1-9][0-9]{0,18}$'::text
                THEN ((%1$s ->> 'schemaVersion'::text)::numeric) <= '9223372036854775807'::bigint::numeric
                ELSE false END, false)
                """.formatted(column);
    }

    private static String normalizeSql(String source) {
        return source == null ? "" : source.replaceAll("\\s+", " ").trim();
    }

    private static String quoted(Set<String> names) {
        return names.stream().sorted().map(name -> "'" + name + "'").collect(Collectors.joining(","));
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new IllegalStateException("TIANSHU_CLOUD_DELIVERY_SCHEMA_MISMATCH: " + detail);
        }
    }

    private record ConstraintVO(String type, boolean validated, boolean deferrable, boolean enforced, String columns,
                                String targetSchema, String targetTable, String targetColumns, String deleteAction,
                                String expression) {
    }
}
