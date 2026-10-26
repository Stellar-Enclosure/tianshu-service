package top.egon.cola.component.tianshu.admin.security.management;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;

import java.util.List;

@Configuration(value = "ddcSourceImportSecurityConfiguration", proxyBeanMethods = false)
public class DdcSourceImportSecurityConfiguration {
    @Bean
    public IdpEndpointAuthenticationPolicy idpEndpointAuthenticationPolicy() {
        return new IdpEndpointAuthenticationPolicy(List.of("/api/v1/tianshu/i18n/public/bundle"),
                List.of("/internal/v1/i18n/source-imports", "/internal/v1/i18n/source-imports/*"));
    }

    @Bean("ddcSourceImportSecurityFilterChain")
    @Order(0)
    public SecurityFilterChain ddcSourceImportSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("idpBearerAuthenticationFilter") IdpBearerAuthenticationFilter identityFilter,
            @Qualifier("idpSecurityObservationFilter") IdpSecurityObservationFilter observationFilter,
            @Qualifier("ddcAdminAuthenticationEntryPoint") DdcAdminAuthenticationEntryPoint errors) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> services = (authentication, context) -> {
            try {
                DdcSourceImportAuthentication.identity(authentication.get());
                return new AuthorizationDecision(true);
            } catch (AccessDeniedException denied) {
                return new AuthorizationDecision(false);
            }
        };
        http.securityMatcher("/internal/v1/i18n/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true).securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/internal/v1/i18n/source-imports").access(services)
                        .requestMatchers(HttpMethod.GET, "/internal/v1/i18n/source-imports/*").access(services)
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors));
        http.addFilterBefore(identityFilter, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(observationFilter, IdpBearerAuthenticationFilter.class);
        return http.build();
    }
}
