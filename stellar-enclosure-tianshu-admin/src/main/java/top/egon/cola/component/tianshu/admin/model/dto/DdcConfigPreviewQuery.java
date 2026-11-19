package top.egon.cola.component.tianshu.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcConfigPreviewQuery {

    @PermissionResource(key = "tianshu.field.DdcConfigPreviewQuery.configId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.configId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Size(max = 64)
    private String configId;


    @PermissionResource(key = "tianshu.field.DdcConfigPreviewQuery.expectedVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.currentVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Positive
    private Long expectedVersion;


    @PermissionResource(key = "tianshu.field.DdcConfigPreviewQuery.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotBlank @Size(max = 64)
    private String bizCode;


    @PermissionResource(key = "tianshu.field.DdcConfigPreviewQuery.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotBlank @Size(max = 64)
    private String appCode;


    @PermissionResource(key = "tianshu.field.DdcConfigPreviewQuery.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotBlank @Size(max = 64)
    private String env;


    @PermissionResource(key = "tianshu.field.DdcConfigPreviewQuery.content", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.content")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotBlank @Size(max = 1_048_576)
    private String content;
}
