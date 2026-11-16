package top.egon.cola.component.tianshu.ai.si;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caching and debounced synchronization facade over the underlying SI catalog RPC client.
 * Preserves last-known-good snapshots when Tianshu is temporarily unreachable.
 */
@Slf4j
public class DdcSiCatalogSyncCoordinator implements DdcSiCatalogClient {

    private final DdcSiCatalogClient delegate;
    private final DdcSiCatalogCache cache;
    @Nullable
    private final RedissonClient redissonClient;
    private final DdcSiClientProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<DdcSiCatalogCache.ScopeKey, AtomicBoolean> pendingRefreshes =
            new ConcurrentHashMap<>();

    public DdcSiCatalogSyncCoordinator(
            @Qualifier("ddcSiCatalogRpcClient") DdcSiCatalogClient delegate,
            @Qualifier("ddcSiCatalogCache") DdcSiCatalogCache cache,
            @Nullable RedissonClient redissonClient,
            DdcSiClientProperties properties,
            Clock clock,
            @Qualifier("ddcSiSyncScheduler") ScheduledExecutorService scheduler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.redissonClient = redissonClient;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public List<DdcSiResourceDescriptor> listResources(DdcSiCatalogQuery query) {
        Objects.requireNonNull(query, "query");
        long tenantId = query.getTenantId();
        String revision = readRemoteRevision(tenantId, query.getBizCode(), query.getNamespaceCode(), query.getEnvCode());
        if (!cache.isStale(tenantId, query.getBizCode(), query.getNamespaceCode(), query.getEnvCode(),
                properties.getCacheTtl(), revision)) {
            List<DdcSiResourceDescriptor> cached = cache.listByScope(
                    tenantId, query.getBizCode(), query.getNamespaceCode(), query.getEnvCode());
            return filterCatalog(cached, query);
        }
        return refreshAndReturn(query, revision);
    }

    @Override
    public Optional<DdcSiResourceDescriptor> resolveResource(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            @Nullable String appCode,
            DdcSiKindEnum kind,
            String resourceKey,
            @Nullable Integer pinnedVersionNo) {
        String revision = readRemoteRevision(tenantId, bizCode, namespaceCode, envCode);
        if (!cache.isStale(tenantId, bizCode, namespaceCode, envCode, properties.getCacheTtl(), revision)) {
            Optional<DdcSiResourceDescriptor> hit = cache.getResource(
                    tenantId, bizCode, namespaceCode, envCode, kind, resourceKey, pinnedVersionNo);
            if (hit.isPresent()) {
                return hit;
            }
        }
        try {
            Optional<DdcSiResourceDescriptor> remote = delegate.resolveResource(
                    tenantId, bizCode, namespaceCode, envCode, appCode, kind, resourceKey, pinnedVersionNo);
            remote.ifPresent(item -> cache.evictOrUpdateOne(tenantId, bizCode, namespaceCode, envCode, item));
            return remote;
        } catch (RuntimeException failure) {
            log.warn("Tianshu SI resolve failed tenant={} env={} kind={} key={}; falling back to snapshot: {}",
                    tenantId, envCode, kind, resourceKey, failure.getMessage());
            return cache.getResource(tenantId, bizCode, namespaceCode, envCode, kind, resourceKey, pinnedVersionNo);
        }
    }

    @Override
    public List<DdcSiSearchHit> semanticSearch(DdcSiSemanticSearchQuery query) {
        return delegate.semanticSearch(query);
    }

    @Override
    public DdcSiResourceDescriptor registerManagedResource(DdcSiSelfRegistrationRequest request) {
        return delegate.registerManagedResource(request);
    }

    /** Schedules a debounced remote refresh for a scope, coalescing bursts within debounceWindow. */
    public void scheduleRefresh(long tenantId, String bizCode, String namespaceCode, String envCode) {
        DdcSiCatalogCache.ScopeKey key = new DdcSiCatalogCache.ScopeKey(tenantId, bizCode, namespaceCode, envCode);
        AtomicBoolean scheduled = pendingRefreshes.computeIfAbsent(key, ignored -> new AtomicBoolean(false));
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        long delayMs = Math.max(5L, properties.getDebounceWindow().toMillis());
        scheduler.schedule(() -> {
            scheduled.set(false);
            refreshScope(bizCode, namespaceCode, envCode, properties.getAppCode(), tenantId);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Synchronously refreshes a scope while preserving previous hot snapshot on remote failure. */
    public void refreshScope(String bizCode, String namespaceCode, String envCode, @Nullable String appCode) {
        Long tenantId = properties.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            return;
        }
        refreshScope(bizCode, namespaceCode, envCode, appCode, tenantId);
    }

    private void refreshScope(String bizCode, String namespaceCode, String envCode, @Nullable String appCode, long tenantId) {
        DdcSiCatalogQuery query = DdcSiCatalogQuery.builder()
                .tenantId(tenantId)
                .bizCode(bizCode)
                .namespaceCode(namespaceCode)
                .envCode(envCode)
                .appCode(appCode)
                .pageNo(1)
                .pageSize(500)
                .build();
        String revision = readRemoteRevision(tenantId, bizCode, namespaceCode, envCode);
        refreshAndReturn(query, revision);
    }

    private List<DdcSiResourceDescriptor> refreshAndReturn(DdcSiCatalogQuery query, @Nullable String revision) {
        try {
            List<DdcSiResourceDescriptor> fresh = delegate.listResources(query);
            cache.putAllSnapshot(query.getTenantId(), query.getBizCode(),
                    query.getNamespaceCode(), query.getEnvCode(), fresh, revision);
            return filterCatalog(fresh, query);
        } catch (RuntimeException failure) {
            log.warn("Tianshu SI catalog refresh failed tenant={} biz={} ns={} env={} time={}: {}; keeping last known good snapshot",
                    query.getTenantId(), query.getBizCode(), query.getNamespaceCode(),
                    query.getEnvCode(), clock.instant(), failure.getMessage());
            return filterCatalog(cache.listByScope(
                    query.getTenantId(), query.getBizCode(), query.getNamespaceCode(), query.getEnvCode()), query);
        }
    }

    @Nullable
    private String readRemoteRevision(long tenantId, String bizCode, String namespaceCode, String envCode) {
        if (redissonClient == null || bizCode == null || namespaceCode == null || envCode == null) {
            return null;
        }
        try {
            String key = DdcRedisKeys.siCatalogVersionKey(tenantId, bizCode, namespaceCode, envCode);
            RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
            return bucket == null ? null : bucket.get();
        } catch (RuntimeException failure) {
            log.debug("Failed reading SI Redis version pointer: {}", failure.getMessage());
            return null;
        }
    }

    private List<DdcSiResourceDescriptor> filterCatalog(
            List<DdcSiResourceDescriptor> items,
            DdcSiCatalogQuery query) {
        return items.stream()
                .filter(item -> query.getKind() == null || query.getKind() == item.getKind())
                .filter(item -> query.getResourceKeys() == null || query.getResourceKeys().isEmpty()
                        || query.getResourceKeys().contains(item.getResourceKey()))
                .toList();
    }
}
