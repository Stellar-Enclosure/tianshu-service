package top.egon.cola.component.tianshu.admin.security.management;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportIdentityBO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

/** Adapts only the identity verifier's SERVICE authentication; headers and audit details are not identity inputs. */
public final class DdcSourceImportAuthentication {
    private DdcSourceImportAuthentication() { }

    public static DdcSourceImportIdentityBO identity(Authentication authentication) {
        if (!(authentication instanceof IdpAuthenticationToken verified) || !verified.isAuthenticated()
                || !(verified.getPrincipal() instanceof ServiceIdentityPrincipal service)) {
            throw new AccessDeniedException("TIANSHU_METADATA_SERVICE_IDENTITY_REQUIRED");
        }
        try {
            return new DdcSourceImportIdentityBO(verified.verifiedIssuer(), service);
        } catch (IllegalStateException missingIssuer) {
            throw new AccessDeniedException("TIANSHU_METADATA_VERIFIED_ISSUER_REQUIRED", missingIssuer);
        }
    }
}
