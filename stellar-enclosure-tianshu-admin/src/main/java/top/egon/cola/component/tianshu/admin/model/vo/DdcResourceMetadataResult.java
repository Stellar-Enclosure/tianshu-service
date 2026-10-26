package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import java.util.List;
import java.util.Map;

public record DdcResourceMetadataResult(
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.catalogScope", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogScope")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        CatalogScope catalogScope,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.catalogActivationVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String catalogActivationVersion,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.producerId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.producerId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String producerId,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.producerVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.producerVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String producerVersion,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.sourceVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.sourceVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String sourceVersion,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.metadataKind", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.metadataKind")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String metadataKind,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.definitionKey", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.definitionKey")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String definitionKey,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.resourceId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.resourceId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String resourceId,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.permissionId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.permissionId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String permissionId,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.resourceType", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.resourceType")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String resourceType,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.lifecycle", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.lifecycle")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String lifecycle,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.pageCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String pageCode,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.labelKey", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.labelKey")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String labelKey,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.descriptionKey", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.descriptionKey")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String descriptionKey,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.target", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.target")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, Object> target,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.policy", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.policy")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, Object> policy,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.i18nRefs", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.i18nRefs")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, Object> i18nRefs,
        @PermissionResource(key = "tianshu.field.DdcResourceMetadataResult.permissionCodes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.permissionCodes")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        List<String> permissionCodes) { }
