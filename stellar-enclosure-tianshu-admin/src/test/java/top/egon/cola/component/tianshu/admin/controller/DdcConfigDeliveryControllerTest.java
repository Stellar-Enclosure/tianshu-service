package top.egon.cola.component.tianshu.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.HandlerMethod;
import top.egon.cola.component.tianshu.admin.internal.controller.DdcConfigDeliveryController;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigProcessReceiptBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigProcessReceiptConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliveryTokenService;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliverySecurityConfiguration;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigReceiptService;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigSnapshotService;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.ServiceAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DdcConfigDeliveryControllerTest {
    private static final String ATTRIBUTE = DdcConfigDeliveryController.VERIFIED_WORKLOAD_ATTRIBUTE;
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(1001L, "1001", "service:reader-client");
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("1001", "core", "prod", "reader", "application.yml");
    private static final String CONTENT = "sha256:" + "a".repeat(64), RESTART = "sha256:" + "b".repeat(64);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DdcConfigSnapshotService snapshots = mock(DdcConfigSnapshotService.class);
    private final DdcConfigReceiptService receipts = mock(DdcConfigReceiptService.class);
    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        var principal = principal();
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal,
                principal.scopes().stream().map(value -> new SimpleGrantedAuthority("SCOPE_" + value)).toList(),
                URI.create("https://identity.example/tenants/1001")));
        mvc = MockMvcBuilders.standaloneSetup(new DdcConfigDeliveryController(snapshots, receipts))
                .setMessageConverters(DdcConfigDeliverySecurityConfiguration.deliveryMessageConverter(json), new MappingJackson2HttpMessageConverter(json))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); MDC.clear(); }

    @Test
    void internalSnapshotAndReceiptPathsHaveExplicitControllerMappings() throws Exception {
        mvc.perform(get("/internal/v1/config/snapshot"))
                .andExpect(result -> assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class));
        mvc.perform(post("/internal/v1/config/receipts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class));
    }

    @Test
    void snapshotReturnsCompleteStringRevisionAndFreshCredentialWithoutSharedCaching() throws Exception {
        var content = snapshot();
        String etag = DdcConfigCanonicalJson.etag(content);
        when(snapshots.snapshot(any(), isNull())).thenReturn(new DdcConfigSnapshotService.Snapshot(content, etag, "signed-delivery-1", false));
        mvc.perform(get("/internal/v1/config/snapshot").requestAttr(ATTRIBUTE, workload())
                        .header("X-Config-Container-Id", "untrusted-container-claim"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-cache"))
                .andExpect(header().string("X-Config-Delivery-Token", "signed-delivery-1"))
                .andExpect(header().string("X-Config-Container-Id", "containerd://a1"))
                .andExpect(header().string("X-Config-Valid-Until", content.getValidUntil().toString()))
                .andExpect(jsonPath("$.releaseRevision").value("43"))
                .andExpect(jsonPath("$.scope.resourceName").value("application.yml"))
                .andExpect(jsonPath("$.hotValues['feature.enabled']").value(true))
                .andExpect(jsonPath("$.success").doesNotExist());
        verifyNoInteractions(receipts);
    }

    @Test
    void unchangedAuthorizedBootReturnsBodyless304AndRefreshesDeliveryCredential() throws Exception {
        var content = snapshot();
        String etag = DdcConfigCanonicalJson.etag(content);
        when(snapshots.snapshot(any(), eq(etag))).thenReturn(new DdcConfigSnapshotService.Snapshot(content, etag, "fresh-signed-delivery", true));
        mvc.perform(get("/internal/v1/config/snapshot").requestAttr(ATTRIBUTE, workload()).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified()).andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-cache"))
                .andExpect(header().string("X-Config-Delivery-Token", "fresh-signed-delivery"))
                .andExpect(header().string("X-Config-Container-Id", "containerd://a1"))
                .andExpect(header().string("X-Config-Valid-Until", content.getValidUntil().toString()));
    }

    @Test
    void bothSnapshotSuccessResponsesDocumentTheVerifiedContainerHeader() throws Exception {
        var mapping = DdcConfigDeliveryController.class.getMethod("configSnapshot", ServiceIdentityPrincipal.class,
                DdcConfigWorkloadIdentityVO.class, String.class, jakarta.servlet.http.HttpServletRequest.class);
        var responses = mapping.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class);
        for (String code : new String[]{"200", "304"}) {
            var response = java.util.Arrays.stream(responses.value()).filter(value -> value.responseCode().equals(code)).findFirst().orElseThrow();
            assertThat(java.util.Arrays.stream(response.headers()).map(value -> value.name()).toList())
                    .as("snapshot %s response headers", code).contains("X-Config-Container-Id");
        }
    }

    @Test
    void matchingEtagCannotSkipMissingWorkloadProof() throws Exception {
        mvc.perform(get("/internal/v1/config/snapshot").header(HttpHeaders.IF_NONE_MATCH, "\"known\""))
                .andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().doesNotExist("X-Config-Container-Id"))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID")).andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(snapshots, receipts);
    }

    @Test
    void authenticatedReceiptReturns204OnlyAfterServiceAccepts() throws Exception {
        var command = command();
        mvc.perform(post("/internal/v1/config/receipts").requestAttr(ATTRIBUTE, workload())
                        .header("Idempotency-Key", key(command)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verify(receipts).accept(eq(workload()), any(DdcConfigReceiptCommand.class), eq(key(command)));
    }

    @Test
    void malformedBodyUsesSafeEnvelopeAndNeverReachesReceiptService() throws Exception {
        mvc.perform(post("/internal/v1/config/receipts").requestAttr(ATTRIBUTE, workload())
                        .header("Idempotency-Key", "synthetic").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"private-synthetic-claim\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CONFIG_REQUEST_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("private-synthetic-claim"));
        verifyNoInteractions(receipts);
    }

    @Test
    void duplicateFieldsAndJsonNumbersCannotReplaceDecimalStrings() throws Exception {
        var command = command();
        String valid = json.writeValueAsString(command);
        String duplicate = valid.replace("\"reportSeq\":\"2\"", "\"reportSeq\":\"2\",\"reportSeq\":\"2\"");
        String numeric = valid.replace("\"reportSeq\":\"2\"", "\"reportSeq\":2");
        assertThat(duplicate).isNotEqualTo(valid);
        assertThat(numeric).isNotEqualTo(valid);
        for (String body : new String[]{duplicate, numeric}) {
            mvc.perform(post("/internal/v1/config/receipts").requestAttr(ATTRIBUTE, workload())
                            .header("Idempotency-Key", key(command)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("CONFIG_REQUEST_INVALID"));
        }
        verifyNoInteractions(receipts);
    }

    @Test
    void actualCredentialFilterChecksWorkloadBeforeMalformedBodyOrMatchingEtag() throws Exception {
        var oauth = mock(ServiceAccessTokenVerifier.class);
        var policies = mock(IdpEndpointAuthenticationPolicy.class);
        var workloads = mock(DdcConfigWorkloadVerifier.class);
        var service = principal();
        when(oauth.verify("synthetic-service-token")).thenReturn(new AccessTokenVerification.Valid<>(service, service.scopes(), null,
                URI.create("https://identity.example/tenants/1001")));
        when(policies.requirement(any())).thenReturn(IdpEndpointAuthenticationPolicy.Requirement.SERVICE);
        when(workloads.verify(any(), any(), any())).thenThrow(new DdcConfigWorkloadVerifier.Rejected(
                HttpStatus.SERVICE_UNAVAILABLE, "KUBERNETES_STATE_UNAVAILABLE"));
        Class<?> filterType = Class.forName("top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliverySecurityConfiguration$DeliveryCredentialsFilter");
        var constructor = filterType.getDeclaredConstructor(ServiceAccessTokenVerifier.class, IdpEndpointAuthenticationPolicy.class,
                DdcConfigWorkloadVerifier.class, ObjectMapper.class);
        constructor.setAccessible(true);
        var filter = (jakarta.servlet.Filter) constructor.newInstance(oauth, policies, workloads, json);
        var protectedMvc = MockMvcBuilders.standaloneSetup(new DdcConfigDeliveryController(snapshots, receipts))
                .setMessageConverters(DdcConfigDeliverySecurityConfiguration.deliveryMessageConverter(json), new MappingJackson2HttpMessageConverter(json))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).addFilters(filter).build();
        protectedMvc.perform(post("/internal/v1/config/receipts").header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-service-token")
                        .header("X-Egon-Workload-Token", "synthetic-pod-token").header("X-Config-Boot-Id", "boot-1")
                        .header("Idempotency-Key", "synthetic").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("KUBERNETES_STATE_UNAVAILABLE"));
        protectedMvc.perform(get("/internal/v1/config/snapshot").header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-service-token")
                        .header("X-Egon-Workload-Token", "synthetic-pod-token").header("X-Config-Boot-Id", "boot-1")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"known\""))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("KUBERNETES_STATE_UNAVAILABLE"));
        verify(workloads, times(2)).verify(any(), eq("synthetic-pod-token"), eq("boot-1"));
        verifyNoInteractions(snapshots, receipts);
    }

    @Test
    void domainErrorsRetainConflictVersusUnavailable() throws Exception {
        var command = command();
        doThrow(new DdcConfigReceiptService.Rejected(HttpStatus.CONFLICT, "STALE_RECEIPT")).when(receipts).accept(any(), any(), anyString());
        mvc.perform(post("/internal/v1/config/receipts").requestAttr(ATTRIBUTE, workload()).header("Idempotency-Key", key(command))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("STALE_RECEIPT"));
        when(snapshots.snapshot(any(), any())).thenThrow(new DdcConfigSnapshotService.Rejected(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_AUTHORITY_UNAVAILABLE"));
        mvc.perform(get("/internal/v1/config/snapshot").requestAttr(ATTRIBUTE, workload()))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("X-Config-Container-Id"))
                .andExpect(jsonPath("$.errorCode").value("CONFIG_AUTHORITY_UNAVAILABLE"));
    }

    @Test
    void receiptUsesTrustedWorkloadAndConfirmedSignedFence() throws Exception {
        try (var validator = validator()) {
            var fixture = receiptService(validator);
            var command = command();
            fixture.service().accept(workload(), command, key(command));
            var saved = ArgumentCaptor.forClass(DdcConfigProcessReceiptBO.class);
            verify(fixture.repository()).accept(eq(TENANT), saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(1001L);
            assertThat(saved.getValue().getConfigId()).isEqualTo(820001L);
            assertThat(saved.getValue().getBootFence()).isEqualTo(8101L);
            assertThat(saved.getValue().getTargetRevision()).isEqualTo(6L);
            assertThat(saved.getValue().getPodUid()).isEqualTo("pod-1");
            assertThat(saved.getValue().getReceiptDigest()).isEqualTo(command.getReceiptDigest().substring(7));
            assertThat(MDC.get("tenantId")).isNull();
        }
    }

    @Test
    void supersededDeliveryWrongBootAndWrongFingerprintCannotWrite() throws Exception {
        try (var validator = validator()) {
            var fixture = receiptService(validator);
            var report = command();
            rejected(() -> fixture.service().accept(workload(), report, "wrong-key"), HttpStatus.CONFLICT, "RECEIPT_IDEMPOTENCY_MISMATCH");
            var wrongBoot = command().setBootId("boot-2");
            rejected(() -> fixture.service().accept(workload(), wrongBoot, key(wrongBoot)), HttpStatus.CONFLICT, "RECEIPT_BOOT_MISMATCH");
            var altered = command().setReceiptDigest("sha256:" + "f".repeat(64));
            rejected(() -> fixture.service().accept(workload(), altered, key(altered)), HttpStatus.BAD_REQUEST, "CONFIG_RECEIPT_DIGEST_INVALID");
            when(fixture.claims().releaseRevision()).thenReturn("42");
            rejected(() -> fixture.service().accept(workload(), report, key(report)), HttpStatus.CONFLICT, "RECEIPT_TARGET_SUPERSEDED");
            verifyNoInteractions(fixture.repository());
        }
    }

    @Test
    void signedCredentialFailureAndPersistenceOutcomesKeepDistinctRecovery() throws Exception {
        try (var validator = validator()) {
            var fixture = receiptService(validator);
            var report = command();
            doThrow(new DdcConfigDeliveryTokenService.Rejected()).when(fixture.tokens()).verify(anyString(), any());
            rejected(() -> fixture.service().accept(workload(), report, key(report)), HttpStatus.UNAUTHORIZED, "CONFIG_DELIVERY_TOKEN_REJECTED");
            doThrow(new DdcConfigDeliveryTokenService.Unavailable()).when(fixture.tokens()).verify(anyString(), any());
            rejected(() -> fixture.service().accept(workload(), report, key(report)), HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_DELIVERY_TOKEN_UNAVAILABLE");
            doReturn(fixture.claims()).when(fixture.tokens()).verify(anyString(), any());
            doReturn(DdcConfigProcessReceiptRepository.AcceptanceEnum.STALE).when(fixture.repository()).accept(any(), any());
            rejected(() -> fixture.service().accept(workload(), report, key(report)), HttpStatus.CONFLICT, "STALE_RECEIPT");
            doReturn(DdcConfigProcessReceiptRepository.AcceptanceEnum.CONFLICT).when(fixture.repository()).accept(any(), any());
            rejected(() -> fixture.service().accept(workload(), report, key(report)), HttpStatus.CONFLICT, "RECEIPT_CONFLICT");
            doThrow(new DdcConfigProcessReceiptRepository.ReceiptUnavailableException()).when(fixture.repository()).accept(any(), any());
            rejected(() -> fixture.service().accept(workload(), report, key(report)), HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_RECEIPT_COMMIT_UNKNOWN");
        }
    }

    private ReceiptFixtureVO receiptService(LocalValidatorFactoryBean validator) throws Exception {
        var converter = (DdcConfigProcessReceiptConverter) Class.forName(DdcConfigProcessReceiptConverter.class.getName() + "Impl").getConstructor().newInstance();
        var tokens = mock(DdcConfigDeliveryTokenService.class);
        var repository = mock(DdcConfigProcessReceiptRepository.class);
        var claims = mock(DdcConfigDeliveryTokenService.Claims.class);
        when(tokens.verify(anyString(), any())).thenReturn(claims);
        when(claims.bootFence()).thenReturn(8101L); when(claims.configId()).thenReturn(820001L);
        when(claims.releaseRevision()).thenReturn("43"); when(claims.enabled()).thenReturn(true);
        when(claims.contentSha256()).thenReturn(CONTENT); when(claims.restartDigest()).thenReturn(RESTART);
        when(claims.scope()).thenReturn(SCOPE); when(claims.identityTenantId()).thenReturn(TENANT.identityTenantId());
        when(claims.targetBindingId()).thenReturn(workload().targetBindingId());
        when(claims.targetBindingRevision()).thenReturn(workload().targetBindingRevision()); when(claims.bootId()).thenReturn(workload().bootId());
        var manifest = json.createObjectNode().put("schemaVersion", "1").put("enabled", true).put("contentSha256", CONTENT).put("restartDigest", RESTART);
        manifest.set("scope", json.valueToTree(SCOPE)); manifest.putObject("source").put("configId", "820001").put("revision", "43");
        when(snapshots.requireCurrent(any())).thenReturn(new DdcConfigSnapshotService.Published(820001L, snapshot(), manifest));
        when(repository.accept(any(), any())).thenReturn(DdcConfigProcessReceiptRepository.AcceptanceEnum.ADVANCED);
        return new ReceiptFixtureVO(new DdcConfigReceiptService(snapshots, tokens, repository, converter, new DdcTenantContextExecutor(), validator), repository, tokens, claims);
    }

    private record ReceiptFixtureVO(DdcConfigReceiptService service,DdcConfigProcessReceiptRepository repository,
            DdcConfigDeliveryTokenService tokens,DdcConfigDeliveryTokenService.Claims claims) { }
    private LocalValidatorFactoryBean validator() { var validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet(); return validator; }
    private void rejected(Runnable action,HttpStatus status,String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DdcConfigReceiptService.Rejected.class, failure -> {
            assertThat(failure.status()).isEqualTo(status); assertThat(failure.errorCode()).isEqualTo(code);
            assertThat(failure.getMessage()).doesNotContain("synthetic-delivery-token");
        });
    }
    private static DdcConfigWorkloadIdentityVO workload() { return new DdcConfigWorkloadIdentityVO(TENANT, SCOPE, "reader-prod", 6L,
            "prod-1", "reader-prod", "workload-1", "pod-1", "application", "containerd://a1", "boot-1"); }
    private static DdcConfigSnapshotDTO snapshot() { return new DdcConfigSnapshotDTO("1", SCOPE, "43", true, RESTART, CONTENT,
            Map.of("feature.enabled", BooleanNode.TRUE), null, Instant.parse("2026-11-18T18:00:00Z")); }
    private static ServiceIdentityPrincipal principal() {
        var now = Instant.now();
        return new ServiceIdentityPrincipal("reader-client", "1001", "reader-client", "token-1", URI.create("https://tianshu.example/config"),
                1L, Set.of("tianshu:config:read", "tianshu:config:receipt:write"), "core", "reader", "prod", "credential-1",
                now.minusSeconds(60), now.plusSeconds(300), "reader-app", ServiceTokenContext.TENANT);
    }
    private static DdcConfigReceiptCommand command() {
        var report = DdcConfigReceiptCommand.builder().schemaVersion("1").bootId("boot-1").releaseRevision("43").reportSeq("2")
                .appliedRevision("43").contentSha256(CONTENT).appliedContentSha256(CONTENT).baselineRestartDigest(RESTART)
                .deliveryToken("synthetic-delivery-token").status(DdcConfigReceiptStatusEnum.APPLIED)
                .reportedAt(Instant.parse("2026-11-18T17:00:00Z")).appliedAt(Instant.parse("2026-11-18T17:00:00Z")).build();
        return report.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(report));
    }
    private static String key(DdcConfigReceiptCommand command) { return "pod-1/containerd://a1/boot-1/" + command.getReleaseRevision() + "/" + command.getReportSeq(); }
}
