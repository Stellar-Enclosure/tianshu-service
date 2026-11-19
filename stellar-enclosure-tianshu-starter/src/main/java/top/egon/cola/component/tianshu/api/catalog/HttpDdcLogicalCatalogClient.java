package top.egon.cola.component.tianshu.api.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client.IdpServiceAuthorizationException;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.APPLICATIONS_PATH;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.APPLICATION_PATH;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.BUSINESSES_PATH;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.BUSINESS_PATH;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.MAX_ID_LENGTH;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.OUTCOME_HEADER;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.OUTCOME_NOT_FOUND;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.SCOPE;

/** Internal HTTP/1.1 client for the four fixed catalog reads. */
@Slf4j
@Validated
public class HttpDdcLogicalCatalogClient implements DdcLogicalCatalogClient, AutoCloseable {

    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 16 * 1024;

    private final URI origin;
    private final IdpServiceOAuth2Client oauth;
    private final Supplier<AuthorizationVO> authorization;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectReader json;

    public HttpDdcLogicalCatalogClient(URI origin, IdpServiceOAuth2Client oauth, Supplier<AuthorizationVO> authorization,
            ObjectMapper mapper, Duration timeout) {
        this(origin, oauth, authorization, mapper, timeout, false);
    }

    HttpDdcLogicalCatalogClient(URI origin, IdpServiceOAuth2Client oauth, Supplier<AuthorizationVO> authorization,
            ObjectMapper mapper, Duration timeout, boolean allowLoopbackFixture) {
        this.origin = serviceOrigin(origin, allowLoopbackFixture);
        this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Catalog timeout is invalid");
        }
        this.timeout = timeout;
        this.json = Objects.requireNonNull(mapper, "mapper").reader()
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(timeout).build();
    }

    @Override
    public Optional<DdcManagementBiz> findBusiness(String id) {
        String requestedId = id(id);
        return this.<DdcManagementBiz>exchange(uri(BUSINESS_PATH, "id", requestedId, null), json.forType(DdcManagementBiz.class), true)
                .map(value -> {
                    DdcManagementBiz business = checkedBusiness(value);
                    requireMatchingId(requestedId, business.id());
                    return business;
                });
    }

    @Override
    public List<DdcManagementBiz> listBusinesses(@Nullable String keyword) {
        ObjectReader reader = json.forType(new TypeReference<List<DdcManagementBiz>>() { });
        return list(exchange(uri(BUSINESSES_PATH, null, null, keyword(keyword)), reader, false),
                HttpDdcLogicalCatalogClient::checkedBusiness, DdcManagementBiz::id);
    }

    @Override
    public Optional<DdcManagementApp> findApplication(String id) {
        String requestedId = id(id);
        return this.<DdcManagementApp>exchange(uri(APPLICATION_PATH, "id", requestedId, null), json.forType(DdcManagementApp.class), true)
                .map(value -> {
                    DdcManagementApp application = checkedApplication(value, null);
                    requireMatchingId(requestedId, application.id());
                    return application;
                });
    }

    @Override
    public List<DdcManagementApp> listApplications(@Nullable String businessId, @Nullable String keyword) {
        String parent = businessId == null || businessId.isBlank() ? null : id(businessId);
        ObjectReader reader = json.forType(new TypeReference<List<DdcManagementApp>>() { });
        return list(exchange(uri(APPLICATIONS_PATH, "businessId", parent, keyword(keyword)), reader, false),
                application -> checkedApplication(application, parent), DdcManagementApp::id);
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    private <T> Optional<T> exchange(URI uri, ObjectReader reader, boolean exactLookup) {
        long deadline = System.nanoTime() + timeout.toNanos();
        String token = accessToken(deadline);
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri).GET()
                .timeout(remaining(deadline))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + token).build(), deadline);
        if (response.statusCode() == 404 && exactLookup
                && response.headers().allValues(OUTCOME_HEADER).equals(List.of(OUTCOME_NOT_FOUND))) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw failure(status(response.statusCode()));
        }
        if (!response.headers().firstValue("Content-Type").map(HttpDdcLogicalCatalogClient::isJson).orElse(false)) {
            throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
        }
        try {
            T parsed = reader.readValue(response.body());
            if (parsed == null) {
                throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
            }
            return Optional.of(parsed);
        } catch (IOException malformed) {
            throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
        }
    }

    private String accessToken(long deadline) {
        try {
            AuthorizationVO grant = authorization.get();
            if (grant == null) {
                throw failure(CatalogFailureCodeEnum.UNAUTHENTICATED);
            }
            String token = oauth.authorizeBounded(grant.request(), grant.issuer(), remaining(deadline)).getTokenValue();
            if (token == null || token.length() > 8192 || !token.matches("[A-Za-z0-9._~+/-]+=*")) {
                throw failure(CatalogFailureCodeEnum.UNAUTHENTICATED);
            }
            return token;
        } catch (IdpServiceAuthorizationException rejected) {
            throw failure(switch (rejected.errorCode()) {
                case "invalid_token", "invalid_client", "invalid_grant", "unauthorized_client" -> CatalogFailureCodeEnum.UNAUTHENTICATED;
                case "invalid_scope", "insufficient_scope", "access_denied" -> CatalogFailureCodeEnum.PERMISSION_DENIED;
                default -> CatalogFailureCodeEnum.UNAVAILABLE;
            });
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request, long deadline) {
        AtomicBoolean receivedSuccess = new AtomicBoolean();
        var response = client.sendAsync(request, info -> {
            boolean success = info.statusCode() == 200;
            receivedSuccess.set(success);
            return success ? BodySubscribers.limiting(BodySubscribers.ofByteArray(), MAX_RESPONSE_BYTES)
                    : BodySubscribers.limiting(
                            BodySubscribers.mapping(BodySubscribers.discarding(), ignored -> new byte[0]), MAX_ERROR_BYTES);
        });
        try {
            return response.get(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(CatalogFailureCodeEnum.UNAVAILABLE);
        } catch (TimeoutException expired) {
            throw failure(CatalogFailureCodeEnum.UNAVAILABLE);
        } catch (ExecutionException transportFailure) {
            if (receivedSuccess.get() && !(transportFailure.getCause() instanceof HttpTimeoutException)) {
                throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
            }
            throw failure(CatalogFailureCodeEnum.UNAVAILABLE);
        } finally {
            if (!response.isDone()) {
                response.cancel(true);
            }
        }
    }

    private URI uri(String path, @Nullable String parameter, @Nullable String value, @Nullable String keyword) {
        List<String> parameters = new ArrayList<>(2);
        if (value != null) {
            parameters.add(parameter + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        if (keyword != null) {
            parameters.add("keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        }
        return origin.resolve(parameters.isEmpty() ? path : path + "?" + String.join("&", parameters));
    }

    private static String id(String value) {
        if (!validId(value)) {
            throw new IllegalArgumentException("Catalog id is invalid");
        }
        return value;
    }

    private static boolean validId(@Nullable String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_ID_LENGTH
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static @Nullable String keyword(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Catalog keyword is invalid");
        }
        return trimmed;
    }

    private static <T> List<T> list(Optional<List<T>> decoded, Function<T, T> validate, Function<T, String> key) {
        List<T> rows = decoded.orElseThrow(() -> failure(CatalogFailureCodeEnum.INVALID_RESPONSE));
        Set<String> ids = new HashSet<>();
        for (T row : rows) {
            if (!ids.add(key.apply(validate.apply(row)))) {
                throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
            }
        }
        return List.copyOf(rows);
    }

    private static DdcManagementBiz checkedBusiness(DdcManagementBiz business) {
        if (business == null || !validId(business.id()) || business.bizCode().isBlank() || business.bizName().isBlank()) {
            throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
        }
        return business;
    }

    private static DdcManagementApp checkedApplication(DdcManagementApp application, @Nullable String parent) {
        if (application == null || !validId(application.id()) || !validId(application.businessId())
                || application.bizCode().isBlank() || application.appCode().isBlank() || application.appName().isBlank()
                || parent != null && !parent.equals(application.businessId())) {
            throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
        }
        return application;
    }

    private static void requireMatchingId(String requestedId, String responseId) {
        if (!requestedId.equals(responseId)) {
            throw failure(CatalogFailureCodeEnum.INVALID_RESPONSE);
        }
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw failure(CatalogFailureCodeEnum.UNAVAILABLE);
        }
        return Duration.ofNanos(nanos);
    }

    private static boolean isJson(String value) {
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(value));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static URI serviceOrigin(URI candidate, boolean allowLoopbackFixture) {
        Objects.requireNonNull(candidate, "origin");
        String host = candidate.getHost();
        if (!"http".equals(candidate.getScheme()) || host == null || candidate.getPort() < 1 || candidate.getPort() > 65535
                || candidate.getUserInfo() != null || candidate.getQuery() != null || candidate.getFragment() != null
                || !(candidate.getPath().isEmpty() || "/".equals(candidate.getPath()))
                || !(allowLoopbackFixture && "127.0.0.1".equals(host) || host.matches(
                        "[a-z0-9](?:[-a-z0-9]*[a-z0-9])?\\.[a-z0-9](?:[-a-z0-9]*[a-z0-9])?\\.svc\\.cluster\\.local"))) {
            throw new IllegalArgumentException("Catalog origin must be an internal Service origin");
        }
        return candidate;
    }

    private static CatalogFailureCodeEnum status(int status) {
        return switch (status) {
            case 401 -> CatalogFailureCodeEnum.UNAUTHENTICATED;
            case 403 -> CatalogFailureCodeEnum.PERMISSION_DENIED;
            case 408, 429, 500, 502, 503, 504 -> CatalogFailureCodeEnum.UNAVAILABLE;
            default -> status >= 500 ? CatalogFailureCodeEnum.UNAVAILABLE : CatalogFailureCodeEnum.INVALID_RESPONSE;
        };
    }

    private static DdcLogicalCatalogException failure(CatalogFailureCodeEnum code) {
        log.debug("Tianshu logical catalog request failed: code={}", code);
        return new DdcLogicalCatalogException(code);
    }

    public record AuthorizationVO(IdpServiceTokenRequest request, String issuer) {
        public AuthorizationVO {
            Objects.requireNonNull(request, "request");
            URI issuerUri = URI.create(Objects.requireNonNull(issuer, "issuer"));
            if (request.context() != ServiceTokenContext.TENANT || !request.scopes().equals(Set.of(SCOPE))
                    || !"https".equals(issuerUri.getScheme()) || issuerUri.getHost() == null
                    || issuerUri.getUserInfo() != null || issuerUri.getRawQuery() != null || issuerUri.getFragment() != null) {
                throw new IllegalArgumentException("Catalog credential binding is invalid");
            }
        }
    }

    public enum CatalogFailureCodeEnum {
        UNAUTHENTICATED,
        PERMISSION_DENIED,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    public static final class DdcLogicalCatalogException extends RuntimeException {
        private final CatalogFailureCodeEnum code;

        private DdcLogicalCatalogException(CatalogFailureCodeEnum code) {
            super(code.name());
            this.code = code;
        }

        public CatalogFailureCodeEnum code() {
            return code;
        }
    }
}
