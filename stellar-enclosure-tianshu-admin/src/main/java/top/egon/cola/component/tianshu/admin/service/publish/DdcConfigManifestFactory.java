package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigSnapshotService;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Freezes and verifies one native immutable version without consulting mutable runtime state. */
@Slf4j
@Validated
@Component("ddcConfigManifestFactory")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigManifestFactory {

    public static final String HOT_SCHEMA_DEPENDENCY_KEY = "tianshu.config-hot-schema";
    private static final String PLACEHOLDER_DIGEST = "sha256:" + "0".repeat(64);
    private static final int MAX_MANIFEST_BYTES = 1_048_576;
    private static final int MAX_BASELINE_BYTES = 900 * 1024;
    private static final Set<String> MANIFEST_FIELDS = Set.of("schemaVersion", "scope", "source", "enabled",
            "contentSha256", "restartDigest", "dependencySnapshotId", "rules", "targetBindings",
            "startupDependencies", "secretReferences");

    @NonNull @Qualifier("jacksonObjectMapper") private final ObjectMapper json;

    public FrozenConfigVO freeze(@Valid @NotNull DdcTenantContextDTO tenant,
            @Valid @NotNull DdcConfigItemBO item, long revision, String yaml, boolean enabled,
            @Valid @NotNull PublicationPolicyVO policy) {
        Objects.requireNonNull(tenant, "TIANSHU_CONFIG_TENANT_REQUIRED");
        Objects.requireNonNull(item, "TIANSHU_CONFIG_ITEM_REQUIRED");
        Objects.requireNonNull(policy, "TIANSHU_CONFIG_POLICY_REQUIRED");
        if (item.getId() == null || item.getId() <= 0 || item.getDeletedAt() != null || revision <= 0
                || !Objects.equals(tenant.tenantId(), item.getTenantId())
                || !Long.toString(tenant.tenantId()).equals(policy.scope().getTenantId())
                || !sameScope(item, policy.scope()) || !"yaml".equalsIgnoreCase(item.getFormat())) {
            throw invalid();
        }
        if (policy.startupDependencies().containsKey(HOT_SCHEMA_DEPENDENCY_KEY)) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_HOT_SCHEMA_DEPENDENCY_RESERVED");
        }
        try {
            var projection = DdcConfigSnapshotService.projectValues(yaml, enabled, policy.rules());
            Map<String, String> dependencies = new LinkedHashMap<>(policy.startupDependencies());
            dependencies.put(HOT_SCHEMA_DEPENDENCY_KEY, hotSchemaDigest(projection.rules()));
            var seed = baseline(policy.scope(), new DdcConfigBaselineVO.SourceVO(Long.toString(item.getId()),
                    Long.toString(revision)), enabled, PLACEHOLDER_DIGEST, PLACEHOLDER_DIGEST,
                    policy.dependencySnapshotId(), projection, policy.targetBindings(), dependencies,
                    policy.secretReferences());
            String restartDigest = DdcConfigCanonicalJson.restartDigest(seed);
            var restarted = baseline(seed.getScope(), seed.getSource(), enabled, PLACEHOLDER_DIGEST,
                    restartDigest, seed.getDependencySnapshotId(), projection, seed.getTargetBindings(),
                    seed.getStartupDependencies(), seed.getSecretReferences());
            var frozen = baseline(seed.getScope(), seed.getSource(), enabled,
                    DdcConfigCanonicalJson.contentSha256(restarted), restartDigest, seed.getDependencySnapshotId(),
                    projection, seed.getTargetBindings(), seed.getStartupDependencies(), seed.getSecretReferences());
            validateBaselineSize(frozen);
            return new FrozenConfigVO(manifest(frozen), frozen);
        } catch (IOException failure) {
            throw invalid();
        }
    }

    /** No legacy, latest-version, or missing-manifest fallback is permitted. */
    public FrozenConfigVO materialize(@Valid @NotNull DdcConfigVersionBO version,
            @Valid @NotNull DdcConfigScopeVO expectedScope) {
        Objects.requireNonNull(version, "TIANSHU_CONFIG_VERSION_REQUIRED");
        Objects.requireNonNull(expectedScope, "TIANSHU_CONFIG_SCOPE_REQUIRED");
        JsonNode manifest = version.getManifestJson();
        if (version.getConfigItemId() == null || version.getConfigItemId() <= 0 || version.getDeletedAt() != null
                || version.getConfigRevision() == null || version.getConfigRevision() <= 0
                || version.getTenantId() == null
                || !Long.toString(version.getTenantId()).equals(expectedScope.getTenantId())
                || !sameScope(version, expectedScope) || !"yaml".equalsIgnoreCase(version.getFormat())
                || manifest == null || !manifest.isObject() || manifest.size() != MANIFEST_FIELDS.size()) {
            throw invalid();
        }
        manifest.fieldNames().forEachRemaining(field -> {
            if (!MANIFEST_FIELDS.contains(field)) { throw invalid(); }
        });
        if (DdcConfigCanonicalJson.canonicalBytes(manifest).length > MAX_MANIFEST_BYTES) { throw invalid(); }
        try {
            ObjectNode complete = (ObjectNode) manifest.deepCopy();
            complete.set("restartValues", json.createObjectNode());
            complete.set("hotSeed", json.createObjectNode());
            var metadata = json.treeToValue(complete, DdcConfigBaselineVO.class);
            if (!expectedScope.equals(metadata.getScope())
                    || !Long.toString(version.getConfigItemId()).equals(metadata.getSource().getConfigId())
                    || !Long.toString(version.getConfigRevision()).equals(metadata.getSource().getRevision())) {
                throw invalid();
            }
            var projection = DdcConfigSnapshotService.projectValues(version.getNewContent(), metadata.getEnabled(),
                    metadata.getRules());
            // A manifest must freeze the entire effective classification, including default RESTART keys.
            if (!metadata.getRules().equals(projection.rules())
                    || !Objects.equals(metadata.getStartupDependencies().get(HOT_SCHEMA_DEPENDENCY_KEY),
                    hotSchemaDigest(metadata.getRules()))) {
                throw invalid();
            }
            var result = baseline(metadata.getScope(), metadata.getSource(), metadata.getEnabled(),
                    metadata.getContentSha256(), metadata.getRestartDigest(), metadata.getDependencySnapshotId(),
                    projection, metadata.getTargetBindings(), metadata.getStartupDependencies(),
                    metadata.getSecretReferences());
            if (!result.getRestartDigest().equals(DdcConfigCanonicalJson.restartDigest(result))
                    || !result.getContentSha256().equals(DdcConfigCanonicalJson.contentSha256(result))) {
                throw invalid();
            }
            validateBaselineSize(result);
            return new FrozenConfigVO(manifest, result);
        } catch (IOException failure) {
            throw invalid();
        }
    }

    private JsonNode manifest(DdcConfigBaselineVO baseline) {
        ObjectNode result = json.valueToTree(baseline);
        result.remove(List.of("restartValues", "hotSeed"));
        if (result.size() != MANIFEST_FIELDS.size()
                || DdcConfigCanonicalJson.canonicalBytes(result).length > MAX_MANIFEST_BYTES) { throw invalid(); }
        return result;
    }

    private void validateBaselineSize(DdcConfigBaselineVO baseline) {
        if (DdcConfigCanonicalJson.canonicalBytes(json.valueToTree(baseline)).length > MAX_BASELINE_BYTES) {
            throw invalid();
        }
    }

    private String hotSchemaDigest(List<DdcConfigBaselineVO.RuleVO> rules) {
        ObjectNode schema = json.createObjectNode();
        schema.put("schemaVersion", "1");
        var array = schema.putArray("rules");
        rules.stream().filter(rule -> rule.getMode() == DdcConfigBaselineVO.ReloadModeEnum.HOT)
                .sorted(Comparator.comparing(DdcConfigBaselineVO.RuleVO::getKey))
                .forEach(rule -> array.add(json.valueToTree(rule)));
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(DdcConfigCanonicalJson.canonicalBytes(schema)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static DdcConfigBaselineVO baseline(DdcConfigScopeVO scope, DdcConfigBaselineVO.SourceVO source,
            boolean enabled, String contentDigest, String restartDigest, String dependencySnapshotId,
            DdcConfigSnapshotService.ConfigProjectionVO projection,
            List<DdcConfigBaselineVO.TargetBindingVO> targets, Map<String, String> dependencies,
            Map<String, DdcConfigBaselineVO.SecretReferenceVO> secrets) {
        if (targets.isEmpty() || targets.size() > 32
                || targets.stream().anyMatch(target -> !"Deployment".equals(target.getWorkloadKind()))) {
            throw invalid();
        }
        validateSecretSlots(projection.rules(), secrets);
        return new DdcConfigBaselineVO("1", scope, source, enabled, contentDigest, restartDigest,
                dependencySnapshotId, projection.rules(), targets, projection.restartValues(), projection.hotValues(),
                dependencies, secrets);
    }

    /** References reserve their property slot; values belong exclusively to the Secret mount/environment. */
    private static void validateSecretSlots(List<DdcConfigBaselineVO.RuleVO> rules,
            Map<String, DdcConfigBaselineVO.SecretReferenceVO> references) {
        var slots = new java.util.ArrayList<ConfigurationPropertyName>();
        for (String alias : references.keySet()) {
            var slot = ConfigurationPropertyName.adapt(alias.replace('_', '.'), '.');
            if (slot.isEmpty() || slots.contains(slot)) { throw invalid(); }
            slots.add(slot);
        }
        for (var rule : rules) {
            var key = ConfigurationPropertyName.adapt(rule.getKey().replace('_', '.'), '.');
            if (slots.stream().anyMatch(slot -> slot.equals(key) || slot.isAncestorOf(key) || key.isAncestorOf(slot))) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_SECRET_SLOT_VALUE_FORBIDDEN");
            }
        }
    }

    private static boolean sameScope(DdcConfigItemBO item, DdcConfigScopeVO scope) {
        return Objects.equals(item.getBizCode(), scope.getBizCode()) && Objects.equals(item.getEnv(), scope.getEnv())
                && Objects.equals(item.getAppCode(), scope.getAppCode())
                && Objects.equals(item.getResourceName(), scope.getResourceName());
    }

    private static boolean sameScope(DdcConfigVersionBO version, DdcConfigScopeVO scope) {
        return Objects.equals(version.getBizCode(), scope.getBizCode()) && Objects.equals(version.getEnv(), scope.getEnv())
                && Objects.equals(version.getAppCode(), scope.getAppCode())
                && Objects.equals(version.getResourceName(), scope.getResourceName());
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("TIANSHU_CONFIG_MANIFEST_INVALID");
    }

    public record PublicationPolicyVO(@Valid @NotNull DdcConfigScopeVO scope,
            List<DdcConfigBaselineVO.RuleVO> rules, List<DdcConfigBaselineVO.TargetBindingVO> targetBindings,
            String dependencySnapshotId, Map<String, String> startupDependencies,
            Map<String, DdcConfigBaselineVO.SecretReferenceVO> secretReferences) {
        public PublicationPolicyVO {
            Objects.requireNonNull(scope, "TIANSHU_CONFIG_SCOPE_REQUIRED");
            rules = List.copyOf(rules);
            targetBindings = List.copyOf(targetBindings);
            startupDependencies = Map.copyOf(startupDependencies);
            secretReferences = Map.copyOf(secretReferences);
        }
    }

    /** Supplied exclusively by the trusted mounted publication policy, never by an HTTP request or live lease. */
    @FunctionalInterface
    public interface PublicationPolicyResolver {
        PublicationPolicyVO require(DdcTenantContextDTO trustedTenant, DdcConfigScopeVO scope);
    }

    public record FrozenConfigVO(JsonNode manifest, DdcConfigBaselineVO baseline) {
        public FrozenConfigVO {
            manifest = Objects.requireNonNull(manifest, "manifest").deepCopy();
            Objects.requireNonNull(baseline, "baseline");
        }
        @Override public JsonNode manifest() { return manifest.deepCopy(); }
        @Override public String toString() {
            return "FrozenConfig[configId=" + baseline.getSource().getConfigId()
                    + ", revision=" + baseline.getSource().getRevision() + "]";
        }
    }
}
