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
import top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO;
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
 * Authorization-preserving catalog facade. Every successful read comes from the delegate's
 * current authorization; cached artifacts and Redis revisions never grant offline access.
 */
@Slf4j
public class DdcSiCatalogSyncCoordinator implements DdcSiCatalogClient {
    private final DdcSiCatalogClient delegate;
    private final DdcSiCatalogCache cache;
    @Nullable private final RedissonClient redissonClient;
    private final DdcSiClientProperties properties;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<DdcSiCatalogCache.ScopeKey, AtomicBoolean> pendingRefreshes = new ConcurrentHashMap<>();

    public DdcSiCatalogSyncCoordinator(@Qualifier("ddcSiCatalogRpcClient") DdcSiCatalogClient delegate,
            @Qualifier("ddcSiCatalogCache") DdcSiCatalogCache cache, @Nullable RedissonClient redissonClient,
            DdcSiClientProperties properties, Clock clock,
            @Qualifier("ddcSiSyncScheduler") ScheduledExecutorService scheduler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.redissonClient = redissonClient;
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public List<DdcSiResourceDescriptor> listResources(DdcSiCatalogQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.getTenantId() == null || query.getTenantId() <= 0) throw new IllegalArgumentException("SI_CATALOG_SCOPE_INVALID");
        DdcSiCatalogCache.coordinate(query.getBizCode());
        DdcSiCatalogCache.coordinate(query.getNamespaceCode());
        DdcSiCatalogCache.coordinate(query.getEnvCode());
        if (query.getAppCode() != null) DdcSiCatalogCache.coordinate(query.getAppCode());
        String revision = readRemoteRevision(query.getTenantId(), query.getBizCode(), query.getNamespaceCode(), query.getEnvCode());
        // A live read is intentional: this port has no independent current-authorization proof API.
        List<DdcSiResourceDescriptor> fresh = Objects.requireNonNull(delegate.listResources(query), "catalog result");
        cache.putAllSnapshot(query.getTenantId(), query.getBizCode(), query.getNamespaceCode(), query.getEnvCode(), fresh, revision);
        return fresh.stream().map(DdcSiCatalogCache::copy)
                .filter(item -> query.getKind() == null || query.getKind() == item.getKind())
                .filter(item -> query.getResourceKeys() == null || query.getResourceKeys().isEmpty()
                        || query.getResourceKeys().contains(item.getResourceKey())).toList();
    }

    @Override
    public Optional<DdcSiResourceDescriptor> resolveResource(long tenantId, String bizCode, String namespaceCode,
            String envCode, @Nullable String appCode, DdcSiKindEnum kind, String resourceKey, @Nullable Integer pinnedVersionNo) {
        requireRequest(tenantId, bizCode, namespaceCode, envCode, kind, resourceKey,
                pinnedVersionNo == null ? null : pinnedVersionNo.longValue());
        if (appCode != null) DdcSiCatalogCache.coordinate(appCode);
        Optional<DdcSiResourceDescriptor> remote = Objects.requireNonNull(delegate.resolveResource(
                tenantId, bizCode, namespaceCode, envCode, appCode, kind, resourceKey, pinnedVersionNo), "resolve result");
        return remote.map(item -> {
            DdcSiResourceDescriptor result = DdcSiCatalogCache.copy(item);
            requireResponseScope(result, tenantId, bizCode, namespaceCode, envCode, kind, resourceKey);
            Long selected = result.getResolvedVersionNo() != null ? result.getResolvedVersionNo()
                    : result.getActiveVersionNo() == null ? null : result.getActiveVersionNo().longValue();
            if (pinnedVersionNo != null && !Objects.equals(pinnedVersionNo.longValue(), selected)) throw DdcSiCatalogCache.historyUnavailable();
            // Historical reads must not replace the active metadata head.
            if (pinnedVersionNo == null) cache.evictOrUpdateOne(tenantId, bizCode, namespaceCode, envCode, result);
            return DdcSiCatalogCache.copy(result);
        });
    }

    @Override
    public Optional<DdcSiResourceDescriptor> resolveExecutionResource(long tenantId, String bizCode, String namespaceCode,
            String envCode, @Nullable String ownerAppCode, String callerAppCode, DdcSiKindEnum kind,
            String resourceKey, @Nullable Long pinnedVersionNo) {
        requireRequest(tenantId, bizCode, namespaceCode, envCode, kind, resourceKey, pinnedVersionNo);
        DdcSiCatalogCache.coordinate(callerAppCode);
        if (ownerAppCode != null) DdcSiCatalogCache.coordinate(ownerAppCode);
        // Never use the legacy resolve port or Redis pointer to satisfy an execution request.
        Optional<DdcSiResourceDescriptor> remote = Objects.requireNonNull(delegate.resolveExecutionResource(
                tenantId, bizCode, namespaceCode, envCode, ownerAppCode, callerAppCode, kind, resourceKey, pinnedVersionNo), "execution result");
        if (remote.isEmpty()) {
            if (pinnedVersionNo != null) throw DdcSiCatalogCache.historyUnavailable();
            return Optional.empty();
        }
        DdcSiResourceDescriptor result = DdcSiCatalogCache.verifiedExecution(remote.orElseThrow());
        requireResponseScope(result, tenantId, bizCode, namespaceCode, envCode, kind, resourceKey);
        if (!Objects.equals(ownerAppCode, result.getOwnerAppCode())
                || pinnedVersionNo != null && !pinnedVersionNo.equals(result.getResolvedVersionNo())) {
            throw DdcSiCatalogCache.historyUnavailable();
        }
        cache.putExecutionSnapshot(callerAppCode, result);
        return Optional.of(DdcSiCatalogCache.copy(result));
    }

