package top.egon.cola.component.tianshu.ai.http;

import static top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.ai.properties.DdcAiHttpProperties;
import top.egon.cola.component.tianshu.ai.properties.DdcAiHttpProperties.CapabilityEnum;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.api.client.DdcAiFeedbackClient;
import top.egon.cola.component.tianshu.api.client.DdcAiSyncClient;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;
import top.egon.cola.component.tianshu.error.DdcClientTransportException;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.component.tianshu.model.ai.DdcAiContentDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiPublicationReceiptDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiResourceQuery;
import top.egon.cola.component.tianshu.model.ai.DdcAiRevisionDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillDraftUploadDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillPublishDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportBatchDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportReceiptDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiValidationGroups;
import top.egon.cola.component.tianshu.model.ai.DdcAiVersionHeadDTO;
import top.egon.cola.component.tianshu.model.ai.DdcPromptResult;
import top.egon.cola.component.tianshu.model.ai.DdcSkillResult;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;

/** One deployment-bound HTTP/1.1 transport. Local workflow owners, not this transport, decide command retries. */
@Slf4j
@Validated
public class DdcAiHttpClient implements AutoCloseable {
    private final URI origin;
    private final String issuer;
    private final DdcAiClientScope scope;
    private final Set<CapabilityEnum> capabilities;
    private final Map<Operation, IdpServiceTokenRequest> grants;
    private final IdpServiceOAuth2Client oauth;
    private final Duration timeout;
    private final ObjectMapper json;
    private final Validator validator;
    private final HttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DdcAiHttpClient(
        DdcAiHttpProperties properties, IdpServiceOAuth2Client oauth, ObjectMapper json, Validator validator) {
        this(properties, oauth, json, validator, false);
    }

    /** Package-only loopback fixture; deployment properties cannot enable it. */
    DdcAiHttpClient(DdcAiHttpProperties properties, IdpServiceOAuth2Client oauth, ObjectMapper json,
        Validator validator, boolean loopbackFixture) {
        if (properties == null || !properties.isEnabled() || !properties.isBindingValid()
            || Boolean.getBoolean("jdk.httpclient.enableAllMethodRetry"))
            throw invalidConfiguration();
        this.origin = origin(properties.getServiceOrigin(), loopbackFixture);
        this.issuer = properties.getIssuer().toASCIIString();
        this.scope = new DdcAiClientScope(
            properties.getTenantId(), properties.getBizCode(), properties.getEnv(), properties.getAppCode());
        this.capabilities = Set.copyOf(properties.getCapabilities());
        var bound = new EnumMap<Operation, IdpServiceTokenRequest>(Operation.class);
        properties.getOperationScopes().forEach(
            (operation, requiredScope)
                -> bound.put(operation,
                    new IdpServiceTokenRequest(properties.getRegistrationId(), properties.getAppId(),
                        properties.getResourceUri(), ServiceTokenContext.TENANT, properties.getIdentityTenantId(),
                        Set.of(requiredScope))));
        this.grants = Map.copyOf(bound);
        this.oauth = Objects.requireNonNull(oauth, "oauth");
        this.timeout = properties.getTimeout();
        this.json =
            Objects.requireNonNull(json, "json")
                .copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.validator = Objects.requireNonNull(validator, "validator");
        this.client = HttpClient.newBuilder()
                          .version(HttpClient.Version.HTTP_1_1)
                          .connectTimeout(timeout)
                          .followRedirects(HttpClient.Redirect.NEVER)
                          .proxy(HttpClient.Builder.NO_PROXY)
                          .build();
    }

    public DdcPromptClient promptClient() {
        return new PromptPort(this);
    }
    public DdcSkillClient skillClient() {
        return new SkillPort(this);
    }
    public DdcAiSyncClient syncClient() {
        return new SyncPort(this);
    }
    public DdcAiFeedbackClient feedbackClient() {
        return this::report;
    }

