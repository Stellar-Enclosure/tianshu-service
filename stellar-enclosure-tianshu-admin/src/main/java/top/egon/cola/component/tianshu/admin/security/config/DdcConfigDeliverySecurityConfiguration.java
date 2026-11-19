package top.egon.cola.component.tianshu.admin.security.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.egon.cola.component.tianshu.admin.internal.controller.DdcConfigDeliveryController;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigSnapshotService;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration(value = "ddcConfigDeliverySecurityConfiguration", proxyBeanMethods = false)
@ConditionalOnProperty(prefix = DdcConfigDeliverySecurityConfiguration.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DdcConfigDeliverySecurityConfiguration.DeliveryProperties.class)
public class DdcConfigDeliverySecurityConfiguration {

    public static final String PREFIX = "egon.cola.component.tianshu.admin.config-delivery";
    public static final String SNAPSHOT_PATH = "/internal/v1/config/snapshot";
    public static final String RECEIPT_PATH = "/internal/v1/config/receipts";
    public static final String WORKLOAD_HEADER = "X-Egon-Workload-Token";
    public static final String BOOT_HEADER = "X-Config-Boot-Id";
    private static final String REQUEST_ID_ATTRIBUTE = DdcConfigDeliverySecurityConfiguration.class.getName() + ".requestId";
    private static final String INTERNAL_OPEN_API_GROUP = "tianshu-config-internal";
    private static final String INTERNAL_OPEN_API_PATH = "/v3/api-docs/" + INTERNAL_OPEN_API_GROUP;

