package top.egon.cola.component.tianshu.api.list;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A business-owned list definition. Field keys refer to the active permission catalog. */
public record ListDefinition(String pageResourceKey, String listKey, List<Column> columns) {
    public ListDefinition {
        pageResourceKey = key(pageResourceKey, 200);
        listKey = key(listKey, 128);
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        if (columns.isEmpty() || columns.size() > 256) throw new IllegalArgumentException("List must declare 1..256 columns");
        var seen = new HashSet<String>();
        for (Column column : columns) {
            if (!seen.add(column.columnKey())) throw new IllegalArgumentException("Duplicate list column");
        }
    }

    public String digest() {
        return CanonicalJson.digest(Map.of("pageResourceKey", pageResourceKey, "listKey", listKey,
                "columns", columns.stream().map(column -> Map.of("columnKey", column.columnKey(),
                        "fieldKeys", column.fieldKeys(), "labelPage", column.labelPage(), "labelKey", column.labelKey(),
                        "defaultVisible", column.defaultVisible(), "exportable", column.exportable())).toList()));
    }

    public record Column(String columnKey, List<String> fieldKeys, String labelPage, String labelKey,
                         boolean defaultVisible, boolean exportable) {
        public Column {
            columnKey = key(columnKey, 128);
            fieldKeys = List.copyOf(Objects.requireNonNull(fieldKeys, "fieldKeys"));
            if (fieldKeys.isEmpty() || fieldKeys.size() > 16 || new HashSet<>(fieldKeys).size() != fieldKeys.size()) {
                throw new IllegalArgumentException("Column must declare 1..16 unique fields");
            }
            fieldKeys.forEach(field -> key(field, 200));
            labelPage = I18nSourceRules.key(labelPage);
            labelKey = I18nSourceRules.key(labelKey);
        }
    }

    private static String key(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException("Invalid list identifier");
        }
        return value;
    }
}
