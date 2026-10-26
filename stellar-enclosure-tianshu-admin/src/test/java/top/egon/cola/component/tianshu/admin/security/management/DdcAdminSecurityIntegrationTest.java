package top.egon.cola.component.tianshu.admin.security.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import top.egon.cola.component.tianshu.admin.controller.config.DdcCacheController;
import top.egon.cola.component.tianshu.admin.controller.config.DdcConfigController;
import top.egon.cola.component.tianshu.admin.controller.config.DdcPublishTaskController;
import top.egon.cola.component.tianshu.admin.controller.ai.DdcAiResourceController;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigCreateRequest;
import top.egon.cola.component.tianshu.admin.model.query.DdcAiIdQuery;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskResultConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.service.cache.DdcCacheService;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigService;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiFeedbackService;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiOptimizationService;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiResourceService;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishTaskQueryService;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.authorization.AuthorizationService;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        DdcConfigController.class,
        DdcPublishTaskController.class,
        DdcCacheController.class,
        DdcAiResourceController.class,
        top.egon.cola.component.tianshu.admin.controller.i18n.DdcI18nPageController.class,
        top.egon.cola.component.tianshu.admin.controller.i18n.DdcAdminUiI18nController.class,
        top.egon.cola.component.tianshu.admin.controller.i18n.DdcResourceMetadataController.class,
        DdcAdminSecurityIntegrationTest.HealthInfoController.class,
        DdcAdminSecurityIntegrationTest.RegistryInfoController.class,
        DdcAdminSecurityIntegrationTest.BindingInfoController.class,
        DdcAdminSecurityIntegrationTest.PagedInfoController.class
})
@Import({DdcAdminSecurityConfiguration.class, DdcTenantRegistry.class,
        DdcTenantContextExecutor.class, top.egon.cola.component.tianshu.admin.config.DdcI18nWebConfiguration.class,
        DdcAdminSecurityIntegrationTest.JacksonCompatibilityConfiguration.class, top.egon.cola.component.tianshu.admin.config.DdcTransportSliceTestConfiguration.class})
