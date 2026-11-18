package top.egon.cola.component.tianshu.admin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier.BindingVO;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier.BindingsVO;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier.Rejected;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcConfigWorkloadVerifierTest {
    private static final String AUDIENCE = "https://config.example/workload";
    private static final URI RESOURCE = URI.create("https://config.example/config");
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("7", "trade", "prod", "orders", "application.yml");
    private final ObjectMapper mapper = new ObjectMapper();
    private DdcKubernetesApiClient api;
    private DdcTenantRegistry tenants;
    private DdcConfigWorkloadVerifier verifier;
    private ObjectNode review;
    private ObjectNode pod;
    private ObjectNode replicaSet;
    private ObjectNode deployment;
    @TempDir Path directory;

    @BeforeEach
    void setUp() throws Exception {
        api = mock(DdcKubernetesApiClient.class);
        tenants = mock(DdcTenantRegistry.class);
        when(tenants.requireByIdentityTenantId("identity-tenant"))
                .thenReturn(DdcTenantBindingDTO.builder().tenantId(7L).identityTenantId("identity-tenant").enabled(true).build());
        review = json("""
                {"apiVersion":"authentication.k8s.io/v1","kind":"TokenReview","status":{"authenticated":true,
                 "audiences":["https://config.example/workload"],"user":{"username":"system:serviceaccount:apps-prod:orders",
                 "extra":{"authentication.kubernetes.io/pod-name":["orders-abc-def"],"authentication.kubernetes.io/pod-uid":["pod-uid"]}}}}
                """);
        pod = json("""
                {"apiVersion":"v1","kind":"Pod","metadata":{"name":"orders-abc-def","namespace":"apps-prod","uid":"pod-uid",
                 "resourceVersion":"11","labels":{"app":"orders"},"ownerReferences":[{"apiVersion":"apps/v1","kind":"ReplicaSet",
                 "name":"orders-abc","uid":"rs-uid","controller":true}]},"spec":{"serviceAccountName":"orders","containers":[{"name":"app"}]},
                 "status":{"phase":"Running","containerStatuses":[{"name":"app","containerID":"containerd://first",
                 "state":{"running":{"startedAt":"2026-11-18T01:00:00Z"}}}]}}
                """);
        replicaSet = json("""
                {"apiVersion":"apps/v1","kind":"ReplicaSet","metadata":{"name":"orders-abc","namespace":"apps-prod","uid":"rs-uid",
                 "resourceVersion":"12","ownerReferences":[{"apiVersion":"apps/v1","kind":"Deployment","name":"orders",
                 "uid":"deployment-uid","controller":true}]}}
                """);
        deployment = json("""
                {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"orders","namespace":"apps-prod","uid":"deployment-uid",
                 "resourceVersion":"13","generation":3},"spec":{"selector":{"matchLabels":{"app":"orders"}}},"status":{"observedGeneration":3}}
                """);
        when(api.tokenReview("projected.proof", AUDIENCE)).thenReturn(review);
        when(api.pod("apps-prod", "orders-abc-def")).thenReturn(pod);
        when(api.replicaSet("apps-prod", "orders-abc")).thenReturn(replicaSet);
        when(api.deployment("apps-prod", "orders")).thenReturn(deployment);
        verifier = new DdcConfigWorkloadVerifier(api, tenants, new BindingsVO(AUDIENCE, List.of(binding())));
    }

    @Test
    void combinesVerifiedServiceWithFreshBoundPodAndRegisteredController() {
        var identity = verifier.verify(principal("credential-a"), "projected.proof", "boot-1");
        assertThat(identity.tenant().tenantId()).isEqualTo(7L);
        assertThat(identity.tenant().actor()).isEqualTo("service:credential-a");
        assertThat(identity.scope()).isEqualTo(SCOPE);
        assertThat(identity.targetBindingId()).isEqualTo("orders-target");
        assertThat(identity.targetBindingRevision()).isEqualTo(2);
        assertThat(identity.workloadUid()).isEqualTo("deployment-uid");
        assertThat(identity.podUid()).isEqualTo("pod-uid");
        assertThat(identity.containerId()).isEqualTo("containerd://first");
        assertThat(identity.bootId()).isEqualTo("boot-1");
        verify(api).tokenReview("projected.proof", AUDIENCE);
        verify(api).pod("apps-prod", "orders-abc-def");
        verify(api).replicaSet("apps-prod", "orders-abc");
        verify(api).deployment("apps-prod", "orders");
    }

    @Test
    void acceptsExplicitCredentialRotationAndObservesContainerRestartWithoutTreatingBootAsAKubernetesClaim() {
        var first = verifier.verify(principal("credential-a"), "projected.proof", "boot-1");
        ((ObjectNode) pod.path("status").path("containerStatuses").get(0)).put("containerID", "containerd://second");
        var second = verifier.verify(principal("credential-b"), "projected.proof", "boot-2");
        assertThat(second.podUid()).isEqualTo(first.podUid());
        assertThat(second.containerId()).isNotEqualTo(first.containerId());
        assertThat(second.bootId()).isEqualTo("boot-2");
    }

    @Test
    void rejectsUnregisteredServiceBeforeAnyKubernetesCall() {
        assertRejected(principal("other-credential"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(api);
    }

    @Test
    void rejectsEveryUnapprovedSignedServiceDimensionBeforeAnyKubernetesCall() {
        var p = principal("credential-a");
        var now = Instant.now();
        for (int dimension = 0; dimension < 7; dimension++) {
            var changed = new ServiceIdentityPrincipal(dimension == 0 ? "other" : p.clientId(),
                    dimension == 1 ? "other-tenant" : p.tenantId(), dimension == 0 ? "other" : p.clientId(), p.tokenId(),
                    dimension == 2 ? URI.create("https://other.example/config") : p.resourceUri(), 1, p.scopes(),
                    dimension == 3 ? "other" : p.sourceBizCode(), dimension == 4 ? "other" : p.sourceAppCode(),
                    dimension == 5 ? "other" : p.sourceEnvironment(), p.credentialId(), now.minusSeconds(1), now.plusSeconds(60),
                    dimension == 6 ? "other" : p.appId(), ServiceTokenContext.TENANT);
            assertRejected(changed, HttpStatus.FORBIDDEN);
        }
        verifyNoInteractions(api);
    }

    @Test
    void deniesPlatformScopeMissingEndpointScopesAndUnmappedTenant() {
        var p = principal("credential-a");
        var platform = new ServiceIdentityPrincipal(p.subject(), null, p.clientId(), p.tokenId(), p.resourceUri(), 1,
                p.scopes(), p.sourceBizCode(), p.sourceAppCode(), p.sourceEnvironment(), p.credentialId(),
                p.issuedAt(), p.expiresAt(), p.appId(), ServiceTokenContext.PLATFORM);
        assertRejected(platform, HttpStatus.FORBIDDEN);
        var missingScope = new ServiceIdentityPrincipal(p.subject(), p.tenantId(), p.clientId(), p.tokenId(), p.resourceUri(), 1,
                Set.of("other:read"), p.sourceBizCode(), p.sourceAppCode(), p.sourceEnvironment(), p.credentialId(),
                p.issuedAt(), p.expiresAt(), p.appId(), p.scopeContext());
        assertRejected(missingScope, HttpStatus.FORBIDDEN);
        when(tenants.requireByIdentityTenantId("identity-tenant")).thenThrow(new IllegalArgumentException("disabled"));
        assertRejected(p, HttpStatus.FORBIDDEN);
        verifyNoInteractions(api);
    }

    @Test
    void expiredServiceAndInvalidBootDoNotConsumePodProof() {
        var p = principal("credential-a");
        var expired = new ServiceIdentityPrincipal(p.subject(), p.tenantId(), p.clientId(), p.tokenId(), p.resourceUri(), 1,
                p.scopes(), p.sourceBizCode(), p.sourceAppCode(), p.sourceEnvironment(), p.credentialId(),
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), p.appId(), p.scopeContext());
        assertRejected(expired, HttpStatus.UNAUTHORIZED);
        for (String value : List.of(":boot", ".boot", "boot:1", "a".repeat(65))) {
            assertThatThrownBy(() -> verifier.verify(p, "projected.proof", value))
                    .isInstanceOfSatisfying(Rejected.class, rejected -> assertThat(rejected.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }
        verifyNoInteractions(api);
    }

    @Test
    void tokenReviewInvalidProofIs401ButItsAuthorityErrorIs503() {
        ((ObjectNode) review.path("status")).put("authenticated", false);
        assertRejected(principal("credential-a"), HttpStatus.UNAUTHORIZED);
        ((ObjectNode) review.path("status")).put("error", "sensitive upstream detail");
        assertRejected(principal("credential-a"), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void requiresDedicatedAudienceAndPodBoundTokenExtras() {
        ((ObjectNode) review.path("status")).putArray("audiences").add("https://kubernetes.default.svc");
        assertRejected(principal("credential-a"), HttpStatus.UNAUTHORIZED);
        ((ObjectNode) review.path("status")).putArray("audiences").add(AUDIENCE);
        ((ObjectNode) review.path("status").path("user")).remove("extra");
        assertRejected(principal("credential-a"), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsSameNamePodWithDifferentUidAndDeletedPod() {
        ((ObjectNode) pod.path("metadata")).put("uid", "replacement-uid");
        assertRejected(principal("credential-a"), HttpStatus.UNAUTHORIZED);
        ((ObjectNode) pod.path("metadata")).put("uid", "pod-uid").put("deletionTimestamp", "2026-11-18T01:00:00Z");
        assertRejected(principal("credential-a"), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsWrongServiceAccountSelectorAndDeploymentOwnership() {
        ((ObjectNode) review.path("status").path("user")).put("username", "system:serviceaccount:apps-prod:other");
        assertRejected(principal("credential-a"), HttpStatus.FORBIDDEN);
        ((ObjectNode) review.path("status").path("user")).put("username", "system:serviceaccount:apps-prod:orders");
        ((ObjectNode) pod.path("metadata").path("labels")).put("app", "other");
        assertRejected(principal("credential-a"), HttpStatus.FORBIDDEN);
        ((ObjectNode) pod.path("metadata").path("labels")).put("app", "orders");
        ((ObjectNode) replicaSet.path("metadata").path("ownerReferences").get(0)).put("uid", "other-deployment");
        assertRejected(principal("credential-a"), HttpStatus.FORBIDDEN);
    }

    @Test
    void refusesDuplicateControllerAndUnobservedDeploymentAsUnavailable() {
        ((com.fasterxml.jackson.databind.node.ArrayNode) pod.path("metadata").path("ownerReferences"))
                .add(pod.path("metadata").path("ownerReferences").get(0).deepCopy());
        assertRejected(principal("credential-a"), HttpStatus.SERVICE_UNAVAILABLE);
        ((com.fasterxml.jackson.databind.node.ArrayNode) pod.path("metadata").path("ownerReferences")).remove(1);
        ((ObjectNode) deployment.path("status")).put("observedGeneration", 2);
        assertRejected(principal("credential-a"), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void doesNotAuthorizeADeclaredContainerThatIsNotCurrentlyRunning() {
        ((ObjectNode) pod.path("status").path("containerStatuses").get(0).path("state")).remove("running");
        assertRejected(principal("credential-a"), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void infrastructureUnavailableAndAbsentPodRemainDistinctAndSanitized() {
        doThrow(new DdcKubernetesApiClient.Unavailable()).when(api).pod(anyString(), anyString());
        assertRejected(principal("credential-a"), HttpStatus.SERVICE_UNAVAILABLE);
        doThrow(new DdcKubernetesApiClient.NotFound()).when(api).pod(anyString(), anyString());
        assertRejected(principal("credential-a"), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void immutableRegistrationRequiresPositiveRevisionAndOneFixedTargetPerClient() {
        var target = binding();
        assertThatIllegalArgumentException().isThrownBy(() -> new BindingVO(target.targetBindingId(), 0,
                target.identityTenantId(), target.clientId(), target.credentialIds(), target.appId(), target.sourceBizCode(),
                target.sourceAppCode(), target.sourceEnvironment(), target.resourceUri(), target.scope(), target.clusterId(),
                target.k8sNamespace(), target.serviceAccountName(), target.deploymentName(), target.workloadUid(), target.containerName(), target.podSelector()));
        assertThatIllegalArgumentException().isThrownBy(() -> new BindingsVO(AUDIENCE, List.of(target, target)));
        assertThatThrownBy(() -> target.podSelector().put("version", "canary")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void actualHttpsTransportPinsCaRotatesApiCredentialAndNeverRedirects() throws Exception {
        try (var fixture = new KubernetesHttpsFixture(directory)) {
            try (var client = new DdcKubernetesApiClient(fixture.uri(), fixture.ca, fixture.token, mapper, Duration.ofSeconds(2), 4096)) {
                assertThat(client.tokenReview("caller.proof", AUDIENCE).path("kind").textValue()).isEqualTo("TokenReview");
                Files.writeString(fixture.token, "api-rotated\n");
                client.tokenReview("caller.second", AUDIENCE);
                assertThat(fixture.headers).containsExactly("Bearer api-first", "Bearer api-rotated");
                assertThat(fixture.bodies.get(0)).contains("caller.proof", AUDIENCE).doesNotContain("api-first");
                assertThatThrownBy(() -> client.pod("apps-prod", "redirect")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
                assertThat(fixture.redirected.get()).isZero();
                assertThatThrownBy(() -> client.pod("apps-prod", "denied")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
                assertThatThrownBy(() -> client.pod("apps-prod", "absent")).isInstanceOf(DdcKubernetesApiClient.NotFound.class);
                assertThatThrownBy(() -> client.pod("apps-prod", "foreign404")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
                assertThatThrownBy(() -> client.pod("apps-prod", "malformed404")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
                assertThatThrownBy(() -> client.pod("apps-prod", "malformed")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
                assertThatThrownBy(() -> client.pod("apps-prod", "duplicate")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
                assertThatIllegalArgumentException().isThrownBy(() -> client.pod("apps-prod", "../secret"));
            }
            try (var wrongHost = new DdcKubernetesApiClient(URI.create("https://localhost:" + fixture.server.getAddress().getPort()),
                    fixture.ca, fixture.token, mapper, Duration.ofSeconds(2), 4096)) {
                assertThatThrownBy(() -> wrongHost.tokenReview("caller.proof", AUDIENCE)).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
            }
            var otherCa = directory.resolve("invalid-ca");
            Files.writeString(otherCa, "invalid");
            assertThatThrownBy(() -> new DdcKubernetesApiClient(fixture.uri(), otherCa, fixture.token, mapper, Duration.ofSeconds(1), 4096))
                    .isInstanceOf(IllegalStateException.class).hasMessage("CONFIG_KUBERNETES_CA_UNAVAILABLE");
        }
    }

    @Test
    void actualHttpsBoundsBodyAndEntireExchangeIncludingStalledResponse() throws Exception {
        try (var fixture = new KubernetesHttpsFixture(directory);
             var client = new DdcKubernetesApiClient(fixture.uri(), fixture.ca, fixture.token, mapper, Duration.ofMillis(800), 128)) {
            assertThatThrownBy(() -> client.pod("apps-prod", "oversize")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
            long before = System.nanoTime();
            assertThatThrownBy(() -> client.pod("apps-prod", "stalled")).isInstanceOf(DdcKubernetesApiClient.Unavailable.class);
            assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(4));
            assertThat(fixture.stallStarted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertRejected(ServiceIdentityPrincipal principal, HttpStatus status) {
        assertThatThrownBy(() -> verifier.verify(principal, "projected.proof", "boot-1"))
                .isInstanceOfSatisfying(Rejected.class, rejected -> {
                    assertThat(rejected.status()).isEqualTo(status);
                    assertThat(rejected.getMessage()).isEqualTo(rejected.errorCode()).doesNotContain("sensitive", "projected.proof");
                    assertThat(rejected.getCause()).isNull();
                });
    }

    private BindingVO binding() {
        return new BindingVO("orders-target", 2, "identity-tenant", "orders-client", Set.of("credential-a", "credential-b"),
                "orders-app", "trade", "orders", "prod", RESOURCE, SCOPE, "cluster-1", "apps-prod", "orders", "orders",
                "deployment-uid", "app", Map.of("app", "orders"));
    }

    private ServiceIdentityPrincipal principal(String credentialId) {
        var now = Instant.now();
        return new ServiceIdentityPrincipal("orders-client", "identity-tenant", "orders-client", "jti", RESOURCE, 1,
                Set.of(DdcConfigWorkloadVerifier.READ_SCOPE, DdcConfigWorkloadVerifier.RECEIPT_SCOPE), "trade", "orders", "prod",
                credentialId, now.minusSeconds(1), now.plusSeconds(120), "orders-app", ServiceTokenContext.TENANT);
    }

    private ObjectNode json(String value) throws Exception { return (ObjectNode) mapper.readTree(value); }

    private static final class KubernetesHttpsFixture implements AutoCloseable {
        private final HttpsServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(4);
        private final Path ca;
        private final Path token;
        private final List<String> headers = new CopyOnWriteArrayList<>();
        private final List<String> bodies = new CopyOnWriteArrayList<>();
        private final AtomicInteger redirected = new AtomicInteger();
        private final CountDownLatch stallStarted = new CountDownLatch(1);
        private final CountDownLatch releaseStall = new CountDownLatch(1);

        private KubernetesHttpsFixture(Path directory) throws Exception {
            Path store = directory.resolve("server.p12");
            ca = directory.resolve("ca.pem");
            token = directory.resolve("api-token");
            Files.writeString(token, "api-first");
            String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
            command(keytool, "-genkeypair", "-alias", "api", "-storetype", "PKCS12", "-keystore", store.toString(),
                    "-storepass", "fixture-password", "-keypass", "fixture-password", "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "2", "-dname", "CN=Kubernetes Test", "-ext", "SAN=IP:127.0.0.1", "-noprompt");
            command(keytool, "-exportcert", "-rfc", "-alias", "api", "-keystore", store.toString(),
                    "-storepass", "fixture-password", "-file", ca.toString());
            var keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(store)) { keys.load(input, "fixture-password".toCharArray()); }
            var factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keys, "fixture-password".toCharArray());
            var context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(context));
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    if (path.endsWith("tokenreviews")) {
                        headers.add(exchange.getRequestHeaders().getFirst("Authorization"));
                        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    }
                    int status = path.endsWith("denied") ? 403 : path.endsWith("absent") || path.endsWith("404") ? 404 : path.endsWith("redirect") ? 302 : 200;
                    if (path.endsWith("redirect")) exchange.getResponseHeaders().add("Location", uri() + "/followed");
                    if (path.endsWith("followed")) redirected.incrementAndGet();
                    if (path.endsWith("stalled")) {
                        exchange.sendResponseHeaders(200, 0);
                        exchange.getResponseBody().write('{');
                        exchange.getResponseBody().flush();
                        stallStarted.countDown();
                        try { releaseStall.await(5, TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                        return;
                    }
                    String result = path.endsWith("oversize") ? "x".repeat(1024) : path.endsWith("malformed") || path.endsWith("malformed404") ? "not json"
                            : path.endsWith("duplicate") ? "{\"kind\":\"a\",\"kind\":\"b\"}"
                            : path.endsWith("absent") ? "{\"apiVersion\":\"v1\",\"kind\":\"Status\",\"status\":\"Failure\",\"reason\":\"NotFound\",\"code\":404}"
                            : "{\"apiVersion\":\"authentication.k8s.io/v1\",\"kind\":\"TokenReview\",\"status\":{\"authenticated\":true}}";
                    byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }
        private URI uri() { return URI.create("https://127.0.0.1:" + server.getAddress().getPort()); }
        private static void command(String... command) throws Exception {
            var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("fixture keytool timed out"); }
            if (process.exitValue() != 0) throw new IllegalStateException("fixture keytool failed");
        }
        @Override public void close() {
            releaseStall.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