    public DdcPromptResult prompt(DdcAiResourceQuery query) {
        checked(query);
        JsonNode body = exchange(Operation.READ, PROMPTS_PATH, selector(query), null);
        versionIdentity(body, query);
        if (!body.path("template").isTextual())
            throw invalidResponse();
        var result = decode(body, DdcPromptResult.class);
        validContent(new DdcAiContentDTO(result.getTemplate(), List.of()));
        return result;
    }

    public DdcSkillResult skill(DdcAiResourceQuery query) {
        checked(query);
        JsonNode body = exchange(Operation.READ, SKILLS_PATH, selector(query), null);
        versionIdentity(body, query);
        var result = decode(body, DdcSkillResult.class);
        validContent(new DdcAiContentDTO(null, result.getFiles()));
        return result;
    }

    public DdcAiVersionHeadDTO head(DdcAiResourceQuery query) {
        checked(query);
        if (query.versionNo() != null)
            throw invalidInput();
        JsonNode body = exchange(Operation.READ, VERSION_HEAD_PATH, selector(query), null);
        if (decimal(body, "resourceId", false) != query.resourceId()
            || !query.resourceKey().equals(text(body, "resourceKey")) || !body.path("published").isBoolean())
            throw invalidResponse();
        decimal(body, "resourceRevision", true);
        if (body.path("published").booleanValue()) {
            decimal(body, "versionId", false);
            decimal(body, "versionNo", false);
            digest(body, "contentSha256");
        } else if (!body.has("versionId") || !body.get("versionId").isNull() || !body.has("versionNo")
            || !body.get("versionNo").isNull() || !body.has("contentSha256") || !body.get("contentSha256").isNull())
            throw invalidResponse();
        return checkedResponse(decode(body, DdcAiVersionHeadDTO.class));
    }

    public DdcAiRevisionDTO replaceDraft(DdcAiSkillDraftUploadDTO upload) {
        authoring();
        checked(upload);
        if (!validator.validate(new DdcAiContentDTO(null, upload.getFiles()), DdcAiValidationGroups.Save.class)
                .isEmpty())
            throw invalidInput();
        ObjectNode request = object(upload);
        request.put("resourceId", upload.getResourceId().toString());
        request.put("expectedRevision", upload.getExpectedRevision().toString());
        long resourceId = decimal(request, "resourceId", false);
        long expected = decimal(request, "expectedRevision", true);
        JsonNode body = exchange(Operation.WRITE, SKILL_DRAFT_PATH, scopeParameters(), request);
        if (decimal(body, "resourceId", false) != resourceId || decimal(body, "resourceRevision", true) < expected)
            throw invalidResponse();
        digest(body, "draftSha256");
        if (!body.path("draftMatchesCurrent").isBoolean())
            throw invalidResponse();
        return checkedResponse(decode(body, DdcAiRevisionDTO.class));
    }

    public DdcAiPublicationReceiptDTO publishDraft(DdcAiSkillPublishDTO publish) {
        authoring();
        checked(publish);
        ObjectNode request = object(publish);
        request.put("resourceId", publish.getResourceId().toString());
        request.put("expectedRevision", publish.getExpectedRevision().toString());
        long resourceId = decimal(request, "resourceId", false);
        long expected = decimal(request, "expectedRevision", true);
        JsonNode body = exchange(Operation.PUBLISH, SKILL_PUBLICATIONS_PATH, scopeParameters(), request);
        if (decimal(body, "resourceId", false) != resourceId || decimal(body, "resourceRevision", true) < expected)
            throw invalidResponse();
        decimal(body, "publicationId", false);
        decimal(body, "targetVersionId", false);
        decimal(body, "targetVersionNo", false);
        digest(body, "contentSha256");
        return checkedResponse(decode(body, DdcAiPublicationReceiptDTO.class));
    }

