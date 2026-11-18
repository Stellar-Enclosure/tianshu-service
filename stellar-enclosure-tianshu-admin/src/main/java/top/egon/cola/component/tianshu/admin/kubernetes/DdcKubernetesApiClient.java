package top.egon.cola.component.tianshu.admin.kubernetes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Restricted Kubernetes infrastructure transport. Neither request tokens nor API error bodies are logged. */
@Slf4j
public class DdcKubernetesApiClient implements AutoCloseable {
    private static final int MAX_TOKEN_BYTES = 16_384;
    private final URI apiServer;
    private final Path apiServiceAccountTokenFile;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final int maxResponseBytes;
    private final HttpClient client;

    /** Value-based construction owns its HTTPS client; application wiring never supplies an arbitrary transport. */
    public DdcKubernetesApiClient(URI apiServer, Path caFile, Path apiServiceAccountTokenFile,
                                  ObjectMapper mapper, Duration timeout, int maxResponseBytes) {
        if (apiServer == null || !"https".equals(apiServer.getScheme()) || apiServer.getHost() == null
                || apiServer.getRawUserInfo() != null || apiServer.getRawQuery() != null
                || apiServer.getRawFragment() != null || apiServer.getPort() == 0 || apiServer.getPort() > 65_535
                || !(apiServer.getRawPath().isEmpty() || "/".equals(apiServer.getRawPath()))
                || timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0 || timeout.toMillis() < 1
                || maxResponseBytes < 1 || maxResponseBytes > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("CONFIG_KUBERNETES_TRANSPORT_INVALID");
        }
        this.apiServer = apiServer.resolve("/");
        this.apiServiceAccountTokenFile = Objects.requireNonNull(apiServiceAccountTokenFile, "apiServiceAccountTokenFile");
        this.mapper = Objects.requireNonNull(mapper, "mapper").copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
        try {
            var certificateFactory = CertificateFactory.getInstance("X.509");
            var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            try (var input = Files.newInputStream(Objects.requireNonNull(caFile, "caFile"))) {
                var certificates = certificateFactory.generateCertificates(input);
                if (certificates.isEmpty()) throw new IllegalArgumentException("CONFIG_KUBERNETES_CA_INVALID");
                int index = 0;
                for (var certificate : certificates) trustStore.setCertificateEntry("kubernetes-" + index++, certificate);
            }
            var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers.getTrustManagers(), null);
            var sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            this.client = HttpClient.newBuilder().sslContext(sslContext).sslParameters(sslParameters)
                    .connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
                    .proxy(new DirectProxySelector()).version(HttpClient.Version.HTTP_1_1).build();
        } catch (Exception invalidMaterial) {
            throw new IllegalStateException("CONFIG_KUBERNETES_CA_UNAVAILABLE");
        }
    }

    public JsonNode tokenReview(String projectedPodToken, String audience) {
        if (!validToken(projectedPodToken) || audience == null || audience.isBlank() || audience.length() > 512) {
            throw new IllegalArgumentException("CONFIG_WORKLOAD_PROOF_INVALID");
        }
        var request = mapper.createObjectNode();
        request.put("apiVersion", "authentication.k8s.io/v1").put("kind", "TokenReview");
        var spec = request.putObject("spec");
        spec.put("token", projectedPodToken);
        spec.putArray("audiences").add(audience);
        return exchange("POST", "/apis/authentication.k8s.io/v1/tokenreviews", request);
    }

    public JsonNode pod(String namespace, String name) {
        return exchange("GET", "/api/v1/namespaces/" + segment(namespace, 63) + "/pods/" + segment(name, 253), null);
    }

    public JsonNode replicaSet(String namespace, String name) {
        return exchange("GET", "/apis/apps/v1/namespaces/" + segment(namespace, 63) + "/replicasets/" + segment(name, 253), null);
    }

    public JsonNode deployment(String namespace, String name) {
        return exchange("GET", "/apis/apps/v1/namespaces/" + segment(namespace, 63) + "/deployments/" + segment(name, 253), null);
    }

    private JsonNode exchange(String method, String path, JsonNode body) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        BoundedBodySubscriber subscriber = new BoundedBodySubscriber(maxResponseBytes);
        try {
            // A projected API credential may rotate independently of the Pod proof under verification.
            String apiToken;
            try (var input = Files.newInputStream(apiServiceAccountTokenFile)) {
                byte[] bytes = input.readNBytes(MAX_TOKEN_BYTES + 1);
                if (bytes.length > MAX_TOKEN_BYTES) throw new Unavailable();
                apiToken = new String(bytes, StandardCharsets.US_ASCII).strip();
                if (!validToken(apiToken)) throw new Unavailable();
            }
            var builder = HttpRequest.newBuilder(apiServer.resolve(path)).timeout(timeout)
                    .header("Authorization", "Bearer " + apiToken).header("Accept", "application/json");
            if (body == null) builder.GET();
            else builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            pending = client.sendAsync(builder.build(), ignored -> subscriber);
            // Bounds the entire exchange, including a peer that sends headers then stalls its body.
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() == 404 && "GET".equals(method)) {
                var status = mapper.readTree(response.body());
                if (status != null && status.isObject() && "v1".equals(status.path("apiVersion").asText())
                        && "Status".equals(status.path("kind").asText())
                        && "Failure".equals(status.path("status").asText())
                        && "NotFound".equals(status.path("reason").asText())
                        && status.path("code").isIntegralNumber() && status.path("code").canConvertToInt()
                        && status.path("code").intValue() == 404) throw new NotFound();
                throw new Unavailable();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new Unavailable();
            var result = mapper.readTree(response.body());
            if (result == null || !result.isObject()) throw new Unavailable();
            return result;
        } catch (NotFound | Unavailable rejection) {
            throw rejection;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Unavailable();
        } catch (IOException | ExecutionException | TimeoutException | RuntimeException failure) {
            throw new Unavailable();
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            subscriber.cancel();
        }
    }

    private static boolean validToken(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_TOKEN_BYTES
                && value.matches("[A-Za-z0-9._~+/-]+=*");
    }

    private static String segment(String value, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
                || value.contains("..")) throw new IllegalArgumentException("CONFIG_KUBERNETES_NAME_INVALID");
        return value;
    }

    @Override
    @PreDestroy
    public void close() {
        client.shutdownNow();
    }

    public static final class Unavailable extends RuntimeException {
        public Unavailable() { super("CONFIG_KUBERNETES_UNAVAILABLE", null, false, false); }
    }

    public static final class NotFound extends RuntimeException {
        public NotFound() { super("CONFIG_KUBERNETES_OBJECT_ABSENT", null, false, false); }
    }

    private static final class DirectProxySelector extends java.net.ProxySelector {
        @Override public List<java.net.Proxy> select(URI uri) { return List.of(java.net.Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, java.net.SocketAddress address, IOException failure) { }
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private boolean cancelled;

        private BoundedBodySubscriber(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public synchronized void onSubscribe(Flow.Subscription value) {
            if (subscription != null || cancelled) { value.cancel(); return; }
            subscription = value;
            value.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> buffers) {
            if (cancelled || result.isDone()) return;
            long received = 0;
            for (var buffer : buffers) received += buffer.remaining();
            if (received > maximum - bytes.size()) { cancel(); result.completeExceptionally(new Unavailable()); return; }
            for (var buffer : buffers) {
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public synchronized void onError(Throwable failure) { result.completeExceptionally(new Unavailable()); }
        @Override public synchronized void onComplete() { result.complete(bytes.toByteArray()); }
        private synchronized void cancel() {
            cancelled = true;
            if (subscription != null) subscription.cancel();
        }
    }
}
