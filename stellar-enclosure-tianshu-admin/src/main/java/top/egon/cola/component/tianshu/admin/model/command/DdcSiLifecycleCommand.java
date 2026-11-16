package top.egon.cola.component.tianshu.admin.model.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/** Lifecycle command carrying optimistic revision, promotion target env, or rollback target version. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Lifecycle transition command for publish, promote, rollback, and deprecate")
public class DdcSiLifecycleCommand {

    public interface PublishGroup { }

    public interface PromoteGroup { }

    public interface RollbackGroup { }

    public interface LifecycleActionGroup { }

    @Schema(description = "Expected optimistic lock revision of the resource")
    @NotNull(groups = {PublishGroup.class, RollbackGroup.class, LifecycleActionGroup.class})
    @Min(value = 0, groups = {PublishGroup.class, RollbackGroup.class, LifecycleActionGroup.class})
    private Long expectedVersion;

    @Schema(description = "Target version number when performing a rollback")
    @NotNull(groups = RollbackGroup.class)
    @Positive(groups = RollbackGroup.class)
    private Long targetVersionNo;

    @Schema(description = "Target environment code when promoting across environments", example = "prod")
    @NotBlank(groups = PromoteGroup.class)
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,31}$", groups = PromoteGroup.class)
    private String targetEnvCode;

    @Schema(description = "Lifecycle action code", example = "DEPRECATE")
    @NotBlank(groups = LifecycleActionGroup.class)
    @Pattern(regexp = "^(DEPRECATE)$", groups = LifecycleActionGroup.class)
    private String action;

    @Schema(description = "Human-readable change note for audit trail")
    @Size(max = 512)
    private String changeNote;
}
