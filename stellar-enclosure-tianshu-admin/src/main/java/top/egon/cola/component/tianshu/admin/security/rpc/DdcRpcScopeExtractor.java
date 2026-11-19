package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.protobuf.Message;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiResourceQuery;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiSkillDraftUpload;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiSkillPublishRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiUsageReportBatch;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DeleteConfigRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.FindConfigRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.GetConfigClientsRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.GetInstancesRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.GetScopeBindingsRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.GetServiceKeysRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.PublishConfigRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.RegisterManagedSiRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.SemanticSearchSiRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.UpsertConfigRequest;

/**
 * 从已发布的 Tianshu unary protobuf 请求中提取鉴权作用域。
 * / Extracts authorization scope from published Tianshu unary protobuf requests.
 */
public final class DdcRpcScopeExtractor {

    private static final String MANAGEMENT =
            "egon.tianshu.v1.DdcManagementService/";
    private static final String AI_RESOURCE =
            "egon.tianshu.v1.DdcAiResourceService/";
    private static final String SI_RESOURCE =
            "egon.tianshu.v1.DdcSiResourceGrpcService/";

    /** 严格提取方法对应的客户端类型和作用域。 / Strictly extracts client type and scope. */
    public Scope extract(String fullMethodName, Message request) {
        if (fullMethodName == null || request == null) {
            throw new IllegalArgumentException(
                    "fullMethodName and request are required");
        }
        return switch (fullMethodName) {
            case MANAGEMENT + "FindConfig" -> managementScope(
                    type(request, FindConfigRequest.class).getScope());
            case MANAGEMENT + "UpsertConfig" -> managementScope(
                    type(request, UpsertConfigRequest.class).getScope());
            case MANAGEMENT + "DeleteConfig" -> managementScope(
                    type(request, DeleteConfigRequest.class).getScope());
            case MANAGEMENT + "PublishConfig" -> managementScope(
                    type(request, PublishConfigRequest.class).getScope());
            case MANAGEMENT + "GetPublishTask",
                 MANAGEMENT + "RetryPublishTask" -> Scope.unscoped(
                    "MANAGEMENT");
            case MANAGEMENT + "GetBiz",
                 MANAGEMENT + "ListBizs",
                 MANAGEMENT + "GetApp",
                 MANAGEMENT + "ListApps" -> Scope.unscoped("MANAGEMENT");
            case MANAGEMENT + "GetConfigClients" -> managementScope(
                    type(request, GetConfigClientsRequest.class).getScope());
            case MANAGEMENT + "GetScopeBindings" -> scopeBindingQuery(
                    type(request, GetScopeBindingsRequest.class));
            case MANAGEMENT + "GetServiceKeys" -> managementQuery(
                    type(request, GetServiceKeysRequest.class));
            case MANAGEMENT + "GetInstances" -> managementQuery(
                    type(request, GetInstancesRequest.class));
            case AI_RESOURCE + "GetPrompt",
                 AI_RESOURCE + "GetSkill",
                 AI_RESOURCE + "GetVersionHead" -> aiReadScope(
                    type(request, AiResourceQuery.class));
            case AI_RESOURCE + "ReportUsage" -> aiReportScope(
                    type(request, AiUsageReportBatch.class));
            case AI_RESOURCE + "ReplaceSkillDraft" -> aiResourceScope(
                    type(request, AiSkillDraftUpload.class).getNamespaceCode(),
                    type(request, AiSkillDraftUpload.class).getResourceKey(),
                    type(request, AiSkillDraftUpload.class).getResourceId());
            case AI_RESOURCE + "PublishSkillDraft" -> aiResourceScope(
                    type(request, AiSkillPublishRequest.class).getNamespaceCode(),
                    type(request, AiSkillPublishRequest.class).getResourceKey(),
                    type(request, AiSkillPublishRequest.class).getResourceId());
            case SI_RESOURCE + "ListSiCatalog" -> siScope(
                    type(request, ListSiCatalogRequest.class).getAppCode(),
                    type(request, ListSiCatalogRequest.class).getEnvCode(),
                    type(request, ListSiCatalogRequest.class).getBizCode(),
                    type(request, ListSiCatalogRequest.class).getNamespaceCode());
            case SI_RESOURCE + "ResolveSiResource" -> siScope(
                    type(request, ResolveSiResourceRequest.class).getAppCode(),
                    type(request, ResolveSiResourceRequest.class).getEnvCode(),
                    type(request, ResolveSiResourceRequest.class).getBizCode(),
                    type(request, ResolveSiResourceRequest.class).getNamespaceCode());
            case SI_RESOURCE + "SemanticSearchSiResources" -> siScope(
                    null,
                    type(request, SemanticSearchSiRequest.class).getEnvCode(),
                    type(request, SemanticSearchSiRequest.class).getBizCode(),
                    type(request, SemanticSearchSiRequest.class).getNamespaceCode());
            case SI_RESOURCE + "RegisterManagedSiResource" -> siScope(
                    type(request, RegisterManagedSiRequest.class).getServiceName(),
                    type(request, RegisterManagedSiRequest.class).getEnvCode(),
                    type(request, RegisterManagedSiRequest.class).getBizCode(),
                    type(request, RegisterManagedSiRequest.class).getNamespaceCode());
            default -> throw new IllegalArgumentException(
                    "Unknown Tianshu RPC method");
        };
    }

