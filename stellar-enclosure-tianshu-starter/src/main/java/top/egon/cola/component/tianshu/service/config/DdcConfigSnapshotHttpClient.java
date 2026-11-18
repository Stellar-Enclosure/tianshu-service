package top.egon.cola.component.tianshu.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fixed Service transport for two authorized configuration operations; no credential or response body is logged. */
@Slf4j
@Validated
public class DdcConfigSnapshotHttpClient implements AutoCloseable {
    private static final String READ_SCOPE = "tianshu:config:read";
    private static final String RECEIPT_SCOPE = "tianshu:config:receipt:write";
    private static final int MAX_SNAPSHOT_BYTES = 1_048_576;
    private static final int MAX_RECEIPT_BYTES = 32_768;
    private static final int MAX_PROOF_BYTES = 16_384;
    private static final String SNAPSHOT_PATH = "/internal/v1/config/snapshot";
    private static final String RECEIPT_PATH = "/internal/v1/config/receipts";
    private static final Set<String> RECEIPT_CONFLICT_CODES = Set.of("RECEIPT_CONFLICT", "STALE_RECEIPT",
            "RECEIPT_TARGET_SUPERSEDED", "RECEIPT_BOOT_MISMATCH", "RECEIPT_IDEMPOTENCY_MISMATCH",
            "RECEIPT_RESTART_BASELINE_MISMATCH", "TIANSHU_CONFIG_BOOT_BINDING_CONFLICT");
    private final URI serviceOrigin;
    private final Path workloadTokenFile;
    private final IdpServiceOAuth2Client oauth;
    private final IdpServiceTokenRequest readAuthorization;
    private final IdpServiceTokenRequest receiptAuthorization;
    private final String expectedIssuer;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public DdcConfigSnapshotHttpClient(URI serviceOrigin, Path workloadTokenFile, IdpServiceOAuth2Client oauth,
            IdpServiceTokenRequest readAuthorization, IdpServiceTokenRequest receiptAuthorization,
            String expectedIssuer, Duration timeout) {
        this(serviceOrigin, workloadTokenFile, oauth, readAuthorization, receiptAuthorization, expectedIssuer, timeout, false);
    }

