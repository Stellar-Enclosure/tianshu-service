package top.egon.cola.component.tianshu.model.si;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Idempotent startup self-registration payload submitted by downstream service instances.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiSelfRegistrationRequest {

    @NotNull
    @Positive
    private Long tenantId;

    @NotBlank
    private String bizCode;

    @NotBlank
    private String namespaceCode;

    @NotBlank
    private String envCode;

    @NotBlank
    private String serviceName;

    @NotBlank
    private String instanceId;

    @NotNull
    private DdcSiKindEnum kind;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9._:-]{1,126}$")
    private String resourceKey;

    @NotBlank
    @Size(max = 256)
    private String displayName;

    @Nullable
    @Size(max = 2000)
    private String description;

    @NotBlank
    @Size(max = 524288)
    private String specJson;

    @Nullable
    private String specHash;

    @Builder.Default
    private Map<String, String> labels = Collections.emptyMap();

    @Builder.Default
    private List<@Valid DdcSiReferenceDescriptor> references = Collections.emptyList();

    private boolean autoPublishIfNew;
}
