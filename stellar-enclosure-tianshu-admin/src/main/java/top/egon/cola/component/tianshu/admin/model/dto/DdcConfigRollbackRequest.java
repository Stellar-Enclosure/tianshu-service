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
public class DdcConfigRollbackRequest {

    @PermissionResource(key = "tianshu.field.DdcConfigRollbackRequest.configId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.configId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotBlank
    @Size(max = 64)
    private String configId;

    @PermissionResource(key = "tianshu.field.DdcConfigRollbackRequest.version", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.version")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotNull
    @Positive
    private Long version;

    @PermissionResource(key = "tianshu.field.DdcConfigRollbackRequest.reason", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.reason")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Size(max = 512)
    private String reason;
}
