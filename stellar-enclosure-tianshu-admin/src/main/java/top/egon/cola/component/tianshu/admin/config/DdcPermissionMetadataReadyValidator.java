package top.egon.cola.component.tianshu.admin.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the source/translation schema without changing runtime activation or database state. */
final class DdcPermissionMetadataReadyValidator {
    private static final String SQL_RESOURCE = "db/egon-mp/V20261023_001__tianshu_permission_metadata.sql";
    private static final Map<String, Set<String>> TABLE_COLUMNS = Map.ofEntries(
            Map.entry("ddc_metadata_scope_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "biz_code", "namespace_id", "namespace_code", "env_code", "app_id", "app_code", "binding_id")),
            Map.entry("ddc_source_import_grant_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "producer_id", "issuer_uri", "client_id", "resource_uri", "principal_binding_digest", "enabled")),
            Map.entry("ddc_i18n_source_version_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "producer_id", "schema_version", "source_version", "policy_digest", "metadata_digest", "manifest_digest", "projection_digest", "canonical_manifest_json", "resource_id_map_json", "permission_id_map_json", "resource_count", "permission_count", "message_count")),
            Map.entry("ddc_resource_metadata_projection_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "source_id", "producer_id", "source_version", "metadata_kind", "definition_key", "resource_id", "permission_id", "resource_type", "definition_lifecycle", "page_code", "label_key", "description_key", "target_json", "policy_json", "i18n_refs_json", "referenced_permission_codes_json")),
            Map.entry("ddc_i18n_source_message_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "source_id", "producer_id", "source_version", "page_code", "message_key", "default_locale", "source_hash", "defaults_json", "placeholders_json")),
            Map.entry("ddc_i18n_import_receipt_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "operation_id", "producer_id", "producer_version", "source_id", "source_version", "manifest_digest", "policy_digest", "metadata_digest", "request_digest", "grant_id", "grant_version", "publisher_issuer", "publisher_client_id", "response_json")),
            Map.entry("ddc_catalog_activation_projection_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "activation_version", "descriptor_digest", "descriptor_json", "producer_count", "authority_resource_uri")),
            Map.entry("ddc_catalog_activation_producer_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "activation_projection_id", "producer_id", "producer_version", "source_id", "source_version", "policy_digest", "metadata_digest")),
            Map.entry("ddc_i18n_published_override_t0", Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version", "catalog_scope_id", "page_id", "page_code", "publication_revision", "draft_revision", "based_on_activation_version", "publish_operation_id", "request_digest", "default_locale", "translations_json", "reviewed_source_hashes_json", "published_at"))
    );
    private static final Map<String, ConstraintSpecBO> CONSTRAINTS = Map.ofEntries(
            Map.entry("pk_metadata_scope", new ConstraintSpecBO("ddc_metadata_scope_t0", "p", "id", "", "")),
            Map.entry("uk_metadata_scope_tenant_id", new ConstraintSpecBO("ddc_metadata_scope_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_metadata_scope_catalog", new ConstraintSpecBO("ddc_metadata_scope_t0", "u", "tenant_id,catalog_scope_id", "", "")),
            Map.entry("uk_metadata_scope_target", new ConstraintSpecBO("ddc_metadata_scope_t0", "u", "tenant_id,biz_code,namespace_code,env_code,app_code", "", "")),
            Map.entry("uk_metadata_scope_page_target", new ConstraintSpecBO("ddc_metadata_scope_t0", "u", "tenant_id,catalog_scope_id,biz_code,namespace_id,env_code,app_code", "", "")),
            Map.entry("fk_metadata_scope_namespace", new ConstraintSpecBO("ddc_metadata_scope_t0", "f", "tenant_id,namespace_id", "ddc_namespace_t0", "tenant_id,id")),
            Map.entry("fk_metadata_scope_app", new ConstraintSpecBO("ddc_metadata_scope_t0", "f", "tenant_id,app_id", "ddc_app_t0", "tenant_id,id")),
            Map.entry("fk_metadata_scope_binding", new ConstraintSpecBO("ddc_metadata_scope_t0", "f", "tenant_id,binding_id", "ddc_namespace_env_app_t0", "tenant_id,id")),
            Map.entry("ck_metadata_scope_identity", new ConstraintSpecBO("ddc_metadata_scope_t0", "c", "", "", "")),
            Map.entry("ck_metadata_scope_immutable", new ConstraintSpecBO("ddc_metadata_scope_t0", "c", "", "", "")),
            Map.entry("pk_source_import_grant", new ConstraintSpecBO("ddc_source_import_grant_t0", "p", "id", "", "")),
            Map.entry("uk_source_import_grant_tenant_id", new ConstraintSpecBO("ddc_source_import_grant_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_source_import_grant_target", new ConstraintSpecBO("ddc_source_import_grant_t0", "u", "tenant_id,catalog_scope_id,producer_id,id", "", "")),
            Map.entry("uk_source_import_grant_identity", new ConstraintSpecBO("ddc_source_import_grant_t0", "u", "tenant_id,catalog_scope_id,producer_id,principal_binding_digest", "", "")),
            Map.entry("fk_source_import_grant_scope", new ConstraintSpecBO("ddc_source_import_grant_t0", "f", "tenant_id,catalog_scope_id", "ddc_metadata_scope_t0", "tenant_id,catalog_scope_id")),
            Map.entry("ck_source_import_grant_digest", new ConstraintSpecBO("ddc_source_import_grant_t0", "c", "", "", "")),
            Map.entry("ck_source_import_grant_strings", new ConstraintSpecBO("ddc_source_import_grant_t0", "c", "", "", "")),
            Map.entry("ck_source_import_grant_live", new ConstraintSpecBO("ddc_source_import_grant_t0", "c", "", "", "")),
            Map.entry("pk_i18n_source_version", new ConstraintSpecBO("ddc_i18n_source_version_t0", "p", "id", "", "")),
            Map.entry("uk_i18n_source_version_tenant_id", new ConstraintSpecBO("ddc_i18n_source_version_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_i18n_source_version_identity", new ConstraintSpecBO("ddc_i18n_source_version_t0", "u", "tenant_id,catalog_scope_id,producer_id,source_version", "", "")),
            Map.entry("uk_i18n_source_version_owner", new ConstraintSpecBO("ddc_i18n_source_version_t0", "u", "tenant_id,catalog_scope_id,id,producer_id,source_version", "", "")),
            Map.entry("uk_i18n_source_version_receipt", new ConstraintSpecBO("ddc_i18n_source_version_t0", "u", "tenant_id,catalog_scope_id,id,producer_id,source_version,policy_digest,metadata_digest", "", "")),
            Map.entry("fk_i18n_source_version_scope", new ConstraintSpecBO("ddc_i18n_source_version_t0", "f", "tenant_id,catalog_scope_id", "ddc_metadata_scope_t0", "tenant_id,catalog_scope_id")),
            Map.entry("ck_i18n_source_version_digests", new ConstraintSpecBO("ddc_i18n_source_version_t0", "c", "", "", "")),
            Map.entry("ck_i18n_source_version_json", new ConstraintSpecBO("ddc_i18n_source_version_t0", "c", "", "", "")),
            Map.entry("ck_i18n_source_version_immutable", new ConstraintSpecBO("ddc_i18n_source_version_t0", "c", "", "", "")),
            Map.entry("pk_resource_metadata_projection", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "p", "id", "", "")),
            Map.entry("uk_resource_metadata_projection_tenant", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_resource_metadata_projection_key", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "u", "tenant_id,source_id,metadata_kind,definition_key", "", "")),
            Map.entry("fk_resource_metadata_projection_source", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "f", "tenant_id,catalog_scope_id,source_id,producer_id,source_version", "ddc_i18n_source_version_t0", "tenant_id,catalog_scope_id,id,producer_id,source_version")),
            Map.entry("ck_resource_metadata_projection_kind", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "c", "", "", "")),
            Map.entry("ck_resource_metadata_projection_lifecycle", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "c", "", "", "")),
            Map.entry("ck_resource_metadata_projection_json", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "c", "", "", "")),
            Map.entry("ck_resource_metadata_projection_immutable", new ConstraintSpecBO("ddc_resource_metadata_projection_t0", "c", "", "", "")),
            Map.entry("pk_i18n_source_message", new ConstraintSpecBO("ddc_i18n_source_message_t0", "p", "id", "", "")),
            Map.entry("uk_i18n_source_message_tenant_id", new ConstraintSpecBO("ddc_i18n_source_message_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_i18n_source_message_key", new ConstraintSpecBO("ddc_i18n_source_message_t0", "u", "tenant_id,source_id,page_code,message_key", "", "")),
            Map.entry("fk_i18n_source_message_source", new ConstraintSpecBO("ddc_i18n_source_message_t0", "f", "tenant_id,catalog_scope_id,source_id,producer_id,source_version", "ddc_i18n_source_version_t0", "tenant_id,catalog_scope_id,id,producer_id,source_version")),
            Map.entry("ck_i18n_source_message_key", new ConstraintSpecBO("ddc_i18n_source_message_t0", "c", "", "", "")),
            Map.entry("ck_i18n_source_message_hash", new ConstraintSpecBO("ddc_i18n_source_message_t0", "c", "", "", "")),
            Map.entry("ck_i18n_source_message_defaults", new ConstraintSpecBO("ddc_i18n_source_message_t0", "c", "", "", "")),
            Map.entry("ck_i18n_source_message_immutable", new ConstraintSpecBO("ddc_i18n_source_message_t0", "c", "", "", "")),
            Map.entry("pk_i18n_import_receipt", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "p", "id", "", "")),
            Map.entry("uk_i18n_import_receipt_tenant_id", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_i18n_import_receipt_operation", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "u", "tenant_id,catalog_scope_id,producer_id,operation_id", "", "")),
            Map.entry("fk_i18n_import_receipt_source", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "f", "tenant_id,catalog_scope_id,source_id,producer_id,source_version,policy_digest,metadata_digest", "ddc_i18n_source_version_t0", "tenant_id,catalog_scope_id,id,producer_id,source_version,policy_digest,metadata_digest")),
            Map.entry("fk_i18n_import_receipt_grant", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "f", "tenant_id,catalog_scope_id,producer_id,grant_id", "ddc_source_import_grant_t0", "tenant_id,catalog_scope_id,producer_id,id")),
            Map.entry("ck_i18n_import_receipt_digests", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "c", "", "", "")),
            Map.entry("ck_i18n_import_receipt_json", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "c", "", "", "")),
            Map.entry("ck_i18n_import_receipt_immutable", new ConstraintSpecBO("ddc_i18n_import_receipt_t0", "c", "", "", "")),
            Map.entry("pk_catalog_activation_projection", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "p", "id", "", "")),
            Map.entry("uk_catalog_activation_projection_tenant", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_catalog_activation_projection_version", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "u", "tenant_id,catalog_scope_id,activation_version", "", "")),
            Map.entry("uk_catalog_activation_projection_owner", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "u", "tenant_id,catalog_scope_id,id", "", "")),
            Map.entry("fk_catalog_activation_projection_scope", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "f", "tenant_id,catalog_scope_id", "ddc_metadata_scope_t0", "tenant_id,catalog_scope_id")),
            Map.entry("ck_catalog_activation_projection_digest", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "c", "", "", "")),
            Map.entry("ck_catalog_activation_projection_json", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "c", "", "", "")),
            Map.entry("ck_catalog_activation_projection_immutable", new ConstraintSpecBO("ddc_catalog_activation_projection_t0", "c", "", "", "")),
            Map.entry("pk_catalog_activation_producer", new ConstraintSpecBO("ddc_catalog_activation_producer_t0", "p", "id", "", "")),
            Map.entry("uk_catalog_activation_producer_tenant", new ConstraintSpecBO("ddc_catalog_activation_producer_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_catalog_activation_producer_member", new ConstraintSpecBO("ddc_catalog_activation_producer_t0", "u", "tenant_id,activation_projection_id,producer_id", "", "")),
            Map.entry("fk_catalog_activation_producer_descriptor", new ConstraintSpecBO("ddc_catalog_activation_producer_t0", "f", "tenant_id,catalog_scope_id,activation_projection_id", "ddc_catalog_activation_projection_t0", "tenant_id,catalog_scope_id,id")),
            Map.entry("fk_catalog_activation_producer_source", new ConstraintSpecBO("ddc_catalog_activation_producer_t0", "f", "tenant_id,catalog_scope_id,source_id,producer_id,source_version,policy_digest,metadata_digest", "ddc_i18n_source_version_t0", "tenant_id,catalog_scope_id,id,producer_id,source_version,policy_digest,metadata_digest")),
            Map.entry("ck_catalog_activation_producer_digest", new ConstraintSpecBO("ddc_catalog_activation_producer_t0", "c", "", "", "")),
            Map.entry("ck_catalog_activation_producer_immutable", new ConstraintSpecBO("ddc_catalog_activation_producer_t0", "c", "", "", "")),
            Map.entry("pk_i18n_published_override", new ConstraintSpecBO("ddc_i18n_published_override_t0", "p", "id", "", "")),
            Map.entry("uk_i18n_published_override_tenant_id", new ConstraintSpecBO("ddc_i18n_published_override_t0", "u", "tenant_id,id", "", "")),
            Map.entry("uk_i18n_published_override_revision", new ConstraintSpecBO("ddc_i18n_published_override_t0", "u", "tenant_id,catalog_scope_id,page_id,publication_revision", "", "")),
            Map.entry("uk_i18n_published_override_operation", new ConstraintSpecBO("ddc_i18n_published_override_t0", "u", "tenant_id,catalog_scope_id,page_id,publish_operation_id", "", "")),
            Map.entry("uk_i18n_published_override_head", new ConstraintSpecBO("ddc_i18n_published_override_t0", "u", "tenant_id,catalog_scope_id,page_id,id,publication_revision", "", "")),
            Map.entry("fk_i18n_published_override_page", new ConstraintSpecBO("ddc_i18n_published_override_t0", "f", "tenant_id,catalog_scope_id,page_id,page_code", "ddc_i18n_page_t0", "tenant_id,catalog_scope_id,id,page_code")),
            Map.entry("fk_i18n_published_override_activation", new ConstraintSpecBO("ddc_i18n_published_override_t0", "f", "tenant_id,catalog_scope_id,based_on_activation_version", "ddc_catalog_activation_projection_t0", "tenant_id,catalog_scope_id,activation_version")),
            Map.entry("ck_i18n_published_override_digest", new ConstraintSpecBO("ddc_i18n_published_override_t0", "c", "", "", "")),
            Map.entry("ck_i18n_published_override_json", new ConstraintSpecBO("ddc_i18n_published_override_t0", "c", "", "", "")),
            Map.entry("ck_i18n_published_override_immutable", new ConstraintSpecBO("ddc_i18n_published_override_t0", "c", "", "", "")),
            Map.entry("uk_i18n_page_catalog_key", new ConstraintSpecBO("ddc_i18n_page_t0", "u", "tenant_id,catalog_scope_id,page_code", "", "")),
            Map.entry("uk_i18n_page_catalog_id", new ConstraintSpecBO("ddc_i18n_page_t0", "u", "tenant_id,catalog_scope_id,id", "", "")),
            Map.entry("uk_i18n_page_catalog_identity", new ConstraintSpecBO("ddc_i18n_page_t0", "u", "tenant_id,catalog_scope_id,id,page_code", "", "")),
            Map.entry("fk_i18n_page_metadata_scope", new ConstraintSpecBO("ddc_i18n_page_t0", "f", "tenant_id,catalog_scope_id,biz_code,namespace_id,env_code,app_code", "ddc_metadata_scope_t0", "tenant_id,catalog_scope_id,biz_code,namespace_id,env_code,app_code")),
            Map.entry("fk_i18n_page_draft_activation", new ConstraintSpecBO("ddc_i18n_page_t0", "f", "tenant_id,catalog_scope_id,draft_activation_version", "ddc_catalog_activation_projection_t0", "tenant_id,catalog_scope_id,activation_version")),
            Map.entry("ck_i18n_page_source_hashes", new ConstraintSpecBO("ddc_i18n_page_t0", "c", "", "", "")),
            Map.entry("ck_i18n_page_publication_context", new ConstraintSpecBO("ddc_i18n_page_t0", "c", "", "", "")),
            Map.entry("ck_i18n_page_managed_live", new ConstraintSpecBO("ddc_i18n_page_t0", "c", "", "", "")),
            Map.entry("fk_i18n_page_current_override", new ConstraintSpecBO("ddc_i18n_page_t0", "f", "tenant_id,catalog_scope_id,id,current_override_id,published_version", "ddc_i18n_published_override_t0", "tenant_id,catalog_scope_id,page_id,id,publication_revision"))
    );
    private static final Map<String, IndexSpecBO> INDEXES = Map.ofEntries(
            Map.entry("ix_resource_metadata_scope_type", new IndexSpecBO("ddc_resource_metadata_projection_t0", "btree", false, "tenant_id,catalog_scope_id,resource_type,source_id,definition_key")),
            Map.entry("ix_resource_metadata_permission_refs", new IndexSpecBO("ddc_resource_metadata_projection_t0", "gin", false, "(referenced_permission_codes_json::jsonb)")),
            Map.entry("uk_resource_metadata_key_case", new IndexSpecBO("ddc_resource_metadata_projection_t0", "btree", true, "tenant_id,source_id,metadata_kind,lower(definition_key)")),
            Map.entry("ix_i18n_source_message_page", new IndexSpecBO("ddc_i18n_source_message_t0", "btree", false, "tenant_id,catalog_scope_id,page_code,source_id")),
            Map.entry("uk_i18n_source_message_key_case", new IndexSpecBO("ddc_i18n_source_message_t0", "btree", true, "tenant_id,source_id,lower(page_code),lower(message_key)")),
            Map.entry("ix_i18n_import_receipt_source", new IndexSpecBO("ddc_i18n_import_receipt_t0", "btree", false, "tenant_id,catalog_scope_id,producer_id,producer_version")),
            Map.entry("ix_i18n_published_override_history", new IndexSpecBO("ddc_i18n_published_override_t0", "btree", false, "tenant_id,catalog_scope_id,page_id,publication_revision DESC"))
    );
    private static final Map<String, TriggerSpecBO> TRIGGERS = Map.ofEntries(
            Map.entry("tr_ddc_metadata_scope_immutable", new TriggerSpecBO("ddc_metadata_scope_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_ddc_i18n_source_version_immutable", new TriggerSpecBO("ddc_i18n_source_version_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_ddc_resource_metadata_projection_immutable", new TriggerSpecBO("ddc_resource_metadata_projection_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_ddc_i18n_source_message_immutable", new TriggerSpecBO("ddc_i18n_source_message_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_ddc_i18n_import_receipt_immutable", new TriggerSpecBO("ddc_i18n_import_receipt_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_ddc_catalog_activation_projection_immutable", new TriggerSpecBO("ddc_catalog_activation_projection_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_ddc_catalog_activation_producer_immutable", new TriggerSpecBO("ddc_catalog_activation_producer_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_ddc_i18n_published_override_immutable", new TriggerSpecBO("ddc_i18n_published_override_t0", "ddc_metadata_reject_mutation", 27)),
            Map.entry("tr_source_import_grant_guard", new TriggerSpecBO("ddc_source_import_grant_t0", "ddc_source_import_grant_guard", 27)),
            Map.entry("tr_i18n_page_publication_guard", new TriggerSpecBO("ddc_i18n_page_t0", "ddc_i18n_page_publication_guard", 23))
    );
    private static final Set<String> FUNCTIONS = Set.of("ddc_metadata_reject_mutation", "ddc_source_import_grant_guard", "ddc_i18n_page_publication_guard");

    private DdcPermissionMetadataReadyValidator() { }

    static int tableCount() { return TABLE_COLUMNS.size(); }

    static void validate(Connection connection) throws SQLException {
        validateColumns(connection.getMetaData());
        validateConstraints(connection);
        validateIndexes(connection);
        validateFunctions(connection);
        validateTriggers(connection);
    }

    private static void validateColumns(DatabaseMetaData metadata) throws SQLException {
        for (var entry : TABLE_COLUMNS.entrySet()) {
            try (ResultSet tables = metadata.getTables(null, "public", entry.getKey(), new String[]{"TABLE"})) {
                if (!tables.next() || !entry.getKey().equals(tables.getString("TABLE_NAME"))) {
                    throw invalid("TABLE_MISSING", entry.getKey());
                }
            }
            Set<String> actual = new LinkedHashSet<>();
            try (ResultSet columns = metadata.getColumns(null, "public", entry.getKey(), null)) {
                while (columns.next()) actual.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            if (!entry.getValue().equals(actual)) throw invalid("COLUMN_SET_MISMATCH", entry.getKey());
        }
    }

    private static void validateConstraints(Connection connection) throws SQLException {
        String sql = "/* tianshu-permission-metadata constraints */ SELECT t.relname,c.conname,c.contype,c.convalidated, "
                + "(SELECT string_agg(a.attname,',' ORDER BY k.ordinality) FROM unnest(c.conkey) WITH ORDINALITY k(attnum,ordinality) "
                + "JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum), "
                + "rt.relname, (SELECT string_agg(a.attname,',' ORDER BY k.ordinality) FROM unnest(c.confkey) WITH ORDINALITY k(attnum,ordinality) "
                + "JOIN pg_catalog.pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.attnum), c.confdeltype, rn.nspname "
                + "FROM pg_catalog.pg_constraint c JOIN pg_catalog.pg_class t ON t.oid=c.conrelid "
                + "JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace "
                + "LEFT JOIN pg_catalog.pg_class rt ON rt.oid=c.confrelid "
                + "LEFT JOIN pg_catalog.pg_namespace rn ON rn.oid=rt.relnamespace "
                + "WHERE n.nspname='public' AND c.conname IN (" + quoted(CONSTRAINTS.keySet()) + ")";
        Set<String> seen = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(2);
                ConstraintSpecBO expected = CONSTRAINTS.get(name);
                if (expected == null || !seen.add(name) || !expected.table().equals(rows.getString(1))
                        || !expected.type().equals(rows.getString(3)) || !rows.getBoolean(4)
                        || !"c".equals(expected.type()) && !expected.columns().equals(rows.getString(5))) {
                    throw invalid("CONSTRAINT_MISMATCH", name);
                }
                if ("f".equals(expected.type()) && (!expected.targetTable().equals(rows.getString(6))
                        || !expected.targetColumns().equals(rows.getString(7)) || !"r".equals(rows.getString(8))
                        || !"public".equals(rows.getString(9)))) throw invalid("FOREIGN_KEY_MISMATCH", name);
            }
        }
        requireComplete(CONSTRAINTS.keySet(), seen, "CONSTRAINT_MISSING");
    }

    private static void validateIndexes(Connection connection) throws SQLException {
        String sql = "/* tianshu-permission-metadata indexes */ SELECT i.relname,t.relname,a.amname,x.indisunique,x.indisvalid,x.indisready,x.indpred IS NULL, "
                + "(SELECT string_agg(pg_catalog.pg_get_indexdef(x.indexrelid,k,false),',' ORDER BY k) "
                + "FROM generate_series(1,x.indnkeyatts) k) "
                + "FROM pg_catalog.pg_index x JOIN pg_catalog.pg_class i ON i.oid=x.indexrelid "
                + "JOIN pg_catalog.pg_class t ON t.oid=x.indrelid JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace "
                + "JOIN pg_catalog.pg_am a ON a.oid=i.relam WHERE n.nspname='public' AND i.relname IN (" + quoted(INDEXES.keySet()) + ")";
        Set<String> seen = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1);
                IndexSpecBO expected = INDEXES.get(name);
                if (expected == null || !seen.add(name) || !expected.table().equals(rows.getString(2))
                        || !expected.method().equals(rows.getString(3)) || expected.unique() != rows.getBoolean(4)
                        || !rows.getBoolean(5) || !rows.getBoolean(6) || !rows.getBoolean(7)
                        || !normalizeIndex(expected.columns()).equals(normalizeIndex(rows.getString(8)))) {
                    throw invalid("INDEX_MISMATCH", name);
                }
            }
        }
        requireComplete(INDEXES.keySet(), seen, "INDEX_MISSING");
    }

    private static void validateFunctions(Connection connection) throws SQLException {
        String migration = migrationSql();
        String sql = "/* tianshu-permission-metadata functions */ SELECT p.proname,p.prosrc,l.lanname,p.prosecdef,p.pronargs,p.prorettype='trigger'::regtype,p.provolatile,p.proconfig IS NULL "
                + "FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace "
                + "JOIN pg_catalog.pg_language l ON l.oid=p.prolang WHERE n.nspname='public' AND p.proname IN (" + quoted(FUNCTIONS) + ")";
        Set<String> seen = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1);
                if (!FUNCTIONS.contains(name) || !seen.add(name) || !"plpgsql".equals(rows.getString(3))
                        || rows.getBoolean(4) || rows.getInt(5) != 0 || !rows.getBoolean(6)
                        || !"v".equals(rows.getString(7)) || !rows.getBoolean(8)
                        || !functionBody(migration, name).equals(normalizeBody(rows.getString(2)))) {
                    throw invalid("FUNCTION_MISMATCH", name);
                }
            }
        }
        requireComplete(FUNCTIONS, seen, "FUNCTION_MISSING");
    }

    private static void validateTriggers(Connection connection) throws SQLException {
        String sql = "/* tianshu-permission-metadata triggers */ SELECT g.tgname,t.relname,p.proname,g.tgtype,g.tgenabled,g.tgisinternal,pn.nspname "
                + "FROM pg_catalog.pg_trigger g JOIN pg_catalog.pg_class t ON t.oid=g.tgrelid "
                + "JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace JOIN pg_catalog.pg_proc p ON p.oid=g.tgfoid "
                + "JOIN pg_catalog.pg_namespace pn ON pn.oid=p.pronamespace "
                + "WHERE n.nspname='public' AND g.tgname IN (" + quoted(TRIGGERS.keySet()) + ")";
        Set<String> seen = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1);
                TriggerSpecBO expected = TRIGGERS.get(name);
                if (expected == null || !seen.add(name) || !expected.table().equals(rows.getString(2))
                        || !expected.function().equals(rows.getString(3)) || expected.type() != rows.getInt(4)
                        || !"O".equals(rows.getString(5)) || rows.getBoolean(6) || !"public".equals(rows.getString(7))) {
                    throw invalid("TRIGGER_MISMATCH", name);
                }
            }
        }
        requireComplete(TRIGGERS.keySet(), seen, "TRIGGER_MISSING");
    }

    private static String migrationSql() {
        try (var input = DdcPermissionMetadataReadyValidator.class.getClassLoader().getResourceAsStream(SQL_RESOURCE)) {
            if (input == null) throw invalid("MIGRATION_MISSING", SQL_RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("TIANSHU_METADATA_SCHEMA_MIGRATION_UNREADABLE", failure);
        }
    }

    private static String functionBody(String sql, String name) {
        var matcher = Pattern.compile("(?s)CREATE FUNCTION " + Pattern.quote(name)
                + "\\(\\) RETURNS trigger LANGUAGE plpgsql AS \\$body\\$(.*?)\\$body\\$;").matcher(sql);
        if (!matcher.find()) throw invalid("FUNCTION_SOURCE_MISSING", name);
        return normalizeBody(matcher.group(1));
    }

    private static String normalizeBody(String value) { return value == null ? "" : value.replace("\r\n", "\n").trim(); }
    private static String normalizeIndex(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("::(?:text|jsonb|character varying)", "")
                .replaceAll("\\b(?:asc|desc)\\b", "").replaceAll("[()\\s\"]+", "");
    }
    private static String quoted(Set<String> names) { return names.stream().sorted().map(name -> "'" + name + "'").collect(java.util.stream.Collectors.joining(",")); }
    private static void requireComplete(Set<String> expected, Set<String> seen, String reason) {
        for (String name : expected) if (!seen.contains(name)) throw invalid(reason, name);
    }
    private static IllegalStateException invalid(String reason, String detail) { return new IllegalStateException("TIANSHU_METADATA_SCHEMA_" + reason + ": " + detail); }
    private record ConstraintSpecBO(String table, String type, String columns, String targetTable, String targetColumns) { }
    private record IndexSpecBO(String table, String method, boolean unique, String columns) { }
    private record TriggerSpecBO(String table, String function, int type) { }
}
