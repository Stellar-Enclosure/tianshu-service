package top.egon.cola.component.tianshu.admin.model.dto;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class DdcConfigUpdateRequest {

    @PermissionResource(key = "tianshu.field.DdcConfigUpdateRequest.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotBlank
    @Size(max = 64)
    private String id;

    @PermissionResource(key = "tianshu.field.DdcConfigUpdateRequest.content", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.content")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotNull
    private String content;

    @PermissionResource(key = "tianshu.field.DdcConfigUpdateRequest.changeReason", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeReason")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Size(max = 512)
    private String changeReason;

    @PermissionResource(key = "tianshu.field.DdcConfigUpdateRequest.currentVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.currentVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Positive
    private Long currentVersion;
}
