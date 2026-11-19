package top.egon.cola.component.tianshu.admin.rpc.provider;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.MetadataUtils;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.google.rpc.ErrorInfo;
import io.grpc.protobuf.StatusProto;
import top.egon.cola.component.rpc.context.invocation.RpcMetadataKeys;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpJwtVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcAdmissionIdentityResolver;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcBearerServerInterceptor;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcSecurityContext;
import top.egon.cola.component.tianshu.admin.config.DdcSiNativeRpcProperties;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcSiNativeRpcAuthorization;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcSiResourceRpcProviderIT {

    private static final long TENANT_ID = 1001L;
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private static final URI ISSUER = URI.create("https://idp.example/tenant");
    private static final URI RESOURCE = URI.create("https://tianshu.example/si");
    private static final Instant NATIVE_NOW = Instant.now();
    private static final String FULL_RESOLVE = DdcSiResourceGrpcServiceGrpc.getResolveSiResourceMethod().getFullMethodName();


    @Test
    void executionRequiredRejectsLegacyScopeOnlyBeforeResourceServiceInvocation() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        var provider = new DdcSiResourceRpcProvider(resources, new DdcSiProtoMapperImpl());
        var request = ResolveSiResourceRequest.newBuilder().setTenantId(TENANT_ID).setBizCode("ai")
                .setNamespaceCode("core").setEnvCode("prod").setOwnerAppCode("owner-app")
                .setCallerAppCode("caller-app").setKind("AGENT").setResourceKey("planner")
                .setExactVersionNo(1).setExecutionRequired(true).build();
        MDC.put("tenantId", Long.toString(TENANT_ID));
        MDC.put("userId", "unverified-request-state");
        Throwable rejected = org.assertj.core.api.Assertions.catchThrowable(() -> provider.resolveSiResource(request));
        assertThat(rejected).isInstanceOfSatisfying(StatusRuntimeException.class,
                failure -> assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
        verifyNoInteractions(resources);
    }

    @AfterEach
    void clearThreadState() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void nativeMetadataSearchAndRegistrationPreserveBusinessErrorMapping() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        var descriptor = DdcSiResourceDescriptor.builder().tenantId(TENANT_ID).bizCode("ai").namespaceCode("core")
                .envCode("prod").resourceId(910001L).kind(DdcSiKindEnum.AGENT).resourceKey("order-agent")
                .displayName("Order Agent").status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.AUTO_REPORTED).activeVersionNo(2)
                .digestSha256("b".repeat(64)).publishedAt(NOW).updatedAt(NOW).build();
        when(resources.listCatalog(any())).thenReturn(List.of(descriptor));
        when(resources.resolveResource(eq(TENANT_ID), eq("ai"), eq("core"), eq("prod"), eq("owner-app"),
                any(), eq(DdcSiKindEnum.AGENT), eq("missing"), any())).thenReturn(Optional.empty());
        when(resources.hybridSearch(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("tenantId")).isEqualTo("1001");
            return List.of(DdcSiSearchHit.builder().resource(descriptor).hybridScore(0.95).degradedToFtsOnly(true).rank(1).build());
        });
        when(resources.registerManaged(any(), eq(false)))
                .thenThrow(new CommonException(56409, "TIANSHU_SI_CONFLICT", "Owned by console"));
        try (var fixture = new NativeFixture(resources, servicePrincipal(Set.of("si:read", "si:write")), true, true)) {
            var stub = fixture.stub();
            rejected(Status.Code.PERMISSION_DENIED, () -> stub.listSiCatalog(ListSiCatalogRequest.newBuilder()
                    .setTenantId(2002).setBizCode("ai").setNamespaceCode("core").setEnvCode("prod").build()));
            var catalog = stub.listSiCatalog(ListSiCatalogRequest.newBuilder().setTenantId(TENANT_ID).setBizCode("ai")
                    .setNamespaceCode("core").setEnvCode("prod").setPageNo(1).setPageSize(20).build());
            assertThat(catalog.getItemsList()).hasSize(1);
            assertThat(catalog.getItems(0).getResourceKey()).isEqualTo("order-agent");
            rejected(Status.Code.NOT_FOUND, () -> stub.resolveSiResource(ResolveSiResourceRequest.newBuilder()
                    .setTenantId(TENANT_ID).setBizCode("ai").setNamespaceCode("core").setEnvCode("prod")
                    .setAppCode("owner-app").setKind("AGENT").setResourceKey("missing").build()));
            var search = stub.semanticSearchSiResources(SemanticSearchSiRequest.newBuilder().setTenantId(TENANT_ID)
                    .setBizCode("ai").setNamespaceCode("core").setEnvCode("prod").setQueryText("order").setTopK(5).build());
            assertThat(search.getHitsList()).hasSize(1);
            assertThat(search.getHits(0).getDegradedToFtsOnly()).isTrue();
            rejected(Status.Code.ABORTED, () -> stub.registerManagedSiResource(registration("tianji", "core")));
            verify(resources, times(1)).registerManaged(any(), eq(false));
        }
    }

    @Test
    void namespacePrivilegesDoNotExpandWhenMutableConfigurationChangesAfterStartup() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        when(resources.listCatalog(any())).thenReturn(List.of());
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            fixture.privileges.getBindings().getFirst().getMethods()
                    .put(DdcSiNativeRpcAuthorization.LIST, Set.of("core", "other"));
            rejected(Status.Code.PERMISSION_DENIED, () -> fixture.stub().listSiCatalog(ListSiCatalogRequest.newBuilder()
                    .setTenantId(TENANT_ID).setBizCode("ai").setNamespaceCode("other").setEnvCode("prod").build()));
            fixture.stub().listSiCatalog(ListSiCatalogRequest.newBuilder().setTenantId(TENANT_ID)
                    .setBizCode("ai").setNamespaceCode("core").setEnvCode("prod").setPageNo(1).setPageSize(10).build());
            verify(resources, times(1)).listCatalog(any());
        }
    }

    @Test
    void registrationNeedsExplicitWriteApprovalAndCannotSpoofTheOwnerApplication() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        var principal = servicePrincipal(Set.of("si:read", "si:write"));
        try (var fixture = new NativeFixture(resources, principal, true)) {
            rejected(Status.Code.PERMISSION_DENIED, () -> fixture.stub().registerManagedSiResource(registration("tianji", "core")));
        }
        try (var fixture = new NativeFixture(resources, principal, true, true)) {
            rejected(Status.Code.PERMISSION_DENIED, () -> fixture.stub().registerManagedSiResource(registration("owner-app", "core")));
            rejected(Status.Code.PERMISSION_DENIED, () -> fixture.stub().registerManagedSiResource(registration("tianji", "other")));
        }
        verifyNoInteractions(resources);
    }

    @Test
    void nativeCatalogListUsesMappedTenantAndKeepsOwnerFilterSeparateFromCaller() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        when(resources.listCatalog(any())).thenAnswer(invocation -> {
            top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery query = invocation.getArgument(0);
            assertThat(query.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(query.getAppCode()).isEqualTo("owner-app");
            assertThat(MDC.get("tenantId")).isEqualTo("1001");
            assertThat(MDC.get("userId")).isEqualTo("service:runtime-client");
            return List.of();
        });
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            var response = fixture.stub().listSiCatalog(ListSiCatalogRequest.newBuilder().setTenantId(TENANT_ID)
                    .setBizCode("ai").setNamespaceCode("core").setEnvCode("prod").setAppCode("owner-app")
                    .setPageNo(1).setPageSize(20).build());
            assertThat(response.getItemsList()).isEmpty();
            verify(resources, times(1)).listCatalog(any());
        }
    }

    @Test
    void nativeServiceProofUsesMappedLocalTenantAndVerifiedCallerWithoutOwnerConflation() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        when(resources.resolveExecutionResource(TENANT_ID, "ai", "core", "prod", "owner-app", "tianji",
                DdcSiKindEnum.AGENT, "planner", 3_000_000_001L)).thenAnswer(invocation -> {
            assertThat(MDC.get("tenantId")).isEqualTo("1001");
            assertThat(MDC.get("userId")).isEqualTo("service:runtime-client");
            return Optional.of(executionDescriptor(3_000_000_001L));
        });
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            var response = fixture.stub().resolveSiResource(executionRequest().setExactVersionNo(3_000_000_001L).build());
            assertThat(response.getResource().getResolvedVersionNo()).isEqualTo(3_000_000_001L);
            assertThat(response.getResource().getResolvedVersionId()).isEqualTo(920001L);
            assertThat(response.getResource().hasDependencyManifest()).isTrue();
            assertThat(response.getResource().getOwnerAppCode()).isEqualTo("owner-app");
            verify(resources, times(1)).resolveExecutionResource(TENANT_ID, "ai", "core", "prod", "owner-app", "tianji",
                    DdcSiKindEnum.AGENT, "planner", 3_000_000_001L);
            verify(fixture.verifier, times(1)).verifyAny("verified-fixture");
        }
    }

    @Test
    void nativeProofRejectsSpoofedCallerSourceOrTenantBeforeDomain() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            for (var request : List.of(executionRequest().setCallerAppCode("owner-app").build(),
                    executionRequest().setBizCode("another-biz").build(),
                    executionRequest().setEnvCode("dev").build(),
                    executionRequest().setTenantId(90001L).build())) {
                rejected(Status.Code.PERMISSION_DENIED, () -> fixture.stub().resolveSiResource(request));
            }
            verifyNoInteractions(resources);
        }
    }

    @Test
    void nativeProofWithoutMethodAuthorizationFrameCannotInvokeExecution() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        var principal = servicePrincipal();
        var identityBeans = new StaticListableBeanFactory(Map.of("idpRpcAdmissionIdentityResolver", new IdpRpcAdmissionIdentityResolver(),
                "idpStarterProperties", nativePolicies(false)));
        var gate = new DdcSiNativeRpcAuthorization(nativePrivileges(false), identityBeans.getBeanProvider(IdpStarterProperties.class),
                identityBeans.getBeanProvider(IdpRpcAdmissionIdentityResolver.class), tenantRegistry(), Clock.fixed(NATIVE_NOW, ZoneOffset.UTC));
        var authorityBeans = new StaticListableBeanFactory(Map.of("ddcSiNativeRpcAuthorization", gate));
        var provider = new DdcSiResourceRpcProvider(resources, new DdcSiProtoMapperImpl(),
                authorityBeans.getBeanProvider(DdcSiNativeRpcAuthorization.class), new DdcTenantContextExecutor());
        var prior = SecurityContextHolder.getContext();
        var bare = new IdpAuthenticationToken(principal, List.of(new SimpleGrantedAuthority("SCOPE_si:read")), ISSUER);
        bare.setDetails(proof());
        var springContext = SecurityContextHolder.createEmptyContext();
        springContext.setAuthentication(bare);
        try {
            SecurityContextHolder.setContext(springContext);
            IdpRpcSecurityContext.with(principal, "verified-fixture", proof(), null, principal.scopes()).run(() ->
                    rejected(Status.Code.UNAUTHENTICATED, () -> provider.resolveSiResource(executionRequest().build())));
        } finally { SecurityContextHolder.setContext(prior); }
        verifyNoInteractions(resources);
    }

    @Test
    void nativeVerifiedMethodWithoutConfiguredAuthorityFailsUnavailable() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        try (var fixture = new NativeFixture(resources, servicePrincipal(), false)) {
            rejected(Status.Code.UNAVAILABLE, () -> fixture.stub().resolveSiResource(executionRequest().build()));
            verifyNoInteractions(resources);
        }
    }

    @Test
    void executionSelectorsAreStrictAndNeverDowngradeToLegacyMetadata() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            for (var request : List.of(executionRequest().setAppCode("legacy").build(),
                    executionRequest().setPinnedVersionNo(1).build(), executionRequest().setExactVersionNo(0).build(),
                    executionRequest().setOwnerAppCode("").build(), executionRequest().setCallerAppCode(" tianji").build(),
                    executionRequest().setExecutionRequired(false).build())) {
                rejected(Status.Code.INVALID_ARGUMENT, () -> fixture.stub().resolveSiResource(request));
            }
            verifyNoInteractions(resources);
        }
    }

    @Test
    void namespaceOwnedResourceKeepsOwnerAbsentAndDoesNotDefaultToCaller() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        var descriptor = executionDescriptor(1L).setOwnerAppCode(null);
        when(resources.resolveExecutionResource(TENANT_ID, "ai", "core", "prod", null, "tianji",
                DdcSiKindEnum.AGENT, "planner", 1L)).thenReturn(Optional.of(descriptor));
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            var response = fixture.stub().resolveSiResource(executionRequest().clearOwnerAppCode().build());
            assertThat(response.getResource().hasOwnerAppCode()).isFalse();
            verify(resources).resolveExecutionResource(TENANT_ID, "ai", "core", "prod", null, "tianji",
                    DdcSiKindEnum.AGENT, "planner", 1L);
        }
    }

    @Test
    void fixedHistoryForbiddenAndUnavailableRemainDistinctWithNoMetadataFallback() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        when(resources.resolveExecutionResource(TENANT_ID, "ai", "core", "prod", "owner-app", "tianji",
                DdcSiKindEnum.AGENT, "planner", 1L))
                .thenThrow(new CommonException(56999, "SI_PINNED_HISTORY_UNAVAILABLE", "Fixed history is unavailable"))
                .thenThrow(new CommonException(56403, "TIANSHU_SI_FORBIDDEN", "Execution is forbidden"))
                .thenThrow(new TransientDataAccessResourceException("synthetic-store-detail"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(executionDescriptor(1L).setDependencyManifest(null)));
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            var failure = org.assertj.core.api.Assertions.catchThrowable(() -> fixture.stub().resolveSiResource(executionRequest().build()));
            assertThat(failure).isInstanceOfSatisfying(StatusRuntimeException.class, status -> {
                assertThat(status.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                var structured = StatusProto.fromThrowable(status);
                assertThat(structured).isNotNull();
                assertThat(structured.getDetailsCount()).isEqualTo(1);
                try {
                    var detail = structured.getDetails(0).unpack(ErrorInfo.class);
                    assertThat(detail.getReason()).isEqualTo("SI_PINNED_HISTORY_UNAVAILABLE");
                    assertThat(detail.getDomain()).isEqualTo("tianshu.si");
                    assertThat(detail.getMetadataMap()).isEqualTo(Map.of("retryable", "false"));
                } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
                    throw new AssertionError("Native SI status must carry standard ErrorInfo", invalid);
                }
            });
            rejected(Status.Code.PERMISSION_DENIED, () -> fixture.stub().resolveSiResource(executionRequest().build()));
            rejected(Status.Code.UNAVAILABLE, () -> fixture.stub().resolveSiResource(executionRequest().build()));
            rejected(Status.Code.FAILED_PRECONDITION, () -> fixture.stub().resolveSiResource(executionRequest().build()));
            rejected(Status.Code.FAILED_PRECONDITION, () -> fixture.stub().resolveSiResource(executionRequest().build()));
            verify(resources, times(5)).resolveExecutionResource(TENANT_ID, "ai", "core", "prod", "owner-app", "tianji",
                    DdcSiKindEnum.AGENT, "planner", 1L);
        }
    }

    @Test
    void nativeDomainAndInfrastructureErrorsKeepTheirCodesAndExposeOnlySafeStandardDetails() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        List<RuntimeException> failures = List.of(
                new CommonException(56400, "TIANSHU_SI_SPEC_INVALID", "synthetic-secret-domain"),
                new CommonException(56403, "TIANSHU_SI_FORBIDDEN", "synthetic-secret-domain"),
                new CommonException(56404, "TIANSHU_SI_NOT_FOUND", "synthetic-secret-domain"),
                new CommonException(56409, "TIANSHU_SI_CONFLICT", "synthetic-secret-domain"),
                new CommonException(56422, "SI_PINNED_HISTORY_UNAVAILABLE", "synthetic-secret-domain"),
                new CommonException(56429, "TIANSHU_SI_CAPACITY_EXCEEDED", "synthetic-secret-domain"),
                new CommonException(56999, "TIANSHU_SI_UNKNOWN_FAILURE", "synthetic-secret-domain"),
                new IllegalArgumentException("synthetic-secret-validation"),
                new TransientDataAccessResourceException("synthetic-secret-store"),
                new org.springframework.security.authentication.AuthenticationServiceException("synthetic-secret-auth"),
                new IllegalStateException("synthetic-secret-internal"));
        List<Status.Code> codes = List.of(Status.Code.INVALID_ARGUMENT, Status.Code.PERMISSION_DENIED,
                Status.Code.NOT_FOUND, Status.Code.ABORTED, Status.Code.FAILED_PRECONDITION,
                Status.Code.RESOURCE_EXHAUSTED, Status.Code.INTERNAL, Status.Code.INVALID_ARGUMENT,
                Status.Code.UNAVAILABLE, Status.Code.UNAVAILABLE, Status.Code.INTERNAL);
        List<String> reasons = List.of("TIANSHU_SI_SPEC_INVALID", "TIANSHU_SI_FORBIDDEN", "TIANSHU_SI_NOT_FOUND",
                "TIANSHU_SI_CONFLICT", "SI_PINNED_HISTORY_UNAVAILABLE", "TIANSHU_SI_CAPACITY_EXCEEDED",
                "TIANSHU_SI_UNKNOWN_FAILURE", "TIANSHU_SI_VALIDATION_ERROR", "TIANSHU_SERVICE_UNAVAILABLE",
                "TIANSHU_SI_AUTHORITY_UNAVAILABLE", "TIANSHU_SI_INTERNAL_ERROR");
        var next = new java.util.concurrent.atomic.AtomicInteger();
        when(resources.resolveExecutionResource(TENANT_ID, "ai", "core", "prod", "owner-app", "tianji",
                DdcSiKindEnum.AGENT, "planner", 1L)).thenAnswer(invocation -> { throw failures.get(next.getAndIncrement()); });
        try (var fixture = new NativeFixture(resources, servicePrincipal(), true)) {
            for (int index = 0; index < failures.size(); index++) {
                Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                        () -> fixture.stub().resolveSiResource(executionRequest().build()));
                assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
                StatusRuntimeException status = (StatusRuntimeException) thrown;
                assertThat(status.getStatus().getCode()).isEqualTo(codes.get(index));
                assertThat(status.getMessage()).doesNotContain("synthetic-secret");
                var rich = StatusProto.fromThrowable(status);
                assertThat(rich).isNotNull();
                assertThat(rich.getDetailsCount()).isEqualTo(1);
                ErrorInfo detail = rich.getDetails(0).unpack(ErrorInfo.class);
                assertThat(detail.getDomain()).isEqualTo("tianshu.si");
                assertThat(detail.getReason()).isEqualTo(reasons.get(index));
                assertThat(detail.getMetadataMap()).isEqualTo(Map.of("retryable", Boolean.toString(index == 8 || index == 9)));
            }
            verify(resources, times(failures.size())).resolveExecutionResource(TENANT_ID, "ai", "core", "prod", "owner-app", "tianji",
                    DdcSiKindEnum.AGENT, "planner", 1L);
        }
    }

    @Test void authenticatedCompleteExecutionIsExplicitBoundedAndNeverDowngradesToDescriptor() throws Exception {
        var resources=mock(DdcSiResourceService.class);var descriptor=executionDescriptor(1L);
        var bundle=new top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO(
                new top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO.MaterialVO(descriptor.getDependencyManifest().root(),"ai","core","owner-app","planner","{}"),
                descriptor.getDependencyManifest(),List.of());
        when(resources.resolveExecutionBundle(TENANT_ID,"ai","core","prod","owner-app","tianji",DdcSiKindEnum.AGENT,"planner",1L))
                .thenReturn(Optional.of(bundle)).thenThrow(new CommonException(56403,"TIANSHU_SI_SCOPE_DENIED","dependency revoked"));
        try(var fixture=new NativeFixture(resources,servicePrincipal(),true)) {
            var response=fixture.stub().resolveSiResource(executionRequest().setIncludeExecutionMaterial(true).build());
            assertThat(response.hasExecutionBundle()).isTrue();assertThat(response.hasResource()).isFalse();
            assertThat(new DdcSiProtoMapperImpl().fromProto(response.getExecutionBundle())).isEqualTo(bundle);
            rejected(Status.Code.PERMISSION_DENIED,()->fixture.stub().resolveSiResource(executionRequest().setIncludeExecutionMaterial(true).build()));
            rejected(Status.Code.INVALID_ARGUMENT,()->fixture.stub().resolveSiResource(executionRequest().setExecutionRequired(false).setIncludeExecutionMaterial(true).build()));
            rejected(Status.Code.INVALID_ARGUMENT,()->fixture.stub().resolveSiResource(executionRequest().setKind("PROMPT").setIncludeExecutionMaterial(true).build()));
            verify(resources,times(2)).resolveExecutionBundle(TENANT_ID,"ai","core","prod","owner-app","tianji",DdcSiKindEnum.AGENT,"planner",1L);
            org.mockito.Mockito.verify(resources,org.mockito.Mockito.never()).resolveExecutionResource(any(),any(),any(),any(),any(),any(),any(),any(),any());
        }
    }

    private static ResolveSiResourceRequest.Builder executionRequest() {
        return ResolveSiResourceRequest.newBuilder().setTenantId(TENANT_ID).setBizCode("ai")
                .setNamespaceCode("core").setEnvCode("prod").setOwnerAppCode("owner-app").setCallerAppCode("tianji")
                .setKind("AGENT").setResourceKey("planner").setExactVersionNo(1).setExecutionRequired(true);
    }

    private static ServiceIdentityPrincipal servicePrincipal() { return servicePrincipal(Set.of("si:read")); }

    private static ServiceIdentityPrincipal servicePrincipal(Set<String> scopes) {
        return new ServiceIdentityPrincipal("runtime-client", "90001", "runtime-client", "fixture-jti", RESOURCE,
                1L, scopes, "ai", "tianji", "prod", "fixture-credential",
                NATIVE_NOW.minusSeconds(30), NATIVE_NOW.plusSeconds(300), "runtime-app", ServiceTokenContext.TENANT);
    }

    private static TokenAuditProofBO proof() {
        return new TokenAuditProofBO(ISSUER, RESOURCE, "a".repeat(64), "b".repeat(64), "c".repeat(64));
    }

    private static DdcTenantRegistry tenantRegistry() {
        var tenants = mock(DdcTenantRegistry.class);
        when(tenants.requireByIdentityTenantId("90001")).thenReturn(new DdcTenantBindingDTO(TENANT_ID, "90001", true));
        return tenants;
    }

    private static DdcSiResourceDescriptor executionDescriptor(long version) throws Exception {
        var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("{}".getBytes(StandardCharsets.UTF_8)));
        var manifest = DdcSiDependencyManifestVO.create(new DdcSiDependencyManifestVO.VersionIdentityVO("1001", "910001",
                "920001", Long.toString(version), DdcSiKindEnum.AGENT, "prod", digest), null, List.of(), List.of());
        return DdcSiResourceDescriptor.builder().tenantId(TENANT_ID).bizCode("ai").namespaceCode("core").envCode("prod")
                .ownerAppCode("owner-app").resourceId(910001L).kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .displayName("Planner").status(DdcSiLifecycleStatusEnum.PUBLISHED).sourceType(DdcSiSourceTypeEnum.AUTO_REPORTED)
                .activeVersionNo(2).resolvedVersionId(920001L).resolvedVersionNo(version).digestSha256(digest).specJson("{}")
                .dependencyManifest(manifest).publishedAt(NOW).updatedAt(NOW).build();
    }

    private static void rejected(Status.Code code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(StatusRuntimeException.class,
                failure -> assertThat(failure.getStatus().getCode()).isEqualTo(code));
    }

    /** The real official OAuth interceptor creates the non-public method authorization frame over TCP. */
    private static final class NativeFixture implements AutoCloseable {
        private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
        private final IdpJwtVerifier verifier = mock(IdpJwtVerifier.class);
        private final Server server;
        private final ManagedChannel channel;
        private final DdcSiNativeRpcProperties privileges;

        private NativeFixture(DdcSiResourceService resources, ServiceIdentityPrincipal principal, boolean configuredAuthority) throws Exception {
            this(resources, principal, configuredAuthority, false);
        }

        private NativeFixture(DdcSiResourceService resources, ServiceIdentityPrincipal principal,
                boolean configuredAuthority, boolean write) throws Exception {
            doAnswer(invocation -> new AccessTokenVerification.Valid<>(principal, principal.scopes(), proof(), ISSUER))
                    .when(verifier).verifyAny("verified-fixture");
            var policies = nativePolicies(write);
            privileges = nativePrivileges(write);
            var interceptor = IdpRpcBearerServerInterceptor.createConfigured(mock(UserAccessTokenVerifier.class), verifier,
                    policies, null, validators.getValidator());
            interceptor.validateConfiguration();
            var identities = new StaticListableBeanFactory(configuredAuthority
                    ? Map.of("idpRpcAdmissionIdentityResolver", new IdpRpcAdmissionIdentityResolver(), "idpStarterProperties", policies)
                    : Map.of("idpStarterProperties", policies));
            var gate = new DdcSiNativeRpcAuthorization(privileges, identities.getBeanProvider(IdpStarterProperties.class),
                    identities.getBeanProvider(IdpRpcAdmissionIdentityResolver.class), tenantRegistry(), Clock.fixed(NATIVE_NOW, ZoneOffset.UTC));
            var authorityBeans = new StaticListableBeanFactory(Map.of("ddcSiNativeRpcAuthorization", gate));
            var provider = new DdcSiResourceRpcProvider(resources, new DdcSiProtoMapperImpl(),
                    authorityBeans.getBeanProvider(DdcSiNativeRpcAuthorization.class), new DdcTenantContextExecutor());
            server = NettyServerBuilder.forPort(0).addService(provider).intercept(interceptor).build().start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().disableRetry()
                    .disableServiceConfigLookUp().build();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            ConnectivityState state = channel.getState(true);
            while (state != ConnectivityState.READY && System.nanoTime() < deadline) {
                var changed = new CountDownLatch(1);
                channel.notifyWhenStateChanged(state, changed::countDown);
                changed.await(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                state = channel.getState(true);
            }
            if (state != ConnectivityState.READY) { close(); throw new IllegalStateException("Synthetic native channel did not become ready"); }
        }

        private DdcSiResourceGrpcServiceGrpc.DdcSiResourceGrpcServiceBlockingStub stub() {
            var headers = new Metadata();
            headers.put(RpcMetadataKeys.AUTHORIZATION, "Bearer verified-fixture");
            return DdcSiResourceGrpcServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers)).withDeadlineAfter(3, TimeUnit.SECONDS);
        }

        @Override public void close() throws InterruptedException {
            try { channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS); }
            finally {
                try { server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS); }
                finally { validators.close(); }
            }
        }
    }

    private static RegisterManagedSiRequest registration(String owner, String namespace) {
        return RegisterManagedSiRequest.newBuilder().setTenantId(TENANT_ID).setBizCode("ai").setNamespaceCode(namespace)
                .setEnvCode("prod").setServiceName(owner).setInstanceId("pod-a").setKind("AGENT").setResourceKey("order-agent")
                .setDisplayName("Order Agent").setSpecJson("{}").build();
    }

    private static IdpStarterProperties nativePolicies(boolean write) {
        var policies = new IdpStarterProperties();
        policies.getRpc().setEnabled(true);
        policies.getRpc().getProvider().setEnabled(true);
        var methods = new LinkedHashMap<String, IdpStarterProperties.RpcProviderMethodProperties>();
        for (String method : List.of(DdcSiNativeRpcAuthorization.LIST, DdcSiNativeRpcAuthorization.RESOLVE,
                DdcSiNativeRpcAuthorization.SEARCH)) methods.put(method, nativePolicy("si:read"));
        if (write) methods.put(DdcSiNativeRpcAuthorization.REGISTER, nativePolicy("si:write"));
        policies.getRpc().getProvider().setMethods(methods);
        return policies;
    }

    private static IdpStarterProperties.RpcProviderMethodProperties nativePolicy(String scope) {
        return new IdpStarterProperties.RpcProviderMethodProperties().setRequired(true)
                .setPrincipalKind(IdpStarterProperties.RpcPrincipalKindEnum.SERVICE).setScopeContext(ServiceTokenContext.TENANT)
                .setRequiredScopes(Set.of(scope)).setExpectedResourceUri(RESOURCE);
    }

    private static DdcSiNativeRpcProperties nativePrivileges(boolean write) {
        var methods = new LinkedHashMap<String, Set<String>>();
        for (String method : List.of(DdcSiNativeRpcAuthorization.LIST, DdcSiNativeRpcAuthorization.RESOLVE,
                DdcSiNativeRpcAuthorization.SEARCH)) methods.put(method, Set.of("core"));
        if (write) methods.put(DdcSiNativeRpcAuthorization.REGISTER, Set.of("core"));
        return new DdcSiNativeRpcProperties().setBindings(new ArrayList<>(List.of(new DdcSiNativeRpcProperties.BindingDTO()
                .setIssuer(ISSUER).setResourceUri(RESOURCE).setIdentityTenantId("90001").setClientId("runtime-client")
                .setSourceBizCode("ai").setSourceAppCode("tianji").setSourceEnvironment("prod").setMethods(methods))));
    }
}
