package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;

import java.time.Instant;
import java.util.Optional;

/** Authoritative durable facts for one deployment-approved Service binding, independent of config observations. */
public interface DdcServiceObservationRepository {
    long nextWriterEpoch(@NotNull @Valid DdcTenantContextDTO trusted);
    AcceptanceEnum upsert(@NotNull @Valid DdcTenantContextDTO trusted, @NotNull @Valid DdcServiceObservationBindingVO binding,
            @Positive long writerEpoch, @Positive long reportSeq, @NotNull Instant capturedAt, @NotNull @Valid DdcServiceObservationViewVO view);
    Optional<DdcServiceObservationViewVO> findCurrent(@NotNull @Valid DdcTenantContextDTO trusted,
            @NotNull @Valid DdcServiceObservationBindingVO binding);

    final class StoreUnavailableException extends IllegalStateException {
        public StoreUnavailableException() { super("TIANSHU_SERVICE_OBSERVATION_STORE_UNAVAILABLE", null); }
    }
}
