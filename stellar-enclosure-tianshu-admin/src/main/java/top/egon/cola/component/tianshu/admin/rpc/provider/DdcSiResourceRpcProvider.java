package top.egon.cola.component.tianshu.admin.rpc.provider;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.rpc.annotation.EgonRpcProvider;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.RegisterManagedSiRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.RegisterManagedSiResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.SemanticSearchSiRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.SemanticSearchSiResponse;
import top.egon.cola.component.rpc.tianshu.mapping.DdcSiProtoMapper;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcSiRpcErrorMapper;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcServicePrincipal;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcAdmissionIdentityResolver;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcSecurityContext;

import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** gRPC provider serving Tianshu SI catalog, discovery, hybrid search, and idempotent self-registration. */
@Component("ddcSiResourceRpcProvider")
@EgonRpcProvider
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiResourceRpcProvider
        extends DdcSiResourceGrpcServiceGrpc.DdcSiResourceGrpcServiceImplBase
        implements DdcSiResourceRpc {

    @Qualifier("ddcSiResourceService")
    private final DdcSiResourceService resourceService;

    @Qualifier("ddcSiProtoMapperImpl")
    private final DdcSiProtoMapper protoMapper;

    @Qualifier("idpRpcAdmissionIdentityResolver")
    private final ObjectProvider<IdpRpcAdmissionIdentityResolver> methodIdentities;

    @Qualifier("ddcTenantRegistry")
    private final DdcTenantRegistry tenants;

    @Qualifier("ddcTenantContextExecutor")
    private final DdcTenantContextExecutor tenantContexts;

    @Qualifier("ddcPublishClock")
    private final Clock clock;

    /** Metadata-only source compatibility; no verified execution authority is fabricated. */
    public DdcSiResourceRpcProvider(DdcSiResourceService resourceService, DdcSiProtoMapper protoMapper) {
        this(resourceService, protoMapper, new StaticListableBeanFactory().getBeanProvider(IdpRpcAdmissionIdentityResolver.class),
                null, new DdcTenantContextExecutor(), Clock.systemUTC());
    }

    @Override
    public void listSiCatalog(
            ListSiCatalogRequest request,
            StreamObserver<ListSiCatalogResponse> responseObserver) {
        respondUnary("ListSiCatalog", responseObserver, () -> listSiCatalog(request));
    }

    @Override
    public ListSiCatalogResponse listSiCatalog(ListSiCatalogRequest request) {
        return invoke("ListSiCatalog", () -> {
            assertAuthorizedTenant(request.getTenantId());
            List<DdcSiResourceDescriptor> descriptors = resourceService.listCatalog(protoMapper.fromProto(request));
            ListSiCatalogResponse.Builder builder = ListSiCatalogResponse.newBuilder()
                    .setTotal(descriptors.size());
            descriptors.forEach(item -> builder.addItems(protoMapper.toProto(item)));
            return builder.build();
        });
    }

    @Override
    public void resolveSiResource(
            ResolveSiResourceRequest request,
            StreamObserver<ResolveSiResourceResponse> responseObserver) {
        respondUnary("ResolveSiResource", responseObserver, () -> resolveSiResource(request));
    }

    @Override
    public ResolveSiResourceResponse resolveSiResource(ResolveSiResourceRequest request) {
        return invoke("ResolveSiResource", () -> {
            if (request == null) { throw invalidExecutionRequest(); }
            if (request.getExecutionRequired()) { return resolveExecution(request); }
            if (request.hasOwnerAppCode() || !request.getCallerAppCode().isEmpty() || request.hasExactVersionNo()) {
                throw invalidExecutionRequest();
            }
            long tenantId = assertAuthorizedTenant(request.getTenantId());
            DdcSiKindEnum kind = DdcSiKindEnum.valueOf(request.getKind().trim().toUpperCase());
            Integer pinnedVersion = request.getPinnedVersionNo() > 0 ? request.getPinnedVersionNo() : null;
            Optional<DdcSiResourceDescriptor> resolved = resourceService.resolveResource(
                    tenantId,
                    request.getBizCode(),
                    request.getNamespaceCode(),
                    request.getEnvCode(),
                    request.getAppCode(),
                    null,
                    kind,
                    request.getResourceKey(),
                    pinnedVersion
            );
            DdcSiResourceDescriptor descriptor = resolved.orElseThrow(() ->
                    new CommonException(56404, "TIANSHU_SI_NOT_FOUND", "SI resource not found: " + request.getResourceKey()));
            return ResolveSiResourceResponse.newBuilder()
                    .setResource(protoMapper.toProto(descriptor))
                    .build();
        });
    }

    private ResolveSiResourceResponse resolveExecution(ResolveSiResourceRequest request) {
        if (!request.getAppCode().isEmpty() || request.getPinnedVersionNo() != 0
                || request.hasExactVersionNo() && request.getExactVersionNo() <= 0
                || request.hasOwnerAppCode() && request.getOwnerAppCode().isBlank()) {
            throw invalidExecutionRequest();
        }
        requireCoordinate(request.getBizCode()); requireCoordinate(request.getNamespaceCode());
        requireCoordinate(request.getEnvCode()); requireCoordinate(request.getCallerAppCode());
        requireCoordinate(request.getResourceKey());
        if (request.hasOwnerAppCode()) { requireCoordinate(request.getOwnerAppCode()); }
        DdcSiKindEnum kind = DdcSiKindEnum.fromCode(request.getKind());
        var caller = requireExecutionCaller(request);
        return tenantContexts.call(caller.tenant(), () -> {
            var descriptor = resourceService.resolveExecutionResource(caller.tenant().tenantId(), request.getBizCode(),
                    request.getNamespaceCode(), request.getEnvCode(), request.hasOwnerAppCode() ? request.getOwnerAppCode() : null,
                    caller.callerAppCode(), kind, request.getResourceKey(), request.hasExactVersionNo() ? request.getExactVersionNo() : null)
                    .orElseThrow(DdcSiResourceRpcProvider::historyUnavailable);
            if (descriptor.getDependencyManifest() == null || !descriptor.isExecutionSnapshotShapeValid()) {
                throw historyUnavailable();
            }
            return ResolveSiResourceResponse.newBuilder().setResource(protoMapper.toProto(descriptor)).build();
        });
    }

    private ExecutionCallerVO requireExecutionCaller(ResolveSiResourceRequest request) {
        var principal = IdpRpcSecurityContext.currentPrincipalOrNull();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (principal == null || !(authentication instanceof IdpAuthenticationToken accepted)
                || !accepted.isAuthenticated() || accepted.getPrincipal() != principal) {
            throw unauthenticated();
        }
        if (!(principal instanceof ServiceIdentityPrincipal service) || service.scopeContext() != ServiceTokenContext.TENANT) {
            throw forbidden();
        }
        var proof = IdpRpcSecurityContext.currentProofOrNull();
        if (proof == null || !proof.equals(accepted.getDetails()) || !service.expiresAt().isAfter(clock.instant())) {
            throw unauthenticated();
        }
        try {
            if (!proof.issuer().equals(accepted.verifiedIssuer()) || !proof.resource().equals(service.resourceUri())) {
                throw forbidden();
            }
        } catch (IllegalStateException missingIssuer) { throw unauthenticated(); }
        if (!request.getCallerAppCode().equals(service.sourceAppCode())
                || !request.getBizCode().equals(service.sourceBizCode())
                || !request.getEnvCode().equals(service.sourceEnvironment())) { throw forbidden(); }
        final IdpRpcAdmissionIdentityResolver methodIdentity;
        try { methodIdentity = methodIdentities.getIfAvailable(); }
        catch (RuntimeException unavailable) { throw authorityUnavailable(); }
        if (methodIdentity == null || tenants == null) { throw authorityUnavailable(); }
        // Read-only method proof; the native server's admission interceptor remains the sole quota owner.
        var method = methodIdentity.resolve(DdcSiResourceGrpcServiceGrpc.getResolveSiResourceMethod().getFullMethodName(), request);
        if (!Long.toString(method.tenantId()).equals(service.tenantId()) || !method.clientId().equals(service.clientId())
                || !method.resource().equals(service.resourceUri().toASCIIString())) { throw forbidden(); }
        final top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO tenant;
        try { tenant = tenants.requireByIdentityTenantId(service.tenantId()); }
        catch (IllegalArgumentException unregistered) { throw forbidden(); }
        if (request.getTenantId() <= 0 || !tenant.getTenantId().equals(request.getTenantId())) { throw forbidden(); }
        return new ExecutionCallerVO(new DdcTenantContextDTO(tenant.getTenantId(), tenant.getIdentityTenantId(),
                "service:" + service.clientId()), service.sourceAppCode());
    }

    private static void requireCoordinate(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || !value.equals(value.strip())
                || value.codePoints().anyMatch(Character::isISOControl)) { throw invalidExecutionRequest(); }
    }

    private static CommonException invalidExecutionRequest() {
        return new CommonException(56400, "TIANSHU_SI_VALIDATION_ERROR", "SI execution request is invalid");
    }
    private static CommonException historyUnavailable() {
        return new CommonException(56422, "SI_PINNED_HISTORY_UNAVAILABLE", "Fixed SI execution history is unavailable");
    }
    private static StatusRuntimeException unauthenticated() {
        return DdcSiRpcErrorMapper.status(Status.UNAUTHENTICATED, "TIANSHU_SI_AUTHENTICATION_REQUIRED", false);
    }
    private static StatusRuntimeException forbidden() {
        return DdcSiRpcErrorMapper.status(Status.PERMISSION_DENIED, "TIANSHU_SI_FORBIDDEN", false);
    }
    private static StatusRuntimeException authorityUnavailable() {
        return DdcSiRpcErrorMapper.status(Status.UNAVAILABLE, "TIANSHU_SI_AUTHORITY_UNAVAILABLE", true);
    }
    private record ExecutionCallerVO(DdcTenantContextDTO tenant, String callerAppCode) { }

    @Override
    public void semanticSearchSiResources(
            SemanticSearchSiRequest request,
            StreamObserver<SemanticSearchSiResponse> responseObserver) {
        respondUnary("SemanticSearchSiResources", responseObserver, () -> semanticSearchSiResources(request));
    }

    @Override
    public SemanticSearchSiResponse semanticSearchSiResources(SemanticSearchSiRequest request) {
        return invoke("SemanticSearchSiResources", () -> {
            assertAuthorizedTenant(request.getTenantId());
            DdcSiSemanticSearchQuery query = protoMapper.fromProto(request);
            List<DdcSiSearchHit> hits = resourceService.hybridSearch(query);
            SemanticSearchSiResponse.Builder builder = SemanticSearchSiResponse.newBuilder();
            hits.forEach(hit -> builder.addHits(protoMapper.toProto(hit)));
            return builder.build();
        });
    }

    @Override
    public void registerManagedSiResource(
            RegisterManagedSiRequest request,
            StreamObserver<RegisterManagedSiResponse> responseObserver) {
        respondUnary("RegisterManagedSiResource", responseObserver, () -> registerManagedSiResource(request));
    }

    @Override
    public RegisterManagedSiResponse registerManagedSiResource(RegisterManagedSiRequest request) {
        return invoke("RegisterManagedSiResource", () -> {
            assertAuthorizedTenant(request.getTenantId());
            DdcSiSelfRegistrationRequest domainReq = protoMapper.fromProto(request);
            DdcSiResourceDescriptor descriptor = resourceService.registerManaged(domainReq, false);
            return RegisterManagedSiResponse.newBuilder()
                    .setResource(protoMapper.toProto(descriptor))
                    .build();
        });
    }

    private long assertAuthorizedTenant(long declaredTenantId) {
        DdcServicePrincipal principal;
        try {
            principal = DdcServicePrincipal.current();
        } catch (IllegalStateException missingPrincipal) {
            throw new CommonException(56403, "TIANSHU_SI_FORBIDDEN", "Authenticated service principal is required");
        }
        if (declaredTenantId <= 0 || !principal.tenantId().equals(declaredTenantId)) {
            log.warn("Rejected cross-tenant SI RPC principalTenant={} declaredTenant={}",
                    principal.tenantId(), declaredTenantId);
            throw new CommonException(56403, "TIANSHU_SI_FORBIDDEN", "Cross-tenant SI RPC access is denied");
        }
        return principal.tenantId();
    }

    private <T> void respondUnary(String method, StreamObserver<T> observer, Invocation<T> invocation) {
        try {
            T result = invocation.get();
            observer.onNext(result);
            observer.onCompleted();
        } catch (StatusRuntimeException sre) {
            observer.onError(sre);
        } catch (RuntimeException failure) {
            log.error("Unhandled SI RPC failure method={}", method, failure);
            observer.onError(DdcSiRpcErrorMapper.status(
                    Status.INTERNAL, "TIANSHU_SI_INTERNAL_ERROR", false));
        }
    }

    private <T> T invoke(String method, Invocation<T> invocation) {
        try {
            return invocation.get();
        } catch (StatusRuntimeException failure) {
            throw failure;
        } catch (CommonException failure) {
            throw mapSiFailure(failure);
        } catch (IllegalArgumentException failure) {
            throw DdcSiRpcErrorMapper.status(
                    Status.INVALID_ARGUMENT, "TIANSHU_SI_VALIDATION_ERROR", false);
        } catch (TransientDataAccessException failure) {
            log.warn("SI RPC temporarily unavailable method={}", method, failure);
            throw DdcSiRpcErrorMapper.status(
                    Status.UNAVAILABLE, "TIANSHU_SERVICE_UNAVAILABLE", true);
        } catch (AuthenticationServiceException failure) {
            throw authorityUnavailable();
        } catch (RuntimeException failure) {
            log.error("Tianshu SI RPC failed method={}", method, failure);
            throw DdcSiRpcErrorMapper.status(
                    Status.INTERNAL, "TIANSHU_SI_INTERNAL_ERROR", false);
        }
    }

    private StatusRuntimeException mapSiFailure(CommonException failure) {
        Status status = "SI_PINNED_HISTORY_UNAVAILABLE".equals(failure.getStatus()) ? Status.FAILED_PRECONDITION : switch (failure.getCode()) {
            case 56400 -> Status.INVALID_ARGUMENT;
            case 56403 -> Status.PERMISSION_DENIED;
            case 56404 -> Status.NOT_FOUND;
            case 56409 -> Status.ABORTED;
            case 56422 -> Status.FAILED_PRECONDITION;
            case 56429 -> Status.RESOURCE_EXHAUSTED;
            default -> Status.INTERNAL;
        };
        return DdcSiRpcErrorMapper.status(
                status, failure.getStatus(), failure.isRetryable());
    }

    @FunctionalInterface
    private interface Invocation<T> {
        T get();
    }
}
