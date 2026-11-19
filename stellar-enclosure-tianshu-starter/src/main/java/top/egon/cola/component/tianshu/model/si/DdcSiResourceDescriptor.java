package top.egon.cola.component.tianshu.model.si;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Cross-service descriptor representing an SI resource snapshot and metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiResourceDescriptor {

    @NotNull
    @Positive
    private Long tenantId;

    @NotBlank
    private String bizCode;

    @NotBlank
    private String namespaceCode;

    @NotBlank
    private String envCode;

    @Nullable
    @Positive
    private Long resourceId;

    @NotNull
    private DdcSiKindEnum kind;

    @NotBlank
    private String resourceKey;

    @NotBlank
    private String displayName;

    @Nullable
    private String description;

    @NotNull
    private DdcSiLifecycleStatusEnum status;

    @NotNull
    private DdcSiSourceTypeEnum sourceType;

    @Nullable
    @Positive
    private Integer activeVersionNo;

    @Nullable
    private String digestSha256;

    @Builder.Default
    private Map<String, String> labels = Collections.emptyMap();

    @Builder.Default
    private List<@Valid DdcSiReferenceDescriptor> references = Collections.emptyList();

    @Nullable
    private String specJson;

    @Nullable
    private Instant publishedAt;

    @Nullable
    private Instant updatedAt;

    /** Null identifies namespace ownership; caller identity is supplied independently at the catalog port. */
    @Nullable @JsonInclude(JsonInclude.Include.NON_NULL)
    private String ownerAppCode;

    @Nullable @Positive @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resolvedVersionId;

    @Nullable @Positive @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resolvedVersionNo;

    /** Null is legacy metadata without an execution snapshot, never an empty dependency closure. */
    @Nullable @Valid @JsonInclude(JsonInclude.Include.NON_NULL)
    private DdcSiDependencyManifestVO dependencyManifest;

    /** Preserves the original metadata construction contract for existing clients. */
    public DdcSiResourceDescriptor(Long tenantId, String bizCode, String namespaceCode, String envCode, Long resourceId,
            DdcSiKindEnum kind, String resourceKey, String displayName, String description, DdcSiLifecycleStatusEnum status,
            DdcSiSourceTypeEnum sourceType, Integer activeVersionNo, String digestSha256, Map<String, String> labels,
            List<DdcSiReferenceDescriptor> references, String specJson, Instant publishedAt, Instant updatedAt) {
        this(tenantId, bizCode, namespaceCode, envCode, resourceId, kind, resourceKey, displayName, description, status,
                sourceType, activeVersionNo, digestSha256, labels, references, specJson, publishedAt, updatedAt, null, null, null, null);
    }

    /** Local structural consistency only; authorization and historical-body verification remain server responsibilities. */
    @JsonIgnore @AssertTrue(message = "SI execution identity must match its immutable dependency root")
    public boolean isExecutionSnapshotShapeValid() {
        if (ownerAppCode != null && (ownerAppCode.isBlank() || !ownerAppCode.equals(ownerAppCode.strip())
                || ownerAppCode.codePoints().anyMatch(Character::isISOControl))) return false;
        if (resolvedVersionId == null && resolvedVersionNo == null && dependencyManifest == null) return true;
        if (resolvedVersionId == null || resolvedVersionNo == null || dependencyManifest == null || tenantId == null || resourceId == null) return false;
        var root = dependencyManifest.root();
        return Long.toString(tenantId).equals(root.tenantId()) && Long.toString(resourceId).equals(root.resourceId())
                && Long.toString(resolvedVersionId).equals(root.versionId()) && Long.toString(resolvedVersionNo).equals(root.versionNo())
                && kind == root.kind() && java.util.Objects.equals(envCode, root.envCode())
                && java.util.Objects.equals(digestSha256, root.digestSha256());
    }
}
