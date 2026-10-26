package top.egon.cola.component.tianshu.admin.service.i18n;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.config.DdcCatalogActivationProperties;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ActivationDescriptor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ManifestRegistrationRequest;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Fetches one immutable activation over a configured SERVICE identity, before local SQL transactions. */
@Component("ddcCatalogActivationClient")
@EnableConfigurationProperties(DdcCatalogActivationProperties.class)
public class DdcCatalogActivationClient {
    public static final String READ_SCOPE = "tianquan-jianshen:permission-catalog:read";
    private final Map<String, DdcCatalogActivationProperties.Binding> bindings;
    private final IdpServiceOAuth2Client oauth;
    private final DdcCatalogActivationJson json;
    private final RestClient transport;

    @Autowired
    public DdcCatalogActivationClient(DdcCatalogActivationProperties properties, IdpServiceOAuth2Client oauth, DdcCatalogActivationJson json) {
        this(properties, oauth, json, boundedTransport());
    }

    DdcCatalogActivationClient(DdcCatalogActivationProperties properties, IdpServiceOAuth2Client oauth, DdcCatalogActivationJson json, RestClient transport) {
        this.bindings = Map.copyOf(properties.getBindings());
        if (bindings.size() > 1024 || bindings.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().target().catalogScopeId()))) {
            throw new IllegalArgumentException("Invalid catalog authority bindings");
        }
        this.oauth = oauth; this.json = json; this.transport = transport;
    }

    public ActivationDescriptor fetch(CatalogScope scope, String version) {
        if (!EgonColaTenantIdProvider.currentTenantId().toString().equals(scope.tenantId())) throw unavailable();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("CATALOG_HTTP_INSIDE_SQL_TRANSACTION");
        if (version == null || !version.matches("[1-9][0-9]{0,18}") || Long.parseLong(version) <= 0) throw DdcI18nValidation.invalid("Invalid activation version");
        var binding = bindings.get(scope.catalogScopeId());
        if (binding == null || !binding.target().equals(scope)) throw unavailable();
        var request = new IdpServiceTokenRequest(binding.registrationId(), binding.appId(), binding.resourceUri(),
                ServiceTokenContext.TENANT, scope.tenantId(), Set.of(READ_SCOPE));
        var uri = UriComponentsBuilder.fromUri(binding.baseUri().resolve("internal/v1/permission-catalogs/" + scope.catalogScopeId() + "/activations/" + version));
        ManifestRegistrationRequest.targetMap(scope).forEach(uri::queryParam);
        uri.queryParam("producerId", binding.producerId());
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                Exchange response = transport.get().uri(uri.build().encode().toUri())
                        .headers(headers -> headers.setBearerAuth(oauth.authorize(request).getTokenValue())).accept(MediaType.APPLICATION_JSON)
                        .exchange((outgoing, incoming) -> {
                            int status = incoming.getStatusCode().value();
                            if (status == 401) return new Exchange(status, null);
                            if (status == 404) throw missing();
                            if (status != 200) throw unavailable();
                            byte[] bytes = incoming.getBody().readNBytes(DdcCatalogActivationJson.MAX_RESPONSE_BYTES + 1);
                            return new Exchange(status, json.readResponse(bytes));
                        });
                if (response == null) throw unavailable();
                if (response.status() == 200) {
                    if (!response.descriptor().scope().equals(scope) || !response.descriptor().activationVersion().equals(version)) throw unavailable();
                    return response.descriptor();
                }
                oauth.invalidate(request);
            }
            throw unavailable();
        } catch (CommonException failure) { throw failure; }
        catch (RuntimeException failure) { throw unavailable(); }
    }

    public String authorityResource(CatalogScope scope) {
        var binding = bindings.get(scope.catalogScopeId());
        if (binding == null || !binding.target().equals(scope)) throw unavailable();
        return binding.resourceUri().toASCIIString();
    }

    private static RestClient boundedTransport() {
        Duration timeout = Duration.ofSeconds(5);
        var client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client); factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    static CommonException unavailable() { return new CommonException(56431, "TIANSHU_CATALOG_UNAVAILABLE", "The requested catalog is unavailable"); }
    static CommonException missing() { return new CommonException(56432, "TIANSHU_CATALOG_NOT_FOUND", "The requested activation is not available"); }
    private record Exchange(int status, ActivationDescriptor descriptor) { }
}
