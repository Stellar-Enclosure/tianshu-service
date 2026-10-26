package top.egon.cola.component.tianshu.admin.model.command;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.admin.service.ai.validation.ValidDurationRange;

import java.time.Duration;

/** API-013 完整替换优化策略。 / API-013 full replacement of an optimization policy. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder(toBuilder = true)
public class DdcAiOptimizationPolicyCommand {

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.expectedRevision", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.expectedRevision")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotBlank
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    @Schema(description = "当前策略修订；首次保存为 0", example = "0")
    private String expectedRevision;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.enabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.enabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    private Boolean enabled;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.scoreThreshold", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.scoreThreshold")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @Min(1)
    @Max(100)
    private Integer scoreThreshold;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.minSamples", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.minSamples")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @Min(1)
    @Max(100000)
    private Integer minSamples;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.evaluationWindow", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.evaluationWindow")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @ValidDurationRange(minSeconds = 300, maxSeconds = 2592000)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "duration", example = "PT24H")
    private Duration evaluationWindow;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.maxIterations", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.maxIterations")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @Min(1)
    @Max(50)
    private Integer maxIterations;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.cooldown", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.cooldown")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @ValidDurationRange(minSeconds = 60, maxSeconds = 604800)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "duration", example = "PT1H")
    private Duration cooldown;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.autoPublish", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.autoPublish")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    private Boolean autoPublish;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.judgeEnabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeEnabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    private Boolean judgeEnabled;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.optimizerModelAlias", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.optimizerModelAlias")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,63}$")
    private String optimizerModelAlias;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.judgeModelAlias", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeModelAlias")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,63}$")
    private String judgeModelAlias;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.optimizerInstruction", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.optimizerInstruction")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @Size(max = 4096)
    @Pattern(regexp = "^(?:[^\\p{Cntrl}]|\\n)*$")
    private String optimizerInstruction;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyCommand.rearm", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.rearm")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    private Boolean rearm;
}