    @ConfigurationProperties(PREFIX)
    public record DeliveryProperties(URI apiServer, Path apiCaFile, Path apiTokenFile,
            Duration apiTimeout, int apiMaxResponseBytes, String workloadAudience,
            List<DdcConfigWorkloadVerifier.BindingVO> bindings, Path signingKeyringFile,
            Duration deliveryTokenTtl, Duration snapshotValidity) {
        public DeliveryProperties {
            if (snapshotValidity == null || snapshotValidity.compareTo(Duration.ofSeconds(10)) < 0
                    || snapshotValidity.compareTo(Duration.ofHours(24)) > 0) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_SNAPSHOT_VALIDITY_INVALID");
            }
        }
    }

    @Bean("ddcKubernetesApiClient")
    DdcKubernetesApiClient ddcKubernetesApiClient(DeliveryProperties settings,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
        return new DdcKubernetesApiClient(settings.apiServer(), settings.apiCaFile(), settings.apiTokenFile(),
                mapper, settings.apiTimeout(), settings.apiMaxResponseBytes());
    }

    @Bean("ddcConfigWorkloadVerifier")
    DdcConfigWorkloadVerifier ddcConfigWorkloadVerifier(@Qualifier("ddcKubernetesApiClient") DdcKubernetesApiClient api,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants, DeliveryProperties settings) {
        return new DdcConfigWorkloadVerifier(api, tenants,
                new DdcConfigWorkloadVerifier.BindingsVO(settings.workloadAudience(), settings.bindings()));
    }

    @Bean("ddcConfigDeliveryTokenService")
    DdcConfigDeliveryTokenService ddcConfigDeliveryTokenService(DeliveryProperties settings) {
        return new DdcConfigDeliveryTokenService(settings.signingKeyringFile(), Clock.systemUTC(), settings.deliveryTokenTtl());
    }

    @Bean("ddcConfigSnapshotService")
    DdcConfigSnapshotService ddcConfigSnapshotService(@Qualifier("ddcConfigItemRepository") DdcConfigItemRepository items,
            @Qualifier("ddcConfigVersionRepository") DdcConfigVersionRepository versions,
            @Qualifier("ddcConfigBootBindingRepository") DdcConfigBootBindingRepository boots,
            @Qualifier("ddcTenantContextExecutor") DdcTenantContextExecutor tenants,
            @Qualifier("ddcConfigDeliveryTokenService") DdcConfigDeliveryTokenService tokens,
            @Qualifier("ddcConfigManifestFactory") DdcConfigManifestFactory manifests,
            DeliveryProperties settings) {
        return new DdcConfigSnapshotService(items, versions, boots, tenants, tokens, Clock.systemUTC(),
                settings.snapshotValidity(), manifests);
    }

    @Bean("ddcConfigDeliverySecurityFilterChain")
    @Order(0)
    SecurityFilterChain deliverySecurity(HttpSecurity http,
            @Qualifier("serviceAccessTokenVerifier") ServiceAccessTokenVerifier oauth,
            @Qualifier("idpSecurityObservationFilter") IdpSecurityObservationFilter observation,
            @Qualifier("idpEndpointAuthenticationPolicy") IdpEndpointAuthenticationPolicy policies,
            @Qualifier("ddcConfigWorkloadVerifier") DdcConfigWorkloadVerifier workloads,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper) throws Exception {
        http.securityMatcher(SNAPSHOT_PATH, RECEIPT_PATH);
        configureStateless(http).authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> writeError(
                                mapper, request, response, HttpStatus.UNAUTHORIZED, "TIANSHU_CONFIG_AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, failure) -> writeError(
                                mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CONFIG_SCOPE_REQUIRED")));
        http.addFilterBefore(observation, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(new DeliveryCredentialsFilter(oauth, policies, workloads, mapper), AuthorizationFilter.class);
        return http.build();
    }

    @Bean("ddcConfigDeliveryWebConfigurer")
    WebMvcConfigurer deliveryWebConfigurer(@Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
        var converter = deliveryMessageConverter(mapper);
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.addFirst(converter);
            }
        };
    }

    /** Applies the same bounded Jackson 2 wire contract in production and protocol tests. */
    public static MappingJackson2HttpMessageConverter deliveryMessageConverter(ObjectMapper applicationMapper) {
        var mapper = applicationMapper.copy();
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        mapper.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(8).maxStringLength(16_384).maxNumberLength(32).build());
        return new DeliveryJsonConverter(mapper);
    }

    @Bean("ddcConfigInternalOpenApi")
    @ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
    GroupedOpenApi internalOpenApi() {
        return GroupedOpenApi.builder().group(INTERNAL_OPEN_API_GROUP).pathsToMatch(SNAPSHOT_PATH, RECEIPT_PATH).build();
    }

    @Bean("ddcConfigInternalContractSecurityFilterChain")
    @Order(0)
    @ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
    SecurityFilterChain internalContractSecurity(HttpSecurity http,
            @Qualifier("idpBearerAuthenticationFilter") IdpBearerAuthenticationFilter identity,
            @Qualifier("idpSecurityObservationFilter") IdpSecurityObservationFilter observation) throws Exception {
        http.securityMatcher(INTERNAL_OPEN_API_PATH);
        configureStateless(http).authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("SCOPE_TIANSHU_READ"));
        http.addFilterBefore(identity, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(observation, IdpBearerAuthenticationFilter.class);
        return http.build();
    }

    private static HttpSecurity configureStateless(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true)
                        .securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()));
    }

    public static String requestId(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ID_ATTRIBUTE) instanceof String id) {
            return id;
        }
        String id = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, id);
        return id;
    }

    private static void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String errorCode) throws IOException {
        boolean audited = switch (status) {
            case UNAUTHORIZED -> IdpSecurityObservationFilter.beforeAuthenticationFailure(request);
            case FORBIDDEN -> IdpSecurityObservationFilter.beforeAuthorizationDenied(request);
            default -> true;
        };
        response.setStatus(audited ? status.value() : HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (audited && status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("errorCode",
                audited ? errorCode : "OAUTH_AUDIT_DELIVERY_UNAVAILABLE", "requestId", requestId(request)));
    }

    private static final class DeliveryJsonConverter extends MappingJackson2HttpMessageConverter {
        private static final int MAX_RECEIPT_BYTES = 32_768;

        private DeliveryJsonConverter(ObjectMapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canRead(Class<?> type, MediaType mediaType) {
            return type == DdcConfigReceiptCommand.class && super.canRead(type, mediaType);
        }

        @Override
        public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
            return type == DdcConfigReceiptCommand.class && super.canRead(type, contextClass, mediaType);
        }

        @Override
        public boolean canWrite(Class<?> type, MediaType mediaType) {
            return responseType(type) && super.canWrite(type, mediaType);
        }

        @Override
        public boolean canWrite(Type type, Class<?> actual, MediaType mediaType) {
            return responseType(actual) && super.canWrite(type, actual, mediaType);
        }

        private static boolean responseType(Class<?> type) {
            return type == DdcConfigSnapshotDTO.class || type == DdcConfigDeliveryController.DeliveryErrorVO.class;
        }

        @Override
        protected Object readInternal(Class<?> type, HttpInputMessage message) throws IOException {
            return readReceipt(message);
        }

        @Override
        public Object read(Type type, Class<?> contextClass, HttpInputMessage message) throws IOException {
            return readReceipt(message);
        }

        private DdcConfigReceiptCommand readReceipt(HttpInputMessage message) throws IOException {
            if (message.getHeaders().getContentLength() > MAX_RECEIPT_BYTES) {
                throw new HttpMessageNotReadableException("Configuration receipt exceeds the body limit", message);
            }
            byte[] bytes = message.getBody().readNBytes(MAX_RECEIPT_BYTES + 1);
            try {
                if (bytes.length > MAX_RECEIPT_BYTES) {
                    throw new HttpMessageNotReadableException("Configuration receipt exceeds the body limit", message);
                }
                return getObjectMapper().readValue(bytes, DdcConfigReceiptCommand.class);
            } catch (JsonProcessingException invalid) {
                throw new HttpMessageNotReadableException("Invalid configuration receipt JSON", message);
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    /** Tianquan owns JWT verification and current security state. This filter binds its result to a live workload. */
    static final class DeliveryCredentialsFilter extends OncePerRequestFilter {
        private final ServiceAccessTokenVerifier oauth;
        private final IdpEndpointAuthenticationPolicy policies;
        private final DdcConfigWorkloadVerifier workloads;
        private final ObjectMapper mapper;

        DeliveryCredentialsFilter(ServiceAccessTokenVerifier oauth, IdpEndpointAuthenticationPolicy policies,
                DdcConfigWorkloadVerifier workloads, ObjectMapper mapper) {
            this.oauth = oauth;
            this.policies = policies;
            this.workloads = workloads;
            this.mapper = mapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String scope = requiredScope(request);
            if (scope == null || policies.requirement(request) != IdpEndpointAuthenticationPolicy.Requirement.SERVICE) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CONFIG_ENDPOINT_NOT_ALLOWED");
                return;
            }
            String token = bearer(request);
            if (token == null) {
                writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, "TIANSHU_CONFIG_AUTHENTICATION_REQUIRED");
                return;
            }
            var verification = oauth.verify(token);
            if (!(verification instanceof AccessTokenVerification.Valid<ServiceIdentityPrincipal> valid)) {
                writeAuthenticationFailure(request, response, verification);
                return;
            }
            var existing = SecurityContextHolder.getContext();
            var context = SecurityContextHolder.createEmptyContext();
            var authorities = valid.scopes().stream().map(value -> new SimpleGrantedAuthority("SCOPE_" + value)).toList();
            var authentication = new IdpAuthenticationToken(valid.principal(), authorities, valid.verifiedIssuer());
            authentication.setDetails(valid.proof());
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            try {
                if (!valid.scopes().contains(scope)) {
                    writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CONFIG_SCOPE_REQUIRED");
                    return;
                }
                if (!authenticateWorkload(request, response, valid.principal())) {
                    return;
                }
                chain.doFilter(request, response);
            } finally {
                request.removeAttribute(DdcConfigDeliveryController.VERIFIED_WORKLOAD_ATTRIBUTE);
                SecurityContextHolder.setContext(existing);
            }
        }

        private boolean authenticateWorkload(HttpServletRequest request, HttpServletResponse response,
                ServiceIdentityPrincipal service) throws IOException {
            try {
                var workload = workloads.verify(service, oneHeader(request, WORKLOAD_HEADER), oneHeader(request, BOOT_HEADER));
                request.setAttribute(DdcConfigDeliveryController.VERIFIED_WORKLOAD_ATTRIBUTE, workload);
                return true;
            } catch (DdcConfigWorkloadVerifier.Rejected rejected) {
                writeError(mapper, request, response, rejected.status(), rejected.errorCode());
                return false;
            } catch (ConstraintViolationException invalid) {
                writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, "TIANSHU_CONFIG_WORKLOAD_PROOF_INVALID");
                return false;
            }
        }

        private void writeAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                AccessTokenVerification<ServiceIdentityPrincipal> verification) throws IOException {
            if (verification instanceof AccessTokenVerification.Unavailable<?>) {
                writeError(mapper, request, response, HttpStatus.SERVICE_UNAVAILABLE, "TIANSHU_CONFIG_OAUTH_UNAVAILABLE");
            } else if (verification instanceof AccessTokenVerification.Invalid<?> invalid
                    && ("IDENTITY_STATE_REJECTED".equals(invalid.reasonCode()) || "JWT_REVOKED".equals(invalid.reasonCode()))) {
                writeError(mapper, request, response, HttpStatus.FORBIDDEN, "TIANSHU_CONFIG_AUTHORIZATION_REVOKED");
            } else {
                writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, "TIANSHU_CONFIG_AUTHENTICATION_REQUIRED");
            }
        }

        private static String requiredScope(HttpServletRequest request) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if ("GET".equals(request.getMethod()) && SNAPSHOT_PATH.equals(path)) {
                return DdcConfigWorkloadVerifier.READ_SCOPE;
            }
            if ("POST".equals(request.getMethod()) && RECEIPT_PATH.equals(path)) {
                return DdcConfigWorkloadVerifier.RECEIPT_SCOPE;
            }
            return null;
        }

        private static String bearer(HttpServletRequest request) {
            String value = oneHeader(request, HttpHeaders.AUTHORIZATION);
            if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return null;
            }
            String token = value.substring(7);
            return token.length() <= 8192 && token.matches("[A-Za-z0-9._-]+") ? token : null;
        }

        private static String oneHeader(HttpServletRequest request, String header) {
            var values = Collections.list(request.getHeaders(header));
            return values.size() == 1 && !values.getFirst().contains(",") ? values.getFirst() : null;
        }
    }
}
