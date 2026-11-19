package top.egon.cola.component.tianshu.admin.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

/** Current evidence for one configured workload target. */
public record DdcInstanceWorkloadVO(
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.targetBindingId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.targetBindingId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String targetBindingId,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.clusterId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.clusterId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String clusterId,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.k8sNamespace", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.k8sNamespace")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String k8sNamespace,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.workloadUid", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.workloadUid")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String workloadUid,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.status", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.status")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        StatusEnum status,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.reasonCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.reasonCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @Nullable String reasonCode,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.observedAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.observedAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @JsonFormat(shape = JsonFormat.Shape.STRING) @Nullable Instant observedAt,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.validUntil", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.validUntil")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @JsonFormat(shape = JsonFormat.Shape.STRING) @Nullable Instant validUntil,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.desiredReplicas", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.desiredReplicas")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @Nullable Integer desiredReplicas,
        @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.pods", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.currentPods")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        List<PodVO> pods) {

    public DdcInstanceWorkloadVO {
        pods = List.copyOf(pods);
    }

    /** Pod readiness is Kubernetes state. Configuration application is reported separately. */
    public record PodVO(
            @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.PodVO.podUid", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.podUid")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
            String podUid,
            @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.PodVO.podName", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.podName")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
            String podName,
            @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.PodVO.containerName", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.containerName")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
            String containerName,
            @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.PodVO.ready", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.ready")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
            boolean ready,
            @PermissionResource(key = "tianshu.field.DdcInstanceWorkloadVO.PodVO.restartCount", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.restartCount")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER, plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
            int restartCount) {
    }
}