    public DdcAiUsageReportReceiptDTO report(DdcAiUsageReportBatchDTO batch) {
        if (!capabilities.contains(CapabilityEnum.FEEDBACK))
            throw operationDenied();
        // Invalid individual reports retain their per-item server receipt; only the envelope is rejected here.
        if (batch == null || batch.getReports() == null || batch.getReports().isEmpty()
            || batch.getReports().size() > MAX_BATCH_SIZE || batch.getReports().stream().anyMatch(Objects::isNull))
            throw invalidInput();
        ObjectNode request = object(batch);
        List<JsonNode> usageIds = new ArrayList<>();
        request.path("reports").forEach(report -> usageIds.add(report.path("usageId").deepCopy()));
        JsonNode body = exchange(Operation.REPORT, USAGE_REPORTS_PATH, List.of(), request);
        if (!body.path("items").isArray() || body.path("items").size() != usageIds.size())
            throw invalidResponse();
        for (int i = 0; i < usageIds.size(); i++) {
            JsonNode item = body.path("items").get(i);
            if (!usageIds.get(i).equals(item.path("usageId")))
                throw invalidResponse();
            String status = text(item, "status");
            if (!Set.of("ACCEPTED", "ACCEPTED_DUPLICATE", "REJECTED").contains(status))
                throw invalidResponse();
            if ("REJECTED".equals(status)) {
                if (!item.path("reason").isTextual() || !item.path("reason").textValue().matches("[A-Z0-9_]{1,64}"))
                    throw invalidResponse();
            } else if (!item.has("reason") || !item.path("reason").isNull())
                throw invalidResponse();
        }
        return decode(body, DdcAiUsageReportReceiptDTO.class);
    }