    /** A material bundle is always freshly authorized by the native authority, never supplied by local/Redis cache. */
    @Override
    public Optional<DdcSiExecutionBundleVO> resolveExecutionBundle(long tenantId, String bizCode, String namespaceCode,
            String envCode, @Nullable String ownerAppCode, String callerAppCode, DdcSiKindEnum kind,
            String resourceKey, @Nullable Long pinnedVersionNo) {
        requireRequest(tenantId, bizCode, namespaceCode, envCode, kind, resourceKey, pinnedVersionNo);
        if (kind != DdcSiKindEnum.AGENT) throw new IllegalArgumentException("SI_EXECUTION_REQUEST_INVALID");
        DdcSiCatalogCache.coordinate(callerAppCode);
        if (ownerAppCode != null) DdcSiCatalogCache.coordinate(ownerAppCode);
        var remote = Objects.requireNonNull(delegate.resolveExecutionBundle(tenantId, bizCode, namespaceCode, envCode,
                ownerAppCode, callerAppCode, kind, resourceKey, pinnedVersionNo), "execution bundle result");
        if (remote.isEmpty()) throw DdcSiCatalogCache.historyUnavailable();
        var bundle = remote.orElseThrow(); var root = bundle.root(); var identity = root.identity();
        if (!Long.toString(tenantId).equals(identity.tenantId()) || !bizCode.equals(root.bizCode())
                || !namespaceCode.equals(root.namespaceCode()) || !envCode.equals(identity.envCode())
                || kind != identity.kind() || !resourceKey.equals(root.resourceKey())
                || !Objects.equals(ownerAppCode, root.ownerAppCode())
                || pinnedVersionNo != null && !Long.toString(pinnedVersionNo).equals(identity.versionNo()))
            throw DdcSiCatalogCache.historyUnavailable();
        return remote;
    }

    @Override public List<DdcSiSearchHit> semanticSearch(DdcSiSemanticSearchQuery query) { return delegate.semanticSearch(query); }
    @Override public DdcSiResourceDescriptor registerManagedResource(DdcSiSelfRegistrationRequest request) { return delegate.registerManagedResource(request); }

    /** Publication messages only coalesce live refreshes; they are not authorization or content proof. */
    public void scheduleRefresh(long tenantId, String bizCode, String namespaceCode, String envCode) {
        var key = new DdcSiCatalogCache.ScopeKey(tenantId, bizCode, namespaceCode, envCode);
        AtomicBoolean scheduled = pendingRefreshes.computeIfAbsent(key, ignored -> new AtomicBoolean(false));
        if (!scheduled.compareAndSet(false, true)) return;
        long delayMs = Math.max(5L, properties.getDebounceWindow().toMillis());
        try {
            scheduler.schedule(() -> {
                scheduled.set(false);
                try { refreshScope(bizCode, namespaceCode, envCode, properties.getAppCode(), tenantId); }
                catch (RuntimeException failure) {
                    log.warn("SI catalog background refresh rejected or unavailable; failureType={}", failure.getClass().getSimpleName());
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException schedulingFailure) {
            scheduled.set(false);
            throw schedulingFailure;
        }
    }

    /** Failure retains non-authorizing local bytes, but does not return them as a successful refresh. */
    public void refreshScope(String bizCode, String namespaceCode, String envCode, @Nullable String appCode) {
        Long tenantId = properties.getTenantId();
        if (tenantId != null && tenantId > 0) refreshScope(bizCode, namespaceCode, envCode, appCode, tenantId);
    }

    private void refreshScope(String bizCode, String namespaceCode, String envCode, @Nullable String appCode, long tenantId) {
        listResources(DdcSiCatalogQuery.builder().tenantId(tenantId).bizCode(bizCode).namespaceCode(namespaceCode)
                .envCode(envCode).appCode(appCode).pageNo(1).pageSize(200).build());
    }

    @Nullable private String readRemoteRevision(long tenantId, String bizCode, String namespaceCode, String envCode) {
        if (redissonClient == null) return null;
        try {
            RBucket<String> bucket = redissonClient.getBucket(DdcRedisKeys.siCatalogVersionKey(
                    tenantId, bizCode, namespaceCode, envCode), StringCodec.INSTANCE);
            return bucket == null ? null : bucket.get();
        } catch (RuntimeException failure) {
            log.debug("SI catalog revision hint unavailable; failureType={}", failure.getClass().getSimpleName());
            return null;
        }
    }

    private static void requireRequest(long tenant, String biz, String namespace, String env,
            DdcSiKindEnum kind, String key, Long pinnedVersion) {
        if (tenant <= 0 || kind == null || pinnedVersion != null && pinnedVersion <= 0) throw new IllegalArgumentException("SI_EXECUTION_REQUEST_INVALID");
        DdcSiCatalogCache.coordinate(biz); DdcSiCatalogCache.coordinate(namespace);
        DdcSiCatalogCache.coordinate(env); DdcSiCatalogCache.coordinate(key);
    }
    private static void requireResponseScope(DdcSiResourceDescriptor value, long tenant, String biz, String namespace,
            String env, DdcSiKindEnum kind, String key) {
        if (!Objects.equals(value.getTenantId(), tenant) || !biz.equals(value.getBizCode()) || !namespace.equals(value.getNamespaceCode())
                || !env.equals(value.getEnvCode()) || kind != value.getKind() || !key.equals(value.getResourceKey())) {
            throw DdcSiCatalogCache.historyUnavailable();
        }
    }
}
