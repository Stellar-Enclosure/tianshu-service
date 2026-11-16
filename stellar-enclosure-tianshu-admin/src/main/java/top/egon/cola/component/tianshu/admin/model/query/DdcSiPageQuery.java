package top.egon.cola.component.tianshu.admin.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;

/** Multi-dimensional paginated catalog query for SI resources. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Paginated catalog query for SI resources")
public class DdcSiPageQuery {

    @Schema(description = "Optional business domain filter")
    @Size(max = 128)
    private String bizCode;

    @Schema(description = "Optional namespace filter")
    @Size(max = 128)
    private String namespaceCode;

    @Schema(description = "Optional environment filter")
    @Size(max = 32)
    private String envCode;

    @Schema(description = "Optional application filter")
    @Size(max = 128)
    private String appCode;

    @Schema(description = "Optional SI kind filter")
    private DdcSiKindEnum kind;

    @Schema(description = "Optional lifecycle status filter")
    private DdcSiLifecycleStatusEnum lifecycleStatus;

    @Schema(description = "Optional keyword search query")
    @Size(max = 256)
    private String keyword;

    @Schema(description = "Optional exact tag filter")
    @Size(max = 64)
    private String tag;

    @Schema(description = "One-based page number", defaultValue = "1")
    @Min(1)
    private int pageNo = 1;

    @Schema(description = "Bounded page size 1..100", defaultValue = "20")
    @Min(1)
    @Max(100)
    private int pageSize = 20;
}
