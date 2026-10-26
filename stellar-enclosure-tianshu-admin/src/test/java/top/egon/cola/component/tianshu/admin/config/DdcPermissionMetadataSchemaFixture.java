package top.egon.cola.component.tianshu.admin.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Synthetic JDBC observations derived from the SQL, not evidence of an executed PostgreSQL migration. */
public final class DdcPermissionMetadataSchemaFixture {
    public static final String RESOURCE = "db/egon-mp/V20261023_001__tianshu_permission_metadata.sql";

    private DdcPermissionMetadataSchemaFixture() { }

    public static String sql() throws IOException {
        try (var input = DdcPermissionMetadataSchemaFixture.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing metadata SQL fixture");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static boolean handles(String query) { return query.startsWith("/* tianshu-permission-metadata "); }

    public static Set<String> columns(String table) throws IOException {
        var tables = Pattern.compile("(?s)CREATE TABLE " + Pattern.quote(table) + " \\((.*?)\\n\\);").matcher(sql());
        Set<String> columns = new LinkedHashSet<>();
        if (tables.find()) {
            var matcher = Pattern.compile("(?m)^    ([a-z_][a-z0-9_]*) (?:bigint|varchar|char|text|integer|boolean|timestamptz|timestamp)\\b")
                    .matcher(tables.group(1));
            while (matcher.find()) columns.add(matcher.group(1));
        }
        return columns;
    }

    public static ResultSet rows(String query, String corruption) throws Exception {
        List<Object[]> values = observations(query);
        if ("missing-trigger".equals(corruption) && query.contains(" triggers */")) values.removeLast();
        if ("disabled-trigger".equals(corruption) && query.contains(" triggers */")) values.getFirst()[4] = "D";
        if ("changed-function".equals(corruption) && query.contains(" functions */")) values.getFirst()[1] = "BEGIN RETURN NEW; END;";
        if ("cross-scope-fk".equals(corruption) && query.contains(" constraints */")) {
            for (Object[] row : values) {
                if ("fk_i18n_import_receipt_source".equals(row[1])) row[6] = "tenant_id,id";
            }
        }
        if ("invalid-index".equals(corruption) && query.contains(" indexes */")) values.getFirst()[4] = false;
        ResultSet result = mock(ResultSet.class);
        int[] position = {-1};
        when(result.next()).thenAnswer(call -> ++position[0] < values.size());
        when(result.getString(anyInt())).thenAnswer(call -> {
            Object value = values.get(position[0])[(int) call.getArgument(0) - 1];
            return value == null ? null : value.toString();
        });
        when(result.getBoolean(anyInt())).thenAnswer(call -> values.get(position[0])[(int) call.getArgument(0) - 1]);
        when(result.getInt(anyInt())).thenAnswer(call -> values.get(position[0])[(int) call.getArgument(0) - 1]);
        return result;
    }

    private static List<Object[]> observations(String query) throws Exception {
        String sql = sql();
        List<Object[]> rows = new ArrayList<>();
        if (query.contains(" constraints */")) {
            var tables = Pattern.compile("(?s)CREATE TABLE (\\w+) \\((.*?)\\n\\);").matcher(sql);
            while (tables.find()) appendConstraints(rows, tables.group(1), tables.group(2));
            var alters = Pattern.compile("(?s)ALTER TABLE (\\w+)\\s+(.*?);").matcher(sql);
            while (alters.find()) appendConstraints(rows, alters.group(1), alters.group(2));
        } else if (query.contains(" indexes */")) {
            var indexes = Pattern.compile("(?ms)^CREATE (UNIQUE )?INDEX (\\w+) ON (\\w+)\\s+(?:USING (\\w+)\\s+)?\\((.*?)\\);").matcher(sql);
            while (indexes.find()) rows.add(new Object[]{indexes.group(2), indexes.group(3),
                    indexes.group(4) == null ? "btree" : indexes.group(4), indexes.group(1) != null,
                    true, true, true, String.join(",", split(indexes.group(5)))});
        } else if (query.contains(" functions */")) {
            var functions = Pattern.compile("(?s)CREATE FUNCTION (\\w+)\\(\\) RETURNS trigger LANGUAGE plpgsql AS \\$body\\$(.*?)\\$body\\$;").matcher(sql);
            while (functions.find()) rows.add(new Object[]{functions.group(1), functions.group(2), "plpgsql", false, 0, true, "v", true});
        } else if (query.contains(" triggers */")) {
            var triggers = Pattern.compile("(?s)CREATE TRIGGER (\\w+)\\s+BEFORE (.*?) ON (\\w+)\\s+FOR EACH ROW EXECUTE FUNCTION (\\w+)\\(\\);").matcher(sql);
            while (triggers.find()) {
                int type = 3;
                for (String event : triggers.group(2).split("OR")) type += switch (event.trim()) {
                    case "INSERT" -> 4;
                    case "DELETE" -> 8;
                    case "UPDATE" -> 16;
                    default -> throw new IllegalArgumentException("Unknown trigger event");
                };
                rows.add(new Object[]{triggers.group(1), triggers.group(3), triggers.group(4), type, "O", false, "public"});
            }
        } else throw new IllegalArgumentException("Unknown metadata readiness query");
        return rows;
    }

    private static void appendConstraints(List<Object[]> rows, String table, String body) {
        for (String part : split(body)) {
            String value = part.startsWith("ADD CONSTRAINT ") ? part.substring(4) : part;
            if (!value.startsWith("CONSTRAINT ")) continue;
            String[] prefix = value.split(" ", 3);
            String definition = prefix[2];
            String type = definition.startsWith("PRIMARY KEY") ? "p" : definition.startsWith("UNIQUE") ? "u"
                    : definition.startsWith("FOREIGN KEY") ? "f" : "c";
            String columns = null;
            String targetTable = null;
            String targetColumns = null;
            if (!"c".equals(type)) {
                var keys = Pattern.compile("(?s)\\((.*?)\\)").matcher(definition);
                if (!keys.find()) throw new IllegalArgumentException("Missing constraint columns");
                columns = keys.group(1).replaceAll("\\s+", "");
            }
            if ("f".equals(type)) {
                var target = Pattern.compile("(?s)REFERENCES (\\w+) \\((.*?)\\)").matcher(definition);
                if (!target.find()) throw new IllegalArgumentException("Missing referenced columns");
                targetTable = target.group(1);
                targetColumns = target.group(2).replaceAll("\\s+", "");
            }
            rows.add(new Object[]{table, prefix[1], type, true, columns, targetTable, targetColumns, "r", "public"});
        }
    }

    private static List<String> split(String body) {
        List<String> values = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean quoted = false;
        for (int index = 0; index < body.length(); index++) {
            char value = body.charAt(index);
            if (value == '\'') {
                if (quoted && index + 1 < body.length() && body.charAt(index + 1) == '\'') { index++; continue; }
                quoted = !quoted;
            } else if (!quoted) {
                if (value == '(') depth++;
                else if (value == ')') depth--;
                else if (value == ',' && depth == 0) { values.add(body.substring(start, index).trim()); start = index + 1; }
            }
        }
        values.add(body.substring(start).trim());
        return values;
    }
}
