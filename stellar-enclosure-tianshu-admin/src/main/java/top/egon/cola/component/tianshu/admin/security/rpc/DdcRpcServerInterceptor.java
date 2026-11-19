package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.protobuf.Message;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.util.PatternMatchUtils;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcCanonicalRequest;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcMetadataKeys;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcOperation;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcOperationResolver;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcRequestSigner;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcAdmissionIdentityResolver;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 在 Provider 执行前认证单条 Tianshu unary RPC 请求。
 * / Authenticates one Tianshu unary RPC request before provider execution.
 */
@Slf4j
public final class DdcRpcServerInterceptor implements ServerInterceptor {

    /** 需要 namespace 授权的 AI 操作。 / AI operations that require namespace authorization. */
    private static final Set<DdcRpcOperation> AI_OPERATIONS = java.util.EnumSet.of(
            DdcRpcOperation.AI_READ, DdcRpcOperation.AI_REPORT, DdcRpcOperation.AI_WRITE, DdcRpcOperation.AI_PUBLISH);

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NONCE = Pattern.compile("[\\x21-\\x7e]{1,128}");
    private static final Pattern TENANT_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final String NATIVE_SI_RESOLVE =
            DdcSiResourceGrpcServiceGrpc.getResolveSiResourceMethod().getFullMethodName();

    private final DdcAdminProperties.Rpc properties;
    private final DdcHmacCredentialRegistry credentialRegistry;
    private final DdcTenantRegistry tenantRegistry;
    private final DdcTenantContextExecutor tenantContextExecutor;
    private final DdcNonceStore nonceStore;
    private final Clock clock;
    private final ObjectProvider<IdpRpcAdmissionIdentityResolver> nativeIdentities;
    private final DdcRpcRequestSigner signer = new DdcRpcRequestSigner();
    private final DdcRpcOperationResolver operationResolver =
            new DdcRpcOperationResolver();
    private final DdcRpcScopeExtractor scopeExtractor =
            new DdcRpcScopeExtractor();

    /** 创建生产时钟的服务端认证拦截器。 / Creates the server authenticator with the production clock. */
    public DdcRpcServerInterceptor(
            DdcAdminProperties properties,
            DdcHmacCredentialRegistry credentialRegistry,
            DdcTenantRegistry tenantRegistry,
            DdcTenantContextExecutor tenantContextExecutor,
            DdcNonceStore nonceStore) {
        this(properties, credentialRegistry, tenantRegistry, tenantContextExecutor,
                nonceStore, Clock.systemUTC());
    }

    /** Supplies the verifier-owned native identity boundary independently of legacy signing credentials. */
    public DdcRpcServerInterceptor(DdcAdminProperties properties, DdcHmacCredentialRegistry credentialRegistry,
            DdcTenantRegistry tenantRegistry, DdcTenantContextExecutor tenantContextExecutor,
            DdcNonceStore nonceStore, ObjectProvider<IdpRpcAdmissionIdentityResolver> nativeIdentities) {
        this(properties, credentialRegistry, tenantRegistry, tenantContextExecutor, nonceStore,
                Clock.systemUTC(), nativeIdentities);
    }

    DdcRpcServerInterceptor(
            DdcAdminProperties properties,
            DdcHmacCredentialRegistry credentialRegistry,
            DdcTenantRegistry tenantRegistry,
            DdcTenantContextExecutor tenantContextExecutor,
            DdcNonceStore nonceStore,
            Clock clock) {
        this(properties, credentialRegistry, tenantRegistry, tenantContextExecutor, nonceStore, clock,
                new StaticListableBeanFactory().getBeanProvider(IdpRpcAdmissionIdentityResolver.class));
    }

