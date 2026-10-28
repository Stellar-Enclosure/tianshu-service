package top.egon.cola.component.tianshu.admin.security.management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcListDelegationVerifierTest {
    private final Rbac3AuthorizationClient client = mock(Rbac3AuthorizationClient.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<Rbac3AuthorizationClient> clients = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<ServiceAccessTokenVerifier> serviceVerifiers = mock(ObjectProvider.class);
    private final ServiceAccessTokenVerifier serviceVerifier = mock(ServiceAccessTokenVerifier.class);
    private final DdcListDelegationVerifier verifier = new DdcListDelegationVerifier(clients, serviceVerifiers);
    private final URI issuer = URI.create("https://idp.example/tenants/20");

    @Test void bindsTheAuthorityUserToTheVerifiedServiceApplicationAndTenant() throws Exception {
        when(clients.getIfAvailable()).thenReturn(client);
        when(serviceVerifiers.getIfAvailable()).thenReturn(serviceVerifier);
        var caller = service("shop", Set.of(DdcListDelegationVerifier.REQUIRED_SCOPE));
        when(serviceVerifier.verify("service-proof")).thenReturn(new AccessTokenVerification.Valid<>(caller, caller.scopes(), null, issuer));
        String token = token(issuer.toString(), "alice", "20", "shop:read");
        var snapshot = snapshot("20", "trade", "shop", "test", issuer.toString());
        when(client.fetchDelegated("shop", "20", token)).thenReturn(snapshot);
        var result = verifier.verify(new IdpAuthenticationToken(caller, List.of(), issuer), "service-proof", token);
        assertThat(result.userSnapshot()).isSameAs(snapshot);
        assertThat(result.userScopes()).containsExactly("shop:read");
        var otherApplication = snapshot("20", "trade", "other", "test", issuer.toString());
        when(client.fetchDelegated("shop", "20", token)).thenReturn(otherApplication);
        assertThatThrownBy(() -> verifier.verify(new IdpAuthenticationToken(caller, List.of(), issuer), "service-proof", token))
                .isInstanceOf(AccessDeniedException.class).hasMessage("LIST_DELEGATION_IDENTITY_DENIED");
    }

    @Test void deniesMissingServiceScopeBeforeSendingTheUserToken() {
        assertThatThrownBy(() -> verifier.verify(new IdpAuthenticationToken(service("shop", Set.of("other")), List.of(), issuer), "service-proof", "secret-token"))
                .isInstanceOf(AccessDeniedException.class).hasMessageNotContaining("secret-token");
        verifyNoInteractions(client, serviceVerifier);
    }

    @Test void cannotUseScopesFromAUserTokenThatDoesNotMatchTheAuthorityView() throws Exception {
        when(clients.getIfAvailable()).thenReturn(client);
        when(serviceVerifiers.getIfAvailable()).thenReturn(serviceVerifier);
        var caller = service("shop", Set.of(DdcListDelegationVerifier.REQUIRED_SCOPE));
        when(serviceVerifier.verify("service-proof")).thenReturn(new AccessTokenVerification.Valid<>(caller, caller.scopes(), null, issuer));
        String token = token(issuer.toString(), "mallory", "20", "shop:read");
        var authorityView = snapshot("20", "trade", "shop", "test", issuer.toString());
        when(client.fetchDelegated("shop", "20", token)).thenReturn(authorityView);
        assertThatThrownBy(() -> verifier.verify(new IdpAuthenticationToken(caller, List.of(), issuer), "service-proof", token))
                .isInstanceOf(AccessDeniedException.class).hasMessage("LIST_DELEGATION_IDENTITY_DENIED");
    }

    @Test void rechecksTheServiceCredentialAndStopsAfterItIsRevoked() throws Exception {
        when(clients.getIfAvailable()).thenReturn(client);
        when(serviceVerifiers.getIfAvailable()).thenReturn(serviceVerifier);
        var caller = service("shop", Set.of(DdcListDelegationVerifier.REQUIRED_SCOPE));
        when(serviceVerifier.verify("service-proof")).thenReturn(new AccessTokenVerification.Valid<>(caller, caller.scopes(), null, issuer),
                new AccessTokenVerification.Invalid<>("REVOKED"));
        String token = token(issuer.toString(), "alice", "20", "shop:read");
        var authorityView = snapshot("20", "trade", "shop", "test", issuer.toString());
        when(client.fetchDelegated("shop", "20", token)).thenReturn(authorityView);
        var authentication = new IdpAuthenticationToken(caller, List.of(), issuer);
        verifier.verify(authentication, "service-proof", token);
        assertThatThrownBy(() -> verifier.verify(authentication, "service-proof", token)).isInstanceOf(AccessDeniedException.class);
        verify(client, times(1)).fetchDelegated("shop", "20", token);
    }

    private ServiceIdentityPrincipal service(String app, Set<String> scopes) {
        var now = Instant.now();
        return new ServiceIdentityPrincipal("client", "20", "client", "token", URI.create("https://metadata.example"), 1,
                scopes, "trade", app, "test", "credential", now.minusSeconds(1), now.plusSeconds(120), "app-id", ServiceTokenContext.TENANT);
    }
    private String token(String issuerValue, String subject, String tenant, String scope) {
        String payload = "{\"iss\":\"" + issuerValue + "\",\"sub\":\"" + subject + "\",\"tid\":\"" + tenant + "\",\"scope\":\"" + scope + "\"}";
        return "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
    private SystemAuthorizationSnapshot snapshot(String tenant, String biz, String app, String env, String issuerValue) {
        var scope = new CatalogScope("10", tenant, biz, app, "default", env);
        var view = new EffectivePermissionSnapshot(issuerValue, "alice", scope, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext(app, List.of("7"), List.of("7")), List.of("read"), Instant.now().plusSeconds(60));
        var snapshot = mock(SystemAuthorizationSnapshot.class);
        when(snapshot.permissionSnapshot()).thenReturn(view);
        when(snapshot.policyBinding()).thenReturn(mock(top.egon.cola.platform.tianquan.jianshen.contract.authorization.RuntimePolicyBinding.class));
        when(snapshot.expiresAt()).thenReturn(view.snapshotExpiresAt());
        when(snapshot.tenantId()).thenReturn(tenant);
        when(snapshot.identitySub()).thenReturn("alice");
        return snapshot;
    }
}
