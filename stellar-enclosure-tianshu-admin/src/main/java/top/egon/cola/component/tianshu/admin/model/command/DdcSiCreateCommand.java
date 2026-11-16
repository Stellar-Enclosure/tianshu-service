package top.egon.cola.component.tianshu.admin.model.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.util.List;

/** Command to create a draft Super-Intelligence atomic resource. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Create atomic Super-Intelligence draft resource command")
public class DdcSiCreateCommand {

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

    @Schema(description = "Target environment code", example = "dev")
    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,31}$")
    private String envCode;

    @Schema(description = "Owning application code", example = "tianji")
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[^\\p{Cntrl}]+$")
    private String appCode;

    @Schema(description = "SI resource kind: AGENT, MCP, PROMPT, SKILL")
    @NotNull
    private DdcSiKindEnum kind;

    @Schema(description = "Unique resource key within scope and kind", example = "order-planner")
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}$")
    private String resourceKey;

    @Schema(description = "Human-readable display name", example = "Order Planner Agent")
    @NotBlank
    @Size(max = 256)
    private String displayName;

    @Schema(description = "Summary description")
    @Size(max = 2000)
    private String description;

    @Schema(description = "Discovery tags")
    @Size(max = 32)
    private List<@NotBlank @Size(max = 64) String> tags;

    @Schema(description = "Registration source type", example = "CONSOLE")
    private DdcSiSourceTypeEnum sourceType;

    @Schema(description = "Normalized JSON specification payload")
    @NotBlank
    @Size(max = 1048576)
    private String specJson;
}