    private DdcRpcServerInterceptor(DdcAdminProperties properties, DdcHmacCredentialRegistry credentialRegistry,
            DdcTenantRegistry tenantRegistry, DdcTenantContextExecutor tenantContextExecutor,
            DdcNonceStore nonceStore, Clock clock, ObjectProvider<IdpRpcAdmissionIdentityResolver> nativeIdentities) {
        if (properties == null || credentialRegistry == null || tenantRegistry == null
                || tenantContextExecutor == null || clock == null || nativeIdentities == null) {
            throw new IllegalArgumentException(
                    "properties, credentialRegistry, tenantRegistry, tenantContextExecutor and clock are required");
        }
        this.properties = properties.getRpc();
        this.credentialRegistry = credentialRegistry;
        this.tenantRegistry = tenantRegistry;
        this.tenantContextExecutor = tenantContextExecutor;
        this.nonceStore = nonceStore;
        this.clock = clock;
        this.nativeIdentities = nativeIdentities;
        validateConfiguration();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (NATIVE_SI_RESOLVE.equals(call.getMethodDescriptor().getFullMethodName())) {
            final IdpRpcAdmissionIdentityResolver resolver;
            try {
                resolver = nativeIdentities.getIfAvailable();
            } catch (RuntimeException unavailable) {
                return rejectNativeUnavailable(call);
            }
            if (resolver == null) {
                return rejectNativeUnavailable(call);
            }
            return new NativeSiAuthenticatingListener<>(call, resolver, next.startCall(call, headers));
        }
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
        return new AuthenticatingListener<>(call, headers, delegate);
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> rejectNativeUnavailable(ServerCall<ReqT, RespT> call) {
        call.close(Status.UNAVAILABLE.withDescription("Native SI authentication is unavailable"), new Metadata());
        return new ServerCall.Listener<>() { };
    }

    /** The exact SI method has one authentication strategy; no signature or metadata fallback exists. */
    private static final class NativeSiAuthenticatingListener<ReqT, RespT> extends ServerCall.Listener<ReqT> {
        private final ServerCall<ReqT, RespT> call;
        private final IdpRpcAdmissionIdentityResolver resolver;
        private final ServerCall.Listener<ReqT> delegate;
        private ResolveSiResourceRequest request;
        private boolean closed;
        private boolean ready;

        private NativeSiAuthenticatingListener(ServerCall<ReqT, RespT> call,
                IdpRpcAdmissionIdentityResolver resolver, ServerCall.Listener<ReqT> delegate) {
            this.call = call;
            this.resolver = resolver;
            this.delegate = delegate;
        }

        @Override
        public void onMessage(ReqT message) {
            if (closed) {
                return;
            }
            if (request != null || !(message instanceof ResolveSiResourceRequest received) || !received.getExecutionRequired()) {
                reject(Status.INVALID_ARGUMENT.withDescription("An SI execution snapshot request is required").asRuntimeException());
                return;
            }
            request = received;
            if (authorize()) {
                delegate.onMessage(message);
                if (ready) {
                    delegate.onReady();
                }
            }
        }

        @Override
        public void onHalfClose() {
            if (closed) {
                return;
            }
            if (request == null) {
                reject(Status.INVALID_ARGUMENT.withDescription("An SI execution snapshot request is required").asRuntimeException());
            } else if (authorize()) {
                delegate.onHalfClose();
            }
        }

        private boolean authorize() {
            try {
                if (resolver.resolve(NATIVE_SI_RESOLVE, request) == null) {
                    throw new IllegalStateException("Native SI identity decision is unavailable");
                }
                return true;
            } catch (StatusRuntimeException rejected) {
                reject(rejected);
            } catch (RuntimeException unavailable) {
                reject(Status.UNAVAILABLE.withDescription("Native SI authentication is unavailable").asRuntimeException());
            }
            return false;
        }

        @Override public void onCancel() { delegate.onCancel(); }
        @Override public void onComplete() { delegate.onComplete(); }
        @Override public void onReady() {
            ready = true;
            if (!closed && request != null) {
                delegate.onReady();
            }
        }

        private void reject(StatusRuntimeException failure) {
            closed = true;
            call.close(failure.getStatus(), failure.getTrailers() == null ? new Metadata() : failure.getTrailers());
        }
    }

    private Authentication authenticate(
            String fullMethodName,
            Metadata headers,
            Message request) {
        Headers signed = null;
        DdcHmacCredential credential = null;
        long timestamp = 0L;
        long now = clock.millis();
        long skew = properties.getAllowedClockSkewSeconds() * 1000L;
        if (!properties.isSignatureEnabled()) {
            throw authentication(DdcErrorStatus.SIGNATURE_REQUIRED);
        }
        signed = headers(headers);
        timestamp = timestamp(signed.timestamp());
        if (timestamp < now - skew || timestamp > now + skew) {
            throw authentication(DdcErrorStatus.SIGNATURE_EXPIRED);
        }
        credential = credentialRegistry.resolve(
                signed.accessKey()).orElseThrow(() ->
                authentication(DdcErrorStatus.SIGNATURE_INVALID));
        long signedTenantId = tenantId(signed.tenantId());
        DdcRpcCanonicalRequest canonical = new DdcRpcCanonicalRequest(
                signedTenantId, fullMethodName, timestamp, signed.nonce(), request);
        if (!signer.matches(
                canonical.contentSha256(), signed.contentSha256())
                || !signer.matches(
                signer.sign(canonical, credential.secret()),
                signed.signature())) {
            throw authentication(DdcErrorStatus.SIGNATURE_INVALID);
        }
        DdcTenantContextDTO tenantContext;
        Set<String> namespacePatterns;
        try {
            tenantContext = tenantRegistry.resolveCredentialContext(
                    credential.credentialId(), signed.tenantId());
            namespacePatterns = tenantRegistry.namespacePatternsForCredential(
                    credential.credentialId());
        } catch (IllegalArgumentException invalidTenantBinding) {
            throw authentication(DdcErrorStatus.SIGNATURE_INVALID);
        }
        DdcRpcOperation operation;
        try {
            operation = operationResolver.resolve(fullMethodName);
        } catch (IllegalArgumentException unknownMethod) {
            throw denied();
        }
        DdcRpcScopeExtractor.Scope scope;
        try {
            scope = scopeExtractor.extract(fullMethodName, request);
        } catch (IllegalArgumentException invalidScope) {
            throw DdcRpcProviderExceptionMapper.status(
                    Status.INVALID_ARGUMENT,
                    DdcErrorStatus.INVALID_REQUEST.getStatus(),
                    DdcErrorStatus.INVALID_REQUEST.getMessage(),
                    false
            );
        }
        if (!credential.permits(
                scope.clientType(),
                operation.name(),
                scope.appCode(),
                scope.env(),
                scope.bizCode())) {
            throw denied();
        }
        if (AI_OPERATIONS.contains(operation)
                && !permitsNamespaces(namespacePatterns, scope.namespaceCodes())) {
            throw denied();
        }
        consumeNonce(credential, signed.nonce(), timestamp, now, skew, operation);
        return new Authentication(
                credential.principal(
                        scope.appCode(), scope.env(), scope.bizCode(),
                        tenantContext, namespacePatterns)
        );
    }

    private Headers headers(Metadata metadata) {
        String accessKey = metadata.get(DdcRpcMetadataKeys.ACCESS_KEY);
        String timestamp = metadata.get(DdcRpcMetadataKeys.TIMESTAMP);
        String nonce = metadata.get(DdcRpcMetadataKeys.NONCE);
        String contentSha256 = metadata.get(DdcRpcMetadataKeys.CONTENT_SHA256);
        String signature = metadata.get(DdcRpcMetadataKeys.SIGNATURE);
        String tenantId = metadata.get(DdcRpcMetadataKeys.TENANT_ID);
        String contractVersion = metadata.get(
                DdcRpcMetadataKeys.CONTRACT_VERSION);
        if (accessKey == null || timestamp == null || nonce == null
                || contentSha256 == null || signature == null
                || tenantId == null || contractVersion == null) {
            throw authentication(DdcErrorStatus.SIGNATURE_REQUIRED);
        }
        if (accessKey.isBlank() || accessKey.length() > 256
                || !NONCE.matcher(nonce).matches()
                || !TENANT_ID.matcher(tenantId).matches()
                || !HEX_64.matcher(contentSha256).matches()
                || !HEX_64.matcher(signature).matches()
                || !DdcRpcCanonicalRequest.CONTRACT_VERSION.equals(
                contractVersion)) {
            throw authentication(DdcErrorStatus.SIGNATURE_INVALID);
        }
        tenantId(tenantId);
        return new Headers(
                accessKey, timestamp, nonce, contentSha256, signature, tenantId);
    }

    private long tenantId(String value) {
        try {
            long tenantId = Long.parseLong(value);
            if (tenantId <= 0 || !Long.toString(tenantId).equals(value)) {
                throw authentication(DdcErrorStatus.SIGNATURE_INVALID);
            }
            return tenantId;
        } catch (NumberFormatException invalid) {
            throw authentication(DdcErrorStatus.SIGNATURE_INVALID);
        }
    }

    private long timestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw authentication(DdcErrorStatus.SIGNATURE_INVALID);
        }
    }

    private void consumeNonce(
            DdcHmacCredential credential,
            String nonce,
            long timestamp,
            long now,
            long skew,
            DdcRpcOperation operation) {
        try {
            Duration ttl = Duration.ofMillis(Math.max(
                    1L, timestamp + skew - now));
            if (!nonceStore.markIfAbsent(
                    credential.credentialId(), nonce, ttl)) {
                throw authentication(DdcErrorStatus.SIGNATURE_REPLAY);
            }
        } catch (StatusRuntimeException rejected) {
            throw rejected;
        } catch (RuntimeException unavailable) {
            if (operation != DdcRpcOperation.AI_READ) {
                throw DdcRpcProviderExceptionMapper.status(
                        Status.UNAVAILABLE,
                        "TIANSHU_NONCE_STORE_UNAVAILABLE",
                        "Tianshu nonce store is unavailable",
                        true
                );
            }
        }
    }

    private boolean permitsNamespaces(Set<String> patterns, Set<String> namespaceCodes) {
        return !namespaceCodes.isEmpty()
                && namespaceCodes.stream().allMatch(namespaceCode -> permitsNamespace(patterns, namespaceCode));
    }

    private boolean permitsNamespace(Set<String> patterns, String namespaceCode) {
        return namespaceCode != null && !patterns.isEmpty()
                && patterns.stream().anyMatch(pattern ->
                PatternMatchUtils.simpleMatch(pattern, namespaceCode));
    }

    private StatusRuntimeException authentication(DdcErrorStatus error) {
        return DdcRpcProviderExceptionMapper.status(
                Status.UNAUTHENTICATED,
                error.getStatus(),
                error.getMessage(),
                false
        );
    }

    private StatusRuntimeException denied() {
        return DdcRpcProviderExceptionMapper.status(
                Status.PERMISSION_DENIED,
                "TIANSHU_HMAC_SCOPE_DENIED",
                "Tianshu HMAC credential scope denied",
                false
        );
    }

    private void validateConfiguration() {
        if (properties.getAllowedClockSkewSeconds() <= 0) {
            throw new IllegalStateException(
                    "Tianshu RPC allowed clock skew must be positive");
        }
        if (properties.isSignatureEnabled() && credentialRegistry.isEmpty()) {
            throw new IllegalStateException(
                    "Tianshu RPC credentials are required when signatures are enabled");
        }
        if (properties.isSignatureEnabled() && nonceStore == null) {
            throw new IllegalStateException(
                    "Redis DdcNonceStore is required when Tianshu RPC signatures are enabled");
        }
    }

    private final class AuthenticatingListener<ReqT, RespT>
            extends ServerCall.Listener<ReqT> {

        private final ServerCall<ReqT, RespT> call;
        private final Metadata headers;
        private final ServerCall.Listener<ReqT> delegate;
        private Context authenticatedContext;
        private DdcServicePrincipal authenticatedPrincipal;
        private boolean closed;
        private boolean ready;

        private AuthenticatingListener(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCall.Listener<ReqT> delegate) {
            this.call = call;
            this.headers = headers;
            this.delegate = delegate;
        }

        @Override
        public void onMessage(ReqT request) {
            if (closed) {
                return;
            }
            if (!(request instanceof Message message)) {
                reject(DdcRpcProviderExceptionMapper.status(
                        Status.INVALID_ARGUMENT,
                        DdcErrorStatus.INVALID_REQUEST.getStatus(),
                        DdcErrorStatus.INVALID_REQUEST.getMessage(),
                        false
                ));
                return;
            }
            try {
                Authentication authentication = authenticate(
                        call.getMethodDescriptor().getFullMethodName(),
                        headers,
                        message
                );
                authenticatedPrincipal = authentication.principal();
                authenticatedContext = authenticatedPrincipal
                        .bind(Context.current());
                callback(() -> delegate.onMessage(request));
                if (ready) {
                    callback(delegate::onReady);
                }
            } catch (StatusRuntimeException failure) {
                reject(failure);
            }
        }

        @Override
        public void onHalfClose() {
            if (closed) {
                return;
            }
            if (authenticatedContext == null) {
                reject(DdcRpcProviderExceptionMapper.status(
                        Status.INVALID_ARGUMENT,
                        DdcErrorStatus.INVALID_REQUEST.getStatus(),
                        DdcErrorStatus.INVALID_REQUEST.getMessage(),
                        false
                ));
                return;
            }
            callback(delegate::onHalfClose);
        }

        @Override
        public void onCancel() {
            callback(delegate::onCancel);
        }

        @Override
        public void onComplete() {
            callback(delegate::onComplete);
        }

        @Override
        public void onReady() {
            ready = true;
            if (authenticatedContext != null) {
                callback(delegate::onReady);
            }
        }

        private void callback(Runnable action) {
            if (authenticatedContext == null || authenticatedPrincipal == null) {
                action.run();
            } else {
                authenticatedContext.run(() -> tenantContextExecutor.run(
                        authenticatedPrincipal.tenantContext(), action));
            }
        }

        private void reject(StatusRuntimeException failure) {
            closed = true;
            log.debug("Rejected Tianshu RPC request method={} status={}",
                    call.getMethodDescriptor().getFullMethodName(),
                    failure.getStatus().getCode());
            call.close(
                    failure.getStatus(),
                    failure.getTrailers() == null
                            ? new Metadata()
                            : failure.getTrailers()
            );
        }
    }

    private record Headers(
            String accessKey,
            String timestamp,
            String nonce,
            String contentSha256,
            String signature,
            String tenantId) {
    }

    private record Authentication(DdcServicePrincipal principal) {
    }
}
