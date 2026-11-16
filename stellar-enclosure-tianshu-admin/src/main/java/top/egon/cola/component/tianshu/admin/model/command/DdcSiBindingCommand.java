package top.egon.cola.component.tianshu.admin.model.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

/** Command to pin or switch a resource version binding for a specific application scope. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Application scope version binding command")
public class DdcSiBindingCommand {

    @Schema(description = "Business domain code", example = "ai")
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[^\\p{Cntrl}]+$")
    private String bizCode;

    @Schema(description = "Namespace code", example = "core")
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[^\\p{Cntrl}]+$")
    private String namespaceCode;

    @Schema(description = "Environment code", example = "dev")
    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,31}$")
    private String envCode;

    @Schema(description = "Consumer application code", example = "tianji")
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[^\\p{Cntrl}]+$")
    private String appCode;

    @Schema(description = "Resource kind")
    @NotNull
    private DdcSiKindEnum kind;

    @Schema(description = "Bound resource key", example = "order-planner")
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[^\\p{Cntrl}]+$")
    private String resourceKey;

    @Schema(description = "Optional pinned version number; null tracks active published version")
    @Positive
    private Long pinnedVersionNo;
}
