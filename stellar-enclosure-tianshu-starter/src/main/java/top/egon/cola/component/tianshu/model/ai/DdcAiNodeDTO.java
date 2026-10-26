package top.egon.cola.component.tianshu.model.ai;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.ai.validation.ValidAiNode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@ValidAiNode
public class DdcAiNodeDTO {

    @PermissionResource(key = "tianshu.field.DdcAiNodeDTO.path", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.path")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotNull
    @Size(max = 1024)
    private String path;

    @PermissionResource(key = "tianshu.field.DdcAiNodeDTO.nodeType", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.nodeType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    @NotNull
    private DdcAiNodeTypeEnum nodeType;

    @PermissionResource(key = "tianshu.field.DdcAiNodeDTO.content", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.content")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
            write = @PermissionGroup(codes = {"TIANSHU_AI_WRITE"}))
    private String content;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static DdcAiNodeDTO of(
            @JsonProperty(value = "path", required = true) String path,
            @JsonProperty(value = "nodeType", required = true) DdcAiNodeTypeEnum nodeType,
            @JsonProperty(value = "content", required = true) String content) {
        return DdcAiNodeDTO.builder().path(path).nodeType(nodeType).content(content).build();
    }
}
