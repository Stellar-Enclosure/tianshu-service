package top.egon.cola.component.tianshu.admin.model.bo;

import org.springframework.security.access.AccessDeniedException;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Internal adapter input built only from an authenticated SERVICE token and its verified issuer. */
public record DdcSourceImportIdentityBO(URI issuer, ServiceIdentityPrincipal principal) {
    public static final String REQUIRED_SCOPE = "TIANSHU_METADATA_IMPORT";

    public DdcSourceImportIdentityBO {
        Objects.requireNonNull(issuer, "verified issuer");
        Objects.requireNonNull(principal, "verified SERVICE principal");
        if (!"https".equalsIgnoreCase(issuer.getScheme()) || issuer.getHost() == null || issuer.getUserInfo() != null
                || issuer.getQuery() != null || issuer.getFragment() != null || issuer.toString().length() > 2048
                || principal.scopeContext() != ServiceTokenContext.TENANT || !principal.scopes().contains(REQUIRED_SCOPE)
                || principal.clientId().length() > 200 || principal.resourceUri().toString().length() > 2048) {
            throw new AccessDeniedException("TIANSHU_METADATA_SERVICE_GRANT_REQUIRED");
        }
        try {
            long tenant = Long.parseLong(principal.tenantId());
            if (tenant <= 0 || !Long.toString(tenant).equals(principal.tenantId())) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            throw new AccessDeniedException("TIANSHU_METADATA_TENANT_ID_INVALID");
        }
    }

    public Long tenantId() { return Long.valueOf(principal.tenantId()); }

    public String bindingDigest() {
        return CanonicalJson.digest(Map.of("issuer", issuer.toString(), "clientId", principal.clientId(),
                "resourceUri", principal.resourceUri().toString()));
    }
}
