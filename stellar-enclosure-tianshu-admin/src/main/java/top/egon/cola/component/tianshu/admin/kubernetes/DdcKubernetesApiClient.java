package top.egon.cola.component.tianshu.admin.kubernetes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
    @Nullable private final ScopeVO scope;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, WatchTask> watches = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchDeadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("tianshu-kubernetes-watch-deadline").factory());
    public static final String REVISION_ANNOTATION = "stellar.io/config-revision";
    public static final String CONTENT_ANNOTATION = "stellar.io/config-content-sha256";
    public static final String RESTART_ANNOTATION = "stellar.io/config-restart-digest";
    private static final Set<String> CONFIG_ANNOTATIONS = Set.of(REVISION_ANNOTATION, CONTENT_ANNOTATION, RESTART_ANNOTATION);

    /** Value-based construction owns its HTTPS client; application wiring never supplies an arbitrary transport. */
    public DdcKubernetesApiClient(URI apiServer, Path caFile, Path apiServiceAccountTokenFile,
                                  ObjectMapper mapper, Duration timeout, int maxResponseBytes) {
        this(apiServer, caFile, apiServiceAccountTokenFile, mapper, timeout, maxResponseBytes, null, true);
    }

    /** A role-scoped instance uses its own projected credential and can never perform TokenReview. */
    public DdcKubernetesApiClient(URI apiServer, Path caFile, Path apiServiceAccountTokenFile,
                                  ObjectMapper mapper, Duration timeout, int maxResponseBytes, ScopeVO scope) {
        this(apiServer, caFile, apiServiceAccountTokenFile, mapper, timeout, maxResponseBytes,
                Objects.requireNonNull(scope, "scope"), false);
    }

    private DdcKubernetesApiClient(URI apiServer, Path caFile, Path apiServiceAccountTokenFile,
                                  ObjectMapper mapper, Duration timeout, int maxResponseBytes,
                                  @Nullable ScopeVO scope, boolean verifier) {
        if (verifier != (scope == null)) throw new IllegalArgumentException("CONFIG_KUBERNETES_SCOPE_INVALID");
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
        this.scope = scope;
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
        requireVerifier();
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
        requireVerifier();
        return exchange("GET", "/api/v1/namespaces/" + segment(namespace, 63) + "/pods/" + segment(name, 253), null);
    }

    public JsonNode replicaSet(String namespace, String name) {
        requireVerifier();
        return exchange("GET", "/apis/apps/v1/namespaces/" + segment(namespace, 63) + "/replicasets/" + segment(name, 253), null);
    }

    public JsonNode deployment(String namespace, String name) {
        requireVerifier();
        return exchange("GET", "/apis/apps/v1/namespaces/" + segment(namespace, 63) + "/deployments/" + segment(name, 253), null);
    }

    @Nullable public ScopeVO scope() { return scope; }

    public Optional<ResourceSnapshot> getConfigMap(String namespace, String name) {
        requireRole(Mode.PUBLISHER, namespace);
        baselineName(name);
        try {
            return Optional.of(snapshot(scopedExchange("GET", configMapPath(namespace, name), null, "application/json"),
                    namespace, name, "v1", "ConfigMap", false));
        } catch (Failure failure) {
            if (failure.kind() == FailureKind.NOT_FOUND) return Optional.empty();
            throw failure;
        }
    }

    public ResourceSnapshot createImmutableConfigMap(String namespace, String name, String baselineJson) {
        requireRole(Mode.PUBLISHER, namespace);
        baselineName(name);
        if (baselineJson == null || baselineJson.isEmpty()
                || baselineJson.getBytes(StandardCharsets.UTF_8).length > 1_044_480
                || !name.equals("tianshu-baseline-" + sha256(baselineJson))) {
            throw new IllegalArgumentException("CONFIG_KUBERNETES_BASELINE_INVALID");
        }
        try {
            JsonNode parsed = mapper.readTree(baselineJson);
            if (parsed == null || !parsed.isObject()) throw new IllegalArgumentException("CONFIG_KUBERNETES_BASELINE_INVALID");
        } catch (IOException invalid) { throw new IllegalArgumentException("CONFIG_KUBERNETES_BASELINE_INVALID"); }
        var object = mapper.createObjectNode().put("apiVersion", "v1").put("kind", "ConfigMap").put("immutable", true);
        object.putObject("metadata").put("namespace", namespace).put("name", name);
        object.putObject("data").put("baseline.json", baselineJson);
        try {
            if (mapper.writeValueAsBytes(object).length > 1_048_576) throw new IllegalArgumentException("CONFIG_KUBERNETES_BASELINE_TOO_LARGE");
        } catch (IOException invalid) { throw new IllegalArgumentException("CONFIG_KUBERNETES_BASELINE_INVALID"); }
        return snapshot(scopedExchange("POST", "/api/v1/namespaces/" + namespace + "/configmaps", object, "application/json"),
                namespace, name, "v1", "ConfigMap", false);
    }

    public ResourceSnapshot getDeployment(DeploymentKey target) {
        requireDeployment(target);
        return snapshot(scopedExchange("GET", deploymentPath(target), null, "application/json"),
                target.namespace(), target.name(), "apps/v1", "Deployment", true);
    }

    /** Tests both object identity and version; no arbitrary JSON Patch or full workload object is accepted. */
    public ResourceSnapshot patchDeploymentBaseline(DeploymentKey target, String expectedUid, String expectedResourceVersion,
            String configMapName, Map<String, String> configAnnotations) {
        requireDeployment(target);
        wireToken(expectedUid, 128);
        wireToken(expectedResourceVersion, 256);
        baselineName(configMapName);
        if (configAnnotations == null || !CONFIG_ANNOTATIONS.equals(configAnnotations.keySet())) {
            throw new IllegalArgumentException("CONFIG_KUBERNETES_ANNOTATIONS_INVALID");
        }
        var annotations = Map.copyOf(configAnnotations);
        positiveDecimal(annotations.get(REVISION_ANNOTATION));
        if (!annotations.get(CONTENT_ANNOTATION).matches("[0-9a-f]{64}")
                || !annotations.get(RESTART_ANNOTATION).matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CONFIG_KUBERNETES_ANNOTATIONS_INVALID");
        }
        var current = getDeployment(target);
        if (!expectedUid.equals(current.uid()) || !expectedResourceVersion.equals(current.resourceVersion())) {
            throw new Failure(FailureKind.CONFLICT);
        }
        JsonNode template = current.document().path("spec").path("template");
        JsonNode volumes = template.path("spec").path("volumes");
        if (!template.path("metadata").isObject() || !volumes.isArray()) throw new Failure(FailureKind.INVALID_RESPONSE);
        int index = -1;
        for (int i = 0; i < volumes.size(); i++) {
            if ("tianshu-baseline".equals(volumes.get(i).path("name").asText())) {
                if (index >= 0 || !volumes.get(i).path("configMap").path("name").isTextual()) {
                    throw new Failure(FailureKind.INVALID_RESPONSE);
                }
                index = i;
            }
        }
        if (index < 0) throw new Failure(FailureKind.INVALID_RESPONSE);
        var previous = template.path("metadata").get("annotations");
        if (previous != null && !previous.isObject()) throw new Failure(FailureKind.INVALID_RESPONSE);
        if (previous != null && previous.has(REVISION_ANNOTATION)) {
            String revision = requiredText(previous, REVISION_ANNOTATION, 19);
            try {
                positiveDecimal(revision);
                if (Long.parseLong(revision) > Long.parseLong(annotations.get(REVISION_ANNOTATION))) {
                    throw new Failure(FailureKind.CONFLICT);
                }
            } catch (IllegalArgumentException malformed) { throw new Failure(FailureKind.INVALID_RESPONSE); }
        }
        boolean same = configMapName.equals(volumes.get(index).path("configMap").path("name").textValue())
                && previous != null && annotations.entrySet().stream()
                .allMatch(entry -> previous.path(entry.getKey()).isTextual() && entry.getValue().equals(previous.path(entry.getKey()).textValue()));
        if (same) return current;
        var patch = mapper.createArrayNode();
        patch.addObject().put("op", "test").put("path", "/metadata/uid").put("value", expectedUid);
        patch.addObject().put("op", "test").put("path", "/metadata/resourceVersion").put("value", expectedResourceVersion);
        patch.addObject().put("op", "replace").put("path", "/spec/template/spec/volumes/" + index + "/configMap/name").put("value", configMapName);
        if (previous == null) {
            patch.addObject().put("op", "add").put("path", "/spec/template/metadata/annotations").set("value", mapper.valueToTree(annotations));
        } else {
            annotations.forEach((key, value) -> patch.addObject().put("op", "add")
                    .put("path", "/spec/template/metadata/annotations/" + key.replace("~", "~0").replace("/", "~1"))
                    .put("value", value));
        }
        var updated = snapshot(scopedExchange("PATCH", deploymentPath(target) + "?fieldManager=tianshu-config-publisher",
                patch, "application/json-patch+json"), target.namespace(), target.name(), "apps/v1", "Deployment", true);
        if (!expectedUid.equals(updated.uid())) throw new Failure(FailureKind.INVALID_RESPONSE);
        return updated;
    }

    public ListPage list(ResourceKind kind, String namespace, @Nullable String continuation, int limit) {
        requireRole(Mode.OBSERVER, namespace);
        Objects.requireNonNull(kind, "kind");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("CONFIG_KUBERNETES_PAGE_LIMIT_INVALID");
        if (continuation != null) wireToken(continuation, 4096);
        String path = kind.path(namespace) + "?limit=" + limit
                + (continuation == null ? "" : "&continue=" + query(continuation));
        JsonNode object = scopedExchange("GET", path, null, "application/json");
        if (!kind.apiVersion.equals(object.path("apiVersion").asText())
                || !(kind.kind + "List").equals(object.path("kind").asText()) || !object.path("items").isArray()
                || object.path("items").size() > limit) throw new Failure(FailureKind.INVALID_RESPONSE);
        var metadata = object.path("metadata");
        String rv = requiredText(metadata, "resourceVersion", 256);
        JsonNode next = metadata.get("continue");
        if (next != null && !next.isTextual()) throw new Failure(FailureKind.INVALID_RESPONSE);
        String continuationValue = next == null || next.textValue().isEmpty() ? null : requiredText(metadata, "continue", 4096);
        var items = new java.util.ArrayList<JsonNode>();
        for (JsonNode item : object.path("items")) {
            validateResource(item, namespace, kind.apiVersion, kind.kind);
            items.add(item);
        }
        return new ListPage(rv, continuationValue, items);
    }

    public WatchHandle watch(ResourceKind kind, String namespace, String resourceVersion, Duration watchTimeout,
            Consumer<WatchEvent> consumer) {
        requireRole(Mode.OBSERVER, namespace);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(consumer, "consumer");
        wireToken(resourceVersion, 256);
        if (watchTimeout == null || watchTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || watchTimeout.compareTo(Duration.ofSeconds(30)) > 0 || watchTimeout.toNanosPart() != 0) {
            throw new IllegalArgumentException("CONFIG_KUBERNETES_WATCH_TIMEOUT_INVALID");
        }
        String key = kind.name() + ":" + namespace;
        var task = new WatchTask(key, kind, namespace, resourceVersion, watchTimeout, consumer);
        synchronized (watches) {
            if (closed.get()) throw new Failure(FailureKind.UNAVAILABLE);
            if (watches.putIfAbsent(key, task) != null) throw new Failure(FailureKind.CONFLICT);
            task.start();
        }
        return task;
    }

    private void requireVerifier() {
        if (scope != null) throw new Failure(FailureKind.FORBIDDEN);
        if (closed.get()) throw new Unavailable();
    }

    private void requireRole(Mode expected, String namespace) {
        namespace(namespace);
        if (scope == null || scope.mode() != expected || !scope.namespaces().contains(namespace)) {
            throw new Failure(FailureKind.FORBIDDEN);
        }
        if (closed.get()) throw new Failure(FailureKind.UNAVAILABLE);
    }

    private void requireDeployment(DeploymentKey target) {
        Objects.requireNonNull(target, "target");
        requireRole(Mode.PUBLISHER, target.namespace());
        if (!scope.deploymentTargets().contains(target)) throw new Failure(FailureKind.FORBIDDEN);
    }

    private String apiToken() throws IOException {
        try (var input = Files.newInputStream(apiServiceAccountTokenFile)) {
            byte[] bytes = input.readNBytes(MAX_TOKEN_BYTES + 1);
            try {
                if (bytes.length > MAX_TOKEN_BYTES) throw new Failure(FailureKind.UNAVAILABLE);
                String token = new String(bytes, StandardCharsets.US_ASCII).strip();
                if (!validToken(token)) throw new Failure(FailureKind.UNAVAILABLE);
                return token;
            } finally { java.util.Arrays.fill(bytes, (byte) 0); }
        }
    }

    private HttpRequest.Builder scopedRequest(String path, Duration requestTimeout) throws IOException {
        if (closed.get()) throw new Failure(FailureKind.UNAVAILABLE);
        return HttpRequest.newBuilder(apiServer.resolve(path)).timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiToken()).header("Accept", "application/json");
    }

    private JsonNode scopedExchange(String method, String path, @Nullable JsonNode body, String contentType) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        var subscriber = new BoundedBodySubscriber(maxResponseBytes);
        try {
            var builder = scopedRequest(path, timeout);
            if (body == null) builder.GET();
            else builder.header("Content-Type", contentType).method(method,
                    HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            pending = client.sendAsync(builder.build(), ignored -> subscriber);
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw httpFailure(response.statusCode(), response.body());
            }
            JsonNode result = mapper.readTree(response.body());
            if (result == null || !result.isObject()) throw new Failure(FailureKind.INVALID_RESPONSE);
            return result;
        } catch (Failure failure) { throw failure; }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new Failure(FailureKind.INVALID_RESPONSE); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure(FailureKind.UNAVAILABLE);
        } catch (IOException | ExecutionException | TimeoutException | RuntimeException unavailable) {
            throw new Failure(FailureKind.UNAVAILABLE);
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            subscriber.cancel();
        }
    }

    private Failure httpFailure(int status, byte[] body) {
        if (status == 404) {
            try {
                JsonNode result = mapper.readTree(body);
                if (isStatus(result, 404, "NotFound")) return new Failure(FailureKind.NOT_FOUND);
            } catch (IOException ignored) { /* A foreign/malformed 404 proves no Kubernetes absence. */ }
            return new Failure(FailureKind.INVALID_RESPONSE);
        }
        return new Failure(Map.of(401, FailureKind.UNAUTHENTICATED, 403, FailureKind.FORBIDDEN,
                409, FailureKind.CONFLICT, 410, FailureKind.GONE, 429, FailureKind.THROTTLED)
                .getOrDefault(status, FailureKind.UNAVAILABLE));
    }

    private static boolean isStatus(@Nullable JsonNode node, int code, String reason) {
        return node != null && node.isObject() && "v1".equals(node.path("apiVersion").asText())
                && "Status".equals(node.path("kind").asText()) && "Failure".equals(node.path("status").asText())
                && reason.equals(node.path("reason").asText()) && node.path("code").isIntegralNumber()
                && node.path("code").canConvertToInt() && node.path("code").intValue() == code;
    }

    private ResourceSnapshot snapshot(JsonNode document, String namespace, String name, String apiVersion,
            String kind, boolean requireGeneration) {
        validateResource(document, namespace, apiVersion, kind);
        var metadata = document.path("metadata");
        if (!name.equals(metadata.path("name").asText())) throw new Failure(FailureKind.INVALID_RESPONSE);
        JsonNode generation = metadata.get("generation");
        long value = 0;
        if (generation != null) {
            if (!generation.isIntegralNumber() || !generation.canConvertToLong() || generation.longValue() < 0) {
                throw new Failure(FailureKind.INVALID_RESPONSE);
            }
            value = generation.longValue();
        }
        if (requireGeneration && value < 1) throw new Failure(FailureKind.INVALID_RESPONSE);
        return new ResourceSnapshot(namespace, name, requiredText(metadata, "uid", 128),
                requiredText(metadata, "resourceVersion", 256), value, document);
    }

    private static void validateResource(JsonNode object, String namespace, String apiVersion, String kind) {
        if (!object.isObject() || !apiVersion.equals(object.path("apiVersion").asText())
                || !kind.equals(object.path("kind").asText())
                || !namespace.equals(object.path("metadata").path("namespace").asText())) {
            throw new Failure(FailureKind.INVALID_RESPONSE);
        }
        var metadata = object.path("metadata");
        try { segment(requiredText(metadata, "name", 253), 253); }
        catch (IllegalArgumentException invalid) { throw new Failure(FailureKind.INVALID_RESPONSE); }
        requiredText(metadata, "uid", 128);
        requiredText(metadata, "resourceVersion", 256);
    }

    private static String requiredText(JsonNode node, String field, int maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw new Failure(FailureKind.INVALID_RESPONSE);
        try { wireToken(value.textValue(), maximum); }
        catch (IllegalArgumentException invalid) { throw new Failure(FailureKind.INVALID_RESPONSE); }
        return value.textValue();
    }

    private static void wireToken(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum
                || !value.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)) {
            throw new IllegalArgumentException("CONFIG_KUBERNETES_TOKEN_INVALID");
        }
    }

    private static void namespace(String value) {
        if (value == null || value.length() > 63 || !value.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
            throw new IllegalArgumentException("CONFIG_KUBERNETES_NAMESPACE_INVALID");
        }
    }

    private static void positiveDecimal(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("CONFIG_KUBERNETES_REVISION_INVALID");
        try { Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("CONFIG_KUBERNETES_REVISION_INVALID"); }
    }

    private static void baselineName(String name) {
        if (name == null || !name.matches("tianshu-baseline-[0-9a-f]{64}")) throw new IllegalArgumentException("CONFIG_KUBERNETES_BASELINE_NAME_INVALID");
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }

    private static String query(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String configMapPath(String namespace, String name) { return "/api/v1/namespaces/" + namespace + "/configmaps/" + name; }
    private static String deploymentPath(DeploymentKey key) { return "/apis/apps/v1/namespaces/" + key.namespace() + "/deployments/" + key.name(); }

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
        if (!closed.compareAndSet(false, true)) return;
        List<WatchTask> active;
        synchronized (watches) { active = List.copyOf(watches.values()); }
        active.forEach(WatchTask::close);
        watchDeadlines.shutdownNow();
        client.shutdownNow();
    }

    public enum Mode { PUBLISHER, OBSERVER }

    public record ScopeVO(@NotNull Mode mode, @NotNull @Size(min = 1, max = 32) Set<@NotBlank String> namespaces,
                          @NotNull @Size(max = 128) Set<@NotNull @Valid DeploymentKey> deploymentTargets) {
        public ScopeVO {
            Objects.requireNonNull(mode, "mode");
            namespaces = Set.copyOf(Objects.requireNonNull(namespaces, "namespaces"));
            deploymentTargets = Set.copyOf(Objects.requireNonNull(deploymentTargets, "deploymentTargets"));
            if (namespaces.isEmpty() || namespaces.size() > 32 || deploymentTargets.size() > 128
                    || mode == Mode.PUBLISHER && deploymentTargets.isEmpty()
                    || mode == Mode.OBSERVER && !deploymentTargets.isEmpty()) {
                throw new IllegalArgumentException("CONFIG_KUBERNETES_SCOPE_INVALID");
            }
            namespaces.forEach(DdcKubernetesApiClient::namespace);
            for (var target : deploymentTargets) {
                if (!namespaces.contains(target.namespace())) throw new IllegalArgumentException("CONFIG_KUBERNETES_SCOPE_INVALID");
            }
        }
    }

    public record DeploymentKey(@NotBlank @Size(max = 63) String namespace, @NotBlank @Size(max = 253) String name) {
        public DeploymentKey { DdcKubernetesApiClient.namespace(namespace); segment(name, 253); }
    }

    public enum ResourceKind {
        POD("/api/v1", "pods", "v1", "Pod"),
        SERVICE("/api/v1", "services", "v1", "Service"),
        ENDPOINT_SLICE("/apis/discovery.k8s.io/v1", "endpointslices", "discovery.k8s.io/v1", "EndpointSlice"),
        DEPLOYMENT("/apis/apps/v1", "deployments", "apps/v1", "Deployment"),
        REPLICA_SET("/apis/apps/v1", "replicasets", "apps/v1", "ReplicaSet"),
        STATEFUL_SET("/apis/apps/v1", "statefulsets", "apps/v1", "StatefulSet");
        private final String prefix;
        private final String plural;
        private final String apiVersion;
        private final String kind;
        ResourceKind(String prefix, String plural, String apiVersion, String kind) {
            this.prefix = prefix; this.plural = plural; this.apiVersion = apiVersion; this.kind = kind;
        }
        private String path(String namespace) { return prefix + "/namespaces/" + namespace + "/" + plural; }
    }

    public record ResourceSnapshot(String namespace, String name, String uid, String resourceVersion,
                                   long generation, JsonNode document) {
        public ResourceSnapshot {
            DdcKubernetesApiClient.namespace(namespace); segment(name, 253);
            wireToken(uid, 128); wireToken(resourceVersion, 256);
            if (generation < 0) throw new IllegalArgumentException("CONFIG_KUBERNETES_GENERATION_INVALID");
            document = Objects.requireNonNull(document, "document").deepCopy();
        }
        @Override public JsonNode document() { return document.deepCopy(); }
    }

    public record ListPage(String resourceVersion, @Nullable String continuation, List<JsonNode> items) {
        public ListPage {
            wireToken(resourceVersion, 256);
            if (continuation != null) wireToken(continuation, 4096);
            if (items == null || items.size() > 500) throw new IllegalArgumentException("CONFIG_KUBERNETES_PAGE_INVALID");
            items = items.stream().<JsonNode>map(value -> Objects.requireNonNull(value, "item").deepCopy()).toList();
        }
        @Override public List<JsonNode> items() { return items.stream().<JsonNode>map(JsonNode::deepCopy).toList(); }
    }

    public enum WatchEventType { ADDED, MODIFIED, DELETED, BOOKMARK }

    public record WatchEvent(WatchEventType type, String resourceVersion, JsonNode object) {
        public WatchEvent {
            Objects.requireNonNull(type, "type"); wireToken(resourceVersion, 256);
            object = Objects.requireNonNull(object, "object").deepCopy();
        }
        @Override public JsonNode object() { return object.deepCopy(); }
    }

    public interface WatchHandle extends AutoCloseable {
        CompletionStage<Void> completion();
        @Override void close();
    }

    public enum FailureKind { UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, GONE, THROTTLED, UNAVAILABLE, INVALID_RESPONSE }

    public static final class Failure extends RuntimeException {
        private final FailureKind kind;
        public Failure(FailureKind kind) {
            super("CONFIG_KUBERNETES_" + Objects.requireNonNull(kind, "kind").name(), null, false, false);
            this.kind = kind;
        }
        public FailureKind kind() { return kind; }
    }

    /** One bounded streaming request; callbacks are serialized directly without an event queue. */
    private final class WatchTask implements WatchHandle {
        private final String key;
        private final ResourceKind kind;
        private final String namespace;
        private final String rv;
        private final Duration duration;
        private final Consumer<WatchEvent> consumer;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private volatile CompletableFuture<HttpResponse<InputStream>> pending;
        private volatile InputStream input;
        private volatile Thread worker;
        private volatile ScheduledFuture<?> deadline;
        private WatchTask(String key, ResourceKind kind, String namespace, String rv, Duration duration, Consumer<WatchEvent> consumer) {
            this.key = key; this.kind = kind; this.namespace = namespace; this.rv = rv; this.duration = duration; this.consumer = consumer;
        }
        private void start() {
            deadline = watchDeadlines.schedule(() -> finish(new Failure(FailureKind.UNAVAILABLE)),
                    duration.plus(timeout).toMillis(), TimeUnit.MILLISECONDS);
            worker = Thread.ofVirtual().name("tianshu-kubernetes-watch-" + kind.name()).start(this::run);
        }
        private void run() {
            try {
                String path = kind.path(namespace) + "?watch=true&allowWatchBookmarks=true&resourceVersion=" + query(rv)
                        + "&timeoutSeconds=" + duration.toSeconds();
                pending = client.sendAsync(scopedRequest(path, duration.plus(timeout)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (stopped.get()) { pending.cancel(true); return; }
                var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                input = new java.io.BufferedInputStream(response.body());
                if (stopped.get()) return;
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    byte[] body = input.readNBytes(maxResponseBytes + 1);
                    if (body.length > maxResponseBytes) throw new Failure(FailureKind.INVALID_RESPONSE);
                    throw httpFailure(response.statusCode(), body);
                }
                readEvents();
                finish(null);
            } catch (Failure failure) { finish(failure); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                finish(new Failure(FailureKind.UNAVAILABLE));
            } catch (IOException | ExecutionException | TimeoutException | RuntimeException unavailable) {
                finish(new Failure(FailureKind.UNAVAILABLE));
            } finally {
                // Even an abrupt callback failure must release the slot and complete its lifecycle.
                if (!stopped.get()) finish(new Failure(FailureKind.UNAVAILABLE));
                closeResources();
            }
        }
        private void readEvents() throws IOException {
            var line = new ByteArrayOutputStream();
            int next;
            while (!stopped.get() && (next = input.read()) >= 0) {
                if (next == '\n') {
                    if (line.size() > 0) emit(line.toByteArray());
                    line.reset();
                } else {
                    if (line.size() >= maxResponseBytes) throw new Failure(FailureKind.INVALID_RESPONSE);
                    line.write(next);
                }
            }
            if (!stopped.get() && line.size() > 0) emit(line.toByteArray());
        }
        private void emit(byte[] bytes) {
            try {
                JsonNode envelope = mapper.readTree(bytes);
                if (envelope == null || !envelope.isObject() || !envelope.path("type").isTextual()
                        || !envelope.path("object").isObject()) throw new Failure(FailureKind.INVALID_RESPONSE);
                var object = envelope.get("object");
                String type = envelope.get("type").textValue();
                if ("ERROR".equals(type)) {
                    if (isStatus(object, 410, "Expired") || isStatus(object, 410, "Gone")) throw new Failure(FailureKind.GONE);
                    int status = object.path("code").isInt() ? object.path("code").intValue() : 0;
                    throw httpFailure(status, mapper.writeValueAsBytes(object));
                }
                WatchEventType eventType;
                try { eventType = WatchEventType.valueOf(type); }
                catch (IllegalArgumentException invalid) { throw new Failure(FailureKind.INVALID_RESPONSE); }
                String eventRv = requiredText(object.path("metadata"), "resourceVersion", 256);
                if (eventType == WatchEventType.BOOKMARK) {
                    if (!kind.apiVersion.equals(object.path("apiVersion").asText()) || !kind.kind.equals(object.path("kind").asText())) {
                        throw new Failure(FailureKind.INVALID_RESPONSE);
                    }
                } else validateResource(object, namespace, kind.apiVersion, kind.kind);
                if (!stopped.get()) consumer.accept(new WatchEvent(eventType, eventRv, object));
            } catch (IOException invalid) { throw new Failure(FailureKind.INVALID_RESPONSE); }
        }
        private void finish(@Nullable Failure failure) {
            if (!stopped.compareAndSet(false, true)) return;
            watches.remove(key, this);
            closeResources();
            if (failure == null) completion.complete(null);
            else completion.completeExceptionally(failure);
        }
        private void closeResources() {
            if (deadline != null) deadline.cancel(false);
            if (pending != null && !pending.isDone()) pending.cancel(true);
            if (input != null) {
                try { input.close(); } catch (IOException ignored) { /* Request is already terminal. */ }
            }
            if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        }
        @Override public CompletionStage<Void> completion() { return completion.minimalCompletionStage(); }
        @Override public void close() { finish(null); }
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
