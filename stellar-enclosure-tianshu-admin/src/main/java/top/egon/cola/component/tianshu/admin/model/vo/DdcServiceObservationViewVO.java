package top.egon.cola.component.tianshu.admin.model.vo;

import org.jspecify.annotations.Nullable;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Observed EndpointSlice facts for one approved Service port. */
public record DdcServiceObservationViewVO(
        StatusEnum status, @Nullable String reasonCode, @Nullable Instant observedAt, @Nullable Instant validUntil,
        @Nullable String serviceResourceVersion, List<EndpointVO> endpoints) {

    public DdcServiceObservationViewVO {
        Objects.requireNonNull(status, "status");
        endpoints = List.copyOf(endpoints);
        if (validUntil != null && (observedAt == null || !validUntil.isAfter(observedAt))) {
            throw invalid();
        }
        if (status == StatusEnum.FRESH) {
            if (observedAt == null || validUntil == null || serviceResourceVersion == null
                    || serviceResourceVersion.isBlank() || serviceResourceVersion.length() > 128) {
                throw invalid();
            }
        } else if (!endpoints.isEmpty()) {
            throw invalid();
        }
    }

    public boolean fresh() {
        return status == StatusEnum.FRESH;
    }

    public record EndpointVO(String podUid, String podName, int targetPort,
                             @Nullable Boolean ready, @Nullable Boolean serving, @Nullable Boolean terminating) {
        public EndpointVO {
            if (podUid == null || podUid.isBlank() || podUid.length() > 128
                    || podName == null || podName.isBlank() || podName.length() > 253
                    || targetPort < 1 || targetPort > 65_535) {
                throw invalid();
            }
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("TIANSHU_SERVICE_OBSERVATION_VIEW_INVALID");
    }
}
