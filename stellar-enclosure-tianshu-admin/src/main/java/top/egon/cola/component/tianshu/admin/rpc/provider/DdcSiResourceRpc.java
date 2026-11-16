package top.egon.cola.component.tianshu.admin.rpc.provider;

import top.egon.cola.component.rpc.annotation.EgonRpcMethod;
import top.egon.cola.component.rpc.annotation.EgonRpcService;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.RegisterManagedSiRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.RegisterManagedSiResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceResponse;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.SemanticSearchSiRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.SemanticSearchSiResponse;

/** Egon RPC service contract binding for Tianshu SI catalog, discovery, semantic search, and registration. */
@EgonRpcService(
        grpcClass = DdcSiResourceGrpcServiceGrpc.class,
        group = "tianshu",
        version = "1.0.0"
)
public interface DdcSiResourceRpc {

    @EgonRpcMethod(name = "ListSiCatalog", idempotent = true)
    ListSiCatalogResponse listSiCatalog(ListSiCatalogRequest request);

    @EgonRpcMethod(name = "ResolveSiResource", idempotent = true)
    ResolveSiResourceResponse resolveSiResource(ResolveSiResourceRequest request);

    @EgonRpcMethod(name = "SemanticSearchSiResources", idempotent = true)
    SemanticSearchSiResponse semanticSearchSiResources(SemanticSearchSiRequest request);

    @EgonRpcMethod(name = "RegisterManagedSiResource", idempotent = false)
    RegisterManagedSiResponse registerManagedSiResource(RegisterManagedSiRequest request);
}
