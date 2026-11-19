package top.egon.cola.component.tianshu.ai.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import top.egon.cola.component.tianshu.ai.properties.DdcAiHttpProperties;
import top.egon.cola.component.tianshu.ai.properties.DdcAiHttpProperties.CapabilityEnum;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.error.DdcClientTransportException;
import top.egon.cola.component.tianshu.model.ai.DdcAiNodeDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiNodeTypeEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiResourceQuery;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillDraftUploadDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillPublishDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportBatchDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportDTO;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;

class DdcAiHttpClientTest {
    private static final String ISSUER = "https://idp.example.invalid/tenant-a";
    private static final String HASH = "a".repeat(64);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class);
    private final List<DdcAiHttpClient> clients = new ArrayList<>();
    private final AtomicInteger delivered = new AtomicInteger();
    private final AtomicReference<RequestVO> received = new AtomicReference<>();
    private HttpServer server;
    private ValidatorFactory validators;
    private Responder response;
    private URI origin;

    @BeforeEach
    void setUp() throws IOException {
        validators = Validation.buildDefaultValidatorFactory();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.createContext("/", exchange -> {
            delivered.incrementAndGet();
            received.set(new RequestVO(exchange.getRequestMethod(), exchange.getRequestURI(),
                exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestBody().readAllBytes()));
            response.respond(exchange);
        });
        response = exchange -> reply(exchange, 200, prompt());
        server.start();
        when(oauth.authorizeBounded(any(), eq(ISSUER), any())).thenAnswer(invocation -> {
            IdpServiceTokenRequest request = invocation.getArgument(0);
            return token(request.scopes());
        });
    }

    @AfterEach
    void close() {
        clients.forEach(DdcAiHttpClient::close);
        server.stop(0);
        validators.close();
    }

    @Test
    void readsExactVersionThroughApprovedScopeAndOneBoundCredential() {
        var client = client(properties());
        assertThat(client.promptClient().get(query(2L)).getVersionNo()).isEqualTo(2L);
        assertThat(received.get().method()).isEqualTo("GET");
        assertThat(received.get().uri().getPath()).isEqualTo("/internal/v1/ai/prompts");
        assertThat(parameters(received.get().uri()))
            .containsExactlyInAnyOrderEntriesOf(
                Map.of("tenantId", "7", "bizCode", "retail", "env", "prod", "appCode", "orders", "namespaceCode",
                    "logical ns", "resourceKey", "skill-a", "resourceId", "41", "versionNo", "2"));
        assertThat(received.get().authorization()).isEqualTo("Bearer synthetic-token");
        var grant = org.mockito.ArgumentCaptor.forClass(IdpServiceTokenRequest.class);
        verify(oauth).authorizeBounded(grant.capture(), eq(ISSUER), any());
        assertThat(grant.getValue().registrationId()).isEqualTo("registered-ai-client");
        assertThat(grant.getValue().appId()).isEqualTo("source-app-id");
        assertThat(grant.getValue().tenantId()).isEqualTo("identity-tenant-a");
        assertThat(grant.getValue().audience()).isEqualTo(URI.create("https://resource.example.invalid/tianshu"));
        assertThat(grant.getValue().scopes()).containsExactly("fixture:ai:read");
        assertThat(client.promptClient().scope().tenantId()).isEqualTo(7L);
        assertThat(client.skillClient().scope()).isEqualTo(client.promptClient().scope());
    }

    @Test
    void skillAndUnpublishedHeadKeepTypedNullableSemantics() {
        var client = client(properties());
        var skill = json.createObjectNode()
                        .put("resourceId", "41")
                        .put("resourceKey", "skill-a")
                        .put("versionId", "201")
                        .put("versionNo", "2")
                        .put("contentSha256", HASH);
        skill.set("files", json.valueToTree(files()));
        response = exchange -> reply(exchange, 200, skill);
        assertThat(client.skillClient().get(query(2L)).getFiles()).hasSize(2);
        assertThat(received.get().uri().getPath()).isEqualTo("/internal/v1/ai/skills");
        var head = json.createObjectNode()
                       .put("resourceId", "41")
                       .put("resourceKey", "skill-a")
                       .put("resourceRevision", "0")
                       .put("published", false)
                       .putNull("versionId")
                       .putNull("versionNo")
                       .putNull("contentSha256");
        response = exchange -> reply(exchange, 200, head);
        assertThat(client.syncClient().getVersionHead(query(null)).getVersionId()).isNull();
        assertThat(parameters(received.get().uri())).doesNotContainKey("versionNo");
        int before = delivered.get();
        assertThatThrownBy(() -> client.syncClient().getVersionHead(query(2L)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(delivered).hasValue(before);
    }

    @Test
    void draftAndPublishUsePostWithFixedScopeAndStringIdsIncludingZeroRevision() throws Exception {
        var client = client(properties());
        response = exchange
            -> reply(exchange, 200,
                json.createObjectNode()
                    .put("resourceId", "41")
                    .put("resourceRevision", "1")
                    .put("draftSha256", HASH)
                    .put("draftMatchesCurrent", false));
        assertThat(client.syncClient().replaceSkillDraft(upload()).getResourceRevision()).isEqualTo(1L);
        assertThat(received.get().method()).isEqualTo("POST");
        assertThat(received.get().uri().getPath()).isEqualTo("/internal/v1/ai/skill-draft");
        assertThat(parameters(received.get().uri()))
            .containsExactlyInAnyOrderEntriesOf(
                Map.of("tenantId", "7", "bizCode", "retail", "env", "prod", "appCode", "orders"));
        var draftBody = json.readTree(received.get().body());
        assertThat(draftBody.path("resourceId").textValue()).isEqualTo("41");
        assertThat(draftBody.path("expectedRevision").textValue()).isEqualTo("0");
        response = exchange -> reply(exchange, 200, publication());
        assertThat(client.syncClient().publishSkillDraft(publish()).getTargetVersionNo()).isEqualTo(3L);
        var publishBody = json.readTree(received.get().body());
        assertThat(received.get().method()).isEqualTo("POST");
        assertThat(publishBody.path("expectedRevision").textValue()).isEqualTo("1");
        assertThat(publishBody.path("requestId").textValue()).isEqualTo("stable-request-0001");
    }

    @Test
    void usageReportKeepsPartialReceiptsAndHasNoCallerChosenScopeQuery() {
        response = exchange -> reply(exchange, 200, json.readTree("""
                {"items":[{"usageId":"usage-valid-00001","status":"ACCEPTED","reason":null},
                {"usageId":"usage-invalid-01","status":"REJECTED","reason":"INVALID_REPORT"}]}
                """));
        var client = client(properties());
        // Deliberately invalid item content is for the receiver's individual-receipt validator.
        var batch = new DdcAiUsageReportBatchDTO(List.of(new DdcAiUsageReportDTO().setUsageId("usage-valid-00001"),
            new DdcAiUsageReportDTO().setUsageId("usage-invalid-01")));
        assertThat(client.feedbackClient().report(batch).getItems()).hasSize(2);
        assertThat(received.get().uri().getPath()).isEqualTo("/internal/v1/ai/usage-reports");
        assertThat(received.get().uri().getRawQuery()).isNull();
    }

    @Test
    void everyWriteGetsExactlyOneDeliveredAttemptOn503AndNoTokenInvalidation() {
        response = exchange -> reply(exchange, 503, json.createObjectNode().put("code", "TIANSHU_AI_UNAVAILABLE"));
        var client = client(properties());
        assertRetryable(() -> client.syncClient().replaceSkillDraft(upload()));
        assertRetryable(() -> client.syncClient().publishSkillDraft(publish()));
        assertRetryable(()
                            -> client.feedbackClient().report(new DdcAiUsageReportBatchDTO(
                                List.of(new DdcAiUsageReportDTO().setUsageId("usage-valid-00001")))));
        assertThat(delivered).hasValue(3);
        verify(oauth, times(3)).authorizeBounded(any(), eq(ISSUER), any());
        verifyNoMoreInteractions(oauth);
    }

    @Test
    void deliveredPostWithLostResponseIsNotTransparentlyReplayed() {
        response = HttpExchange::close;
        var client = client(properties());
        assertRetryable(() -> client.syncClient().publishSkillDraft(publish()));
        assertThat(delivered).hasValue(1);
        assertThat(received.get().method()).isEqualTo("POST");
    }

    @Test
    void authorizationRejectionPrecedesAnyHttpSendAndDoesNotExposeCause() {
        doThrow(new IllegalStateException("secret-token-authority-detail"))
            .when(oauth)
            .authorizeBounded(any(), eq(ISSUER), any());
        var client = client(properties());
        assertThatThrownBy(() -> client.promptClient().get(query(2L)))
            .isInstanceOfSatisfying(DdcClientTransportException.class, failure -> {
                assertThat(failure.getMessage()).isEqualTo("TIANSHU_AI_UNAVAILABLE");
                assertThat(failure.getCause()).isNull();
            });
        assertThat(delivered).hasValue(0);
    }

    @Test
    void wrongTokenScopeOrExpiredTokenNeverReachesTheServer() {
        var client = client(properties());
        doReturn(token(Set.of("unrelated:scope"))).when(oauth).authorizeBounded(any(), eq(ISSUER), any());
        assertPermanent(() -> client.promptClient().get(query(2L)));
        doReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "synthetic-token",
                     Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), Set.of("fixture:ai:read")))
            .when(oauth)
            .authorizeBounded(any(), eq(ISSUER), any());
        assertPermanent(() -> client.promptClient().get(query(2L)));
        assertThat(delivered).hasValue(0);
    }

    @Test
    void readOnlyBindingCannotAcquireWriteCredentialsAndConfigurationMutationCannotBroadenIt() {
        var properties = properties()
                             .setCapabilities(Set.of(CapabilityEnum.READ))
                             .setOperationScopes(Map.of(Operation.READ, "fixture:ai:read"));
        var client = client(properties);
        properties.setCapabilities(Set.of(CapabilityEnum.AUTHORING))
            .setOperationScopes(Map.of(Operation.READ, "fixture:ai:read", Operation.WRITE, "fixture:ai:write",
                Operation.PUBLISH, "fixture:ai:publish"));
        assertPermanent(() -> client.syncClient().replaceSkillDraft(upload()));
        assertPermanent(() -> client.syncClient().publishSkillDraft(publish()));
        assertPermanent(() -> client.feedbackClient().report(new DdcAiUsageReportBatchDTO(List.of())));
        verifyNoInteractions(oauth);
        assertThat(delivered).hasValue(0);
    }

    @Test
    void rejectsWrongIdentityVersionNumericIdsDuplicateJsonAndExtraFields() {
        var client = client(properties());
        for (var malformed :
            List.of(prompt().deepCopy().put("resourceId", "42"), prompt().deepCopy().put("versionNo", "3"),
                prompt().deepCopy().put("versionNo", 2), prompt().deepCopy().put("unexpected", true))) {
            response = exchange -> reply(exchange, 200, malformed);
            assertPermanent(() -> client.promptClient().get(query(2L)));
        }
        response = exchange -> raw(exchange, 200, "{\"resourceId\":\"41\",\"resourceId\":\"42\"}");
        assertPermanent(() -> client.promptClient().get(query(2L)));
    }

    @Test
    void rejectsMismatchedPublicationAndOutOfOrderReportReceipts() {
        var client = client(properties());
        response = exchange -> reply(exchange, 200, publication().put("resourceId", "42"));
        assertPermanent(() -> client.syncClient().publishSkillDraft(publish()));
        response = exchange
            -> raw(exchange, 200, "{\"items\":[{\"usageId\":\"unrelated\",\"status\":\"ACCEPTED\",\"reason\":null}]}");
        assertPermanent(()
                            -> client.feedbackClient().report(new DdcAiUsageReportBatchDTO(
                                List.of(new DdcAiUsageReportDTO().setUsageId("usage-valid-00001")))));
    }

    @Test
    void redirectAndRoute404AreNeverTreatedAsAnAuthorizedResourceMiss() {
        var client = client(properties());
        response = exchange -> {
            exchange.getResponseHeaders().set("Location", origin.resolve("/not-approved").toString());
            reply(exchange, 302, json.createObjectNode());
        };
        assertPermanent(() -> client.promptClient().get(query(2L)));
        assertThat(delivered).hasValue(1);
        response = exchange -> reply(exchange, 404, json.createObjectNode().put("code", "ROUTE_NOT_FOUND"));
        assertThatThrownBy(() -> client.promptClient().get(query(2L))).hasMessage("TIANSHU_AI_RESPONSE_INVALID");
        response = exchange -> reply(exchange, 404, json.createObjectNode().put("code", "TIANSHU_AI_NOT_FOUND"));
        assertThatThrownBy(() -> client.promptClient().get(query(2L))).hasMessage("TIANSHU_AI_NOT_FOUND");
    }

    @Test
    void tokenAndHttpShareOneBudgetAndExhaustedTokenBudgetCannotSend() {
        doAnswer(invocation -> {
            Duration budget = invocation.getArgument(2);
            assertThat(budget).isPositive().isLessThanOrEqualTo(Duration.ofMillis(30));
            Thread.sleep(60);
            return token(Set.of("fixture:ai:read"));
        })
            .when(oauth)
            .authorizeBounded(any(), eq(ISSUER), any());
        var client = client(properties().setTimeout(Duration.ofMillis(30)));
        assertRetryable(() -> client.promptClient().get(query(2L)));
        assertThat(delivered).hasValue(0);
    }

    @Test
    void malformedBindingAndPublicOriginAreRejectedBeforeAnyToken() {
        assertThatThrownBy(() -> client(properties().setOperationScopes(Map.of())))
            .isInstanceOf(IllegalArgumentException.class);
        var publicOrigin = properties().setServiceOrigin(URI.create("https://unapproved.example.invalid"));
        assertThatThrownBy(() -> client(publicOrigin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DdcAiHttpClient(properties(), oauth, json, validators.getValidator()))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(oauth);
    }

    @Test
    void closePreventsNewAuthorizationOrSend() {
        var client = client(properties());
        client.close();
        client.close();
        assertPermanent(() -> client.promptClient().get(query(2L)));
        verifyNoInteractions(oauth);
        assertThat(delivered).hasValue(0);
    }

    @Test
    void boundedErrorResponseAndOversizedRequestCannotEscapeTheWireBudget() {
        var client = client(properties());
        response = exchange -> raw(exchange, 503, "x".repeat(8193));
        assertRetryable(() -> client.promptClient().get(query(2L)));
        clearInvocations(oauth);
        int before = delivered.get();
        var report =
            new DdcAiUsageReportDTO().setUsageId("usage-valid-00001").setInputExcerpt("x".repeat(16 * 1024 * 1024));
        assertThatThrownBy(() -> client.feedbackClient().report(new DdcAiUsageReportBatchDTO(List.of(report))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("TIANSHU_AI_INVALID_REQUEST");
        verifyNoInteractions(oauth);
        assertThat(delivered).hasValue(before);
    }

    @Test
    void slowResponseUsesOnlyTheBudgetRemainingAfterTokenAcquisition() {
        doAnswer(invocation -> {
            Thread.sleep(100);
            return token(Set.of("fixture:ai:read"));
        })
            .when(oauth)
            .authorizeBounded(any(), eq(ISSUER), any());
        response = exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            try {
                reply(exchange, 200, prompt());
            } catch (IOException cancelled) {
                exchange.close();
            }
        };
        var client = client(properties().setTimeout(Duration.ofMillis(200)));
        long started = System.nanoTime();
        assertRetryable(() -> client.promptClient().get(query(2L)));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(800));
        assertThat(delivered).hasValue(1);
    }

    private DdcAiHttpClient client(DdcAiHttpProperties properties) {
        var client = new DdcAiHttpClient(properties, oauth, json, validators.getValidator(), true);
        clients.add(client);
        return client;
    }

    private DdcAiHttpProperties properties() {
        var scopes = new EnumMap<Operation, String>(Operation.class);
        for (var operation : Operation.values())
            scopes.put(operation, "fixture:ai:" + operation.name().toLowerCase(java.util.Locale.ROOT));
        return new DdcAiHttpProperties()
            .setEnabled(true)
            .setServiceOrigin(origin)
            .setIssuer(URI.create(ISSUER))
            .setResourceUri(URI.create("https://resource.example.invalid/tianshu"))
            .setRegistrationId("registered-ai-client")
            .setAppId("source-app-id")
            .setIdentityTenantId("identity-tenant-a")
            .setTenantId(7L)
            .setBizCode("retail")
            .setEnv("prod")
            .setAppCode("orders")
            .setCapabilities(Set.of(CapabilityEnum.READ, CapabilityEnum.AUTHORING, CapabilityEnum.FEEDBACK))
            .setOperationScopes(scopes);
    }

    private OAuth2AccessToken token(Set<String> scopes) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "synthetic-token", Instant.now(),
            Instant.now().plusSeconds(120), scopes);
    }
    private DdcAiResourceQuery query(Long version) {
        return new DdcAiResourceQuery("logical ns", "skill-a", version, 41L);
    }
    private DdcAiSkillDraftUploadDTO upload() {
        return new DdcAiSkillDraftUploadDTO("logical ns", "skill-a", 41L, 0L, "Skill", "", files());
    }
    private DdcAiSkillPublishDTO publish() {
        return new DdcAiSkillPublishDTO("logical ns", "skill-a", 41L, 1L, "stable-request-0001", "Publish fixture");
    }
    private List<DdcAiNodeDTO> files() {
        return List.of(new DdcAiNodeDTO("", DdcAiNodeTypeEnum.DIRECTORY, null),
            new DdcAiNodeDTO("SKILL.md", DdcAiNodeTypeEnum.FILE, "# Skill"));
    }
    private com.fasterxml.jackson.databind.node.ObjectNode prompt() {
        return json.createObjectNode()
            .put("resourceId", "41")
            .put("resourceKey", "skill-a")
            .put("versionId", "201")
            .put("versionNo", "2")
            .put("contentSha256", HASH)
            .put("template", "Hello");
    }
    private com.fasterxml.jackson.databind.node.ObjectNode publication() {
        return json.createObjectNode()
            .put("resourceId", "41")
            .put("publicationId", "301")
            .put("targetVersionId", "203")
            .put("targetVersionNo", "3")
            .put("resourceRevision", "2")
            .put("contentSha256", HASH);
    }

    private Map<String, String> parameters(URI uri) {
        var values = new LinkedHashMap<String, String>();
        for (String pair : uri.getRawQuery().split("&")) {
            var parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            assertThat(values.put(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8))).isNull();
        }
        return values;
    }
    private void reply(HttpExchange exchange, int status, JsonNode body) throws IOException {
        raw(exchange, status, json.writeValueAsString(body));
    }
    private void raw(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
    private void assertRetryable(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(
                DdcClientTransportException.class, failure -> assertThat(failure.retryable()).isTrue());
    }
    private void assertPermanent(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(
                DdcClientTransportException.class, failure -> assertThat(failure.retryable()).isFalse());
    }
    private record RequestVO(String method, URI uri, String authorization, byte[] body) {}
    @FunctionalInterface
    private interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }
}
