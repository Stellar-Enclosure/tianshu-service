package top.egon.cola.component.tianshu.model.management;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * 管理接口返回的服务注册实例。 / Service-registry instance returned by the management API.
 *
 * @param instanceId      实例标识 / instance identifier
 * @param leaseId         租约标识 / lease identifier
 * @param host            实例主机 / instance host
 * @param port            实例端口 / instance port
 * @param secure          是否使用安全传输 / whether secure transport is used
 * @param metadata        实例元数据 / instance metadata
 * @param status          服务端租约状态文本 / server-side lease-status text
 * @param registeredAt    注册时间 / registration time
 * @param lastHeartbeatAt 最近心跳时间 / most recent heartbeat time
 * @param expireAt        租约过期时间 / lease expiration time
 */
public record DdcManagementServiceInstance(
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.instanceId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.instanceId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String instanceId,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.leaseId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.leaseId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String leaseId,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.host", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.host")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String host,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.port", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.port")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        int port,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.secure", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.secure")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        boolean secure,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.metadata", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.metadata")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @Nullable Map<String, String> metadata,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.status", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.status")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String status,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.registeredAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.registeredAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant registeredAt,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.lastHeartbeatAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.lastHeartbeatAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant lastHeartbeatAt,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceInstance.expireAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.expireAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant expireAt
) {

    /**
     * 构造服务实例并将元数据归一化为不可变映射。 /
     * Constructs a service instance and normalizes metadata to an immutable map.
     */
    public DdcManagementServiceInstance {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 将服务端租约状态归一化为稳定的客户端枚举。 / Normalizes the server lease status to a stable client enum.
     *
     * @return 归一化租约状态 / normalized lease status
     */
    public DdcInstanceStatus normalizedStatus() {
        return DdcInstanceStatus.fromWire(status);
    }
}
