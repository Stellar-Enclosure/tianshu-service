package top.egon.cola.component.tianshu.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

/** Directed dependency edge and archive-blocker topology item VO. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "SI dependency reference or archive blocker view")
public class DdcSiReferenceResult {

    @Schema(description = "Reference identifier")
    private Long id;

    @Schema(description = "Source resource identifier if applicable")
    private Long sourceResourceId;

    @Schema(description = "Source version number if applicable")
    private Long sourceVersionNo;

    @Schema(description = "Source entity type (AGENT, SKILL, PROCESS, etc.)")
    private DdcSiReferenceTargetTypeEnum sourceType;

    @Schema(description = "Source entity key")
    private String sourceKey;

    @Schema(description = "Target entity type")
    private DdcSiReferenceTargetTypeEnum targetType;

    @Schema(description = "Target SI kind")
    private DdcSiKindEnum targetKind;

    @Schema(description = "Target entity key")
    private String targetKey;

    @Schema(description = "Pinned target version number")
    private Long pinnedVersionNo;

    @Schema(description = "Environment code")
    private String envCode;

    @Schema(description = "Whether the reference is mandatory")
    private boolean required;

    @Schema(description = "Optional role hint")
    private String roleHint;
}
