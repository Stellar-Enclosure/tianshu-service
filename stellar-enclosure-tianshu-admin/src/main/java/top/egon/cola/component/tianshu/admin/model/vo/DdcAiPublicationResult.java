package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcAiPublicationResult {

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("id")
    private Long publicationId;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long resourceId;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.operation", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.operation")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotBlank
    @Pattern(regexp = "^(PUBLISH|ROLLBACK|PROMOTE)$")
    private String operation;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.previousVersionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.previousVersionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long previousVersionId;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.targetVersionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetVersionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long targetVersionId;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.targetVersionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetVersionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long targetVersionNo;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.sourceVersionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.sourceVersionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long sourceVersionId;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.resourceRevision", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceRevision")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Min(0)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0|[1-9][0-9]{0,18})$")
    private Long resourceRevision;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.targetCreated", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetCreated")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    private Boolean targetCreated;

    @PermissionResource(key = "tianshu.field.DdcAiPublicationResult.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", format = "date-time")
    private Instant createdAt;

    @JsonProperty("id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(name = "id", description = "Publication record ID", type = "string",
            pattern = "^[1-9][0-9]{0,18}$", requiredMode = Schema.RequiredMode.REQUIRED)
    public Long getPublicationId() {
        return publicationId;
    }
}
