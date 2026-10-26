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
 * 某次观测得到的服务键目录。 / Catalog of service keys captured by one observation.
 *
 * @param generation 服务注册表代次 / service-registry generation
 * @param observedAt 目录观测时间 / catalog observation time
 * @param services   目录中的服务键 / service keys in the catalog
 */
public record DdcManagementServiceCatalog(
        @PermissionResource(key = "tianshu.field.DdcManagementServiceCatalog.generation", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.generation")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        long generation,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceCatalog.observedAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.observedAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant observedAt,
        @PermissionResource(key = "tianshu.field.DdcManagementServiceCatalog.services", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.services")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @Nullable List<DdcManagementServiceKey> services
) {

    /**
     * 构造服务目录并将服务键列表归一化为不可变列表。 /
     * Constructs a service catalog and normalizes the service-key list to an immutable list.
     */
    public DdcManagementServiceCatalog {
        services = services == null ? List.of() : List.copyOf(services);
    }
}
