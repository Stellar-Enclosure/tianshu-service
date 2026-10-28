package top.egon.cola.component.tianshu.api.list;

import java.util.List;

/** Result of a delegated list operation; all versions and identifiers are decimal strings. */
public record ListDelegationResponse(String definitionDigest, String catalogVersion, List<Column> columns,
        List<Template> templates, String selectedTemplateId, List<String> visibleColumns,
        boolean mayManageSystem, boolean mayManagePersonal, Template savedTemplate) {
    public record Column(String columnKey, String labelPage, String labelKey) { }
    public record Template(String id, String type, String name, boolean defaultTemplate, String version, List<String> columnKeys) { }
}
