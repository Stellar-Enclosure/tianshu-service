package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import java.util.List;

public record DdcListTemplateResult(
        @PermissionResource(key = "tianshu.field.DdcListTemplateResult.id", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.id")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String id,
        @PermissionResource(key = "tianshu.field.DdcListTemplateResult.type", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.templateType")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String type,
        @PermissionResource(key = "tianshu.field.DdcListTemplateResult.name", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.name")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String name,
        @PermissionResource(key = "tianshu.field.DdcListTemplateResult.defaultTemplate", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.defaultTemplate")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) boolean defaultTemplate,
        @PermissionResource(key = "tianshu.field.DdcListTemplateResult.version", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.version")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String version,
        @PermissionResource(key = "tianshu.field.DdcListTemplateResult.columnKeys", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.columnKeys")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) List<String> columnKeys) { }
