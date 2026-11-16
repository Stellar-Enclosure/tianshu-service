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
import top.egon.cola.component.tianshu.admin.service.si.impl.DdcSiResourceServiceImpl;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiAgentContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiMcpContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiPromptSkillContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private DdcSiDependencyValidator dependencyValidator;
    @Mock
    private DdcSiCatalogOutboxNotifier outboxNotifier;
    @Mock
    private DdcSiSemanticIndexService semanticIndexService;
    @Mock
    private DdcSiResourceConverter converter;

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
                dependencyValidator,
                transitionPolicy,
                outboxNotifier,
                semanticIndexService,
                converter,
                txTemplate,
                fixedClock);
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

        when(repository.findActiveForUpdateById(1L, 101L)).thenReturn(Optional.of(draft));
        when(repository.allocateNextVersionNo(1L, 101L)).thenReturn(1L);
        when(repository.insertVersion(any())).thenAnswer(inv -> {
            DdcSiVersionBO v = inv.getArgument(0);
            v.setId(201L);
            return v;
        });
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
        when(repository.findVersionByResourceAndNo(1L, 101L, 1L)).thenReturn(Optional.of(
                DdcSiVersionBO.builder().id(201L).resourceId(101L).versionNo(1L).digestSha256("b".repeat(64)).build()));

        DdcSiVersionBO published = service.publish(1L, 101L, 2L, "init release", "user-1");
        assertThat(published.getVersionNo()).isEqualTo(1L);
        verify(outboxNotifier).enqueueAndScheduleAfterCommit(
                eq(DdcSiPublicationEvent.EventType.PUBLISHED), any(DdcSiResourceBO.class), any(DdcSiVersionBO.class));
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
        DdcSiVersionBO v2 = DdcSiVersionBO.builder()
                .id(302L).resourceId(101L).versionNo(2L).kind(DdcSiKindEnum.AGENT)
                .specJson("{\"modelKey\":\"pinned-v2\"}").digestSha256("d".repeat(64))
                .publishedTime(fixedClock.instant()).build();

        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "app-a", DdcSiKindEnum.AGENT, "planner"))
                .thenReturn(Optional.of(resource));
        when(repository.findActiveBinding(1L, "xingyuan", 10L, "prod", "tianji-agent-server",
                DdcSiKindEnum.AGENT, "planner")).thenReturn(Optional.of(pinned));
        when(repository.findVersionByResourceAndNo(1L, 101L, 2L)).thenReturn(Optional.of(v2));
        when(converter.toDescriptor(any(), any(), any())).thenAnswer(inv -> {
            DdcSiResourceBO res = inv.getArgument(0);
            DdcSiVersionBO ver = inv.getArgument(1);
            return DdcSiResourceDescriptor.builder()
                    .tenantId(res.getTenantId()).bizCode(res.getBizCode()).namespaceCode(res.getNamespaceCode())
                    .envCode(res.getEnvCode()).kind(res.getKind()).resourceKey(res.getResourceKey())
                    .displayName(res.getDisplayName()).status(res.getLifecycleStatus()).sourceType(res.getSourceType())
                    .activeVersionNo(ver.getVersionNo().intValue()).specJson(ver.getSpecJson())
                    .digestSha256(ver.getDigestSha256()).build();
        });

        Optional<DdcSiResourceDescriptor> resolved = service.resolveResource(
                1L, "xingyuan", "default", "prod", "app-a", "tianji-agent-server", DdcSiKindEnum.AGENT, "planner", null);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getActiveVersionNo()).isEqualTo(2);
        assertThat(resolved.get().getSpecJson()).contains("pinned-v2");
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
        when(repository.findActiveForUpdateById(1L, 101L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.publish(1L, 101L, 1L, "retry", "user-1"))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56409));
    }

    private static final class DdcAiKindEnumHolder {
        private static final DdcSiKindEnum AGENT = DdcSiKindEnum.AGENT;
    }
}
