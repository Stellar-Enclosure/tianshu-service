package top.egon.cola.component.tianshu.model.si;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.lang.Nullable;

/**
 * Ranked result hit from hybrid RRF vector + FTS search.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiSearchHit {

    @NotNull
    @Valid
    private DdcSiResourceDescriptor resource;

    private double hybridScore;

    private double vectorScore;

    private double ftsScore;

    @Nullable
    private String matchedChunkText;

    private boolean degradedToFtsOnly;

    @Min(1)
    private int rank;
}
