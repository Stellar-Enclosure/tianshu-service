package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.egon.cola.component.rpc.provider.server.RpcProviderExceptionMapper;

import java.util.Optional;

/**
 * Native SI failures use the standard gRPC rich-error contract, independent of retired DDC transports.
 * / 原生 SI 失败使用标准 gRPC 结构化错误，不依赖旧 DDC 运输协议。
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DdcSiRpcErrorMapper {
    public static final String DOMAIN = "tianshu.si";
    private static final String INTERNAL_REASON = "TIANSHU_SI_INTERNAL_ERROR";
    private static final String SAFE_MESSAGE = "SI RPC operation failed";

    /**
     * Preserves the gRPC status code; only a bounded diagnostic code and retryability cross the boundary.
     * / 保留状态码；仅传递有界诊断码及可重试标记，不传递异常消息或调用参数。
     */
    public static StatusRuntimeException status(Status status, String reason, boolean retryable) {
        if (status == null || status.isOk()) {
            throw new IllegalArgumentException("An SI failure status is required");
        }
        String safeReason = reason != null && reason.matches("[A-Z][A-Z0-9_]{0,62}")
                && (reason.startsWith("TIANSHU_SI_") || reason.equals("SI_PINNED_HISTORY_UNAVAILABLE")
                    || reason.equals("TIANSHU_SERVICE_UNAVAILABLE")) ? reason : INTERNAL_REASON;
        return new NativeSiStatusException(wire(status.getCode(), safeReason, retryable), safeReason, retryable);
    }

    /** 仅接受本工厂建立的脱敏错误；普通业务 Status 不建立可信来源。 <p>Accepts only factory-owned sanitized provenance.</p> */
    public static RpcProviderExceptionMapper providerExceptionMapper() {
        return failure -> failure instanceof NativeSiStatusException nativeFailure
                ? Optional.of(wire(nativeFailure.code, nativeFailure.reason, nativeFailure.retryable))
                : Optional.empty();
    }

    private static StatusRuntimeException wire(Status.Code code, String reason, boolean retryable) {
        ErrorInfo detail = ErrorInfo.newBuilder().setDomain(DOMAIN).setReason(reason)
                .putMetadata("retryable", Boolean.toString(retryable)).build();
        com.google.rpc.Status richStatus = com.google.rpc.Status.newBuilder()
                .setCode(code.value()).setMessage(SAFE_MESSAGE).addDetails(Any.pack(detail)).build();
        return StatusProto.toStatusRuntimeException(richStatus);
    }

    /** Private provenance cannot be recreated by forging a rich-error trailer. */
    private static final class NativeSiStatusException extends StatusRuntimeException {
        private static final long serialVersionUID = 1L;
        private final Status.Code code;
        private final String reason;
        private final boolean retryable;

        private NativeSiStatusException(StatusRuntimeException wire, String reason, boolean retryable) {
            super(wire.getStatus(), wire.getTrailers());
            this.code = wire.getStatus().getCode();
            this.reason = reason;
            this.retryable = retryable;
        }
    }
}
