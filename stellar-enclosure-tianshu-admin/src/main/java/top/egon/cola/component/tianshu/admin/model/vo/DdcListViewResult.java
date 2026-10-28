package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import java.util.List;

public record DdcListViewResult(
        @PermissionResource(key = "tianshu.field.DdcListViewResult.definitionDigest", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.definitionDigest")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String definitionDigest,
        @PermissionResource(key = "tianshu.field.DdcListViewResult.catalogVersion", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String catalogVersion,
        @PermissionResource(key = "tianshu.field.DdcListViewResult.columns", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.columnKeys")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) List<DdcListColumnResult> columns,
        @PermissionResource(key = "tianshu.field.DdcListViewResult.templates", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.templates")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) List<DdcListTemplateResult> templates,
        @PermissionResource(key = "tianshu.field.DdcListViewResult.selectedTemplateId", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.selectedTemplateId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) String selectedTemplateId,
        @PermissionResource(key = "tianshu.field.DdcListViewResult.visibleColumns", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.columnKeys")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) List<String> visibleColumns,
        @PermissionResource(key = "tianshu.field.DdcListViewResult.mayManageSystem", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.mayManageSystem")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) boolean mayManageSystem,
        @PermissionResource(key = "tianshu.field.DdcListViewResult.mayManagePersonal", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.mayManagePersonal")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN, plainRead = @PermissionGroup(codes = "TIANSHU_READ")) boolean mayManagePersonal) { }
