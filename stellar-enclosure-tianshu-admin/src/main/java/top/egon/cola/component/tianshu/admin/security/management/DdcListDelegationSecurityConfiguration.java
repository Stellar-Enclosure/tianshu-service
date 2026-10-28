package top.egon.cola.component.tianshu.admin.security.management;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;

@Configuration(proxyBeanMethods = false)
public class DdcListDelegationSecurityConfiguration {
    @Bean("ddcListDelegationSecurityFilterChain")
    @Order(0)
    public SecurityFilterChain listDelegationSecurity(HttpSecurity http,
            @Qualifier("idpBearerAuthenticationFilter") IdpBearerAuthenticationFilter identityFilter,
            @Qualifier("idpSecurityObservationFilter") IdpSecurityObservationFilter observationFilter,
            @Qualifier("ddcAdminAuthenticationEntryPoint") DdcAdminAuthenticationEntryPoint errors) throws Exception {
        http.securityMatcher("/internal/v1/tianshu/list-delegation")
                .csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(true).securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(authorize -> authorize.requestMatchers(HttpMethod.POST, "/internal/v1/tianshu/list-delegation")
                        .access((authentication, context) -> {
                            var auth = authentication.get();
                            boolean allowed = auth instanceof IdpAuthenticationToken token && token.isAuthenticated()
                                    && token.getPrincipal() instanceof ServiceIdentityPrincipal service
                                    && service.scopeContext() == ServiceTokenContext.TENANT
                                    && service.scopes().contains(DdcListDelegationVerifier.REQUIRED_SCOPE);
                            return new AuthorizationDecision(allowed);
                        }).anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors));
        http.addFilterBefore(identityFilter, AnonymousAuthenticationFilter.class);
        http.addFilterBefore(observationFilter, IdpBearerAuthenticationFilter.class);
        return http.build();
    }
}
