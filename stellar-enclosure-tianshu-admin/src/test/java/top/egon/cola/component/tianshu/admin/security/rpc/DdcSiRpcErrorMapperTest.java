package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcSiRpcErrorMapperTest {
    @Test
    void nativeSiFailureUsesOnlyStandardStructuredStatus() throws Exception {
        StatusRuntimeException failure = DdcSiRpcErrorMapper.status(Status.FAILED_PRECONDITION,
                "SI_PINNED_HISTORY_UNAVAILABLE", false);
        assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertStandard(failure, "SI_PINNED_HISTORY_UNAVAILABLE", false);
        assertThat(failure.getTrailers().keys()).containsExactly("grpc-status-details-bin");
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"INVALID_ARGUMENT", "PERMISSION_DENIED", "NOT_FOUND",
            "ABORTED", "FAILED_PRECONDITION", "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL", "UNAUTHENTICATED"})
    void everyExistingSiFailureCodeSurvivesWithoutCauseOrDescriptionLeak(Status.Code code) throws Exception {
        Status status = code.toStatus().withDescription("synthetic-sensitive-detail")
                .withCause(new IllegalStateException("synthetic-sensitive-cause"));
        StatusRuntimeException failure = DdcSiRpcErrorMapper.status(status, "TIANSHU_SI_VALIDATION_ERROR", false);
        assertThat(failure.getStatus().getCode()).isEqualTo(code);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getMessage()).doesNotContain("synthetic-sensitive");
        assertStandard(failure, "TIANSHU_SI_VALIDATION_ERROR", false);
    }

    @Test
    void temporaryFailureRetainsRetryabilityOnlyAsDiagnosticMetadata() throws Exception {
        assertStandard(DdcSiRpcErrorMapper.status(Status.UNAVAILABLE, "TIANSHU_SERVICE_UNAVAILABLE", true),
                "TIANSHU_SERVICE_UNAVAILABLE", true);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"synthetic-secret", "OTHER_DOMAIN_REASON", "TIANSHU_SI_ secret", "TIANSHU_SI_\r\nSECRET",
            "TIANSHU_SI_ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"})
    void arbitraryOrUnboundedReasonsAreNotExposed(String reason) throws Exception {
        StatusRuntimeException failure = DdcSiRpcErrorMapper.status(Status.INVALID_ARGUMENT, reason, false);
        assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertStandard(failure, "TIANSHU_SI_INTERNAL_ERROR", false);
    }

    @Test
    void successOrMissingStatusCannotBeEncodedAsFailure() {
        assertThatThrownBy(() -> DdcSiRpcErrorMapper.status(Status.OK, "TIANSHU_SI_INTERNAL_ERROR", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DdcSiRpcErrorMapper.status(null, "TIANSHU_SI_INTERNAL_ERROR", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertStandard(StatusRuntimeException failure, String reason, boolean retryable) throws Exception {
        com.google.rpc.Status structured = StatusProto.fromThrowable(failure);
        assertThat(structured).isNotNull();
        assertThat(structured.getCode()).isEqualTo(failure.getStatus().getCode().value());
        assertThat(structured.getMessage()).isEqualTo("SI RPC operation failed");
        assertThat(structured.getDetailsCount()).isEqualTo(1);
        ErrorInfo detail = structured.getDetails(0).unpack(ErrorInfo.class);
        assertThat(detail.getReason()).isEqualTo(reason);
        assertThat(detail.getDomain()).isEqualTo("tianshu.si");
        assertThat(detail.getMetadataMap()).isEqualTo(Map.of("retryable", Boolean.toString(retryable)));
    }
}
