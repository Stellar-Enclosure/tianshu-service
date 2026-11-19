package top.egon.cola.component.tianshu.admin.security.ai;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiServiceCallerVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** The frame can only be established by the verified HTTP boundary, and is not inherited by async work. */
@Slf4j
public final class DdcAiServiceCallerResolver {
    private static final ScopedValue<Frame> CURRENT = ScopedValue.newInstance();
    private final DdcAiInternalSecurityConfiguration.InternalProperties properties;
    private final DdcTenantRegistry registry;
    private final Clock clock;

    public DdcAiServiceCallerResolver(DdcAiInternalSecurityConfiguration.InternalProperties properties,
            DdcTenantRegistry registry, Clock clock) {
        this.properties = properties;
        this.registry = registry;
        this.clock = clock;
    }

    DdcAiServiceCallerVO resolve(Operation operation) {
        IdpAuthenticationToken token = verifiedToken();
        ServiceIdentityPrincipal service = (ServiceIdentityPrincipal) token.getPrincipal();
        String requiredScope = properties.operationScopes().get(operation);
        if (requiredScope == null || !service.scopes().contains(requiredScope) || !validToken(token, clock)) {
            throw new Rejected();
        }
        var binding = properties.callers().stream()
                .filter(value -> value.accepts(token, service) && value.operations().contains(operation))
                .findFirst().orElseThrow(Rejected::new);
        final top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO tenant;
        try {
            tenant = registry.requireByIdentityTenantId(service.tenantId());
        } catch (IllegalArgumentException denied) {
            throw new Rejected();
        } catch (RuntimeException unavailable) {
            throw new Unavailable();
        }
        if (tenant == null || !tenant.isEnabled() || tenant.getTenantId() == null
                || tenant.getTenantId() != binding.tenantId()
                || !binding.identityTenantId().equals(tenant.getIdentityTenantId())) {
            throw new Rejected();
        }
        return new DdcAiServiceCallerVO(new DdcTenantContextDTO(tenant.getTenantId(), tenant.getIdentityTenantId(),
                "service:" + binding.stableActorId()), binding.stableActorId(), binding.sourceBizCode(),
                binding.sourceAppCode(), binding.sourceEnvironment(), binding.placements(), binding.operations());
    }

    <T> T within(DdcAiServiceCallerVO caller, Operation operation, @Nullable DdcAiClientScope selector,
            Callable<T> action) throws Exception {
        if (CURRENT.isBound()) {
            throw new Rejected();
        }
        IdpAuthenticationToken token = verifiedToken();
        if (!validToken(token, clock) || !caller.operations().contains(operation)
                || selector != null && selector.tenantId() != caller.tenant().tenantId()) {
            throw new Rejected();
        }
        Frame frame = new Frame(token, token.getDetails(), caller, operation, selector, clock);
        return ScopedValue.where(CURRENT, frame).call(action::call);
    }

    /** Revalidates the exact verifier-owned authentication, tenant and operation at the domain boundary. */
    public static DdcAiServiceCallerVO current(Operation operation) {
        if (!CURRENT.isBound()) {
            throw new Rejected();
        }
        Frame frame = CURRENT.get();
        if (frame.operation() != operation || frame.token() != SecurityContextHolder.getContext().getAuthentication()
                || frame.proof() != frame.token().getDetails() || !validToken(frame.token(), frame.clock())
                || !Long.toString(frame.caller().tenant().tenantId()).equals(MDC.get("tenantId"))
                || !frame.caller().tenant().actor().equals(MDC.get("userId"))) {
            throw new Rejected();
        }
        return frame.caller();
    }

    public static DdcAiServiceCallerVO current(Operation operation, DdcAiScopeDTO actualScope) {
        DdcAiServiceCallerVO caller = current(operation);
        Frame frame = CURRENT.get();
        DdcAiClientScope selector = frame.selector();
        if (!caller.placements().contains(actualScope) || selector != null
                && (!selector.bizCode().equals(actualScope.bizCode()) || !selector.env().equals(actualScope.env())
                || !selector.appCode().equals(actualScope.appCode()))) {
            throw new Rejected();
        }
        return caller;
    }

    private static IdpAuthenticationToken verifiedToken() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof IdpAuthenticationToken token)
                || !(token.getPrincipal() instanceof ServiceIdentityPrincipal)) {
            throw new Rejected();
        }
        return token;
    }

    private static boolean validToken(IdpAuthenticationToken token, Clock clock) {
        if (!token.isAuthenticated() || !(token.getPrincipal() instanceof ServiceIdentityPrincipal service)
                || service.scopeContext() != ServiceTokenContext.TENANT || service.resourceVersion() <= 0
                || !service.expiresAt().isAfter(clock.instant()) || service.issuedAt().isAfter(clock.instant())
                || !(token.getDetails() instanceof TokenAuditProofBO proof)) {
            return false;
        }
        Set<String> expected = service.scopes().stream().map(value -> "SCOPE_" + value).collect(Collectors.toSet());
        if (!expected.equals(token.getAuthorities().stream().map(value -> value.getAuthority()).collect(Collectors.toSet()))) {
            return false;
        }
        try {
            return token.verifiedIssuer().equals(proof.issuer()) && service.resourceUri().equals(proof.resource());
        } catch (IllegalStateException absentIssuer) {
            return false;
        }
    }

    private record Frame(IdpAuthenticationToken token, Object proof, DdcAiServiceCallerVO caller,
            Operation operation, @Nullable DdcAiClientScope selector, Clock clock) { }

    public static final class Rejected extends RuntimeException {
        public Rejected() { super("TIANSHU_AI_FORBIDDEN", null, false, false); }
    }

    public static final class Unavailable extends RuntimeException {
        public Unavailable() { super("TIANSHU_AI_UNAVAILABLE", null, false, false); }
    }
}
