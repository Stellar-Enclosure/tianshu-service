package top.egon.cola.component.tianshu.admin.controller.metadata;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import top.egon.cola.component.tianshu.admin.security.management.DdcLogicalCatalogSecurityConfiguration;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcLogicalCatalogQueryService;
import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;

import java.util.List;
import java.util.Map;

/** Bare, read-only logical metadata. The dedicated SERVICE chain installs the mapped tenant scope. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController("ddcLogicalCatalogController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin", entityDomainName = "天枢逻辑目录", interfaceGroupCode = "tianshu")
@SecurityScheme(name = "tianshuLogicalCatalogServiceBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@ApiResponses({@ApiResponse(responseCode = "400", description = "Invalid selector"),
        @ApiResponse(responseCode = "401", description = "SERVICE bearer required"),
        @ApiResponse(responseCode = "403", description = "Caller, scope or tenant denied"),
        @ApiResponse(responseCode = "503", description = "Current authority, tenant mapping or catalog unavailable")})
public class DdcLogicalCatalogController {
    @Qualifier("ddcLogicalCatalogQueryService")
    private final DdcLogicalCatalogQueryService queries;

    @GetMapping(DdcLogicalCatalogContract.BUSINESS_PATH)
    @Operation(operationId = "findLogicalCatalogBusiness", summary = "Find a business by its public identifier",
            security = @SecurityRequirement(name = "tianshuLogicalCatalogServiceBearer"))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Authorized business, including disabled entries"),
            @ApiResponse(responseCode = "404", description = "Authorized lookup found no entry; X-Egon-Catalog-Outcome is not-found")})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @PermissionResource(key = "tianshu.api.logicalCatalog.findBusiness", type = ResourceType.API, access = ResourceAccess.INTERNAL,
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE,
            labelPage = "tianshu.permissions", labelKey = "api.logicalCatalog.findBusiness")
    public ResponseEntity<DdcManagementBiz> findBusiness(
            @RequestParam("id") @NotBlank @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String id,
            @Parameter(hidden = true) HttpServletRequest request) {
        requireScope(request);
        identifier(id);
        return queries.findBusiness(id).map(value -> new ResponseEntity<>(value, headers(request), HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(null, missingHeaders(request), HttpStatus.NOT_FOUND));
    }

    @GetMapping(DdcLogicalCatalogContract.BUSINESSES_PATH)
    @Operation(operationId = "listLogicalCatalogBusinesses", summary = "List authorized businesses, including disabled entries",
            security = @SecurityRequirement(name = "tianshuLogicalCatalogServiceBearer"))
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @PermissionResource(key = "tianshu.api.logicalCatalog.listBusinesses", type = ResourceType.API, access = ResourceAccess.INTERNAL,
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE,
            labelPage = "tianshu.permissions", labelKey = "api.logicalCatalog.listBusinesses")
    public ResponseEntity<List<DdcManagementBiz>> listBusinesses(
            @RequestParam(value = "keyword", required = false) @Size(max = DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH) String keyword,
            @Parameter(hidden = true) HttpServletRequest request) {
        requireScope(request);
        keyword(keyword);
        return new ResponseEntity<>(queries.listBusinesses(keyword), headers(request), HttpStatus.OK);
    }

    @GetMapping(DdcLogicalCatalogContract.APPLICATION_PATH)
    @Operation(operationId = "findLogicalCatalogApplication", summary = "Find an application by its public identifier",
            security = @SecurityRequirement(name = "tianshuLogicalCatalogServiceBearer"))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Authorized application and parent enable state"),
            @ApiResponse(responseCode = "404", description = "Authorized lookup found no entry; X-Egon-Catalog-Outcome is not-found")})
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @PermissionResource(key = "tianshu.api.logicalCatalog.findApplication", type = ResourceType.API, access = ResourceAccess.INTERNAL,
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE,
            labelPage = "tianshu.permissions", labelKey = "api.logicalCatalog.findApplication")
    public ResponseEntity<DdcManagementApp> findApplication(
            @RequestParam("id") @NotBlank @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String id,
            @Parameter(hidden = true) HttpServletRequest request) {
        requireScope(request);
        identifier(id);
        return queries.findApplication(id).map(value -> new ResponseEntity<>(value, headers(request), HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(null, missingHeaders(request), HttpStatus.NOT_FOUND));
    }

    @GetMapping(DdcLogicalCatalogContract.APPLICATIONS_PATH)
    @Operation(operationId = "listLogicalCatalogApplications", summary = "List applications by optional parent public identifier and keyword",
            security = @SecurityRequirement(name = "tianshuLogicalCatalogServiceBearer"))
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @PermissionResource(key = "tianshu.api.logicalCatalog.listApplications", type = ResourceType.API, access = ResourceAccess.INTERNAL,
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = DdcLogicalCatalogContract.SCOPE,
            labelPage = "tianshu.permissions", labelKey = "api.logicalCatalog.listApplications")
    public ResponseEntity<List<DdcManagementApp>> listApplications(
            @RequestParam(value = "businessId", required = false) @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String businessId,
            @RequestParam(value = "keyword", required = false) @Size(max = DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH) String keyword,
            @Parameter(hidden = true) HttpServletRequest request) {
        requireScope(request);
        if (businessId != null) identifier(businessId);
        keyword(keyword);
        return new ResponseEntity<>(queries.listApplications(businessId, keyword), headers(request), HttpStatus.OK);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            ServletRequestBindingException.class, InvalidSelector.class})
    public ResponseEntity<Map<String, String>> invalid(Exception ignored, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "TIANSHU_CATALOG_REQUEST_INVALID");
    }
    @ExceptionHandler({AccessDeniedException.class, Rbac3AuthorizationClient.AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, String>> denied(RuntimeException ignored, HttpServletRequest request) {
        boolean audited = IdpSecurityObservationFilter.beforeAuthorizationDenied(request);
        return error(request, audited ? HttpStatus.FORBIDDEN : HttpStatus.SERVICE_UNAVAILABLE,
                audited ? "TIANSHU_CATALOG_ACCESS_DENIED" : "OAUTH_AUDIT_DELIVERY_UNAVAILABLE");
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> unavailable(RuntimeException ignored, HttpServletRequest request) {
        log.warn("Logical catalog storage unavailable; code=TIANSHU_CATALOG_UNAVAILABLE");
        return error(request, HttpStatus.SERVICE_UNAVAILABLE, "TIANSHU_CATALOG_UNAVAILABLE");
    }
    private static ResponseEntity<Map<String, String>> error(HttpServletRequest request, HttpStatus status, String code) {
        return new ResponseEntity<>(Map.of("errorCode", code, "requestId", DdcLogicalCatalogSecurityConfiguration.requestId(request)), headers(request), status);
    }
    private static HttpHeaders headers(HttpServletRequest request) {
        var headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setVary(List.of(HttpHeaders.AUTHORIZATION));
        headers.set("x-egon-request-id", DdcLogicalCatalogSecurityConfiguration.requestId(request));
        return headers;
    }
    private static HttpHeaders missingHeaders(HttpServletRequest request) {
        var headers = headers(request);
        headers.set(DdcLogicalCatalogContract.OUTCOME_HEADER, DdcLogicalCatalogContract.OUTCOME_NOT_FOUND);
        return headers;
    }
    private static void requireScope(HttpServletRequest request) {
        if (!(request.getAttribute(DdcLogicalCatalogSecurityConfiguration.TRUSTED_TENANT_ATTRIBUTE) instanceof DdcTenantContextDTO trusted)
                || !Long.toString(trusted.tenantId()).equals(MDC.get("tenantId")) || !trusted.actor().equals(MDC.get("userId"))) {
            throw new AccessDeniedException("TIANSHU_CATALOG_ACCESS_DENIED");
        }
    }
    private static void identifier(String value) {
        if (value == null || value.isBlank() || value.length() > DdcLogicalCatalogContract.MAX_ID_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl)) throw new InvalidSelector();
    }
    private static final class InvalidSelector extends IllegalArgumentException {
        private InvalidSelector() { super("TIANSHU_CATALOG_REQUEST_INVALID"); }
    }
    private static void keyword(String value) {
        if (value != null && (value.length() > DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl))) throw new InvalidSelector();
    }
}
