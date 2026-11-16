package top.egon.cola.component.tianshu.model.si;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
}