    /** Reflection-only fixture construction. No production property or public API enables a loopback destination. */
    private DdcConfigSnapshotHttpClient(URI serviceOrigin, Path workloadTokenFile, IdpServiceOAuth2Client oauth,
            IdpServiceTokenRequest readAuthorization, IdpServiceTokenRequest receiptAuthorization,
            String expectedIssuer, Duration timeout, boolean allowLoopbackFixture) {
        this.serviceOrigin = origin(serviceOrigin, allowLoopbackFixture);
        this.workloadTokenFile = Objects.requireNonNull(workloadTokenFile, "workloadTokenFile");
        if (!workloadTokenFile.isAbsolute() || !workloadTokenFile.normalize().equals(workloadTokenFile)) throw invalidConfiguration();
        this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.readAuthorization = Objects.requireNonNull(readAuthorization, "readAuthorization");
        this.receiptAuthorization = Objects.requireNonNull(receiptAuthorization, "receiptAuthorization");
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() < 1
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) throw invalidConfiguration();
        this.timeout = timeout;
        this.expectedIssuer = canonicalHttps(expectedIssuer);
        if (readAuthorization.context() != ServiceTokenContext.TENANT || receiptAuthorization.context() != ServiceTokenContext.TENANT
                || !readAuthorization.scopes().equals(Set.of(READ_SCOPE)) || !receiptAuthorization.scopes().equals(Set.of(RECEIPT_SCOPE))
                || !readAuthorization.registrationId().equals(receiptAuthorization.registrationId())
                || !readAuthorization.appId().equals(receiptAuthorization.appId())
                || !readAuthorization.audience().equals(receiptAuthorization.audience())
                || !readAuthorization.tenantId().equals(receiptAuthorization.tenantId())) throw invalidConfiguration();
        canonicalHttps(readAuthorization.audience().toString());
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout)
                .proxy(new DirectProxySelector()).version(HttpClient.Version.HTTP_1_1).build();
        validator.afterPropertiesSet();
    }

    public SnapshotResultVO snapshot(@NotBlank String bootId, @Nullable String ifNoneMatch) {
        long started = System.nanoTime();
        validateBoot(bootId);
        if (ifNoneMatch != null) validateEtag(ifNoneMatch);
        var response = exchange(readAuthorization, "GET", SNAPSHOT_PATH, bootId, ifNoneMatch, null, null, started);
        int status = response.statusCode();
        if (status != 200 && status != 304) throw statusFailure(response);
        String etag = header(response, "ETag", 96);
        validateEtag(etag);
        String delivery = header(response, "X-Config-Delivery-Token", 8192);
        String container = header(response, "X-Config-Container-Id", 512);
        Instant validUntil = validUntil(header(response, "X-Config-Valid-Until", 40));
        if (status == 304) {
            if (ifNoneMatch == null || !ifNoneMatch.equals(etag) || response.body().length != 0) throw invalidResponse();
            remaining(started);
            return new SnapshotResultVO(null, etag, validUntil, delivery, container);
        }
        String contentType = header(response, "Content-Type", 128);
        if (!contentType.split(";", 2)[0].strip().equalsIgnoreCase("application/json")) throw invalidResponse();
        try {
            JsonNode json = DdcConfigCanonicalJson.parse(response.body());
            if (!json.isObject()) throw invalidResponse();
            var snapshot = mapper.treeToValue(json, DdcConfigSnapshotDTO.class);
            if (!validator.validate(snapshot).isEmpty()
                    || !snapshot.getContentSha256().equals(DdcConfigCanonicalJson.contentSha256(snapshot))
                    || !etag.equals(DdcConfigCanonicalJson.etag(snapshot))
                    || !validUntil.equals(snapshot.getValidUntil())) throw invalidResponse();
            remaining(started);
            return new SnapshotResultVO(snapshot, etag, validUntil, delivery, container);
        } catch (DeliveryFailure failure) { throw failure; }
        catch (IOException | RuntimeException malformed) { throw invalidResponse(); }
    }

    public void report(@NotBlank String bootId, @NotBlank String idempotencyKey, @NotNull @Valid DdcConfigReceiptCommand receipt) {
        long started = System.nanoTime();
        validateBoot(bootId);
        if (!opaque(idempotencyKey, 2048)) throw invalidInput();
        final byte[] body;
        try {
            if (receipt == null) throw invalidInput();
            body = mapper.writeValueAsBytes(receipt);
            if (body.length > MAX_RECEIPT_BYTES) throw invalidInput();
            var frozen = mapper.treeToValue(DdcConfigCanonicalJson.parse(body), DdcConfigReceiptCommand.class);
            if (!validator.validate(frozen).isEmpty() || !bootId.equals(frozen.getBootId())
                    || !frozen.getReceiptDigest().equals(DdcConfigCanonicalJson.receiptDigest(frozen))) throw invalidInput();
        } catch (DeliveryFailure failure) { throw failure; }
        catch (IOException | RuntimeException malformed) { throw invalidInput(); }
        var response = exchange(receiptAuthorization, "POST", RECEIPT_PATH, bootId, null, idempotencyKey, body, started);
        if (response.statusCode() != 204) throw statusFailure(response);
        if (response.body().length != 0 || response.headers().allValues("Content-Length").stream().anyMatch(value -> !"0".equals(value))) {
            throw invalidResponse();
        }
        remaining(started);
    }

    private HttpResponse<byte[]> exchange(IdpServiceTokenRequest authorization, String method, String path,
            String bootId, String etag, String idempotencyKey, byte[] body, long started) {
        if (closed.get()) throw unavailable("CONFIG_DELIVERY_CLOSED");
        for (int attempt = 0; attempt < 2; attempt++) {
            final String accessToken;
            try {
                var token = oauth.authorizeBounded(authorization, expectedIssuer, remaining(started));
                if (token == null || !org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())
                        || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(Instant.now())
                        || !token.getScopes().equals(authorization.scopes()) || !opaque(token.getTokenValue(), MAX_PROOF_BYTES)) {
                    throw unavailable("CONFIG_OAUTH_UNAVAILABLE");
                }
                accessToken = token.getTokenValue();
            } catch (DeliveryFailure failure) { throw failure; }
            catch (RuntimeException authorizationFailure) { throw unavailable("CONFIG_OAUTH_UNAVAILABLE"); }
            String proof = workloadProof();
            var builder = HttpRequest.newBuilder(serviceOrigin.resolve(path)).timeout(remaining(started))
                    .header("Authorization", "Bearer " + accessToken).header("X-Egon-Workload-Token", proof)
                    .header("X-Config-Boot-Id", bootId).header("Accept", "application/json");
            if (etag != null) builder.header("If-None-Match", etag);
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if (body == null) builder.GET();
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            var response = send(builder.build(), started, "GET".equals(method) ? MAX_SNAPSHOT_BYTES : MAX_RECEIPT_BYTES);
            if (response.statusCode() != 401 || attempt == 1) return response;
            try { oauth.invalidateBounded(authorization, expectedIssuer, remaining(started)); }
            catch (DeliveryFailure failure) { throw failure; }
            catch (RuntimeException invalidationFailure) { throw unavailable("CONFIG_OAUTH_UNAVAILABLE"); }
        }
        throw unavailable("CONFIG_DELIVERY_UNAVAILABLE");
    }

    private HttpResponse<byte[]> send(HttpRequest request, long started, int maximum) {
        var subscriber = new BoundedBodySubscriber(maximum);
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            pending = client.sendAsync(request, ignored -> subscriber);
            return pending.get(remaining(started).toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable("CONFIG_DELIVERY_INTERRUPTED");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof DeliveryFailure deliveryFailure) throw deliveryFailure;
            throw unavailable("CONFIG_DELIVERY_UNAVAILABLE");
        } catch (TimeoutException expired) { throw unavailable("CONFIG_DELIVERY_TIMEOUT"); }
        catch (DeliveryFailure failure) { throw failure; }
        catch (RuntimeException failure) { throw unavailable("CONFIG_DELIVERY_UNAVAILABLE"); }
        finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            subscriber.cancel();
        }
    }

    private Duration remaining(long started) {
        long left = timeout.toNanos() - (System.nanoTime() - started);
        if (left <= 0) throw unavailable("CONFIG_DELIVERY_TIMEOUT");
        return Duration.ofNanos(left);
    }

    private String workloadProof() {
        try {
            if (!Files.isRegularFile(workloadTokenFile)) throw unavailable("CONFIG_WORKLOAD_PROOF_UNAVAILABLE");
            try (var input = Files.newInputStream(workloadTokenFile)) {
                byte[] bytes = input.readNBytes(MAX_PROOF_BYTES + 1);
                if (bytes.length > MAX_PROOF_BYTES) throw unavailable("CONFIG_WORKLOAD_PROOF_UNAVAILABLE");
                String token = new String(bytes, StandardCharsets.US_ASCII).strip();
                if (!token.matches("[A-Za-z0-9._~+/-]+=*")) throw unavailable("CONFIG_WORKLOAD_PROOF_UNAVAILABLE");
                return token;
            }
        } catch (DeliveryFailure failure) { throw failure; }
        catch (IOException | RuntimeException unreadable) { throw unavailable("CONFIG_WORKLOAD_PROOF_UNAVAILABLE"); }
    }

    private DeliveryFailure statusFailure(HttpResponse<byte[]> response) {
        Kind kind = switch (response.statusCode()) {
            case 401 -> Kind.UNAUTHENTICATED;
            case 403 -> Kind.FORBIDDEN;
            case 404 -> Kind.NOT_FOUND;
            case 409 -> Kind.CONFLICT;
            case 408, 425, 429, 500, 502, 503, 504 -> Kind.UNAVAILABLE;
            default -> response.statusCode() >= 500 ? Kind.UNAVAILABLE : Kind.INVALID_RESPONSE;
        };
        String code = switch (kind) {
            case UNAUTHENTICATED -> "CONFIG_AUTHENTICATION_REQUIRED";
            case FORBIDDEN -> "CONFIG_AUTHORIZATION_DENIED";
            case NOT_FOUND -> "CONFIG_NOT_FOUND";
            case CONFLICT -> "CONFIG_RECEIPT_CONFLICT";
            case UNAVAILABLE -> "CONFIG_DELIVERY_UNAVAILABLE";
            case INVALID_RESPONSE -> "CONFIG_DELIVERY_RESPONSE_INVALID";
        };
        try {
            JsonNode error = DdcConfigCanonicalJson.parse(response.body());
            var value = error.path("errorCode");
            if (kind == Kind.CONFLICT && value.isTextual() && RECEIPT_CONFLICT_CODES.contains(value.textValue())) code = value.textValue();
        } catch (RuntimeException ignored) { /* Status still determines the class; error bodies are never exposed. */ }
        return new DeliveryFailure(kind, code);
    }

    private static String header(HttpResponse<?> response, String name, int maximum) {
        var values = response.headers().allValues(name);
        if (values.size() != 1 || !opaque(values.getFirst(), maximum)) throw invalidResponse();
        return values.getFirst();
    }

    private static Instant validUntil(String value) {
        try {
            var instant = Instant.parse(value);
            if (!instant.toString().equals(value) || !value.endsWith("Z") || !instant.isAfter(Instant.now())) throw invalidResponse();
            return instant;
        } catch (RuntimeException invalid) { throw invalidResponse(); }
    }

    private static void validateEtag(String value) {
        if (value == null || !value.matches("\"cfg-r[1-9][0-9]{0,18}-[0-9a-f]{64}\"")) throw invalidResponse();
        try { Long.parseLong(value.substring(6, value.indexOf('-', 6))); }
        catch (RuntimeException invalid) { throw invalidResponse(); }
    }

    private static void validateBoot(String bootId) {
        if (bootId == null || !bootId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw invalidInput();
    }

    private static boolean opaque(String value, int maximum) {
        return value != null && !value.isEmpty() && value.length() <= maximum
                && value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e && character != ',');
    }

    private static URI origin(URI value, boolean allowLoopback) {
        if (value == null || !"http".equals(value.getScheme()) || value.getHost() == null || value.getRawUserInfo() != null
                || value.getRawQuery() != null || value.getRawFragment() != null
                || !(value.getRawPath().isEmpty() || "/".equals(value.getRawPath()))
                || !value.normalize().equals(value) || value.getPort() == 0 || value.getPort() > 65_535) throw invalidConfiguration();
        String host = value.getHost();
        boolean service = host.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.svc\\.cluster\\.local");
        if (!service && !(allowLoopback && "127.0.0.1".equals(host))) throw invalidConfiguration();
        return value.resolve("/");
    }

    private static String canonicalHttps(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.normalize().equals(uri)) throw invalidConfiguration();
            return value;
        } catch (RuntimeException invalid) { throw invalidConfiguration(); }
    }

    private static IllegalArgumentException invalidConfiguration() { return new IllegalArgumentException("CONFIG_DELIVERY_CLIENT_CONFIGURATION_INVALID"); }
    private static DeliveryFailure invalidInput() { return new DeliveryFailure(Kind.INVALID_RESPONSE, "CONFIG_DELIVERY_INPUT_INVALID"); }
    private static DeliveryFailure invalidResponse() { return new DeliveryFailure(Kind.INVALID_RESPONSE, "CONFIG_DELIVERY_RESPONSE_INVALID"); }
    private static DeliveryFailure unavailable(String code) { return new DeliveryFailure(Kind.UNAVAILABLE, code); }

    @Override
    @PreDestroy
    public void close() {
        if (closed.compareAndSet(false, true)) {
            client.shutdownNow();
            validator.close();
        }
    }

    public record SnapshotResultVO(@Nullable DdcConfigSnapshotDTO snapshot, String etag, Instant validUntil,
                                   String deliveryToken, String containerId) {
        public SnapshotResultVO {
            Objects.requireNonNull(etag, "etag");
            Objects.requireNonNull(validUntil, "validUntil");
            Objects.requireNonNull(deliveryToken, "deliveryToken");
            Objects.requireNonNull(containerId, "containerId");
        }
        @Override public String toString() { return "ConfigSnapshotResult[notModified=" + (snapshot == null) + ", etag=" + etag + "]"; }
    }

    public enum Kind { UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, UNAVAILABLE, INVALID_RESPONSE }

    public static final class DeliveryFailure extends RuntimeException {
        private final Kind kind;
        private final String errorCode;
        public DeliveryFailure(Kind kind, String errorCode) {
            super(safeCode(errorCode), null, false, false);
            this.kind = Objects.requireNonNull(kind, "kind");
            this.errorCode = safeCode(errorCode);
        }
        public Kind kind() { return kind; }
        public String errorCode() { return errorCode; }
        private static String safeCode(String code) {
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalidConfiguration();
            return code;
        }
    }

    private static final class DirectProxySelector extends ProxySelector {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
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
            long count = 0;
            for (var buffer : buffers) count += buffer.remaining();
            if (count > maximum - bytes.size()) { cancel(); result.completeExceptionally(invalidResponse()); return; }
            for (var buffer : buffers) {
                var part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public synchronized void onError(Throwable failure) { result.completeExceptionally(unavailable("CONFIG_DELIVERY_UNAVAILABLE")); }
        @Override public synchronized void onComplete() { result.complete(bytes.toByteArray()); }
        private synchronized void cancel() { cancelled = true; if (subscription != null) subscription.cancel(); }
    }
}
