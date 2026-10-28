package top.egon.cola.component.tianshu.api.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Resolves presentation columns using a current, independently verified field decision. */
public final class ListTemplateResolver {
    private ListTemplateResolver() { }

    /** A null template selects definition defaults; an empty template remains empty. */
    public static List<ListDefinition.Column> visible(ListDefinition definition, List<String> template,
            Predicate<String> readableField) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(readableField, "readableField");
        Map<String, ListDefinition.Column> columns = index(definition);
        List<String> selected = template == null
                ? definition.columns().stream().filter(ListDefinition.Column::defaultVisible).map(ListDefinition.Column::columnKey).toList()
                : checkedKeys(template);
        return selected.stream().map(columns::get).filter(Objects::nonNull)
                .filter(column -> column.fieldKeys().stream().allMatch(readableField)).toList();
    }

    /** Updates only the currently readable subset and retains temporarily inaccessible saved columns. */
    public static List<String> mergePersonal(ListDefinition definition, List<String> stored, List<String> requested,
            Predicate<String> readableField) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(readableField, "readableField");
        Map<String, ListDefinition.Column> columns = index(definition);
        List<String> previous = checkedKeys(stored);
        List<String> selected = checkedKeys(requested);
        for (String key : selected) {
            var column = columns.get(key);
            if (column == null || !column.fieldKeys().stream().allMatch(readableField)) {
                throw new IllegalArgumentException("Requested column is unavailable");
            }
        }
        List<String> result = new ArrayList<>(selected);
        for (String key : previous) {
            var column = columns.get(key);
            if (column == null || !column.fieldKeys().stream().allMatch(readableField)) result.add(key);
        }
        return checkedKeys(result);
    }

    /** Resolves a saved selection, followed by personal and system defaults. */
    public static Template select(List<Template> templates, String selectedId, String issuer, String subject) {
        Objects.requireNonNull(templates, "templates");
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("A verified user identity is required");
        }
        var accessible = templates.stream().filter(template -> template.type() == Template.Type.SYSTEM
                || issuer.equals(template.ownerIssuer()) && subject.equals(template.ownerSubject())).toList();
        if (selectedId != null) {
            var selected = accessible.stream().filter(template -> selectedId.equals(template.id())).findFirst();
            if (selected.isPresent()) return selected.get();
        }
        var personal = accessible.stream().filter(template -> template.type() == Template.Type.PERSONAL && template.isDefault()).toList();
        var system = accessible.stream().filter(template -> template.type() == Template.Type.SYSTEM && template.isDefault()).toList();
        if (personal.size() > 1 || system.size() > 1) throw new IllegalStateException("Ambiguous template defaults");
        return personal.isEmpty() ? system.stream().findFirst().orElse(null) : personal.getFirst();
    }

    public record Template(String id, Type type, String ownerIssuer, String ownerSubject, boolean isDefault,
                           List<String> columnKeys) {
        public Template {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Template id is required");
            Objects.requireNonNull(type, "type");
            if (type == Type.SYSTEM && (ownerIssuer != null || ownerSubject != null)
                    || type == Type.PERSONAL && (ownerIssuer == null || ownerIssuer.isBlank()
                    || ownerSubject == null || ownerSubject.isBlank())) throw new IllegalArgumentException("Invalid template owner");
            columnKeys = checkedKeys(columnKeys);
        }

        public enum Type { SYSTEM, PERSONAL }
    }

    private static Map<String, ListDefinition.Column> index(ListDefinition definition) {
        Map<String, ListDefinition.Column> result = new HashMap<>();
        definition.columns().forEach(column -> result.put(column.columnKey(), column));
        return result;
    }

    private static List<String> checkedKeys(List<String> keys) {
        Objects.requireNonNull(keys, "column keys");
        if (keys.size() > 256 || keys.stream().anyMatch(key -> key == null || key.isBlank())
                || new HashSet<>(keys).size() != keys.size()) throw new IllegalArgumentException("Invalid template columns");
        return List.copyOf(keys);
    }
}
