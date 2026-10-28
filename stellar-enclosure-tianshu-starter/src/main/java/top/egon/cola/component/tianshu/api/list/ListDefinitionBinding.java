package top.egon.cola.component.tianshu.api.list;

import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;

/** Binds a list to its owning application's existing API authorization boundaries. */
public record ListDefinitionBinding(ListDefinition definition, String componentKey, String pageApiKey,
                                    String systemTemplateApiKey, String personalTemplateApiKey) {
    public ListDefinitionBinding {
        Objects.requireNonNull(definition, "definition");
        for (String key : new String[]{componentKey, pageApiKey, systemTemplateApiKey, personalTemplateApiKey}) {
            if (key == null || key.length() > 200 || !key.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
                throw new IllegalArgumentException("Invalid list authorization binding");
            }
        }
    }

    /** Bind delegated scope mappings to the activated list metadata, not a caller-selected rule. */
    public static String scopePolicyDigest(boolean enabled, Map<String, Set<String>> scopes) {
        Objects.requireNonNull(scopes, "permission scopes");
        Map<String, Object> sorted = new TreeMap<>();
        scopes.forEach((code, values) -> sorted.put(code, Objects.requireNonNull(values, "scopes").stream().sorted().toList()));
        return CanonicalJson.digest(Map.of("enabled", enabled, "permissionScopes", sorted));
    }
}
