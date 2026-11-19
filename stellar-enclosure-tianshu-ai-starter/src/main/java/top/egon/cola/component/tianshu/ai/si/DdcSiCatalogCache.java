package top.egon.cola.component.tianshu.ai.si;

import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only snapshots, not authorization decisions. Metadata heads and exact execution history
 * are separate; neither a TTL nor a Redis revision proves a caller's current authority.
 */
public class DdcSiCatalogCache {
    private static final int MAX_EXECUTION_SNAPSHOTS = 64;
    private final Clock clock;
    private final ConcurrentHashMap<ScopeKey, ScopeSnapshot> snapshots = new ConcurrentHashMap<>();
    // Global bound includes all callers, owners, versions and content identities.
    private final Map<ExecutionKey, DdcSiResourceDescriptor> executions = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_EXECUTION_SNAPSHOTS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ExecutionKey, DdcSiResourceDescriptor> eldest) {
                    return size() > MAX_EXECUTION_SNAPSHOTS;
                }
            });

    public DdcSiCatalogCache(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /** The legacy scope addresses namespace-owned resources, never an arbitrary app-owned resource. */
    public Optional<DdcSiResourceDescriptor> getResource(long tenantId, String bizCode, String namespaceCode,
            String envCode, DdcSiKindEnum kind, String resourceKey, Integer pinnedVersionNo) {
        return getResource(tenantId, bizCode, namespaceCode, envCode, null, kind, resourceKey, pinnedVersionNo);
    }

    public Optional<DdcSiResourceDescriptor> getResource(long tenantId, String bizCode, String namespaceCode,
            String envCode, String ownerAppCode, DdcSiKindEnum kind, String resourceKey, Integer pinnedVersionNo) {
        if (kind == null || resourceKey == null || resourceKey.isBlank()) return Optional.empty();
        ScopeSnapshot snapshot = snapshots.get(scope(tenantId, bizCode, namespaceCode, envCode));
        if (snapshot == null) return Optional.empty();
        DdcSiResourceDescriptor found = snapshot.byResource().get(new ResourceKey(ownerAppCode, kind, resourceKey));
        if (found == null || pinnedVersionNo != null && !pinnedVersionNo.equals(found.getActiveVersionNo())) return Optional.empty();
        return Optional.of(copy(found));
    }

    public List<DdcSiResourceDescriptor> listByScope(long tenantId, String bizCode, String namespaceCode, String envCode) {
        ScopeSnapshot snapshot = snapshots.get(scope(tenantId, bizCode, namespaceCode, envCode));
        return snapshot == null ? List.of() : copies(snapshot.ordered());
    }

    public void putAllSnapshot(long tenantId, String bizCode, String namespaceCode, String envCode,
            List<DdcSiResourceDescriptor> items, String revisionToken) {
        ScopeKey key = scope(tenantId, bizCode, namespaceCode, envCode);
        List<DdcSiResourceDescriptor> frozen = copies(items == null ? List.of() : items);
        var index = new LinkedHashMap<ResourceKey, DdcSiResourceDescriptor>();
        for (var item : frozen) {
            requireScope(key, item);
            ResourceKey resource = resourceKey(item);
            if (index.putIfAbsent(resource, item) != null) throw new IllegalArgumentException("SI_CATALOG_DUPLICATE_RESOURCE");
        }
        snapshots.put(key, new ScopeSnapshot(frozen, Collections.unmodifiableMap(index), revisionToken, clock.instant()));
    }

    public void evictOrUpdateOne(long tenantId, String bizCode, String namespaceCode, String envCode,
            DdcSiResourceDescriptor descriptor) {
        if (descriptor == null) return;
        ScopeKey key = scope(tenantId, bizCode, namespaceCode, envCode);
        DdcSiResourceDescriptor frozen = copy(descriptor);
        requireScope(key, frozen);
        ResourceKey resource = resourceKey(frozen);
        snapshots.compute(key, (ignored, existing) -> {
            var index = new LinkedHashMap<ResourceKey, DdcSiResourceDescriptor>();
            if (existing != null) index.putAll(existing.byResource());
            if (frozen.getStatus() != null && frozen.getStatus().isQueryableByRuntime()) index.put(resource, frozen);
            else index.remove(resource);
            return new ScopeSnapshot(List.copyOf(index.values()), Collections.unmodifiableMap(index),
                    existing == null ? null : existing.revisionToken(), clock.instant());
        });
    }

    /** An exact artifact is stored independently of metadata and never changes its current head. */
    public void putExecutionSnapshot(String callerAppCode, DdcSiResourceDescriptor descriptor) {
        coordinate(callerAppCode);
        DdcSiResourceDescriptor frozen = verifiedExecution(descriptor);
        executions.putIfAbsent(executionKey(callerAppCode, frozen), frozen);
    }

    /** Explicit content and closure identities are required even for a non-authorizing local lookup. */
    public Optional<DdcSiResourceDescriptor> getExecutionSnapshot(ExecutionKey key) {
        return Optional.ofNullable(executions.get(Objects.requireNonNull(key, "key"))).map(DdcSiCatalogCache::copy);
    }

    public Optional<String> currentRevision(long tenantId, String bizCode, String namespaceCode, String envCode) {
        ScopeSnapshot snapshot = snapshots.get(scope(tenantId, bizCode, namespaceCode, envCode));
        return snapshot == null ? Optional.empty() : Optional.ofNullable(snapshot.revisionToken());
    }

    public boolean isStale(long tenantId, String bizCode, String namespaceCode, String envCode,
            Duration ttl, String latestRevisionToken) {
        ScopeSnapshot snapshot = snapshots.get(scope(tenantId, bizCode, namespaceCode, envCode));
        if (snapshot == null) return true;
        if (latestRevisionToken != null && !latestRevisionToken.isBlank()
                && !Objects.equals(latestRevisionToken, snapshot.revisionToken())) return true;
        return ttl != null && !ttl.isZero() && !ttl.isNegative()
                && Duration.between(snapshot.loadedAt(), clock.instant()).compareTo(ttl) > 0;
    }

    static DdcSiResourceDescriptor verifiedExecution(DdcSiResourceDescriptor descriptor) {
        DdcSiResourceDescriptor value = copy(Objects.requireNonNull(descriptor, "descriptor"));
        try {
            if (value.getTenantId() == null || value.getTenantId() <= 0 || value.getResourceId() == null || value.getResourceId() <= 0
                    || value.getKind() == null || value.getStatus() == null || value.getStatus() == DdcSiLifecycleStatusEnum.DRAFT
                    || value.getResolvedVersionId() == null || value.getResolvedVersionId() <= 0
                    || value.getResolvedVersionNo() == null || value.getResolvedVersionNo() <= 0
                    || value.getDependencyManifest() == null || !value.isExecutionSnapshotShapeValid()
                    || value.getSpecJson() == null || value.getDigestSha256() == null
                    || !value.getDigestSha256().matches("[0-9a-f]{64}")) throw historyUnavailable();
            coordinate(value.getBizCode()); coordinate(value.getNamespaceCode()); coordinate(value.getEnvCode());
            coordinate(value.getResourceKey());
            if (value.getOwnerAppCode() != null) coordinate(value.getOwnerAppCode());
            byte[] body = value.getSpecJson().getBytes(StandardCharsets.UTF_8);
            if (body.length > 1_048_576 || !value.getDigestSha256().equals(sha256(body))
                    || !DdcConfigCanonicalJson.parse(body).isObject()) throw historyUnavailable();
            return value;
        } catch (IllegalArgumentException invalid) { throw historyUnavailable(); }
    }

    static ExecutionKey executionKey(String callerAppCode, DdcSiResourceDescriptor value) {
        return new ExecutionKey(callerAppCode, value.getTenantId(), value.getBizCode(), value.getNamespaceCode(),
                value.getEnvCode(), value.getOwnerAppCode(), value.getKind(), value.getResourceKey(),
                value.getResolvedVersionId(), value.getResolvedVersionNo(), value.getDigestSha256(),
                value.getDependencyManifest().closureDigest());
    }

    static DdcSiResourceDescriptor copy(DdcSiResourceDescriptor value) {
        if (value == null) throw new IllegalArgumentException("SI_CATALOG_DESCRIPTOR_INVALID");
        var references = new ArrayList<DdcSiReferenceDescriptor>();
        if (value.getReferences() != null) for (var reference : value.getReferences()) {
            if (reference == null) throw new IllegalArgumentException("SI_CATALOG_REFERENCE_INVALID");
            references.add(DdcSiReferenceDescriptor.builder().targetType(reference.getTargetType()).targetKey(reference.getTargetKey())
                    .pinnedVersionNo(reference.getPinnedVersionNo()).required(reference.isRequired()).roleHint(reference.getRoleHint()).build());
        }
        return DdcSiResourceDescriptor.builder().tenantId(value.getTenantId()).bizCode(value.getBizCode())
                .namespaceCode(value.getNamespaceCode()).envCode(value.getEnvCode()).resourceId(value.getResourceId())
                .kind(value.getKind()).resourceKey(value.getResourceKey()).displayName(value.getDisplayName())
                .description(value.getDescription()).status(value.getStatus()).sourceType(value.getSourceType())
                .activeVersionNo(value.getActiveVersionNo()).digestSha256(value.getDigestSha256())
                .labels(value.getLabels() == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value.getLabels())))
                .references(List.copyOf(references)).specJson(value.getSpecJson()).publishedAt(value.getPublishedAt())
                .updatedAt(value.getUpdatedAt()).ownerAppCode(value.getOwnerAppCode()).resolvedVersionId(value.getResolvedVersionId())
                .resolvedVersionNo(value.getResolvedVersionNo()).dependencyManifest(value.getDependencyManifest()).build();
    }

    private static List<DdcSiResourceDescriptor> copies(List<DdcSiResourceDescriptor> items) {
        return items.stream().map(DdcSiCatalogCache::copy).toList();
    }
    private static ScopeKey scope(long tenant, String biz, String namespace, String env) {
        return new ScopeKey(tenant, safe(biz), safe(namespace), safe(env));
    }
    private static void requireScope(ScopeKey key, DdcSiResourceDescriptor item) {
        if (!Objects.equals(item.getTenantId(), key.tenantId()) || !key.bizCode().equals(item.getBizCode())
                || !key.namespaceCode().equals(item.getNamespaceCode()) || !key.envCode().equals(item.getEnvCode())) {
            throw new IllegalArgumentException("SI_CATALOG_SCOPE_MISMATCH");
        }
    }
    private static ResourceKey resourceKey(DdcSiResourceDescriptor item) {
        if (item.getKind() == null || item.getResourceKey() == null || item.getResourceKey().isBlank()) {
            throw new IllegalArgumentException("SI_CATALOG_DESCRIPTOR_INVALID");
        }
        return new ResourceKey(item.getOwnerAppCode(), item.getKind(), item.getResourceKey());
    }
    static void coordinate(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException("SI_EXECUTION_REQUEST_INVALID");
        }
    }
    private static String sha256(byte[] body) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    static IllegalStateException historyUnavailable() { return new IllegalStateException("SI_PINNED_HISTORY_UNAVAILABLE"); }
    private static String safe(String value) { return value == null ? "" : value; }

    public record ScopeKey(long tenantId, String bizCode, String namespaceCode, String envCode) { }
    private record ResourceKey(String ownerAppCode, DdcSiKindEnum kind, String resourceKey) { }
    public record ExecutionKey(String callerAppCode, long tenantId, String bizCode, String namespaceCode, String envCode,
            String ownerAppCode, DdcSiKindEnum kind, String resourceKey, long versionId, long versionNo,
            String digestSha256, String closureDigest) { }
    private record ScopeSnapshot(List<DdcSiResourceDescriptor> ordered, Map<ResourceKey, DdcSiResourceDescriptor> byResource,
            String revisionToken, Instant loadedAt) { }
}
