package top.egon.cola.component.tianshu.api.catalog;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import top.egon.cola.component.tianshu.api.catalog.HttpDdcLogicalCatalogClient.AuthorizationVO;
import top.egon.cola.component.tianshu.api.catalog.HttpDdcLogicalCatalogClient.CatalogFailureCodeEnum;
import top.egon.cola.component.tianshu.api.catalog.HttpDdcLogicalCatalogClient.DdcLogicalCatalogException;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HttpDdcLogicalCatalogClientTest {
    private static final String ISSUER = "https://idp.example.test/tenant-a";
    private static final URI AUDIENCE = URI.create("https://api.example.test/tianshu");
    private static final IdpServiceTokenRequest TOKEN_REQUEST = new IdpServiceTokenRequest(
            "catalog-tenant-a", "tianquan-jianshen", AUDIENCE,
            ServiceTokenContext.TENANT, "tenant-a", Set.of("TIANSHU_CATALOG_READ"));
    private static final String BUSINESS = """
            {"id":"biz-7","bizCode":"finance","bizName":"Finance","enabled":false}
            """;
    private static final String APPLICATION = """
            {"id":"app-4","businessId":"biz-7","bizCode":"finance","appCode":"billing",
             "appName":"Billing","enabled":true,"businessEnabled":false}
            """;

    private final AtomicReference<ReplyVO> reply = new AtomicReference<>();
    private final AtomicReference<ArrivalVO> last = new AtomicReference<>();
    private final AtomicInteger arrivals = new AtomicInteger();
    private HttpServer server;
    private HttpDdcLogicalCatalogClient client;
    private IdpServiceOAuth2Client oauth;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        oauth = mock(IdpServiceOAuth2Client.class);
        Instant now = Instant.now();
        when(oauth.authorizeBounded(eq(TOKEN_REQUEST), eq(ISSUER), any()))
                .thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fixture-token",
                        now, now.plusSeconds(120)));
        client = new HttpDdcLogicalCatalogClient(origin(), oauth,
                () -> new AuthorizationVO(TOKEN_REQUEST, ISSUER),
                Jackson2ObjectMapperBuilder.json().build(), Duration.ofSeconds(2), true);
    }

    @AfterEach
    void stop() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retrievesFourTypedViewsAndPreservesDisabledState() {
        reply.set(new ReplyVO(200, BUSINESS));
        assertThat(client.findBusiness("biz-7")).hasValueSatisfying(
                business -> assertThat(business.enabled()).isFalse());
        assertThat(last.get()).isEqualTo(new ArrivalVO("GET", "/internal/v1/tianshu/catalog/business",
                "id=biz-7", "Bearer fixture-token"));
        reply.set(new ReplyVO(200, "[" + BUSINESS + "]"));
        assertThat(client.listBusinesses("研发 & 财务+")).hasSize(1);
        assertThat(last.get().path()).isEqualTo("/internal/v1/tianshu/catalog/businesses");
        assertThat(URLDecoder.decode(last.get().rawQuery(), StandardCharsets.UTF_8)).isEqualTo("keyword=研发 & 财务+");
        reply.set(new ReplyVO(200, APPLICATION));
        assertThat(client.findApplication("app-4")).hasValueSatisfying(app -> {
            assertThat(app.enabled()).isTrue();
            assertThat(app.businessEnabled()).isFalse();
        });
        assertThat(last.get().path()).isEqualTo("/internal/v1/tianshu/catalog/application");
        reply.set(new ReplyVO(200, "[" + APPLICATION + "]"));
        assertThat(client.listApplications("biz-7", null)).hasSize(1);
        assertThat(last.get().path()).isEqualTo("/internal/v1/tianshu/catalog/applications");
        assertThat(last.get().rawQuery()).isEqualTo("businessId=biz-7");
        assertThat(arrivals).hasValue(4);
    }

    @Test
    void distinguishesAnExactMissFromAuthorityFailuresAndDoesNotRetryOrRedirect() {
        reply.set(new ReplyVO(404, ""));
        assertThat(client.findBusiness("biz-7")).isEmpty();
        reply.set(new ReplyVO(404, "", false));
        assertInvalidResponse(() -> client.findBusiness("biz-7"));
        assertInvalidResponse(() -> client.listBusinesses(null));
        for (var status : new int[]{401, 403, 503, 307}) {
            reply.set(new ReplyVO(status, ""));
            var code = switch (status) {
                case 401 -> CatalogFailureCodeEnum.UNAUTHENTICATED;
                case 403 -> CatalogFailureCodeEnum.PERMISSION_DENIED;
                case 503 -> CatalogFailureCodeEnum.UNAVAILABLE;
                default -> CatalogFailureCodeEnum.INVALID_RESPONSE;
            };
            assertThatThrownBy(() -> client.findBusiness("biz-7"))
                    .isInstanceOfSatisfying(DdcLogicalCatalogException.class,
                            failure -> assertThat(failure.code()).isEqualTo(code));
        }
        assertThat(arrivals).hasValue(7);
    }

    @Test
    void preservesOpaquePublicIdsAsASingleEncodedQueryValue() {
        String legacyId = "biz:1/研发 + a";
        reply.set(new ReplyVO(200, BUSINESS.replace("biz-7", legacyId)));

        assertThat(client.findBusiness(legacyId)).hasValueSatisfying(
                business -> assertThat(business.id()).isEqualTo(legacyId));
        assertThat(URLDecoder.decode(last.get().rawQuery(), StandardCharsets.UTF_8)).isEqualTo("id=" + legacyId);
        assertThat(arrivals).hasValue(1);
    }

    @Test
    void rejectsMismatchedRowsDuplicatesAndIncompleteStatusFields() {
        reply.set(new ReplyVO(200, BUSINESS.replace("biz-7", "other")));
        assertInvalidResponse(() -> client.findBusiness("biz-7"));
        reply.set(new ReplyVO(200, "[" + APPLICATION + "]"));
        assertInvalidResponse(() -> client.listApplications("other-biz", null));
        reply.set(new ReplyVO(200, "[" + BUSINESS + "," + BUSINESS + "]"));
        assertInvalidResponse(() -> client.listBusinesses(null));
        reply.set(new ReplyVO(200, BUSINESS.replace(",\"enabled\":false", "")));
        assertInvalidResponse(() -> client.findBusiness("biz-7"));
    }

    @Test
    void rejectsOversizedSuccessWithoutReturningAPartialList() {
        reply.set(new ReplyVO(200, "[" + BUSINESS + " ".repeat(8 * 1024 * 1024) + "]"));
        assertInvalidResponse(() -> client.listBusinesses(null));
        assertThat(arrivals).hasValue(1);
    }

    @Test
    void productionConstructorRejectsANonServiceOriginBeforeAcquiringCredentials() {
        assertThatThrownBy(() -> new HttpDdcLogicalCatalogClient(origin(), oauth,
                () -> new AuthorizationVO(TOKEN_REQUEST, ISSUER),
                Jackson2ObjectMapperBuilder.json().build(), Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(oauth);
        assertThat(arrivals).hasValue(0);
    }

    private URI origin() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void respond(HttpExchange request) throws IOException {
        try (request) {
            arrivals.incrementAndGet();
            last.set(new ArrivalVO(request.getRequestMethod(), request.getRequestURI().getPath(),
                    request.getRequestURI().getRawQuery(), request.getRequestHeaders().getFirst("Authorization")));
            ReplyVO current = reply.get();
            if (current.status() == 404 && current.authoritativeMiss()) {
                request.getResponseHeaders().set("X-Egon-Catalog-Outcome", "not-found");
            }
            if (current.status() == 307) {
                request.getResponseHeaders().set("Location", origin() + "/redirect-target");
            }
            byte[] bytes = current.body().getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(current.status(), bytes.length == 0 ? -1 : bytes.length);
            request.getResponseBody().write(bytes);
        }
    }

    private static void assertInvalidResponse(Runnable request) {
        assertThatThrownBy(request::run).isInstanceOfSatisfying(DdcLogicalCatalogException.class,
                failure -> assertThat(failure.code()).isEqualTo(CatalogFailureCodeEnum.INVALID_RESPONSE));
    }

    private record ReplyVO(int status, String body, boolean authoritativeMiss) {
        private ReplyVO(int status, String body) {
            this(status, body, status == 404);
        }
    }

    private record ArrivalVO(String method, String path, String rawQuery, String authorization) { }
}