    private Scope managementScope(
            top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcScope scope) {
        return required("MANAGEMENT", scope);
    }

    /**
     * 上报批的作用域：业务作用域取自凭据，批内全部 namespace 都需授权。
     * / Scope of a report batch: business scope comes from the credential and every namespace in the batch is checked.
     */
    private Scope aiReportScope(AiUsageReportBatch batch) {
        if (batch.getReportsCount() == 0) throw new IllegalArgumentException("AI usage report batch is empty");
        java.util.LinkedHashSet<String> namespaces = new java.util.LinkedHashSet<>();
        for (var report : batch.getReportsList()) {
            if (!text(report.getNamespaceCode())) {
                throw new IllegalArgumentException("AI usage report namespace is required");
            }
            namespaces.add(report.getNamespaceCode());
        }
        return new Scope("SDK", null, null, null, namespaces.iterator().next(), java.util.Set.copyOf(namespaces));
    }

    private Scope aiResourceScope(String namespaceCode, String resourceKey, String resourceId) {
        if (!text(namespaceCode) || !text(resourceKey) || resourceId == null
                || !resourceId.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("AI namespace, resource key and positive resourceId are required");
        }
        return new Scope("SDK", null, null, null, namespaceCode);
    }

    private Scope aiReadScope(AiResourceQuery query) {
        if (!text(query.getBizCode()) || !text(query.getNamespaceCode())
                || !text(query.getEnv()) || !text(query.getAppCode())
                || !text(query.getResourceKey())) {
            throw new IllegalArgumentException("AI read scope and resource key are required");
        }
        if (!query.hasResourceId()) throw new IllegalArgumentException("AI resourceId is required");
        if (query.hasVersionNo() && query.getVersionNo() <= 0) {
            throw new IllegalArgumentException("AI versionNo must be positive");
        }
        String resourceId = query.getResourceId();
        if (!resourceId.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("AI resourceId must be a positive decimal Long");
        }
        try {
            Long.parseLong(resourceId);
        } catch (NumberFormatException invalidResourceId) {
            throw new IllegalArgumentException("AI resourceId exceeds Long range", invalidResourceId);
        }
        return new Scope("SDK", query.getAppCode(), query.getEnv(), query.getBizCode(),
                query.getNamespaceCode());
    }

    private Scope required(
            String clientType,
            top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcScope scope) {
        if (scope == null
                || !text(scope.getBizCode())
                || !text(scope.getEnv())
                || !text(scope.getAppCode())) {
            throw new IllegalArgumentException(
                    "bizCode, env and appCode scope are required");
        }
        return new Scope(
                clientType,
                scope.getAppCode(),
                scope.getEnv(),
                scope.getBizCode(),
                null
        );
    }

    private Scope scopeBindingQuery(GetScopeBindingsRequest request) {
        if (!request.hasQuery()) {
            throw new IllegalArgumentException("scope query is required");
        }
        var query = request.getQuery();
        return new Scope(
                "MANAGEMENT",
                query.hasAppCode() ? query.getAppCode() : null,
                query.hasEnv() ? query.getEnv() : null,
                query.hasBizCode() ? query.getBizCode() : null
        );
    }

    private Scope managementQuery(GetServiceKeysRequest request) {
        if (!request.hasQuery()) {
            throw new IllegalArgumentException("service query is required");
        }
        var query = request.getQuery();
        return new Scope(
                "MANAGEMENT",
                query.hasAppCode() ? query.getAppCode() : null,
                query.hasEnv() ? query.getEnv() : null,
                query.hasBizCode() ? query.getBizCode() : null
        );
    }

    private Scope managementQuery(GetInstancesRequest request) {
        if (!request.hasQuery()) {
            throw new IllegalArgumentException("service query is required");
        }
        var query = request.getQuery();
        return new Scope(
                "MANAGEMENT",
                query.hasAppCode() ? query.getAppCode() : null,
                query.hasEnv() ? query.getEnv() : null,
                query.hasBizCode() ? query.getBizCode() : null
        );
    }

    private <T extends Message> T type(Message value, Class<T> expected) {
        if (!expected.isInstance(value)) {
            throw new IllegalArgumentException("Unexpected Tianshu RPC request type");
        }
        return expected.cast(value);
    }

    private boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private Scope siScope(String appCode, String envCode, String bizCode, String namespaceCode) {
        return new Scope(
                "SDK",
                text(appCode) ? appCode : null,
                text(envCode) ? envCode : null,
                text(bizCode) ? bizCode : null,
                text(namespaceCode) ? namespaceCode : null
        );
    }

    /** 鉴权使用的传输无关作用域。 / Transport-neutral scope used for authorization. */
    public record Scope(
            String clientType,
            String appCode,
            String env,
            String bizCode,
            String namespaceCode,
            java.util.Set<String> namespaceCodes) {

        public Scope {
            namespaceCodes = namespaceCodes != null ? java.util.Set.copyOf(namespaceCodes)
                    : namespaceCode == null ? java.util.Set.of() : java.util.Set.of(namespaceCode);
        }

        public Scope(String clientType, String appCode, String env, String bizCode, String namespaceCode) {
            this(clientType, appCode, env, bizCode, namespaceCode, null);
        }

        public Scope(String clientType, String appCode, String env, String bizCode) {
            this(clientType, appCode, env, bizCode, null, null);
        }

        private static Scope unscoped(String clientType) {
            return new Scope(clientType, null, null, null, null, null);
        }
    }
}
