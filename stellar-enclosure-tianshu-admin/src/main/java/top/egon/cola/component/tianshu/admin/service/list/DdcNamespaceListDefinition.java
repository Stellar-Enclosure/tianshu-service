package top.egon.cola.component.tianshu.admin.service.list;

import top.egon.cola.component.tianshu.api.list.ListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;

import java.util.List;

/** The namespace list uses the same output DTO and field resources as the page API. */
public final class DdcNamespaceListDefinition {
    public static final ListDefinition DEFINITION = new ListDefinition("tianshu.page.namespaces", "main", List.of(
            column("bizCode", "tianshu.namespaces", "fields.bizCode"),
            column("namespaceCode", "tianshu.namespaces", "fields.namespaceCode"),
            column("namespace", "common", "common.name"),
            column("description", "common", "common.description"),
            column("enabled", "common", "common.enabled"),
            column("updatedAt", "common", "common.updatedAt")));
    public static final ListDefinitionBinding BINDING = new ListDefinitionBinding(DEFINITION, "tianshu.component.namespaces.main",
            "tianshu.api.namespace.page", "tianshu.api.listTemplate.saveSystem", "tianshu.api.listTemplate.savePersonal");

    private DdcNamespaceListDefinition() { }

    private static ListDefinition.Column column(String key, String labelPage, String labelKey) {
        return new ListDefinition.Column(key, List.of("tianshu.field.DdcNamespaceResult." + key),
                labelPage, labelKey, true, true);
    }
}
