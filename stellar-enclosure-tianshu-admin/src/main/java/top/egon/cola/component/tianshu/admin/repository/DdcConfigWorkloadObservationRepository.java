package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.WorkloadViewVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import java.time.Instant;
import java.util.Optional;

public interface DdcConfigWorkloadObservationRepository {
    long nextWriterEpoch(@NotNull @Valid DdcTenantContextDTO trusted);
    AcceptanceEnum upsert(@NotNull @Valid DdcTenantContextDTO trusted, @NotNull @Valid TargetBindingVO target,
            @Positive long writerEpoch, @Positive long reportSeq, @NotNull Instant capturedAt, @NotNull WorkloadViewVO view);
    Optional<WorkloadViewVO> findCurrent(@NotNull @Valid DdcTenantContextDTO trusted, @NotNull @Valid TargetBindingVO target);
    enum AcceptanceEnum { SAME, ADVANCED, STALE, CONFLICT }
    final class StoreUnavailableException extends IllegalStateException {
        public StoreUnavailableException() { super("TIANSHU_CONFIG_OBSERVATION_STORE_UNAVAILABLE", null); }
    }
}
