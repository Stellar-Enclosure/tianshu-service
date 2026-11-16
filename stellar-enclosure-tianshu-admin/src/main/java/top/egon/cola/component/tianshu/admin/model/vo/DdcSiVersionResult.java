package top.egon.cola.component.tianshu.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.time.Instant;

/** Immutable published snapshot and cross-environment promotion lineage VO. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Immutable Super-Intelligence version snapshot response view")
public class DdcSiVersionResult {

    @Schema(description = "Version snapshot identifier")
    private Long id;

    @Schema(description = "Parent resource identifier")
    private Long resourceId;

    @Schema(description = "Monotonic version number")
    private Long versionNo;

    @Schema(description = "SI resource kind")
    private DdcSiKindEnum kind;

    @Schema(description = "Environment code")
    private String envCode;

    @Schema(description = "Immutable specification JSON snapshot")
    private String specJson;

    @Schema(description = "Content SHA-256 hex digest")
    private String digestSha256;

    @Schema(description = "Source environment if promoted")
    private String sourceEnvCode;

    @Schema(description = "Source version number if promoted")
    private Long sourceVersionNo;

    @Schema(description = "Release or promotion change note")
    private String changeNote;

    @Schema(description = "Semantic vector index status (PENDING/INDEXED/SKIPPED)")
    private String indexStatus;

    @Schema(description = "Operator who published or promoted this version")
    private String createUserId;

    @Schema(description = "Publication timestamp in UTC ISO-8601 format")
    private Instant publishedTime;

    @Schema(description = "Vector index completion timestamp in UTC ISO-8601 format")
    private Instant indexedTime;
}
