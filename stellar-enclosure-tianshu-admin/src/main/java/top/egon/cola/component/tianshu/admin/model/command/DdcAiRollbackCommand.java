package top.egon.cola.component.tianshu.admin.model.command;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcAiRollbackCommand {

    @PermissionResource(key = "tianshu.field.DdcAiRollbackCommand.expectedRevision", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.expectedRevision")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotBlank
    @Size(max = 19)
    @Pattern(regexp = "^(0|[1-9][0-9]*)$")
    @DecimalMax("9223372036854775807")
    private String expectedRevision;

    @PermissionResource(key = "tianshu.field.DdcAiRollbackCommand.versionNo", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.versionNo")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotBlank
    @Size(max = 19)
    @Pattern(regexp = "^[1-9][0-9]*$")
    @DecimalMax("9223372036854775807")
    private String versionNo;

    @PermissionResource(key = "tianshu.field.DdcAiRollbackCommand.changeNote", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeNote")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_PUBLISH"}))
    @NotNull
    @Size(max = 512)
    private String changeNote;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static DdcAiRollbackCommand of(
            @JsonProperty(value = "expectedRevision", required = true) String expectedRevision,
            @JsonProperty(value = "versionNo", required = true) String versionNo,
            @JsonProperty(value = "changeNote", required = true) String changeNote) {
        return DdcAiRollbackCommand.builder().expectedRevision(expectedRevision).versionNo(versionNo)
                .changeNote(changeNote).build();
    }
}
