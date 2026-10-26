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
import java.time.Instant;

/** API-014/015/016 优化运行视图。 / API-014/015/016 optimization-run view. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DdcAiOptimizationRunResult {

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long id;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long resourceId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.policyId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.policyId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long policyId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.runNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.runNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long runNo;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.iterationNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.iterationNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer iterationNo;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.trigger", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.trigger")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Schema(allowableValues = {"SCHEDULED", "MANUAL"})
    private String trigger;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.state", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.state")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Schema(allowableValues = {"PENDING", "RUNNING", "SUCCEEDED", "FAILED", "SKIPPED"})
    private String state;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.baseVersionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.baseVersionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long baseVersionId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.baseVersionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.baseVersionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long baseVersionNo;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.sampleCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.sampleCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer sampleCount;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.averageScore", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.averageScore")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, example = "71.50")
    private BigDecimal averageScore;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.thresholdSnapshot", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.thresholdSnapshot")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer thresholdSnapshot;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.windowStart", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.windowStart")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant windowStart;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.windowEnd", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.windowEnd")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant windowEnd;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.modelAlias", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.modelAlias")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String modelAlias;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.promptTokens", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.promptTokens")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer promptTokens;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.completionTokens", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.completionTokens")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer completionTokens;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.producedVersionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.producedVersionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long producedVersionId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.producedVersionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.producedVersionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long producedVersionNo;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.publicationId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.publicationId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long publicationId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.autoPublished", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.autoPublished")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Boolean autoPublished;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.errorCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.errorCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String errorCode;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.errorMessage", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.errorMessage")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String errorMessage;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.requestId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.requestId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String requestId;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.note", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.note")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String note;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.startedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.startedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time", nullable = true)
    private Instant startedAt;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.finishedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.finishedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time", nullable = true)
    private Instant finishedAt;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant createdAt;

    @PermissionResource(key = "tianshu.field.DdcAiOptimizationRunResult.createdBy", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdBy")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String createdBy;
}
