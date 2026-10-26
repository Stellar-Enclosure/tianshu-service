package top.egon.cola.component.tianshu.admin.model.dto;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

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
public class DdcConfigQueryRequest {

    @PermissionResource(key = "tianshu.field.DdcConfigQueryRequest.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    @Size(max = 128)
    private String bizCode;

    @PermissionResource(key = "tianshu.field.DdcConfigQueryRequest.namespaceCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.namespaceCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    @Size(max = 128)
    private String namespaceCode;

    @PermissionResource(key = "tianshu.field.DdcConfigQueryRequest.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    @Size(max = 32)
    private String env;

    @PermissionResource(key = "tianshu.field.DdcConfigQueryRequest.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    @Size(max = 128)
    private String appCode;

    @PermissionResource(key = "tianshu.field.DdcConfigQueryRequest.includeDeleted", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.includeDeleted")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            write = @PermissionGroup(codes = {"TIANSHU_READ"}))
    private boolean includeDeleted;
}
