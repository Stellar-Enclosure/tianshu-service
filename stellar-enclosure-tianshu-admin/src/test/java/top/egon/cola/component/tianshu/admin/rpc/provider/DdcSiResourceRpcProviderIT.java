package top.egon.cola.component.tianshu.admin.rpc.provider;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.RegisterManagedSiRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.SemanticSearchSiRequest;
import top.egon.cola.component.rpc.tianshu.mapping.DdcSiProtoMapperImpl;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcRpcScopeExtractor;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcServicePrincipal;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcSiResourceRpcProviderIT {

    private static final long TENANT_ID = 1001L;
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsSpoofedCrossTenantRequestsWithPermissionDeniedAndMapsDomainErrors() throws Exception {
        DdcSiResourceService siResourceService = mock(DdcSiResourceService.class);
        DdcSiResourceRpcProvider provider = new DdcSiResourceRpcProvider(siResourceService, new DdcSiProtoMapperImpl());
        AtomicReference<DdcServicePrincipal> activePrincipal = new AtomicReference<>(principal(TENANT_ID));

        ServerInterceptor principalInjector = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                Context ctx = activePrincipal.get().bind(Context.current());
                return Contexts.interceptCall(ctx, call, headers, next);
            }
        };

        server = NettyServerBuilder.forPort(0)
                .addService(provider)
                .intercept(principalInjector)
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        DdcSiResourceGrpcServiceGrpc.DdcSiResourceGrpcServiceBlockingStub stub =
                DdcSiResourceGrpcServiceGrpc.newBlockingStub(channel);

        // Cross-tenant spoofing must be denied before touching service
        assertThatThrownBy(() -> stub.listSiCatalog(ListSiCatalogRequest.newBuilder()
                .setTenantId(2002L)
                .setEnvCode("prod")
                .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.PERMISSION_DENIED));
        verifyNoInteractions(siResourceService);

        // Authorized list call
        DdcSiResourceDescriptor descriptor = DdcSiResourceDescriptor.builder()
                .tenantId(TENANT_ID).bizCode("ai").namespaceCode("core").envCode("prod")
                .resourceId(910001L).kind(DdcSiKindEnum.AGENT).resourceKey("order-agent")
                .displayName("Order Agent").status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.AUTO_REPORTED).activeVersionNo(2)
                .digestSha256("b".repeat(64)).publishedAt(NOW).updatedAt(NOW).build();
        when(siResourceService.listCatalog(any())).thenReturn(List.of(descriptor));

        ListSiCatalogResponse catalog = stub.listSiCatalog(ListSiCatalogRequest.newBuilder()
                .setTenantId(TENANT_ID)
                .setBizCode("ai")
                .setNamespaceCode("core")
                .setEnvCode("prod")
                .setKind("AGENT")
                .build());
        assertThat(catalog.getItemsList()).hasSize(1);
        assertThat(catalog.getItems(0).getResourceKey()).isEqualTo("order-agent");

        // Resolve NOT_FOUND -> gRPC NOT_FOUND
        when(siResourceService.resolveResource(eq(TENANT_ID), eq("ai"), eq("core"), eq("prod"),
                eq("tianji"), any(), eq(DdcSiKindEnum.AGENT), eq("missing"), any()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> stub.resolveSiResource(ResolveSiResourceRequest.newBuilder()
                .setTenantId(TENANT_ID).setBizCode("ai").setNamespaceCode("core")
                .setEnvCode("prod").setAppCode("tianji").setKind("AGENT").setResourceKey("missing")
                .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.NOT_FOUND));

        // Semantic search delegation
        when(siResourceService.hybridSearch(any())).thenReturn(List.of(
                DdcSiSearchHit.builder().resource(descriptor).hybridScore(0.95)
                        .degradedToFtsOnly(true).rank(1).build()));
        var searchResp = stub.semanticSearchSiResources(SemanticSearchSiRequest.newBuilder()
                .setTenantId(TENANT_ID).setEnvCode("prod").setQueryText("order").setTopK(5).build());
        assertThat(searchResp.getHitsList()).hasSize(1);
        assertThat(searchResp.getHits(0).getDegradedToFtsOnly()).isTrue();

        // Self-registration conflict -> ABORTED
        when(siResourceService.registerManaged(any(), eq(false)))
                .thenThrow(new CommonException(56409, "TIANSHU_SI_CONFLICT", "Owned by console"));
        assertThatThrownBy(() -> stub.registerManagedSiResource(RegisterManagedSiRequest.newBuilder()
                .setTenantId(TENANT_ID).setBizCode("ai").setNamespaceCode("core")
                .setEnvCode("prod").setServiceName("tianji-agent").setKind("AGENT")
                .setResourceKey("order-agent").setSpecJson("{}").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.ABORTED));
    }

    @Test
    void rpcScopeExtractorSupportsAllFourSiMethods() {
        DdcRpcScopeExtractor extractor = new DdcRpcScopeExtractor();
        var listScope = extractor.extract(
                "egon.tianshu.v1.DdcSiResourceGrpcService/ListSiCatalog",
                ListSiCatalogRequest.newBuilder()
                        .setTenantId(TENANT_ID).setBizCode("ai").setNamespaceCode("core")
                        .setEnvCode("prod").setAppCode("tianji").build());
        assertThat(listScope.clientType()).isEqualTo("SDK");
        assertThat(listScope.namespaceCode()).isEqualTo("core");

        var regScope = extractor.extract(
                "egon.tianshu.v1.DdcSiResourceGrpcService/RegisterManagedSiResource",
                RegisterManagedSiRequest.newBuilder()
                        .setTenantId(TENANT_ID).setBizCode("ai").setNamespaceCode("core")
                        .setEnvCode("prod").setServiceName("tianji-agent").build());
        assertThat(regScope.clientType()).isEqualTo("SDK");
        assertThat(regScope.appCode()).isEqualTo("tianji-agent");
    }

    private DdcServicePrincipal principal(long tenantId) {
        return new DdcServicePrincipal(
                "cred-si-test",
                "SDK",
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                Set.of("AI_READ", "AI_WRITE"),
                "tianji",
                "prod",
                "ai",
                tenantId,
                "tenant-" + tenantId,
                Set.of("*")
        );
    }
}
