package top.egon.cola.component.tianshu.admin.rpc.provider;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.TransientDataAccessException;
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
import top.egon.cola.component.tianshu.admin.security.rpc.DdcRpcProviderExceptionMapper;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcServicePrincipal;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;

import java.util.List;
import java.util.Optional;

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
            observer.onError(DdcRpcProviderExceptionMapper.status(
                    Status.INTERNAL, "TIANSHU_SI_INTERNAL_ERROR", "SI RPC operation failed", false));
        }
    }

    private <T> T invoke(String method, Invocation<T> invocation) {
        try {
            return invocation.get();
        } catch (CommonException failure) {
            throw mapSiFailure(failure);
        } catch (IllegalArgumentException failure) {
            throw DdcRpcProviderExceptionMapper.status(
                    Status.INVALID_ARGUMENT, "TIANSHU_SI_VALIDATION_ERROR", failure.getMessage(), false);
        } catch (TransientDataAccessException failure) {
            log.warn("SI RPC temporarily unavailable method={}", method, failure);
            throw DdcRpcProviderExceptionMapper.status(
                    Status.UNAVAILABLE, "TIANSHU_SERVICE_UNAVAILABLE", "Tianshu SI service temporarily unavailable", true);
        } catch (RuntimeException failure) {
            log.error("Tianshu SI RPC failed method={}", method, failure);
            throw DdcRpcProviderExceptionMapper.status(
                    Status.INTERNAL, "TIANSHU_SI_INTERNAL_ERROR", "Tianshu SI operation failed", false);
        }
    }

    private StatusRuntimeException mapSiFailure(CommonException failure) {
        Status status = switch (failure.getCode()) {
            case 56400 -> Status.INVALID_ARGUMENT;
            case 56403 -> Status.PERMISSION_DENIED;
            case 56404 -> Status.NOT_FOUND;
            case 56409 -> Status.ABORTED;
            case 56422 -> Status.FAILED_PRECONDITION;
            case 56429 -> Status.RESOURCE_EXHAUSTED;
            default -> Status.INTERNAL;
        };
        return DdcRpcProviderExceptionMapper.status(
                status, failure.getStatus(), failure.getMessage(), failure.isRetryable());
    }

    @FunctionalInterface
    private interface Invocation<T> {
        T get();
    }
}