    private JsonNode exchange(Operation operation, String path, List<String> parameters, JsonNode body) {
        long started = System.nanoTime();
        if (closed.get())
            throw failure(false, "TIANSHU_AI_CLIENT_CLOSED");
        IdpServiceTokenRequest grant = grants.get(operation);
        if (grant == null)
            throw operationDenied();
        byte[] bytes = null;
        try {
            if (body != null)
                bytes = json.writeValueAsBytes(body);
        } catch (IOException | RuntimeException invalid) {
            throw invalidInput();
        }
        if (bytes != null && bytes.length > MAX_BODY_BYTES)
            throw invalidInput();
        final String bearer;
        try {
            var token = oauth.authorizeBounded(grant, issuer, remaining(started));
            if (token == null
                || !org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER.equals(
                    token.getTokenType())
                || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(Instant.now())
                || !token.getScopes().equals(grant.scopes()) || token.getTokenValue() == null
                || token.getTokenValue().length() > 8192 || !token.getTokenValue().matches("[A-Za-z0-9._~+/-]+=*"))
                throw operationDenied();
            bearer = token.getTokenValue();
        } catch (IdpServiceOAuth2Client.IdpServiceAuthorizationException rejected) {
            boolean retryable = !Set.of("invalid_token", "invalid_client", "invalid_grant", "unauthorized_client",
                                        "invalid_scope", "insufficient_scope", "access_denied")
                                     .contains(rejected.errorCode());
            throw failure(retryable, retryable ? ERROR_UNAVAILABLE : ERROR_FORBIDDEN);
        } catch (DdcClientTransportException known) {
            throw known;
        } catch (RuntimeException unavailable) {
            throw failure(true, ERROR_UNAVAILABLE);
        }
        URI endpoint = origin.resolve(path + (parameters.isEmpty() ? "" : "?" + String.join("&", parameters)));
        var builder = HttpRequest.newBuilder(endpoint)
                          .timeout(remaining(started))
                          .header("Authorization", "Bearer " + bearer)
                          .header("Accept", MediaType.APPLICATION_JSON_VALUE);
        if (bytes == null)
            builder.GET();
        else
            builder.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        AtomicBoolean successful = new AtomicBoolean();
        final java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending;
        try {
            pending = client.sendAsync(builder.build(), info -> {
                successful.set(info.statusCode() == 200);
                return HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.ofByteArray(),
                    info.statusCode() == 200 ? MAX_BODY_BYTES : MAX_ERROR_BODY_BYTES);
            });
        } catch (RuntimeException unavailable) {
            throw failure(true, ERROR_UNAVAILABLE);
        }
        try {
            var response = pending.get(remaining(started).toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200)
                throw remoteFailure(response);
            if (response.headers().allValues("Content-Type").size() != 1
                || !jsonType(response.headers().firstValue("Content-Type").orElse("")))
                throw invalidResponse();
            JsonNode result = json.readTree(response.body());
            if (result == null || !result.isObject())
                throw invalidResponse();
            remaining(started);
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(true, ERROR_UNAVAILABLE);
        } catch (TimeoutException expired) {
            throw failure(true, ERROR_UNAVAILABLE);
        } catch (ExecutionException failed) {
            throw successful.get() ? invalidResponse() : failure(true, ERROR_UNAVAILABLE);
        } catch (IOException malformed) {
            throw invalidResponse();
        } finally {
            if (!pending.isDone())
                pending.cancel(true);
        }
    }

    private DdcClientTransportException remoteFailure(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status == 401)
            return failure(false, ERROR_UNAUTHENTICATED);
        if (status == 403)
            return operationDenied();
        if (status == 404 || status == 409) {
            String expected = status == 404 ? ERROR_NOT_FOUND : ERROR_REVISION_CONFLICT;
            try {
                if (expected.equals(json.readTree(response.body()).path("code").asText()))
                    return failure(false, expected);
            } catch (IOException | RuntimeException ignored) { /* No untrusted body or diagnostic escapes. */
            }
            return invalidResponse();
        }
        return failure(status == 408 || status == 429 || status >= 500,
            status >= 500 || status == 408 || status == 429 ? ERROR_UNAVAILABLE : "TIANSHU_AI_RESPONSE_INVALID");
    }

    private List<String> selector(DdcAiResourceQuery query) {
        List<String> parameters = new ArrayList<>(scopeParameters());
        parameters.add(parameter("namespaceCode", query.namespaceCode()));
        parameters.add(parameter("resourceKey", query.resourceKey()));
        parameters.add(parameter("resourceId", query.resourceId().toString()));
        if (query.versionNo() != null)
            parameters.add(parameter("versionNo", query.versionNo().toString()));
        return parameters;
    }

    private List<String> scopeParameters() {
        return List.of(parameter("tenantId", Long.toString(scope.tenantId())), parameter("bizCode", scope.bizCode()),
            parameter("env", scope.env()), parameter("appCode", scope.appCode()));
    }

    private static String parameter(String key, String value) {
        return key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void versionIdentity(JsonNode body, DdcAiResourceQuery query) {
        if (decimal(body, "resourceId", false) != query.resourceId()
            || !query.resourceKey().equals(text(body, "resourceKey")))
            throw invalidResponse();
        decimal(body, "versionId", false);
        long version = decimal(body, "versionNo", false);
        if (query.versionNo() != null && version != query.versionNo())
            throw invalidResponse();
        digest(body, "contentSha256");
    }

    private static long decimal(JsonNode body, String name, boolean zero) {
        JsonNode value = body.path(name);
        if (!value.isTextual() || !value.textValue().matches(zero ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}"))
            throw invalidResponse();
        try {
            return Long.parseLong(value.textValue());
        } catch (NumberFormatException invalid) {
            throw invalidResponse();
        }
    }

    private static String text(JsonNode body, String name) {
        if (!body.path(name).isTextual())
            throw invalidResponse();
        return body.path(name).textValue();
    }

    private static void digest(JsonNode body, String name) {
        if (!text(body, name).matches("[a-f0-9]{64}"))
            throw invalidResponse();
    }

    private <T> T decode(JsonNode body, Class<T> type) {
        try {
            return json.treeToValue(body, type);
        } catch (IOException | RuntimeException malformed) {
            throw invalidResponse();
        }
    }

    private ObjectNode object(Object body) {
        try {
            return json.valueToTree(body);
        } catch (RuntimeException invalid) {
            throw invalidInput();
        }
    }

    private void checked(Object value) {
        if (value == null || !validator.validate(value).isEmpty())
            throw invalidInput();
    }

    private <T> T checkedResponse(T value) {
        if (value == null || !validator.validate(value).isEmpty())
            throw invalidResponse();
        return value;
    }

    private void validContent(DdcAiContentDTO value) {
        if (!validator.validate(value, DdcAiValidationGroups.Save.class).isEmpty())
            throw invalidResponse();
    }

    private void authoring() {
        if (!capabilities.contains(CapabilityEnum.AUTHORING))
            throw operationDenied();
    }

    private Duration remaining(long started) {
        long left = timeout.toNanos() - (System.nanoTime() - started);
        if (left <= 0)
            throw failure(true, ERROR_UNAVAILABLE);
        return Duration.ofNanos(left);
    }

    private static boolean jsonType(String value) {
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(value));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static URI origin(URI uri, boolean loopbackFixture) {
        if (uri == null || !"http".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1
            || uri.getPort() > 65535 || uri.getRawUserInfo() != null || uri.getRawQuery() != null
            || uri.getRawFragment() != null || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
            || !uri.normalize().equals(uri)
            || !(loopbackFixture && "127.0.0.1".equals(uri.getHost())
                || uri.getHost().matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[.][a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[."
                                         + "]svc[.]cluster[.]local")))
            throw invalidConfiguration();
        return uri.resolve("/");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true))
            client.shutdownNow();
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("TIANSHU_AI_HTTP_CONFIGURATION_INVALID");
    }
    private static IllegalArgumentException invalidInput() {
        return new IllegalArgumentException(ERROR_INVALID_REQUEST);
    }
    private static DdcClientTransportException invalidResponse() {
        return failure(false, "TIANSHU_AI_RESPONSE_INVALID");
    }
    private static DdcClientTransportException operationDenied() {
        return failure(false, ERROR_FORBIDDEN);
    }
    private static DdcClientTransportException failure(boolean retryable, String code) {
        log.debug("AI HTTP operation failed: code={}", code);
        return new DdcClientTransportException(code, retryable);
    }

    @Slf4j
    @RequiredArgsConstructor
    private static final class PromptPort implements DdcPromptClient {
        private final DdcAiHttpClient transport;
        @Override
        public DdcPromptResult get(DdcAiResourceQuery query) {
            return transport.prompt(query);
        }
        @Override
        public DdcAiClientScope scope() {
            return transport.scope;
        }
    }
    @Slf4j
    @RequiredArgsConstructor
    private static final class SkillPort implements DdcSkillClient {
        private final DdcAiHttpClient transport;
        @Override
        public DdcSkillResult get(DdcAiResourceQuery query) {
            return transport.skill(query);
        }
        @Override
        public DdcAiClientScope scope() {
            return transport.scope;
        }
    }
    @Slf4j
    @RequiredArgsConstructor
    private static final class SyncPort implements DdcAiSyncClient {
        private final DdcAiHttpClient transport;
        @Override
        public DdcAiVersionHeadDTO getVersionHead(DdcAiResourceQuery query) {
            return transport.head(query);
        }
        @Override
        public DdcAiRevisionDTO replaceSkillDraft(DdcAiSkillDraftUploadDTO upload) {
            return transport.replaceDraft(upload);
        }
        @Override
        public DdcAiPublicationReceiptDTO publishSkillDraft(DdcAiSkillPublishDTO publish) {
            return transport.publishDraft(publish);
        }
    }
}
