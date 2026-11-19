package top.egon.cola.component.tianshu.admin.service.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiResourceConverter;
import top.egon.cola.component.tianshu.admin.outbox.DdcSiCatalogOutboxNotifier;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.admin.search.DdcSiSemanticIndexService;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier.ApprovalVO;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier.PublicationScopeVO;
import top.egon.cola.component.tianshu.admin.service.si.impl.DdcSiResourceServiceImpl;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiAgentContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiMcpContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiPromptSkillContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiPublicationEvent;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TEST-001/002/003/006：验证并发乐观锁冲突、不可变快照回滚与晋级溯源、appCode 绑定优先解析，
 * 以及运行时自上报幂等与保护人工发布主线不被覆盖。
 */
@ExtendWith(MockitoExtension.class)
class DdcSiResourceServiceImplTest {

    @Mock
    private DdcSiResourceRepository repository;
    @Mock
    private DdcSiCatalogOutboxNotifier outboxNotifier;
    @Mock
    private DdcSiSemanticIndexService semanticIndexService;
    @Mock
    private DdcSiResourceConverter converter;
    @Mock
    private DdcSiMcpPublicationVerifier mcpPublicationVerifier;

    private Clock fixedClock;
    private TransactionTemplate txTemplate;
    private DdcSiLifecycleTransitionPolicy transitionPolicy;
    private List<DdcSiContentStrategy> strategies;
    private DdcSiResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
        ObjectMapper objectMapper = new ObjectMapper();
        strategies = List.of(
                new DdcSiAgentContentStrategy(objectMapper),
                new DdcSiMcpContentStrategy(objectMapper),
                new DdcSiPromptSkillContentStrategy(objectMapper));
        transitionPolicy = new DdcSiLifecycleTransitionPolicy(Map.of("dev", List.of("test", "staging"),
                "test", List.of("staging", "prod"), "staging", List.of("prod")));
        txTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
        service = new DdcSiResourceServiceImpl(
                repository,
                strategies,
                new DdcSiDependencyValidator(repository, objectMapper),
                transitionPolicy,
                outboxNotifier,
                semanticIndexService,
                converter,
                txTemplate,
                fixedClock,
                mcpPublicationVerifier);
    }

    @Test
    void publishEmitsOutboxEventAndAbortsWithoutNotificationOnVersionConflict() {
        String agentSpec = "{\"modelKey\":\"gpt-6-luna\",\"runtime\":{\"maxSteps\":6}}";
        DdcSiResourceBO draft = DdcSiResourceBO.builder()
                .id(101L).tenantId(1L).bizCode("xingyuan").namespaceId(10L).namespaceCode("default")
                .envCode("dev").appCode("app-a").kind(DdcAiKindEnumHolder.AGENT)
                .resourceKey("research-agent").displayName("Research").description("desc")
                .tags(List.of("research")).lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE).specJson(agentSpec)
                .contentSha256("a".repeat(64)).lastVersionNo(0L).version(2L).build();

        when(repository.findProjectedById(1L, 101L)).thenReturn(Optional.of(draft));
        when(repository.findActiveForUpdateById(1L, 101L)).thenReturn(Optional.of(draft));
        when(repository.allocateNextVersionNo(1L, 101L)).thenReturn(1L);
        when(repository.allocateVersionId()).thenReturn(201L);
        when(repository.insertVersion(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.updateVersioned(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(1L, 101L, 2L, "init release", "user-1"))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56409));
        verify(outboxNotifier, never()).enqueueAndScheduleAfterCommit(any(), any(), any());

        DdcSiResourceBO updated = DdcSiResourceBO.builder()
                .id(101L).tenantId(1L).bizCode("xingyuan").namespaceCode("default").envCode("dev")
                .kind(DdcSiKindEnum.AGENT).resourceKey("research-agent")
                .lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).lastVersionNo(1L)
                .currentVersionId(201L).currentVersionNo(1L).version(3L).build();
        when(repository.updateVersioned(any())).thenReturn(Optional.of(updated));
        DdcSiVersionBO published = service.publish(1L, 101L, 2L, "init release", "user-1");
        assertThat(published.getVersionNo()).isEqualTo(1L);
        assertThat(published.getDependencyManifestJson().path("root").path("versionId").asText()).isEqualTo("201");
        assertThat(published.getDependencyManifestJson().path("nodes").isEmpty()).isTrue();
        verify(outboxNotifier).enqueueAndScheduleAfterCommit(
                eq(DdcSiPublicationEvent.EventType.PUBLISHED), any(DdcSiResourceBO.class), any(DdcSiVersionBO.class));
    }

    @Test
    void mcpPublishWithoutAuthoritativeApprovalDoesNotAllocateVersionOrOutbox() {
        var draft = mcpResource(canonicalMcpDraft(), DdcSiLifecycleStatusEnum.DRAFT);
        when(repository.findProjectedById(1L, 101L)).thenReturn(Optional.of(draft));
        allowMcpWriteUnlessApprovalRejects(draft);
        when(mcpPublicationVerifier.requireApproved(any(), any(), any()))
                .thenThrow(new IllegalStateException("TIANSHU_MCP_PUBLICATION_APPROVAL_MISSING"));

        assertThatThrownBy(() -> service.publish(1L, 101L, 2L, "publish", "operator"))
                .isInstanceOf(CommonException.class);
        verify(repository, never()).allocateVersionId();
        verify(repository, never()).insertVersion(any());
        verify(outboxNotifier, never()).enqueueAndScheduleAfterCommit(any(), any(), any());
    }

    @Test
    void historicalMcpCanBeReadButCannotCreateAnUnapprovedActiveRollback() {
        String priorBody = """
                {"protocolVersion":"2025-03-26","transport":"SSE","endpoint":"https://tools.example.test/legacy"}
                """;
        var published = mcpResource(priorBody, DdcSiLifecycleStatusEnum.PUBLISHED);
        String digest = DdcSiContentStrategy.sha256Hex(priorBody);
        var root = new VersionIdentityVO("1", "101", "201", "1", DdcSiKindEnum.MCP, "dev", digest);
        var prior = DdcSiVersionBO.builder().id(201L).tenantId(1L).resourceId(101L).versionNo(1L)
                .kind(DdcSiKindEnum.MCP).envCode("dev").specJson(priorBody).digestSha256(digest)
                .dependencyManifestJson(new ObjectMapper().valueToTree(DdcSiDependencyManifestVO.create(root, null, List.of(), List.of())))
                .build();
        when(repository.findProjectedById(1L, 101L)).thenReturn(Optional.of(published));
        when(repository.findActiveForUpdateById(1L, 101L)).thenReturn(Optional.of(published));
        when(repository.findHistoryVersionByResourceAndNo(1L, 101L, 1L)).thenReturn(Optional.of(prior));
        allowMcpWriteUnlessApprovalRejects(published);
        when(mcpPublicationVerifier.requireApproved(any(), eq(priorBody), eq(digest)))
                .thenThrow(new IllegalStateException("TIANSHU_MCP_PUBLICATION_APPROVAL_SCHEMA_INVALID"));

        assertThat(service.get(1L, 101L).getSpecJson()).isEqualTo(priorBody);
        assertThatThrownBy(() -> service.rollback(1L, 101L, 2L, 1L, "rollback", "operator"))
                .isInstanceOf(CommonException.class).hasMessageContaining("approved MCP schema");
        assertThat(prior.getSpecJson()).isEqualTo(priorBody);
        verify(repository, never()).allocateVersionId();
        verify(repository, never()).insertVersion(any());
        verify(outboxNotifier, never()).enqueueAndScheduleAfterCommit(any(), any(), any());
    }

    @Test
    void mcpPublishSuppliesExactCanonicalBodyAndThePersistedResourceScope() {
        var draft = mcpResource(canonicalMcpDraft(), DdcSiLifecycleStatusEnum.DRAFT);
        when(repository.findProjectedById(1L, 101L)).thenReturn(Optional.of(draft));
        allowMcpWriteUnlessApprovalRejects(draft);
        var normalized = strategies.get(1).validateAndNormalize(DdcSiKindEnum.MCP, draft.getSpecJson());
        var scope = new PublicationScopeVO("1", "xingyuan", "default", "dev", "app-a", "weather", "101");
        when(mcpPublicationVerifier.requireApproved(scope, normalized.getNormalizedJson(), normalized.getDigestSha256()))
                .thenReturn(new ApprovalVO("approval-1", "b".repeat(64), normalized.getDigestSha256(), fixedClock.instant().plusSeconds(60)));

        var version = service.publish(1L, 101L, 2L, "approved", "operator");

        assertThat(version.getSpecJson()).isEqualTo(normalized.getNormalizedJson());
        assertThat(version.getDigestSha256()).isEqualTo(normalized.getDigestSha256());
        verify(mcpPublicationVerifier).requireApproved(scope, version.getSpecJson(), version.getDigestSha256());
        verify(repository).insertVersion(any());
        verify(outboxNotifier).enqueueAndScheduleAfterCommit(eq(DdcSiPublicationEvent.EventType.PUBLISHED), any(), any());
    }

    private DdcSiResourceBO mcpResource(String body, DdcSiLifecycleStatusEnum status) {
        return DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan").namespaceId(10L).namespaceCode("default")
                .envCode("dev").appCode("app-a").kind(DdcSiKindEnum.MCP).resourceKey("weather").displayName("Weather")
                .tags(List.of()).specJson(body).contentSha256(DdcSiContentStrategy.sha256Hex(body))
                .lifecycleStatus(status).lastVersionNo(1L).version(2L).build();
    }

    private void allowMcpWriteUnlessApprovalRejects(DdcSiResourceBO draft) {
        lenient().when(repository.findActiveForUpdateById(1L, 101L)).thenReturn(Optional.of(draft));
        lenient().when(repository.allocateNextVersionNo(1L, 101L)).thenReturn(2L);
        lenient().when(repository.allocateVersionId()).thenReturn(202L);
        lenient().when(repository.insertVersion(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(repository.updateVersioned(any())).thenReturn(Optional.of(draft));
    }

    private static String canonicalMcpDraft() {
        return """
                {
                  "schemaVersion":"mcp-server-v2","sourceType":"REMOTE_HTTPS","protocolVersion":"2025-11-25",
                  "transport":"STREAMABLE_HTTP","endpoint":"https://weather.example.test/mcp","capabilityFingerprint":"%s","tools":[]
                }
                """.formatted("a".repeat(64));
    }

    @Test
    void mcpAutoRegistrationRequiresAnExistingIdentityBeforeAutomaticPublication() {
        var request = DdcSiSelfRegistrationRequest.builder().tenantId(1L).bizCode("xingyuan").namespaceCode("default")
                .envCode("dev").serviceName("app-a").instanceId("weather-0").kind(DdcSiKindEnum.MCP)
                .resourceKey("weather").displayName("Weather").specJson(canonicalMcpDraft()).autoPublishIfNew(true)
                .build();
        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);

        assertThatThrownBy(() -> service.registerManaged(request, false))
                .isInstanceOf(CommonException.class).hasMessageContaining("Register the target MCP resource");
        verify(repository, never()).insert(any());
        verify(repository, never()).insertVersion(any());
        verifyNoInteractions(mcpPublicationVerifier, outboxNotifier);
    }

    @Test
    void resolveResourcePrioritizesActiveAppBindingPinOverDefaultActiveVersion() {
        DdcSiResourceBO resource = DdcSiResourceBO.builder()
                .id(101L).tenantId(1L).bizCode("xingyuan").namespaceId(10L).namespaceCode("default")
                .envCode("prod").appCode("app-a").kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .displayName("Planner").lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE).lastVersionNo(3L).currentVersionId(303L)
                .currentVersionNo(3L).specJson("{\"modelKey\":\"latest\"}").build();
        DdcSiBindingBO pinned = DdcSiBindingBO.builder()
                .id(501L).appCode("tianji-agent-server").resourceId(101L).pinnedVersionNo(2L)
                .status(DdcSiBindingStatusEnum.ACTIVE).build();
        DdcSiVersionBO v2 = storedVersion(302L, 2L, "{\"modelKey\":\"pinned-v2\"}");
        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "app-a", DdcSiKindEnum.AGENT, "planner"))
                .thenReturn(Optional.of(resource));
        when(repository.findActiveBinding(1L, "xingyuan", 10L, "prod", "tianji-agent-server",
                DdcSiKindEnum.AGENT, "planner")).thenReturn(Optional.of(pinned));
        when(repository.findHistoryVersionByResourceAndNo(1L, 101L, 2L)).thenReturn(Optional.of(v2));
        when(converter.toCatalogDescriptor(any(), any())).thenAnswer(inv -> {
            DdcSiResourceBO res = inv.getArgument(0);
            DdcSiVersionBO ver = inv.getArgument(1);
            return DdcSiResourceDescriptor.builder()
                    .tenantId(res.getTenantId()).bizCode(res.getBizCode()).namespaceCode(res.getNamespaceCode())
                    .envCode(res.getEnvCode()).kind(res.getKind()).resourceKey(res.getResourceKey())
                    .displayName(res.getDisplayName()).status(res.getLifecycleStatus()).sourceType(res.getSourceType())
                    .activeVersionNo(ver.getVersionNo().intValue())
                    .digestSha256(ver.getDigestSha256()).build();
        });

        Optional<DdcSiResourceDescriptor> resolved = service.resolveResource(
                1L, "xingyuan", "default", "prod", "app-a", "tianji-agent-server", DdcSiKindEnum.AGENT, "planner", null);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getActiveVersionNo()).isEqualTo(2);
        assertThat(resolved.get().getDigestSha256()).isEqualTo(v2.getDigestSha256());
        assertThat(resolved.get().getSpecJson()).isNull();
    }

    @Test
    void requestedMissingVersionNeverReturnsCurrentBody() {
        var resource = DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan")
                .namespaceId(10L).namespaceCode("default").envCode("prod").appCode("app-a")
                .kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).currentVersionId(303L)
                .currentVersionNo(3L).specJson("{\"modelKey\":\"latest\"}").build();
        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "app-a",
                DdcSiKindEnum.AGENT, "planner")).thenReturn(Optional.of(resource));
        org.mockito.Mockito.lenient().when(converter.toDescriptor(eq(resource), org.mockito.ArgumentMatchers.isNull(), anyList()))
                .thenReturn(DdcSiResourceDescriptor.builder().activeVersionNo(3).specJson(resource.getSpecJson()).build());

        assertThatThrownBy(() -> service.resolveResource(1L, "xingyuan", "default", "prod", "app-a", "app-a",
                DdcSiKindEnum.AGENT, "planner", 1)).isInstanceOf(CommonException.class);
        verify(converter, never()).toDescriptor(any(), any(), any());
        verify(converter, never()).toCatalogDescriptor(any(), any());
    }

    @Test
    void selfRegistrationIsIdempotentAndProtectsManuallyPublishedConsoleResources() {
        String validAgentJson = "{\"modelKey\":\"gpt-6-luna\"}";
        DdcSiSelfRegistrationRequest req = DdcSiSelfRegistrationRequest.builder()
                .tenantId(1L).bizCode("xingyuan").namespaceCode("default").envCode("prod")
                .serviceName("tianji-agent-server").instanceId("inst-1").kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .displayName("Planner").description("desc").specJson(validAgentJson).autoPublishIfNew(true).build();

        DdcSiResourceBO consolePublished = DdcSiResourceBO.builder()
                .id(101L).tenantId(1L).bizCode("xingyuan").namespaceId(10L).namespaceCode("default")
                .envCode("prod").appCode("tianji-agent-server").kind(DdcSiKindEnum.AGENT)
                .resourceKey("planner").displayName("Planner").lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE).contentSha256("e".repeat(64))
                .lastVersionNo(2L).currentVersionId(202L).currentVersionNo(2L).version(1L).build();

        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "tianji-agent-server",
                DdcSiKindEnum.AGENT, "planner")).thenReturn(Optional.of(consolePublished));
        when(converter.toDescriptor(eq(consolePublished), any(), any())).thenReturn(
                DdcSiResourceDescriptor.builder()
                        .tenantId(1L).bizCode("xingyuan").namespaceCode("default").envCode("prod")
                        .kind(DdcSiKindEnum.AGENT).resourceKey("planner").displayName("Planner")
                        .status(DdcSiLifecycleStatusEnum.PUBLISHED).sourceType(DdcSiSourceTypeEnum.CONSOLE)
                        .activeVersionNo(2).build());

        DdcSiResourceDescriptor result = service.registerManaged(req, false);
        assertThat(result.getActiveVersionNo()).isEqualTo(2);
        assertThat(result.getSourceType()).isEqualTo(DdcSiSourceTypeEnum.CONSOLE);
        verify(repository, never()).updateVersioned(any());
        verify(outboxNotifier, never()).enqueueAndScheduleAfterCommit(any(), any(), any());
    }

    @Test
    void archivedResourceRejectsUpdatesAndPublish() {
        DdcSiResourceBO archived = DdcSiResourceBO.builder()
                .id(101L).tenantId(1L).lifecycleStatus(DdcSiLifecycleStatusEnum.ARCHIVED).version(1L).build();
        when(repository.findProjectedById(1L, 101L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.publish(1L, 101L, 1L, "retry", "user-1"))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56409));
    }

    @Test
    void rollbackPublishesANewVersionWithoutLookingUpTheEditableReferenceGraph() {
        var resource = DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan")
                .namespaceId(10L).namespaceCode("default").envCode("prod").appCode("app-a")
                .kind(DdcSiKindEnum.AGENT).resourceKey("planner").version(10L).currentVersionId(303L)
                .lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).build();
        var old = storedVersion(301L, 1L, "{\"modelKey\":\"original\"}");
        when(repository.findActiveForUpdateById(1L, 101L)).thenReturn(Optional.of(resource));
        when(repository.findHistoryVersionByResourceAndNo(1L, 101L, 1L)).thenReturn(Optional.of(old));
        when(repository.allocateNextVersionNo(1L, 101L)).thenReturn(4L);
        when(repository.allocateVersionId()).thenReturn(304L);
        when(repository.insertVersion(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.updateVersioned(any())).thenReturn(Optional.of(resource));

        var rolledBack = service.rollback(1L, 101L, 10L, 1L, null, "operator");

        assertThat(rolledBack.getId()).isEqualTo(304L);
        assertThat(rolledBack.getVersionNo()).isEqualTo(4L);
        assertThat(rolledBack.getDependencyManifestJson().at("/source/versionId").asText()).isEqualTo("301");
        assertThat(rolledBack.getSpecJson()).isEqualTo(old.getSpecJson());
        assertThat(resource.getCurrentVersionId()).isEqualTo(304L);
        verify(outboxNotifier).enqueueAndScheduleAfterCommit(DdcSiPublicationEvent.EventType.ROLLED_BACK, resource, rolledBack);
        verify(repository, never()).findReferencesBySource(any(), any(), any(), any());
    }

    @Test
    void rollbackOutboxSignalSchedulesTheFreshVersionForIndexing() {
        var adapters = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        adapters.addBean("siIndex", semanticIndexService);
        var outbox = mock(top.egon.cola.component.outbox.api.TransactionalOutbox.class);
        var notifier = new DdcSiCatalogOutboxNotifier(outbox,
                adapters.getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class),
                adapters.getBeanProvider(DdcSiSemanticIndexService.class),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                new ObjectMapper().findAndRegisterModules(), fixedClock);
        var resource = DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan")
                .namespaceCode("default").envCode("prod").kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .currentVersionId(304L).build();
        var fresh = storedVersion(304L, 4L, "{\"modelKey\":\"restored\"}");

        notifier.enqueueAndScheduleAfterCommit(DdcSiPublicationEvent.EventType.ROLLED_BACK, resource, fresh);

        verify(outbox).enqueue(any());
        verify(semanticIndexService).scheduleReindex(1L, 101L, 304L);
    }

    @Test
    void promotionRecordsTheFrozenSourceIdentityAndLocksSourceAndDestination() {
        var source = DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan")
                .namespaceId(10L).namespaceCode("default").envCode("staging").appCode("app-a")
                .kind(DdcSiKindEnum.AGENT).resourceKey("planner").version(10L)
                .currentVersionId(301L).currentVersionNo(1L).lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).build();
        var destination = DdcSiResourceBO.builder().id(99L).tenantId(1L).bizCode("xingyuan").namespaceId(10L)
                .envCode("prod").appCode("app-a").kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .version(0L).lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT).build();
        var version = storedVersion(301L, 1L, "{\"modelKey\":\"original\"}", "staging");
        when(repository.findProjectedById(1L, 101L)).thenReturn(Optional.of(source));
        when(repository.findHistoryVersionByResourceAndNo(1L, 101L, 1L)).thenReturn(Optional.of(version));
        when(repository.findHistoryVersionsByIds(1L, List.of(301L))).thenReturn(List.of(version));
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "app-a", DdcSiKindEnum.AGENT, "planner"))
                .thenReturn(Optional.of(destination));
        when(repository.findActiveForUpdateById(1L, 99L)).thenReturn(Optional.of(destination));
        when(repository.findActiveForUpdateById(1L, 101L)).thenReturn(Optional.of(source));
        when(repository.allocateNextVersionNo(1L, 99L)).thenReturn(1L);
        when(repository.allocateVersionId()).thenReturn(401L);
        when(repository.insertVersion(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.updateVersioned(any())).thenReturn(Optional.of(destination));

        var promoted = service.promote(1L, 101L, "prod", null, "operator");

        var order = org.mockito.Mockito.inOrder(repository);
        order.verify(repository).findActiveForUpdateById(1L, 99L);
        order.verify(repository).findActiveForUpdateById(1L, 101L);
        order.verify(repository).allocateNextVersionNo(1L, 99L);
        assertThat(promoted.getDependencyManifestJson().at("/root/envCode").asText()).isEqualTo("prod");
        assertThat(promoted.getDependencyManifestJson().at("/source/versionId").asText()).isEqualTo("301");
        assertThat(promoted.getDependencyManifestJson().at("/source/digestSha256").asText()).isEqualTo(version.getDigestSha256());
        verify(outboxNotifier).enqueueAndScheduleAfterCommit(DdcSiPublicationEvent.EventType.PROMOTED, destination, promoted);
    }

    @Test
    void strictExecutionRequiresAnActiveBindingForAnotherOwner() {
        var resource = DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan")
                .namespaceId(10L).envCode("prod").appCode("owner").kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).currentVersionId(303L).build();
        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "owner", DdcSiKindEnum.AGENT, "planner"))
                .thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> service.resolveExecutionResource(1L, "xingyuan", "default", "prod",
                "owner", "caller", DdcSiKindEnum.AGENT, "planner", 2L))
                .isInstanceOfSatisfying(CommonException.class, exception -> assertThat(exception.getCode()).isEqualTo(56403));
        verify(repository, never()).findHistoryVersionByResourceAndNo(any(), any(), any());
    }

    @Test
    void catalogProjectionOmitsTheBodyWhileAuthorizedExecutionKeepsTheExactSnapshot() throws Exception {
        var actual = org.mapstruct.factory.Mappers.getMapper(DdcSiResourceConverter.class);
        var resource = DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan")
                .namespaceCode("default").envCode("prod").appCode("").kind(DdcSiKindEnum.AGENT)
                .resourceKey("planner").lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE).specJson("{\"modelKey\":\"draft\"}").build();
        var pinned = storedVersion(301L, 1L, "{\"modelKey\":\"fixed\"}");
        var manifest = new ObjectMapper().treeToValue(pinned.getDependencyManifestJson(), DdcSiDependencyManifestVO.class);
        var metadata = actual.toCatalogDescriptor(resource, pinned);
        var executable = actual.toExecutionDescriptor(resource, pinned, manifest);

        assertThat(metadata.getSpecJson()).isNull();
        assertThat(metadata.getDependencyManifest()).isNull();
        assertThat(metadata.getDigestSha256()).isEqualTo(pinned.getDigestSha256());
        assertThat(executable.getOwnerAppCode()).isNull();
        assertThat(executable.getSpecJson()).isEqualTo(pinned.getSpecJson());
        assertThat(executable.isExecutionSnapshotShapeValid()).isTrue();
    }

    @Test
    void strictUnpinnedResolutionUsesThePhysicalCurrentIdWithoutProjection() {
        var resource = DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan")
                .namespaceId(10L).envCode("prod").appCode("").kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).currentVersionId(303L).build();
        var version = storedVersion(303L, 3L, "{\"modelKey\":\"current\"}");
        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "", DdcSiKindEnum.AGENT, "planner"))
                .thenReturn(Optional.of(resource));
        when(repository.findHistoryVersionsByIds(1L, List.of(303L))).thenReturn(List.of(version));
        var descriptor = new DdcSiResourceDescriptor().setResolvedVersionNo(3L);
        when(converter.toExecutionDescriptor(eq(resource), eq(version), any())).thenReturn(descriptor);

        assertThat(service.resolveExecutionResource(1L, "xingyuan", "default", "prod",
                null, "caller", DdcSiKindEnum.AGENT, "planner", null)).contains(descriptor);
        assertThat(resource.getNamespaceCode()).isEqualTo("default");
    }

    @Test
    void completeExecutionReadsFrozenBodiesOnceAndRequiresEachDependencyCurrentOwnerGrant() throws Exception {
        var json=new ObjectMapper();
        var root=DdcSiResourceBO.builder().id(101L).tenantId(1L).bizCode("xingyuan").namespaceId(10L)
                .envCode("prod").appCode("").kind(DdcSiKindEnum.AGENT).resourceKey("planner")
                .lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).currentVersionId(301L).build();
        var child=DdcSiResourceBO.builder().id(102L).tenantId(1L).bizCode("xingyuan").namespaceId(10L)
                .envCode("prod").appCode("prompt-owner").kind(DdcSiKindEnum.PROMPT).resourceKey("instructions")
                .lifecycleStatus(DdcSiLifecycleStatusEnum.DEPRECATED).specJson("{\"draft\":true}").build();
        var rootVersion=storedVersion(301L,1L,"{\"frozen\":\"agent\"}");
        var childBody="{\"frozen\":\"prompt\"}";
        var childIdentity=new VersionIdentityVO("1","102","302","4",DdcSiKindEnum.PROMPT,"prod",DdcSiContentStrategy.sha256Hex(childBody));
        var childVersion=DdcSiVersionBO.builder().id(302L).tenantId(1L).resourceId(102L).versionNo(4L)
                .kind(DdcSiKindEnum.PROMPT).envCode("prod").specJson(childBody).digestSha256(childIdentity.digestSha256())
                .dependencyManifestJson(json.valueToTree(DdcSiDependencyManifestVO.create(childIdentity,null,List.of(),List.of()))).build();
        var rootIdentity=json.treeToValue(rootVersion.getDependencyManifestJson(),DdcSiDependencyManifestVO.class).root();
        rootVersion.setDependencyManifestJson(json.valueToTree(DdcSiDependencyManifestVO.create(rootIdentity,null,List.of(childIdentity),
                List.of(new DdcSiDependencyManifestVO.EdgeVO("301","302")))));
        when(repository.resolveNamespaceId(1L,"xingyuan","default")).thenReturn(10L);
        when(repository.findByBusinessKey(1L,"xingyuan",10L,"prod","",DdcSiKindEnum.AGENT,"planner")).thenReturn(Optional.of(root));
        when(repository.findHistoryVersionByResourceAndNo(1L,101L,1L)).thenReturn(Optional.of(rootVersion));
        when(repository.findHistoryVersionsByIds(1L,List.of(302L))).thenReturn(List.of(childVersion));
        when(repository.findActiveById(1L,102L)).thenReturn(Optional.of(child));
        when(repository.findActiveBinding(1L,"xingyuan",10L,"prod","caller",DdcSiKindEnum.AGENT,"planner")).thenReturn(Optional.empty());
        when(repository.findActiveBinding(1L,"xingyuan",10L,"prod","caller",DdcSiKindEnum.PROMPT,"instructions"))
                .thenReturn(Optional.of(DdcSiBindingBO.builder().resourceId(102L).status(DdcSiBindingStatusEnum.ACTIVE).build()));
        var actual=org.mapstruct.factory.Mappers.getMapper(DdcSiResourceConverter.class);
        when(converter.toExecutionMaterial(any(),any(),any())).thenAnswer(call->actual.toExecutionMaterial(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
        var bundle=service.resolveExecutionBundle(1L,"xingyuan","default","prod",null,"caller",DdcSiKindEnum.AGENT,"planner",1L).orElseThrow();
        assertThat(bundle.nodes()).hasSize(1);assertThat(bundle.nodes().getFirst().specJson()).isEqualTo(childBody);
        assertThat(bundle.nodes().getFirst().ownerAppCode()).isEqualTo("prompt-owner");
        verify(repository).findHistoryVersionsByIds(1L,List.of(302L));
        verify(repository,never()).findHistoryVersionByResourceAndNo(1L,102L,4L);
        when(repository.findActiveBinding(1L,"xingyuan",10L,"prod","caller",DdcSiKindEnum.PROMPT,"instructions")).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.resolveExecutionBundle(1L,"xingyuan","default","prod",null,"caller",DdcSiKindEnum.AGENT,"planner",1L))
                .isInstanceOfSatisfying(CommonException.class,failure->assertThat(failure.getCode()).isEqualTo(56403));
        child.setLifecycleStatus(DdcSiLifecycleStatusEnum.ARCHIVED);
        assertThatThrownBy(()->service.resolveExecutionBundle(1L,"xingyuan","default","prod",null,"caller",DdcSiKindEnum.AGENT,"planner",1L))
                .isInstanceOf(CommonException.class);
        child.setLifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT);
        assertThatThrownBy(()->service.resolveExecutionBundle(1L,"xingyuan","default","prod",null,"caller",DdcSiKindEnum.AGENT,"planner",1L))
                .isInstanceOf(CommonException.class);
    }

    @Test void completeExecutionCannotReadDraftArchivedOrNonAgentRoot() {
        when(repository.resolveNamespaceId(1L,"xingyuan","default")).thenReturn(10L);
        var root=DdcSiResourceBO.builder().id(101L).lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT).build();
        when(repository.findByBusinessKey(1L,"xingyuan",10L,"prod","",DdcSiKindEnum.AGENT,"planner")).thenReturn(Optional.of(root));
        assertThat(service.resolveExecutionBundle(1L,"xingyuan","default","prod",null,"caller",DdcSiKindEnum.AGENT,"planner",1L)).isEmpty();
        root.setLifecycleStatus(DdcSiLifecycleStatusEnum.ARCHIVED);
        assertThat(service.resolveExecutionBundle(1L,"xingyuan","default","prod",null,"caller",DdcSiKindEnum.AGENT,"planner",1L)).isEmpty();
        assertThatThrownBy(()->service.resolveExecutionBundle(1L,"xingyuan","default","prod",null,"caller",DdcSiKindEnum.PROMPT,"planner",1L)).isInstanceOf(CommonException.class);
        verify(repository,never()).findHistoryVersionByResourceAndNo(any(),any(),any());
    }

    private static DdcSiVersionBO storedVersion(Long id, Long versionNo, String body) {
        return storedVersion(id, versionNo, body, "prod");
    }

    private static DdcSiVersionBO storedVersion(Long id, Long versionNo, String body, String env) {
        String digest = DdcSiContentStrategy.sha256Hex(body);
        var root = new VersionIdentityVO("1", "101", id.toString(), versionNo.toString(), DdcSiKindEnum.AGENT, env, digest);
        return DdcSiVersionBO.builder().id(id).tenantId(1L).resourceId(101L).versionNo(versionNo)
                .kind(DdcSiKindEnum.AGENT).envCode(env).specJson(body).digestSha256(digest)
                .dependencyManifestJson(new ObjectMapper().valueToTree(DdcSiDependencyManifestVO.create(root, null, List.of(), List.of())))
                .build();
    }

    private static final class DdcAiKindEnumHolder {
        private static final DdcSiKindEnum AGENT = DdcSiKindEnum.AGENT;
    }
}
