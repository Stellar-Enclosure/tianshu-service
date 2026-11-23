package top.egon.cola.component.tianshu.admin.web;

import jakarta.servlet.FilterChain;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcAiOpenApiConfiguration;
import top.egon.cola.component.tianshu.admin.controller.audit.DdcOperationLogController;
import top.egon.cola.component.tianshu.admin.model.query.DdcOperationLogPageQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogSummaryVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogDetailVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogPageResult;
import top.egon.cola.component.tianshu.admin.service.audit.DdcOperationLogQueryService;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;
import top.egon.cola.component.common.core.enums.ResultCode;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPhaseEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import org.slf4j.MDC;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.egon.cola.component.tianshu.admin.config.DdcTransportSliceTestConfiguration;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminSecurityConfiguration;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DdcOperationLogController.class)
@Import({DdcAdminSecurityConfiguration.class, DdcTenantRegistry.class, DdcTenantContextExecutor.class,
        DdcTransportSliceTestConfiguration.class, DdcOperationLogWebContractTest.JacksonConfiguration.class,
        DdcAiOpenApiConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class,
        MultipleOpenApiSupportConfiguration.class})
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "egon.cola.platform.tianquan.shoubing.usage-observation.enabled=false",
        "egon.cola.component.tianshu.admin.tenancy.bindings[0].tenant-id=101",
        "egon.cola.component.tianshu.admin.tenancy.bindings[0].identity-tenant-id=tenant-a",
        "egon.cola.component.tianshu.admin.tenancy.bindings[0].enabled=true",
        "egon.cola.component.tianshu.admin.tenancy.bindings[1].tenant-id=202",
        "egon.cola.component.tianshu.admin.tenancy.bindings[1].identity-tenant-id=tenant-b",
        "egon.cola.component.tianshu.admin.tenancy.bindings[1].enabled=true"
})
class DdcOperationLogWebContractTest {
    private static final String BASE = "/api/v1/tianshu/operation-logs";
    private static final String TENANT = "X-Egon-Tianshu-Tenant";
    private static final String ID = "118230000100000001";
    private static final String OCCURRED = "2026-11-23T00:05:03.001001Z";
    private static final Set<String> READ = Set.of("TIANSHU_OPERATION_LOG_READ");
    private static final Set<String> DETAIL = Set.of("TIANSHU_OPERATION_LOG_READ", "TIANSHU_OPERATION_LOG_DETAIL");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext webContext;
    @MockitoBean(name = "ddcOperationLogQueryService") DdcOperationLogQueryService queries;
    @MockitoBean IdpBearerAuthenticationFilter idp;
    @MockitoBean Rbac3BearerAuthenticationFilter rbac;
    @MockitoBean(name = "idpObservedVerificationComponent")
    top.egon.cola.platform.tianquan.shoubing.starter.security.IdpObservedVerificationComponent observations;

