package top.egon.cola.component.tianshu.model.management;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

/**
 * Tianshu 服务注册表中的规范化服务键。 / Canonical service key in the Tianshu service registry.
 *
 * @param bizCode     业务编码 / business code
 * @param env         环境编码 / environment code
 * @param appCode     应用编码 / application code
 * @param serviceId   服务的规范物理标识 / canonical physical service identifier
 * @param serviceKind 服务类型 / service kind
 * @param serviceName 服务名称 / service name
 * @param group       服务分组 / service group
 * @param version     服务版本 / service version
 * @param protocol    服务协议 / service protocol
 */
public record DdcManagementServiceKey(
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.bizCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String bizCode,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.env", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.env")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String env,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.appCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String appCode,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.serviceId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.serviceId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String serviceId,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.serviceKind", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.serviceKind")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String serviceKind,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.serviceName", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.serviceName")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String serviceName,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.group", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.group")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String group,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.version", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.version")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String version,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceKey.protocol", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.protocol")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String protocol
) {
}
