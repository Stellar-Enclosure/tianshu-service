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

import java.time.Instant;

/** API-017 使用样本视图。 / API-017 usage-sample view. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DdcAiUsageFeedbackResult {

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long id;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long resourceId;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.versionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.versionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long versionId;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.versionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.versionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long versionNo;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.usageId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.usageId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String usageId;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.reporterCredentialId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.reporterCredentialId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String reporterCredentialId;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String bizCode;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String env;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String appCode;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.outcome", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.outcome")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Schema(allowableValues = {"SUCCESS", "FAILURE", "UNKNOWN"})
    private String outcome;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.explicitScore", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.explicitScore")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer explicitScore;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.judgeScore", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeScore")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer judgeScore;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.effectiveScore", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.effectiveScore")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer effectiveScore;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.scoreSource", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.scoreSource")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Schema(allowableValues = {"EXPLICIT", "JUDGE", "NONE"})
    private String scoreSource;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.judgeState", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeState")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Schema(allowableValues = {"NOT_REQUIRED", "PENDING", "JUDGING", "DONE", "FAILED"})
    private String judgeState;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.judgeAttempts", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeAttempts")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer judgeAttempts;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.judgeError", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgeError")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String judgeError;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.inputExcerpt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.inputExcerpt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String inputExcerpt;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.outputExcerpt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.outputExcerpt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String outputExcerpt;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.latencyMs", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.latencyMs")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private Integer latencyMs;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.occurredAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.occurredAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant occurredAt;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.traceId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.traceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    private String traceId;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.judgedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.judgedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time", nullable = true)
    private Instant judgedAt;

    @PermissionResource(key = "tianshu.field.DdcAiUsageFeedbackResult.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant createdAt;
}
