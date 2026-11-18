package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;

import java.util.Optional;

/** Authoritative immutable boot binding. Callers supply only freshly authorized incarnation facts. */
public interface DdcConfigBootBindingRepository {

    DdcConfigBootBindingBO createOrConfirm(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull @Valid DdcConfigBootBindingBO verified);
    Optional<DdcConfigBootBindingBO> findConfirmed(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull @Valid DdcConfigBootBindingBO verified);
    DdcConfigBootBindingBO assertConfirmedFence(@NotNull @Valid DdcTenantContextDTO trustedContext,
            @NotNull @Valid DdcConfigBootBindingBO verified);

    /** Known authoritative identity/fence mismatch; contains no storage or credential details. */
    class BindingConflictException extends IllegalStateException {
        public BindingConflictException() { super("TIANSHU_CONFIG_BOOT_BINDING_CONFLICT"); }
    }

    /** A committed authoritative outcome could not be established. */
    class BindingUnavailableException extends IllegalStateException {
        public BindingUnavailableException() { super("TIANSHU_CONFIG_BOOT_BINDING_UNAVAILABLE"); }
    }
}
