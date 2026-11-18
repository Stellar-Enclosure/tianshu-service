package top.egon.cola.component.tianshu.admin.model.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.util.Objects;

/** Verified OAuth deployment and current Kubernetes container; bootId remains an application claim. */
public record DdcConfigWorkloadIdentityVO(
        @NotNull @Valid DdcTenantContextDTO tenant,
        @NotNull @Valid DdcConfigScopeVO scope,
        @NotBlank String targetBindingId,
        @Positive long targetBindingRevision,
        @NotBlank String clusterId,
        @NotBlank String k8sNamespace,
        @NotBlank String workloadUid,
        @NotBlank String podUid,
        @NotBlank String containerName,
        @NotBlank String containerId,
        @NotBlank @Size(max = 64) String bootId) {

    public DdcConfigWorkloadIdentityVO {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(scope, "scope");
        if (!Long.toString(tenant.tenantId()).equals(scope.getTenantId()) || targetBindingRevision <= 0) {
            throw new IllegalArgumentException("CONFIG_WORKLOAD_IDENTITY_INVALID");
        }
        for (String value : new String[]{targetBindingId, clusterId, k8sNamespace, workloadUid,
                podUid, containerName, containerId}) {
            if (value == null || value.isBlank() || value.length() > 512
                    || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
                throw new IllegalArgumentException("CONFIG_WORKLOAD_IDENTITY_INVALID");
            }
        }
        if (bootId == null || !bootId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("CONFIG_BOOT_ID_INVALID");
        }
    }
}
