package top.egon.cola.component.tianshu.model.management;

import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

/**
 * Tianshu 应用目录项。 / Tianshu application catalog entry.
 *
 * @param id              Tianshu 应用标识 / Tianshu application identifier
 * @param businessId      所属业务域标识 / owning business identifier
 * @param bizCode         所属业务编码 / owning business code
 * @param appCode         应用编码 / application code
 * @param appName         应用名称 / application name
 * @param enabled         应用是否启用 / whether the application is enabled
 * @param businessEnabled 父业务域是否启用 / whether the parent business is enabled
 */
public record DdcManagementApp(
        @PermissionResource(key = "tianshu.field.DdcManagementApp.id", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.application.id",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String id,
        @PermissionResource(key = "tianshu.field.DdcManagementApp.businessId", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.application.businessId",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String businessId,
        @PermissionResource(key = "tianshu.field.DdcManagementApp.bizCode", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.application.bizCode",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String bizCode,
        @PermissionResource(key = "tianshu.field.DdcManagementApp.appCode", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.application.appCode",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String appCode,
        @PermissionResource(key = "tianshu.field.DdcManagementApp.appName", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.application.appName",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String appName,
        @PermissionResource(key = "tianshu.field.DdcManagementApp.enabled", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.application.enabled",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        boolean enabled,
        @PermissionResource(key = "tianshu.field.DdcManagementApp.businessEnabled", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.application.businessEnabled",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        boolean businessEnabled
) {
}
