package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.protobuf.Message;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.provider.server.RpcProviderExceptionMapper;
import top.egon.cola.component.tianshu.admin.config.DdcSiNativeRpcProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcAdmissionIdentityResolver;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcSecurityContext;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 在 SI 业务调用前核验原生方法身份和冻结的部署权限。
 * <p>Native method identity and immutable deployment privileges precede every SI domain call.</p>
 */
@Component("ddcSiNativeRpcAuthorization")
@EnableConfigurationProperties(DdcSiNativeRpcProperties.class)
public class DdcSiNativeRpcAuthorization {
    public static final String LIST = DdcSiResourceGrpcServiceGrpc.getListSiCatalogMethod().getFullMethodName();
    public static final String RESOLVE = DdcSiResourceGrpcServiceGrpc.getResolveSiResourceMethod().getFullMethodName();
    public static final String SEARCH = DdcSiResourceGrpcServiceGrpc.getSemanticSearchSiResourcesMethod().getFullMethodName();
    public static final String REGISTER = DdcSiResourceGrpcServiceGrpc.getRegisterManagedSiResourceMethod().getFullMethodName();
    private static final Set<String> METHODS = Set.of(LIST, RESOLVE, SEARCH, REGISTER);

    private final ObjectProvider<IdpRpcAdmissionIdentityResolver> identities;
    private final DdcTenantRegistry tenants;
    private final Clock clock;
    private final Map<CallerKeyVO, ApprovedCallerVO> callers;

    /** 冻结配置权限，属性 Bean 的后续变化不能扩权。 <p>Freezes privileges so later property mutation cannot expand authority.</p> */
    @Autowired
    public DdcSiNativeRpcAuthorization(
            DdcSiNativeRpcProperties properties,
            @Qualifier("idpStarterProperties") ObjectProvider<IdpStarterProperties> identityProperties,
            @Qualifier("idpRpcAdmissionIdentityResolver") ObjectProvider<IdpRpcAdmissionIdentityResolver> identities,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants,
            @Qualifier("ddcPublishClock") Clock clock) {
        this.identities = Objects.requireNonNull(identities);
        this.tenants = Objects.requireNonNull(tenants);
        this.clock = Objects.requireNonNull(clock);
        this.callers = freeze(Objects.requireNonNull(properties), identityProperties);
    }

    /** 向原生 Provider 收集器注册唯一 SI 错误映射器。 <p>Registers the SI mapper for native provider collection discovery.</p> */
    @Bean("ddcSiNativeRpcProviderExceptionMapper")
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public RpcProviderExceptionMapper providerExceptionMapper() {
        return DdcSiRpcErrorMapper.providerExceptionMapper();
    }

    /** 核验方法、主体、namespace 与本地租户映射。 <p>Verifies method, principal, namespace, and local tenant mapping.</p> */
    public AuthorizedCallerVO authorize(String fullMethodName, Message request, long declaredTenantId,
            String bizCode, String namespaceCode, String envCode, @Nullable String registrationOwnerApp) {
        var principal = IdpRpcSecurityContext.currentPrincipalOrNull();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (principal == null || !(authentication instanceof IdpAuthenticationToken accepted)
                || !accepted.isAuthenticated() || accepted.getPrincipal() != principal) throw unauthenticated();
        if (!(principal instanceof ServiceIdentityPrincipal service)
                || service.scopeContext() != ServiceTokenContext.TENANT) throw forbidden();
        var proof = IdpRpcSecurityContext.currentProofOrNull();
        if (proof == null || !proof.equals(accepted.getDetails()) || !service.expiresAt().isAfter(clock.instant())) {
            throw unauthenticated();
        }
        final URI issuer;
        try { issuer = accepted.verifiedIssuer(); }
        catch (IllegalStateException unavailable) { throw unauthenticated(); }
        if (!issuer.equals(proof.issuer()) || !service.resourceUri().equals(proof.resource())) throw forbidden();
        final IdpRpcAdmissionIdentityResolver resolver;
        try { resolver = identities.getIfAvailable(); }
        catch (RuntimeException unavailable) { throw unavailable(); }
        if (resolver == null) throw unavailable();
        // This resolver verifies the private frame produced by the official Bearer interceptor.
        // It does not acquire quota; the neutral native admission component is the quota owner.
        final top.egon.cola.component.rpc.provider.server.RpcAdmissionIdentityVO method;
        try { method = resolver.resolve(fullMethodName, request); }
        catch (StatusRuntimeException rejected) {
            throw switch (rejected.getStatus().getCode()) {
                case UNAUTHENTICATED -> unauthenticated();
                case PERMISSION_DENIED -> forbidden();
                default -> unavailable();
            };
        }
        if (!Long.toString(method.tenantId()).equals(service.tenantId()) || !method.clientId().equals(service.clientId())
                || !method.resource().equals(service.resourceUri().toASCIIString())) throw forbidden();
        requireCoordinate(bizCode); requireCoordinate(namespaceCode); requireCoordinate(envCode);
        if (request == null || !service.sourceBizCode().equals(bizCode) || !service.sourceEnvironment().equals(envCode)) throw forbidden();
        var approved = callers.get(new CallerKeyVO(issuer, service.resourceUri(), service.tenantId(), service.clientId(),
                service.sourceBizCode(), service.sourceAppCode(), service.sourceEnvironment()));
        var privilege = approved == null ? null : approved.methods().get(fullMethodName);
        if (privilege == null || !privilege.namespaces().contains(namespaceCode)
                || !service.scopes().containsAll(privilege.scopes())) throw forbidden();
        if (REGISTER.equals(fullMethodName)) {
            requireCoordinate(registrationOwnerApp);
            if (!service.sourceAppCode().equals(registrationOwnerApp)) throw forbidden();
        }
        final top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO tenant;
        try { tenant = tenants.requireByIdentityTenantId(service.tenantId()); }
        catch (IllegalArgumentException unregistered) { throw forbidden(); }
        if (tenant == null || !tenant.isEnabled() || !Objects.equals(tenant.getTenantId(), approved.localTenantId())
                || declaredTenantId <= 0 || !tenant.getTenantId().equals(declaredTenantId)) throw forbidden();
        return new AuthorizedCallerVO(new DdcTenantContextDTO(tenant.getTenantId(), tenant.getIdentityTenantId(),
                "service:" + service.clientId()), service.sourceAppCode());
    }

