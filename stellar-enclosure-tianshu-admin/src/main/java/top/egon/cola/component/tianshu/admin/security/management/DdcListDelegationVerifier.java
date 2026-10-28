package top.egon.cola.component.tianshu.admin.security.management;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;

/** Resolves the original USER through Jianshen; request parameters never select the tenant or subject. */
@Component("ddcListDelegationVerifier")
@RequiredArgsConstructor
public class DdcListDelegationVerifier {
    public static final String REQUIRED_SCOPE = "tianshu:list-template:delegate";
    private final ObjectProvider<Rbac3AuthorizationClient> clients;
    private final ObjectProvider<ServiceAccessTokenVerifier> serviceVerifiers;

    public VerifiedDelegation verify(Authentication authentication, String serviceAccessToken, String userAccessToken) {
        if (!(authentication instanceof IdpAuthenticationToken verified) || !verified.isAuthenticated()
                || !(verified.getPrincipal() instanceof ServiceIdentityPrincipal service)
                || service.scopeContext() != ServiceTokenContext.TENANT || !service.scopes().contains(REQUIRED_SCOPE)
                || !service.expiresAt().isAfter(Instant.now()) || service.tenantId() == null) throw denied();
        final URI issuer;
        try { issuer = verified.verifiedIssuer(); }
        catch (IllegalStateException missing) { throw denied(); }
        var serviceVerifier = serviceVerifiers.getIfAvailable();
        if (serviceVerifier == null) throw new IllegalStateException("LIST_DELEGATION_IDENTITY_AUTHORITY_UNAVAILABLE");
        var check = serviceVerifier.verify(serviceAccessToken);
        if (check instanceof AccessTokenVerification.Unavailable<?>) throw new IllegalStateException("LIST_DELEGATION_IDENTITY_AUTHORITY_UNAVAILABLE");
        if (!(check instanceof AccessTokenVerification.Valid<ServiceIdentityPrincipal> valid)
                || !service.equals(valid.principal()) || !issuer.equals(valid.verifiedIssuer())
                || !valid.scopes().contains(REQUIRED_SCOPE)) throw denied();
        var client = clients.getIfAvailable();
        if (client == null) throw new IllegalStateException("LIST_DELEGATION_AUTHORITY_UNAVAILABLE");
        final SystemAuthorizationSnapshot snapshot;
        try { snapshot = client.fetchDelegated(service.sourceAppCode(), service.tenantId(), userAccessToken); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LIST_DELEGATION_AUTHORITY_INTERRUPTED", interrupted);
        }
        var view = snapshot == null ? null : snapshot.permissionSnapshot();
        if (view == null || snapshot.policyBinding() == null || !snapshot.expiresAt().isAfter(Instant.now())
                || !service.tenantId().equals(snapshot.tenantId()) || !service.tenantId().equals(view.catalogScope().tenantId())
                || !service.sourceBizCode().equals(view.catalogScope().businessCode())
                || !service.sourceAppCode().equals(view.catalogScope().applicationCode())
                || !service.sourceEnvironment().equals(view.catalogScope().environment())
                || !issuer.toASCIIString().equals(view.issuer()) || !snapshot.identitySub().equals(view.subject())) throw denied();
        return new VerifiedDelegation(service, issuer, snapshot, verifiedTokenScopes(userAccessToken, view));
    }

    /** The authority has just verified these exact token bytes; decode only the bound claims and scopes. */
    private static Set<String> verifiedTokenScopes(String token, top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot view) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || parts[1].length() > 12_000) throw denied();
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            var claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (claims == null || !claims.isObject() || !view.issuer().equals(claims.path("iss").asText())
                    || !view.subject().equals(claims.path("sub").asText())
                    || !view.catalogScope().tenantId().equals(claims.path("tid").asText())) throw denied();
            var raw = claims.path("scope");
            TreeSet<String> scopes = new TreeSet<>();
            if (raw.isTextual() && !raw.textValue().isBlank()) {
                for (String scope : raw.textValue().trim().split("\\s+")) addScope(scopes, scope);
            } else if (raw.isArray()) {
                for (var value : raw) { if (!value.isTextual()) throw denied(); addScope(scopes, value.textValue()); }
            } else throw denied();
            if (scopes.isEmpty() || scopes.size() > 64) throw denied();
            return Set.copyOf(scopes);
        } catch (java.io.IOException | IllegalArgumentException invalid) { throw denied(); }
    }

    private static void addScope(Set<String> scopes, String value) {
        if (value == null || !value.matches("[A-Za-z0-9:._-]{1,128}") || !scopes.add(value)) throw denied();
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("LIST_DELEGATION_IDENTITY_DENIED"); }

    public record VerifiedDelegation(ServiceIdentityPrincipal service, URI issuer, SystemAuthorizationSnapshot userSnapshot, Set<String> userScopes) {
        public VerifiedDelegation { userScopes = Set.copyOf(userScopes); }
    }
}
