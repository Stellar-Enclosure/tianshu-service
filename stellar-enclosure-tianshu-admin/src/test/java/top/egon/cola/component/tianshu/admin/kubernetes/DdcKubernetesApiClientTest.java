package top.egon.cola.component.tianshu.admin.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.*;

class DdcKubernetesApiClientTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exposesRoleScopedPublicationAndCancellableObservationContract() throws Exception {
        Class<?> scope = Class.forName(DdcKubernetesApiClient.class.getName() + "$ScopeVO");
        assertThat(DdcKubernetesApiClient.class.getConstructor(URI.class, Path.class, Path.class,
                ObjectMapper.class, Duration.class, int.class, scope)).isNotNull();
        assertThat(DdcKubernetesApiClient.class.getDeclaredMethods()).extracting(java.lang.reflect.Method::getName)
                .contains("getConfigMap", "createImmutableConfigMap", "getDeployment", "patchDeploymentBaseline", "list", "watch");
        Class<?> watch = Class.forName(DdcKubernetesApiClient.class.getName() + "$WatchHandle");
        assertThat(AutoCloseable.class.isAssignableFrom(watch)).isTrue();
        assertThat(watch.getMethod("completion")).isNotNull();
    }

    @Test
    void existingVerifierConstructorStillUsesRealPinnedHttpsAndRotatedToken() throws Exception {
        try (var fixture = new KubernetesFixture(directory);
             var client = new DdcKubernetesApiClient(fixture.uri(), fixture.ca, fixture.token,
                     mapper, Duration.ofSeconds(2), 65_536)) {
            client.tokenReview("caller.proof", "urn:tianshu:config");
            Files.writeString(fixture.token, "api-rotated\n");
            client.tokenReview("caller.proof", "urn:tianshu:config");
            assertThat(fixture.headers).containsExactly("Bearer api-first", "Bearer api-rotated");
        }
    }

    @Test
    void immutableScopesKeepVerifierPublisherAndObserverAuthoritySeparate() throws Exception {
        var target = new DeploymentKey("apps-prod", "orders");
        var scope = new ScopeVO(Mode.PUBLISHER, Set.of("apps-prod"), Set.of(target));
        assertThatThrownBy(() -> scope.namespaces().add("other")).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new ScopeVO(Mode.OBSERVER, Set.of("apps-prod"), Set.of(target)));
        assertThatIllegalArgumentException().isThrownBy(() -> new ScopeVO(Mode.PUBLISHER, Set.of("other"), Set.of(target)));
        assertThatIllegalArgumentException().isThrownBy(() -> new DeploymentKey("../other", "orders"));
        try (var fixture = new KubernetesFixture(directory);
             var publisher = client(fixture, Mode.PUBLISHER);
             var observer = client(fixture, Mode.OBSERVER);
             var verifier = new DdcKubernetesApiClient(fixture.uri(), fixture.ca, fixture.token, mapper, Duration.ofSeconds(2), 65_536)) {
            assertThat(publisher.scope()).isEqualTo(scope);
            assertThat(verifier.scope()).isNull();
            failure(() -> publisher.tokenReview("caller.proof", "urn:audience"), FailureKind.FORBIDDEN);
            failure(() -> observer.pod("apps-prod", "orders-1"), FailureKind.FORBIDDEN);
            failure(() -> publisher.list(ResourceKind.POD, "apps-prod", null, 10), FailureKind.FORBIDDEN);
            failure(() -> observer.getDeployment(target), FailureKind.FORBIDDEN);
            failure(() -> publisher.getDeployment(new DeploymentKey("apps-prod", "other")), FailureKind.FORBIDDEN);
            failure(() -> observer.list(ResourceKind.POD, "other", null, 10), FailureKind.FORBIDDEN);
            failure(() -> verifier.getConfigMap("apps-prod", baselineName("{}")), FailureKind.FORBIDDEN);
            assertThat(fixture.requests).isEmpty();
        }
    }

    @Test
    void createsOnlyExactImmutableBaselineAndReportsConflictWithoutReplaying() throws Exception {
        String baseline = "{\"schemaVersion\":\"1\"}";
        String name = baselineName(baseline);
        try (var fixture = new KubernetesFixture(directory);
             var publisher = client(fixture, Mode.PUBLISHER)) {
            fixture.responder = exchange -> {
                if ("POST".equals(exchange.getRequestMethod())) {
                    JsonNode request = mapper.readTree(fixture.last().body());
                    assertThat(request.path("immutable").booleanValue()).isTrue();
                    assertThat(request.path("metadata").size()).isEqualTo(2);
                    assertThat(request.path("data").size()).isEqualTo(1);
                    assertThat(request.path("data").path("baseline.json").textValue()).isEqualTo(baseline);
                    assertThat(request.has("binaryData")).isFalse();
                    var result = request.deepCopy();
                    ((com.fasterxml.jackson.databind.node.ObjectNode) result.path("metadata")).put("uid", "cm-uid").put("resourceVersion", "9");
                    fixture.respond(exchange, 201, result.toString());
                } else fixture.respond(exchange, 404, status(404, "NotFound"));
            };
            assertThat(publisher.getConfigMap("apps-prod", name)).isEmpty();
            var created = publisher.createImmutableConfigMap("apps-prod", name, baseline);
            assertThat(created.uid()).isEqualTo("cm-uid");
            created.document().path("metadata").deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) created.document()).put("kind", "changed");
            assertThat(created.document().path("kind").asText()).isEqualTo("ConfigMap");
            assertThatIllegalArgumentException().isThrownBy(() -> publisher.createImmutableConfigMap("apps-prod", baselineName("{}"), baseline));
            fixture.responder = exchange -> fixture.respond(exchange, 409, status(409, "AlreadyExists"));
            failure(() -> publisher.createImmutableConfigMap("apps-prod", name, baseline), FailureKind.CONFLICT);
            assertThat(fixture.requests.stream().filter(request -> request.method().equals("POST"))).hasSize(2);
        }
    }

    @Test
    void deploymentPatchTestsUidAndVersionAndTouchesOnlyOwnedVolumeAndAnnotations() throws Exception {
        var target = new DeploymentKey("apps-prod", "orders");
        String name = baselineName("{}");
        try (var fixture = new KubernetesFixture(directory);
             var publisher = client(fixture, Mode.PUBLISHER)) {
            fixture.responder = exchange -> fixture.respond(exchange, 200, deployment("deployment-uid", "10", "old-config", "1"));
            var current = publisher.getDeployment(target);
            failure(() -> publisher.patchDeploymentBaseline(target, "different-uid", current.resourceVersion(), name, annotations("2")), FailureKind.CONFLICT);
            failure(() -> publisher.patchDeploymentBaseline(target, current.uid(), "old-rv", name, annotations("2")), FailureKind.CONFLICT);
            assertThatIllegalArgumentException().isThrownBy(() -> publisher.patchDeploymentBaseline(target,
                    current.uid(), current.resourceVersion(), name, annotations("0")));
            fixture.responder = exchange -> fixture.respond(exchange, 200, deployment("deployment-uid", "10", "old-config", "3"));
            failure(() -> publisher.patchDeploymentBaseline(target, current.uid(), current.resourceVersion(), name, annotations("2")), FailureKind.CONFLICT);
            assertThat(fixture.requests).noneMatch(request -> request.method().equals("PATCH"));
            fixture.responder = exchange -> {
                if ("PATCH".equals(exchange.getRequestMethod())) {
                    JsonNode patch = mapper.readTree(fixture.last().body());
                    assertThat(fixture.last().contentType()).isEqualTo("application/json-patch+json");
                    assertThat(patch.get(0).path("op").asText()).isEqualTo("test");
                    assertThat(patch.get(0).path("path").asText()).isEqualTo("/metadata/uid");
                    assertThat(patch.get(0).path("value").asText()).isEqualTo("deployment-uid");
                    assertThat(patch.get(1).path("path").asText()).isEqualTo("/metadata/resourceVersion");
                    assertThat(patch.get(1).path("value").asText()).isEqualTo("10");
                    assertThat(patch.get(2).path("path").asText()).isEqualTo("/spec/template/spec/volumes/1/configMap/name");
                    assertThat(patch.size()).isEqualTo(6);
                    for (int i = 3; i < patch.size(); i++) {
                        assertThat(patch.get(i).path("path").asText()).startsWith("/spec/template/metadata/annotations/stellar.io~1config-");
                    }
                    assertThat(patch.toString()).doesNotContain("replicas", "containers", "gitops.example", "foreign-volume");
                    fixture.respond(exchange, 200, deployment("deployment-uid", "11", name, "2"));
                } else fixture.respond(exchange, 200, deployment("deployment-uid", "10", "old-config", "1"));
            };
            assertThat(publisher.patchDeploymentBaseline(target, current.uid(), current.resourceVersion(), name, annotations("2")).resourceVersion()).isEqualTo("11");
            fixture.responder = exchange -> fixture.respond(exchange, 200, deployment("deployment-uid", "11", name, "2"));
            int calls = fixture.requests.size();
            publisher.patchDeploymentBaseline(target, current.uid(), "11", name, annotations("2"));
            assertThat(fixture.requests).hasSize(calls + 1);
        }
    }

    @Test
    void boundedListPreservesOpaquePaginationAndReturnsDefensiveResources() throws Exception {
        try (var fixture = new KubernetesFixture(directory);
             var observer = client(fixture, Mode.OBSERVER)) {
            fixture.responder = exchange -> fixture.respond(exchange, 200,
                    "{\"apiVersion\":\"v1\",\"kind\":\"PodList\",\"metadata\":{\"resourceVersion\":\"rv-7\",\"continue\":\"a+/=&?\"},\"items\":[" + pod("pod-1", "rv-7") + "]}");
            var page = observer.list(ResourceKind.POD, "apps-prod", "a+/=&?", 10);
            assertThat(fixture.last().rawQuery()).isEqualTo("limit=10&continue=a%2B%2F%3D%26%3F");
            assertThat(page.resourceVersion()).isEqualTo("rv-7");
            assertThat(page.continuation()).isEqualTo("a+/=&?");
            ((com.fasterxml.jackson.databind.node.ObjectNode) page.items().getFirst()).put("kind", "changed");
            assertThat(page.items().getFirst().path("kind").asText()).isEqualTo("Pod");
            assertThatThrownBy(() -> page.items().add(mapper.createObjectNode())).isInstanceOf(UnsupportedOperationException.class);
            assertThatIllegalArgumentException().isThrownBy(() -> observer.list(ResourceKind.POD, "apps-prod", null, 501));
            fixture.responder = exchange -> fixture.respond(exchange, 200,
                    "{\"apiVersion\":\"v1\",\"kind\":\"PodList\",\"metadata\":{\"resourceVersion\":\"8\"},\"items\":[" + pod("pod-1", "8").replace("apps-prod", "other") + "]}");
            failure(() -> observer.list(ResourceKind.POD, "apps-prod", null, 10), FailureKind.INVALID_RESPONSE);
        }
    }

    @Test
    void classifiesRealApiFailuresWithoutRedirectingOrLeakingResponseBodies() throws Exception {
        try (var fixture = new KubernetesFixture(directory);
             var publisher = client(fixture, Mode.PUBLISHER)) {
            for (var entry : Map.of(401, FailureKind.UNAUTHENTICATED, 403, FailureKind.FORBIDDEN,
                    409, FailureKind.CONFLICT, 410, FailureKind.GONE, 429, FailureKind.THROTTLED, 503, FailureKind.UNAVAILABLE).entrySet()) {
                fixture.responder = exchange -> fixture.respond(exchange, entry.getKey(), "sensitive api body");
                failure(() -> publisher.getDeployment(new DeploymentKey("apps-prod", "orders")), entry.getValue());
            }
            fixture.responder = exchange -> fixture.respond(exchange, 404, "{\"not\":\"kubernetes\"}");
            failure(() -> publisher.getConfigMap("apps-prod", baselineName("{}")), FailureKind.INVALID_RESPONSE);
            fixture.responder = exchange -> {
                exchange.getResponseHeaders().add("Location", fixture.uri() + "/stolen");
                fixture.respond(exchange, 302, "redirect");
            };
            int calls = fixture.requests.size();
            failure(() -> publisher.getDeployment(new DeploymentKey("apps-prod", "orders")), FailureKind.UNAVAILABLE);
            assertThat(fixture.requests).hasSize(calls + 1);
            try (var wrongHost = new DdcKubernetesApiClient(URI.create("https://localhost:" + fixture.server.getAddress().getPort()),
                    fixture.ca, fixture.token, mapper, Duration.ofSeconds(2), 65_536, publisher.scope())) {
                failure(() -> wrongHost.getDeployment(new DeploymentKey("apps-prod", "orders")), FailureKind.UNAVAILABLE);
            }
        }
    }

    @Test
    void watchStreamsOrderedEventsRereadsCredentialsAndReportsGoneForRelist() throws Exception {
        try (var fixture = new KubernetesFixture(directory);
             var observer = client(fixture, Mode.OBSERVER)) {
            fixture.responder = exchange -> fixture.respond(exchange, 200,
                    "{\"type\":\"ADDED\",\"object\":" + pod("pod-1", "11") + "}\n"
                            + "{\"type\":\"BOOKMARK\",\"object\":{\"apiVersion\":\"v1\",\"kind\":\"Pod\",\"metadata\":{\"resourceVersion\":\"12\"}}}\n");
            var events = new CopyOnWriteArrayList<WatchEvent>();
            observer.watch(ResourceKind.POD, "apps-prod", "10", Duration.ofSeconds(2), events::add)
                    .completion().toCompletableFuture().get(4, TimeUnit.SECONDS);
            assertThat(events).extracting(WatchEvent::type).containsExactly(WatchEventType.ADDED, WatchEventType.BOOKMARK);
            assertThat(events).extracting(WatchEvent::resourceVersion).containsExactly("11", "12");
            assertThat(fixture.last().rawQuery()).contains("watch=true", "resourceVersion=10", "timeoutSeconds=2");
            ((com.fasterxml.jackson.databind.node.ObjectNode) events.getFirst().object()).put("kind", "changed");
            assertThat(events.getFirst().object().path("kind").asText()).isEqualTo("Pod");
            Files.writeString(fixture.token, "api-rotated");
            fixture.responder = exchange -> fixture.respond(exchange, 200, "{\"type\":\"ERROR\",\"object\":" + status(410, "Expired") + "}\n");
            var expired = observer.watch(ResourceKind.POD, "apps-prod", "12", Duration.ofSeconds(2), events::add);
            assertThatThrownBy(() -> expired.completion().toCompletableFuture().get(4, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(ExecutionException.class, failure -> assertThat(failure.getCause())
                            .isInstanceOfSatisfying(Failure.class, error -> assertThat(error.kind()).isEqualTo(FailureKind.GONE)));
            assertThat(events).hasSize(2);
            assertThat(fixture.headers).containsExactly("Bearer api-first", "Bearer api-rotated");
        }
    }

    @Test
    void watchCloseAndClientCloseCancelStalledBodiesAndPreventNewRequests() throws Exception {
        try (var fixture = new KubernetesFixture(directory)) {
            var started = new CountDownLatch(1);
            fixture.responder = exchange -> {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                started.countDown();
                fixture.release.await(5, TimeUnit.SECONDS);
            };
            var observer = client(fixture, Mode.OBSERVER);
            try {
                var watch = observer.watch(ResourceKind.POD, "apps-prod", "1", Duration.ofSeconds(2), event -> {});
                assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
                failure(() -> observer.watch(ResourceKind.POD, "apps-prod", "1", Duration.ofSeconds(2), event -> {}), FailureKind.CONFLICT);
                watch.close();
                watch.close();
                watch.completion().toCompletableFuture().get(1, TimeUnit.SECONDS);
                var next = observer.watch(ResourceKind.POD, "apps-prod", "1", Duration.ofSeconds(2), event -> {});
                observer.close();
                next.completion().toCompletableFuture().get(1, TimeUnit.SECONDS);
                failure(() -> observer.list(ResourceKind.POD, "apps-prod", null, 1), FailureKind.UNAVAILABLE);
            } finally { observer.close(); }
        }
    }

    @Test
    void watchHttpGoneAndMalformedOrOversizedEventsRemainDistinctFromAuthoritativeAbsence() throws Exception {
        try (var fixture = new KubernetesFixture(directory);
             var observer = client(fixture, Mode.OBSERVER)) {
            fixture.responder = exchange -> fixture.respond(exchange, 410, status(410, "Expired"));
            watchFailure(observer, FailureKind.GONE);
            fixture.responder = exchange -> fixture.respond(exchange, 403, status(403, "Forbidden"));
            watchFailure(observer, FailureKind.FORBIDDEN);
            fixture.responder = exchange -> fixture.respond(exchange, 200,
                    "{\"type\":\"ADDED\",\"type\":\"DELETED\",\"object\":" + pod("p1", "1") + "}\n");
            watchFailure(observer, FailureKind.INVALID_RESPONSE);
            fixture.responder = exchange -> fixture.respond(exchange, 200, "x".repeat(65_537) + "\n");
            watchFailure(observer, FailureKind.INVALID_RESPONSE);
        }
    }

    private void watchFailure(DdcKubernetesApiClient observer, FailureKind expected) {
        var watch = observer.watch(ResourceKind.POD, "apps-prod", "1", Duration.ofSeconds(2), event -> {
            throw new AssertionError("invalid stream must not emit events");
        });
        assertThatThrownBy(() -> watch.completion().toCompletableFuture().get(4, TimeUnit.SECONDS))
                .isInstanceOfSatisfying(ExecutionException.class, failure -> assertThat(failure.getCause())
                        .isInstanceOfSatisfying(Failure.class, error -> assertThat(error.kind()).isEqualTo(expected)));
    }

    private DdcKubernetesApiClient client(KubernetesFixture fixture, Mode mode) {
        return new DdcKubernetesApiClient(fixture.uri(), fixture.ca, fixture.token, mapper, Duration.ofSeconds(2), 65_536,
                new ScopeVO(mode, Set.of("apps-prod"), mode == Mode.PUBLISHER ? Set.of(new DeploymentKey("apps-prod", "orders")) : Set.of()));
    }

    private static String baselineName(String value) throws Exception {
        return "tianshu-baseline-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> annotations(String revision) {
        return Map.of(REVISION_ANNOTATION, revision, CONTENT_ANNOTATION, "a".repeat(64), RESTART_ANNOTATION, "b".repeat(64));
    }

    private static String deployment(String uid, String rv, String configMap, String revision) {
        return "{\"apiVersion\":\"apps/v1\",\"kind\":\"Deployment\",\"metadata\":{\"name\":\"orders\",\"namespace\":\"apps-prod\",\"uid\":\"" + uid + "\",\"resourceVersion\":\"" + rv
                + "\",\"generation\":2},\"spec\":{\"replicas\":0,\"template\":{\"metadata\":{\"annotations\":{\"gitops.example/owner\":\"gitops\",\"" + REVISION_ANNOTATION + "\":\"" + revision
                + "\",\"" + CONTENT_ANNOTATION + "\":\"" + "a".repeat(64) + "\",\"" + RESTART_ANNOTATION + "\":\"" + "b".repeat(64)
                + "\"}},\"spec\":{\"containers\":[{\"name\":\"app\",\"image\":\"example:1\"}],\"volumes\":[{\"name\":\"foreign-volume\",\"emptyDir\":{}},{\"name\":\"tianshu-baseline\",\"configMap\":{\"name\":\"" + configMap + "\"}}]}}}}";
    }

    private static String pod(String uid, String rv) {
        return "{\"apiVersion\":\"v1\",\"kind\":\"Pod\",\"metadata\":{\"namespace\":\"apps-prod\",\"name\":\"orders-1\",\"uid\":\"" + uid + "\",\"resourceVersion\":\"" + rv + "\"}}";
    }

    private static String status(int code, String reason) {
        return "{\"apiVersion\":\"v1\",\"kind\":\"Status\",\"status\":\"Failure\",\"reason\":\"" + reason + "\",\"code\":" + code + "}";
    }

    private static void failure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, FailureKind expected) {
        assertThatThrownBy(action).isInstanceOfSatisfying(Failure.class, error -> {
            assertThat(error.kind()).isEqualTo(expected);
            assertThat(error.getCause()).isNull();
            assertThat(error.getMessage()).doesNotContain("sensitive", "api-first", "caller.proof");
        });
    }

    private static final class KubernetesFixture implements AutoCloseable {
        private final HttpsServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        private final Path ca;
        private final Path token;
        private final List<String> headers = new CopyOnWriteArrayList<>();
        private final List<RequestVO> requests = new CopyOnWriteArrayList<>();
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile Responder responder = exchange -> respond(exchange, 200,
                "{\"apiVersion\":\"authentication.k8s.io/v1\",\"kind\":\"TokenReview\",\"status\":{\"authenticated\":true}}");

        private KubernetesFixture(Path directory) throws Exception {
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
            server.createContext("/", this::handle);
            server.start();
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                headers.add(exchange.getRequestHeaders().getFirst("Authorization"));
                requests.add(new RequestVO(exchange.getRequestMethod(), exchange.getRequestURI().getRawQuery(),
                        exchange.getRequestHeaders().getFirst("Content-Type"), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                try { responder.respond(exchange); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                catch (java.io.IOException cancelled) { /* A cancelled streaming test closes its socket. */ }
                catch (Exception failed) { throw new java.io.IOException("fixture response failed", failed); }
            }
        }

        private RequestVO last() { return requests.getLast(); }
        private void respond(HttpExchange exchange, int code, String value) throws java.io.IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        @FunctionalInterface private interface Responder { void respond(HttpExchange exchange) throws Exception; }
        private record RequestVO(String method, String rawQuery, String contentType, String body) {}

        private URI uri() { return URI.create("https://127.0.0.1:" + server.getAddress().getPort()); }
        private static void command(String... command) throws Exception {
            var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("fixture keytool timed out");
            }
            if (process.exitValue() != 0) throw new IllegalStateException("fixture keytool failed");
        }

        @Override public void close() {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
