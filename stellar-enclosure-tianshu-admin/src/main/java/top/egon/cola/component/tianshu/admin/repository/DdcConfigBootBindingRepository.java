package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;

import java.util.Optional;
import java.util.List;
import jakarta.validation.constraints.*;

/** Authoritative immutable boot binding. Callers supply only freshly authorized incarnation facts. */
public interface DdcConfigBootBindingRepository {

    DdcConfigBootBindingBO createOrConfirm(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull @Valid DdcConfigBootBindingBO verified);
    Optional<DdcConfigBootBindingBO> findConfirmed(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull @Valid DdcConfigBootBindingBO verified);
    DdcConfigBootBindingBO assertConfirmedFence(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull @Valid DdcConfigBootBindingBO verified);

    /** Read only: bootId and fence are learned from committed authority for freshly observed containers. */
    List<DdcConfigBootBindingBO> findCurrentBindings(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotBlank @Size(max = 64) String clusterId, @NotBlank @Size(max = 63) String k8sNamespace,
            @NotBlank @Size(max = 64) String workloadUid,
            @NotNull @Size(max = 500) List<@Valid ObservedIncarnationVO> observed);

    record ObservedIncarnationVO(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[\\x21-\\x7e]+") String podUid,
            @NotBlank @Size(max = 63) @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?") String containerName,
            @NotBlank @Size(max = 256) @Pattern(regexp = "[\\x21-\\x7e]+") String containerId) { }

    /** Known authoritative identity/fence mismatch; contains no storage or credential details. */
    class BindingConflictException extends IllegalStateException {
        public BindingConflictException() { super("TIANSHU_CONFIG_BOOT_BINDING_CONFLICT"); }
    }

    /** A committed authoritative outcome could not be established. */
    class BindingUnavailableException extends IllegalStateException {
        public BindingUnavailableException() { super("TIANSHU_CONFIG_BOOT_BINDING_UNAVAILABLE"); }
    }
}
