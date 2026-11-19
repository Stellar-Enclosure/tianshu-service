package top.egon.cola.component.tianshu.admin.security.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
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
import org.springframework.lang.Nullable;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** SERVICE-only internal API. Neither an HTTP selector nor a legacy credential can establish its authority. */
@Slf4j
@Configuration(value = "ddcAiInternalSecurityConfiguration", proxyBeanMethods = false)
@EnableConfigurationProperties(DdcAiInternalSecurityConfiguration.InternalProperties.class)
public class DdcAiInternalSecurityConfiguration {
    private static final String REQUEST_ID_ATTRIBUTE = DdcAiInternalSecurityConfiguration.class.getName() + ".requestId";
    private static final Map<String, Operation> PATHS = Map.of(
            DdcAiHttpContract.PROMPTS_PATH, Operation.READ,
            DdcAiHttpContract.SKILLS_PATH, Operation.READ,
            DdcAiHttpContract.VERSION_HEAD_PATH, Operation.READ,
            DdcAiHttpContract.SKILL_DRAFT_PATH, Operation.WRITE,
            DdcAiHttpContract.SKILL_PUBLICATIONS_PATH, Operation.PUBLISH,
            DdcAiHttpContract.USAGE_REPORTS_PATH, Operation.REPORT);

    @Bean("ddcAiServiceCallerResolver")
    public DdcAiServiceCallerResolver resolver(InternalProperties properties,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry registry,
            @Qualifier("ddcPublishClock") Clock clock) {
        return new DdcAiServiceCallerResolver(properties, registry, clock);
    }

