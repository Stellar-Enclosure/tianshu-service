package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

public record DdcListColumnResult(
        @PermissionResource(key = "tianshu.field.DdcListColumnResult.columnKey", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.columnKey")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String columnKey,
        @PermissionResource(key = "tianshu.field.DdcListColumnResult.labelPage", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String labelPage,
        @PermissionResource(key = "tianshu.field.DdcListColumnResult.labelKey", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.labelKey")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String labelKey) { }
