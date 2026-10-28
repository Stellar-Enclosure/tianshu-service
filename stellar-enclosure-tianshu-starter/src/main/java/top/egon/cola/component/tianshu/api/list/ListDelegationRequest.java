package top.egon.cola.component.tianshu.api.list;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Service-to-service list request. Tenant and subject come only from verified credentials. */
public record ListDelegationRequest(ListDefinitionBinding binding, Operation operation, String catalogVersion,
        String authVersion, String policyVersion, List<String> activeRootRoleIds, Map<String, Set<String>> permissionScopes,
        String templateId, String expectedVersion, String name, List<String> columnKeys, Boolean defaultTemplate,
        boolean includeSystemMetadata) {
    public enum Operation { LOAD, SAVE_SYSTEM, SAVE_PERSONAL, SELECT, DELETE_SYSTEM, DELETE_PERSONAL }

    public ListDelegationRequest {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(operation, "operation");
        decimal(catalogVersion, false); decimal(authVersion, true); decimal(policyVersion, true);
        activeRootRoleIds = List.copyOf(Objects.requireNonNull(activeRootRoleIds, "activeRootRoleIds"));
        if (activeRootRoleIds.size() > 32 || activeRootRoleIds.stream().distinct().count() != activeRootRoleIds.size()) throw invalid();
        activeRootRoleIds.forEach(value -> decimal(value, false));
        if (permissionScopes == null || permissionScopes.size() > 128) throw invalid();
        Map<String, Set<String>> mappings = new TreeMap<>();
        permissionScopes.forEach((code, scopes) -> {
            if (!validScope(code) || scopes == null || scopes.isEmpty() || scopes.size() > 64
                    || scopes.stream().anyMatch(scope -> !validScope(scope))) throw invalid();
            mappings.put(code, Set.copyOf(scopes));
        });
        permissionScopes = Map.copyOf(mappings);
        if (templateId != null) decimal(templateId, false);
        if (expectedVersion != null) decimal(expectedVersion, true);
        switch (operation) {
            case LOAD -> {
                if (templateId != null || expectedVersion != null || name != null || columnKeys != null || defaultTemplate != null) throw invalid();
            }
            case SAVE_SYSTEM, SAVE_PERSONAL -> {
                if (name == null || name.isBlank() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl)
                        || columnKeys == null || columnKeys.size() > 256 || columnKeys.stream().anyMatch(key -> !validIdentifier(key))
                        || columnKeys.stream().distinct().count() != columnKeys.size() || defaultTemplate == null
                        || (templateId == null) != (expectedVersion == null) || includeSystemMetadata) throw invalid();
                columnKeys = List.copyOf(columnKeys);
            }
            case SELECT -> {
                if (templateId == null || expectedVersion != null || name != null || columnKeys != null || defaultTemplate != null || includeSystemMetadata) throw invalid();
            }
            case DELETE_SYSTEM, DELETE_PERSONAL -> {
                if (templateId == null || expectedVersion == null || name != null || columnKeys != null || defaultTemplate != null || includeSystemMetadata) throw invalid();
            }
        }
    }

    private static void decimal(String value, boolean allowZero) {
        try {
            if (value == null || !value.matches(allowZero ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}")
                    || Long.parseLong(value) < (allowZero ? 0 : 1)) throw invalid();
        } catch (NumberFormatException invalid) { throw invalid(); }
    }
    private static boolean validIdentifier(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"); }
    private static boolean validScope(String value) { return value != null && value.matches("[A-Za-z0-9._:-]{1,128}"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid list delegation request"); }
}
