package top.egon.cola.component.tianshu.admin.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.tianshu.admin.internal.controller.DdcAiInternalController;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcAiInternalConverter;
import top.egon.cola.component.tianshu.admin.security.ai.DdcAiInternalSecurityConfiguration;
import top.egon.cola.component.tianshu.admin.security.ai.DdcAiServiceCallerResolver;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpObservedVerificationComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.config.DdcAiWebConfiguration;
import top.egon.cola.component.tianshu.admin.controller.ai.DdcAiResourceController;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiPromotionCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcAiResourceSummaryDTO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcAiVersionSummaryDTO;
import top.egon.cola.component.tianshu.admin.model.query.DdcAiIdQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiPublicationResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiResourceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiVersionResult;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiFeedbackService;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiOptimizationService;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiResourceService;
import top.egon.cola.component.tianshu.model.ai.DdcAiContentDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiKindEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiResourceQuery;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DdcAiResourceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DdcAiWebConfiguration.class, top.egon.cola.component.tianshu.admin.config.DdcTransportSliceTestConfiguration.class})
class DdcEndToEndContractTest {

    private static final long TENANT_ID = 101L;
    private static final long SOURCE_RESOURCE_ID = 810001L;
    private static final long TARGET_RESOURCE_ID = 810002L;
    private static final long SOURCE_VERSION_ID = 820001L;
    private static final long TARGET_VERSION_ID = 820002L;
    private static final String CONTENT_HASH = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T12:00:00Z");
    private static final DdcAiScopeDTO SOURCE_SCOPE = new DdcAiScopeDTO(
            "retail", "draft-space", "dev", "assistant-app");
    private static final DdcAiScopeDTO TARGET_SCOPE = new DdcAiScopeDTO(
            "retail", "production-space", "prod", "assistant-app");
    private static final String RESOURCE_KEY = "review-order";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean(name = "ddcAiResourceService")
    private DdcAiResourceService service;

    @MockitoBean(name = "ddcAiOptimizationService")
    private DdcAiOptimizationService optimizationService;

    @MockitoBean(name = "ddcAiFeedbackService")
    private DdcAiFeedbackService feedbackService;

