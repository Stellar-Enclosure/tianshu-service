package top.egon.cola.component.tianshu.model.si;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
 * Paginated catalog query contract for downstream SI consumers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiCatalogQuery {

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
    private String appCode;

    @Nullable
    private DdcSiKindEnum kind;

    @Builder.Default
    private List<String> resourceKeys = Collections.emptyList();

    @Builder.Default
    private Map<String, String> labels = Collections.emptyMap();

    @Builder.Default
    private boolean includeDeprecated = true;

    @Nullable
    private String keyword;

    @Min(1)
    @Builder.Default
    private int pageNo = 1;

    @Min(1)
    @Max(200)
    @Builder.Default
    private int pageSize = 50;
}