    private Map<CallerKeyVO, ApprovedCallerVO> freeze(DdcSiNativeRpcProperties properties,
            ObjectProvider<IdpStarterProperties> identityProperties) {
        if (properties.getBindings() == null || properties.getBindings().size() > 128) throw invalid();
        if (properties.getBindings().isEmpty()) return Map.of();
        final IdpStarterProperties identity;
        try { identity = identityProperties.getIfAvailable(); }
        catch (RuntimeException unavailable) { throw invalid(); }
        if (identity == null || !identity.getRpc().isEnabled() || !identity.getRpc().getProvider().isEnabled()) throw invalid();
        var result = new LinkedHashMap<CallerKeyVO, ApprovedCallerVO>();
        for (var binding : properties.getBindings()) {
            if (binding == null || !https(binding.getIssuer()) || !https(binding.getResourceUri())
                    || !coordinate(binding.getIdentityTenantId()) || !coordinate(binding.getClientId())
                    || !coordinate(binding.getSourceBizCode()) || !coordinate(binding.getSourceAppCode())
                    || !coordinate(binding.getSourceEnvironment()) || binding.getMethods() == null
                    || binding.getMethods().isEmpty() || binding.getMethods().size() > 4) throw invalid();
            final top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO tenant;
            try { tenant = tenants.requireByIdentityTenantId(binding.getIdentityTenantId()); }
            catch (IllegalArgumentException unregistered) { throw invalid(); }
            if (tenant == null || !tenant.isEnabled() || tenant.getTenantId() == null || tenant.getTenantId() <= 0) throw invalid();
            var methods = new LinkedHashMap<String, PrivilegeVO>();
            for (var entry : binding.getMethods().entrySet()) {
                var policy = identity.getRpc().getProvider().getMethods().get(entry.getKey());
                if (!METHODS.contains(entry.getKey()) || policy == null || !policy.isRequired()
                        || policy.getPrincipalKind() != IdpStarterProperties.RpcPrincipalKindEnum.SERVICE
                        || policy.getScopeContext() != ServiceTokenContext.TENANT
                        || !binding.getResourceUri().equals(policy.getExpectedResourceUri())
                        || policy.getRequiredScopes() == null || policy.getRequiredScopes().isEmpty()
                        || policy.getRequiredScopes().stream().anyMatch(value -> !coordinate(value))
                        || entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().size() > 64
                        || entry.getValue().stream().anyMatch(value -> !coordinate(value))) throw invalid();
                methods.put(entry.getKey(), new PrivilegeVO(Set.copyOf(entry.getValue()), Set.copyOf(policy.getRequiredScopes())));
            }
            var key = new CallerKeyVO(binding.getIssuer(), binding.getResourceUri(), binding.getIdentityTenantId(),
                    binding.getClientId(), binding.getSourceBizCode(), binding.getSourceAppCode(), binding.getSourceEnvironment());
            if (result.putIfAbsent(key, new ApprovedCallerVO(tenant.getTenantId(), Map.copyOf(methods))) != null) throw invalid();
        }
        return Map.copyOf(result);
    }

    private static boolean https(URI value) {
        return value != null && "https".equals(value.getScheme()) && value.getHost() != null && !value.isOpaque()
                && value.getRawUserInfo() == null && value.getRawQuery() == null && value.getRawFragment() == null
                && value.getPort() != 0 && value.getPort() <= 65535 && value.normalize().equals(value);
    }
    private static boolean coordinate(@Nullable String value) {
        return value != null && !value.isBlank() && value.length() <= 256 && value.equals(value.strip())
                && !value.contains("*") && !value.contains("?") && value.codePoints().noneMatch(Character::isISOControl);
    }
    private static void requireCoordinate(@Nullable String value) {
        if (!coordinate(value)) throw DdcSiRpcErrorMapper.status(Status.INVALID_ARGUMENT, "TIANSHU_SI_VALIDATION_ERROR", false);
    }
    private static IllegalStateException invalid() { return new IllegalStateException("TIANSHU_SI_NATIVE_BINDING_INVALID"); }
    private static StatusRuntimeException unauthenticated() {
        return DdcSiRpcErrorMapper.status(Status.UNAUTHENTICATED, "TIANSHU_SI_AUTHENTICATION_REQUIRED", false);
    }
    private static StatusRuntimeException forbidden() {
        return DdcSiRpcErrorMapper.status(Status.PERMISSION_DENIED, "TIANSHU_SI_FORBIDDEN", false);
    }
    private static StatusRuntimeException unavailable() {
        return DdcSiRpcErrorMapper.status(Status.UNAVAILABLE, "TIANSHU_SI_AUTHORITY_UNAVAILABLE", true);
    }

    public record AuthorizedCallerVO(DdcTenantContextDTO tenant, String callerAppCode) { }
    private record CallerKeyVO(URI issuer, URI resource, String identityTenant, String client,
                               String biz, String app, String env) { }
    private record PrivilegeVO(Set<String> namespaces, Set<String> scopes) { }
    private record ApprovedCallerVO(Long localTenantId, Map<String, PrivilegeVO> methods) { }
}
