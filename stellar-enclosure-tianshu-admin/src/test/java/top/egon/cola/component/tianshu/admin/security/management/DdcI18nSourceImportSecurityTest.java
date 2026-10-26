package top.egon.cola.component.tianshu.admin.security.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.component.tianshu.admin.config.DdcSourceImportWebConfiguration;
import top.egon.cola.component.tianshu.admin.controller.i18n.DdcI18nSourceImportController;
import top.egon.cola.component.tianshu.admin.controller.i18n.DdcI18nSourceImportExceptionAdvice;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportIdentityBO;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nSourceImportJson;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nSourceImportService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportReceipt;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpObservedVerificationComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

/** Exercises real MVC and security filters with explicit verifier/runtime doubles; no server or database is started. */
class DdcI18nSourceImportSecurityTest {
    private static final String PATH = "/internal/v1/i18n/source-imports";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ServiceAccessTokenVerifier serviceVerifier;
    private UserAccessTokenVerifier userVerifier;
    private DdcI18nSourceImportService service;
    private IdpObservedVerificationComponent observations;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        serviceVerifier = context.getBean(ServiceAccessTokenVerifier.class);
        userVerifier = context.getBean(UserAccessTokenVerifier.class);
        service = context.getBean(DdcI18nSourceImportService.class);
        observations = context.getBean(IdpObservedVerificationComponent.class);
        when(serviceVerifier.verify("valid")).thenReturn(valid(identity().principal(), true));
        var request = request();
        when(service.importSource(any(), any())).thenReturn(new SourceImportReceipt("901", request.operationId(), request.scope(),
                request.producerId(), request.producerVersion(), request.sourceVersion(), request.digests(), request.projectionDigest(), 7, 3, 10, NOW));
    }

    @AfterEach void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test void onlyVerifiedTenantServiceTokensReachTheStrictControllerWithoutCreatingASession() throws Exception {
        var result = mvc.perform(post(PATH).header("Authorization", "Bearer valid").contentType(MediaType.APPLICATION_JSON).content(bytes(body(request()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.receiptId").value("901"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        ArgumentCaptor<DdcSourceImportIdentityBO> identity = ArgumentCaptor.forClass(DdcSourceImportIdentityBO.class);
        verify(service).importSource(identity.capture(), eq(request()));
        assertThat(identity.getValue().issuer()).isEqualTo(ISSUER);
        assertThat(identity.getValue().tenantId()).isEqualTo(101L);
        verifyNoInteractions(userVerifier);
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test void missingBearerAndCookieOnlyCallsCannotReachTheImportService() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(bytes(body(request()))))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        var result = mvc.perform(post(PATH).cookie(new jakarta.servlet.http.Cookie("access", "not-a-bearer"))
                        .contentType(MediaType.APPLICATION_JSON).content(bytes(body(request()))))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(service, serviceVerifier, userVerifier);
    }

    @Test void aUserTokenIsNotRetriedThroughTheUserVerifier() throws Exception {
        when(serviceVerifier.verify("user-token")).thenReturn(new AccessTokenVerification.Invalid<>("SERVICE_PRINCIPAL_REQUIRED"));
        mvc.perform(post(PATH).header("Authorization", "Bearer user-token").contentType(MediaType.APPLICATION_JSON).content(bytes(body(request()))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userVerifier, service);
    }

    @Test void platformContextMissingCoarseScopeAndMissingVerifiedIssuerFailClosed() throws Exception {
        when(serviceVerifier.verify("platform")).thenReturn(valid(principal(ServiceTokenContext.PLATFORM, Set.of(DdcSourceImportIdentityBO.REQUIRED_SCOPE)), true));
        when(serviceVerifier.verify("scope")).thenReturn(valid(principal(ServiceTokenContext.TENANT, Set.of("TIANSHU_READ")), true));
        when(serviceVerifier.verify("issuer")).thenReturn(valid(identity().principal(), false));
        for (String token : new String[]{"platform", "scope", "issuer"}) {
            mvc.perform(post(PATH).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(bytes(body(request()))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void unknownManifestFieldsCannotFallBackToTheDefaultJacksonConverter() throws Exception {
        var input = body(request());
        input.put("publishDraft", true);
        mvc.perform(post(PATH).header("Authorization", "Bearer valid").contentType(MediaType.APPLICATION_JSON).content(bytes(input)))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test void databaseAuthorityFailureReturnsUnavailableWithoutCallingTheDomainService() throws Exception {
        when(serviceVerifier.verify("unavailable")).thenReturn(new AccessTokenVerification.Unavailable<>("IDENTITY_STATE_UNAVAILABLE"));
        mvc.perform(post(PATH).header("Authorization", "Bearer unavailable").contentType(MediaType.APPLICATION_JSON).content(bytes(body(request()))))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(service);
    }

    @Test void grantDenialMustCompleteItsStrictAuditHandoff() throws Exception {
        when(observations.enabled()).thenReturn(true);
        when(observations.authorizationDenied(any(TokenAuditProofBO.class), anyString())).thenReturn(false);
        when(service.importSource(any(), any())).thenThrow(new AccessDeniedException("grant disabled"));
        mvc.perform(post(PATH).header("Authorization", "Bearer valid").contentType(MediaType.APPLICATION_JSON).content(bytes(body(request()))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OAUTH_AUDIT_DELIVERY_UNAVAILABLE"));
    }

    @Test void receiptQueriesCannotUseAnOperationIdWithoutTheFullScope() throws Exception {
        mvc.perform(get(PATH + "/" + request().operationId()).header("Authorization", "Bearer valid"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    private static AccessTokenVerification.Valid<ServiceIdentityPrincipal> valid(ServiceIdentityPrincipal principal, boolean issuer) {
        return new AccessTokenVerification.Valid<>(principal, principal.scopes(),
                new TokenAuditProofBO(ISSUER, RESOURCE, "a".repeat(64), "b".repeat(64), "c".repeat(64)), issuer ? ISSUER : null);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({DdcSourceImportSecurityConfiguration.class, DdcSourceImportWebConfiguration.class, DdcI18nSourceImportController.class,
            DdcI18nSourceImportExceptionAdvice.class, DdcI18nSourceImportJson.class})
    static class TestConfiguration {
        @Bean("ddcI18nSourceImportService") DdcI18nSourceImportService service() { return mock(DdcI18nSourceImportService.class); }
        @Bean UserAccessTokenVerifier userAccessTokenVerifier() { return mock(UserAccessTokenVerifier.class); }
        @Bean ServiceAccessTokenVerifier serviceAccessTokenVerifier() { return mock(ServiceAccessTokenVerifier.class); }
        @Bean IdpObservedVerificationComponent observations() { return mock(IdpObservedVerificationComponent.class); }
        @Bean("ddcAdminAuthenticationEntryPoint") DdcAdminAuthenticationEntryPoint errors() { return new DdcAdminAuthenticationEntryPoint(new ObjectMapper()); }
        @Bean("idpBearerAuthenticationFilter") IdpBearerAuthenticationFilter identityFilter(UserAccessTokenVerifier users,
                ServiceAccessTokenVerifier services, IdpEndpointAuthenticationPolicy policy) {
            return new IdpBearerAuthenticationFilter(users, services, policy, new ObjectMapper());
        }
        @Bean("idpSecurityObservationFilter") IdpSecurityObservationFilter observationFilter(IdpObservedVerificationComponent observations,
                IdpEndpointAuthenticationPolicy policy) { return new IdpSecurityObservationFilter(observations, policy); }
    }
}
