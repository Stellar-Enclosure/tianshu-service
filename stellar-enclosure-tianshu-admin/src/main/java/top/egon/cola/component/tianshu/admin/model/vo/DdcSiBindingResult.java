package top.egon.cola.component.tianshu.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.time.Instant;

/** Application scope version pinning binding VO. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Application scope version binding response view")
public class DdcSiBindingResult {

    @Schema(description = "Binding identifier")
    private Long id;

    @Schema(description = "Business domain code")
    private String bizCode;

    @Schema(description = "Namespace identifier")
    private Long namespaceId;

    @Schema(description = "Environment code")
    private String envCode;

    @Schema(description = "Application code")
    private String appCode;

    @Schema(description = "Resource kind")
    private DdcSiKindEnum kind;

    @Schema(description = "Target resource identifier")
    private Long resourceId;

    @Schema(description = "Target resource key")
    private String resourceKey;

    @Schema(description = "Pinned immutable version number, or null when tracking latest ACTIVE version")
    private Long pinnedVersionNo;

    @Schema(description = "Binding status")
    private DdcSiBindingStatusEnum status;

    @Schema(description = "Binding timestamp in UTC ISO-8601 format")
    private Instant boundTime;
}
