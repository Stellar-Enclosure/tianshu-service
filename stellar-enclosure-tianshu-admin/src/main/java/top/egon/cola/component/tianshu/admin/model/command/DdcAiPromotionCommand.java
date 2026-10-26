package top.egon.cola.component.tianshu.admin.model.command;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcAiPromotionCommand {

    @PermissionResource(key = "tianshu.field.DdcAiPromotionCommand.sourceVersionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.sourceVersionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotBlank
    @Size(max = 19)
    @Pattern(regexp = "^[1-9][0-9]*$")
    @DecimalMax("9223372036854775807")
    private String sourceVersionNo;

    @PermissionResource(key = "tianshu.field.DdcAiPromotionCommand.targetScope", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetScope")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @Valid
    private DdcAiScopeDTO targetScope;

    @PermissionResource(key = "tianshu.field.DdcAiPromotionCommand.targetResourceKey", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetResourceKey")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private String targetResourceKey;

    @PermissionResource(key = "tianshu.field.DdcAiPromotionCommand.expectedTargetRevision", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.expectedTargetRevision")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @Size(max = 19)
    @Pattern(regexp = "^(0|[1-9][0-9]*)$")
    @DecimalMax("9223372036854775807")
    @Schema(type = "string", nullable = true, pattern = "^(0|[1-9][0-9]{0,18})$")
    private String expectedTargetRevision;

    @PermissionResource(key = "tianshu.field.DdcAiPromotionCommand.changeNote", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeNote")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @Size(max = 512)
    private String changeNote;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static DdcAiPromotionCommand of(
            @JsonProperty(value = "sourceVersionNo", required = true) String sourceVersionNo,
            @JsonProperty(value = "targetScope", required = true) DdcAiScopeDTO targetScope,
            @JsonProperty(value = "targetResourceKey", required = true) String targetResourceKey,
            @JsonProperty(value = "expectedTargetRevision", required = true) String expectedTargetRevision,
            @JsonProperty(value = "changeNote", required = true) String changeNote) {
        return DdcAiPromotionCommand.builder().sourceVersionNo(sourceVersionNo).targetScope(targetScope)
                .targetResourceKey(targetResourceKey).expectedTargetRevision(expectedTargetRevision)
                .changeNote(changeNote).build();
    }
}
