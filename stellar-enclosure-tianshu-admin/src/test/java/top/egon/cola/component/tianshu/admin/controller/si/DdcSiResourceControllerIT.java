package top.egon.cola.component.tianshu.admin.controller.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.config.DdcTransportSliceTestConfiguration;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiBindingCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcSiLifecycleCommand;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiCqeConverterImpl;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DdcSiResourceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        DdcSiExceptionAdvice.class,
        DdcTransportSliceTestConfiguration.class,
        DdcSiResourceControllerIT.SliceConverterConfig.class
})
class DdcSiResourceControllerIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class SliceConverterConfig {
        @Bean("ddcSiCqeConverterImpl")
        top.egon.cola.component.tianshu.admin.model.convertor.DdcSiCqeConverter ddcSiCqeConverter() {
            return new DdcSiCqeConverterImpl();
        }
    }

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final long TENANT_ID = 1001L;
    private static final long RESOURCE_ID = 910001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean(name = "ddcSiResourceService")
    private DdcSiResourceService siResourceService;

    @BeforeEach
    void bindTenant() {
        MDC.put("tenantId", Long.toString(TENANT_ID));
        MDC.put("userId", "user:si-admin");
    }

    @AfterEach
    void clearTenant() {
        MDC.clear();
    }

    @Test
    void pageAndSearchAndMatrixExposeCatalogWithUtcTimestamps() throws Exception {
        DdcSiResourceBO resource = sampleResource(DdcSiLifecycleStatusEnum.PUBLISHED);
        when(siResourceService.page(eq(TENANT_ID), eq("ai"), eq("core"), eq("dev"),
                eq("tianji"), eq(DdcSiKindEnum.AGENT), eq(DdcSiLifecycleStatusEnum.PUBLISHED),
                eq("planner"), eq("core-tag"), any()))
                .thenReturn(DdcPageResult.<DdcSiResourceBO>builder()
                        .records(List.of(resource)).total(1L).pageNo(1).pageSize(20).build());

        mockMvc.perform(get("/api/v1/tianshu/si/resources")
                        .param("bizCode", "ai")
                        .param("namespaceCode", "core")
                        .param("envCode", "dev")
                        .param("appCode", "tianji")
                        .param("kind", "AGENT")
                        .param("lifecycleStatus", "PUBLISHED")
                        .param("keyword", "planner")
                        .param("tag", "core-tag")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.records[0].resourceKey").value("order-planner"))
                .andExpect(jsonPath("$.records[0].publishedTime").value("2026-11-16T10:00:00Z"));

        DdcSiVersionBO version = sampleVersion("dev", 3L);
        when(siResourceService.getEnvMatrix(TENANT_ID, "ai", "core", DdcSiKindEnum.AGENT, "order-planner"))
                .thenReturn(List.of(version));
        mockMvc.perform(get("/api/v1/tianshu/si/resources/env-matrix")
                        .param("bizCode", "ai")
                        .param("namespaceCode", "core")
                        .param("kind", "AGENT")
                        .param("resourceKey", "order-planner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].envCode").value("dev"))
                .andExpect(jsonPath("$.data[0].activeVersionNo").value(3));
    }

    @Test
    void lifecycleAndDraftAndBindingEndpointsEnforceValidationAndStatusCodes() throws Exception {
        DdcSiResourceBO created = sampleResource(DdcSiLifecycleStatusEnum.DRAFT);
        when(siResourceService.create(eq(TENANT_ID), eq("ai"), eq("core"), eq("dev"),
                eq("tianji"), eq(DdcSiKindEnum.AGENT), eq("order-planner"), eq("Order Planner"),
                eq("Plans orders"), any(), eq(DdcSiSourceTypeEnum.CONSOLE), any(), eq("user:alice")))
                .thenReturn(created);

        DdcSiCreateCommand createCommand = new DdcSiCreateCommand()
                .setBizCode("ai")
                .setNamespaceCode("core")
                .setEnvCode("dev")
                .setAppCode("tianji")
                .setKind(DdcSiKindEnum.AGENT)
                .setResourceKey("order-planner")
                .setDisplayName("Order Planner")
                .setDescription("Plans orders")
                .setTags(List.of("planner"))
                .setSourceType(DdcSiSourceTypeEnum.CONSOLE)
                .setSpecJson("{\"modelKey\":\"gpt-6-luna\",\"systemPromptKey\":\"sys\"}");

        mockMvc.perform(post("/api/v1/tianshu/si/resources")
                        .principal(aliceAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCommand)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/tianshu/si/resources/" + RESOURCE_ID))
                .andExpect(jsonPath("$.data.id").value(RESOURCE_ID));

        when(siResourceService.updateDraft(eq(TENANT_ID), eq(RESOURCE_ID), eq(1L),
                eq("Order Planner v2"), eq("Updated"), any(), any(), eq("user:alice")))
                .thenReturn(sampleResource(DdcSiLifecycleStatusEnum.DRAFT).setVersion(2L));
        DdcSiDraftCommand draftCommand = new DdcSiDraftCommand()
                .setExpectedVersion(1L)
                .setDisplayName("Order Planner v2")
                .setDescription("Updated")
                .setTags(List.of("planner"))
                .setSpecJson("{\"modelKey\":\"gpt-6-luna\"}");
        mockMvc.perform(put("/api/v1/tianshu/si/resources/{id}/draft", RESOURCE_ID)
                        .principal(aliceAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftCommand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        DdcSiVersionBO published = sampleVersion("dev", 1L);
        when(siResourceService.publish(TENANT_ID, RESOURCE_ID, 2L, "Initial release", "user:alice"))
                .thenReturn(published);
        mockMvc.perform(post("/api/v1/tianshu/si/resources/{id}/publications", RESOURCE_ID)
                        .principal(aliceAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DdcSiLifecycleCommand().setExpectedVersion(2L).setChangeNote("Initial release"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publishedTime").value("2026-11-16T10:00:00Z"));

        DdcSiVersionBO promoted = sampleVersion("prod", 1L);
        when(siResourceService.promote(TENANT_ID, RESOURCE_ID, "prod", "Promote to prod", "user:alice"))
                .thenReturn(promoted);
        mockMvc.perform(post("/api/v1/tianshu/si/resources/{id}/promotions", RESOURCE_ID)
                        .principal(aliceAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DdcSiLifecycleCommand().setTargetEnvCode("prod").setChangeNote("Promote to prod"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.envCode").value("prod"));

        when(siResourceService.rollback(TENANT_ID, RESOURCE_ID, 3L, 1L, "Rollback", "user:alice"))
                .thenReturn(published);
        mockMvc.perform(post("/api/v1/tianshu/si/resources/{id}/rollbacks", RESOURCE_ID)
                        .principal(aliceAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DdcSiLifecycleCommand().setExpectedVersion(3L).setTargetVersionNo(1L).setChangeNote("Rollback"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1));

        when(siResourceService.deprecate(TENANT_ID, RESOURCE_ID, 4L, "user:alice"))
                .thenReturn(sampleResource(DdcSiLifecycleStatusEnum.DEPRECATED));
        mockMvc.perform(post("/api/v1/tianshu/si/resources/{id}/lifecycle", RESOURCE_ID)
                        .principal(aliceAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DdcSiLifecycleCommand().setExpectedVersion(4L).setAction("DEPRECATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DEPRECATED"));

        when(siResourceService.bind(TENANT_ID, "ai", "core", "dev", "tianji", DdcSiKindEnum.AGENT,
                "order-planner", 1L, "user:alice"))
                .thenReturn(DdcSiBindingBO.builder()
                        .id(8001L).tenantId(TENANT_ID).bizCode("ai").namespaceId(2001L)
                        .envCode("dev").appCode("tianji").kind(DdcSiKindEnum.AGENT)
                        .resourceKey("order-planner").pinnedVersionNo(1L).boundTime(NOW).build());
        mockMvc.perform(put("/api/v1/tianshu/si/resources/{id}/service-binding", RESOURCE_ID)
                        .principal(aliceAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DdcSiBindingCommand()
                                .setBizCode("ai").setNamespaceCode("core").setEnvCode("dev")
                                .setAppCode("tianji").setKind(DdcSiKindEnum.AGENT)
                                .setResourceKey("order-planner").setPinnedVersionNo(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinnedVersionNo").value(1))
                .andExpect(jsonPath("$.data.boundTime").value("2026-11-16T10:00:00Z"));

        doNothing().when(siResourceService).archive(TENANT_ID, RESOURCE_ID, 5L, "user:alice");
        mockMvc.perform(delete("/api/v1/tianshu/si/resources/{id}", RESOURCE_ID)
                        .principal(aliceAuth())
                        .param("expectedVersion", "5"))
                .andExpect(status().isNoContent());
    }

    @Test
    void hybridSearchAndExceptionMappingReturnCleanSanitizedPayloads() throws Exception {
        when(siResourceService.hybridSearch(any())).thenReturn(List.of(
                DdcSiSearchHit.builder()
                        .resource(DdcSiResourceDescriptor.builder()
                                .tenantId(TENANT_ID).bizCode("ai").namespaceCode("core").envCode("dev")
                                .resourceId(RESOURCE_ID).kind(DdcSiKindEnum.AGENT)
                                .resourceKey("order-planner").displayName("Order Planner")
                                .status(DdcSiLifecycleStatusEnum.PUBLISHED).sourceType(DdcSiSourceTypeEnum.CONSOLE)
                                .publishedAt(NOW).updatedAt(NOW).build())
                        .hybridScore(0.92).vectorScore(0.88).ftsScore(0.75)
                        .degradedToFtsOnly(true).rank(1).build()
        ));

        mockMvc.perform(post("/api/v1/tianshu/si/resources/semantic-search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"envCode\":\"dev\",\"queryText\":\"order planner\",\"topK\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degradedToFtsOnly").value(true))
                .andExpect(jsonPath("$.data.hits[0].resource.resourceKey").value("order-planner"));

        when(siResourceService.get(TENANT_ID, RESOURCE_ID))
                .thenThrow(new CommonException(56422, "TIANSHU_SI_SPEC_INVALID",
                        "Atomic agent must not contain workflow nodes"));
        mockMvc.perform(get("/api/v1/tianshu/si/resources/{id}", RESOURCE_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(56422))
                .andExpect(jsonPath("$.status").value("TIANSHU_SI_SPEC_INVALID"));

        doThrow(new CommonException(56409, "TIANSHU_SI_ARCHIVE_BLOCKED",
                "Resource archived blocked by active inbound references"))
                .when(siResourceService).archive(TENANT_ID, RESOURCE_ID, 2L, "user:alice");
        mockMvc.perform(delete("/api/v1/tianshu/si/resources/{id}", RESOURCE_ID)
                        .principal(aliceAuth())
                        .param("expectedVersion", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(56409))
                .andExpect(jsonPath("$.status").value("TIANSHU_SI_ARCHIVE_BLOCKED"));

        mockMvc.perform(get("/api/v1/tianshu/si/resources")
                        .param("pageSize", "999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTenantContextIsRejectedAndAllEndpointsDeclareJianshenPermissions() {
        MDC.clear();
        List<Method> endpoints = Arrays.stream(DdcSiResourceController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PermissionResource.class))
                .toList();
        assertThat(endpoints).hasSizeGreaterThanOrEqualTo(14);
        assertThat(endpoints).allSatisfy(method -> {
            PermissionResource resource = method.getAnnotation(PermissionResource.class);
            RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
            assertThat(resource.key()).startsWith("tianshu.api.siResource.");
            assertThat(permissions).isNotNull();
            assertThat(permissions.codes()).allSatisfy(code ->
                    assertThat(code).startsWith("TIANSHU_SI_"));
        });
        assertThat(DdcPermissionCodes.SI_RESOURCE_READ).isEqualTo("TIANSHU_SI_READ");
        assertThat(DdcPermissionCodes.SI_RESOURCE_WRITE).isEqualTo("TIANSHU_SI_WRITE");
        assertThat(DdcPermissionCodes.SI_RESOURCE_PUBLISH).isEqualTo("TIANSHU_SI_PUBLISH");
        assertThat(DdcPermissionCodes.SI_RESOURCE_PROMOTE).isEqualTo("TIANSHU_SI_PROMOTE");
        assertThat(DdcPermissionCodes.SI_RESOURCE_ARCHIVE).isEqualTo("TIANSHU_SI_ARCHIVE");
        assertThat(DdcPermissionCodes.SI_BINDING_WRITE).isEqualTo("TIANSHU_SI_BINDING_WRITE");
        assertThat(DdcPermissionCodes.SI_INDEX_MANAGE).isEqualTo("TIANSHU_SI_INDEX_MANAGE");
    }

    private TestingAuthenticationToken aliceAuth() {
        return new TestingAuthenticationToken("alice", "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private DdcSiResourceBO sampleResource(DdcSiLifecycleStatusEnum status) {
        return DdcSiResourceBO.builder()
                .id(RESOURCE_ID)
                .tenantId(TENANT_ID)
                .bizCode("ai")
                .namespaceCode("core")
                .envCode("dev")
                .appCode("tianji")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("order-planner")
                .displayName("Order Planner")
                .description("Plans orders")
                .lifecycleStatus(status)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .tags(List.of("planner"))
                .specJson("{\"modelKey\":\"gpt-6-luna\"}")
                .currentVersionNo(1L)
                .version(1L)
                .publishedTime(NOW)
                .createTime(NOW)
                .updateTime(NOW)
                .build();
    }

    private DdcSiVersionBO sampleVersion(String envCode, long versionNo) {
        return DdcSiVersionBO.builder()
                .id(920001L)
                .tenantId(TENANT_ID)
                .resourceId(RESOURCE_ID)
                .envCode(envCode)
                .versionNo(versionNo)
                .digestSha256("a".repeat(64))
                .specJson("{\"modelKey\":\"gpt-6-luna\"}")
                .sourceEnvCode("dev")
                .sourceVersionNo(1L)
                .changeNote("Initial release")
                .createUserId("user:alice")
                .publishedTime(NOW)
                .build();
    }

    @Test
    void versionHistoryReferencesUnbindAndReindexEndpointsWorkWithUtcTimestamps() throws Exception {
        DdcSiVersionBO version = sampleVersion("dev", 2L);
        when(siResourceService.pageVersions(eq(TENANT_ID), eq(RESOURCE_ID), any()))
                .thenReturn(DdcPageResult.<DdcSiVersionBO>builder()
                        .records(List.of(version)).total(1L).pageNo(1).pageSize(20).build());
        mockMvc.perform(get("/api/v1/tianshu/si/resources/{id}/versions", RESOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].versionNo").value(2))
                .andExpect(jsonPath("$.records[0].publishedTime").value("2026-11-16T10:00:00Z"));

        when(siResourceService.getVersion(TENANT_ID, RESOURCE_ID, 2L)).thenReturn(version);
        mockMvc.perform(get("/api/v1/tianshu/si/resources/{id}/versions/{versionNo}", RESOURCE_ID, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));

        when(siResourceService.listReferences(TENANT_ID, RESOURCE_ID, "INBOUND"))
                .thenReturn(List.of(DdcSiReferenceBO.builder()
                        .id(501L).sourceType(DdcSiReferenceTargetTypeEnum.TIANJI_PROCESS).sourceKey("order-flow")
                        .targetType(DdcSiReferenceTargetTypeEnum.AGENT).targetKind(DdcSiKindEnum.AGENT)
                        .targetKey("order-planner").envCode("dev").required(true).build()));
        mockMvc.perform(get("/api/v1/tianshu/si/resources/{id}/versions/{versionNo}/references", RESOURCE_ID, 2L)
                        .param("direction", "INBOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceKey").value("order-flow"));

        doNothing().when(siResourceService).unbind(TENANT_ID, "ai", "core", "dev", "tianji",
                DdcSiKindEnum.AGENT, "order-planner", "user:alice");
        mockMvc.perform(delete("/api/v1/tianshu/si/resources/{id}/service-binding", RESOURCE_ID)
                        .principal(aliceAuth())
                        .param("bizCode", "ai").param("namespaceCode", "core").param("envCode", "dev")
                        .param("appCode", "tianji").param("kind", "AGENT").param("resourceKey", "order-planner"))
                .andExpect(status().isNoContent());

        doNothing().when(siResourceService).triggerReindex(TENANT_ID, RESOURCE_ID, 920001L);
        mockMvc.perform(post("/api/v1/tianshu/si/resources/{id}/reindex", RESOURCE_ID)
                        .param("versionId", "920001"))
                .andExpect(status().isAccepted());
        verify(siResourceService).triggerReindex(TENANT_ID, RESOURCE_ID, 920001L);
    }
}
