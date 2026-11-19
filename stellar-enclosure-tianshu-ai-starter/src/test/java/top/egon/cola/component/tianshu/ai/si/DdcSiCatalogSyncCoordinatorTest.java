package top.egon.cola.component.tianshu.ai.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.component.tianshu.ai.autoconfigure.DdcAiStarterAutoConfiguration;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.*;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcSiCatalogSyncCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DdcSiCatalogClient delegate = mock(DdcSiCatalogClient.class);
    private final DdcSiCatalogCache cache = new DdcSiCatalogCache(clock);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final DdcSiClientProperties props = new DdcSiClientProperties().setEnabled(true).setTenantId(1001L)
            .setBizCode("ai").setNamespaceCode("core").setEnvCode("prod").setAppCode("tianji")
            .setCacheTtl(Duration.ofMinutes(1)).setDebounceWindow(Duration.ofMillis(80));

    @Test void completeExecutionBundleNeverUsesWarmMetadataAndRechecksLiveAuthorityEveryTime() throws Exception {
        var identity=new DdcSiDependencyManifestVO.VersionIdentityVO("1001","1","2","3",DdcSiKindEnum.AGENT,"prod",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("{}".getBytes(StandardCharsets.UTF_8))));
        var bundle=new DdcSiExecutionBundleVO(new DdcSiExecutionBundleVO.MaterialVO(identity,"ai","core",null,"order-agent","{}"),
                DdcSiDependencyManifestVO.create(identity,null,List.of(),List.of()),List.of());
        when(delegate.resolveExecutionBundle(1001,"ai","core","prod",null,"tianji",DdcSiKindEnum.AGENT,"order-agent",3L))
                .thenReturn(Optional.of(bundle)).thenThrow(new SecurityException("revoked"));
        var coordinator=coordinator(null);
        assertThat(coordinator.resolveExecutionBundle(1001,"ai","core","prod",null,"tianji",DdcSiKindEnum.AGENT,"order-agent",3L)).contains(bundle);
        assertThatThrownBy(()->coordinator.resolveExecutionBundle(1001,"ai","core","prod",null,"tianji",DdcSiKindEnum.AGENT,"order-agent",3L)).isInstanceOf(SecurityException.class);
        verify(delegate,times(2)).resolveExecutionBundle(1001,"ai","core","prod",null,"tianji",DdcSiKindEnum.AGENT,"order-agent",3L);
        verify(delegate,never()).resolveResource(anyLong(),anyString(),anyString(),anyString(),any(),any(),anyString(),any());
        verify(delegate,never()).resolveExecutionResource(anyLong(),anyString(),anyString(),anyString(),any(),anyString(),any(),anyString(),any());
    }

    @Test
    void warmCatalogStillRequiresCurrentAuthorizationAndDoesNotFallbackForFailure() {
        var coordinator = coordinator(null);
        var query = query();
        when(delegate.listResources(query)).thenReturn(List.of(descriptor("order-agent", 1)));
        assertThat(coordinator.listResources(query)).hasSize(1);
        assertThat(coordinator.listResources(query)).hasSize(1);
        verify(delegate, times(2)).listResources(query);
        var denied = new SecurityException("scope denied");
        when(delegate.listResources(query)).thenThrow(denied);
        assertThatThrownBy(() -> coordinator.listResources(query)).isSameAs(denied);
        assertThat(cache.listByScope(1001L, "ai", "core", "prod")).hasSize(1);
        var unavailable = new IllegalStateException("authority unavailable");
        doThrow(unavailable).when(delegate).listResources(query);
        assertThatThrownBy(() -> coordinator.listResources(query)).isSameAs(unavailable);
    }

    @Test
    void warmLegacyResolveDoesNotBypassRevocationOrTurnMissingVersionIntoCurrent() {
        var coordinator = coordinator(null);
        cache.putAllSnapshot(1001L, "ai", "core", "prod", List.of(descriptor("order-agent", 2)), "6");
        when(delegate.resolveResource(1001L, "ai", "core", "prod", "tianji", DdcSiKindEnum.AGENT,
                "order-agent", 1)).thenReturn(Optional.empty());
        assertThat(coordinator.resolveResource(1001L, "ai", "core", "prod", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1)).isEmpty();
        var denied = new SecurityException("revoked");
        when(delegate.resolveResource(1001L, "ai", "core", "prod", "tianji", DdcSiKindEnum.AGENT,
                "order-agent", null)).thenThrow(denied);
        assertThatThrownBy(() -> coordinator.resolveResource(1001L, "ai", "core", "prod", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", null)).isSameAs(denied);
    }

    @Test
    void exactExecutionDelegatesOwnerCallerAndLongVersionOnEveryInvocation() throws Exception {
        var coordinator = coordinator(null);
        var v1 = execution("owner-a", 1L);
        var v2 = execution("owner-a", 2L);
        when(delegate.resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1L)).thenReturn(Optional.of(v1));
        when(delegate.resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 2L)).thenReturn(Optional.of(v2));
        assertThat(resolve(coordinator, "owner-a", 1L).orElseThrow().getResolvedVersionNo()).isEqualTo(1L);
        assertThat(resolve(coordinator, "owner-a", 2L).orElseThrow().getResolvedVersionNo()).isEqualTo(2L);
        assertThat(resolve(coordinator, "owner-a", 1L).orElseThrow().getDependencyManifest()).isEqualTo(v1.getDependencyManifest());
        verify(delegate, times(2)).resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1L);
        verify(delegate, never()).resolveResource(anyLong(), anyString(), anyString(), anyString(), any(), any(), anyString(), any());
        var large = execution("owner-a", (long) Integer.MAX_VALUE + 7L);
        when(delegate.resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", large.getResolvedVersionNo())).thenReturn(Optional.of(large));
        assertThat(resolve(coordinator, "owner-a", large.getResolvedVersionNo()).orElseThrow().getResolvedVersionNo())
                .isEqualTo((long) Integer.MAX_VALUE + 7L);
    }

    @Test
    void exactExecutionNeverUsesCachedProofWhenAuthorizationUnavailableDeniedOrHistoryMissing() throws Exception {
        var coordinator = coordinator(null);
        var exact = execution("owner-a", 1L);
        when(delegate.resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1L)).thenReturn(Optional.of(exact));
        assertThat(resolve(coordinator, "owner-a", 1L)).isPresent();
        var denied = new SecurityException("revoked");
        when(delegate.resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1L)).thenThrow(denied);
        assertThatThrownBy(() -> resolve(coordinator, "owner-a", 1L)).isSameAs(denied);
        var unavailable = new IllegalStateException("unavailable");
        doThrow(unavailable).when(delegate).resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1L);
        assertThatThrownBy(() -> resolve(coordinator, "owner-a", 1L)).isSameAs(unavailable);
        doReturn(Optional.empty()).when(delegate).resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1L);
        assertThatThrownBy(() -> resolve(coordinator, "owner-a", 1L)).isInstanceOf(IllegalStateException.class)
                .hasMessage("SI_PINNED_HISTORY_UNAVAILABLE");
    }

    @Test
    void mismatchedOrIncompleteExecutionProofCannotBeReturnedOrUsedAsMetadataFallback() throws Exception {
        var coordinator = coordinator(null);
        var valid = execution("owner-a", 1L);
        var wrongOwner = execution("owner-b", 1L);
        var wrongVersion = execution("owner-a", 2L);
        var missingManifest = execution("owner-a", 1L).setDependencyManifest(null);
        var badBody = execution("owner-a", 1L).setSpecJson("{\"tampered\":true}");
        for (var invalid : List.of(wrongOwner, wrongVersion, missingManifest, badBody)) {
            when(delegate.resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                    DdcSiKindEnum.AGENT, "order-agent", 1L)).thenReturn(Optional.of(invalid));
            assertThatThrownBy(() -> resolve(coordinator, "owner-a", 1L)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("SI_PINNED_HISTORY_UNAVAILABLE");
        }
        when(delegate.resolveExecutionResource(1001L, "ai", "core", "prod", "owner-a", "tianji",
                DdcSiKindEnum.AGENT, "order-agent", 1L)).thenReturn(Optional.of(valid));
        assertThat(resolve(coordinator, "owner-a", 1L)).isPresent();
    }

    @Test
    void appOwnedMetadataCannotSatisfyANamespaceOwnedCacheLookup() {
        cache.putAllSnapshot(1001L, "ai", "core", "prod", List.of(
                descriptor("order-agent", 1).setOwnerAppCode("owner-a"),
                descriptor("order-agent", 2).setOwnerAppCode("owner-b")), "5");
        assertThat(cache.getResource(1001L, "ai", "core", "prod", DdcSiKindEnum.AGENT, "order-agent", null)).isEmpty();
        assertThat(cache.listByScope(1001L, "ai", "core", "prod")).hasSize(2);
        assertThat(cache.getResource(1001L, "ai", "core", "prod", "owner-a", DdcSiKindEnum.AGENT, "order-agent", 1))
                .get().extracting(DdcSiResourceDescriptor::getOwnerAppCode).isEqualTo("owner-a");
        assertThat(cache.getResource(1001L, "ai", "core", "prod", "owner-b", DdcSiKindEnum.AGENT, "order-agent", 2))
                .get().extracting(DdcSiResourceDescriptor::getOwnerAppCode).isEqualTo("owner-b");
        assertThat(cache.getResource(1001L, "ai", "core", "prod", "owner-a", DdcSiKindEnum.AGENT, "order-agent", 2)).isEmpty();
    }

    @Test
    void exactHistoryCoexistsByOwnerCallerVersionDigestAndClosureWithoutChangingMetadataHead() throws Exception {
        var v1 = execution("owner-a", 1L);
        var v2 = execution("owner-a", 2L);
        var otherOwner = execution("owner-b", 1L);
        cache.putAllSnapshot(1001L, "ai", "core", "prod", List.of(descriptor("order-agent", 9).setOwnerAppCode("owner-a")), "new-head");
        for (var item : List.of(v1, v2, otherOwner)) cache.putExecutionSnapshot("tianji", item);
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("tianji", v1)))
                .get().extracting(DdcSiResourceDescriptor::getResolvedVersionNo).isEqualTo(1L);
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("tianji", v2)))
                .get().extracting(DdcSiResourceDescriptor::getResolvedVersionNo).isEqualTo(2L);
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("tianji", otherOwner)))
                .get().extracting(DdcSiResourceDescriptor::getOwnerAppCode).isEqualTo("owner-b");
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("other-consumer", v1))).isEmpty();
        var key = DdcSiCatalogCache.executionKey("tianji", v1);
        var wrongContent = new DdcSiCatalogCache.ExecutionKey(key.callerAppCode(), key.tenantId(), key.bizCode(), key.namespaceCode(),
                key.envCode(), key.ownerAppCode(), key.kind(), key.resourceKey(), key.versionId(), key.versionNo(), "f".repeat(64), key.closureDigest());
        var wrongClosure = new DdcSiCatalogCache.ExecutionKey(key.callerAppCode(), key.tenantId(), key.bizCode(), key.namespaceCode(),
                key.envCode(), key.ownerAppCode(), key.kind(), key.resourceKey(), key.versionId(), key.versionNo(), key.digestSha256(), "f".repeat(64));
        assertThat(cache.getExecutionSnapshot(wrongContent)).isEmpty();
        assertThat(cache.getExecutionSnapshot(wrongClosure)).isEmpty();
        assertThat(cache.getResource(1001L, "ai", "core", "prod", "owner-a", DdcSiKindEnum.AGENT, "order-agent", 9)).isPresent();
        assertThat(cache.currentRevision(1001L, "ai", "core", "prod")).contains("new-head");
        v1.setSpecJson("mutated producer");
        var loaded = cache.getExecutionSnapshot(key).orElseThrow();
        assertThat(loaded.getSpecJson()).isEqualTo("{\"version\":1}");
        loaded.setSpecJson("mutated consumer");
        assertThat(cache.getExecutionSnapshot(key).orElseThrow().getSpecJson()).isEqualTo("{\"version\":1}");
    }

    @Test
    void historicalRetentionIsGloballyBoundedAndRefreshesOnlyTheRecentEntry() throws Exception {
        var first = execution("owner-a", 1L);
        var second = execution("owner-a", 2L);
        for (long version = 1; version <= 64; version++) cache.putExecutionSnapshot("tianji", execution("owner-a", version));
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("tianji", first))).isPresent();
        var newest = execution("owner-b", 65L);
        cache.putExecutionSnapshot("other-consumer", newest);
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("tianji", second))).isEmpty();
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("tianji", first))).isPresent();
        assertThat(cache.getExecutionSnapshot(DdcSiCatalogCache.executionKey("other-consumer", newest))).isPresent();
        assertThat(cache.listByScope(1001L, "ai", "core", "prod")).isEmpty();
    }

    @Test
    void cacheOwnsDeepSnapshotsAndNeverLeaksMutableDescriptorMapsOrReferences() {
        var original = descriptor("order-agent", 1).setLabels(new LinkedHashMap<>(java.util.Map.of("team", "orders")))
                .setReferences(new ArrayList<>(List.of(DdcSiReferenceDescriptor.builder()
                        .targetType(DdcSiReferenceTargetTypeEnum.PROMPT).targetKey("original").pinnedVersionNo(1).build())));
        cache.putAllSnapshot(1001L, "ai", "core", "prod", List.of(original), "5");
        original.setDisplayName("mutated input");
        original.getLabels().put("team", "wrong");
        original.getReferences().getFirst().setTargetKey("wrong");
        var first = cache.getResource(1001L, "ai", "core", "prod", DdcSiKindEnum.AGENT, "order-agent", 1).orElseThrow();
        assertThat(first.getDisplayName()).isEqualTo("Order Agent");
        assertThat(first.getLabels()).containsEntry("team", "orders");
        assertThat(first.getReferences().getFirst().getTargetKey()).isEqualTo("original");
        first.setDisplayName("mutated output");
        first.getReferences().getFirst().setTargetKey("output change");
        assertThat(cache.listByScope(1001L, "ai", "core", "prod").getFirst().getDisplayName()).isEqualTo("Order Agent");
        assertThat(cache.listByScope(1001L, "ai", "core", "prod").getFirst().getReferences().getFirst().getTargetKey()).isEqualTo("original");
    }

    @Test
    void publicationHintsRemainDebouncedAndSemanticSearchPassesThrough() throws Exception {
        var coordinator = coordinator(null);
        when(delegate.listResources(any())).thenReturn(List.of(descriptor("order-agent", 2)));
        var listener = new DdcSiPublicationListener(new ObjectMapper().registerModule(new JavaTimeModule()), coordinator, props);
        listener.onCatalogPublicationEvent(event("foreign", 2002L));
        for (int i = 0; i < 8; i++) listener.onCatalogPublicationEvent(event("same", 1001L));
        listener.onCatalogPublicationEvent(event("next", 1001L));
        var scheduled = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(scheduled.capture(), eq(80L), eq(TimeUnit.MILLISECONDS));
        scheduled.getValue().run();
        verify(delegate).listResources(any());
        var query = DdcSiSemanticSearchQuery.builder().tenantId(1001L).bizCode("ai").namespaceCode("core")
                .envCode("prod").queryText("order").topK(5).build();
        var result = List.of(DdcSiSearchHit.builder().resource(descriptor("order-agent", 2)).hybridScore(.95).rank(1).build());
        when(delegate.semanticSearch(query)).thenReturn(result);
        assertThat(coordinator.semanticSearch(query)).isEqualTo(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisRevisionIsOnlyAHintAndCannotReplaceRemoteAuthorization() {
        RedissonClient redisson = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(redisson.<String>getBucket(DdcRedisKeys.siCatalogVersionKey(1001L, "ai", "core", "prod"), StringCodec.INSTANCE)).thenReturn(bucket);
        when(bucket.get()).thenReturn("unchanged");
        var coordinator = coordinator(redisson);
        when(delegate.listResources(any())).thenReturn(List.of(descriptor("order-agent", 1)));
        coordinator.listResources(query());
        coordinator.listResources(query());
        verify(delegate, times(2)).listResources(any());
    }

    @Test
    void whenSiClientDisabledNoSiClientBeansAreRegistered() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DdcAiStarterAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DdcSiCatalogCache.class);
                    assertThat(context).doesNotHaveBean(DdcSiCatalogSyncCoordinator.class);
                    assertThat(context).doesNotHaveBean(DdcSiPublicationListener.class);
                    assertThat(context).doesNotHaveBean(DdcSiSelfRegistrationRunner.class);
                });
    }

    private DdcSiCatalogSyncCoordinator coordinator(RedissonClient redisson) {
        return new DdcSiCatalogSyncCoordinator(delegate, cache, redisson, props, clock, scheduler);
    }
    private DdcSiCatalogQuery query() {
        return DdcSiCatalogQuery.builder().tenantId(1001L).bizCode("ai").namespaceCode("core").envCode("prod").build();
    }
    private Optional<DdcSiResourceDescriptor> resolve(DdcSiCatalogSyncCoordinator coordinator, String owner, Long version) {
        return coordinator.resolveExecutionResource(1001L, "ai", "core", "prod", owner, "tianji", DdcSiKindEnum.AGENT, "order-agent", version);
    }
    private DdcSiResourceDescriptor descriptor(String key, int version) {
        return DdcSiResourceDescriptor.builder().tenantId(1001L).bizCode("ai").namespaceCode("core").envCode("prod")
                .resourceId(910001L).kind(DdcSiKindEnum.AGENT).resourceKey(key).displayName("Order Agent")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED).sourceType(DdcSiSourceTypeEnum.CONSOLE).activeVersionNo(version)
                .digestSha256("a".repeat(64)).publishedAt(NOW).updatedAt(NOW).build();
    }
    private DdcSiResourceDescriptor execution(String owner, long version) throws Exception {
        String body = "{\"version\":" + version + "}";
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        long versionId = 10000L + version;
        var root = new DdcSiDependencyManifestVO.VersionIdentityVO("1001", "910001", Long.toString(versionId),
                Long.toString(version), DdcSiKindEnum.AGENT, "prod", digest);
        return descriptor("order-agent", 9).setOwnerAppCode(owner).setSpecJson(body).setDigestSha256(digest)
                .setResolvedVersionId(versionId).setResolvedVersionNo(version)
                .setDependencyManifest(DdcSiDependencyManifestVO.create(root, null, List.of(), List.of()));
    }
    private DdcSiPublicationEvent event(String eventId, long tenantId) {
        return DdcSiPublicationEvent.builder().eventId(eventId).eventType(DdcSiPublicationEvent.EventType.PUBLISHED)
                .tenantId(tenantId).bizCode("ai").namespaceCode("core").envCode("prod").kind(DdcSiKindEnum.AGENT)
                .resourceKey("order-agent").activeVersionNo(2).catalogRevision(6L).occurredAt(NOW).build();
    }
}