@TestPropertySource(properties = {
        "egon.cola.platform.tianquan.shoubing.usage-observation.enabled=false",
        "egon.cola.component.tianshu.admin.security.local-dev=true",
        "egon.cola.component.tianshu.admin.tenancy.bindings[0].tenant-id=101",
        "egon.cola.component.tianshu.admin.tenancy.bindings[0].identity-tenant-id=tenant-a",
        "egon.cola.component.tianshu.admin.tenancy.bindings[0].enabled=true"
})
class DdcAdminSecurityIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class JacksonCompatibilityConfiguration {
        @org.springframework.context.annotation.Bean
        static org.springframework.beans.factory.config.BeanFactoryPostProcessor jacksonCompatibilityAlias() {
            return top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterAutoConfiguration.idpJacksonObjectMapperAlias();
        }
    }
    @Test
    void aiScopeDenialCannotSkipItsStrictRequestHandoff() {
        var namespaceRepository = org.mockito.Mockito.mock(top.egon.cola.component.tianshu.admin.repository.DdcNamespaceRepository.class);
        var bindings = org.mockito.Mockito.mock(top.egon.cola.component.tianshu.admin.repository.DdcNamespaceEnvAppBindingRepository.class);
        var namespace = new top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceBO(); namespace.setPublicId("namespace-1"); namespace.setId(1L);
        when(namespaceRepository.findByBizCodeAndNamespaceCode("biz-a", "orders" )).thenReturn(java.util.Optional.of(namespace));
        var binding = new top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceEnvAppBindingBO(); binding.setEnabled(true);
        when(bindings.findAll("biz-a", "orders", "dev", "app-a")).thenReturn(List.of(binding));
        var validator = new top.egon.cola.component.tianshu.admin.service.ai.validation.DdcAiScopeValidator(
                org.mockito.Mockito.mock(top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate.class),
                org.mockito.Mockito.mock(top.egon.cola.component.tianshu.admin.repository.DdcAiResourceRepository.class), namespaceRepository, bindings, org.mockito.Mockito.mock(top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard.class));
        var authentication = unscoped("TIANSHU_AI_READ:namespace-1", "scope-fixture");
        authentication.setDetails(new top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO(
                java.net.URI.create("https://idp.example/tenants/1001"), java.net.URI.create("https://api.example/tianshu"), "a".repeat(64), "b".repeat(64), "c".repeat(64)));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> validator.resolveNamespaceIdForRead(new top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO("biz-a", "orders", "dev", "app-a")))
                    .isInstanceOfSatisfying(top.egon.cola.component.common.core.exception.CommonException.class, failure -> {
                        assertThat(failure.getCode()).isEqualTo(56310); assertThat(failure.getStatus()).isEqualTo("OAUTH_AUDIT_DELIVERY_UNAVAILABLE");
                    });
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
    }

    @Test
    void scopeAndCapabilityForSixIntents() throws Exception {
        mockMvc.perform(get("/api/v1/tianshu/configs").with(authentication(unscoped("TIANSHU_READ", "scope-fixture"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tianshu/configs").with(authentication(unscoped("TIANSHU_WRITE", "scope-fixture")))
                        .contentType(MediaType.APPLICATION_JSON).content(configBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tianshu/configs/config-1/publish").with(authentication(unscoped("TIANSHU_PUBLISH", "scope-fixture")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tianshu/cache/check").param("appCode", "app-a").param("env", "dev").param("bizCode", "biz-a")
                        .with(authentication(unscoped("TIANSHU_CACHE", "scope-fixture"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(configService, publishService, cacheService);
        when(configService.list(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/tianshu/configs").with(authentication(rbac3("TIANSHU_READ"))))
                .andExpect(status().isOk());
    }

    @MockitoBean(name = "ddcI18nPageService")
    private top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nPageService i18nService;

    @MockitoBean(name = "ddcAdminUiI18nService")
    private top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nService adminUiI18nService;

    @MockitoBean(name = "ddcAdminUiI18nCatalog")
    private top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nCatalog adminUiI18nCatalog;

    @MockitoBean(name = "ddcResourceMetadataService")
    private top.egon.cola.component.tianshu.admin.service.i18n.DdcResourceMetadataService resourceMetadataService;

    @Test
    void publicAuthLanguageIsAnonymousAndNeverReadsEnterprisePages() throws Exception {
        when(adminUiI18nCatalog.publicBundle("en-US")).thenReturn(
                new top.egon.cola.component.tianshu.admin.model.vo.DdcPublicI18nBundleResult(
                        "tianshu.auth", "en-US", "zh-CN", "public-version", Map.of("login", "Log in")));
        mockMvc.perform(get("/api/v1/tianshu/i18n/public/bundle").param("locale", "en-US")
                        .param("pageCode", "tianshu.configs").param("tenantId", "999").param("bizCode", "attacker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageCode").value("tianshu.auth"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/v1/tianshu/i18n/public/bundle").param("locale", "en-US")
                        .with(authentication(rbac3("TIANSHU_READ"))).header("X-Egon-Tianshu-Tenant", "wrong-tenant"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tianshu/i18n/public/bundle").param("locale", "en-US"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tianshu/i18n/admin/bundle").param("pageCode", "tianshu.bizs").param("locale", "en-US").param("catalogScopeId", "501").param("catalogActivationVersion", "9"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tianshu/i18n/bundle").param("pageCode", "common").param("locale", "en-US"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUiI18nService, i18nService);
    }

    @Test
    void adminLanguageReadRequiresAdminReadAndMatchingExpectedTenant() throws Exception {
        for (String path : List.of("bootstrap", "bundle")) {
            mockMvc.perform(get("/api/v1/tianshu/i18n/admin/" + path).with(authentication(rbac3("TIANSHU_READ"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/tianshu/i18n/admin/" + path).with(authentication(rbac3("TIANSHU_READ")))
                            .header("X-Egon-Tianshu-Tenant", "tenant-b"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/tianshu/i18n/admin/" + path).with(authentication(rbac3("TIANSHU_I18N_READ")))
                            .header("X-Egon-Tianshu-Tenant", "tenant-a"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(adminUiI18nService);
    }

    @Test
    void adminLanguageRequestsCannotChooseTenantOrBusinessScope() throws Exception {
        when(adminUiI18nService.bundle("501", "9", "tianshu.bizs", "en-US")).thenReturn(
                new top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult(
                        "tianshu.bizs", "en-US", "zh-CN", "admin-version", Map.of("title", "Domains")));
        mockMvc.perform(get("/api/v1/tianshu/i18n/admin/bundle").param("pageCode", "tianshu.bizs").param("locale", "en-US").param("catalogScopeId", "501").param("catalogActivationVersion", "9")
                        .param("tenantId", "999").param("bizCode", "attacker").param("appCode", "another-app")
                        .param("namespaceCode", "another-namespace").param("env", "another-env")
                        .with(authentication(rbac3("TIANSHU_READ"))).header("X-Egon-Tianshu-Tenant", "tenant-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages.title").value("Domains"));
        verify(adminUiI18nService).bundle("501", "9", "tianshu.bizs", "en-US");
    }

    @Test
    void adminLanguageInitializationRequiresReadWritePublishTogether() throws Exception {
        for (String permission : List.of("TIANSHU_READ", "TIANSHU_WRITE", "TIANSHU_PUBLISH")) {
            mockMvc.perform(post("/api/v1/tianshu/i18n/admin/initialize").with(authentication(rbac3(permission)))
                            .header("X-Egon-Tianshu-Tenant", "tenant-a"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(adminUiI18nService);
        when(adminUiI18nService.initialize()).thenThrow(
                new top.egon.cola.component.common.core.exception.CommonException(56433, "TIANSHU_I18N_MANIFEST_REQUIRED", "Manifest required"));
        mockMvc.perform(post("/api/v1/tianshu/i18n/admin/initialize").with(authentication(rbac3(java.util.Set.of("TIANSHU_READ", "TIANSHU_WRITE", "TIANSHU_PUBLISH"))))
                        .header("X-Egon-Tianshu-Tenant", "tenant-a"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(56433));
        verify(adminUiI18nService).initialize();
    }

    @Test
    void i18nConditionalRequestStillRequiresPermissionAndExpectedTenant() throws Exception {
        var request = get("/api/v1/tianshu/i18n/bundle").param("bizCode", "retail").param("namespaceCode", "main")
                .param("env", "prod").param("appCode", "shop").param("pageCode", "orders").param("locale", "en-US")
                        .param("catalogScopeId", "501").param("catalogActivationVersion", "9")
                .header("If-None-Match", "W/\"hash\"");
        mockMvc.perform(request.with(authentication(unscoped("TIANSHU_I18N_READ", "reader")))
                        .header("X-Egon-Tianshu-Tenant", "tenant-a")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tianshu/i18n/bundle").with(authentication(rbac3("TIANSHU_I18N_READ"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(request.with(authentication(rbac3("TIANSHU_I18N_READ")))
                        .header("X-Egon-Tianshu-Tenant", "tenant-b")).andExpect(status().isForbidden());
        verifyNoInteractions(i18nService);
    }

    @Test
    void i18nConsumerCanReadPublishedBundleAnd304ButCannotReadDrafts() throws Exception {
        when(i18nService.bundle(any(), any(), any(), any(), any())).thenReturn(
                new top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult("orders", "en-US", "zh-CN", "hash", Map.of("title", "Orders")));
        for (String permission : List.of("TIANSHU_I18N_READ", "TIANSHU_READ")) {
            mockMvc.perform(get("/api/v1/tianshu/i18n/bundle").param("bizCode", "retail").param("namespaceCode", "main")
                            .param("env", "prod").param("appCode", "shop").param("pageCode", "orders").param("locale", "en-US")
                        .param("catalogScopeId", "501").param("catalogActivationVersion", "9")
                            .header("X-Egon-Tianshu-Tenant", "tenant-a").with(authentication(rbac3(permission))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages.title").value("Orders"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("ETag", "W/\"hash\""))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-cache"));
        }
        mockMvc.perform(get("/api/v1/tianshu/i18n/bundle").param("bizCode", "retail").param("namespaceCode", "main")
                        .param("env", "prod").param("appCode", "shop").param("pageCode", "orders").param("locale", "en-US")
                        .param("catalogScopeId", "501").param("catalogActivationVersion", "9")
                        .header("X-Egon-Tianshu-Tenant", "tenant-a").header("If-None-Match", "\"old\", W/\"hash\"")
                        .with(authentication(rbac3("TIANSHU_I18N_READ"))))
                .andExpect(status().isNotModified()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        mockMvc.perform(get("/api/v1/tianshu/i18n/pages/1").param("catalogActivationVersion", "9").header("X-Egon-Tianshu-Tenant", "tenant-a")
                        .with(authentication(rbac3("TIANSHU_I18N_READ"))))
                .andExpect(status().isForbidden());
        verify(i18nService, org.mockito.Mockito.times(3)).bundle(any(), any(), any(), any(), any());
    }

    @Test
    void i18nErrorsRemainStructuredAndNeverCache() throws Exception {
        when(i18nService.get(1L, "9")).thenThrow(new top.egon.cola.component.common.core.exception.CommonException(
                56402, "TIANSHU_I18N_PAGE_NOT_FOUND", "Page not found"));
        mockMvc.perform(get("/api/v1/tianshu/i18n/pages/1").param("catalogActivationVersion", "9").header("X-Egon-Tianshu-Tenant", "tenant-a")
                        .with(authentication(rbac3("TIANSHU_READ"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(56402))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        org.mockito.Mockito.doThrow(new org.springframework.dao.TransientDataAccessResourceException("database unavailable"))
                .when(i18nService).get(1L, "9");
        mockMvc.perform(get("/api/v1/tianshu/i18n/pages/1").param("catalogActivationVersion", "9").header("X-Egon-Tianshu-Tenant", "tenant-a")
                        .with(authentication(rbac3("TIANSHU_READ"))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(56410));
    }

    @Test
    void i18nPublishNeedsPublishAuthorityAndStrictJsonRejectsDuplicateOrNonTextMessages() throws Exception {
        mockMvc.perform(post("/api/v1/tianshu/i18n/pages/1/publish").header("X-Egon-Tianshu-Tenant", "tenant-a")
                        .with(authentication(rbac3("TIANSHU_WRITE"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":\"0\"}"))
                .andExpect(status().isForbidden());
        for (String translations : List.of("{\"en-US\":{\"title\":1}}", "{\"en-US\":{\"title\":\"one\",\"title\":\"two\"}}")) {
            mockMvc.perform(post("/api/v1/tianshu/i18n/pages").header("X-Egon-Tianshu-Tenant", "tenant-a")
                            .with(authentication(rbac3("TIANSHU_WRITE"))).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bizCode\":\"retail\",\"namespaceCode\":\"main\",\"env\":\"prod\",\"appCode\":\"shop\","
                                    + "\"pageCode\":\"orders\",\"name\":\"Orders\",\"defaultLocale\":\"en-US\",\"catalogScopeId\":\"501\","
                                    + "\"catalogActivationVersion\":\"9\",\"reviewedSourceHashes\":{},\"translations\":" + translations + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(i18nService);
    }

    @Test
    void resourceMetadataIndexRequiresReadAndForwardsTheExactVersionWithoutAnyWriteRoute() throws Exception {
        when(resourceMetadataService.search(any(), any(), any(), any(), any())).thenReturn(
                top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult.<top.egon.cola.component.tianshu.admin.model.vo.DdcResourceMetadataResult>builder()
                        .records(List.of()).pageNo(1).pageSize(20).total(0L).build());
        mockMvc.perform(get("/api/v1/tianshu/resource-metadata").param("bizCode", "retail").param("namespaceCode", "main")
                        .param("env", "prod").param("appCode", "shop").param("catalogScopeId", "501").param("catalogActivationVersion", "9")
                        .param("resourceType", "API").param("permissionCode", "order:read")
                        .header("X-Egon-Tianshu-Tenant", "tenant-a").with(authentication(rbac3("TIANSHU_READ"))))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        verify(resourceMetadataService).search(any(), eq("501"), eq("9"), argThat(filter -> "API".equals(filter.resourceType()) && "order:read".equals(filter.permissionCode())), any());
        mockMvc.perform(post("/api/v1/tianshu/resource-metadata").header("X-Egon-Tianshu-Tenant", "tenant-a")
                        .with(authentication(rbac3("*")))).andExpect(status().isForbidden());
    }

    @Test
    void publicationReturnsAnOperationReceiptAndMissingOperationOrNumericVersionIsRejected() throws Exception {
        String operation = "2dbecf98-a5e4-41a6-b805-9c2c5387be7c";
        when(i18nService.publish(eq(1L), any())).thenReturn(new top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPublishResult(
                "1", operation, "2", "4", "9", java.time.Instant.parse("2026-10-25T00:00:00Z"), Map.of(), Map.of()));
        mockMvc.perform(post("/api/v1/tianshu/i18n/pages/1/publish").header("X-Egon-Tianshu-Tenant", "tenant-a")
                        .with(authentication(rbac3("TIANSHU_PUBLISH"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":\"4\",\"operationId\":\"" + operation + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.operationId").value(operation)).andExpect(jsonPath("$.data.publicationRevision").value("2"));
        for (String invalid : List.of("{\"expectedRevision\":\"4\"}", "{\"expectedRevision\":4,\"operationId\":\"" + operation + "\"}",
                "{\"expectedRevision\":\"4\",\"operationId\":\"" + operation + "\"}{}")) {
            mockMvc.perform(post("/api/v1/tianshu/i18n/pages/1/publish").header("X-Egon-Tianshu-Tenant", "tenant-a")
                            .with(authentication(rbac3("TIANSHU_PUBLISH"))).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verify(i18nService, org.mockito.Mockito.times(1)).publish(eq(1L), any());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private DdcConfigService configService;

    @MockitoBean
    private DdcPublishService publishService;

    @MockitoBean
    private DdcPublishTaskQueryService publishTaskQueryService;

    @MockitoBean
    private DdcPublishTaskResultConverter publishTaskResultConverter;

    @MockitoBean
    private DdcPublishTaskRepository publishTaskRepository;

    @MockitoBean
    private DdcCacheService cacheService;

    @MockitoBean(name = "ddcAiResourceService")
    private DdcAiResourceService aiResourceService;

    @MockitoBean(name = "ddcAiOptimizationService")
    private DdcAiOptimizationService aiOptimizationService;

    @MockitoBean(name = "ddcAiFeedbackService")
    private DdcAiFeedbackService aiFeedbackService;

    @MockitoBean
    private IdpBearerAuthenticationFilter idpBearerAuthenticationFilter;

    @MockitoBean
    private Rbac3BearerAuthenticationFilter rbac3BearerAuthenticationFilter;

    @BeforeEach
    void passThroughAuthenticationFilters() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(idpBearerAuthenticationFilter).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rbac3BearerAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void permitsOnlyDeclaredAnonymousEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tianshu/configs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("TIANSHU_ADMIN_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void protectsRegistryAdminReadsWithReadCapability() throws Exception {
        mockMvc.perform(get("/api/v1/tianshu/registry/services"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tianshu/registry/services")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void protectsNamespaceBindingsWithReadAndWriteCapabilities()
            throws Exception {
        mockMvc.perform(get("/api/v1/tianshu/namespace-env-app-bindings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tianshu/namespace-env-app-bindings")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tianshu/namespace-env-app-bindings")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tianshu/namespace-env-app-bindings")
                        .with(authority("CAP_TIANSHU_WRITE")))
                .andExpect(status().isOk());
    }

    @Test
    void protectsPagedManagementReadsWithReadCapability()
            throws Exception {
        when(configService.page(any(), any())).thenReturn(DdcPageResult.<DdcConfigVO>builder()
                .records(List.of()).total(0).pageNo(1).pageSize(10).build());
        when(publishTaskQueryService.page(any(), any())).thenReturn(DdcPageResult.<top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO>builder()
                .records(List.of()).total(0).pageNo(1).pageSize(10).build());

        mockMvc.perform(get("/api/v1/tianshu/configs/page")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tianshu/publish-tasks/page")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tianshu/apps/page")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tianshu/apps/page")
                        .with(authority("CAP_TIANSHU_WRITE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDdcAdminPageAfterWebExtraction() throws Exception {
        mockMvc.perform(get("/tianshu-admin/index.html"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void machineOpenApiIsNotMapped() {
        assertThat(handlerMapping.getHandlerMethods().keySet())
                .flatExtracting(mapping -> mapping.getPatternValues())
                .noneMatch(path -> path.startsWith(
                        "/api/v1/tianshu/" + "openapi"
                ));
    }

    @Test
    void enforcesExactReadWritePermissionsAndRejectsWildcardCapabilities() throws Exception {
        when(configService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tianshu/configs")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tianshu/configs")
                        .with(authority("CAP_TIANSHU_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tianshu/configs")
                        .with(authority("CAP_TIANSHU_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tianshu/configs")
                        .with(authority("CAP_*"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenAuthorizationClaimsDoNotGrantDdcPermission() throws Exception {
        mockMvc.perform(get("/api/v1/tianshu/configs")
                        .with(jwt().jwt(token -> token.subject("limited-user")
                                .claim("roles", List.of("ADMIN"))
                                .claim("capabilities", List.of("TIANSHU_READ")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rbac3SnapshotGrantsDdcPermission() throws Exception {
        when(configService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tianshu/configs")
                        .with(authentication(rbac3("TIANSHU_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void rbac3PrincipalUsesTheStableIdentitySubjectAsOperator() throws Exception {
        mockMvc.perform(post("/api/v1/tianshu/configs")
                        .with(authentication(rbac3("TIANSHU_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configBody()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> operator = ArgumentCaptor.forClass(String.class);
        verify(configService).create(any(DdcConfigCreateRequest.class), operator.capture());
        assertThat(operator.getValue()).isEqualTo("user:admin-sub [requested=system]");
    }

    @Test
    void reservesPublishAndCacheOperationsForExactCapabilities()
            throws Exception {
        mockMvc.perform(post("/api/v1/tianshu/configs/config-1/publish")
                        .with(authority("CAP_TIANSHU_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                        "/api/v1/tianshu/publish-tasks/change-1/retry"
                ).with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tianshu/cache/check")
                        .param("appCode", "app-a")
                        .param("env", "dev")
                        .param("bizCode", "biz-a")
                        .with(authority("CAP_TIANSHU_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tianshu/cache/check")
                        .param("appCode", "app-a")
                        .param("env", "dev")
                        .param("bizCode", "biz-a")
                        .with(authority("CAP_TIANSHU_CACHE")))
                .andExpect(status().isOk());
    }

    @Test
    void deniesAuthenticatedRequestsOutsideDeclaredRoutes()
            throws Exception {
        mockMvc.perform(get("/api/v1/tianshu/unknown")
                        .with(authority("CAP_*")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aiRoutesRequireAuthenticationButLeaveNamespacePermissionToTheService() throws Exception {
        when(aiResourceService.get(any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/tianshu/ai/resources/810001"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANSHU_ADMIN_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("TIANSHU_ADMIN_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/tianshu/ai/resources/810001")
                        .with(authentication(rbac3("TIANSHU_AI_READ:namespace-1"))))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANSHU_ADMIN_TENANT_MISMATCH"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/tianshu/ai/resources/810001")
                        .header("X-Egon-Tianshu-Tenant", "tenant-a")
                        .with(authentication(rbac3("TIANSHU_AI_READ:namespace-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(aiResourceService).get(new DdcAiIdQuery(810001L));
    }

    @Test
    void usesJwtSubjectAsTrustedOperator() throws Exception {
        mockMvc.perform(post("/api/v1/tianshu/configs")
                        .param("operator", "claimed-user")
                        .with(authentication(rbac3("TIANSHU_WRITE", "admin-42")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configBody()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> operator =
                ArgumentCaptor.forClass(String.class);
        verify(configService).create(
                any(DdcConfigCreateRequest.class),
                operator.capture()
        );
        assertThat(operator.getValue()).isEqualTo(
                "user:admin-42 [requested=claimed-user]"
        );
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor
    authority(String value) {
        String permission = value.startsWith("CAP_") ? value.substring(4) : value;
        return authentication(rbac3(permission, "test-user"));
    }

    private Rbac3AuthenticationToken rbac3(java.util.Set<String> permissions) {
        var principal = unscoped("TIANSHU_READ", "admin-sub").getPrincipal();
        var previous = principal.snapshot();
        var snapshot = new SystemAuthorizationSnapshot(previous.tenantId(), previous.identitySub(), previous.rbac3UserId(),
                previous.systemCode(), previous.authVersion(), previous.policyVersion(), List.of("tianshu-reader"), permissions,
                Map.of(), Map.of(), "sha256:ddc", previous.generatedAt(), previous.expiresAt());
        return new Rbac3AuthenticationToken(new Rbac3UserDetails(principal.identity(), snapshot),
                permissions.stream().map(value -> new SimpleGrantedAuthority("SCOPE_" + value)).toList());
    }

    private Rbac3AuthenticationToken rbac3(String permission) {
        return rbac3(permission, "admin-sub");
    }

    private Rbac3AuthenticationToken rbac3(String permission, String subject) {
        var token = unscoped(permission, subject);
        String scope = permission.startsWith("TIANSHU_AI_READ") ? "TIANSHU_AI_READ"
                : permission.startsWith("TIANSHU_AI_WRITE") ? "TIANSHU_AI_WRITE" : permission;
        List<SimpleGrantedAuthority> scopes = "*".equals(permission)
                ? List.of("TIANSHU_READ", "TIANSHU_WRITE", "TIANSHU_PUBLISH", "TIANSHU_CACHE", "TIANSHU_AI_READ", "TIANSHU_AI_WRITE")
                        .stream().map(value -> new SimpleGrantedAuthority("SCOPE_" + value)).toList()
                : List.of(new SimpleGrantedAuthority("SCOPE_" + scope));
        return new Rbac3AuthenticationToken(token.getPrincipal(), scopes);
    }

    private Rbac3AuthenticationToken unscoped(String permission, String subject) {
        Instant now = Instant.parse("2026-08-02T04:00:00Z");
        IdentityPrincipal identity = new IdentityPrincipal(
                subject, "tenant-a", "token-1",
                java.util.Set.of("tianshu-admin-web"), now, now.plusSeconds(900),
                AuthenticationContext.password());
        SystemAuthorizationSnapshot snapshot = new SystemAuthorizationSnapshot(
                "tenant-a", subject, "101", "tianshu-admin",
                3, 4, List.of("tianshu-reader"), java.util.Set.of(permission),
                Map.of(), Map.of(), "sha256:ddc", now, now.plusSeconds(900));
        return new Rbac3AuthenticationToken(new Rbac3UserDetails(identity, snapshot));
    }

    private String configBody() {
        return """
                {
                  "bizCode":"biz-a",
                  "appCode":"app-a",
                  "env":"dev",
                  "resourceName":"application.yml",
                  "content":"feature:\\n  enabled: true\\n",
                  "format":"YAML"
                }
                """;
    }

    @RestController
    static class HealthInfoController {

        @GetMapping({
                "/actuator/health",
                "/actuator/health/readiness",
                "/actuator/info"
        })
        Map<String, String> status() {
            return Map.of("status", "UP");
        }
    }

    @RestController
    @RequestMapping("/api/v1/tianshu/registry")
    static class RegistryInfoController {

        @GetMapping("/services")
        Map<String, Object> services() {
            return Map.of("services", List.of());
        }
    }

    @RestController
    @RequestMapping("/api/v1/tianshu/namespace-env-app-bindings")
    static class BindingInfoController {

        @GetMapping
        Map<String, Object> bindings() {
            return Map.of("bindings", List.of());
        }

        @PostMapping
        Map<String, String> createBinding() {
            return Map.of("status", "created");
        }
    }

    @RestController
    static class PagedInfoController {

        @GetMapping({
                "/api/v1/tianshu/apps/page",
                "/api/v1/tianshu/bizs/page",
                "/api/v1/tianshu/envs/page",
                "/api/v1/tianshu/namespaces/page",
                "/api/v1/tianshu/namespace-env-app-bindings/page",
                "/api/v1/tianshu/instances/page"
        })
        Map<String, Object> page() {
            return Map.of("records", List.of());
        }
    }
}
