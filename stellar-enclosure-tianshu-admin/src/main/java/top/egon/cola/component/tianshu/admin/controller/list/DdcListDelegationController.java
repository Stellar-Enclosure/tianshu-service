package top.egon.cola.component.tianshu.admin.controller.list;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.security.management.DdcListDelegationVerifier;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.service.list.DdcListTemplateService;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;
import top.egon.cola.component.tianshu.api.list.ListDelegationResponse;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

@RestController
@RequestMapping(path = "/internal/v1/tianshu/list-delegation", produces = MediaType.APPLICATION_JSON_VALUE)
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域", entityDomainCode = "tianshu-admin",
        entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@RequiredArgsConstructor
public class DdcListDelegationController {
    @Qualifier("ddcListDelegationVerifier") private final DdcListDelegationVerifier verifier;
    @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor contexts;
    @Qualifier("ddcListTemplateService") private final DdcListTemplateService templates;

    @PermissionResource(key = "tianshu.api.listTemplate.delegate", type = ResourceType.API, labelPage = "tianshu.permissions",
            labelKey = "api.listTemplate.delegate", allowedPrincipals = PermissionManifest.PrincipalType.SERVICE,
            oauthScopes = DdcListDelegationVerifier.REQUIRED_SCOPE)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @Operation(operationId = "tianshuDelegatedListTemplate", summary = "以已验证的服务和用户身份访问列表模板")
    public ResponseEntity<ResultRecord<ListDelegationResponse>> execute(Authentication authentication, HttpServletRequest http,
            @RequestBody ListDelegationRequest request) {
        var headers = java.util.Collections.list(http.getHeaders("X-Egon-Subject-Token"));
        if (headers.size() != 1 || headers.getFirst().isBlank() || headers.getFirst().length() > 16_384
                || !headers.getFirst().equals(headers.getFirst().trim())) throw new IllegalArgumentException("A single user proof is required");
        String token = headers.getFirst();
        var authorization = java.util.Collections.list(http.getHeaders(HttpHeaders.AUTHORIZATION));
        if (authorization.size() != 1 || !authorization.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new IllegalArgumentException("A single service proof is required");
        }
        String serviceToken = authorization.getFirst().substring(7).trim();
        if (serviceToken.isEmpty() || serviceToken.length() > 8192 || serviceToken.contains(",")) throw new IllegalArgumentException("Invalid service proof");
        var verified = verifier.verify(authentication, serviceToken, token);
        var service = verified.service();
        var tenant = new DdcTenantContextDTO(Long.valueOf(service.tenantId()), service.tenantId(), "service:" + service.clientId());
        var result = contexts.call(tenant, () -> templates.executeDelegated(verified, request, () -> verifier.verify(authentication, serviceToken, token)));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(ResultRecord.success(result));
    }
}
