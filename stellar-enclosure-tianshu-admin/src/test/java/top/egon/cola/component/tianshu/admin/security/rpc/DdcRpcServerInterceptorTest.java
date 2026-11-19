package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcCredentialTenantDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.management.DdcManagementFacade;
import top.egon.cola.component.tianshu.model.management.DdcManagementConfigQuery;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiSkillDraftUpload;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiSkillPublishRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiUsageReport;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiUsageReportBatch;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiResourceQuery;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcAiResourceServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcManagementServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcScope;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.FindConfigRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.FindConfigResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.UpsertConfigRequest;
import top.egon.cola.component.rpc.tianshu.mapping.DdcRpcStatusExceptionMapper;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcCanonicalRequest;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcMetadataKeys;
import top.egon.cola.component.rpc.tianshu.security.DdcRpcRequestSigner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DdcRpcServerInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final String ACCESS_KEY = "sdk-access";
    private static final String SECRET = "sdk-secret";

    private final DdcRpcRequestSigner signer = new DdcRpcRequestSigner();
    private final DdcManagementFacade facade = mock(DdcManagementFacade.class);
    private final AtomicReference<DdcServicePrincipal> principal =
            new AtomicReference<>();

    private DdcRpcServerInterceptor interceptor;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @BeforeEach
    void setUp() {
        DdcAdminProperties properties = properties(List.of(
                managementCredential("MANAGEMENT_CONFIG_READ")));
        interceptor = interceptor(properties, new InMemoryDdcNonceStore(100));
    }

    @Test
    void authenticatesDeterministicRequestAndExposesPrincipalContext() {
        FindConfigRequest request = request("biz-a", "dev", "app-a");
        MDC.put("tenantId", "999");
        MDC.put("userId", "system:outer");

        RecordingServerCall<FindConfigRequest, FindConfigResponse> call =
                invoke(request, signed(request, NOW.toEpochMilli(), "nonce-1"));

        assertThat(call.status()).isNull();
        verify(facade).findConfig(new DdcManagementConfigQuery("biz-a", "dev", "app-a"));
        assertThat(principal.get().credentialId()).isEqualTo("sdk-a");
        assertThat(principal.get().tenantId()).isEqualTo(101L);
        assertThat(principal.get().identityTenantId()).isEqualTo("tenant-a");
        assertThat(principal.get().namespacePatterns()).contains("orders-*");
        assertThat(principal.get().auditOperator("claimed-user"))
                .isEqualTo("service:sdk-a [requested=claimed-user]");
        assertThat(MDC.get("tenantId")).isEqualTo("999");
        assertThat(MDC.get("userId")).isEqualTo("system:outer");
        MDC.clear();
    }

    @Test
    void nativeSiResolveNeverFallsBackToAnOtherwiseValidLegacySignature() {
        interceptor = interceptor(properties(List.of(credential(List.of("AI_READ"), List.of("app-a")))),
                new InMemoryDdcNonceStore(100));
        var method = DdcSiResourceGrpcServiceGrpc.getResolveSiResourceMethod();
        var request = ResolveSiResourceRequest.newBuilder().setTenantId(101L).setBizCode("biz-a")
                .setNamespaceCode("orders-api").setEnvCode("dev").setAppCode("app-a")
                .setKind("MCP").setResourceKey("orders-tools").setExecutionRequired(true).build();

        var call = invoke(method, request,
                signed(method.getFullMethodName(), request, NOW.toEpochMilli(), "native-no-hmac-fallback"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run")));

        assertThat(call.status()).isNotNull();
        assertThat(call.status().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        verifyNoInteractions(facade);
    }

    @Test
    void rejectsMissingMalformedAndExpiredAuthenticationBeforeFacade() {
        FindConfigRequest request = request("biz-a", "dev", "app-a");

        assertRejected(request, new Metadata(), Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_REQUIRED");

        Metadata malformed = signed(request, NOW.toEpochMilli(), "nonce-bad-ts");
        malformed.removeAll(DdcRpcMetadataKeys.TIMESTAMP);
        malformed.put(DdcRpcMetadataKeys.TIMESTAMP, "not-a-number");
        assertRejected(request, malformed, Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_INVALID");

        Metadata unknownAccess = signed(
                request, NOW.toEpochMilli(), "nonce-unknown-access");
        unknownAccess.removeAll(DdcRpcMetadataKeys.ACCESS_KEY);
        unknownAccess.put(DdcRpcMetadataKeys.ACCESS_KEY, "unknown");
        assertRejected(request, unknownAccess, Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_INVALID");

        Metadata wrongContract = signed(
                request, NOW.toEpochMilli(), "nonce-contract");
        wrongContract.removeAll(DdcRpcMetadataKeys.CONTRACT_VERSION);
        wrongContract.put(DdcRpcMetadataKeys.CONTRACT_VERSION, "v1");
        assertRejected(request, wrongContract, Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_INVALID");

        Metadata missingTenant = signed(request, NOW.toEpochMilli(), "nonce-missing-tenant");
        missingTenant.removeAll(DdcRpcMetadataKeys.TENANT_ID);
        assertRejected(request, missingTenant, Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_REQUIRED");

        Metadata wrongTenant = signed(102L,
                DdcManagementServiceGrpc.getFindConfigMethod().getFullMethodName(),
                request, NOW.toEpochMilli(), "nonce-wrong-tenant");
        assertRejected(request, wrongTenant, Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_INVALID");

        assertRejected(
                request,
                signed(request, NOW.minusSeconds(301).toEpochMilli(),
                        "nonce-expired"),
                Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_EXPIRED"
        );
        verifyNoInteractions(facade);
    }

    @Test
    void rejectsBadHashBadSignatureAndReplayBeforeFacade() {
        FindConfigRequest request = request("biz-a", "dev", "app-a");
        Metadata badHash = signed(request, NOW.toEpochMilli(), "nonce-hash");
        badHash.removeAll(DdcRpcMetadataKeys.CONTENT_SHA256);
        badHash.put(DdcRpcMetadataKeys.CONTENT_SHA256, "0".repeat(64));
        assertRejected(request, badHash, Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_INVALID");

        Metadata badSignature = signed(
                request, NOW.toEpochMilli(), "nonce-signature");
        badSignature.removeAll(DdcRpcMetadataKeys.SIGNATURE);
        badSignature.put(DdcRpcMetadataKeys.SIGNATURE, "0".repeat(64));
        assertRejected(request, badSignature, Status.Code.UNAUTHENTICATED,
                "TIANSHU_SIGNATURE_INVALID");

        Metadata replay = signed(request, NOW.toEpochMilli(), "nonce-replay");
        RecordingServerCall<FindConfigRequest, FindConfigResponse> first =
                invoke(request, replay);
        assertThat(first.status()).isNull();
        RecordingServerCall<FindConfigRequest, FindConfigResponse> second =
                invoke(request, replay);
        assertThat(second.status().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(errorCode(second)).isEqualTo("TIANSHU_SIGNATURE_REPLAY");
    }

    @Test
    void rejectsWrongOperationScopeAndUnknownMethodBeforeFacade() {
        DdcAdminProperties wrongOperationProperties = properties(List.of(
                managementCredential("MANAGEMENT_CONFIG_WRITE")));
        interceptor = interceptor(wrongOperationProperties, new InMemoryDdcNonceStore(100));
        FindConfigRequest allowedScope = request("biz-a", "dev", "app-a");
        assertRejected(
                allowedScope,
                signed(allowedScope, NOW.toEpochMilli(), "wrong-operation"),
                Status.Code.PERMISSION_DENIED,
                "TIANSHU_HMAC_SCOPE_DENIED"
        );

        DdcAdminProperties scopedProperties = properties(List.of(
                managementCredential("MANAGEMENT_CONFIG_READ")));
        interceptor = interceptor(scopedProperties, new InMemoryDdcNonceStore(100));
        FindConfigRequest wrongScope = request("biz-a", "dev", "app-b");
        assertRejected(
                wrongScope,
                signed(wrongScope, NOW.toEpochMilli(), "wrong-scope"),
                Status.Code.PERMISSION_DENIED,
                "TIANSHU_HMAC_SCOPE_DENIED"
        );

        MethodDescriptor<StringValue, StringValue> unknown =
                MethodDescriptor.<StringValue, StringValue>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName("example.UnknownService/Echo")
                        .setRequestMarshaller(ProtoUtils.marshaller(
                                StringValue.getDefaultInstance()))
                        .setResponseMarshaller(ProtoUtils.marshaller(
                                StringValue.getDefaultInstance()))
                        .build();
        StringValue value = StringValue.of("request");
        RecordingServerCall<StringValue, StringValue> unknownCall = invoke(
                unknown,
                value,
                signed(unknown.getFullMethodName(), value,
                        NOW.toEpochMilli(), "unknown-method"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run"))
        );
        assertThat(unknownCall.status().getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(errorCode(unknownCall)).isEqualTo("TIANSHU_HMAC_SCOPE_DENIED");
        verifyNoInteractions(facade);
    }

    @Test
    void failsClosedForNonceStoreOutageOnWriteRequests() {
        DdcAdminProperties properties = properties(List.of(
                managementCredential("MANAGEMENT_CONFIG_WRITE")));
        interceptor = interceptor(properties, (credentialId, nonce, ttl) -> {
            throw new IllegalStateException("redis-secret");
        });
        var method = DdcManagementServiceGrpc.getUpsertConfigMethod();
        var request = UpsertConfigRequest.newBuilder()
                .setScope(scope("biz-a", "dev", "app-a"))
                .build();

        RecordingServerCall<?, ?> call = invoke(
                method,
                request,
                signed(method.getFullMethodName(), request,
                        NOW.toEpochMilli(), "nonce-store-down"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run"))
        );

        assertThat(call.status().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(errorCode(call)).isEqualTo("TIANSHU_NONCE_STORE_UNAVAILABLE");
        assertThat(call.status().getDescription()).doesNotContain("redis-secret");
        verifyNoInteractions(facade);
    }

    @Test
    void preservesReadAvailabilityWhenNonceStoreIsTemporarilyUnavailable() {
        DdcAdminProperties properties = properties(List.of(
                managementCredential("MANAGEMENT_CONFIG_READ")));
        interceptor = interceptor(properties, (credentialId, nonce, ttl) -> {
            throw new IllegalStateException("redis unavailable");
        });
        FindConfigRequest request = request("biz-a", "dev", "app-a");

        RecordingServerCall<FindConfigRequest, FindConfigResponse> call =
                invoke(request, signed(
                        request, NOW.toEpochMilli(), "read-store-down"));

        assertThat(call.status()).isNull();
        verify(facade).findConfig(new DdcManagementConfigQuery("biz-a", "dev", "app-a"));
    }

    @Test
    void enforcesNamespaceGrantForAiReadsOnly() {
        DdcAdminProperties properties = properties(List.of(
                credential(List.of("AI_READ"), List.of("app-a"))));
        interceptor = interceptor(properties, new InMemoryDdcNonceStore(100),
                List.of("orders-*"));
        var method = DdcAiResourceServiceGrpc.getGetPromptMethod();
        AiResourceQuery deniedNamespace = aiQuery("billing-api");
        RecordingServerCall<?, ?> deniedCall = invoke(
                method,
                deniedNamespace,
                signed(method.getFullMethodName(), deniedNamespace,
                        NOW.toEpochMilli(), "ai-namespace-denied"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run"))
        );

        assertThat(deniedCall.status()).as("ungranted namespace must be rejected")
                .isNotNull();
        assertThat(deniedCall.status().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(errorCode(deniedCall)).isEqualTo("TIANSHU_HMAC_SCOPE_DENIED");

        interceptor = interceptor(properties, new InMemoryDdcNonceStore(100), List.of());
        AiResourceQuery emptyGrantNamespace = aiQuery("orders-api");
        RecordingServerCall<?, ?> emptyGrantCall = invoke(
                method,
                emptyGrantNamespace,
                signed(method.getFullMethodName(), emptyGrantNamespace,
                        NOW.toEpochMilli(), "ai-namespace-empty-grant"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run"))
        );

        assertThat(emptyGrantCall.status()).as("empty namespace grant must be rejected")
                .isNotNull();
        assertThat(emptyGrantCall.status().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(errorCode(emptyGrantCall)).isEqualTo("TIANSHU_HMAC_SCOPE_DENIED");

        AiResourceQuery missingRoot = aiQuery("orders-api").toBuilder().clearResourceId().build();
        RecordingServerCall<?, ?> missingRootCall = invoke(
                method,
                missingRoot,
                signed(method.getFullMethodName(), missingRoot,
                        NOW.toEpochMilli(), "ai-root-id-missing"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run"))
        );
        assertThat(missingRootCall.status().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(errorCode(missingRootCall)).isEqualTo("TIANSHU_INVALID_REQUEST");
        verifyNoInteractions(facade);
    }

    private RecordingServerCall<FindConfigRequest, FindConfigResponse> invoke(
            FindConfigRequest request,
            Metadata headers) {
        return invoke(
                DdcManagementServiceGrpc.getFindConfigMethod(),
                request,
                headers,
                message -> {
                    principal.set(DdcServicePrincipal.current());
                    var requestScope = message.getScope();
                    facade.findConfig(new DdcManagementConfigQuery(
                            requestScope.getBizCode(), requestScope.getEnv(), requestScope.getAppCode()));
                }
        );
    }

    private <ReqT extends Message, RespT extends Message>
            RecordingServerCall<ReqT, RespT> invoke(
                    MethodDescriptor<ReqT, RespT> method,
                    ReqT request,
                    Metadata headers,
                    java.util.function.Consumer<ReqT> action) {
        RecordingServerCall<ReqT, RespT> call =
                new RecordingServerCall<>(method);
        AtomicReference<ReqT> received = new AtomicReference<>();
        ServerCallHandler<ReqT, RespT> handler = (ignored, ignoredHeaders) ->
                new ServerCall.Listener<>() {
                    @Override
                    public void onMessage(ReqT message) {
                        received.set(message);
                    }

                    @Override
                    public void onHalfClose() {
                        action.accept(received.get());
                    }
                };
        ServerCall.Listener<ReqT> listener = interceptor.interceptCall(
                call, headers, handler);
        listener.onMessage(request);
        listener.onHalfClose();
        return call;
    }

    private void assertRejected(
            FindConfigRequest request,
            Metadata headers,
            Status.Code status,
            String errorCode) {
        RecordingServerCall<FindConfigRequest, FindConfigResponse> call =
                invoke(request, headers);
        assertThat(call.status().getCode()).isEqualTo(status);
        assertThat(errorCode(call)).isEqualTo(errorCode);
    }

    private String errorCode(RecordingServerCall<?, ?> call) {
        return call.trailers()
                .get(DdcRpcStatusExceptionMapper.ERROR_DETAIL)
                .getCode();
    }

    private Metadata signed(
            FindConfigRequest request,
            long timestamp,
            String nonce) {
        return signed(
                DdcManagementServiceGrpc.getFindConfigMethod()
                        .getFullMethodName(),
                request,
                timestamp,
                nonce
        );
    }

    private Metadata signed(
            String fullMethodName,
            Message request,
            long timestamp,
            String nonce) {
        return signed(101L, fullMethodName, request, timestamp, nonce);
    }

    private Metadata signed(
            long tenantId,
            String fullMethodName,
            Message request,
            long timestamp,
            String nonce) {
        DdcRpcCanonicalRequest canonical = new DdcRpcCanonicalRequest(
                tenantId, fullMethodName, timestamp, nonce, request);
        Metadata headers = new Metadata();
        headers.put(DdcRpcMetadataKeys.ACCESS_KEY, ACCESS_KEY);
        headers.put(DdcRpcMetadataKeys.TENANT_ID, Long.toString(tenantId));
        headers.put(DdcRpcMetadataKeys.TIMESTAMP, Long.toString(timestamp));
        headers.put(DdcRpcMetadataKeys.NONCE, nonce);
        headers.put(DdcRpcMetadataKeys.CONTENT_SHA256,
                canonical.contentSha256());
        headers.put(DdcRpcMetadataKeys.SIGNATURE,
                signer.sign(canonical, SECRET));
        headers.put(DdcRpcMetadataKeys.CONTRACT_VERSION,
                DdcRpcCanonicalRequest.CONTRACT_VERSION);
        return headers;
    }

    @Test
    void aiReportUploadAndPublishRequireEveryNamespaceGrant() {
        DdcAdminProperties properties = properties(List.of(
                credential(List.of("AI_REPORT", "AI_WRITE", "AI_PUBLISH"), List.of("app-a"))));
        interceptor = interceptor(properties, new InMemoryDdcNonceStore(100), List.of("orders-*"));
        var report = DdcAiResourceServiceGrpc.getReportUsageMethod();
        AiUsageReportBatch mixed = AiUsageReportBatch.newBuilder()
                .addReports(usage("orders-api")).addReports(usage("billing-api")).build();
        RecordingServerCall<?, ?> mixedCall = invoke(report, mixed,
                signed(report.getFullMethodName(), mixed, NOW.toEpochMilli(), "ai-report-mixed"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run")));
        assertThat(mixedCall.status().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);

        AiUsageReportBatch granted = AiUsageReportBatch.newBuilder()
                .addReports(usage("orders-api")).addReports(usage("orders-web")).build();
        RecordingServerCall<?, ?> grantedCall = invoke(report, granted,
                signed(report.getFullMethodName(), granted, NOW.toEpochMilli(), "ai-report-granted"),
                ignored -> principal.set(DdcServicePrincipal.current()));
        assertThat(grantedCall.status()).isNull();
        assertThat(principal.get().allowedOperations()).contains("AI_REPORT");
        assertThat(principal.get().appCode()).isNull();

        var upload = DdcAiResourceServiceGrpc.getReplaceSkillDraftMethod();
        AiSkillDraftUpload foreignUpload = AiSkillDraftUpload.newBuilder().setNamespaceCode("billing-api")
                .setResourceKey("refund").setResourceId("7").setExpectedRevision("1").build();
        RecordingServerCall<?, ?> uploadCall = invoke(upload, foreignUpload,
                signed(upload.getFullMethodName(), foreignUpload, NOW.toEpochMilli(), "ai-upload-denied"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run")));
        assertThat(uploadCall.status().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);

        var publish = DdcAiResourceServiceGrpc.getPublishSkillDraftMethod();
        AiSkillPublishRequest grantedPublish = AiSkillPublishRequest.newBuilder().setNamespaceCode("orders-api")
                .setResourceKey("refund").setResourceId("7").setExpectedRevision("1")
                .setRequestId("auth-0123456789abcdef").build();
        RecordingServerCall<?, ?> publishCall = invoke(publish, grantedPublish,
                signed(publish.getFullMethodName(), grantedPublish, NOW.toEpochMilli(), "ai-publish-granted"),
                ignored -> principal.set(DdcServicePrincipal.current()));
        assertThat(publishCall.status()).isNull();
        verifyNoInteractions(facade);
    }

    @Test
    void aiReportIsAWriteThatFailsClosedWhenTheNonceStoreIsDown() {
        DdcAdminProperties properties = properties(List.of(credential(List.of("AI_REPORT"), List.of("app-a"))));
        interceptor = interceptor(properties, (credentialId, nonce, ttl) -> {
            throw new IllegalStateException("redis down");
        }, List.of("orders-*"));
        var report = DdcAiResourceServiceGrpc.getReportUsageMethod();
        AiUsageReportBatch batch = AiUsageReportBatch.newBuilder().addReports(usage("orders-api")).build();

        RecordingServerCall<?, ?> call = invoke(report, batch,
                signed(report.getFullMethodName(), batch, NOW.toEpochMilli(), "ai-report-nonce-down"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run")));

        assertThat(call.status().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(errorCode(call)).isEqualTo("TIANSHU_NONCE_STORE_UNAVAILABLE");
    }

    @Test
    void emptyReportBatchIsInvalidBeforeAuthorization() {
        DdcAdminProperties properties = properties(List.of(credential(List.of("AI_REPORT"), List.of("app-a"))));
        interceptor = interceptor(properties, new InMemoryDdcNonceStore(100), List.of("orders-*"));
        var report = DdcAiResourceServiceGrpc.getReportUsageMethod();
        AiUsageReportBatch empty = AiUsageReportBatch.getDefaultInstance();

        RecordingServerCall<?, ?> call = invoke(report, empty,
                signed(report.getFullMethodName(), empty, NOW.toEpochMilli(), "ai-report-empty"),
                ignored -> facade.findConfig(new DdcManagementConfigQuery("must", "not", "run")));

        assertThat(call.status().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    private static AiUsageReport usage(String namespaceCode) {
        return AiUsageReport.newBuilder().setUsageId("01JAX0M7Q9K2R5T8V1W4X7Y0ZB").setNamespaceCode(namespaceCode)
                .setResourceKey("refund").setResourceId("7").setVersionId("9").setVersionNo(1L)
                .setOccurredAt("2026-08-09T08:00:00Z").build();
    }

    private DdcRpcServerInterceptor interceptor(
            DdcAdminProperties properties,
            DdcNonceStore nonceStore) {
        return interceptor(properties, nonceStore, List.of("orders-*"));
    }

    private DdcRpcServerInterceptor interceptor(
            DdcAdminProperties properties,
            DdcNonceStore nonceStore,
            List<String> namespacePatterns) {
        DdcTenantAdminProperties tenantProperties = new DdcTenantAdminProperties();
        tenantProperties.setBindings(List.of(DdcTenantBindingDTO.builder()
                .tenantId(101L)
                .identityTenantId("tenant-a")
                .enabled(true)
                .build()));
        tenantProperties.setCredentialBindings(properties.getRpc().getCredentials().stream()
                .map(value -> DdcCredentialTenantDTO.builder()
                        .credentialId(value.getCredentialId())
                        .tenantId(101L)
                        .namespacePatterns(namespacePatterns)
                        .build())
                .toList());
        DdcTenantRegistry tenantRegistry = new DdcTenantRegistry(tenantProperties, properties);
        tenantRegistry.initialize();
        return new DdcRpcServerInterceptor(
                properties,
                new DdcHmacCredentialRegistry(properties),
                tenantRegistry,
                new DdcTenantContextExecutor(),
                nonceStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DdcAdminProperties properties(
            List<DdcAdminProperties.Credential> credentials) {
        DdcAdminProperties properties = new DdcAdminProperties();
        properties.getRpc().setSignatureEnabled(true);
        properties.getRpc().setAllowedClockSkewSeconds(300);
        properties.getRpc().setCredentials(credentials);
        return properties;
    }

    private DdcAdminProperties.Credential credential(
            List<String> operations,
            List<String> apps) {
        DdcAdminProperties.Credential credential =
                new DdcAdminProperties.Credential();
        credential.setCredentialId("sdk-a");
        credential.setAccessKey(ACCESS_KEY);
        credential.setSecret(SECRET);
        credential.setClientType("SDK");
        credential.setBizCodePatterns(List.of("biz-a"));
        credential.setEnvPatterns(List.of("dev"));
        credential.setAppCodePatterns(apps);
        credential.setAllowedOperations(operations);
        return credential;
    }

    private DdcAdminProperties.Credential managementCredential(String operation) {
        DdcAdminProperties.Credential credential = credential(List.of(operation), List.of("app-a"));
        credential.setClientType("MANAGEMENT");
        return credential;
    }

    private FindConfigRequest request(
            String bizCode,
            String env,
            String appCode) {
        return FindConfigRequest.newBuilder()
                .setScope(scope(bizCode, env, appCode))
                .build();
    }

    private AiResourceQuery aiQuery(String namespaceCode) {
        return AiResourceQuery.newBuilder()
                .setBizCode("biz-a")
                .setNamespaceCode(namespaceCode)
                .setEnv("dev")
                .setAppCode("app-a")
                .setResourceKey("review-order")
                .setResourceId("810001")
                .build();
    }

    private DdcScope scope(String bizCode, String env, String appCode) {
        return DdcScope.newBuilder()
                .setBizCode(bizCode)
                .setEnv(env)
                .setAppCode(appCode)
                .build();
    }

    private static final class RecordingServerCall<ReqT, RespT>
            extends ServerCall<ReqT, RespT> {

        private final MethodDescriptor<ReqT, RespT> method;
        private Status status;
        private Metadata trailers;

        private RecordingServerCall(MethodDescriptor<ReqT, RespT> method) {
            this.method = method;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(RespT message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
            this.status = status;
            this.trailers = trailers;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
            return method;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }

        private Status status() {
            return status;
        }

        private Metadata trailers() {
            return trailers;
        }
    }
}
