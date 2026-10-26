package top.egon.cola.component.tianshu.model.management;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * 某个服务键在特定代次的实例快照。 / Instance snapshot of a service key at a specific generation.
 *
 * @param serviceKey 快照对应的服务键 / service key represented by the snapshot
 * @param generation 服务注册表代次 / service-registry generation
 * @param observedAt 快照观测时间 / snapshot observation time
 * @param instances  快照中的服务实例 / service instances in the snapshot
 */
public record DdcManagementServiceSnapshot(
        @PermissionResource(key = "tianshu.field.DdcManagementServiceSnapshot.serviceKey", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.serviceKey")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        DdcManagementServiceKey serviceKey,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceSnapshot.generation", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.generation")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        long generation,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceSnapshot.observedAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.observedAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant observedAt,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceSnapshot.instances", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.instances")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @Nullable List<DdcManagementServiceInstance> instances
) {

    /**
     * 构造服务快照并将实例列表归一化为不可变列表。 /
     * Constructs a service snapshot and normalizes the instance list to an immutable list.
     */
    public DdcManagementServiceSnapshot {
        instances = instances == null ? List.of() : List.copyOf(instances);
    }
}