    @BeforeEach void passVerifiedAuthentication() throws Exception {
        doAnswer(invocation -> {
            invocation.<FilterChain>getArgument(2).doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(idp).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            invocation.<FilterChain>getArgument(2).doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rbac).doFilter(any(), any(), any());
    }

    @AfterEach void releaseContext() { MDC.clear(); }

    @Test void independentAuditReadPermissionReachesTheActualSecurityRoute() throws Exception {
        when(queries.page(any())).thenReturn(pageResult());
        mvc.perform(get("/api/v1/tianshu/operation-logs/page")
                        .with(authentication(reader(Set.of("TIANSHU_OPERATION_LOG_READ"))))
                        .header("X-Egon-Tianshu-Tenant", "tenant-a"))
                .andExpect(status().isOk());
    }

    @Test void summaryWireRetainsMicrosecondsAndExcludesAllDetailFields() throws Exception {
        MDC.put("tenantId", "outer"); MDC.put("userId", "outer-user");
        when(queries.page(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("tenantId")).isEqualTo("101");
            assertThat(MDC.get("userId")).isEqualTo("user:audit-user");
            return pageResult();
        });
        var response = mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(reader(READ))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.records[0].eventId").value(ID))
                .andExpect(jsonPath("$.data.records[0].occurredAt").value(OCCURRED))
                .andExpect(jsonPath("$.data.nextCursor.beforeId").value(ID))
                .andExpect(jsonPath("$.data.nextCursor.beforeTime").value(OCCURRED)).andReturn();
        var body = json.readTree(response.getResponse().getContentAsByteArray());
        assertThat(body.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("success", "code", "status", "message", "data", "traceId", "timestamp");
        assertThat(body.path("code").asInt()).isEqualTo(10000);
        assertThat(body.path("timestamp").isNumber()).isTrue();
        assertThat(body.path("data").properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("from", "to", "records", "nextCursor");
        assertThat(body.path("data").path("records").get(0).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("eventId", "operationId", "occurredAt", "sourceService", "moduleCode",
                        "actionCode", "resourceType", "resourceId", "actorType", "actorId", "actorName", "phase", "outcome", "routePattern");
        assertThat(MDC.get("tenantId")).isEqualTo("outer");
        assertThat(MDC.get("userId")).isEqualTo("outer-user");
    }

    @Test void detailRequiresBothPermissionsAndPreservesExactLocatorAndPayload() throws Exception {
        when(queries.detail(ID, OCCURRED)).thenReturn(DdcOperationLogDetailVO.builder().eventId(ID).operationId("change-102")
                .schemaVersion(1).occurredAt(Instant.parse(OCCURRED)).recordedAt(Instant.parse("2026-11-23T00:05:03.014002Z"))
                .requestUrl("https://tianshu.example.invalid/api/v1/tianshu/configs/01/publish")
                .handlerMethod("sample.ConfigService#publish(java.lang.String)").durationMs(25L)
                .details(json.createObjectNode().put("changeId", "102").put("targetRevision", "8")).build());
        mvc.perform(get(BASE + "/" + ID).param("occurredAt", OCCURRED).header(TENANT, "tenant-a")
                        .with(authentication(reader(DETAIL))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.eventId").value(ID)).andExpect(jsonPath("$.data.occurredAt").value(OCCURRED))
                .andExpect(jsonPath("$.data.durationMs").value(25)).andExpect(jsonPath("$.data.details.targetRevision").value("8"));
        verify(queries).detail(ID, OCCURRED);
    }

    @Test void ordinaryCapabilitiesWildcardAndIncompleteAuditPermissionsCannotRead() throws Exception {
        for (Set<String> permissions : List.of(Set.of("TIANSHU_READ"), Set.of("TIANSHU_WRITE"), Set.of("TIANSHU_PUBLISH"),
                Set.of("*"), Set.of("TIANSHU_OPERATION_LOG_DETAIL"))) {
            mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(reader(permissions))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANSHU_ADMIN_CAPABILITY_REQUIRED"));
        }
        for (Set<String> permissions : List.of(READ, Set.of("TIANSHU_OPERATION_LOG_DETAIL"))) {
            mvc.perform(get(BASE + "/" + ID).param("occurredAt", OCCURRED).header(TENANT, "tenant-a")
                            .with(authentication(reader(permissions))))
                    .andExpect(status().isForbidden());
        }
        var noScope = reader(DETAIL);
        noScope = new Rbac3AuthenticationToken(noScope.getPrincipal(), List.of());
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(noScope)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries);
    }

    @Test void missingAuthenticationAndNonGetMethodsRetainStableSecurityShapes() throws Exception {
        mvc.perform(get(BASE + "/page")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"code\":\"TIANSHU_ADMIN_AUTHENTICATION_REQUIRED\",\"message\":\"TIANSHU_ADMIN_AUTHENTICATION_REQUIRED\"}"));
        for (HttpMethod method : List.of(HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)) {
            mvc.perform(request(method, BASE + "/page").header(TENANT, "tenant-a").with(authentication(reader(DETAIL))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(BASE + "/not/a/route").header(TENANT, "tenant-a").with(authentication(reader(DETAIL))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries);
    }

    @Test void expectedTenantHeaderCannotChooseOrOmitTheAuthenticatedTenant() throws Exception {
        mvc.perform(get(BASE + "/page").with(authentication(reader(READ))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANSHU_ADMIN_TENANT_MISMATCH"));
        for (String header : List.of("", "tenant-b", "101", "%0Dtenant-a", "%ZZ")) {
            mvc.perform(get(BASE + "/page").header(TENANT, header).with(authentication(reader(READ))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANSHU_ADMIN_TENANT_MISMATCH"));
        }
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a", "tenant-a").with(authentication(reader(READ))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANSHU_ADMIN_TENANT_MISMATCH"));
        verifyNoInteractions(queries);
    }

    @Test void anotherAuthenticatedTenantUsesItsOwnRegistryMappingAndNoHeaderAuthority() throws Exception {
        when(queries.page(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("tenantId")).isEqualTo("202");
            return pageResult();
        });
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-b").with(authentication(reader(READ, "tenant-b"))))
                .andExpect(status().isOk());
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test void unknownDuplicateBodyAndMalformedBindingAreBadRequestsBeforeQuery() throws Exception {
        for (String parameter : List.of("tenantId", "pageNo", "pageSize", "offset", "unknown", "occurredAt")) {
            mvc.perform(get(BASE + "/page").param(parameter, "1").header(TENANT, "tenant-a")
                            .with(authentication(reader(READ))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(56000))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        for (String limit : List.of("", "0", "101", "abc")) {
            mvc.perform(get(BASE + "/page").param("limit", limit).header(TENANT, "tenant-a").with(authentication(reader(READ))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(BASE + "/page").param("limit", "1", "2").header(TENANT, "tenant-a").with(authentication(reader(READ))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/page").param("actorId", "user\nsecret").header(TENANT, "tenant-a")
                        .with(authentication(reader(READ)))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/page").content("{}").header(TENANT, "tenant-a").with(authentication(reader(READ))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/" + ID).param("occurredAt", OCCURRED, OCCURRED).header(TENANT, "tenant-a")
                        .with(authentication(reader(DETAIL)))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/" + ID).header(TENANT, "tenant-a").with(authentication(reader(DETAIL))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/" + ID).param("occurredAt", " ").header(TENANT, "tenant-a")
                        .with(authentication(reader(DETAIL)))).andExpect(status().isBadRequest());
        for (String bad : List.of("0", "+1", "-1", "12345678901234567890")) {
            mvc.perform(get(BASE + "/" + bad).param("occurredAt", OCCURRED).header(TENANT, "tenant-a")
                            .with(authentication(reader(DETAIL)))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(queries);
    }

    @Test void queryBindingRetainsExactTimeCursorAndAllDeclaredFilters() throws Exception {
        when(queries.page(any())).thenReturn(pageResult());
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(reader(READ)))
                        .param("from", "2026-11-23T00:00:00+08:00").param("to", "2026-11-24T00:00:00+08:00")
                        .param("beforeTime", OCCURRED).param("beforeId", "9223372036854775807").param("limit", "100")
                        .param("actionCode", "TIANSHU_CONFIG_PUBLISH").param("actorId", "user-352")
                        .param("resourceType", "CONFIG").param("resourceId", "config-01").param("bizCode", "infra")
                        .param("namespaceCode", "public").param("env", "prod").param("appCode", "console").param("outcome", "ACCEPTED"))
                .andExpect(status().isOk());
        var captured = org.mockito.ArgumentCaptor.forClass(DdcOperationLogPageQuery.class);
        verify(queries).page(captured.capture());
        var query = captured.getValue();
        assertThat(query.getFrom()).isEqualTo("2026-11-23T00:00:00+08:00");
        assertThat(query.getBeforeTime()).isEqualTo(OCCURRED);
        assertThat(query.getBeforeId()).isEqualTo("9223372036854775807");
        assertThat(query.getLimit()).isEqualTo(100);
        assertThat(query.getActionCode()).isEqualTo("TIANSHU_CONFIG_PUBLISH");
        assertThat(query.getActorId()).isEqualTo("user-352");
        assertThat(query.getNamespaceCode()).isEqualTo("public");
    }

    @Test void applicationErrorsHaveApprovedHttpCodesSevenFieldsAndSafeMessages() throws Exception {
        for (var error : List.of(new CommonException(DdcErrorStatus.INVALID_REQUEST), new CommonException(ResultCode.NOT_FOUND),
                new CommonException(DdcErrorStatus.OPERATION_LOG_UNAVAILABLE), new CommonException(123, "PRIVATE", "jdbc://private/token"))) {
            doThrow(error).when(queries).detail(ID, OCCURRED);
            int status = error.getCode() == 56000 ? 400 : error.getCode() == 404000 ? 404 : error.getCode() == 56998 ? 503 : 500;
            var response = mvc.perform(get(BASE + "/" + ID).param("occurredAt", OCCURRED).header(TENANT, "tenant-a")
                            .with(authentication(reader(DETAIL))))
                    .andExpect(status().is(status)).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.success").value(false)).andReturn();
            var body = json.readTree(response.getResponse().getContentAsByteArray());
            assertThat(body.properties()).extracting(Map.Entry::getKey)
                    .containsExactlyInAnyOrder("success", "code", "status", "message", "data", "traceId", "timestamp");
            assertThat(body.path("data").isNull()).isTrue();
            assertThat(body.toString()).doesNotContain("jdbc://private", "token");
            assertThat(body.path("code").asInt()).isEqualTo(error.getCode() == 123 ? 56999 : error.getCode());
        }
        when(queries.page(any())).thenThrow(new IllegalStateException("private-target-password"));
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(reader(READ))))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(56999))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-target-password"))));
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test void methodPermissionFailuresKeepSecurityResponsesAndStrictAuditFailureIsUnavailable() throws Exception {
        doThrow(new org.springframework.security.authorization.AuthorizationDeniedException("private-policy"))
                .when(queries).page(any());
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(reader(READ))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANSHU_ADMIN_CAPABILITY_REQUIRED"))
                .andExpect(jsonPath("$.message").value("TIANSHU_ADMIN_CAPABILITY_REQUIRED"));
        doThrow(new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("private-proof"))
                .when(queries).page(any());
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(reader(READ))))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        when(observations.enabled()).thenReturn(true);
        var denied = reader(Set.of("TIANSHU_READ"));
        denied.setDetails(new top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO(
                java.net.URI.create("https://idp.example/tenants/tenant-a"), java.net.URI.create("https://tianshu.example/"),
                "a".repeat(64), "b".repeat(64), "c".repeat(64)));
        mvc.perform(get(BASE + "/page").header(TENANT, "tenant-a").with(authentication(denied)))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"code\":\"OAUTH_AUDIT_DELIVERY_UNAVAILABLE\",\"message\":\"OAUTH_AUDIT_DELIVERY_UNAVAILABLE\"}"));
        verify(observations).authorizationDenied(any(), any());
    }

    @Test void actualMappingsCarryPrecisePermissionAndGatewayCatalogContracts() {
        var methods = java.util.Arrays.stream(DdcOperationLogController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PermissionResource.class)).toList();
        assertThat(methods).hasSize(2);
        for (var method : methods) {
            boolean page = "page".equals(method.getName());
            var resource = method.getAnnotation(PermissionResource.class);
            assertThat(resource.key()).isEqualTo("tianshu.api.operationLogs." + method.getName());
            assertThat(resource.labelPage()).isEqualTo("tianshu.operationLogs");
            assertThat(resource.labelKey()).isEqualTo("api." + method.getName());
            assertThat(method.getAnnotation(Operation.class).operationId()).isEqualTo("tianshu.ddcOperationLogController." + method.getName());
            assertThat(method.getAnnotation(EgonGatewayPolicy.class).exposure()).isEqualTo(EgonGatewayPolicy.Exposure.EXTERNAL);
            assertThat(method.getAnnotation(RequiresPermissions.class).codes()).containsExactly(page
                    ? new String[]{DdcPermissionCodes.OPERATION_LOG_READ}
                    : new String[]{DdcPermissionCodes.OPERATION_LOG_READ, DdcPermissionCodes.OPERATION_LOG_DETAIL});
        }
    }

    @Test void generatedOpenApiMatchesActualRoutesParametersAndResponseSchemas() throws Exception {
        // Introspect documentation without filters; endpoint authorization is exercised above with the actual chain.
        var documentation = MockMvcBuilders.webAppContextSetup(webContext).build();
        byte[] bytes = documentation.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "operation-log-openapi.json"), bytes);
        var api = json.readTree(bytes);
        assertThat(api.path("openapi").asText()).startsWith("3.1");
        assertThat(api.path("components").path("securitySchemes").path("tianshuBearerAuth").path("scheme").asText()).isEqualTo("bearer");
        var page = api.path("paths").path(BASE + "/page").path("get");
        var detail = api.path("paths").path(BASE + "/{eventId}").path("get");
        for (var entry : Map.of("page", page, "detail", detail).entrySet()) {
            var operation = entry.getValue();
            assertThat(operation.path("operationId").asText()).isEqualTo("tianshu.ddcOperationLogController." + entry.getKey());
            assertThat(operation.path("security").toString()).contains("tianshuBearerAuth");
            assertThat(operation.path("responses").properties()).extracting(Map.Entry::getKey)
                    .contains("200", "400", "401", "403", "500", "503");
            for (var response : operation.path("responses")) {
                assertThat(response.path("headers").path("Cache-Control").path("schema").path("example").asText()).isEqualTo("no-store");
            }
            assertThat(operation.path("parameters").toString()).contains("X-Egon-Tianshu-Tenant");
            assertThat(operation.path("parameters").findValuesAsText("name")).doesNotContain("tenantId", "Authorization", "paired");
            assertThat(operation.has("requestBody")).isFalse();
        }
        assertThat(page.path("responses").has("404")).isFalse();
        assertThat(detail.path("responses").has("404")).isTrue();
        assertThat(page.path("parameters").findValuesAsText("name")).contains("from", "to", "limit", "beforeTime", "beforeId",
                "actionCode", "actorId", "resourceType", "resourceId", "bizCode", "namespaceCode", "env", "appCode", "outcome");
        var schemas = api.path("components").path("schemas");
        assertThat(schemas.path("DdcOperationLogSummaryVO").path("properties").has("requestUrl")).isFalse();
        assertThat(schemas.path("DdcOperationLogSummaryVO").path("properties").has("details")).isFalse();
        assertThat(schemas.path("DdcOperationLogSummaryVO").path("properties").path("eventId").path("type").asText()).isEqualTo("string");
        assertThat(schemas.path("DdcOperationLogDetailVO").path("properties").has("durationMs")).isTrue();
        assertThat(schemas.path("DdcOperationLogDetailVO").path("properties").has("details")).isTrue();
        var cursor = schemas.path("DdcOperationLogPageResult").path("properties").path("nextCursor");
        assertThat(cursor.path("nullable").asBoolean() || cursor.path("type").toString().contains("null")).isTrue();
        assertThat(page.path("responses").path("401").path("content").path("application/json").path("schema").toString())
                .contains("DdcAdminSecurityErrorResult");
        assertThat(page.path("responses").path("503").path("content").path("application/json").path("schema").path("oneOf").toString())
                .contains("ResultRecord", "DdcAdminSecurityErrorResult");
    }

    private DdcOperationLogPageResult pageResult() {
        return DdcOperationLogPageResult.builder().from(Instant.parse("2026-11-23T00:00:00Z"))
                .to(Instant.parse("2026-11-24T00:00:00Z"))
                .records(List.of(DdcOperationLogSummaryVO.builder().eventId(ID).operationId("change-102")
                        .occurredAt(Instant.parse(OCCURRED)).sourceService("tianshu-admin").moduleCode("CONFIG")
                        .actionCode("TIANSHU_CONFIG_PUBLISH").resourceType("CONFIG").resourceId("config-01")
                        .actorType("USER").actorId("user-352").phase(OperationLogPhaseEnum.COMMAND)
                        .outcome(OperationLogOutcomeEnum.ACCEPTED).routePattern("/configs/{id}/publish").build()))
                .nextCursor(new DdcOperationLogPageResult.CursorVO(Instant.parse(OCCURRED), ID)).build();
    }

    private static Rbac3AuthenticationToken reader(Set<String> permissions) { return reader(permissions, "tenant-a"); }
    private static Rbac3AuthenticationToken reader(Set<String> permissions, String tenant) {
        Instant now = Instant.now();
        var identity = new IdentityPrincipal("audit-user", tenant, "token-a", Set.of("tianshu-admin-web"),
                now, now.plusSeconds(900), AuthenticationContext.password());
        var snapshot = new SystemAuthorizationSnapshot(tenant, "audit-user", "101", "tianshu-admin", 3, 4,
                List.of("tianshu-auditor"), permissions, Map.of(), Map.of(), "sha256:audit", now, now.plusSeconds(900));
        return new Rbac3AuthenticationToken(new Rbac3UserDetails(identity, snapshot),
                List.of(new SimpleGrantedAuthority("SCOPE_TIANSHU_READ")));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class JacksonConfiguration {
        @org.springframework.context.annotation.Bean("idpSecurityObservationFilter")
        top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter observationsFilter(
                top.egon.cola.platform.tianquan.shoubing.starter.security.IdpObservedVerificationComponent observations) {
            return new top.egon.cola.platform.tianquan.shoubing.starter.security.IdpSecurityObservationFilter(observations,
                    new top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy());
        }

        @org.springframework.context.annotation.Bean
        static org.springframework.beans.factory.config.BeanFactoryPostProcessor jacksonCompatibilityAlias() {
            return top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterAutoConfiguration.idpJacksonObjectMapperAlias();
        }
    }
}
