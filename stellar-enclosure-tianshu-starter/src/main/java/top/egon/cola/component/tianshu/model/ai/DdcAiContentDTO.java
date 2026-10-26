package top.egon.cola.component.tianshu.model.ai;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.ai.validation.PublishableAiContent;
import top.egon.cola.component.tianshu.model.ai.validation.ValidAiContent;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@ValidAiContent(groups = DdcAiValidationGroups.Save.class)
@PublishableAiContent(groups = DdcAiValidationGroups.Publish.class)
public class DdcAiContentDTO {

    @PermissionResource(key = "tianshu.field.DdcAiContentDTO.promptText", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.promptText")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    private String promptText;

    @PermissionResource(key = "tianshu.field.DdcAiContentDTO.nodes", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.nodes")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotNull
    @Valid
    @Size(max = 257)
    private List<DdcAiNodeDTO> nodes;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static DdcAiContentDTO of(
            @JsonProperty(value = "promptText", required = true) String promptText,
            @JsonProperty(value = "nodes", required = true) List<DdcAiNodeDTO> nodes) {
        return DdcAiContentDTO.builder().promptText(promptText).nodes(nodes).build();
    }
}
