package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import top.egon.cola.component.common.core.enums.ResultCode;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.config.DdcCatalogActivationProperties;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

class DdcCatalogActivationClientTest {
    private final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class);
    private final DdcCatalogActivationJson json = new DdcCatalogActivationJson();
    private MockRestServiceServer server;
    private DdcCatalogActivationClient client;

    @BeforeEach void setup() {
        MDC.put("tenantId", "101");
        var properties = new DdcCatalogActivationProperties();
        properties.setBindings(Map.of("501", new DdcCatalogActivationProperties.Binding(SCOPE, "order-admin", URI.create("https://rbac.example.test/"),
                URI.create("https://rbac.example.test"), "metadata-reader", "metadata")));
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        client = new DdcCatalogActivationClient(properties, oauth, json, builder.build());
        when(oauth.authorize(any())).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-token", NOW, NOW.plusSeconds(300)));
    }
    @AfterEach void cleanup() { MDC.clear(); TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test void exactAuthenticatedDescriptorUsesFullTargetAndSharedCanonicalDigest() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/v1/permission-catalogs/501/activations/9?")))
                .andExpect(queryParam("tenantId", "101")).andExpect(queryParam("namespaceCode", "main"))
                .andExpect(queryParam("environment", "test")).andExpect(queryParam("producerId", "order-admin"))
                .andExpect(header("Authorization", "Bearer test-token")).andRespond(withSuccess(envelope(), MediaType.APPLICATION_JSON));
        var descriptor = client.fetch(SCOPE, "9");
        assertThat(descriptor).isEqualTo(DdcI18nBundleAssemblerTest.descriptor());
        assertThat(json.read(descriptor.canonicalize()).digest()).isEqualTo(descriptor.digest());
        server.verify();
    }

    @Test void onlyOneAuthenticationRenewalIsAllowedAndNoBodyOrUrlControlsDestination() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.fetch(SCOPE, "9")).isInstanceOf(CommonException.class).hasMessageContaining("unavailable");
        verify(oauth, times(2)).authorize(any()); verify(oauth, times(2)).invalidate(any()); server.verify();
    }

    @Test void wrongNamespaceOrTenantAndCallsInsideSqlNeverSendHttp() {
        var other = new CatalogScope("501", "101", "trade", "order", "other", "test");
        assertThatThrownBy(() -> client.fetch(other, "9")).isInstanceOf(CommonException.class);
        MDC.put("tenantId", "102");
        assertThatThrownBy(() -> client.fetch(SCOPE, "9")).isInstanceOf(CommonException.class);
        MDC.put("tenantId", "101"); TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> client.fetch(SCOPE, "9")).isInstanceOf(IllegalStateException.class).hasMessage("CATALOG_HTTP_INSIDE_SQL_TRANSACTION");
        verifyNoInteractions(oauth);
    }

    @Test void wrongVersionAndNonSuccessfulEnvelopeFailClosed() {
        server.expect(anything()).andRespond(withSuccess(envelope().replace("\"activationVersion\":\"9\"", "\"activationVersion\":\"8\""), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.fetch(SCOPE, "9")).isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> json.readResponse(envelope().replace("\"success\":true", "\"success\":false").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void parserRejectsNumericIdsUnknownPropertiesDuplicatesAndTrailingContent() {
        String descriptor = DdcI18nBundleAssemblerTest.descriptor().canonicalize();
        assertThatThrownBy(() -> json.read(descriptor.replace("\"activationVersion\":\"9\"", "\"activationVersion\":9"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.read(descriptor.replaceFirst("\\{", "{\"extra\":true,"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.read(descriptor.replaceFirst("\\{", "{\"activationVersion\":\"9\","))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.read(descriptor + "{}" )).isInstanceOf(IllegalArgumentException.class);
    }

    private String envelope() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true); body.put("code", ResultCode.SUCCESS.getCode()); body.put("status", "SUCCESS");
        body.put("message", "ok"); body.put("data", DdcI18nBundleAssemblerTest.descriptor().toMap()); body.put("traceId", null); body.put("timestamp", NOW.toEpochMilli());
        return CanonicalJson.write(body);
    }
}
