package top.egon.cola.component.tianshu.admin.security.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.component.tianshu.admin.config.DdcListDelegationWebConfiguration;
import top.egon.cola.component.tianshu.admin.controller.list.DdcListDelegationController;
import top.egon.cola.component.tianshu.admin.controller.list.DdcListTemplateExceptionAdvice;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.service.list.DdcListTemplateService;
import top.egon.cola.component.tianshu.api.list.ListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;
import top.egon.cola.component.tianshu.api.list.ListDelegationResponse;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpObservedVerificationComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DdcListDelegationSecurityTest {
    private static final String PATH = "/internal/v1/tianshu/list-delegation";
    private static final URI ISSUER = URI.create("https://idp.example/tenants/20");
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ServiceAccessTokenVerifier serviceTokens;
    private DdcListDelegationVerifier verifier;
    private DdcListTemplateService templates;
    private ServiceIdentityPrincipal service;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(ConfigurationForTest.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        serviceTokens = context.getBean(ServiceAccessTokenVerifier.class);
        verifier = context.getBean(DdcListDelegationVerifier.class);
        templates = context.getBean(DdcListTemplateService.class);
        service = principal(Set.of(DdcListDelegationVerifier.REQUIRED_SCOPE));
        when(serviceTokens.verify("valid")).thenReturn(new AccessTokenVerification.Valid<>(service, service.scopes(), null, ISSUER));
        when(serviceTokens.verify("wrong-scope")).thenReturn(new AccessTokenVerification.Valid<>(principal(Set.of("other")), Set.of("other"), null, ISSUER));
        when(verifier.verify(any(), eq("valid"), eq("subject-proof"))).thenReturn(new DdcListDelegationVerifier.VerifiedDelegation(service, ISSUER,
                mock(SystemAuthorizationSnapshot.class), Set.of("shop:read")));
        when(templates.executeDelegated(any(), any(), any())).thenReturn(new ListDelegationResponse("digest", "1", List.of(), List.of(), null, List.of(), false, false, null));
    }
    @AfterEach void cleanup() { context.close(); SecurityContextHolder.clearContext(); MDC.clear(); }

    @Test void onlyTheVerifiedServiceAndOneUserProofReachTheTemplateService() throws Exception {
        var result = mvc.perform(post(PATH).header("Authorization", "Bearer valid").header("X-Egon-Subject-Token", "subject-proof")
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        verify(templates).executeDelegated(any(), any(), any());
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test void missingBearerWrongServiceScopeAndDuplicateUserProofFailBeforeTemplateAccess() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("Authorization", "Bearer wrong-scope").header("X-Egon-Subject-Token", "subject-proof")
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
        mvc.perform(post(PATH).header("Authorization", "Bearer valid").header("X-Egon-Subject-Token", "one", "two")
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isBadRequest());
        verifyNoInteractions(templates);
    }

    private byte[] body() throws Exception {
        var definition = new ListDefinition("shop.page.list", "main", List.of(
                new ListDefinition.Column("id", List.of("shop.field.id"), "shop.list", "field.id", true, true)));
        var binding = new ListDefinitionBinding(definition, "shop.component.list", "shop.api.list", "shop.api.system", "shop.api.personal");
        var request = new ListDelegationRequest(binding, ListDelegationRequest.Operation.LOAD, "1", "2", "3", List.of("7"),
                Map.of("shop:read", Set.of("shop:read")), null, null, null, null, null, false);
        return new ObjectMapper().writeValueAsBytes(request);
    }
    private static ServiceIdentityPrincipal principal(Set<String> scopes) {
        var now = Instant.now();
        return new ServiceIdentityPrincipal("client", "20", "client", "token", URI.create("https://metadata.example"), 1,
                scopes, "trade", "shop", "test", "credential", now.minusSeconds(1), now.plusSeconds(120), "app-id", ServiceTokenContext.TENANT);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({DdcSourceImportSecurityConfiguration.class, DdcListDelegationSecurityConfiguration.class,
            DdcListDelegationWebConfiguration.class, DdcListDelegationController.class, DdcListTemplateExceptionAdvice.class, DdcTenantContextExecutor.class})
    static class ConfigurationForTest {
        @Bean("ddcListDelegationVerifier") DdcListDelegationVerifier verifier() { return mock(DdcListDelegationVerifier.class); }
        @Bean("ddcListTemplateService") DdcListTemplateService templates() { return mock(DdcListTemplateService.class); }
        @Bean UserAccessTokenVerifier userAccessTokenVerifier() { return mock(UserAccessTokenVerifier.class); }
        @Bean ServiceAccessTokenVerifier serviceAccessTokenVerifier() { return mock(ServiceAccessTokenVerifier.class); }
        @Bean IdpObservedVerificationComponent observations() { return mock(IdpObservedVerificationComponent.class); }
        @Bean("ddcAdminAuthenticationEntryPoint") DdcAdminAuthenticationEntryPoint errors() { return new DdcAdminAuthenticationEntryPoint(new ObjectMapper()); }
        @Bean("idpBearerAuthenticationFilter") IdpBearerAuthenticationFilter identityFilter(UserAccessTokenVerifier users,
                ServiceAccessTokenVerifier services, IdpEndpointAuthenticationPolicy policy) { return new IdpBearerAuthenticationFilter(users, services, policy, new ObjectMapper()); }
        @Bean("idpSecurityObservationFilter") IdpSecurityObservationFilter observationFilter(IdpObservedVerificationComponent observations,
                IdpEndpointAuthenticationPolicy policy) { return new IdpSecurityObservationFilter(observations, policy); }
    }
}
