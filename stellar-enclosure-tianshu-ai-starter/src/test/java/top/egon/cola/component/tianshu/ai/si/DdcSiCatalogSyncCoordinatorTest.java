package top.egon.cola.component.tianshu.ai.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.component.tianshu.ai.autoconfigure.DdcAiStarterAutoConfiguration;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiPublicationEvent;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcSiCatalogSyncCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void usesHotSnapshotPreservesLastKnownGoodOnFailureAndCoalescesBurstEvents() throws Exception {
        DdcSiCatalogClient delegate = mock(DdcSiCatalogClient.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        String redisKey = DdcRedisKeys.siCatalogVersionKey(1001L, "ai", "core", "prod");
        when(redisson.<String>getBucket(redisKey, StringCodec.INSTANCE)).thenReturn(bucket);
        when(bucket.get()).thenReturn("5");

        DdcSiResourceDescriptor agentV1 = descriptor("order-agent", 1);
        DdcSiResourceDescriptor agentV2 = descriptor("order-agent", 2);
        when(delegate.listResources(any())).thenReturn(List.of(agentV1), List.of(agentV2));
        when(delegate.resolveResource(1001L, "ai", "core", "prod", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", null))
                .thenReturn(Optional.of(agentV1));
        when(delegate.semanticSearch(any())).thenReturn(List.of(
                DdcSiSearchHit.builder().resource(agentV1).hybridScore(0.95).rank(1).build()));

        DdcSiClientProperties props = new DdcSiClientProperties()
                .setEnabled(true)
                .setTenantId(1001L)
                .setBizCode("ai")
                .setNamespaceCode("core")
                .setEnvCode("prod")
                .setAppCode("tianji")
                .setCacheTtl(Duration.ofSeconds(60))
                .setDebounceWindow(Duration.ofMillis(80));
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        DdcSiCatalogCache cache = new DdcSiCatalogCache(fixedClock);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            DdcSiCatalogSyncCoordinator coordinator = new DdcSiCatalogSyncCoordinator(
                    delegate, cache, redisson, props, fixedClock, scheduler);

            // Initial list warms hot snapshot cache
            DdcSiCatalogQuery query = DdcSiCatalogQuery.builder()
                    .tenantId(1001L).bizCode("ai").namespaceCode("core").envCode("prod").build();
            assertThat(coordinator.listResources(query)).hasSize(1);
            assertThat(coordinator.listResources(query)).hasSize(1);
            verify(delegate, times(1)).listResources(any());

            // Resolve serves from cache without extra RPC
            assertThat(coordinator.resolveResource(1001L, "ai", "core", "prod", "tianji",
                    DdcSiKindEnum.AGENT, "order-agent", null)).isPresent();
            verify(delegate, times(0)).resolveResource(1001L, "ai", "core", "prod", "tianji",
                    DdcSiKindEnum.AGENT, "order-agent", null);

            // Semantic search always passes through to backend
            assertThat(coordinator.semanticSearch(DdcSiSemanticSearchQuery.builder()
                    .tenantId(1001L).bizCode("ai").namespaceCode("core").envCode("prod")
                    .queryText("order").topK(5).build())).hasSize(1);
            verify(delegate, times(1)).semanticSearch(any());

            // Burst events + duplicate eventIds + foreign tenant event are coalesced/ignored
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            DdcSiPublicationListener listener = new DdcSiPublicationListener(mapper, coordinator, props);
            listener.onCatalogPublicationEvent(event("evt-foreign", 2002L, "prod", 10L));
            for (int i = 0; i < 8; i++) {
                listener.onCatalogPublicationEvent(event("evt-same", 1001L, "prod", 6L));
            }
            listener.onCatalogPublicationEvent(event("evt-next-1", 1001L, "prod", 7L));
            listener.onCatalogPublicationEvent(event("evt-next-2", 1001L, "prod", 8L));
            Thread.sleep(200);
            verify(delegate, times(2)).listResources(any());

            // Fail-safe: backend failure preserves last-known-good hot snapshot
            when(delegate.listResources(any())).thenThrow(new IllegalStateException("tianshu unavailable"));
            coordinator.refreshScope("ai", "core", "prod", "tianji");
            assertThat(coordinator.listResources(query)).hasSize(1);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void whenSiClientDisabledNoSiClientBeansAreRegistered() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DdcAiStarterAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DdcSiCatalogCache.class);
                    assertThat(context).doesNotHaveBean(DdcSiCatalogSyncCoordinator.class);
                    assertThat(context).doesNotHaveBean(DdcSiPublicationListener.class);
                    assertThat(context).doesNotHaveBean(DdcSiSelfRegistrationRunner.class);
                });
    }

    private DdcSiResourceDescriptor descriptor(String key, int version) {
        return DdcSiResourceDescriptor.builder()
                .tenantId(1001L).bizCode("ai").namespaceCode("core").envCode("prod")
                .resourceId(910001L).kind(DdcSiKindEnum.AGENT).resourceKey(key)
                .displayName("Order Agent").status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE).activeVersionNo(version)
                .digestSha256("a".repeat(64)).publishedAt(NOW).updatedAt(NOW).build();
    }

    private DdcSiPublicationEvent event(String eventId, long tenantId, String envCode, long revision) {
        return DdcSiPublicationEvent.builder()
                .eventId(eventId)
                .eventType(DdcSiPublicationEvent.EventType.PUBLISHED)
                .tenantId(tenantId)
                .bizCode("ai")
                .namespaceCode("core")
                .envCode(envCode)
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("order-agent")
                .activeVersionNo(2)
                .catalogRevision(revision)
                .occurredAt(NOW)
                .build();
    }
}
