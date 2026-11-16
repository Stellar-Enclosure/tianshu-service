package top.egon.cola.component.tianshu.model.si;

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

/**
 * Immutable snapshot version descriptor including environment promotion lineage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiVersionDescriptor {

    @Nullable
    @Positive
    private Long versionId;

    @NotNull
    @Positive
    private Long resourceId;

    @NotNull
    @Positive
    private Integer versionNo;

    @NotNull
    private DdcSiLifecycleStatusEnum status;

    @NotBlank
    private String digestSha256;

    @NotBlank
    private String specJson;

    @Nullable
    private String changeSummary;

    @Nullable
    private String sourceEnvCode;

    @Nullable
    @Positive
    private Integer sourceVersionNo;

    @Nullable
    private String publishedBy;

    @Nullable
    private Instant publishedAt;
}
