package top.egon.cola.component.tianshu.model.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

/**
 * Untrusted process report for one target revision. Identity, boot fencing, digest verification,
 * sequence ordering and state transitions remain the receiving service's responsibility.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DdcConfigReceiptCommand {

    @NotBlank
    @Pattern(regexp = "1")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String schemaVersion;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String bootId;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String releaseRevision;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String reportSeq;

    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String appliedRevision;

    @NotBlank
    @Pattern(regexp = "sha256:[0-9a-f]{64}")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String contentSha256;

    @Pattern(regexp = "sha256:[0-9a-f]{64}")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String appliedContentSha256;

    @NotBlank
    @Pattern(regexp = "sha256:[0-9a-f]{64}")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String baselineRestartDigest;

    @NotBlank
    @Pattern(regexp = "sha256:[0-9a-f]{64}")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String receiptDigest;

    @NotBlank
    @Size(max = 8192)
    @Pattern(regexp = "[\\x21-\\x7e]+")
    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    @ToString.Exclude
    private String deliveryToken;

    @NotNull
    private DdcConfigReceiptStatusEnum status;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    @JsonDeserialize(using = DdcConfigCanonicalJson.UtcInstantDeserializer.class)
    private Instant reportedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    @JsonDeserialize(using = DdcConfigCanonicalJson.UtcInstantDeserializer.class)
    private Instant appliedAt;

    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    private String errorCode;

    @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
    @ToString.Exclude
    private String errorMessage;

    /** Blank errors have exactly the same wire and digest representation as absent errors. */
    @Size(max = 64)
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    public String getErrorCode() {
        return blankToNull(errorCode);
    }

    public DdcConfigReceiptCommand setErrorCode(String errorCode) {
        this.errorCode = blankToNull(errorCode);
        return this;
    }

    @Size(max = 1024)
    public String getErrorMessage() {
        return blankToNull(errorMessage);
    }

    public DdcConfigReceiptCommand setErrorMessage(String errorMessage) {
        this.errorMessage = blankToNull(errorMessage);
        return this;
    }

    /** Checks only the report's local shape; this method never authenticates an applied result. */
    @AssertTrue(message = "configuration receipt result shape is invalid")
    @JsonIgnore
    public boolean isReceiptShapeValid() {
        if (status == null || (appliedRevision == null) != (appliedContentSha256 == null)
                || appliedRevision == null && appliedAt != null) {
            return false;
        }
        if (status.isSuccessful()) {
            return appliedRevision != null && Objects.equals(appliedRevision, releaseRevision)
                    && Objects.equals(appliedContentSha256, contentSha256) && appliedAt != null
                    && getErrorCode() == null && getErrorMessage() == null;
        }
        return getErrorCode() != null;
    }

    /** Unknown transport fields must not become alternative scope, pod or authorization claims. */
    @JsonAnySetter
    public void rejectUnknownField(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException("CONFIG_RECEIPT_FIELD_UNKNOWN");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Suppresses sensitive inputs while Lombok supplies the builder's construction methods. */
    public static class DdcConfigReceiptCommandBuilder {
        @Override
        public String toString() {
            return "DdcConfigReceiptCommandBuilder";
        }
    }
}
