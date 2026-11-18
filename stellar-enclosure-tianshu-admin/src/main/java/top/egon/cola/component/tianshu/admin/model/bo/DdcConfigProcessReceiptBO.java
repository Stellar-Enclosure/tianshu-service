package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import java.time.LocalDateTime;

/** Internal authorized observation; credentials never enter this object or its rendering. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class DdcConfigProcessReceiptBO {
    @Positive private Long id;
    @NotNull @Positive private Long tenantId;
    @Size(max = 512) private String createUserId;
    private Instant createTime;
    @Size(max = 512) private String updateUserId;
    private Instant updateTime;
    @jakarta.validation.constraints.Null private LocalDateTime deletedAt;
    private Long version;

    @NotNull @Positive
    private Long configId;

    @NotNull @Positive
    private Long configRevision;

    @NotNull
    private JsonNode scopeJson;

    @NotNull @Size(max = 128) @Pattern(regexp = "[\\x21-\\x7e]+")
    private String targetBindingId;

    @NotNull @Positive
    private Long targetRevision;

    @NotNull @Size(max = 64) @Pattern(regexp = "[\\x21-\\x7e]+")
    private String clusterId;

    @NotNull @Size(max = 63) @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")
    private String k8sNamespace;

    @NotNull @Size(max = 64) @Pattern(regexp = "[\\x21-\\x7e]+")
    private String workloadUid;

    @NotNull @Size(max = 64) @Pattern(regexp = "[\\x21-\\x7e]+")
    private String podUid;

    @NotNull @Size(max = 63) @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")
    private String containerName;

    @NotNull @Size(max = 256) @Pattern(regexp = "[\\x21-\\x7e]+")
    private String containerId;

    @NotNull @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private String bootId;

    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String contentDigest;

    @Positive
    private Long appliedRevision;

    @Pattern(regexp = "[0-9a-f]{64}")
    private String appliedDigest;

    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String restartDigest;

    @NotNull @Pattern(regexp = "APPLIED|DISABLED_APPLIED|REJECTED|RESTART_REQUIRED|FAILED")
    private String result;

    @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String reasonCode;

    @Size(max = 1024)
    private String reasonMessage;

    @NotNull @Positive
    private Long reportSeq;

    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String receiptDigest;

    @NotNull
    private Instant reportedAt;

    private Instant appliedAt;

    private Instant lastReceivedAt;

    /** Committed container boot authority, independently verified before this boundary. */
    @JsonIgnore @NotNull @Positive
    private Long bootFence;

    /** Exact immutable authorized version JSON, never a current-state fallback. */
    @JsonIgnore @NotNull
    private JsonNode authorizedManifest;

    public static class DdcConfigProcessReceiptBOBuilder {
        @Override public String toString() { return "DdcConfigProcessReceiptBOBuilder"; }
    }
}
