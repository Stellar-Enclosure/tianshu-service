package top.egon.cola.component.tianshu.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "egon.cola.component.tianshu.catalog-authority")
public class DdcCatalogActivationProperties {
    private Map<String, Binding> bindings = new LinkedHashMap<>();

    public record Binding(CatalogScope target, String producerId, URI baseUri, URI resourceUri,
            String registrationId, String appId) {
        public Binding {
            if (target == null || producerId == null || !producerId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                    || registrationId == null || registrationId.isBlank() || registrationId.length() > 128
                    || appId == null || appId.isBlank() || appId.length() > 128) {
                throw new IllegalArgumentException("Invalid catalog authority binding");
            }
            secure(baseUri); secure(resourceUri);
            if (!baseUri.getPath().endsWith("/")) throw new IllegalArgumentException("Catalog authority base URI must end with /");
        }

        private static void secure(URI uri) {
            if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.normalize().equals(uri)
                    || !uri.toASCIIString().equals(uri.toString())) throw new IllegalArgumentException("Catalog authority requires a fixed HTTPS URI");
        }
    }
}
