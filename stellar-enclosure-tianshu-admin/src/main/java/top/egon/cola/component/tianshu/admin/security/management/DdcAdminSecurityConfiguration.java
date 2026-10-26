package top.egon.cola.component.tianshu.admin.security.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.config.DdcAdminUiI18nProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextFilter;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;

@lombok.extern.slf4j.Slf4j
@Configuration(value = "ddcAdminSecurityConfiguration", proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({DdcAdminProperties.class, DdcTenantAdminProperties.class, DdcAdminUiI18nProperties.class})
public class DdcAdminSecurityConfiguration {

    @Bean(name = "ddcAdminAuthenticationEntryPoint")
    public DdcAdminAuthenticationEntryPoint ddcAdminAuthenticationEntryPoint(
            @Qualifier("jacksonObjectMapper") ObjectMapper objectMapper) {
        return new DdcAdminAuthenticationEntryPoint(objectMapper);
    }

    @Bean(name = "ddcTenantContextFilter")
    public DdcTenantContextFilter ddcTenantContextFilter(
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenantRegistry,
            @Qualifier("ddcTenantContextExecutor") DdcTenantContextExecutor tenantContextExecutor,
            @Qualifier("ddcAdminAuthenticationEntryPoint")
            DdcAdminAuthenticationEntryPoint securityHandler) {
        return new DdcTenantContextFilter(
                tenantRegistry, tenantContextExecutor, securityHandler);
    }

    @Bean("ddcAdminSecurityFilterChain")
    public SecurityFilterChain ddcAdminSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("ddcAdminAuthenticationEntryPoint")
            DdcAdminAuthenticationEntryPoint securityHandler,
            @Qualifier("ddcTenantContextFilter")
            DdcTenantContextFilter tenantContextFilter,
            ObjectProvider<IdpBearerAuthenticationFilter> idpFilters,
            ObjectProvider<Rbac3BearerAuthenticationFilter> rbac3Filters,
            @Qualifier("idpSecurityObservationFilter") ObjectProvider<top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter> observations)
            throws Exception {
        IdpBearerAuthenticationFilter idpFilter = idpFilters.getIfAvailable();
        Rbac3BearerAuthenticationFilter rbac3Filter = rbac3Filters.getIfAvailable();
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tianshu/i18n/public/bundle").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/authorization/effective-permissions").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tianshu/i18n/admin/bootstrap", "/api/v1/tianshu/i18n/admin/bundle")
                        .access(scopeAndPermission(DdcAdminCapability.READ))
                        .requestMatchers(HttpMethod.POST, "/api/v1/tianshu/i18n/admin/initialize")
                        .access(org.springframework.security.authorization.AuthorizationManagers.allOf(
                                scopeAndPermission(DdcAdminCapability.READ), scopeAndPermission(DdcAdminCapability.WRITE),
                                scopeAndPermission(DdcAdminCapability.PUBLISH)))
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/bootstrap",
                                "/api/v1/tianshu/auth/bootstrap"
                        )
                        .access(scopeAndPermission(DdcAdminCapability.READ))
                        .requestMatchers(HttpMethod.GET, "/api/v1/tianshu/i18n/bundle")
                        .access(org.springframework.security.authorization.AuthorizationManagers.anyOf(
                                scopeAndPermission(DdcAdminCapability.I18N_READ), scopeAndPermission(DdcAdminCapability.READ)))
                        .requestMatchers(HttpMethod.GET, "/api/v1/tianshu/i18n/pages", "/api/v1/tianshu/i18n/pages/*",
                                "/api/v1/tianshu/i18n/source-pages/*", "/api/v1/tianshu/i18n/pages/*/publications/*", "/api/v1/tianshu/resource-metadata")
                        .access(scopeAndPermission(DdcAdminCapability.READ))
                        .requestMatchers(HttpMethod.POST, "/api/v1/tianshu/i18n/pages/*/publish")
                        .access(scopeAndPermission(DdcAdminCapability.PUBLISH))
                        .requestMatchers(HttpMethod.POST, "/api/v1/tianshu/i18n/pages")
                        .access(scopeAndPermission(DdcAdminCapability.WRITE))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tianshu/i18n/pages/*/draft")
                        .access(scopeAndPermission(DdcAdminCapability.WRITE))
                        .requestMatchers("/api/v1/tianshu/ai/**")
                        .authenticated()
                        .requestMatchers("/api/v1/tianshu/cache/**")
                        .access(scopeAndPermission(DdcAdminCapability.CACHE))
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tianshu/configs/*/publish",
                                "/api/v1/tianshu/publish-tasks/*/retry"
                        ).access(scopeAndPermission(DdcAdminCapability.PUBLISH))
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tianshu/configs",
                                "/api/v1/tianshu/configs/*/versions",
                                "/api/v1/tianshu/apps",
                                "/api/v1/tianshu/apps/*",
                                "/api/v1/tianshu/bizs",
                                "/api/v1/tianshu/bizs/*",
                                "/api/v1/tianshu/envs",
                                "/api/v1/tianshu/envs/*",
                                "/api/v1/tianshu/namespaces",
                                "/api/v1/tianshu/namespaces/*",
                                "/api/v1/tianshu/namespace-env-app-bindings",
                                "/api/v1/tianshu/instances",
                                "/api/v1/tianshu/registry/**",
                                "/api/v1/tianshu/publish-tasks",
                                "/api/v1/tianshu/publish-tasks/*"
                        ).access(scopeAndPermission(DdcAdminCapability.READ))
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tianshu/configs/page",
                                "/api/v1/tianshu/configs/*/versions/page",
                                "/api/v1/tianshu/apps/page",
                                "/api/v1/tianshu/bizs/page",
                                "/api/v1/tianshu/envs/page",
                                "/api/v1/tianshu/namespaces/page",
                                "/api/v1/tianshu/namespace-env-app-bindings/page",
                                "/api/v1/tianshu/instances/page",
                                "/api/v1/tianshu/publish-tasks/page"
                        ).access(scopeAndPermission(DdcAdminCapability.READ))
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/tianshu/configs",
                                "/api/v1/tianshu/configs/*/rollback",
                                "/api/v1/tianshu/apps",
                                "/api/v1/tianshu/bizs",
                                "/api/v1/tianshu/envs",
                                "/api/v1/tianshu/namespaces",
                                "/api/v1/tianshu/namespace-env-app-bindings"
                        ).access(scopeAndPermission(DdcAdminCapability.WRITE))
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/tianshu/configs/*",
                                "/api/v1/tianshu/apps/*",
                                "/api/v1/tianshu/apps/*/enabled",
                                "/api/v1/tianshu/bizs/*",
                                "/api/v1/tianshu/bizs/*/enabled",
                                "/api/v1/tianshu/envs/*",
                                "/api/v1/tianshu/envs/*/enabled",
                                "/api/v1/tianshu/namespaces/*",
                                "/api/v1/tianshu/namespaces/*/enabled",
                                "/api/v1/tianshu/namespace-env-app-bindings/*"
                        ).access(scopeAndPermission(DdcAdminCapability.WRITE))
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/tianshu/configs/*",
                                "/api/v1/tianshu/apps/*",
                                "/api/v1/tianshu/bizs/*",
                                "/api/v1/tianshu/envs/*",
                                "/api/v1/tianshu/namespaces/*",
                                "/api/v1/tianshu/namespace-env-app-bindings/*"
                        ).access(scopeAndPermission(DdcAdminCapability.WRITE))
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityHandler)
                        .accessDeniedHandler(securityHandler)
                );
        if (idpFilter != null && rbac3Filter != null) {
            http.addFilterBefore(idpFilter, AnonymousAuthenticationFilter.class);
            http.addFilterAfter(rbac3Filter, IdpBearerAuthenticationFilter.class);
            var observation = observations.getIfAvailable();
            if (observation != null) { http.addFilterBefore(observation, IdpBearerAuthenticationFilter.class); }
            http.addFilterAfter(tenantContextFilter, org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        } else {
            throw new IllegalStateException(
                    "Tianquan-Shoubing and Tianquan-Jianshen authentication filters must be configured together");
        }
        return http.build();
    }

    private org.springframework.security.authorization.AuthorizationManager<org.springframework.security.web.access.intercept.RequestAuthorizationContext>
            scopeAndPermission(DdcAdminCapability capability) {
        return org.springframework.security.authorization.AuthorizationManagers.allOf(
                org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority("SCOPE_TIANSHU_" + capability.name()),
                (authentication, context) -> {
                    var current = authentication.get();
                    boolean granted = current != null && current.isAuthenticated()
                            && current.getPrincipal() instanceof top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails user
                            && user.hasPermission(capability.value());
                    return new org.springframework.security.authorization.AuthorizationDecision(granted);
                });
    }

    @Bean("ddcAuditReadinessHealthIndicator")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "egon.cola.platform.tianquan.shoubing.usage-observation", name = "enabled", havingValue = "true")
    org.springframework.boot.health.contributor.HealthIndicator ddcAuditReadinessHealthIndicator(
            @Qualifier("idpSecurityAuditReady") java.util.function.BooleanSupplier ready) {
        return () -> ready.getAsBoolean() ? org.springframework.boot.health.contributor.Health.up().build()
                : org.springframework.boot.health.contributor.Health.down().build();
    }

    @Bean("ddcIdentityStartupGuard")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "egon.cola.platform.tianquan.shoubing.usage-observation", name = "enabled", havingValue = "true")
    Object ddcIdentityStartupGuard(@Qualifier("environment") org.springframework.core.env.Environment environment,
            @Qualifier("idpStarterProperties") top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties properties,
            @Qualifier("idpSecurityAuditReady") java.util.function.BooleanSupplier ready) {
        properties.validate();
        if (!ready.getAsBoolean() || !properties.isMultiIssuerConfigured() || !environment.getProperty("server.ssl.enabled", Boolean.class, false)) {
            throw new IllegalStateException("OAUTH_HOST_SECURITY_NOT_READY");
        }
        if (properties.getTrustedIssuers().values().stream().anyMatch(issuer -> !issuer.getIssuer().startsWith("https://") || !issuer.getJwksUri().startsWith("https://"))
                || !"https".equalsIgnoreCase(properties.getResourceUri().getScheme())
                || !environment.getProperty("egon.cola.platform.tianquan.jianshen.authorization.endpoint", "").startsWith("https://")) {
            throw new IllegalStateException("OAUTH_HOST_TLS_REQUIRED");
        }
        if (!environment.getProperty("egon.cola.platform.tianquan.shoubing.authority.base-uri", "").startsWith("https://")) {
            throw new IllegalStateException("OAUTH_AUTHORITY_TLS_REQUIRED");
        }
        return Boolean.TRUE;
    }

}
