package top.egon.cola.component.tianshu.admin.security.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.tianshu.admin.config.DdcAiOptimizationProperties;
import top.egon.cola.component.tianshu.admin.internal.controller.DdcAiInternalController;
import top.egon.cola.component.tianshu.admin.model.bo.*;
import top.egon.cola.component.tianshu.admin.model.convertor.*;
import top.egon.cola.component.tianshu.admin.model.dto.DdcAiVersionSummaryDTO;
import top.egon.cola.component.tianshu.admin.model.vo.*;
import top.egon.cola.component.tianshu.admin.repository.*;
import top.egon.cola.component.tianshu.admin.security.tenancy.*;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiResourceService;
import top.egon.cola.component.tianshu.admin.service.ai.impl.DdcAiFeedbackServiceImpl;
import top.egon.cola.component.tianshu.admin.service.ai.validation.DdcAiScopeValidator;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.*;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.*;
import top.egon.cola.platform.tianquan.shoubing.starter.security.*;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;

import java.net.URI;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class DdcAiInternalHttpSecurityTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenant-a");
    private static final URI RESOURCE = URI.create("https://tianshu.example");
    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");
    private static final DdcAiScopeDTO PLACEMENT = new DdcAiScopeDTO("owner-biz", "private", "prod", "owner-app");
    private static final String CLIENT_QUERY = "?tenantId=1001&bizCode=owner-biz&env=prod&appCode=owner-app";
    private static final String READ_QUERY = CLIENT_QUERY + "&namespaceCode=private&resourceKey=answer&resourceId=7";
    private static final String REPORT = "{\"usageId\":\"usage_123456789012\",\"namespaceCode\":\"private\",\"resourceKey\":\"answer\",\"resourceId\":\"7\",\"versionId\":\"9\",\"versionNo\":\"4\",\"outcome\":\"SUCCESS\",\"occurredAt\":\"2026-11-19T00:00:00Z\"}";
    private static final String DRAFT = "{\"namespaceCode\":\"private\",\"resourceKey\":\"answer\",\"resourceId\":\"7\",\"expectedRevision\":\"0\",\"displayName\":\"Answer\",\"description\":\"\",\"files\":[{\"path\":\"\",\"nodeType\":\"DIRECTORY\",\"content\":null},{\"path\":\"SKILL.md\",\"nodeType\":\"FILE\",\"content\":\"# Skill\"}]}";
    private static final String PUBLISH = "{\"namespaceCode\":\"private\",\"resourceKey\":\"answer\",\"resourceId\":\"7\",\"expectedRevision\":\"0\",\"requestId\":\"publication_12345678\",\"changeNote\":\"\"}";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ServiceAccessTokenVerifier verifier;
    private DdcAiUsageFeedbackRepository feedback;
    private DdcAiResourceService resources;

    @BeforeEach
    void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        Map<String, Object> values = new HashMap<>();
        String prefix = "egon.cola.component.tianshu.ai.internal.";
        for (Operation operation : Operation.values()) values.put(prefix + "operation-scopes[" + operation + "]", scope(operation));
        prefix += "callers[0].";
        values.put(prefix + "tenant-id", "1001"); values.put(prefix + "identity-tenant-id", "tenant-a");
        values.put(prefix + "issuer", ISSUER.toString()); values.put(prefix + "resource-uri", RESOURCE.toString());
        values.put(prefix + "client-id", "reporter-client"); values.put(prefix + "app-id", "caller-app");
        values.put(prefix + "source-biz-code", "source-biz"); values.put(prefix + "source-app-code", "runtime-app");
        values.put(prefix + "source-environment", "prod"); values.put(prefix + "stable-actor-id", "stable-reporter");
        values.put(prefix + "placements[0].biz-code", "owner-biz"); values.put(prefix + "placements[0].namespace-code", "private");
        values.put(prefix + "placements[0].env", "prod"); values.put(prefix + "placements[0].app-code", "owner-app");
        values.put(prefix + "operations", "READ,WRITE,PUBLISH,REPORT");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("approved-test-caller", values));
        context.register(Fixture.class); context.refresh();
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        verifier = context.getBean(ServiceAccessTokenVerifier.class);
        feedback = context.getBean(DdcAiUsageFeedbackRepository.class);
        resources = context.getBean(DdcAiResourceService.class);
        var observations = context.getBean(IdpObservedVerificationComponent.class);
        when(observations.enabled()).thenReturn(true);
        when(observations.authenticationFailure()).thenReturn(true);
        when(observations.authorizationDenied(any(), anyString())).thenReturn(true);
        for (Operation operation : Operation.values()) {
            var principal = principal("reporter-client", "secret-v1", Set.of(scope(operation)), RESOURCE, ServiceTokenContext.TENANT);
            when(verifier.verify(operation.name())).thenReturn(valid(principal, ISSUER));
        }
        when(context.getBean(DdcTenantRegistry.class).requireByIdentityTenantId("tenant-a"))
                .thenReturn(new DdcTenantBindingDTO(1001L, "tenant-a", true));
        var row = row("owner-app");
        when(context.getBean(DdcAiResourceRepository.class).findActiveById(7L)).thenReturn(Optional.of(row));
        when(context.getBean(DdcAiVersionRepository.class).findActiveById(9L)).thenReturn(Optional.of(DdcAiVersionBO.builder().id(9L).resourceId(7L).versionNo(4L).build()));
        when(context.getBean(DdcNamespaceRepository.class).findByBizCodeAndNamespaceCode("owner-biz", "private"))
                .thenReturn(Optional.of(DdcNamespaceBO.builder().id(71L).publicId("private-id").build()));
        when(context.getBean(DdcNamespaceEnvAppBindingRepository.class).findAll("owner-biz", "private", "prod", "owner-app"))
                .thenReturn(List.of(DdcNamespaceEnvAppBindingBO.builder().enabled(true).build()));
        when(feedback.insert(any())).thenAnswer(call -> call.getArgument(0));
        var scope = context.getBean(DdcAiScopeValidator.class);
        when(resources.getPublishedVersion(any(), any())).thenAnswer(call -> {
            scope.assertCanRead(row);
            DdcAiKindEnum kind = call.getArgument(1);
            return DdcAiVersionResult.builder().version(DdcAiVersionSummaryDTO.builder().id(Long.MAX_VALUE)
                    .resourceId(7L).versionNo(Long.MAX_VALUE).kind(kind.name()).contentSha256("a".repeat(64)).build())
                    .content(DdcAiContentDTO.builder().promptText(kind == DdcAiKindEnum.PROMPT ? "template" : null)
                            .nodes(kind == DdcAiKindEnum.SKILL ? List.of(DdcAiNodeDTO.builder().path("SKILL.md").nodeType(DdcAiNodeTypeEnum.FILE).content("# Skill").build()) : null).build()).build();
        });
        when(resources.getVersionHead(any())).thenAnswer(call -> {
            scope.assertCanRead(row);
            return DdcAiVersionHeadResult.builder().resourceId(7L).resourceKey("answer").resourceRevision(0L).published(false).build();
        });
        when(resources.replaceSkillDraft(any())).thenAnswer(call -> {
            scope.assertCanWrite(row);
            return DdcAiRevisionResult.builder().resourceId(7L).resourceRevision(Long.MAX_VALUE).draftSha256("b".repeat(64)).draftMatchesCurrent(false).build();
        });
        when(resources.publishSkillDraft(any())).thenAnswer(call -> {
            scope.assertCanPublish(row);
            assertThat(DdcAiServiceCallerResolver.current(Operation.PUBLISH).tenant().actor()).isEqualTo("service:stable-reporter");
            return DdcAiPublicationReceiptResult.builder().publicationId(Long.MAX_VALUE).resourceId(7L).targetVersionId(9L).targetVersionNo(4L).resourceRevision(2L).contentSha256("a".repeat(64)).build();
        });
        MDC.put("tenantId", "outer"); MDC.put("userId", "actor");
    }

    @AfterEach
    void stop() { context.close(); MDC.clear(); SecurityContextHolder.clearContext(); }

    @Test
    void actualProtectedReadsPreserveExactIdsPinsAndUnpublishedNulls() throws Exception {
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY + "&versionNo=" + Long.MAX_VALUE).header("Authorization", "Bearer READ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionId").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.versionNo").value(Long.toString(Long.MAX_VALUE))).andExpect(jsonPath("$.template").value("template"));
        verify(resources).getPublishedVersion(argThat(q -> q.versionNo() == Long.MAX_VALUE && q.resourceId() == 7), eq(DdcAiKindEnum.PROMPT));
        mvc.perform(get(DdcAiHttpContract.SKILLS_PATH + READ_QUERY).header("Authorization", "Bearer READ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files[0].path").value("SKILL.md"));
        String head = mvc.perform(get(DdcAiHttpContract.VERSION_HEAD_PATH + READ_QUERY).header("Authorization", "Bearer READ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false)).andReturn().getResponse().getContentAsString();
        var tree = context.getBean(ObjectMapper.class).readTree(head);
        assertThat(tree.has("versionId") && tree.get("versionId").isNull()).isTrue();
        assertThat(tree.has("versionNo") && tree.get("versionNo").isNull()).isTrue();
        assertThat(tree.has("contentSha256") && tree.get("contentSha256").isNull()).isTrue();
        assertThat(MDC.get("tenantId")).isEqualTo("outer"); assertThat(MDC.get("userId")).isEqualTo("actor");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThatThrownBy(() -> DdcAiServiceCallerResolver.current(Operation.READ)).isInstanceOf(DdcAiServiceCallerResolver.Rejected.class);
        verifyNoInteractions(context.getBean(UserAccessTokenVerifier.class));
        var fixture = context.getBean(Fixture.class);
        verifyNoInteractions(fixture.users, fixture.maskers, fixture.policies, fixture.writes);
    }

    @Test
    void authoringIsPerOperationAndStableAcrossCredentialRotation() throws Exception {
        mvc.perform(post(DdcAiHttpContract.SKILL_DRAFT_PATH + CLIENT_QUERY).header("Authorization", "Bearer WRITE").contentType("application/json").content(DRAFT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resourceRevision").value(Long.toString(Long.MAX_VALUE)));
        verify(resources).replaceSkillDraft(argThat(command -> "0".equals(command.getExpectedRevision()) && command.getContent().getNodes().size() == 2));
        var rotated = principal("reporter-client", "secret-v2", Set.of(scope(Operation.PUBLISH)), RESOURCE, ServiceTokenContext.TENANT);
        when(verifier.verify("rotated")).thenReturn(valid(rotated, ISSUER));
        for (String bearer : List.of("PUBLISH", "rotated")) {
            mvc.perform(post(DdcAiHttpContract.SKILL_PUBLICATIONS_PATH + CLIENT_QUERY).header("Authorization", "Bearer " + bearer).contentType("application/json").content(PUBLISH))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.publicationId").value(Long.toString(Long.MAX_VALUE)));
        }
        for (String bearer : List.of("READ", "REPORT", "WRITE")) {
            mvc.perform(post(DdcAiHttpContract.SKILL_PUBLICATIONS_PATH + CLIENT_QUERY).header("Authorization", "Bearer " + bearer).contentType("application/json").content(PUBLISH))
                    .andExpect(status().isForbidden());
        }
        verify(resources, times(2)).publishSkillDraft(argThat(command -> "publication_12345678".equals(command.getRequestId()) && "0".equals(command.getExpectedRevision())));
    }

    @Test
    void reportPreservesPerItemReceiptsStableReporterAndActualSourceAttribution() throws Exception {
        when(feedback.insert(any())).thenAnswer(call -> {
            DdcAiUsageFeedbackBO inserted = call.getArgument(0);
            assertThat(MDC.get("tenantId")).isEqualTo("1001");
            assertThat(inserted.getReporterCredentialId()).isEqualTo("stable-reporter");
            assertThat(inserted.getBizCode()).isEqualTo("source-biz"); assertThat(inserted.getAppCode()).isEqualTo("runtime-app");
            return inserted;
        });
        mvc.perform(post(DdcAiHttpContract.USAGE_REPORTS_PATH).header("Authorization", "Bearer REPORT").header("X-Tenant-Id", "9999")
                        .contentType("application/json").content("{\"reports\":[" + REPORT + "," + REPORT.replace("usage_123456789012", "bad") + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[1].status").value("REJECTED")).andExpect(jsonPath("$.items[1].reason").value("INVALID_FIELD"));
        verify(feedback, times(1)).insert(any());
        assertThat(MDC.get("tenantId")).isEqualTo("outer");
    }

    @Test
    void selectorsCannotChooseTenantOrBroadenResourcePlacement() throws Exception {
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY.replace("tenantId=1001", "tenantId=9999")).header("Authorization", "Bearer READ"))
                .andExpect(status().isForbidden());
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY.replace("appCode=owner-app", "appCode=other-owner")).header("Authorization", "Bearer READ"))
                .andExpect(status().isForbidden());
        verify(resources, times(1)).getPublishedVersion(any(), any());
        var other = row("other-owner");
        when(context.getBean(DdcAiResourceRepository.class).findActiveById(7L)).thenReturn(Optional.of(other));
        when(context.getBean(DdcNamespaceEnvAppBindingRepository.class).findAll("owner-biz", "private", "prod", "other-owner"))
                .thenReturn(List.of(DdcNamespaceEnvAppBindingBO.builder().enabled(true).build()));
        mvc.perform(post(DdcAiHttpContract.USAGE_REPORTS_PATH).header("Authorization", "Bearer REPORT").contentType("application/json").content("{\"reports\":[" + REPORT + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("REJECTED"));
        verifyNoInteractions(feedback);
    }

    @Test
    void exactCurrentIdentityScopeAudienceIssuerAndProofAreRequired() throws Exception {
        var correct = principal("reporter-client", "secret-v1", Set.of(scope(Operation.READ)), RESOURCE, ServiceTokenContext.TENANT);
        Map<String, AccessTokenVerification<ServiceIdentityPrincipal>> rejected = new LinkedHashMap<>();
        rejected.put("scope", valid(principal("reporter-client", "secret-v1", Set.of("OTHER"), RESOURCE, ServiceTokenContext.TENANT), ISSUER));
        rejected.put("client", valid(principal("other-client", "secret-v1", correct.scopes(), RESOURCE, ServiceTokenContext.TENANT), ISSUER));
        rejected.put("resource", valid(principal("reporter-client", "secret-v1", correct.scopes(), URI.create("https://other.example"), ServiceTokenContext.TENANT), ISSUER));
        rejected.put("issuer", valid(correct, URI.create("https://other.example")));
        rejected.put("platform", valid(principal("reporter-client", "secret-v1", correct.scopes(), RESOURCE, ServiceTokenContext.PLATFORM), ISSUER));
        rejected.put("expired", valid(new ServiceIdentityPrincipal(correct.subject(), correct.tenantId(), correct.clientId(), correct.tokenId(), correct.resourceUri(), 1,
                correct.scopes(), correct.sourceBizCode(), correct.sourceAppCode(), correct.sourceEnvironment(), correct.credentialId(), NOW.minusSeconds(120), NOW.minusSeconds(1), correct.appId(), correct.scopeContext()), ISSUER));
        rejected.put("authority-scopes", new AccessTokenVerification.Valid<>(correct, Set.of("OTHER"), proof(ISSUER, RESOURCE), ISSUER));
        for (var entry : rejected.entrySet()) {
            when(verifier.verify(entry.getKey())).thenReturn(entry.getValue());
            mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY).header("Authorization", "Bearer " + entry.getKey()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_FORBIDDEN));
        }
        verifyNoInteractions(resources, feedback);
    }

    @Test
    void noCallerGrantIsInferredFromAbsentConfigurationOrRawSpringService() throws Exception {
        var empty = new DdcAiInternalSecurityConfiguration.InternalProperties(Map.of(), List.of());
        assertThat(empty.operationScopes()).isEmpty(); assertThat(empty.callers()).isEmpty();
        var principal = principal("reporter-client", "secret-v1", Set.of(scope(Operation.READ)), RESOURCE, ServiceTokenContext.TENANT);
        var token = new IdpAuthenticationToken(principal, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope(Operation.READ))), ISSUER);
        token.setDetails(proof(ISSUER, RESOURCE));
        SecurityContextHolder.getContext().setAuthentication(token);
        assertThatThrownBy(() -> context.getBean(DdcAiScopeValidator.class).assertCanRead(row("owner-app")))
                .isInstanceOf(top.egon.cola.component.common.core.exception.CommonException.class);
        assertThatThrownBy(() -> DdcAiServiceCallerResolver.current(Operation.READ)).isInstanceOf(DdcAiServiceCallerResolver.Rejected.class);
    }

    @Test
    void verificationTenantAndAuditOutagesAre503WithoutAcknowledgedWrites() throws Exception {
        when(verifier.verify("down")).thenReturn(new AccessTokenVerification.Unavailable<>("IDENTITY_STATE_UNAVAILABLE"));
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY).header("Authorization", "Bearer down"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_UNAVAILABLE));
        when(context.getBean(IdpObservedVerificationComponent.class).enabled()).thenReturn(false);
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY)).andExpect(status().isServiceUnavailable());
        mvc.perform(post(DdcAiHttpContract.USAGE_REPORTS_PATH).header("Authorization", "Bearer REPORT").contentType("application/json").content("{\"reports\":[" + REPORT + "]}"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(feedback, resources);
    }

    @Test
    void auditedPlacementDenialCannotBecomePartialSuccessWhenAuditIsUnavailable() throws Exception {
        when(context.getBean(DdcAiResourceRepository.class).findActiveById(7L)).thenReturn(Optional.of(row("other-owner")));
        when(context.getBean(DdcNamespaceEnvAppBindingRepository.class).findAll("owner-biz", "private", "prod", "other-owner"))
                .thenReturn(List.of(DdcNamespaceEnvAppBindingBO.builder().enabled(true).build()));
        when(context.getBean(IdpObservedVerificationComponent.class).authorizationDenied(any(), anyString())).thenReturn(false);
        mvc.perform(post(DdcAiHttpContract.USAGE_REPORTS_PATH).header("Authorization", "Bearer REPORT").contentType("application/json").content("{\"reports\":[" + REPORT + "]}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_UNAVAILABLE));
        verifyNoInteractions(feedback);
    }

    @Test
    void strictEnvelopeAndDecimalWireFailuresNeverReachAWrite() throws Exception {
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY)).andExpect(status().isUnauthorized());
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY).header("Authorization", "Bearer READ", "Bearer READ")).andExpect(status().isUnauthorized());
        mvc.perform(get(DdcAiHttpContract.VERSION_HEAD_PATH + READ_QUERY + "&versionNo=4").header("Authorization", "Bearer READ")).andExpect(status().isBadRequest());
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY + "&resourceId=8").header("Authorization", "Bearer READ")).andExpect(status().isBadRequest());
        for (String body : List.of("{\"reports\":[]}", "{\"reports\":[null]}", "{\"reports\":[],\"reports\":[]}",
                "{\"reports\":[" + REPORT + "],\"tenantId\":9999}", "{\"reports\":[" + REPORT.replace("\"resourceId\":\"7\"", "\"resourceId\":7") + "]}",
                "{\"reports\":[" + REPORT + "]} {}")) {
            mvc.perform(post(DdcAiHttpContract.USAGE_REPORTS_PATH).header("Authorization", "Bearer REPORT").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(DdcAiHttpContract.SKILL_DRAFT_PATH + CLIENT_QUERY).header("Authorization", "Bearer WRITE").contentType("application/json")
                .content(DRAFT.replace("\"expectedRevision\":\"0\"", "\"expectedRevision\":0"))).andExpect(status().isBadRequest());
        verifyNoInteractions(feedback, resources);
    }

    @Test
    void fixedServiceWireDoesNotUseTheUserFieldPolicySerializer() throws Exception {
        var fixture = context.getBean(Fixture.class);
        when(fixture.policies.fields(DdcAiNodeDTO.class)).thenThrow(new IllegalStateException("fixture-field-policy-unavailable"));
        var factory = new top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionJsonMapperFactory(
                fixture.users, fixture.maskers, fixture.policies, fixture.writes);
        var directDto = DdcSkillResult.builder().files(List.of(DdcAiNodeDTO.builder().path("SKILL.md")
                .nodeType(DdcAiNodeTypeEnum.FILE).content("# Skill").build())).build();
        assertThatThrownBy(() -> factory.forTransport(context.getBean(ObjectMapper.class)).writeValueAsString(directDto))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        mvc.perform(get(DdcAiHttpContract.SKILLS_PATH + READ_QUERY).header("Authorization", "Bearer READ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files[0].content").value("# Skill"));
        verify(fixture.policies, times(1)).fields(DdcAiNodeDTO.class);
        clearInvocations(resources);
        mvc.perform(get(DdcAiHttpContract.SKILLS_PATH + READ_QUERY).header("Authorization", "Bearer REPORT"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resources);
        verify(fixture.policies, times(1)).fields(DdcAiNodeDTO.class);
    }

    @Test
    void aPostCannotTriggerServletFormBodyParsingBeforeAuthentication() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", DdcAiHttpContract.USAGE_REPORTS_PATH) {
            @Override public Map<String, String[]> getParameterMap() { throw new AssertionError("pre-auth parameter parsing"); }
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("pre-auth body read"); }
        };
        request.addHeader("Authorization", "Bearer REPORT");
        for (String contentType : List.of("application/x-www-form-urlencoded", "text/plain", "application/json;charset=UTF-16")) {
            request.setContentType(contentType);
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            new DdcAiInternalSecurityConfiguration.StrictRequestFilter(context.getBean(ObjectMapper.class))
                    .doFilter(request, response, (ignoredRequest, ignoredResponse) -> { throw new AssertionError("unexpected continuation"); });
            assertThat(response.getStatus()).isEqualTo(400);
        }
        verifyNoInteractions(verifier, feedback, resources);
    }

    @Test
    void verifierOwnedFrameDoesNotPropagateToAsyncWorkOrSurviveChangedAuthentication() throws Exception {
        doAnswer(call -> {
            assertThat(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { DdcAiServiceCallerResolver.current(Operation.READ); return false; }
                catch (DdcAiServiceCallerResolver.Rejected denied) { return true; }
            }).join()).isTrue();
            var original = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(null);
            try { assertThatThrownBy(() -> DdcAiServiceCallerResolver.current(Operation.READ)).isInstanceOf(DdcAiServiceCallerResolver.Rejected.class); }
            finally { SecurityContextHolder.getContext().setAuthentication(original); }
            throw new top.egon.cola.component.common.core.exception.CommonException(56302, "TIANSHU_AI_NOT_FOUND", "missing");
        }).when(resources).getPublishedVersion(any(), any());
        mvc.perform(get(DdcAiHttpContract.PROMPTS_PATH + READ_QUERY).header("Authorization", "Bearer READ"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(DdcAiHttpContract.ERROR_NOT_FOUND));
    }

    private static DdcAiResourceBO row(String owner) {
        return DdcAiResourceBO.builder().id(7L).namespaceId(71L).namespaceCode("private").bizCode("owner-biz").envCode("prod").appCode(owner).resourceKey("answer").build();
    }
    private static String scope(Operation operation) { return "DEPLOYED_" + operation; }
    private static TokenAuditProofBO proof(URI issuer, URI resource) { return new TokenAuditProofBO(issuer, resource, "a".repeat(64), "b".repeat(64), "c".repeat(64)); }
    private static ServiceIdentityPrincipal principal(String client, String credential, Set<String> scopes, URI resource, ServiceTokenContext kind) {
        return new ServiceIdentityPrincipal(client, kind == ServiceTokenContext.TENANT ? "tenant-a" : null, client, "jti", resource, 1, scopes,
                "source-biz", "runtime-app", "prod", credential, NOW.minusSeconds(1), NOW.plusSeconds(60), "caller-app", kind);
    }
    private static AccessTokenVerification.Valid<ServiceIdentityPrincipal> valid(ServiceIdentityPrincipal principal, URI issuer) {
        return new AccessTokenVerification.Valid<>(principal, principal.scopes(), proof(issuer, principal.resourceUri()), issuer);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({DdcAiInternalSecurityConfiguration.class, DdcAiInternalController.class})
    static class Fixture implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        private final top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User users = mock(top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User.class);
        private final top.egon.cola.component.common.desensitize.strategy.SensitiveStrategyRegistry maskers = mock(top.egon.cola.component.common.desensitize.strategy.SensitiveStrategyRegistry.class);
        private final top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry policies = mock(top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry.class);
        private final top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard writes = mock(top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard.class);

        @Override public void extendMessageConverters(java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
            var factory = new top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionJsonMapperFactory(users, maskers, policies, writes);
            for (var converter : converters) {
                if (converter instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter jackson) {
                    jackson.setObjectMapper(factory.forTransport(jackson.getObjectMapper()));
                }
            }
        }
        @Bean("ddcAiInternalConverterImpl") DdcAiInternalConverter converter() { return new DdcAiInternalConverterImpl(); }
        @Bean("jacksonObjectMapper") ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean("egonColaValidationUtils") ValidationUtils validation() { return new ValidationUtils(Validation.buildDefaultValidatorFactory().getValidator()); }
        @Bean("userAccessTokenVerifier") UserAccessTokenVerifier users() { return mock(UserAccessTokenVerifier.class); }
        @Bean("serviceAccessTokenVerifier") ServiceAccessTokenVerifier services() { return mock(ServiceAccessTokenVerifier.class); }
        @Bean("ddcPublishClock") Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean("ddcTenantRegistry") DdcTenantRegistry registry() { return mock(DdcTenantRegistry.class); }
        @Bean("ddcTenantContextExecutor") DdcTenantContextExecutor tenants() { return new DdcTenantContextExecutor(); }
        @Bean IdpObservedVerificationComponent observations() { return mock(IdpObservedVerificationComponent.class); }
        @Bean("idpSecurityObservationFilter") IdpSecurityObservationFilter observationFilter(IdpObservedVerificationComponent observations) {
            return new IdpSecurityObservationFilter(observations, new IdpEndpointAuthenticationPolicy(List.of(), List.of(DdcAiHttpContract.BASE_PATH + "/**"), false));
        }
        @Bean DdcAiResourceRepository resourceRepository() { return mock(DdcAiResourceRepository.class); }
        @Bean DdcAiVersionRepository versionRepository() { return mock(DdcAiVersionRepository.class); }
        @Bean DdcNamespaceRepository namespaces() { return mock(DdcNamespaceRepository.class); }
        @Bean DdcNamespaceEnvAppBindingRepository bindings() { return mock(DdcNamespaceEnvAppBindingRepository.class); }
        @Bean DdcAiUsageFeedbackRepository feedbackRepository() { return mock(DdcAiUsageFeedbackRepository.class); }
        @Bean DdcAiScopeValidator scope(DdcAiResourceRepository resources, DdcNamespaceRepository namespaces, DdcNamespaceEnvAppBindingRepository bindings) {
            return new DdcAiScopeValidator(mock(DdcScopeGate.class), resources, namespaces, bindings, mock(PermissionExecutionGuard.class));
        }
        @Bean("ddcAiResourceService") DdcAiResourceService resources() { return mock(DdcAiResourceService.class); }
        @Bean("ddcAiFeedbackService") DdcAiFeedbackServiceImpl feedback(DdcAiUsageFeedbackRepository feedback, DdcAiResourceRepository resources,
                DdcAiVersionRepository versions, DdcAiScopeValidator scope, ValidationUtils validation) {
            var tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
            return new DdcAiFeedbackServiceImpl(feedback, resources, versions, scope, new DdcAiEnumConverterImpl(),
                    new DdcAiUsageFeedbackResultConverterImpl(), validation, tx, tx, new DdcAiOptimizationProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
