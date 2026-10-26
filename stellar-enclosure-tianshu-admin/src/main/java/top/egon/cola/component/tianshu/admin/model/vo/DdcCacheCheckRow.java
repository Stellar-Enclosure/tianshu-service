package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DdcCacheCheckRow {

    @PermissionResource(key = "tianshu.field.DdcCacheCheckRow.resourceName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceName;

    @PermissionResource(key = "tianshu.field.DdcCacheCheckRow.databaseValue", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.databaseValue")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String databaseValue;

    @PermissionResource(key = "tianshu.field.DdcCacheCheckRow.redisValue", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.redisValue")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String redisValue;

    @PermissionResource(key = "tianshu.field.DdcCacheCheckRow.databaseVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.databaseVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long databaseVersion;

    @PermissionResource(key = "tianshu.field.DdcCacheCheckRow.redisVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.redisVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long redisVersion;

    @PermissionResource(key = "tianshu.field.DdcCacheCheckRow.matched", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.matched")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private boolean matched;
}
