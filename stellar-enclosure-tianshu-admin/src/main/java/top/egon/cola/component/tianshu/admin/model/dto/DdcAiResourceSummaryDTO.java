package top.egon.cola.component.tianshu.admin.model.dto;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.ai.DdcAiKindEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcAiResourceSummaryDTO {

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    @Schema(type = "string", pattern = "^[1-9][0-9]{0,18}$")
    private String id;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.scope", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.scope")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Valid
    private DdcAiScopeDTO scope;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.kind", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.kind")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    private DdcAiKindEnum kind;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.resourceKey", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceKey")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private String resourceKey;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.displayName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.displayName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotBlank
    @Size(min = 1, max = 128)
    @Schema(minLength = 1, maxLength = 128)
    private String displayName;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.description", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.description")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Size(max = 2048)
    private String description;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.revision", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.revision")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    @Schema(type = "string", pattern = "^(0|[1-9][0-9]{0,18})$")
    private String revision;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.lastVersionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.lastVersionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    @Schema(type = "string", pattern = "^(0|[1-9][0-9]{0,18})$")
    private String lastVersionNo;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.currentVersionId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.currentVersionId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private String currentVersionId;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.currentVersionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.currentVersionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    @Schema(type = "string", nullable = true, pattern = "^[1-9][0-9]{0,18}$")
    private String currentVersionNo;

    @PermissionResource(key = "tianshu.field.DdcAiResourceSummaryDTO.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @NotNull
    private Instant updatedAt;
}
