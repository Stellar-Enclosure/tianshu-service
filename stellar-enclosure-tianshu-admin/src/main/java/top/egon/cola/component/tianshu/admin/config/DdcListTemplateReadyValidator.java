package top.egon.cola.component.tianshu.admin.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Verifies the uniqueness fences and immutable activated-list projection. */
final class DdcListTemplateReadyValidator {
    private static final Map<String, List<String>> INDEXES = Map.of(
            "uk_list_template_name", List.of("tenant_id", "catalog_scope_id", "page_resource_key", "list_key", "template_type", "owner_digest", "lower(template_name)"),
            "uk_list_template_default", List.of("tenant_id", "catalog_scope_id", "page_resource_key", "list_key", "template_type", "owner_digest"));

    private DdcListTemplateReadyValidator() { }

    static void validate(Connection connection) throws SQLException {
        Set<String> seen = new HashSet<>();
        String sql = "/* tianshu-list-templates indexes */ SELECT i.relname, x.indisunique, x.indisvalid, x.indisready, "
                + "pg_catalog.pg_get_expr(x.indpred,x.indrelid), "
                + "pg_catalog.pg_get_indexdef(i.oid,1,true), pg_catalog.pg_get_indexdef(i.oid,2,true), "
                + "pg_catalog.pg_get_indexdef(i.oid,3,true), pg_catalog.pg_get_indexdef(i.oid,4,true), "
                + "pg_catalog.pg_get_indexdef(i.oid,5,true), pg_catalog.pg_get_indexdef(i.oid,6,true), "
                + "pg_catalog.pg_get_indexdef(i.oid,7,true), x.indnkeyatts, x.indnatts "
                + "FROM pg_catalog.pg_index x JOIN pg_catalog.pg_class i ON i.oid=x.indexrelid "
                + "JOIN pg_catalog.pg_class t ON t.oid=x.indrelid JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace "
                + "WHERE n.nspname='public' AND t.relname='ddc_list_template_t0' "
                + "AND i.relname IN ('uk_list_template_name','uk_list_template_default')";
        try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1);
                List<String> expected = INDEXES.get(name);
                if (expected == null || !seen.add(name) || !rows.getBoolean(2) || !rows.getBoolean(3) || !rows.getBoolean(4)
                        || rows.getInt(13) != expected.size() || rows.getInt(14) != expected.size()) throw invalid();
                String predicate = normalize(rows.getString(5));
                String expectedPredicate = name.equals("uk_list_template_default") ? "deleted_atisnullandis_default" : "deleted_atisnull";
                if (!expectedPredicate.equals(predicate)) throw invalid();
                for (int index = 0; index < expected.size(); index++) {
                    String actual = normalize(rows.getString(index + 6)).replace("::text", "");
                    if (!normalize(expected.get(index)).equals(actual)) throw invalid();
                }
            }
        }
        if (!seen.equals(INDEXES.keySet())) throw invalid();
        String triggerSql = "/* tianshu-list-templates trigger */ SELECT p.proname,g.tgtype,g.tgenabled,g.tgisinternal "
                + "FROM pg_catalog.pg_trigger g JOIN pg_catalog.pg_class t ON t.oid=g.tgrelid "
                + "JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace JOIN pg_catalog.pg_proc p ON p.oid=g.tgfoid "
                + "JOIN pg_catalog.pg_namespace pn ON pn.oid=p.pronamespace "
                + "WHERE n.nspname='public' AND pn.nspname='public' AND t.relname='ddc_list_definition_t0' "
                + "AND g.tgname='tr_ddc_list_definition_immutable'";
        try (var statement = connection.prepareStatement(triggerSql); var rows = statement.executeQuery()) {
            if (!rows.next() || !"ddc_metadata_reject_mutation".equals(rows.getString(1)) || rows.getInt(2) != 27
                    || !"O".equals(rows.getString(3)) || rows.getBoolean(4) || rows.next()) throw invalid();
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[()\\s]+", "");
    }
    private static IllegalStateException invalid() { return new IllegalStateException("TIANSHU_LIST_TEMPLATE_SCHEMA_INVALID"); }
}
