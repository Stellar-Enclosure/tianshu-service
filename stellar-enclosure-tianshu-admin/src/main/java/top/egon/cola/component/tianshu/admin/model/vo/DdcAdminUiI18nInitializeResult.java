package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

public record DdcAdminUiI18nInitializeResult(
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nInitializeResult.created", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.created")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        int created,
        @PermissionResource(key = "tianshu.field.DdcAdminUiI18nInitializeResult.skipped", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.skipped")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        int skipped) { }
