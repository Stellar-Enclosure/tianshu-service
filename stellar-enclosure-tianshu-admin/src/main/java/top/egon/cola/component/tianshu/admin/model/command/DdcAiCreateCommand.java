package top.egon.cola.component.tianshu.admin.model.command;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.ai.DdcAiContentDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiKindEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiValidationGroups;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcAiCreateCommand {

    @PermissionResource(key = "tianshu.field.DdcAiCreateCommand.scope", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.scope")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotNull
    @Valid
    private DdcAiScopeDTO scope;

    @PermissionResource(key = "tianshu.field.DdcAiCreateCommand.kind", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.kind")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotNull
    private DdcAiKindEnum kind;

    @PermissionResource(key = "tianshu.field.DdcAiCreateCommand.resourceKey", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceKey")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private String resourceKey;

    @PermissionResource(key = "tianshu.field.DdcAiCreateCommand.displayName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.displayName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotBlank
    @Size(max = 128)
    private String displayName;

    @PermissionResource(key = "tianshu.field.DdcAiCreateCommand.description", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.description")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotNull
    @Size(max = 2048)
    private String description;

    @PermissionResource(key = "tianshu.field.DdcAiCreateCommand.draft", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.draft")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotNull
    @Valid
    @ConvertGroup(from = Default.class, to = DdcAiValidationGroups.Save.class)
    private DdcAiContentDTO draft;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static DdcAiCreateCommand of(
            @JsonProperty(value = "scope", required = true) DdcAiScopeDTO scope,
            @JsonProperty(value = "kind", required = true) DdcAiKindEnum kind,
            @JsonProperty(value = "resourceKey", required = true) String resourceKey,
            @JsonProperty(value = "displayName", required = true) String displayName,
            @JsonProperty(value = "description", required = true) String description,
            @JsonProperty(value = "draft", required = true) DdcAiContentDTO draft) {
        return DdcAiCreateCommand.builder().scope(scope).kind(kind).resourceKey(resourceKey)
                .displayName(displayName).description(description).draft(draft).build();
    }
}
