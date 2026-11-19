package top.egon.cola.component.tianshu.admin.security.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** A separate SERVICE-only boundary; it cannot select a tenant from request data or USER state. */
@Slf4j
@Configuration(value = "ddcLogicalCatalogSecurityConfiguration", proxyBeanMethods = false)
@EnableConfigurationProperties(DdcLogicalCatalogSecurityConfiguration.CatalogProperties.class)
public class DdcLogicalCatalogSecurityConfiguration {
    public static final String TRUSTED_TENANT_ATTRIBUTE = "top.egon.cola.component.tianshu.admin.security.management.DdcLogicalCatalogSecurityConfiguration.trustedTenant";
    private static final String REQUEST_ID_ATTRIBUTE = "top.egon.cola.component.tianshu.admin.security.management.DdcLogicalCatalogSecurityConfiguration.requestId";
    private static final List<String> PATHS = List.of(DdcLogicalCatalogContract.BUSINESS_PATH, DdcLogicalCatalogContract.BUSINESSES_PATH,
            DdcLogicalCatalogContract.APPLICATION_PATH, DdcLogicalCatalogContract.APPLICATIONS_PATH);

    @Bean("ddcLogicalCatalogSecurityFilterChain")
    @Order(0)
    public SecurityFilterChain logicalCatalogSecurity(HttpSecurity http,
            @Qualifier("userAccessTokenVerifier") UserAccessTokenVerifier users,
            @Qualifier("serviceAccessTokenVerifier") ServiceAccessTokenVerifier services,
            @Qualifier("idpSecurityObservationFilter") IdpSecurityObservationFilter observations,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry registry,
            @Qualifier("ddcTenantContextExecutor") DdcTenantContextExecutor tenants,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper, CatalogProperties properties) throws Exception {
        var localPolicy = new IdpEndpointAuthenticationPolicy(List.of(), PATHS, false);
        var bearer = new IdpBearerAuthenticationFilter(users, services, localPolicy, mapper);
        http.securityMatcher(DdcLogicalCatalogContract.BASE_PATH, DdcLogicalCatalogContract.BASE_PATH + "/**")
                .csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true).securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, "TIANSHU_CATALOG_AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, failure) -> writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CATALOG_ACCESS_DENIED")));
        http.addFilterBefore(bearer, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(new StrictRequestFilter(mapper), IdpBearerAuthenticationFilter.class);
        http.addFilterBefore(observations, StrictRequestFilter.class);
        http.addFilterBefore(new CatalogTenantFilter(properties, registry, tenants, mapper), AuthorizationFilter.class);
        return http.build();
    }

    @ConfigurationProperties("egon.cola.component.tianshu.logical-catalog")
    public record CatalogProperties(Map<String, CallerBinding> callers) {
        public CatalogProperties {
            callers = callers == null ? Map.of() : Map.copyOf(callers);
            callers.forEach((tenant, caller) -> {
                coordinate(tenant);
                if (caller == null) throw new IllegalArgumentException("TIANSHU_CATALOG_CALLER_BINDING_INVALID");
            });
        }
    }

    public record CallerBinding(URI issuer, URI resourceUri, String clientId, String appId,
            String sourceBizCode, String sourceAppCode, String sourceEnvironment) {
        public CallerBinding {
            canonical(issuer); canonical(resourceUri);
            coordinate(clientId); coordinate(appId); coordinate(sourceBizCode); coordinate(sourceAppCode); coordinate(sourceEnvironment);
        }
        boolean accepts(IdpAuthenticationToken token, ServiceIdentityPrincipal service) {
            final URI verifiedIssuer;
            try { verifiedIssuer = token.verifiedIssuer(); }
            catch (IllegalStateException missingIssuer) { return false; }
            if (!issuer.equals(verifiedIssuer) || !resourceUri.equals(service.resourceUri())
                    || !clientId.equals(service.clientId()) || !appId.equals(service.appId())
                    || !sourceBizCode.equals(service.sourceBizCode()) || !sourceAppCode.equals(service.sourceAppCode())
                    || !sourceEnvironment.equals(service.sourceEnvironment())) return false;
            return token.getDetails() instanceof TokenAuditProofBO proof
                    && issuer.equals(proof.issuer()) && resourceUri.equals(proof.resource());
        }
    }

    static final class StrictRequestFilter extends OncePerRequestFilter {
        private final ObjectMapper mapper;
        StrictRequestFilter(ObjectMapper mapper) { this.mapper = mapper; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path = path(request);
            if (!"GET".equals(request.getMethod()) || !PATHS.contains(path)) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CATALOG_ENDPOINT_NOT_ALLOWED"); return;
            }
            var credentials = Collections.list(request.getHeaders("Authorization"));
            if (credentials.size() != 1 || !credentials.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)
                    || credentials.getFirst().substring(7).isBlank()) {
                writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, "TIANSHU_CATALOG_AUTHENTICATION_REQUIRED"); return;
            }
            // A query selector is never an identity source; unknown selectors cannot silently succeed.
            Set<String> allowed = Set.of("keyword");
            if (DdcLogicalCatalogContract.BUSINESS_PATH.equals(path) || DdcLogicalCatalogContract.APPLICATION_PATH.equals(path)) allowed = Set.of("id");
            else if (DdcLogicalCatalogContract.APPLICATIONS_PATH.equals(path)) allowed = Set.of("businessId", "keyword");
            for (String name : request.getParameterMap().keySet()) {
                String[] values = request.getParameterValues(name);
                if (!allowed.contains(name) || values == null || values.length != 1) {
                    writeError(mapper, request, response, HttpStatus.BAD_REQUEST, "TIANSHU_CATALOG_REQUEST_INVALID"); return;
                }
            }
            chain.doFilter(request, response);
        }
    }

    static final class CatalogTenantFilter extends OncePerRequestFilter {
        private final CatalogProperties properties;
        private final DdcTenantRegistry registry;
        private final DdcTenantContextExecutor tenants;
        private final ObjectMapper mapper;
        CatalogTenantFilter(CatalogProperties properties, DdcTenantRegistry registry, DdcTenantContextExecutor tenants, ObjectMapper mapper) {
            this.properties = properties; this.registry = registry; this.tenants = tenants; this.mapper = mapper;
        }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof IdpAuthenticationToken token) || !token.isAuthenticated()
                    || !(token.getPrincipal() instanceof ServiceIdentityPrincipal service)) {
                writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, "TIANSHU_CATALOG_AUTHENTICATION_REQUIRED"); return;
            }
            if (service.scopeContext() != ServiceTokenContext.TENANT || service.tenantId() == null || service.tenantId().isBlank()) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CATALOG_ACCESS_DENIED"); return;
            }
            CallerBinding binding = properties.callers().get(service.tenantId());
            Set<String> authorities = token.getAuthorities().stream().map(value -> value.getAuthority()).collect(Collectors.toSet());
            Set<String> expected = service.scopes().stream().map(value -> "SCOPE_" + value).collect(Collectors.toSet());
            if (service.scopeContext() != ServiceTokenContext.TENANT || service.resourceVersion() <= 0
                    || !service.scopes().contains(DdcLogicalCatalogContract.SCOPE) || !authorities.equals(expected)
                    || binding == null || !binding.accepts(token, service)) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CATALOG_ACCESS_DENIED"); return;
            }
            final DdcTenantContextDTO trusted;
            try {
                var mapped = registry.requireByIdentityTenantId(service.tenantId());
                if (mapped == null || mapped.getTenantId() == null || mapped.getTenantId() <= 0 || !mapped.isEnabled()
                        || !service.tenantId().equals(mapped.getIdentityTenantId())) throw new IllegalArgumentException("TENANT_IDENTITY_UNREGISTERED");
                trusted = new DdcTenantContextDTO(mapped.getTenantId(), mapped.getIdentityTenantId(), "service:" + service.credentialId());
            } catch (IllegalArgumentException unknown) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CATALOG_TENANT_DENIED"); return;
            } catch (RuntimeException unavailable) {
                log.warn("Logical catalog tenant mapping unavailable; code=TIANSHU_CATALOG_TENANT_UNAVAILABLE");
                writeError(mapper, request, response, HttpStatus.SERVICE_UNAVAILABLE, "TIANSHU_CATALOG_TENANT_UNAVAILABLE"); return;
            }
            try (var ignored = tenants.bind(trusted)) {
                request.setAttribute(TRUSTED_TENANT_ATTRIBUTE, trusted);
                chain.doFilter(request, response);
            } finally { request.removeAttribute(TRUSTED_TENANT_ATTRIBUTE); }
        }
    }

    public static String requestId(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String known) return known;
        String trace = MDC.get("traceId");
        String value = trace != null && trace.matches("[A-Za-z0-9._~-]{1,128}") ? trace : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, value);
        return value;
    }
    public static void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        boolean audited = status == HttpStatus.UNAUTHORIZED ? IdpSecurityObservationFilter.beforeAuthenticationFailure(request)
                : status != HttpStatus.FORBIDDEN || IdpSecurityObservationFilter.beforeAuthorizationDenied(request);
        HttpStatus effective = audited ? status : HttpStatus.SERVICE_UNAVAILABLE;
        response.setStatus(effective.value());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "Authorization");
        response.setHeader("x-egon-request-id", requestId(request));
        if (effective == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("errorCode", audited ? code : "OAUTH_AUDIT_DELIVERY_UNAVAILABLE", "requestId", requestId(request)));
    }
    private static String path(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()); }
    private static void canonical(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("TIANSHU_CATALOG_CALLER_BINDING_INVALID");
        }
    }
    private static void coordinate(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("TIANSHU_CATALOG_CALLER_BINDING_INVALID");
    }
}
