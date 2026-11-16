package top.egon.cola.component.tianshu.ai.si;

import lombok.extern.slf4j.Slf4j;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lock-free immutable in-memory snapshot cache keyed by tenant/biz/namespace/env scope. */
@Slf4j
public class DdcSiCatalogCache {

    private final Clock clock;
    private final ConcurrentHashMap<ScopeKey, ScopeSnapshot> snapshots = new ConcurrentHashMap<>();

    public DdcSiCatalogCache(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<DdcSiResourceDescriptor> getResource(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            DdcSiKindEnum kind,
            String resourceKey,
            Integer pinnedVersionNo) {
        if (kind == null || resourceKey == null || resourceKey.isBlank()) {
            return Optional.empty();
        }
        ScopeSnapshot snapshot = snapshots.get(new ScopeKey(tenantId, safe(bizCode), safe(namespaceCode), safe(envCode)));
        if (snapshot == null) {
            return Optional.empty();
        }
        DdcSiResourceDescriptor found = snapshot.byResource().get(new ResourceKey(kind, resourceKey));
        if (found == null) {
            return Optional.empty();
        }
        if (pinnedVersionNo != null && !pinnedVersionNo.equals(found.getActiveVersionNo())) {
            return Optional.empty();
        }
        return Optional.of(found);
    }

    public List<DdcSiResourceDescriptor> listByScope(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode) {
        ScopeSnapshot snapshot = snapshots.get(new ScopeKey(tenantId, safe(bizCode), safe(namespaceCode), safe(envCode)));
        return snapshot == null ? List.of() : snapshot.ordered();
    }

    public void putAllSnapshot(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            List<DdcSiResourceDescriptor> items,
            String revisionToken) {
        List<DdcSiResourceDescriptor> immutable = items == null ? List.of() : List.copyOf(items);
        Map<ResourceKey, DdcSiResourceDescriptor> byResource = immutable.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getKind() != null && item.getResourceKey() != null)
                .collect(Collectors.toUnmodifiableMap(
                        item -> new ResourceKey(item.getKind(), item.getResourceKey()),
                        Function.identity(),
                        (first, second) -> second
                ));
        snapshots.put(
                new ScopeKey(tenantId, safe(bizCode), safe(namespaceCode), safe(envCode)),
                new ScopeSnapshot(immutable, byResource, revisionToken, clock.instant())
        );
    }

    public void evictOrUpdateOne(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            DdcSiResourceDescriptor descriptor) {
        if (descriptor == null || descriptor.getKind() == null || descriptor.getResourceKey() == null) {
            return;
        }
        ScopeKey scopeKey = new ScopeKey(tenantId, safe(bizCode), safe(namespaceCode), safe(envCode));
        snapshots.compute(scopeKey, (key, existing) -> {
            List<DdcSiResourceDescriptor> base = existing == null ? List.of() : existing.ordered();
            List<DdcSiResourceDescriptor> updated = new java.util.ArrayList<>(base.size() + 1);
            boolean replaced = false;
            for (DdcSiResourceDescriptor item : base) {
                if (item.getKind() == descriptor.getKind()
                        && descriptor.getResourceKey().equals(item.getResourceKey())) {
                    if (descriptor.getStatus() != null && descriptor.getStatus().isQueryableByRuntime()) {
                        updated.add(descriptor);
                    }
                    replaced = true;
                } else {
                    updated.add(item);
                }
            }
            if (!replaced && descriptor.getStatus() != null && descriptor.getStatus().isQueryableByRuntime()) {
                updated.add(descriptor);
            }
            List<DdcSiResourceDescriptor> frozen = List.copyOf(updated);
            Map<ResourceKey, DdcSiResourceDescriptor> index = frozen.stream()
                    .collect(Collectors.toUnmodifiableMap(
                            item -> new ResourceKey(item.getKind(), item.getResourceKey()),
                            Function.identity(),
                            (first, second) -> second));
            return new ScopeSnapshot(frozen, index, existing == null ? null : existing.revisionToken(), clock.instant());
        });
    }

    public Optional<String> currentRevision(long tenantId, String bizCode, String namespaceCode, String envCode) {
        ScopeSnapshot snapshot = snapshots.get(new ScopeKey(tenantId, safe(bizCode), safe(namespaceCode), safe(envCode)));
        return snapshot == null ? Optional.empty() : Optional.ofNullable(snapshot.revisionToken());
    }

    public boolean isStale(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            Duration ttl,
            String latestRevisionToken) {
        ScopeSnapshot snapshot = snapshots.get(new ScopeKey(tenantId, safe(bizCode), safe(namespaceCode), safe(envCode)));
        if (snapshot == null) {
            return true;
        }
        if (latestRevisionToken != null && !latestRevisionToken.isBlank()
                && !Objects.equals(latestRevisionToken, snapshot.revisionToken())) {
            return true;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        return Duration.between(snapshot.loadedAt(), clock.instant()).compareTo(ttl) > 0;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record ScopeKey(long tenantId, String bizCode, String namespaceCode, String envCode) { }

    private record ResourceKey(DdcSiKindEnum kind, String resourceKey) { }

    private record ScopeSnapshot(
            List<DdcSiResourceDescriptor> ordered,
            Map<ResourceKey, DdcSiResourceDescriptor> byResource,
            String revisionToken,
            Instant loadedAt) { }
}
