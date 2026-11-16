package top.egon.cola.component.tianshu.admin.model.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/** Command to replace draft specification and metadata with optimistic concurrency control. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Update Super-Intelligence draft command")
public class DdcSiDraftCommand {

    @Schema(description = "Expected optimistic lock revision of the resource", example = "1")
    @NotNull
    @Min(0)
    private Long expectedVersion;

    @Schema(description = "Updated display name", example = "Order Planner Agent v2")
    @NotBlank
    @Size(max = 256)
    private String displayName;

    @Schema(description = "Updated description")
    @Size(max = 2000)
    private String description;

    @Schema(description = "Updated discovery tags")
    @Size(max = 32)
    private List<@NotBlank @Size(max = 64) String> tags;

    @Schema(description = "Updated JSON specification payload")
    @NotBlank
    @Size(max = 1048576)
    private String specJson;
}
