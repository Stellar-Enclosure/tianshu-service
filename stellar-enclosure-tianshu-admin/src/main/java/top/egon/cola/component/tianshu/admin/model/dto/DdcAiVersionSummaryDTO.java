package top.egon.cola.component.tianshu.admin.model.dto;

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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class DdcAiVersionSummaryDTO {

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long id;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.resourceId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long resourceId;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.versionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.versionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private Long versionNo;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.kind", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.kind")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotBlank
    @Pattern(regexp = "^(PROMPT|SKILL)$")
    private String kind;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.displayName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.displayName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotBlank
    @Size(min = 1, max = 128)
    @Schema(minLength = 1, maxLength = 128)
    private String displayName;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.description", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.description")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Size(max = 2048)
    private String description;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.contentSha256", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.contentSha256")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Pattern(regexp = "^[0-9a-f]{64}$")
    private String contentSha256;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.contentBytes", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.contentBytes")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Min(0)
    private Long contentBytes;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.nodeCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.nodeCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Min(0)
    @Max(256)
    private Integer nodeCount;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.sourceVersionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.sourceVersionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Positive
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private Long sourceVersionId;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.changeNote", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeNote")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Size(max = 512)
    private String changeNote;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @PermissionResource(key = "tianshu.field.DdcAiVersionSummaryDTO.createdBy", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdBy")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[^\\p{Cntrl}]+$")
    @Schema(type = "string", pattern = "^[^\\p{Cntrl}]+$")
    private String createdBy;
}
