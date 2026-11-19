package top.egon.cola.component.tianshu.admin.permission;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

/**
 * Compile-time catalog of the supported machine-protocol methods, not a Spring bean or handler.
 * The dedicated delivery security chain owns SERVICE OAuth scopes and projected Pod credentials.
 * Methods are explicit: unlike an MVC GET mapping, this catalog does not implicitly authorize HEAD.
 */
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.configDelivery.snapshot", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an authorized workload configuration snapshot"))
@PermissionMessage(pageCode = "tianshu.permissions", key = "api.configDelivery.receipt", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Report an authorized workload configuration receipt"))
public final class DdcConfigDeliveryPermissionMetadata {
    private DdcConfigDeliveryPermissionMetadata() { }

    @RequestMapping(path = "/internal/v1/config/snapshot", method = RequestMethod.GET)
    @PermissionResource(key = "tianshu.api.configDelivery.snapshot", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.configDelivery.snapshot", access = ResourceAccess.INTERNAL,
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcConfigWorkloadVerifier.READ_SCOPE)
    public static void configSnapshot() { throw new UnsupportedOperationException("metadata only"); }

    @RequestMapping(path = "/internal/v1/config/receipts", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PermissionResource(key = "tianshu.api.configDelivery.receipt", type = ResourceType.API,
            labelPage = "tianshu.permissions", labelKey = "api.configDelivery.receipt", access = ResourceAccess.INTERNAL,
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcConfigWorkloadVerifier.RECEIPT_SCOPE)
    public static void reportConfigReceipt() { throw new UnsupportedOperationException("metadata only"); }
}
