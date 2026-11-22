package top.egon.cola.component.tianshu.admin.security.management;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DdcIdentityStartupGuardTest {
    @Test
    void httpsAuthorityAndProxyTerminatedTlsUseTheReviewedPlaintextListener() {
        assertThat(new DdcAdminSecurityConfiguration().ddcIdentityStartupGuard(environment(), properties(), () -> true))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void identityReadinessAndExternalHttpsRemainRequired() {
        var configuration = new DdcAdminSecurityConfiguration();
        assertThatThrownBy(() -> configuration.ddcIdentityStartupGuard(environment(), properties(), () -> false))
                .hasMessage("OAUTH_HOST_SECURITY_NOT_READY");
        assertThatThrownBy(() -> configuration.ddcIdentityStartupGuard(environment().withProperty(
                "egon.cola.platform.tianquan.shoubing.authority.base-uri", "http://idp.example"), properties(), () -> true))
                .hasMessage("OAUTH_AUTHORITY_TLS_REQUIRED");
        assertThatThrownBy(() -> configuration.ddcIdentityStartupGuard(environment().withProperty(
                "egon.cola.platform.tianquan.jianshen.authorization.endpoint", "http://rbac.example"), properties(), () -> true))
                .hasMessage("OAUTH_HOST_TLS_REQUIRED");
        var untrustedJwks = properties();
        untrustedJwks.getTrustedIssuers().get("tenant-a").setJwksUri("http://idp.example/jwks");
        assertThatThrownBy(() -> configuration.ddcIdentityStartupGuard(environment(), untrustedJwks, () -> true))
                .hasMessage("OAUTH_HOST_TLS_REQUIRED");
    }

    @Test
    void rejectsUnreviewedListenerAndForwardedHeaderModesBeforeStartup() {
        var configuration = new DdcAdminSecurityConfiguration();
        assertThatThrownBy(() -> configuration.ddcIdentityStartupGuard(environment().withProperty(
                "server.ssl.enabled", "true"), properties(), () -> true))
                .hasMessage("Tianshu internal application listeners require server.ssl.enabled=false");
        assertThatThrownBy(() -> configuration.ddcIdentityStartupGuard(environment().withProperty(
                "server.forward-headers-strategy", "framework"), properties(), () -> true))
                .hasMessage("Tianshu internal application listeners do not trust forwarded headers");
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("server.ssl.enabled", "false")
                .withProperty("server.forward-headers-strategy", "none")
                .withProperty("egon.cola.component.tianshu.admin.transport-security.mode", "KUBERNETES_PLAINTEXT")
                .withProperty("egon.cola.platform.tianquan.shoubing.authority.base-uri", "https://idp.example")
                .withProperty("egon.cola.platform.tianquan.jianshen.authorization.endpoint", "https://rbac.example");
    }

    private IdpStarterProperties properties() {
        var properties = mock(IdpStarterProperties.class);
        var issuer = new IdpStarterProperties.TrustedIssuerSettings();
        issuer.setIssuer("https://idp.example/tenants/1001");
        issuer.setJwksUri("https://idp.example/tenants/1001/.well-known/jwks.json");
        when(properties.isMultiIssuerConfigured()).thenReturn(true);
        when(properties.getTrustedIssuers()).thenReturn(Map.of("tenant-a", issuer));
        when(properties.getResourceUri()).thenReturn(URI.create("https://tianshu.example"));
        return properties;
    }
}
