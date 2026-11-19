package top.egon.cola.component.tianshu.model.management;

import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

/**
 * Tianshu 业务域目录项。 / Tianshu business-domain catalog entry.
 *
 * @param id        Tianshu 业务域标识 / Tianshu business identifier
 * @param bizCode   业务编码 / business code
 * @param bizName   业务名称 / business name
 * @param enabled   业务域是否启用 / whether the business is enabled
 */
public record DdcManagementBiz(
        @PermissionResource(key = "tianshu.field.DdcManagementBiz.id", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.business.id",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String id,
        @PermissionResource(key = "tianshu.field.DdcManagementBiz.bizCode", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.business.bizCode",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String bizCode,
        @PermissionResource(key = "tianshu.field.DdcManagementBiz.bizName", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.business.bizName",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        String bizName,
        @PermissionResource(key = "tianshu.field.DdcManagementBiz.enabled", type = ResourceType.FIELD,
                access = ResourceAccess.PROTECTED, labelPage = "tianshu.permissions", labelKey = "field.logicalCatalog.business.enabled",
                allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE)
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = DdcLogicalCatalogContract.SCOPE))
        boolean enabled
) {
}