    @Test
    void restDraftPublishPromotionAndOAuthHttpReadKeepOneFixedSnapshotAndSeparateReceipts() throws Exception {
        DdcAiResourceResult created = resourceResult(SOURCE_RESOURCE_ID, SOURCE_SCOPE, RESOURCE_KEY,
                "draft prompt", null, null);
        DdcAiResourceResult saved = resourceResult(SOURCE_RESOURCE_ID, SOURCE_SCOPE, RESOURCE_KEY,
                "approved prompt", null, null);
        DdcAiPublicationResult publishReceipt = publication(SOURCE_RESOURCE_ID, "PUBLISH",
                SOURCE_VERSION_ID, 4L, null, false);
        DdcAiPublicationResult promotionReceipt = publication(TARGET_RESOURCE_ID, "PROMOTE",
                TARGET_VERSION_ID, 1L, SOURCE_VERSION_ID, true);
        DdcAiVersionResult targetSnapshot = versionResult(TARGET_RESOURCE_ID, TARGET_VERSION_ID, 1L,
                "approved prompt");

        when(service.create(any())).thenReturn(created);
        when(service.replaceDraft(any(), any())).thenReturn(saved);
        when(service.publish(any(), eq("publish_order_0001"), any())).thenReturn(publishReceipt);
        when(service.promote(any(), any(), eq("promote_order_0001"))).thenReturn(promotionReceipt);
        when(service.getPublishedVersion(any(), eq(DdcAiKindEnum.PROMPT))).thenAnswer(invocation -> {
            DdcAiResourceQuery query = invocation.getArgument(0);
            DdcAiScopeDTO actualScope = SOURCE_SCOPE.namespaceCode().equals(query.namespaceCode()) ? SOURCE_SCOPE : TARGET_SCOPE;
            var caller = DdcAiServiceCallerResolver.current(Operation.READ, actualScope);
            long callerTenant = caller.tenant().tenantId();
            assertThat(MDC.get("tenantId")).isEqualTo(Long.toString(callerTenant));
            assertThat(MDC.get("userId")).isEqualTo("service:runtime-reader");
            if (callerTenant != TENANT_ID) {
                throw new CommonException(56307, "TIANSHU_AI_SCOPE_FORBIDDEN",
                        "AI permission is required for the requested namespace");
            }
            if (query.resourceId() == SOURCE_RESOURCE_ID) {
                throw new CommonException(56302, "TIANSHU_AI_NOT_FOUND",
                        "Resource has no visible published version");
            }
            return targetSnapshot;
        });

        mockMvc.perform(post("/api/v1/tianshu/ai/resources")
                        .header("X-Egon-Tianshu-Tenant", "tenant-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(SOURCE_SCOPE, "draft prompt")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/tianshu/ai/resources/810001"))
                .andExpect(jsonPath("$.data.resource.id").value("810001"));

        mockMvc.perform(put("/api/v1/tianshu/ai/resources/{id}/draft", SOURCE_RESOURCE_ID)
                        .header("X-Egon-Tianshu-Tenant", "tenant-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody("approved prompt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft.promptText").value("approved prompt"));

        String publishJson = mockMvc.perform(post("/api/v1/tianshu/ai/resources/{id}/publications",
                        SOURCE_RESOURCE_ID)
                        .header("X-Egon-Tianshu-Tenant", "tenant-101")
                        .header("Idempotency-Key", "publish_order_0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":\"3\",\"changeNote\":\"approved\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/tianshu/ai/resources/810001/versions/4"))
                .andExpect(jsonPath("$.data.operation").value("PUBLISH"))
                .andExpect(jsonPath("$.data.targetVersionNo").value("4"))
                .andReturn().getResponse().getContentAsString();
        assertThat(publishJson).doesNotContain("ackCount", "ackStatus", "targetCount");

        mockMvc.perform(post("/api/v1/tianshu/ai/resources/{id}/promotions", SOURCE_RESOURCE_ID)
                        .header("X-Egon-Tianshu-Tenant", "tenant-101")
                        .header("Idempotency-Key", "promote_order_0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/tianshu/ai/resources/810002/versions/1"))
                .andExpect(jsonPath("$.data.operation").value("PROMOTE"))
                .andExpect(jsonPath("$.data.sourceVersionId").value("820001"))
                .andExpect(jsonPath("$.data.targetVersionNo").value("1"));

        verify(service).create(argThat(command -> command.getResourceKey().equals(RESOURCE_KEY)
                && command.getScope().equals(SOURCE_SCOPE)));
        verify(service).replaceDraft(eq(new DdcAiIdQuery(SOURCE_RESOURCE_ID)),
                argThat(command -> command.getDraft().getPromptText().equals("approved prompt")));
        verify(service).publish(eq(new DdcAiIdQuery(SOURCE_RESOURCE_ID)), eq("publish_order_0001"),
                argThat(command -> command.getExpectedRevision().equals("3")));
        verify(service).promote(eq(new DdcAiIdQuery(SOURCE_RESOURCE_ID)), argThat(command ->
                        command.getSourceVersionNo().equals("4")
                        && command.getTargetScope().equals(TARGET_SCOPE)
                        && command.getExpectedTargetRevision() == null), eq("promote_order_0001"));

        try (var internal = new InternalHttpFixture(service, feedbackService)) {
            internal.mvc.perform(promptRead(TARGET_RESOURCE_ID, TARGET_SCOPE, 1L))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_UNAUTHENTICATED));
            internal.mvc.perform(promptRead(TARGET_RESOURCE_ID, TARGET_SCOPE, 1L)
                            .header("Authorization", "Bearer approved")
                            .header("X-Egon-Tianshu-Tenant", "tenant-999").header("X-Tenant-Id", "999"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(jsonPath("$.resourceId").value(Long.toString(TARGET_RESOURCE_ID)))
                    .andExpect(jsonPath("$.versionId").value(Long.toString(TARGET_VERSION_ID)))
                    .andExpect(jsonPath("$.versionNo").value("1"))
                    .andExpect(jsonPath("$.template").value("approved prompt"));
            verify(service).getPublishedVersion(argThat(query -> query.resourceId() == TARGET_RESOURCE_ID
                            && query.versionNo().equals(1L) && query.resourceKey().equals(RESOURCE_KEY)),
                    eq(DdcAiKindEnum.PROMPT));

            mockMvc.perform(delete("/api/v1/tianshu/ai/resources/{id}", SOURCE_RESOURCE_ID)
                            .header("X-Egon-Tianshu-Tenant", "tenant-101")
                            .param("expectedRevision", "4"))
                    .andExpect(status().isNoContent());

            internal.mvc.perform(promptRead(SOURCE_RESOURCE_ID, SOURCE_SCOPE, 4L).header("Authorization", "Bearer approved"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_NOT_FOUND));
            internal.mvc.perform(promptRead(TARGET_RESOURCE_ID, TARGET_SCOPE, 1L).header("Authorization", "Bearer foreign"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_FORBIDDEN));
            internal.mvc.perform(promptRead(102L, TARGET_RESOURCE_ID, TARGET_SCOPE, 1L)
                            .header("Authorization", "Bearer approved"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_FORBIDDEN));
            verify(service, times(2)).getPublishedVersion(any(), eq(DdcAiKindEnum.PROMPT));
        }

        verify(service).delete(eq(new DdcAiIdQuery(SOURCE_RESOURCE_ID)), any());
    }

    private static DdcAiResourceResult resourceResult(long resourceId, DdcAiScopeDTO scope, String key,
                                                       String promptText, String currentVersionId,
                                                       String currentVersionNo) {
        DdcAiResourceSummaryDTO resource = DdcAiResourceSummaryDTO.builder()
                .id(Long.toString(resourceId)).scope(scope).kind(DdcAiKindEnum.PROMPT)
                .resourceKey(key).displayName("Review order").description("Prompt")
                .revision("3").lastVersionNo("4").currentVersionId(currentVersionId)
                .currentVersionNo(currentVersionNo).updatedAt(CREATED_AT).build();
        return DdcAiResourceResult.builder().resource(resource)
                .draft(DdcAiContentDTO.builder().promptText(promptText).nodes(List.of()).build()).build();
    }

    private static DdcAiPublicationResult publication(long resourceId, String operation, long versionId,
                                                      long versionNo, Long sourceVersionId,
                                                      boolean targetCreated) {
        return DdcAiPublicationResult.builder().publicationId(830001L).resourceId(resourceId)
                .operation(operation).previousVersionId(null).targetVersionId(versionId).targetVersionNo(versionNo)
                .sourceVersionId(sourceVersionId).resourceRevision(4L).targetCreated(targetCreated)
                .createdAt(CREATED_AT).build();
    }

    private static DdcAiVersionResult versionResult(long resourceId, long versionId, long versionNo,
                                                    String promptText) {
        DdcAiVersionSummaryDTO version = DdcAiVersionSummaryDTO.builder()
                .id(versionId).resourceId(resourceId).versionNo(versionNo).kind(DdcAiKindEnum.PROMPT.name())
                .displayName("Review order").description("Prompt").contentSha256(CONTENT_HASH)
                .contentBytes((long) promptText.length()).nodeCount(0).changeNote("promoted snapshot")
                .createdAt(CREATED_AT).createdBy("user:operator").build();
        return DdcAiVersionResult.builder().version(version)
                .content(DdcAiContentDTO.builder().promptText(promptText).nodes(List.of()).build()).build();
    }

    private String createBody(DdcAiScopeDTO scope, String promptText) throws Exception {
        return objectMapper.writeValueAsString(DdcAiCreateCommand.builder().scope(scope)
                .kind(DdcAiKindEnum.PROMPT).resourceKey(RESOURCE_KEY)
                .displayName("Review order").description("Prompt")
                .draft(DdcAiContentDTO.builder().promptText(promptText).nodes(List.of()).build()).build());
    }

    private String draftBody(String promptText) throws Exception {
        return objectMapper.writeValueAsString(DdcAiDraftCommand.builder().expectedRevision("3")
                .displayName("Review order")
                .description("Prompt").draft(DdcAiContentDTO.builder().promptText(promptText).nodes(List.of()).build())
                .build());
    }

    private String promotionBody() throws Exception {
        return objectMapper.writeValueAsString(DdcAiPromotionCommand.builder().sourceVersionNo("4")
                .targetScope(TARGET_SCOPE)
                .targetResourceKey(RESOURCE_KEY).expectedTargetRevision(null).changeNote("promote")
                .build());
    }

    private static MockHttpServletRequestBuilder promptRead(long resourceId, DdcAiScopeDTO scope, long versionNo) {
        return promptRead(TENANT_ID, resourceId, scope, versionNo);
    }

    private static MockHttpServletRequestBuilder promptRead(long tenantId, long resourceId, DdcAiScopeDTO scope, long versionNo) {
        return get(DdcAiHttpContract.PROMPTS_PATH).param("tenantId", Long.toString(tenantId))
                .param("bizCode", scope.bizCode()).param("env", scope.env()).param("appCode", scope.appCode())
                .param("namespaceCode", scope.namespaceCode()).param("resourceKey", RESOURCE_KEY)
                .param("resourceId", Long.toString(resourceId)).param("versionNo", Long.toString(versionNo));
    }

    /** Real stateless OAuth filter chain and MVC; only token authority and business storage are test doubles. */
    private static final class InternalHttpFixture implements AutoCloseable {
        private final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        private final MockMvc mvc;
        private final Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        private final org.springframework.security.core.Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();

        private InternalHttpFixture(DdcAiResourceService resources, DdcAiFeedbackService feedback) {
            context.setServletContext(new MockServletContext());
            context.addBeanFactoryPostProcessor(factory -> {
                factory.registerSingleton("ddcAiResourceService", resources);
                factory.registerSingleton("ddcAiFeedbackService", feedback);
            });
            context.register(InternalHttpConfiguration.class);
            context.refresh();
            mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            ServiceAccessTokenVerifier tokens = context.getBean(ServiceAccessTokenVerifier.class);
            when(tokens.verify("approved")).thenReturn(verifiedService("tenant-101"));
            when(tokens.verify("foreign")).thenReturn(verifiedService("tenant-102"));
            when(context.getBean(DdcTenantRegistry.class).requireByIdentityTenantId("tenant-101"))
                    .thenReturn(new DdcTenantBindingDTO(TENANT_ID, "tenant-101", true));
        }

        @Override public void close() {
            context.close();
            if (previousMdc == null) MDC.clear(); else MDC.setContextMap(previousMdc);
            if (previousAuthentication == null) SecurityContextHolder.clearContext();
            else SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    private static final URI ISSUER = URI.create("https://identity.example.test/tenants/tenant-101");
    private static final URI RESOURCE = URI.create("https://tianshu.example.test");
    private static final String READ_SCOPE = "fixture:ai-read";

    private static AccessTokenVerification.Valid<ServiceIdentityPrincipal> verifiedService(String tenant) {
        var principal = new ServiceIdentityPrincipal("runtime-reader", tenant, "runtime-reader", "synthetic-jti", RESOURCE,
                1L, Set.of(READ_SCOPE), "retail", "assistant-app", "prod", "rotated-credential", CREATED_AT.minusSeconds(1),
                CREATED_AT.plusSeconds(300), "assistant-app-id", ServiceTokenContext.TENANT);
        return new AccessTokenVerification.Valid<>(principal, principal.scopes(),
                new TokenAuditProofBO(ISSUER, RESOURCE, "a".repeat(64), "b".repeat(64), "c".repeat(64)), ISSUER);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({DdcAiInternalSecurityConfiguration.class, DdcAiInternalController.class})
    static class InternalHttpConfiguration {
        @Bean("jacksonObjectMapper") ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean("ddcPublishClock") Clock clock() { return Clock.fixed(CREATED_AT, ZoneOffset.UTC); }
        @Bean("ddcTenantRegistry") DdcTenantRegistry tenants() { return mock(DdcTenantRegistry.class); }
        @Bean("ddcTenantContextExecutor") DdcTenantContextExecutor tenantContext() { return new DdcTenantContextExecutor(); }
        @Bean("userAccessTokenVerifier") UserAccessTokenVerifier users() { return mock(UserAccessTokenVerifier.class); }
        @Bean("serviceAccessTokenVerifier") ServiceAccessTokenVerifier services() { return mock(ServiceAccessTokenVerifier.class); }
        @Bean("ddcAiInternalConverterImpl") DdcAiInternalConverter converter() { return Mappers.getMapper(DdcAiInternalConverter.class); }
        @Bean(destroyMethod = "close") jakarta.validation.ValidatorFactory validatorFactory() { return jakarta.validation.Validation.buildDefaultValidatorFactory(); }
        @Bean("egonColaValidationUtils") ValidationUtils validation(@org.springframework.beans.factory.annotation.Qualifier("validatorFactory") jakarta.validation.ValidatorFactory factory) { return new ValidationUtils(factory.getValidator()); }
        @Bean("idpObservedVerificationComponent") IdpObservedVerificationComponent observation() {
            var recorder = mock(IdpObservedVerificationComponent.class);
            when(recorder.enabled()).thenReturn(true);
            when(recorder.authenticationFailure()).thenReturn(true);
            when(recorder.authorizationDenied(any(), any())).thenReturn(true);
            return recorder;
        }
        @Bean("idpSecurityObservationFilter") IdpSecurityObservationFilter observationFilter(IdpObservedVerificationComponent recorder) {
            return new IdpSecurityObservationFilter(recorder,
                    new IdpEndpointAuthenticationPolicy(List.of(), List.of(DdcAiHttpContract.BASE_PATH + "/**"), false));
        }
        @Bean @Primary DdcAiInternalSecurityConfiguration.InternalProperties internalPolicy() {
            var caller = new DdcAiInternalSecurityConfiguration.CallerBinding(TENANT_ID, "tenant-101", ISSUER, RESOURCE,
                    "runtime-reader", "assistant-app-id", "retail", "assistant-app", "prod", "runtime-reader",
                    Set.of(SOURCE_SCOPE, TARGET_SCOPE), Set.of(Operation.READ));
            return new DdcAiInternalSecurityConfiguration.InternalProperties(Map.of(Operation.READ, READ_SCOPE), List.of(caller));
        }
    }
}
