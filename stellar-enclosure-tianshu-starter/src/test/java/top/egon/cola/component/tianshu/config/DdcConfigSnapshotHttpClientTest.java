package top.egon.cola.component.tianshu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient.DeliveryFailure;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient.Kind;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcConfigSnapshotHttpClientTest {
    private static final String ISSUER = "https://identity.example/tenant";
    private static final URI SERVICE = URI.create("http://tianshu-config.platform-system.svc.cluster.local");
    private static final URI RESOURCE = URI.create("https://config.example/config");
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final IdpServiceTokenRequest READ = request(Set.of("tianshu:config:read"));
    private static final IdpServiceTokenRequest REPORT = request(Set.of("tianshu:config:receipt:write"));
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private IdpServiceOAuth2Client oauth;
    @TempDir Path directory;

    @BeforeEach
    void authorization() {
        oauth = mock(IdpServiceOAuth2Client.class);
        when(oauth.authorizeBounded(eq(READ), eq(ISSUER), any(Duration.class))).thenReturn(token("read-secret", READ));
        when(oauth.authorizeBounded(eq(REPORT), eq(ISSUER), any(Duration.class))).thenReturn(token("report-secret", REPORT));
    }

    @Test
    void conditionalSnapshotUsesTheDedicatedFixedTransportClient() throws Exception {
        assertThat(DdcConfigSnapshotHttpClient.class.getMethod("snapshot", String.class, String.class).getReturnType().getSimpleName())
                .isEqualTo("SnapshotResultVO");
    }

    @Test
    void newBootGetsCompleteSnapshotAndSameBootConditionalReadRenewsOnlyTransportProof() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            var content = snapshot();
            f.responses.add(ok(content));
            var first = client.snapshot("boot-1", null);
            assertThat(first.snapshot().getScope().getTenantId()).isEqualTo("7");
            assertThat(first.containerId()).isEqualTo("containerd://a1");
            assertThat(first.deliveryToken()).isEqualTo("opaque.delivery.token");
            assertThat(first.toString()).doesNotContain("opaque.delivery.token", "sample.label");
            var renewedHeaders = new LinkedHashMap<>(headers(content));
            var renewed = content.getValidUntil().plusSeconds(60);
            renewedHeaders.put("X-Config-Valid-Until", List.of(renewed.toString()));
            renewedHeaders.put("X-Config-Delivery-Token", List.of("replacement.opaque.proof"));
            f.responses.add(new ResponseVO(304, renewedHeaders, new byte[0], false));
            var same = client.snapshot("boot-1", first.etag());
            assertThat(same.snapshot()).isNull();
            assertThat(same.etag()).isEqualTo(first.etag());
            assertThat(same.validUntil()).isEqualTo(renewed);
            assertThat(same.deliveryToken()).isEqualTo("replacement.opaque.proof");
            assertThat(f.received).hasSize(2);
            assertThat(f.received.get(0).authorization()).isEqualTo("Bearer read-secret");
            assertThat(f.received.get(0).workload()).isEqualTo("pod-proof-a");
            assertThat(f.received.get(0).etag()).isNull();
            assertThat(f.received.get(1).etag()).isEqualTo(first.etag());
            verify(oauth, times(2)).authorizeBounded(eq(READ), eq(ISSUER), any(Duration.class));
            verify(oauth, never()).authorizeBounded(eq(REPORT), eq(ISSUER), any(Duration.class));
        }
    }

    @Test
    void one401ReauthenticatesAndRereadsRotatedProjectedTokenExactlyOnce() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            when(oauth.authorizeBounded(eq(READ), eq(ISSUER), any(Duration.class)))
                    .thenReturn(token("read-old", READ), token("read-new", READ));
            f.rotateProofOn401 = true;
            f.responses.add(error(401));
            f.responses.add(ok(snapshot()));
            client.snapshot("boot-1", null);
            assertThat(f.received).extracting(ReceivedVO::authorization).containsExactly("Bearer read-old", "Bearer read-new");
            assertThat(f.received).extracting(ReceivedVO::workload).containsExactly("pod-proof-a", "pod-proof-b");
            verify(oauth).invalidateBounded(eq(READ), eq(ISSUER), any(Duration.class));
        }
    }

    @Test
    void second401StopsWithoutAThirdWireCall() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            f.responses.add(error(401));
            f.responses.add(error(401));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.UNAUTHENTICATED);
            assertThat(f.received).hasSize(2);
            verify(oauth).invalidateBounded(eq(READ), eq(ISSUER), any(Duration.class));
        }
    }

    @Test
    void authorizationNotFoundConflictAndTemporaryFailuresKeepDistinctKindsWithoutReplaying() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            for (var entry : Map.of(403, Kind.FORBIDDEN, 404, Kind.NOT_FOUND, 409, Kind.CONFLICT, 429, Kind.UNAVAILABLE, 503, Kind.UNAVAILABLE).entrySet()) {
                int before = f.received.size();
                f.responses.add(error(entry.getKey()));
                assertFailure(() -> client.snapshot("boot-1", null), entry.getValue());
                assertThat(f.received).hasSize(before + 1);
            }
            verify(oauth, never()).invalidateBounded(any(), any(), any());
        }
    }

    @Test
    void redirectIsRejectedAndCredentialsNeverReachTheOtherPath() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            f.responses.add(new ResponseVO(302, Map.of("Location", List.of(f.origin() + "/steal")), new byte[0], false));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.INVALID_RESPONSE);
            assertThat(f.received).singleElement().satisfies(request -> assertThat(request.path()).isEqualTo("/internal/v1/config/snapshot"));
        }
    }

    @Test
    void rejectsUnsolicited304MissingProofDuplicateHeadersAndWrongEtag() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            var content = snapshot();
            var etag = DdcConfigCanonicalJson.etag(content);
            f.responses.add(new ResponseVO(304, headers(content), new byte[0], false));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.INVALID_RESPONSE);
            var missing = new LinkedHashMap<>(headers(content));
            missing.remove("X-Config-Delivery-Token");
            f.responses.add(new ResponseVO(304, missing, new byte[0], false));
            assertFailure(() -> client.snapshot("boot-1", etag), Kind.INVALID_RESPONSE);
            var duplicate = new LinkedHashMap<>(headers(content));
            duplicate.put("X-Config-Container-Id", List.of("containerd://a1", "containerd://a2"));
            f.responses.add(new ResponseVO(304, duplicate, new byte[0], false));
            assertFailure(() -> client.snapshot("boot-1", etag), Kind.INVALID_RESPONSE);
            var mismatch = new LinkedHashMap<>(headers(content));
            mismatch.put("ETag", List.of("\"cfg-r3-" + "b".repeat(64) + "\""));
            f.responses.add(new ResponseVO(304, mismatch, new byte[0], false));
            assertFailure(() -> client.snapshot("boot-1", etag), Kind.INVALID_RESPONSE);
        }
    }

    @Test
    void bodyDigestEtagAndValidUntilMustAgreeAndJsonIsStrict() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            var content = snapshot();
            var bytes = mapper.writeValueAsBytes(content);
            var badEtag = new LinkedHashMap<>(headers(content));
            badEtag.put("ETag", List.of("\"cfg-r2-" + "0".repeat(64) + "\""));
            f.responses.add(new ResponseVO(200, badEtag, bytes, false));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.INVALID_RESPONSE);
            var expiry = new LinkedHashMap<>(headers(content));
            expiry.put("X-Config-Valid-Until", List.of(content.getValidUntil().plusSeconds(1).toString()));
            f.responses.add(new ResponseVO(200, expiry, bytes, false));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.INVALID_RESPONSE);
            var tampered = mapper.valueToTree(content);
            ((com.fasterxml.jackson.databind.node.ObjectNode) tampered.path("hotValues")).put("sample.label", "tampered");
            f.responses.add(new ResponseVO(200, headers(content), mapper.writeValueAsBytes(tampered), false));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.INVALID_RESPONSE);
            for (byte[] malformed : List.of("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8),
                    "{} {}".getBytes(StandardCharsets.UTF_8), new byte[]{'{', '"', (byte) 0xc3, '"', ':', '1', '}'})) {
                f.responses.add(new ResponseVO(200, headers(content), malformed, false));
                assertFailure(() -> client.snapshot("boot-1", null), Kind.INVALID_RESPONSE);
            }
        }
    }

    @Test
    void alreadyExpiredConfirmationIsNotAccepted() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            var content = snapshot();
            var expired = new LinkedHashMap<>(headers(content));
            expired.put("X-Config-Valid-Until", List.of(Instant.now().minusSeconds(1).toString()));
            f.responses.add(new ResponseVO(304, expired, new byte[0], false));
            assertFailure(() -> client.snapshot("boot-1", DdcConfigCanonicalJson.etag(content)), Kind.INVALID_RESPONSE);
        }
    }

    @Test
    void snapshotBodyCapAndHeadersThenStallAreBounded() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofMillis(700))) {
            f.responses.add(new ResponseVO(200, headers(snapshot()), new byte[1_048_577], false));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.INVALID_RESPONSE);
            f.responses.add(new ResponseVO(200, headers(snapshot()), new byte[0], true));
            long started = System.nanoTime();
            assertFailure(() -> client.snapshot("boot-1", null), Kind.UNAVAILABLE);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
            assertThat(f.stallStarted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void receiptUsesSeparateAuthorizationAndPreservesOpaqueRuntimeIdempotencyKey() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            String key = "pod-uid:containerd://a1:boot-1:2";
            client.report("boot-1", key, receipt());
            assertThat(f.received).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/internal/v1/config/receipts");
                assertThat(request.authorization()).isEqualTo("Bearer report-secret");
                assertThat(request.idempotency()).isEqualTo(key);
                assertThat(request.body()).contains("opaque.delivery.token", "\"reportSeq\":\"1\"");
            });
            verify(oauth).authorizeBounded(eq(REPORT), eq(ISSUER), any(Duration.class));
            verify(oauth, never()).authorizeBounded(eq(READ), eq(ISSUER), any(Duration.class));
        }
    }

    @Test
    void receipt401RetriesOnlyAfterReauthAndKeepsExactLogicalReportBytes() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            f.responses.add(error(401));
            f.responses.add(new ResponseVO(204, Map.of(), new byte[0], false));
            client.report("boot-1", "pod:containerd://a1:boot-1:2", receipt());
            assertThat(f.received).hasSize(2);
            assertThat(f.received.get(0).body()).isEqualTo(f.received.get(1).body());
            assertThat(f.received.get(0).idempotency()).isEqualTo(f.received.get(1).idempotency());
            verify(oauth).invalidateBounded(eq(REPORT), eq(ISSUER), any(Duration.class));
        }
    }

    @Test
    void receiptNeverReplays409Or503AndNeverAcceptsAnotherSuccessStatus() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            for (int status : List.of(409, 503, 200)) {
                int before = f.received.size();
                f.responses.add(error(status));
                assertFailure(() -> client.report("boot-1", "pod:containerd://a1:boot-1:2", receipt()),
                        status == 409 ? Kind.CONFLICT : status == 503 ? Kind.UNAVAILABLE : Kind.INVALID_RESPONSE);
                assertThat(f.received).hasSize(before + 1);
            }
        }
    }

    @Test
    void malformedLocalInputAndMissingProjectedFileNeverGoOnTheWire() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            clearInvocations(oauth);
            assertFailure(() -> client.snapshot("boot:invalid", null), Kind.INVALID_RESPONSE);
            assertFailure(() -> client.snapshot("boot-1", "*"), Kind.INVALID_RESPONSE);
            assertFailure(() -> client.report("boot-1", "key", receipt().setReceiptDigest("sha256:" + "0".repeat(64))), Kind.INVALID_RESPONSE);
            assertThat(f.received).isEmpty();
            verifyNoInteractions(oauth);
            Files.delete(f.proof);
            assertFailure(() -> client.snapshot("boot-1", null), Kind.UNAVAILABLE);
            assertThat(f.received).isEmpty();
        }
    }

    @Test
    void oauthAuthorityFailureDoesNotBecomePermanentRevocationAndIsSanitized() throws Exception {
        try (var f = new Fixture(); var client = client(f, Duration.ofSeconds(2))) {
            when(oauth.authorizeBounded(eq(READ), eq(ISSUER), any(Duration.class)))
                    .thenThrow(new IdpServiceOAuth2Client.IdpServiceAuthorizationException("authorization_timeout", new IllegalStateException("SENSITIVE-RAW")));
            assertFailure(() -> client.snapshot("boot-1", null), Kind.UNAVAILABLE);
            assertThat(f.received).isEmpty();
        }
    }

    @Test
    void productionConstructorRejectsLoopbackUnsafeOriginAndCrossOperationAuthorization() {
        Path proof = directory.resolve("proof");
        for (String uri : List.of("http://127.0.0.1:18080", "https://tianshu-config.platform-system.svc.cluster.local", "http://evil.example",
                "http://tianshu-config.platform-system.svc.cluster.local/path", "http://user@tianshu-config.platform-system.svc.cluster.local")) {
            assertThatIllegalArgumentException().isThrownBy(() -> new DdcConfigSnapshotHttpClient(URI.create(uri), proof, oauth, READ, REPORT, ISSUER, Duration.ofSeconds(1)));
        }
        var broad = request(Set.of("tianshu:config:read", "tianshu:config:receipt:write"));
        assertThatIllegalArgumentException().isThrownBy(() -> new DdcConfigSnapshotHttpClient(SERVICE, proof, oauth, broad, REPORT, ISSUER, Duration.ofSeconds(1)));
        var otherTenant = new IdpServiceTokenRequest("service", "orders-app", RESOURCE, ServiceTokenContext.TENANT, "other", REPORT.scopes());
        assertThatIllegalArgumentException().isThrownBy(() -> new DdcConfigSnapshotHttpClient(SERVICE, proof, oauth, READ, otherTenant, ISSUER, Duration.ofSeconds(1)));
    }

    private static void assertFailure(Runnable action, Kind kind) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DeliveryFailure.class, failure -> {
            assertThat(failure.kind()).isEqualTo(kind);
            assertThat(failure.getMessage()).isEqualTo(failure.errorCode()).doesNotContain("SENSITIVE-RAW", "read-secret", "report-secret", "pod-proof");
            assertThat(failure.getCause()).isNull();
        });
    }

    private DdcConfigSnapshotHttpClient client(Fixture fixture, Duration timeout) throws Exception {
        var constructor = DdcConfigSnapshotHttpClient.class.getDeclaredConstructor(URI.class, Path.class, IdpServiceOAuth2Client.class,
                IdpServiceTokenRequest.class, IdpServiceTokenRequest.class, String.class, Duration.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(fixture.origin(), fixture.proof, oauth, READ, REPORT, ISSUER, timeout, true);
    }
    private static IdpServiceTokenRequest request(Set<String> scopes) {
        return new IdpServiceTokenRequest("service", "orders-app", RESOURCE, ServiceTokenContext.TENANT, "identity-tenant", scopes);
    }
    private static OAuth2AccessToken token(String value, IdpServiceTokenRequest request) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, Instant.now().minusSeconds(1), Instant.now().plusSeconds(120), request.scopes());
    }
    private DdcConfigSnapshotDTO snapshot() {
        var scope = new DdcConfigScopeVO("7", "trade", "prod", "orders", "application.yml");
        Map<String, JsonNode> values = Map.of("sample.label", JsonNodeFactory.instance.textNode("hot"));
        var until = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var provisional = new DdcConfigSnapshotDTO("1", scope, "2", true, DIGEST, DIGEST, values, null, until);
        return new DdcConfigSnapshotDTO("1", scope, "2", true, DIGEST, DdcConfigCanonicalJson.contentSha256(provisional), values, null, until);
    }
    private DdcConfigReceiptCommand receipt() {
        var now = Instant.now();
        var receipt = DdcConfigReceiptCommand.builder().schemaVersion("1").bootId("boot-1").releaseRevision("2").reportSeq("1")
                .appliedRevision("2").contentSha256(DIGEST).appliedContentSha256(DIGEST).baselineRestartDigest(DIGEST)
                .receiptDigest(DIGEST).deliveryToken("opaque.delivery.token").status(DdcConfigReceiptStatusEnum.APPLIED)
                .reportedAt(now).appliedAt(now).build();
        return receipt.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(receipt));
    }
    private Map<String, List<String>> headers(DdcConfigSnapshotDTO snapshot) {
        return Map.of("Content-Type", List.of("application/json"), "ETag", List.of(DdcConfigCanonicalJson.etag(snapshot)),
                "X-Config-Delivery-Token", List.of("opaque.delivery.token"), "X-Config-Container-Id", List.of("containerd://a1"),
                "X-Config-Valid-Until", List.of(snapshot.getValidUntil().toString()));
    }
    private ResponseVO ok(DdcConfigSnapshotDTO snapshot) throws Exception { return new ResponseVO(200, headers(snapshot), mapper.writeValueAsBytes(snapshot), false); }
    private static ResponseVO error(int status) {
        return new ResponseVO(status, Map.of("Content-Type", List.of("application/json")),
                "{\"errorCode\":\"SAFE_FAILURE\",\"secret\":\"SENSITIVE-RAW\"}".getBytes(StandardCharsets.UTF_8), false);
    }
    private record ResponseVO(int status, Map<String, List<String>> headers, byte[] body, boolean stall) { }
    private record ReceivedVO(String method, String path, String authorization, String workload, String boot,
                              String etag, String idempotency, String body) { }
    private final class Fixture implements AutoCloseable {
        private final Path proof = directory.resolve("workload-token");
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(4);
        private final ConcurrentLinkedQueue<ResponseVO> responses = new ConcurrentLinkedQueue<>();
        private final List<ReceivedVO> received = new CopyOnWriteArrayList<>();
        private final CountDownLatch stallStarted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean rotateProofOn401;
        private Fixture() throws Exception {
            Files.writeString(proof, "pod-proof-a\n");
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    var request = exchange.getRequestHeaders();
                    received.add(new ReceivedVO(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), request.getFirst("Authorization"),
                            request.getFirst("X-Egon-Workload-Token"), request.getFirst("X-Config-Boot-Id"), request.getFirst("If-None-Match"),
                            request.getFirst("Idempotency-Key"), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    var response = responses.poll();
                    if (response == null) response = new ResponseVO(204, Map.of(), new byte[0], false);
                    if (response.status() == 401 && rotateProofOn401) Files.writeString(proof, "pod-proof-b");
                    response.headers().forEach((name, values) -> values.forEach(value -> exchange.getResponseHeaders().add(name, value)));
                    if (response.stall()) {
                        exchange.sendResponseHeaders(200, 0);
                        exchange.getResponseBody().write('{');
                        exchange.getResponseBody().flush();
                        stallStarted.countDown();
                        try { release.await(4, TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    } else {
                        exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
                        if (response.body().length != 0) exchange.getResponseBody().write(response.body());
                    }
                }
            });
            server.start();
        }
        private URI origin() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
        @Override public void close() { release.countDown(); server.stop(0); executor.shutdownNow(); }
    }
}
