package top.egon.cola.component.tianshu.admin.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

/** Hybrid vector + PG full-text RRF search query request. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Natural language and keyword hybrid search query")
public class DdcSiSemanticSearchRequestQuery {

    @Schema(description = "Optional business domain filter")
    @Size(max = 128)
    private String bizCode;

    @Schema(description = "Optional namespace filter")
    @Size(max = 128)
    private String namespaceCode;

    @Schema(description = "Optional environment filter", example = "dev")
    @Size(max = 32)
    private String envCode;

    @Schema(description = "Optional SI kind filter")
    private DdcSiKindEnum kind;

    @Schema(description = "Natural language or keyword query", example = "order fulfillment planner")
    @NotBlank
    @Size(max = 2000)
    private String queryText;

    @Schema(description = "Maximum hits to return (1..50)", defaultValue = "20")
    @Min(1)
    @Max(50)
    private int topK = 20;
}
