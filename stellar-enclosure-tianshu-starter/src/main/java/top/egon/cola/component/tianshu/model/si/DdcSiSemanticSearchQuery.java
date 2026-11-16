package top.egon.cola.component.tianshu.model.si;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Hybrid semantic and full-text search query contract for SI resources.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiSemanticSearchQuery {

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
    private DdcSiKindEnum kind;

    @NotBlank
    @Size(max = 2000)
    private String queryText;

    @Builder.Default
    private Map<String, String> labels = Collections.emptyMap();

    @Min(1)
    @Max(50)
    @Builder.Default
    private int topK = 10;

    @Builder.Default
    private boolean includeDeprecated = false;
}
