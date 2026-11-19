package top.egon.cola.component.tianshu.admin.security.ai;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test-source-only verifier fixture. No main-source caller can establish the package-private frame. */
public final class DdcAiServiceCallerTestFixture {
    private DdcAiServiceCallerTestFixture() { }

    public static <T> T call(Operation operation, DdcAiScopeDTO placement, long tenantId, String stableActor,
            String credential, Instant now, Supplier<T> action) {
        return call(operation, placement, placement, tenantId, stableActor, credential, now, action);
    }

    public static <T> T call(Operation operation, DdcAiScopeDTO placement, DdcAiScopeDTO source, long tenantId,
            String stableActor, String credential, Instant now, Supplier<T> action) {
        URI issuer = URI.create("https://fixture.identity.example/tenant");
        URI resource = URI.create("https://fixture.tianshu.example/ai");
        String identityTenant = "tenant-" + tenantId;
        String scope = "fixture:ai:" + operation;
        var binding = new DdcAiInternalSecurityConfiguration.CallerBinding(tenantId, identityTenant, issuer, resource,
                "fixture-client", "fixture-app", source.bizCode(), source.appCode(), source.env(), stableActor,
                Set.of(placement), Set.of(operation));
        var properties = new DdcAiInternalSecurityConfiguration.InternalProperties(Map.of(operation, scope), List.of(binding));
        var registry = mock(DdcTenantRegistry.class);
        when(registry.requireByIdentityTenantId(identityTenant)).thenReturn(new DdcTenantBindingDTO(tenantId, identityTenant, true));
        var resolver = new DdcAiServiceCallerResolver(properties, registry, Clock.fixed(now, ZoneOffset.UTC));
        var principal = new ServiceIdentityPrincipal("fixture-client", identityTenant, "fixture-client", "fixture-jti", resource,
                1, Set.of(scope), source.bizCode(), source.appCode(), source.env(), credential, now.minusSeconds(1),
                now.plusSeconds(60), "fixture-app", ServiceTokenContext.TENANT);
        var token = new IdpAuthenticationToken(principal, List.of(new SimpleGrantedAuthority("SCOPE_" + scope)), issuer);
        token.setDetails(new TokenAuditProofBO(issuer, resource, "a".repeat(64), "b".repeat(64), "c".repeat(64)));
        var previousAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/internal/v1/ai/fixture");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        var observations = mock(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpObservedVerificationComponent.class,
                invocation -> switch (invocation.getMethod().getName()) {
                    case "enabled", "authorizationDenied" -> true;
                    default -> org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
                });
        var observationFilter = new top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter(
                observations, new top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy(
                        List.of(), List.of("/internal/v1/ai/**"), false));
        var previous = SecurityContextHolder.getContext();
        var current = SecurityContextHolder.createEmptyContext();
        current.setAuthentication(token);
        SecurityContextHolder.setContext(current);
        try {
            var caller = resolver.resolve(operation);
            java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
            try (var ignored = new DdcTenantContextExecutor().bind(caller.tenant())) {
                observationFilter.doFilter(request, response, (verifiedRequest, verifiedResponse) -> {
                    try { result.set(resolver.within(caller, operation, null, action::get)); }
                    catch (RuntimeException failure) { throw failure; }
                    catch (Exception unexpected) { throw new jakarta.servlet.ServletException("Unexpected checked fixture failure", unexpected); }
                });
            }
            return result.get();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception unexpected) {
            throw new AssertionError("Unexpected checked fixture failure", unexpected);
        } finally {
            SecurityContextHolder.setContext(previous);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previousAttributes);
        }
    }
}
