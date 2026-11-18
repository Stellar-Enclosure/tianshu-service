package top.egon.cola.component.tianshu.admin.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.component.tianshu.admin.controller.config.DdcConfigDeliveryController;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliverySecurityConfiguration.*;

class DdcConfigDeliverySecurityConfigurationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final URI ISSUER = URI.create("https://identity.example.test/tenant-reader");
    private static final URI RESOURCE = URI.create("https://tianshu.example.test");
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("941", "reading", "test", "reader", "application.yml");
    private static final Set<String> READ = Set.of(DdcConfigWorkloadVerifier.READ_SCOPE);
    private final ServiceAccessTokenVerifier oauth = mock(ServiceAccessTokenVerifier.class);
    private final DdcConfigWorkloadVerifier kubernetes = mock(DdcConfigWorkloadVerifier.class);
    private final IdpEndpointAuthenticationPolicy policy =
            new IdpEndpointAuthenticationPolicy(List.of(), List.of(SNAPSHOT_PATH, RECEIPT_PATH), false);
    private final DeliveryCredentialsFilter filter = new DeliveryCredentialsFilter(oauth, policy, kubernetes, JSON);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void distinctAuthoritiesAndVerifiedScopeAreCheckedBeforeTheControllerAndAlwaysCleared() throws Exception {
        var principal = principal();
        var workload = new DdcConfigWorkloadIdentityVO(new DdcTenantContextDTO(941L, "tenant-reader", "service:reader"),
                SCOPE, "bind", 7, "cluster", "runtime", "deployment-uid", "pod-uid", "app", "containerd://abc", "boot-id");
        when(oauth.verify("oauth.proof")).thenReturn(new AccessTokenVerification.Valid<>(principal, READ, null, ISSUER));
        when(kubernetes.verify(principal, "pod.proof", "boot-id")).thenReturn(workload);
        var request = request("GET", SNAPSHOT_PATH);
        request.addHeader("X-Forwarded-For", "198.51.100.15");
        request.addHeader("If-None-Match", "\"cached-but-untrusted\"");
        var response = new MockHttpServletResponse();
        var invocation = new java.util.concurrent.atomic.AtomicBoolean();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(principal);
            assertThat(servletRequest.getAttribute(DdcConfigDeliveryController.VERIFIED_WORKLOAD_ATTRIBUTE)).isSameAs(workload);
            invocation.set(true);
        });
        assertThat(invocation).isTrue();
        assertThat(request.getAttribute(DdcConfigDeliveryController.VERIFIED_WORKLOAD_ATTRIBUTE)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        var order = inOrder(oauth, kubernetes);
        order.verify(oauth).verify("oauth.proof");
        order.verify(kubernetes).verify(principal, "pod.proof", "boot-id");
    }

    @Test
    void expiredRejectedAndUnavailableOAuthProofDoNotCallKubernetesOrReadBody() throws Exception {
        verifyOAuthFailure(new AccessTokenVerification.Expired<>(), 401, "TIANSHU_CONFIG_AUTHENTICATION_REQUIRED");
        verifyOAuthFailure(new AccessTokenVerification.Invalid<>("IDENTITY_STATE_REJECTED"), 403,
                "TIANSHU_CONFIG_AUTHORIZATION_REVOKED");
        verifyOAuthFailure(new AccessTokenVerification.Unavailable<>("JWKS_UNAVAILABLE"), 503,
                "TIANSHU_CONFIG_OAUTH_UNAVAILABLE");
        verifyNoInteractions(kubernetes);
    }

    @Test
    void readScopeDoesNotAuthorizeReceiptAndForwardedIdentityAndDuplicateHeadersDoNotAuthorizeAnyRequest() throws Exception {
        when(oauth.verify("oauth.proof")).thenReturn(new AccessTokenVerification.Valid<>(principal(), READ, null, ISSUER));
        checkDenied(request("POST", RECEIPT_PATH), 403, "TIANSHU_CONFIG_SCOPE_REQUIRED");
        var duplicate = request("GET", SNAPSHOT_PATH);
        duplicate.addHeader("Authorization", "Bearer other.proof");
        checkDenied(duplicate, 401, "TIANSHU_CONFIG_AUTHENTICATION_REQUIRED");
        var forwarded = new MockHttpServletRequest("GET", SNAPSHOT_PATH);
        forwarded.addHeader("X-Forwarded-User", "reader");
        forwarded.addHeader("Forwarded", "proto=https;host=tianshu.example.test");
        checkDenied(forwarded, 401, "TIANSHU_CONFIG_AUTHENTICATION_REQUIRED");
        verify(oauth, times(1)).verify("oauth.proof");
        verifyNoInteractions(kubernetes);
    }

    @Test
    void knownWorkloadRejectionAndAuthorityOutageHaveDistinctStatuses() throws Exception {
        var principal = principal();
        when(oauth.verify("oauth.proof")).thenReturn(new AccessTokenVerification.Valid<>(principal, READ, null, ISSUER));
        doThrow(new DdcConfigWorkloadVerifier.Rejected(HttpStatus.UNAUTHORIZED, "WORKLOAD_PROOF_INVALID"),
                new DdcConfigWorkloadVerifier.Rejected(HttpStatus.SERVICE_UNAVAILABLE, "WORKLOAD_AUTHORITY_UNAVAILABLE"))
                .when(kubernetes).verify(principal, "pod.proof", "boot-id");

        checkDenied(request("GET", SNAPSHOT_PATH), 401, "WORKLOAD_PROOF_INVALID");
        checkDenied(request("GET", SNAPSHOT_PATH), 503, "WORKLOAD_AUTHORITY_UNAVAILABLE");
    }

    @Test
    void productionPropertyBinderBuildsFrozenTenantScopeAndDeploymentSelector() {
        var map = new LinkedHashMap<String, String>();
        map.put("snapshot-validity", "5m");
        map.put("api-server", "https://kubernetes.default.svc");
        map.put("bindings[0].target-binding-id", "bind");
        map.put("bindings[0].target-binding-revision", "7");
        map.put("bindings[0].identity-tenant-id", "tenant-reader");
        map.put("bindings[0].client-id", "client-reader");
        map.put("bindings[0].credential-ids[0]", "cred-reader");
        map.put("bindings[0].app-id", "app-reader");
        map.put("bindings[0].source-biz-code", "reading");
        map.put("bindings[0].source-environment", "test");
        map.put("bindings[0].source-app-code", "reader");
        map.put("bindings[0].resource-uri", RESOURCE.toString());
        map.put("bindings[0].scope.tenant-id", "941");
        map.put("bindings[0].scope.biz-code", "reading");
        map.put("bindings[0].scope.env", "test");
        map.put("bindings[0].scope.app-code", "reader");
        map.put("bindings[0].scope.resource-name", "application.yml");
        map.put("bindings[0].cluster-id", "cluster");
        map.put("bindings[0].k8s-namespace", "runtime");
        map.put("bindings[0].service-account-name", "reader-sa");
        map.put("bindings[0].deployment-name", "reader");
        map.put("bindings[0].workload-uid", "deployment-uid");
        map.put("bindings[0].container-name", "app");
        map.put("bindings[0].pod-selector.app", "reader");
        var source = new MapConfigurationPropertySource();
        map.forEach((key, value) -> source.put(PREFIX + "." + key, value));
        var bound = new Binder(source).bind(PREFIX,
                Bindable.of(DeliveryProperties.class)).get();
        assertThat(bound.bindings()).singleElement().satisfies(binding -> {
            assertThat(binding.scope()).isEqualTo(SCOPE);
            assertThat(binding.podSelector()).containsEntry("app", "reader");
            assertThat(binding.targetBindingRevision()).isEqualTo(7L);
        });
    }

    private void verifyOAuthFailure(AccessTokenVerification<ServiceIdentityPrincipal> verifier, int expected, String code)
            throws Exception {
        when(oauth.verify("oauth.proof")).thenReturn(verifier);
        checkDenied(request("POST", RECEIPT_PATH), expected, code);
    }

    private void checkDenied(MockHttpServletRequest request, int status, String code) throws Exception {
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo(status == 401 ? "Bearer" : null);
        assertThat(response.getContentAsString()).doesNotContain("oauth.proof", "pod.proof");
        var body = JSON.readTree(response.getContentAsString());
        assertThat(body.get("errorCode").textValue()).isEqualTo(code);
        assertThat(body.get("requestId").textValue()).isEqualTo(requestId(request));
    }

    private static MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer oauth.proof");
        request.addHeader(WORKLOAD_HEADER, "pod.proof");
        request.addHeader(BOOT_HEADER, "boot-id");
        request.setContent("{intentionally-unparsed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }

    private static ServiceIdentityPrincipal principal() {
        var now = Instant.now();
        return new ServiceIdentityPrincipal("client-reader", "tenant-reader", "client-reader", "jti",
                RESOURCE, 1, READ, "reading", "reader", "test", "cred-reader", now.minusSeconds(1),
                now.plusSeconds(60), "app-reader", ServiceTokenContext.TENANT);
    }
}
