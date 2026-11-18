package top.egon.cola.component.tianshu.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

import java.time.Instant;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Complete typed HOT snapshot; transport validity is deliberately separate from its content digest. */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class DdcConfigSnapshotDTO {

    @NotBlank
    @Pattern(regexp = "1")
    private final String schemaVersion;
    @Valid
    @NotNull
    private final DdcConfigScopeVO scope;
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String releaseRevision;
    @NotNull
    private final Boolean enabled;
    @NotBlank
    @Pattern(regexp = "sha256:[0-9a-f]{64}")
    private final String restartDigest;
    @NotBlank
    @Pattern(regexp = "sha256:[0-9a-f]{64}")
    private final String contentSha256;
    @NotNull
    private final Map<String, JsonNode> hotValues;
    private final String dependencySnapshotId;
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant validUntil;

    @Builder
    @JsonCreator
    public DdcConfigSnapshotDTO(
            @JsonProperty(value = "schemaVersion", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String schemaVersion,
            @JsonProperty(value = "scope", required = true) DdcConfigScopeVO scope,
            @JsonProperty(value = "releaseRevision", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String releaseRevision,
            @JsonProperty(value = "enabled", required = true)
            @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean enabled,
            @JsonProperty(value = "restartDigest", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String restartDigest,
            @JsonProperty(value = "contentSha256", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String contentSha256,
            @JsonProperty(value = "hotValues", required = true) Map<String, JsonNode> hotValues,
            @JsonProperty(value = "dependencySnapshotId", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String dependencySnapshotId,
            @JsonProperty(value = "validUntil", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.UtcInstantDeserializer.class) Instant validUntil) {
        this.schemaVersion = schema(schemaVersion);
        this.scope = Objects.requireNonNull(scope, "CONFIG_SCOPE_REQUIRED");
        this.releaseRevision = DdcConfigScopeVO.positiveDecimal(releaseRevision, "releaseRevision");
        this.enabled = Objects.requireNonNull(enabled, "CONFIG_ENABLED_REQUIRED");
        this.restartDigest = digest(restartDigest);
        this.contentSha256 = digest(contentSha256);
        this.hotValues = values(hotValues, "hotValues");
        if (!enabled && !this.hotValues.isEmpty()) {
            throw new IllegalArgumentException("CONFIG_DISABLED_HOT_VALUES_MUST_BE_EMPTY");
        }
        this.dependencySnapshotId = dependencySnapshotId == null ? null
                : DdcConfigScopeVO.token(dependencySnapshotId, 256, "dependencySnapshotId");
        this.validUntil = Objects.requireNonNull(validUntil, "CONFIG_VALID_UNTIL_REQUIRED");
    }

    /** A caller receives fresh JSON containers and cannot mutate the stored snapshot through an accessor. */
    public Map<String, JsonNode> getHotValues() {
        return values(hotValues, "hotValues");
    }

    static String schema(String value) {
        if (!"1".equals(value)) {
            throw new IllegalArgumentException("CONFIG_SCHEMA_VERSION_UNSUPPORTED");
        }
        return value;
    }

    static String digest(String value) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CONFIG_DIGEST_INVALID");
        }
        return value;
    }

    static Map<String, JsonNode> values(Map<String, JsonNode> source, String field) {
        Objects.requireNonNull(source, "CONFIG_VALUES_REQUIRED: " + field);
        if (source.size() > 10_000) {
            throw new IllegalArgumentException("CONFIG_VALUES_TOO_LARGE: " + field);
        }
        Map<String, JsonNode> copied = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > 256) {
                throw new IllegalArgumentException("CONFIG_VALUE_KEY_INVALID");
            }
            jsonValue(value, 0);
            copied.put(key, value.deepCopy());
        });
        return Collections.unmodifiableMap(copied);
    }

    private static void jsonValue(JsonNode node, int depth) {
        if (node == null || depth > 64 || node.isMissingNode() || node.isPojo() || node.isBinary()
                || node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException("CONFIG_JSON_VALUE_INVALID");
        }
        if (node.isContainerNode()) {
            node.forEach(child -> jsonValue(child, depth + 1));
        }
    }

    @JsonAnySetter
    private void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("CONFIG_SNAPSHOT_FIELD_UNKNOWN");
    }

    /** Required flags retain JSON type and presence instead of accepting text or integer coercion. */
    public static final class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_TRUE)) { return Boolean.TRUE; }
            if (parser.hasToken(JsonToken.VALUE_FALSE)) { return Boolean.FALSE; }
            return context.reportInputMismatch(Boolean.class, "A configuration flag must be a JSON boolean");
        }
    }
}
