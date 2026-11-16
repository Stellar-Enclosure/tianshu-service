package top.egon.cola.component.tianshu.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;

import java.util.List;

/** Hybrid vector + PG FTS search response carrying global degradation flag and scored hits. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Hybrid vector + FTS search result with degradation indicator")
public class DdcSiSemanticSearchResult {

    @Schema(description = "True when embedding model is unavailable and results fell back to PG FTS")
    private boolean degradedToFtsOnly;

    @Schema(description = "Total matched items returned")
    private int totalHits;

    @Schema(description = "Ranked search hits")
    private List<DdcSiSearchHit> hits;
}