    @Bean("ddcAiInternalSecurityFilterChain")
    @Order(0)
    public SecurityFilterChain security(HttpSecurity http,
            @Qualifier("userAccessTokenVerifier") UserAccessTokenVerifier users,
            @Qualifier("serviceAccessTokenVerifier") ServiceAccessTokenVerifier services,
            @Qualifier("idpSecurityObservationFilter") IdpSecurityObservationFilter observations,
            @Qualifier("ddcAiServiceCallerResolver") DdcAiServiceCallerResolver resolver,
            @Qualifier("ddcTenantContextExecutor") DdcTenantContextExecutor tenants,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper) throws Exception {
        var policy = new IdpEndpointAuthenticationPolicy(List.of(), List.copyOf(PATHS.keySet()), false);
        var bearer = new IdpBearerAuthenticationFilter(users, services, policy, mapper);
        http.securityMatcher(DdcAiHttpContract.BASE_PATH, DdcAiHttpContract.BASE_PATH + "/**")
                .csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true).securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, DdcAiHttpContract.ERROR_UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, failure) -> writeError(mapper, request, response, HttpStatus.FORBIDDEN, DdcAiHttpContract.ERROR_FORBIDDEN)));
        http.addFilterBefore(new AuthenticationBridge(bearer, mapper), AnonymousAuthenticationFilter.class);
        http.addFilterBefore(new StrictRequestFilter(mapper), AuthenticationBridge.class);
        http.addFilterBefore(observations, StrictRequestFilter.class);
        http.addFilterBefore(new CallerFilter(resolver, tenants, mapper), AuthorizationFilter.class);
        return http.build();
    }

    @ConfigurationProperties(value = "egon.cola.component.tianshu.ai.internal", ignoreUnknownFields = false)
    public record InternalProperties(Map<Operation, String> operationScopes, List<CallerBinding> callers) {
        public InternalProperties {
            operationScopes = operationScopes == null ? Map.of() : Map.copyOf(operationScopes);
            callers = callers == null ? List.of() : List.copyOf(callers);
            operationScopes.forEach((operation, scope) -> coordinate(scope, 256));
            Set<String> identities = new java.util.HashSet<>();
            Set<String> actors = new java.util.HashSet<>();
            for (CallerBinding caller : callers) {
                if (!operationScopes.keySet().containsAll(caller.operations())
                        || !identities.add(caller.identityTenantId() + "\u0000" + caller.issuer() + "\u0000" + caller.clientId())
                        || !actors.add(caller.tenantId() + "\u0000" + caller.stableActorId())) {
                    throw invalidBinding();
                }
            }
        }
    }

    public record CallerBinding(long tenantId, String identityTenantId, URI issuer, URI resourceUri,
            String clientId, String appId, String sourceBizCode, String sourceAppCode, String sourceEnvironment,
            String stableActorId, Set<DdcAiScopeDTO> placements, Set<Operation> operations) {
        public CallerBinding {
            if (tenantId <= 0) throw invalidBinding();
            coordinate(identityTenantId, 128); canonical(issuer); canonical(resourceUri);
            coordinate(clientId, 128); coordinate(appId, 128); coordinate(sourceBizCode, 128);
            coordinate(sourceAppCode, 128); coordinate(sourceEnvironment, 32); coordinate(stableActorId, 112);
            placements = placements == null ? Set.of() : Set.copyOf(placements);
            operations = operations == null ? Set.of() : Set.copyOf(operations);
            if (placements.isEmpty() || operations.isEmpty()) throw invalidBinding();
            for (DdcAiScopeDTO scope : placements) {
                coordinate(scope.bizCode(), 128); coordinate(scope.namespaceCode(), 128);
                coordinate(scope.env(), 32); coordinate(scope.appCode(), 128);
            }
        }
        boolean accepts(IdpAuthenticationToken token, ServiceIdentityPrincipal service) {
            try {
                return issuer.equals(token.verifiedIssuer()) && identityTenantId.equals(service.tenantId())
                        && resourceUri.equals(service.resourceUri()) && clientId.equals(service.clientId())
                        && appId.equals(service.appId()) && sourceBizCode.equals(service.sourceBizCode())
                        && sourceAppCode.equals(service.sourceAppCode()) && sourceEnvironment.equals(service.sourceEnvironment());
            } catch (IllegalStateException absentIssuer) {
                return false;
            }
        }
    }

    static final class StrictRequestFilter extends OncePerRequestFilter {
        private final ObjectMapper mapper;
        StrictRequestFilter(ObjectMapper mapper) { this.mapper = mapper; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Operation operation = PATHS.get(path(request));
            if (operation == null || !(operation == Operation.READ ? "GET" : "POST").equals(request.getMethod())) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, DdcAiHttpContract.ERROR_FORBIDDEN);
                return;
            }
            List<String> authorization = Collections.list(request.getHeaders("Authorization"));
            if (authorization.size() != 1 || !authorization.getFirst().matches("(?i)Bearer [^\\s,]+")) {
                writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, DdcAiHttpContract.ERROR_UNAUTHENTICATED);
                return;
            }
            if (operation != Operation.READ && !jsonContentType(request.getContentType())) {
                writeError(mapper, request, response, HttpStatus.BAD_REQUEST, DdcAiHttpContract.ERROR_INVALID_REQUEST);
                return;
            }
            // JSON POST parameters come only from the query. Never allow Servlet form parsing before verification.
            Set<String> allowed = operation == Operation.REPORT ? Set.of() : operation == Operation.READ
                    ? (DdcAiHttpContract.VERSION_HEAD_PATH.equals(path(request))
                    ? Set.of("tenantId", "bizCode", "env", "appCode", "namespaceCode", "resourceKey", "resourceId")
                    : Set.of("tenantId", "bizCode", "env", "appCode", "namespaceCode", "resourceKey", "resourceId", "versionNo"))
                    : Set.of("tenantId", "bizCode", "env", "appCode");
            for (var parameter : request.getParameterMap().entrySet()) {
                if (!allowed.contains(parameter.getKey()) || parameter.getValue() == null || parameter.getValue().length != 1) {
                    writeError(mapper, request, response, HttpStatus.BAD_REQUEST, DdcAiHttpContract.ERROR_INVALID_REQUEST);
                    return;
                }
            }
            if (operation == Operation.READ && (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null)) {
                writeError(mapper, request, response, HttpStatus.BAD_REQUEST, DdcAiHttpContract.ERROR_INVALID_REQUEST);
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /** Normalizes only verifier-generated early errors; successful response bodies are never buffered. */
    static final class AuthenticationBridge extends OncePerRequestFilter {
        private final IdpBearerAuthenticationFilter bearer;
        private final ObjectMapper mapper;
        AuthenticationBridge(IdpBearerAuthenticationFilter bearer, ObjectMapper mapper) { this.bearer = bearer; this.mapper = mapper; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (IdpSecurityObservationFilter.authorizationObservationContext(request) == null) {
                writeResponse(mapper, request, response, HttpStatus.SERVICE_UNAVAILABLE, DdcAiHttpContract.ERROR_UNAVAILABLE);
                return;
            }
            boolean[] continued = {false};
            var early = new HttpServletResponseWrapper(response) {
                private int bytes;
                @Override public ServletOutputStream getOutputStream() {
                    return new ServletOutputStream() {
                        @Override public void write(int value) throws IOException {
                            if (++bytes > DdcAiHttpContract.MAX_ERROR_BODY_BYTES) throw new IOException("TIANSHU_AI_VERIFICATION_RESPONSE_INVALID");
                        }
                        @Override public boolean isReady() { return true; }
                        @Override public void setWriteListener(WriteListener listener) { throw new IllegalStateException("ASYNC_NOT_SUPPORTED"); }
                    };
                }
            };
            bearer.doFilter(request, early, (verifiedRequest, ignoredResponse) -> {
                continued[0] = true;
                chain.doFilter(verifiedRequest, response);
            });
            if (!continued[0]) {
                HttpStatus status = response.getStatus() == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.SERVICE_UNAVAILABLE;
                writeResponse(mapper, request, response, status, status == HttpStatus.UNAUTHORIZED
                        ? DdcAiHttpContract.ERROR_UNAUTHENTICATED : DdcAiHttpContract.ERROR_UNAVAILABLE);
            }
        }
    }

    static final class CallerFilter extends OncePerRequestFilter {
        private final DdcAiServiceCallerResolver resolver;
        private final DdcTenantContextExecutor tenants;
        private final ObjectMapper mapper;
        CallerFilter(DdcAiServiceCallerResolver resolver, DdcTenantContextExecutor tenants, ObjectMapper mapper) {
            this.resolver = resolver; this.tenants = tenants; this.mapper = mapper;
        }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Operation operation = PATHS.get(path(request));
            try {
                if (IdpSecurityObservationFilter.authorizationObservationContext(request) == null) {
                    throw new DdcAiServiceCallerResolver.Unavailable();
                }
                var caller = resolver.resolve(operation);
                DdcAiClientScope selector = operation == Operation.REPORT ? null : selector(request);
                try (var ignored = tenants.bind(caller.tenant())) {
                    resolver.within(caller, operation, selector, () -> { chain.doFilter(request, response); return null; });
                }
            } catch (DdcAiServiceCallerResolver.Rejected denied) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, DdcAiHttpContract.ERROR_FORBIDDEN);
            } catch (DdcAiServiceCallerResolver.Unavailable unavailable) {
                writeResponse(mapper, request, response, HttpStatus.SERVICE_UNAVAILABLE, DdcAiHttpContract.ERROR_UNAVAILABLE);
            } catch (IllegalArgumentException invalid) {
                writeResponse(mapper, request, response, HttpStatus.BAD_REQUEST, DdcAiHttpContract.ERROR_INVALID_REQUEST);
            } catch (IOException | ServletException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new ServletException("TIANSHU_AI_EXECUTION_UNAVAILABLE");
            }
        }
    }

    private static DdcAiClientScope selector(HttpServletRequest request) {
        return new DdcAiClientScope(decimal(request.getParameter("tenantId"), false),
                checked(request.getParameter("bizCode"), 128), checked(request.getParameter("env"), 32),
                checked(request.getParameter("appCode"), 128));
    }

    public static long decimal(String value, boolean zeroAllowed) {
        if (value == null || !value.matches(zeroAllowed ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("TIANSHU_AI_INVALID_REQUEST");
        }
        try { return Long.parseLong(value); }
        catch (NumberFormatException overflow) { throw new IllegalArgumentException("TIANSHU_AI_INVALID_REQUEST"); }
    }

    public static String checked(String value, int max) { coordinate(value, max); return value; }
    public static String requestId(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String known) return known;
        String trace = MDC.get("traceId");
        String value = trace != null && trace.matches("[A-Za-z0-9._~-]{1,128}") ? trace : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, value);
        return value;
    }

    public static void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String code) throws IOException {
        boolean authorizationFailure = status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN;
        boolean audited = (!authorizationFailure || IdpSecurityObservationFilter.authorizationObservationContext(request) != null)
                && (status == HttpStatus.UNAUTHORIZED ? IdpSecurityObservationFilter.beforeAuthenticationFailure(request)
                : status != HttpStatus.FORBIDDEN || IdpSecurityObservationFilter.beforeAuthorizationDenied(request));
        writeResponse(mapper, request, response, audited ? status : HttpStatus.SERVICE_UNAVAILABLE,
                audited ? code : DdcAiHttpContract.ERROR_UNAVAILABLE);
    }

    public static void writeResponse(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "Authorization");
        response.setHeader("x-egon-request-id", requestId(request));
        if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("code", code, "message", "AI operation could not be completed", "requestId", requestId(request)));
    }

    private static boolean jsonContentType(@Nullable String value) {
        if (value == null) return false;
        try {
            MediaType type = MediaType.parseMediaType(value);
            return MediaType.APPLICATION_JSON.isCompatibleWith(type)
                    && (type.getCharset() == null || StandardCharsets.UTF_8.equals(type.getCharset()))
                    && type.getParameters().keySet().stream().allMatch("charset"::equalsIgnoreCase);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static String path(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()); }
    private static void canonical(@Nullable URI value) {
        if (value == null || !"https".equals(value.getScheme()) || value.getHost() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null) throw invalidBinding();
    }
    private static void coordinate(@Nullable String value, int max) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) throw invalidBinding();
    }
    private static IllegalArgumentException invalidBinding() { return new IllegalArgumentException("TIANSHU_AI_CALLER_BINDING_INVALID"); }
}
