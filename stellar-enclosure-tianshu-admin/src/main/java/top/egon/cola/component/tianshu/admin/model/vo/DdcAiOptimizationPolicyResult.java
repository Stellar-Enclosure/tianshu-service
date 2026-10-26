package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** API-012/013 优化策略与评估快照。 / API-012/013 optimization policy with its evaluation snapshot. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DdcAiOptimizationPolicyResult {

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long resourceId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.persisted", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.persisted")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    private Boolean persisted;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.revision", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.revision")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0|[1-9][0-9]{0,18})$")
    private Long revision;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.enabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.enabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Boolean enabled;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.scoreThreshold", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.scoreThreshold")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer scoreThreshold;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.minSamples", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.minSamples")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer minSamples;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.evaluationWindow", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.evaluationWindow")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "duration", example = "PT24H")
    private Duration evaluationWindow;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.maxIterations", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.maxIterations")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer maxIterations;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.cooldown", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.cooldown")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "duration", example = "PT1H")
    private Duration cooldown;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.autoPublish", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.autoPublish")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Boolean autoPublish;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.judgeEnabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeEnabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Boolean judgeEnabled;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.optimizerModelAlias", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.optimizerModelAlias")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String optimizerModelAlias;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.judgeModelAlias", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeModelAlias")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String judgeModelAlias;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.optimizerInstruction", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.optimizerInstruction")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String optimizerInstruction;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.state", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.state")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Schema(allowableValues = {"ARMED", "AWAITING_PUBLISH", "CONVERGED", "EXHAUSTED", "PAUSED"})
    private String state;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.iterationsDone", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.iterationsDone")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer iterationsDone;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.consecutiveFailures", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.consecutiveFailures")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer consecutiveFailures;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.activeRunId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.activeRunId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long activeRunId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.convergedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.convergedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time", nullable = true)
    private Instant convergedAt;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.evaluation", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.evaluation")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private EvaluationResult evaluation;

    /** 最近一次评估快照与实时待评分数。 / Last evaluation snapshot plus the live pending-judge count. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class EvaluationResult {

        @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.EvaluationResult.evaluatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.evaluatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", format = "date-time", nullable = true)
        private Instant evaluatedAt;

        @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.EvaluationResult.versionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.versionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
        private Long versionId;

        @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.EvaluationResult.versionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.versionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
        private Long versionNo;

        @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.EvaluationResult.sampleCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.sampleCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer sampleCount;

        @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.EvaluationResult.averageScore", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.averageScore")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", nullable = true, example = "71.50")
        private BigDecimal averageScore;

        @PermissionResource(key = "tianshu.field.DdcAiOptimizationPolicyResult.EvaluationResult.pendingJudgeCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.pendingJudgeCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer pendingJudgeCount;
    }
}
