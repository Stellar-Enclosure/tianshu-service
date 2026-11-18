package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigProcessReceiptBO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;

/** Tenant-bound receipt ordering and explicit current-incarnation versus historical reads. */
public interface DdcConfigProcessReceiptRepository {
    AcceptanceEnum accept(@NotNull @Valid DdcTenantContextDTO trustedContext, @NotNull @Valid DdcConfigProcessReceiptBO authorizedReceipt);
    List<DdcConfigProcessReceiptBO> readCurrentByTarget(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull @Valid TargetQueryVO target, @NotNull @Size(max = 500) List<@Valid IncarnationVO> currentIncarnations,
            @Min(1) @Max(500) int limit);
    List<DdcConfigProcessReceiptBO> readHistory(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull Instant afterReceivedAt, @NotNull @PositiveOrZero Long afterId, @Min(1) @Max(500) int limit);
    enum AcceptanceEnum { SAME, ADVANCED, CONFLICT, STALE }
    record TargetQueryVO(@NotNull @Positive Long configId, @NotNull @Positive Long configRevision,
            @NotBlank @Size(max=128) String targetBindingId, @NotNull @Positive Long targetRevision,
            @NotBlank @Size(max=64) String clusterId, @NotBlank @Size(max=63) String k8sNamespace,
            @NotBlank @Size(max=64) String workloadUid) { }
    record IncarnationVO(@NotBlank @Size(max=64) String podUid, @NotBlank @Size(max=63) String containerName,
            @NotBlank @Size(max=256) String containerId, @NotBlank @Size(max=64) String bootId) { }
    final class ReceiptUnavailableException extends IllegalStateException {
        public ReceiptUnavailableException() { super("CONFIG_RECEIPT_COMMIT_UNKNOWN"); }
    }
    final class ReceiptStoreUnavailableException extends IllegalStateException {
        public ReceiptStoreUnavailableException() { super("CONFIG_RECEIPT_STORE_UNAVAILABLE"); }
    }
}
