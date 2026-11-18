package top.egon.cola.component.tianshu.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.Nullable;
import top.egon.cola.component.common.core.enums.EgonEnum;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable startup manifest. Secret references contain names and versions, never Secret values. */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class DdcConfigBaselineVO {

    @NotBlank @Pattern(regexp = "1")
    private final String schemaVersion;
    @NotNull @Valid
    private final DdcConfigScopeVO scope;
    @NotNull @Valid
    private final SourceVO source;
    @NotNull
    private final Boolean enabled;
    @NotBlank @Pattern(regexp = "sha256:[0-9a-f]{64}")
    private final String contentSha256;
    @NotBlank @Pattern(regexp = "sha256:[0-9a-f]{64}")
    private final String restartDigest;
    private final String dependencySnapshotId;
    @NotNull @Valid
    private final List<@NotNull @Valid RuleVO> rules;
    @NotNull @Valid
    private final List<@NotNull @Valid TargetBindingVO> targetBindings;
    @NotNull
    private final Map<String, JsonNode> restartValues;
    @NotNull
    private final Map<String, JsonNode> hotSeed;
    @NotNull
    private final Map<String, String> startupDependencies;
    @NotNull @Valid
    private final Map<String, @NotNull @Valid SecretReferenceVO> secretReferences;

    @Builder
    @JsonCreator
    public DdcConfigBaselineVO(
            @JsonProperty(value = "schemaVersion", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String schemaVersion,
            @JsonProperty(value = "scope", required = true) DdcConfigScopeVO scope,
            @JsonProperty(value = "source", required = true) SourceVO source,
            @JsonProperty(value = "enabled", required = true)
            @JsonDeserialize(using = DdcConfigSnapshotDTO.StrictBooleanDeserializer.class) Boolean enabled,
            @JsonProperty(value = "contentSha256", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String contentSha256,
            @JsonProperty(value = "restartDigest", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String restartDigest,
            @JsonProperty("dependencySnapshotId")
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String dependencySnapshotId,
            @JsonProperty(value = "rules", required = true) List<RuleVO> rules,
            @JsonProperty(value = "targetBindings", required = true) List<TargetBindingVO> targetBindings,
            @JsonProperty(value = "restartValues", required = true) Map<String, JsonNode> restartValues,
            @JsonProperty(value = "hotSeed", required = true) Map<String, JsonNode> hotSeed,
            @JsonProperty(value = "startupDependencies", required = true)
            @JsonDeserialize(contentUsing = DdcConfigCanonicalJson.WireStringDeserializer.class)
            Map<String, String> startupDependencies,
            @JsonProperty(value = "secretReferences", required = true) Map<String, SecretReferenceVO> secretReferences) {
        this.schemaVersion = DdcConfigSnapshotDTO.schema(schemaVersion);
        this.scope = Objects.requireNonNull(scope, "CONFIG_SCOPE_REQUIRED");
        this.source = Objects.requireNonNull(source, "CONFIG_SOURCE_REQUIRED");
        this.enabled = Objects.requireNonNull(enabled, "CONFIG_ENABLED_REQUIRED");
        this.contentSha256 = DdcConfigSnapshotDTO.digest(contentSha256);
        this.restartDigest = DdcConfigSnapshotDTO.digest(restartDigest);
        this.dependencySnapshotId = dependencySnapshotId == null ? null
                : DdcConfigScopeVO.token(dependencySnapshotId, 256, "dependencySnapshotId");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "CONFIG_RULES_REQUIRED"));
        var ruleKeys = new HashSet<String>();
        this.rules.forEach(rule -> {
            if (!ruleKeys.add(rule.getKey())) {
                throw new IllegalArgumentException("CONFIG_RULE_KEY_DUPLICATE");
            }
        });
        this.targetBindings = List.copyOf(Objects.requireNonNull(targetBindings, "CONFIG_TARGET_BINDINGS_REQUIRED"));
        var bindingIds = new HashSet<String>();
        this.targetBindings.forEach(binding -> {
            if (!bindingIds.add(binding.getBindingId())) {
                throw new IllegalArgumentException("CONFIG_TARGET_BINDING_DUPLICATE");
            }
        });
        this.restartValues = DdcConfigSnapshotDTO.values(restartValues, "restartValues");
        this.hotSeed = DdcConfigSnapshotDTO.values(hotSeed, "hotSeed");
        if (!enabled && !this.hotSeed.isEmpty()) {
            throw new IllegalArgumentException("CONFIG_DISABLED_HOT_VALUES_MUST_BE_EMPTY");
        }
        Objects.requireNonNull(startupDependencies, "CONFIG_STARTUP_DEPENDENCIES_REQUIRED");
        Map<String, String> dependencies = new LinkedHashMap<>();
        startupDependencies.forEach((name, version) -> dependencies.put(
                DdcConfigScopeVO.token(name, 256, "dependency name"),
                DdcConfigScopeVO.token(version, 256, "dependency version")));
        this.startupDependencies = Collections.unmodifiableMap(dependencies);
        Objects.requireNonNull(secretReferences, "CONFIG_SECRET_REFERENCES_REQUIRED");
        Map<String, SecretReferenceVO> secrets = new LinkedHashMap<>();
        secretReferences.forEach((name, reference) -> secrets.put(
                DdcConfigScopeVO.token(name, 256, "secret reference"),
                Objects.requireNonNull(reference, "CONFIG_SECRET_REFERENCE_REQUIRED")));
        this.secretReferences = Collections.unmodifiableMap(secrets);
    }

    public Map<String, JsonNode> getRestartValues() {
        return DdcConfigSnapshotDTO.values(restartValues, "restartValues");
    }

    public Map<String, JsonNode> getHotSeed() {
        return DdcConfigSnapshotDTO.values(hotSeed, "hotSeed");
    }

    @JsonAnySetter
    private void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("CONFIG_BASELINE_FIELD_UNKNOWN");
    }

    /** Immutable source identity of an already published configuration version. */
    @Getter
    @EqualsAndHashCode
    public static final class SourceVO {
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
        private final String configId;
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
        private final String revision;

        @Builder
        @JsonCreator
        public SourceVO(
                @JsonProperty(value = "configId", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String configId,
                @JsonProperty(value = "revision", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String revision) {
            this.configId = DdcConfigScopeVO.positiveDecimal(configId, "configId");
            this.revision = DdcConfigScopeVO.positiveDecimal(revision, "revision");
        }

        @JsonAnySetter
        private void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("CONFIG_SOURCE_FIELD_UNKNOWN");
        }
    }

    /** Declared reload capability and the explicit null/deletion semantics of one configuration key. */
    @Getter
    @EqualsAndHashCode
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static final class RuleVO {
        @NotBlank
        private final String key;
        @NotNull
        private final ReloadModeEnum mode;
        @NotBlank
        private final String valueType;
        private final String schemaRef;
        @NotNull
        private final Boolean nullable;
        @NotNull
        private final Boolean deletionAllowed;

        @Builder
        @JsonCreator
        public RuleVO(@JsonProperty(value = "key", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String key,
                @JsonProperty(value = "mode", required = true) ReloadModeEnum mode,
                @JsonProperty(value = "valueType", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String valueType,
                @JsonProperty(value = "schemaRef", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String schemaRef,
                @JsonProperty(value = "nullable", required = true)
                @JsonDeserialize(using = DdcConfigSnapshotDTO.StrictBooleanDeserializer.class) Boolean nullable,
                @JsonProperty(value = "deletionAllowed", required = true)
                @JsonDeserialize(using = DdcConfigSnapshotDTO.StrictBooleanDeserializer.class) Boolean deletionAllowed) {
            this.key = DdcConfigScopeVO.token(key, 256, "rule key");
            this.mode = Objects.requireNonNull(mode, "CONFIG_RULE_MODE_REQUIRED");
            this.valueType = DdcConfigScopeVO.token(valueType, 64, "valueType");
            this.schemaRef = schemaRef == null ? null : DdcConfigScopeVO.token(schemaRef, 256, "schemaRef");
            this.nullable = Objects.requireNonNull(nullable, "CONFIG_RULE_NULLABLE_REQUIRED");
            this.deletionAllowed = Objects.requireNonNull(deletionAllowed, "CONFIG_RULE_DELETION_ALLOWED_REQUIRED");
        }

        /** Evaluates type, explicit null, and deletion against this release's fixed key rule. */
        public boolean acceptsValue(@Nullable JsonNode value) {
            if (value == null) {
                return deletionAllowed;
            }
            if (value.isNull()) {
                return nullable;
            }
            return switch (valueType) {
                case "STRING", "DECIMAL_STRING" -> value.isTextual();
                case "BOOLEAN" -> value.isBoolean();
                case "INTEGER" -> value.isIntegralNumber();
                case "NUMBER" -> value.isNumber();
                case "OBJECT" -> value.isObject();
                case "ARRAY" -> value.isArray();
                default -> false;
            };
        }

        @JsonAnySetter
        private void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("CONFIG_RULE_FIELD_UNKNOWN");
        }
    }

    /** Stable wire codes remain independent from the persistence implementation used by a publisher. */
    public enum ReloadModeEnum implements EgonEnum {
        HOT(0), RESTART(1);

        private final int code;

        ReloadModeEnum(int code) { this.code = code; }
        @Override public int getCode() { return code; }
        @Override public String getMessage() { return name(); }
        @JsonValue public String wireValue() { return name(); }
        @JsonCreator public static ReloadModeEnum fromWire(String value) {
            for (ReloadModeEnum mode : values()) {
                if (mode.name().equals(value)) { return mode; }
            }
            throw new IllegalArgumentException("CONFIG_RULE_MODE_INVALID");
        }
    }

    /** A fixed deployed workload/container binding; these declarations grant no runtime authority by themselves. */
    @Getter
    @EqualsAndHashCode
    public static final class TargetBindingVO {
        private final String bindingId;
        private final String bindingRevision;
        private final String clusterId;
        private final String kubernetesNamespace;
        private final String workloadKind;
        private final String workloadName;
        private final String workloadUid;
        private final String containerName;

        @Builder
        @JsonCreator
        public TargetBindingVO(@JsonProperty(value = "bindingId", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String bindingId,
                @JsonProperty(value = "bindingRevision", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String bindingRevision,
                @JsonProperty(value = "clusterId", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String clusterId,
                @JsonProperty(value = "kubernetesNamespace", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String kubernetesNamespace,
                @JsonProperty(value = "workloadKind", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String workloadKind,
                @JsonProperty(value = "workloadName", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String workloadName,
                @JsonProperty(value = "workloadUid", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String workloadUid,
                @JsonProperty(value = "containerName", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String containerName) {
            this.bindingId = DdcConfigScopeVO.token(bindingId, 128, "bindingId");
            this.bindingRevision = DdcConfigScopeVO.positiveDecimal(bindingRevision, "bindingRevision");
            this.clusterId = DdcConfigScopeVO.token(clusterId, 128, "clusterId");
            this.kubernetesNamespace = DdcConfigScopeVO.token(kubernetesNamespace, 63, "kubernetesNamespace");
            this.workloadKind = DdcConfigScopeVO.token(workloadKind, 128, "workloadKind");
            this.workloadName = DdcConfigScopeVO.token(workloadName, 253, "workloadName");
            this.workloadUid = DdcConfigScopeVO.token(workloadUid, 128, "workloadUid");
            this.containerName = DdcConfigScopeVO.token(containerName, 63, "containerName");
        }

        @JsonAnySetter
        private void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("CONFIG_TARGET_BINDING_FIELD_UNKNOWN");
        }
    }

    @Getter
    @EqualsAndHashCode
    public static final class SecretReferenceVO {
        @NotBlank
        private final String name;
        @NotBlank
        private final String version;

        @Builder
        @JsonCreator
        public SecretReferenceVO(@JsonProperty(value = "name", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String name,
                @JsonProperty(value = "version", required = true)
                @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String version) {
            this.name = DdcConfigScopeVO.token(name, 253, "secret name");
            this.version = DdcConfigScopeVO.token(version, 256, "secret version");
        }

        @JsonAnySetter
        private void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("CONFIG_SECRET_REFERENCE_FIELD_UNKNOWN");
        }
    }
}
