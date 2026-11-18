package top.egon.cola.component.tianshu.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

/** Immutable configuration scope shared by the baseline, snapshot and receipt boundaries. */
@Getter
@EqualsAndHashCode
public final class DdcConfigScopeVO {

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String tenantId;
    @NotBlank
    private final String bizCode;
    @NotBlank
    private final String env;
    @NotBlank
    private final String appCode;
    @NotBlank
    @Pattern(regexp = "application\\.yml")
    private final String resourceName;

    @Builder
    @JsonCreator
    public DdcConfigScopeVO(
            @JsonProperty(value = "tenantId", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String tenantId,
            @JsonProperty(value = "bizCode", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String bizCode,
            @JsonProperty(value = "env", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String env,
            @JsonProperty(value = "appCode", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String appCode,
            @JsonProperty(value = "resourceName", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) String resourceName) {
        this.tenantId = positiveDecimal(tenantId, "tenantId");
        this.bizCode = token(bizCode, 128, "bizCode");
        this.env = token(env, 128, "env");
        this.appCode = token(appCode, 128, "appCode");
        if (!"application.yml".equals(resourceName)) {
            throw new IllegalArgumentException("CONFIG_RESOURCE_NAME_INVALID");
        }
        this.resourceName = resourceName;
    }

    static String positiveDecimal(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("CONFIG_DECIMAL_STRING_INVALID: " + field);
        }
        try {
            Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException("CONFIG_DECIMAL_STRING_INVALID: " + field);
        }
        return value;
    }

    static String token(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException("CONFIG_TOKEN_INVALID: " + field);
        }
        return value;
    }

    @JsonAnySetter
    private void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("CONFIG_SCOPE_FIELD_UNKNOWN");
    }
}
