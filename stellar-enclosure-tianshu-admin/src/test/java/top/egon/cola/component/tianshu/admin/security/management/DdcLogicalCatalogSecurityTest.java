package top.egon.cola.component.tianshu.admin.security.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.component.tianshu.admin.controller.metadata.DdcLogicalCatalogController;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcLogicalCatalogQueryService;
import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterAutoConfiguration;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpObservedVerificationComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedTenantContextFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract.*;

/** Real stateless IdP bearer filters and MVC, with synthetic verifier and repository-boundary doubles. */
class DdcLogicalCatalogSecurityTest {
    private static final URI ISSUER = URI.create("https://identity.example.test/tenants/tenant-a");
    private static final URI RESOURCE = URI.create("https://tianshu.example.test");
    private static final Set<String> READ = Set.of(SCOPE);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ServiceAccessTokenVerifier services;
    private UserAccessTokenVerifier users;
    private DdcTenantRegistry registry;
    private DdcLogicalCatalogQueryService queries;
    private IdpObservedVerificationComponent observations;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        String prefix = "egon.cola.component.tianshu.logical-catalog.callers[tenant-a].";
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("approved-synthetic-caller", Map.of(
                prefix + "issuer", ISSUER.toString(), prefix + "resource-uri", RESOURCE.toString(),
                prefix + "client-id", "jianshen-client", prefix + "app-id", "jianshen-app",
                prefix + "source-biz-code", "xingyuan", prefix + "source-app-code", "tianquan-jianshen",
                prefix + "source-environment", "prod")));
        context.register(TestConfiguration.class);
        context.refresh();
        Filter[] servletTenantFilters = new ServletContextInitializerBeans(context).stream()
                .filter(FilterRegistrationBean.class::isInstance)
                .map(value -> (FilterRegistrationBean<?>) value)
                .filter(value -> value.getFilter() instanceof VerifiedTenantContextFilter)
                .filter(FilterRegistrationBean::isEnabled)
                .map(FilterRegistrationBean::getFilter)
                .toArray(Filter[]::new);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(servletTenantFilters).build();
        services = context.getBean(ServiceAccessTokenVerifier.class);
        users = context.getBean(UserAccessTokenVerifier.class);
        registry = context.getBean(DdcTenantRegistry.class);
        verify(registry).initialize();
        clearInvocations(registry);
        queries = context.getBean(DdcLogicalCatalogQueryService.class);
        observations = context.getBean(IdpObservedVerificationComponent.class);
        when(services.verify("valid")).thenReturn(valid(principal("tenant-a", READ, RESOURCE, "jianshen-client", ServiceTokenContext.TENANT), ISSUER));
        when(registry.requireByIdentityTenantId("tenant-a")).thenReturn(new DdcTenantBindingDTO(1001L, "tenant-a", true));
        MDC.put("tenantId", "outer-tenant");
        MDC.put("userId", "outer-user");
    }
    @AfterEach void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void exactFourQueriesUseMappedTenantAndBareTypedPayloadsWithoutSessionOrHeaderTenant() throws Exception {
        var business = new DdcManagementBiz("biz-public", "catalog-code", "Catalog", false);
        var application = new DdcManagementApp("app-public", "biz-public", "catalog-code", "reader", "Reader", false, false);
        when(queries.findBusiness("biz-public")).thenAnswer(invocation -> {
            assertThat(MDC.get("tenantId")).isEqualTo("1001");
            assertThat(MDC.get("userId")).isEqualTo("service:cred-1");
            return Optional.of(business);
        });
        when(queries.listBusinesses("catalog")).thenReturn(List.of(business));
        when(queries.findApplication("app-public")).thenReturn(Optional.of(application));
        when(queries.listApplications("biz-public", "reader")).thenReturn(List.of(application));
        var response = mvc.perform(get(BUSINESS_PATH).param("id", "biz-public").header("Authorization", "Bearer valid")
                        .header("X-Egon-Tianshu-Tenant", "9999").header("X-Tenant-Id", "9999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("biz-public"))
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(response.getRequest().getSession(false)).isNull();
        mvc.perform(get(BUSINESSES_PATH).param("keyword", "catalog").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("biz-public"));
        mvc.perform(get(APPLICATION_PATH).param("id", "app-public").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value("biz-public"))
                .andExpect(jsonPath("$.businessEnabled").value(false));
        mvc.perform(get(APPLICATIONS_PATH).param("businessId", "biz-public").param("keyword", "reader").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].enabled").value(false));
        assertThat(MDC.get("tenantId")).isEqualTo("outer-tenant");
        assertThat(MDC.get("userId")).isEqualTo("outer-user");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(users);
    }

    @Test
    void authorizedPointMissAloneCarriesTheExactCatalogOutcomeMarker() throws Exception {
        when(queries.findBusiness("absent")).thenReturn(Optional.empty());
        when(queries.findApplication("absent")).thenReturn(Optional.empty());
        when(queries.listBusinesses(null)).thenReturn(List.of());
        for (String point : List.of(BUSINESS_PATH, APPLICATION_PATH)) {
            mvc.perform(get(point).param("id", "absent").header("Authorization", "Bearer valid"))
                    .andExpect(status().isNotFound()).andExpect(header().string(OUTCOME_HEADER, OUTCOME_NOT_FOUND));
        }
        mvc.perform(get(BUSINESSES_PATH).header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(content().json("[]")).andExpect(header().doesNotExist(OUTCOME_HEADER));
    }

    @Test
    void noBearerCookieOnlyDuplicateBearerAndForgedSpringContextNeverReachTheQuery() throws Exception {
        mvc.perform(get(BUSINESS_PATH).param("id", "x")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        mvc.perform(get(BUSINESS_PATH).param("id", "x").cookie(new jakarta.servlet.http.Cookie("access", "valid")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer valid", "Bearer other"))
                .andExpect(status().isUnauthorized());
        var forged = new IdpAuthenticationToken(principal("tenant-a", READ, RESOURCE, "jianshen-client", ServiceTokenContext.TENANT),
                List.of(new SimpleGrantedAuthority("SCOPE_" + SCOPE)), ISSUER);
        mvc.perform(get(BUSINESS_PATH).param("id", "x").with(authentication(forged)))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        verifyNoInteractions(queries, services, users, registry);
    }

    @Test
    void wrongScopePlatformIssuerAudienceCallerAndInconsistentScopesAreDenied() throws Exception {
        when(services.verify("scope")).thenReturn(valid(principal("tenant-a", Set.of("OTHER"), RESOURCE, "jianshen-client", ServiceTokenContext.TENANT), ISSUER));
        when(services.verify("platform")).thenReturn(valid(principal(null, READ, RESOURCE, "jianshen-client", ServiceTokenContext.PLATFORM), ISSUER));
        when(services.verify("issuer")).thenReturn(valid(principal("tenant-a", READ, RESOURCE, "jianshen-client", ServiceTokenContext.TENANT), URI.create("https://wrong.example.test")));
        when(services.verify("audience")).thenReturn(valid(principal("tenant-a", READ, URI.create("https://wrong.example.test"), "jianshen-client", ServiceTokenContext.TENANT), ISSUER));
        when(services.verify("caller")).thenReturn(valid(principal("tenant-a", READ, RESOURCE, "other-client", ServiceTokenContext.TENANT), ISSUER));
        when(services.verify("scopes")).thenReturn(new AccessTokenVerification.Valid<>(principal("tenant-a", Set.of("OTHER"), RESOURCE, "jianshen-client", ServiceTokenContext.TENANT), READ, null, ISSUER));
        when(services.verify("missing-proof")).thenReturn(new AccessTokenVerification.Valid<>(principal("tenant-a", READ, RESOURCE, "jianshen-client", ServiceTokenContext.TENANT), READ, null, ISSUER));
        when(services.verify("missing-issuer")).thenReturn(new AccessTokenVerification.Valid<>(principal("tenant-a", READ, RESOURCE, "jianshen-client", ServiceTokenContext.TENANT), READ));
        for (String token : List.of("scope", "platform", "issuer", "audience", "caller", "scopes", "missing-issuer", "missing-proof")) {
            mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        }
        verifyNoInteractions(queries, registry, users);
    }

    @Test
    void userTokenAndUnavailableAuthorityAreNeverTreatedAsMissingCatalogEntries() throws Exception {
        when(services.verify("user")).thenReturn(new AccessTokenVerification.Invalid<>("SERVICE_PRINCIPAL_REQUIRED"));
        when(services.verify("unavailable")).thenReturn(new AccessTokenVerification.Unavailable<>("IDENTITY_STATE_UNAVAILABLE"));
        mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer user"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer unavailable"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        verifyNoInteractions(queries, users, registry);
    }

    @Test
    void unknownOrUnavailableTenantMappingAndCatalogStorageDoNotProduceFalseMisses() throws Exception {
        doThrow(new IllegalArgumentException("TENANT_IDENTITY_UNREGISTERED")).when(registry).requireByIdentityTenantId("tenant-a");
        mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        doThrow(new IllegalStateException("private database address")).when(registry).requireByIdentityTenantId("tenant-a");
        mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer valid"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist(OUTCOME_HEADER))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database address"))));
        doReturn(new DdcTenantBindingDTO(1001L, "tenant-a", true)).when(registry).requireByIdentityTenantId("tenant-a");
        when(queries.findBusiness("x")).thenThrow(new IllegalStateException("private storage details"));
        mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer valid"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist(OUTCOME_HEADER));
    }

    @Test
    void activeServicePolicyDenialIsDifferentFromAnUnavailableServicePolicy() throws Exception {
        when(queries.findBusiness("denied")).thenThrow(
                new Rbac3AuthorizationClient.AuthorizationDeniedException("FIELD_SERVICE_SCOPE_DENIED"));
        when(queries.findBusiness("unavailable")).thenThrow(
                new Rbac3AuthorizationClient.AuthorizationUnavailableException("AUTH_RUNTIME_UNAVAILABLE", null));

        mvc.perform(get(BUSINESS_PATH).param("id", "denied").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        mvc.perform(get(BUSINESS_PATH).param("id", "unavailable").header("Authorization", "Bearer valid"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist(OUTCOME_HEADER));
    }

    @Test
    void unsupportedVerbsPathsSelectorsAndOversizedIdsNeverInvokeCatalogStorage() throws Exception {
        for (String method : List.of("HEAD", "POST", "PUT", "OPTIONS")) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer valid"))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        }
        mvc.perform(get(BASE_PATH + "/not-mounted").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        mvc.perform(get(BUSINESS_PATH).param("id", "x").param("tenantId", "9999").header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        mvc.perform(get(BUSINESS_PATH).param("id", "x", "y").header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BUSINESS_PATH).param("id", "x".repeat(65)).header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist(OUTCOME_HEADER));
        mvc.perform(get(BUSINESSES_PATH).param("keyword", "x".repeat(129)).header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(queries);
    }

    @Test
    void strictDenialAuditFailureBecomesUnavailable() throws Exception {
        when(observations.enabled()).thenReturn(true);
        when(observations.authorizationDenied(any(TokenAuditProofBO.class), anyString())).thenReturn(false);
        when(services.verify("scope")).thenReturn(valid(principal("tenant-a", Set.of("OTHER"), RESOURCE, "jianshen-client", ServiceTokenContext.TENANT), ISSUER));
        mvc.perform(get(BUSINESS_PATH).param("id", "x").header("Authorization", "Bearer scope"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist(OUTCOME_HEADER))
                .andExpect(jsonPath("$.errorCode").value("OAUTH_AUDIT_DELIVERY_UNAVAILABLE"));
        verifyNoInteractions(queries);
    }

    @Test
    void fourActualHandlersRetainInternalServicePermissionGovernance() {
        var handlers = Arrays.stream(DdcLogicalCatalogController.class.getDeclaredMethods())
                .filter(value -> value.isAnnotationPresent(PermissionResource.class)).toList();
        assertThat(handlers).hasSize(4).allSatisfy(method -> {
            var resource = method.getAnnotation(PermissionResource.class);
            assertThat(resource.access()).isEqualTo(ResourceAccess.INTERNAL);
            assertThat(resource.allowedPrincipals()).containsExactly(PermissionManifest.PrincipalType.SERVICE);
            assertThat(resource.oauthScopes()).containsExactly(SCOPE);
            assertThat(resource.key()).startsWith("tianshu.api.logicalCatalog.");
        });
        assertThat(context.getBean(DdcLogicalCatalogSecurityConfiguration.CatalogProperties.class).callers()).containsOnlyKeys("tenant-a");
    }

    @Test
    void verifiedTenantContextRunsInsideBearerWithoutAnAdditionalServletRegistration() {
        var registrations = new ServletContextInitializerBeans(context).stream()
                .filter(FilterRegistrationBean.class::isInstance)
                .map(value -> (FilterRegistrationBean<?>) value)
                .filter(value -> value.getFilter() instanceof VerifiedTenantContextFilter)
                .toList();

        assertThat(registrations).singleElement().satisfies(registration -> {
            assertThat(registration.getFilter()).isSameAs(context.getBean("verifiedTenantContextFilter"));
            assertThat(registration.isEnabled()).isFalse();
        });
    }

    private static AccessTokenVerification.Valid<ServiceIdentityPrincipal> valid(ServiceIdentityPrincipal principal, URI issuer) {
        return new AccessTokenVerification.Valid<>(principal, principal.scopes(),
                new TokenAuditProofBO(issuer, principal.resourceUri(), "a".repeat(64), "b".repeat(64), "c".repeat(64)), issuer);
    }
    private static ServiceIdentityPrincipal principal(String tenant, Set<String> scopes, URI resource, String client, ServiceTokenContext kind) {
        var now = Instant.now();
        return new ServiceIdentityPrincipal(client, tenant, client, "synthetic-jti", resource, 1L, scopes,
                "xingyuan", "tianquan-jianshen", "prod", "cred-1", now.minusSeconds(1), now.plusSeconds(60), "jianshen-app", kind);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({DdcLogicalCatalogSecurityConfiguration.class, DdcLogicalCatalogController.class})
    static class TestConfiguration {
        @Bean("jacksonObjectMapper") ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean UserAccessTokenVerifier userAccessTokenVerifier() { return mock(UserAccessTokenVerifier.class); }
        @Bean ServiceAccessTokenVerifier serviceAccessTokenVerifier() { return mock(ServiceAccessTokenVerifier.class); }
        @Bean("verifiedTenantContextFilter") VerifiedTenantContextFilter verifiedTenantContextFilter() {
            return new IdpStarterAutoConfiguration().verifiedTenantContextFilter();
        }
        @Bean("ddcLogicalCatalogQueryService") DdcLogicalCatalogQueryService queries() { return mock(DdcLogicalCatalogQueryService.class); }
        @Bean("ddcTenantRegistry") DdcTenantRegistry registry() { return mock(DdcTenantRegistry.class); }
        @Bean("ddcTenantContextExecutor") DdcTenantContextExecutor tenants() { return new DdcTenantContextExecutor(); }
        @Bean IdpObservedVerificationComponent observations() { return mock(IdpObservedVerificationComponent.class); }
        @Bean("idpSecurityObservationFilter") IdpSecurityObservationFilter observationsFilter(IdpObservedVerificationComponent observations) {
            return new IdpSecurityObservationFilter(observations, new IdpEndpointAuthenticationPolicy(List.of(), List.of(BASE_PATH + "/**"), false));
        }
    }
}
