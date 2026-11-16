package top.egon.cola.component.tianshu.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Cross-environment active baseline comparison matrix cell VO. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Cross-environment active version matrix entry")
public class DdcSiEnvMatrixResult {

    @Schema(description = "Version record identifier")
    private Long versionId;

    @Schema(description = "Resource identifier")
    private Long resourceId;

    @Schema(description = "Environment code (e.g., dev, test, staging, prod)")
    private String envCode;

    @Schema(description = "Active or promoted version number")
    private Long activeVersionNo;

    @Schema(description = "SHA-256 content digest")
    private String digestSha256;

    @Schema(description = "Origin environment if promoted")
    private String sourceEnvCode;

    @Schema(description = "Origin version number if promoted")
    private Long sourceVersionNo;

    @Schema(description = "Semantic vector index status")
    private String indexStatus;

    @Schema(description = "Published status flag")
    private boolean published;

    @Schema(description = "Publication timestamp in UTC ISO-8601 format")
    private Instant publishedTime;
}
