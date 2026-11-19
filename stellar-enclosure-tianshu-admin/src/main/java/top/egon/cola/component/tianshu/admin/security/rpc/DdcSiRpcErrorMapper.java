package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        ErrorInfo detail = ErrorInfo.newBuilder().setDomain(DOMAIN).setReason(safeReason)
                .putMetadata("retryable", Boolean.toString(retryable)).build();
        com.google.rpc.Status richStatus = com.google.rpc.Status.newBuilder()
                .setCode(status.getCode().value()).setMessage(SAFE_MESSAGE).addDetails(Any.pack(detail)).build();
        return StatusProto.toStatusRuntimeException(richStatus);
    }
}
