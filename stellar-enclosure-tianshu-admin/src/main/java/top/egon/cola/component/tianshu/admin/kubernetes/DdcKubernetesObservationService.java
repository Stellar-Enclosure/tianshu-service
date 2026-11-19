package top.egon.cola.component.tianshu.admin.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ResourceKind;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.WatchEvent;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.WatchHandle;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only, bounded list/watch controller. Only complete namespace bundles become current evidence. */
public final class DdcKubernetesObservationService implements SmartLifecycle, AutoCloseable {
    private static final List<ResourceKind> KINDS = List.of(ResourceKind.POD, ResourceKind.SERVICE,
            ResourceKind.ENDPOINT_SLICE, ResourceKind.DEPLOYMENT, ResourceKind.REPLICA_SET, ResourceKind.STATEFUL_SET);
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 20;
    private static final int MAX_ITEMS = 10_000;
    private static final Set<String> ANNOTATIONS = Set.of("stellar.io/config-revision", "stellar.io/config-content-sha256",
            "stellar.io/config-restart-digest");
    private final DdcKubernetesApiClient api;
    private final Clock clock;
    private final String clusterId;
    private final Duration maxStale;
    private final Duration relistInterval;
    private final Duration watchTimeout;
    private final Map<String, NamespaceState> namespaces;
    private final Object lifecycle = new Object();
    private volatile boolean running;
    private volatile boolean closed;
    private @Nullable ScheduledExecutorService executor;

    /** The source must own only the separate OBSERVER credential, never the verifier or publisher credential. */
    public DdcKubernetesObservationService(DdcKubernetesApiClient api, Clock clock, String clusterId,
            Set<String> namespaces, Duration maxStale, Duration relistInterval, Duration watchTimeout) {
        this.api = Objects.requireNonNull(api, "api");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (clusterId == null || !clusterId.matches("[!-~]{1,128}") || namespaces == null || namespaces.isEmpty()
                || namespaces.size() > 8 || api.scope() == null || api.scope().mode() != DdcKubernetesApiClient.Mode.OBSERVER
                || !api.scope().namespaces().equals(namespaces) || maxStale == null || relistInterval == null || watchTimeout == null
                || maxStale.compareTo(Duration.ofSeconds(1)) < 0 || maxStale.compareTo(Duration.ofMinutes(5)) > 0
                || relistInterval.compareTo(Duration.ofSeconds(1)) < 0 || relistInterval.compareTo(maxStale) >= 0
                || watchTimeout.compareTo(Duration.ofSeconds(1)) < 0 || watchTimeout.compareTo(Duration.ofSeconds(30)) > 0
                || watchTimeout.getNano() != 0) {
            throw new IllegalArgumentException("CONFIG_OBSERVATION_SCOPE_INVALID");
        }
        this.clusterId = clusterId; this.maxStale = maxStale; this.relistInterval = relistInterval; this.watchTimeout = watchTimeout;
        var states = new LinkedHashMap<String, NamespaceState>();
        namespaces.stream().sorted().forEach(namespace -> {
            if (!namespace.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw new IllegalArgumentException("CONFIG_OBSERVATION_SCOPE_INVALID");
            states.put(namespace, new NamespaceState());
        });
        this.namespaces = Map.copyOf(states);
    }

    @Override public void start() {
        synchronized (lifecycle) {
            if (closed) throw new IllegalStateException("CONFIG_OBSERVATION_CLOSED");
            if (running) return;
            executor = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "tianshu-kubernetes-observer"); thread.setDaemon(true); return thread;
            });
            running = true;
            executor.scheduleWithFixedDelay(this::refresh, 0, relistInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Explicit recovery and the lifecycle worker use the same bounded full-relist path. Queries never call this. */
    public void refresh() {
        if (closed) return;
        for (var entry : namespaces.entrySet()) {
            if (closed || Thread.currentThread().isInterrupted()) return;
            refreshNamespace(entry.getKey(), entry.getValue());
        }
    }

    private void refreshNamespace(String namespace, NamespaceState state) {
        long epoch;
        List<WatchHandle> previous;
        synchronized (state) {
            if (closed || state.refreshing) return;
            state.refreshing = true;
            epoch = ++state.epoch;
            previous = drain(state);
            state.bundle.set(state.bundle.get().unavailable(StatusEnum.RELISTING, "RELISTING", clock.instant()));
        }
        closeWatches(previous);
        try {
            var resources = new EnumMap<ResourceKind, Map<String, JsonNode>>(ResourceKind.class);
            var versions = new EnumMap<ResourceKind, String>(ResourceKind.class);
            var verified = new EnumMap<ResourceKind, Instant>(ResourceKind.class);
            for (var kind : KINDS) {
                var items = new LinkedHashMap<String, JsonNode>();
                var names = new HashSet<String>(); var continuations = new HashSet<String>();
                String continuation = null; String version = null;
                for (int page = 0; page < MAX_PAGES; page++) {
                    if (!active(state, epoch)) return;
                    var response = api.list(kind, namespace, continuation, PAGE_SIZE);
                    String current = token(response.resourceVersion(), 256);
                    if (version == null) version = current;
                    else if (!version.equals(current)) throw malformed();
                    for (JsonNode item : response.items()) {
                        JsonNode copy = resource(kind, namespace, item);
                        String uid = text(copy.path("metadata"), "uid", 128);
                        if (items.putIfAbsent(uid, copy) != null || !names.add(text(copy.path("metadata"), "name", 253))
                                || items.size() > MAX_ITEMS) throw malformed();
                    }
                    continuation = response.continuation();
                    if (continuation == null || continuation.isEmpty()) break;
                    token(continuation, 8192);
                    if (!continuations.add(continuation) || page == MAX_PAGES - 1) throw malformed();
                }
                resources.put(kind, Map.copyOf(items)); versions.put(kind, Objects.requireNonNull(version));
                verified.put(kind, clock.instant());
            }
            synchronized (state) {
                if (!active(state, epoch)) return;
                state.bundle.set(new Bundle(Map.copyOf(resources), Map.copyOf(verified), StatusEnum.FRESH, null, null));
            }
            for (var kind : KINDS) {
                if (!active(state, epoch)) return;
                WatchHandle handle = api.watch(kind, namespace, versions.get(kind), watchTimeout,
                        event -> accept(namespace, state, epoch, kind, event));
                synchronized (state) {
                    if (!active(state, epoch)) { handle.close(); return; }
                    state.watches.put(kind, handle);
                }
                handle.completion().whenComplete((ignored, failure) -> invalidate(state, epoch,
                        failure == null ? "WATCH_DISCONNECTED" : failureReason(failure)));
            }
        } catch (RuntimeException unavailable) {
            invalidate(state, epoch, failureReason(unavailable));
        } finally {
            synchronized (state) { state.refreshing = false; }
        }
    }

    private void accept(String namespace, NamespaceState state, long epoch, ResourceKind kind, WatchEvent event) {
        try {
            token(event.resourceVersion(), 256);
            synchronized (state) {
                if (!active(state, epoch)) return;
                var prior = state.bundle.get();
                if (prior.status != StatusEnum.FRESH) return;
                var resources = new EnumMap<ResourceKind, Map<String, JsonNode>>(ResourceKind.class);
                resources.putAll(prior.resources);
                if (event.type() != DdcKubernetesApiClient.WatchEventType.BOOKMARK) {
                    JsonNode copy = resource(kind, namespace, event.object());
                    if (!event.resourceVersion().equals(copy.path("metadata").path("resourceVersion").textValue())) throw malformed();
                    String uid = text(copy.path("metadata"), "uid", 128);
                    var updated = new LinkedHashMap<>(resources.get(kind));
                    switch (event.type()) {
                        case ADDED, MODIFIED -> {
                            if (event.type() == DdcKubernetesApiClient.WatchEventType.MODIFIED && !updated.containsKey(uid)) throw malformed();
                            String name = text(copy.path("metadata"), "name", 253);
                            updated.entrySet().removeIf(entry -> name.equals(entry.getValue().path("metadata").path("name").textValue())
                                    && !uid.equals(entry.getKey()));
                            updated.put(uid, copy);
                            if (updated.size() > MAX_ITEMS) throw malformed();
                        }
                        case DELETED -> updated.remove(uid);
                        default -> throw malformed();
                    }
                    resources.put(kind, Map.copyOf(updated));
                }
                var verified = new EnumMap<ResourceKind, Instant>(ResourceKind.class); verified.putAll(prior.verified);
                verified.put(kind, clock.instant());
                state.bundle.set(new Bundle(Map.copyOf(resources), Map.copyOf(verified), StatusEnum.FRESH, null, null));
            }
        } catch (RuntimeException malformed) { invalidate(state, epoch, "INVALID_RESPONSE"); }
    }

    private void invalidate(NamespaceState state, long epoch, String reason) {
        List<WatchHandle> handles;
        synchronized (state) {
            if (!active(state, epoch)) return;
            ++state.epoch;
            state.bundle.set(state.bundle.get().unavailable(StatusEnum.UNAVAILABLE, reason, clock.instant()));
            handles = drain(state);
        }
        closeWatches(handles);
    }

    /** Returns one immutable local projection. No Kubernetes request, persistence call, or status mutation occurs here. */
    public WorkloadViewVO currentView(TargetBindingVO target) {
        Objects.requireNonNull(target, "target");
        var state = namespaces.get(target.getKubernetesNamespace());
        if (!clusterId.equals(target.getClusterId()) || state == null) return empty(target, StatusEnum.UNAVAILABLE, "OUT_OF_SCOPE", null, null, clock.instant());
        Bundle bundle = state.bundle.get();
        Instant observed = bundle.observedAt(); Instant expiry = observed == null ? null : observed.plus(maxStale);
        StatusEnum status = bundle.status;
        Instant invalidated = bundle.invalidatedAt;
        String reason = bundle.reason;
        if (closed) { status = StatusEnum.CLOSED; reason = "CLOSED"; }
        else if (status == StatusEnum.FRESH && (expiry == null || !clock.instant().isBefore(expiry))) {
            status = StatusEnum.STALE; reason = "OBSERVATION_EXPIRED"; invalidated = expiry;
        }
        ResourceKind workloadKind = switch (target.getWorkloadKind()) {
            case "Deployment" -> ResourceKind.DEPLOYMENT;
            case "StatefulSet" -> ResourceKind.STATEFUL_SET;
            default -> null;
        };
        if (workloadKind == null) return empty(target, StatusEnum.UNAVAILABLE, "UNSUPPORTED_WORKLOAD_KIND", observed, expiry, invalidated);
        JsonNode workload = bundle.resources.getOrDefault(workloadKind, Map.of()).values().stream()
                .filter(item -> target.getWorkloadName().equals(item.path("metadata").path("name").textValue())).findFirst().orElse(null);
        if (workload == null) return empty(target, status == StatusEnum.FRESH ? StatusEnum.UNAVAILABLE : status,
                status == StatusEnum.FRESH ? "WORKLOAD_ABSENT" : reason, observed, expiry, invalidated);
        try {
            String actualUid = text(workload.path("metadata"), "uid", 128);
            if (!target.getWorkloadUid().equals(actualUid)) return new WorkloadViewVO(target, StatusEnum.IDENTITY_CONFLICT,
                    "WORKLOAD_UID_MISMATCH", observed, expiry, clock.instant(), actualUid, 0, 0, 0, List.of(), List.of(), Map.of(), Map.of());
            if (workload.path("metadata").hasNonNull("deletionTimestamp")) return empty(target, StatusEnum.UNAVAILABLE,
                    "WORKLOAD_DELETING", observed, expiry, clock.instant());
            long generation = number(workload.path("metadata"), "generation", true);
            long evaluated = optionalNumber(workload.path("status"), "observedGeneration");
            int replicas = Math.toIntExact(number(workload.path("spec"), "replicas", false));
            var pods = new ArrayList<PodObservationVO>(); var incarnations = new ArrayList<ContainerIncarnationVO>();
            for (JsonNode pod : bundle.resources.getOrDefault(ResourceKind.POD, Map.of()).values()) {
                if (!ownedPod(pod, workloadKind, target, workload, bundle)) continue;
                JsonNode metadata = pod.path("metadata");
                String phase = optionalText(pod.path("status"), "phase", 32);
                if (metadata.hasNonNull("deletionTimestamp") || "Succeeded".equals(phase) || "Failed".equals(phase)) continue;
                var observation = pod(target, pod, phase == null ? "Pending" : phase);
                pods.add(observation);
                if (observation.containerId() != null && observation.startedAt() != null && "Running".equals(observation.phase())) {
                    incarnations.add(new ContainerIncarnationVO(observation.podUid(), observation.containerName(), observation.containerId()));
                }
            }
            pods.sort(Comparator.comparing(PodObservationVO::podUid));
            incarnations.sort(Comparator.comparing(ContainerIncarnationVO::podUid));
            return new WorkloadViewVO(target, status, reason, observed, expiry, invalidated, actualUid, generation,
                    evaluated, replicas, pods, incarnations, annotations(workload), configMaps(workload));
        } catch (RuntimeException malformed) {
            return empty(target, StatusEnum.UNAVAILABLE, "INVALID_WORKLOAD_OBSERVATION", observed, expiry, clock.instant());
        }
    }

    private boolean ownedPod(JsonNode pod, ResourceKind kind, TargetBindingVO target, JsonNode workload, Bundle bundle) {
        JsonNode owner = controller(pod.path("metadata"));
        if (owner == null || !"apps/v1".equals(owner.path("apiVersion").asText())) return false;
        if (kind == ResourceKind.STATEFUL_SET) return "StatefulSet".equals(owner.path("kind").asText())
                && target.getWorkloadUid().equals(owner.path("uid").asText()) && target.getWorkloadName().equals(owner.path("name").asText());
        if (!"ReplicaSet".equals(owner.path("kind").asText())) return false;
        JsonNode replica = bundle.resources.getOrDefault(ResourceKind.REPLICA_SET, Map.of()).get(owner.path("uid").asText());
        if (replica == null) {
            // Cross-kind lists are not a Kubernetes transaction. A matching Pod with an unresolved owner
            // must not disappear from this workload's denominator while ReplicaSet evidence catches up.
            if (matchesSelector(pod.path("metadata").path("labels"), workload.path("spec").path("selector"))) throw malformed();
            return false;
        }
        if (!owner.path("name").asText().equals(replica.path("metadata").path("name").asText())) throw malformed();
        JsonNode parent = controller(replica.path("metadata"));
        return parent != null && "apps/v1".equals(parent.path("apiVersion").asText()) && "Deployment".equals(parent.path("kind").asText())
                && target.getWorkloadUid().equals(parent.path("uid").asText()) && target.getWorkloadName().equals(parent.path("name").asText());
    }

    private static boolean matchesSelector(JsonNode labels, JsonNode selector) {
        if (!selector.isObject() || selector.isEmpty()) throw malformed();
        var exact = selector.path("matchLabels").fields();
        while (exact.hasNext()) {
            var label = exact.next();
            if (!label.getValue().isTextual() || !label.getValue().equals(labels.path(label.getKey()))) return false;
        }
        for (var expression : selector.path("matchExpressions")) {
            String key = text(expression, "key", 253);
            String operator = text(expression, "operator", 32);
            boolean present = labels.has(key);
            boolean member = false;
            for (var value : expression.path("values")) {
                if (!value.isTextual()) throw malformed();
                if (value.equals(labels.path(key))) member = true;
            }
            boolean matches = switch (operator) {
                case "In" -> present && member;
                case "NotIn" -> !present || !member;
                case "Exists" -> present;
                case "DoesNotExist" -> !present;
                default -> throw malformed();
            };
            if (!matches) return false;
        }
        return true;
    }

    private static PodObservationVO pod(TargetBindingVO target, JsonNode pod, String phase) {
        String name = text(pod.path("metadata"), "name", 253); String uid = text(pod.path("metadata"), "uid", 128);
        int declared = 0;
        for (var container : pod.path("spec").path("containers")) if (target.getContainerName().equals(container.path("name").asText())) declared++;
        if (declared != 1) throw malformed();
        JsonNode selected = null;
        for (var container : pod.path("status").path("containerStatuses")) {
            if (!target.getContainerName().equals(container.path("name").asText())) continue;
            if (selected != null) throw malformed(); selected = container;
        }
        String id = null; Instant started = null; int restart = 0;
        if (selected != null) {
            restart = Math.toIntExact(optionalNumber(selected, "restartCount"));
            if (selected.path("state").path("running").isObject()) {
                id = optionalText(selected, "containerID", 256);
                String start = optionalText(selected.path("state").path("running"), "startedAt", 64);
                if (start != null) started = Instant.parse(start);
            }
        }
        boolean ready = false; int readyCount = 0;
        for (var condition : pod.path("status").path("conditions")) if ("Ready".equals(condition.path("type").asText())) {
            readyCount++; ready = "True".equals(condition.path("status").asText());
        }
        if (readyCount > 1) throw malformed();
        return new PodObservationVO(uid, name, phase, ready, target.getContainerName(), id, restart, started);
    }

    private static Map<String, String> annotations(JsonNode workload) {
        JsonNode source = workload.path("spec").path("template").path("metadata").path("annotations");
        var result = new LinkedHashMap<String, String>();
        for (String key : ANNOTATIONS) {
            String value = optionalText(source, key, 256);
            if (value == null) continue;
            if ("stellar.io/config-restart-digest".equals(key)) {
                if (value.matches("[0-9a-f]{64}")) value = "sha256:" + value;
                else if (!value.matches("sha256:[0-9a-f]{64}")) throw malformed();
            }
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> configMaps(JsonNode workload) {
        var result = new LinkedHashMap<String, String>();
        for (JsonNode volume : workload.path("spec").path("template").path("spec").path("volumes")) {
            if (volume.path("configMap").isObject()) {
                String name = text(volume, "name", 63);
                if (result.putIfAbsent(name, text(volume.path("configMap"), "name", 253)) != null) throw malformed();
            }
        }
        return Map.copyOf(result);
    }

    private static @Nullable JsonNode controller(JsonNode metadata) {
        JsonNode result = null;
        for (JsonNode owner : metadata.path("ownerReferences")) if (owner.path("controller").isBoolean() && owner.path("controller").booleanValue()) {
            if (result != null) throw malformed(); result = owner;
        }
        return result;
    }

    private static JsonNode resource(ResourceKind kind, String namespace, JsonNode item) {
        if (item == null || !item.isObject() || !apiVersion(kind).equals(item.path("apiVersion").asText())
                || !kindName(kind).equals(item.path("kind").asText()) || !namespace.equals(item.path("metadata").path("namespace").asText())) throw malformed();
        text(item.path("metadata"), "name", 253); text(item.path("metadata"), "uid", 128); text(item.path("metadata"), "resourceVersion", 256);
        return item.deepCopy();
    }

    private static String apiVersion(ResourceKind kind) { return switch (kind) {
        case POD, SERVICE -> "v1"; case ENDPOINT_SLICE -> "discovery.k8s.io/v1"; default -> "apps/v1";
    }; }
    private static String kindName(ResourceKind kind) { return switch (kind) {
        case POD -> "Pod"; case SERVICE -> "Service"; case ENDPOINT_SLICE -> "EndpointSlice";
        case DEPLOYMENT -> "Deployment"; case REPLICA_SET -> "ReplicaSet"; case STATEFUL_SET -> "StatefulSet";
    }; }
    private static long number(JsonNode object, String key, boolean positive) {
        JsonNode value = object.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < (positive ? 1 : 0)) throw malformed();
        return value.longValue();
    }
    private static long optionalNumber(JsonNode object, String key) { return object.has(key) ? number(object, key, false) : 0; }
    private static String text(JsonNode object, String key, int max) { return token(object.path(key).isTextual() ? object.path(key).textValue() : null, max); }
    private static @Nullable String optionalText(JsonNode object, String key, int max) { return object.hasNonNull(key) ? text(object, key, max) : null; }
    private static String token(@Nullable String value, int max) {
        if (value == null || value.isEmpty() || value.length() > max || !value.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)) throw malformed();
        return value;
    }
    private boolean active(NamespaceState state, long epoch) { return !closed && state.epoch == epoch; }
    private static IllegalArgumentException malformed() { return new IllegalArgumentException("CONFIG_OBSERVATION_INVALID_RESPONSE"); }
    private static String failureReason(Throwable failure) {
        Throwable current = failure;
        for (int i = 0; i < 8 && current.getCause() != null; i++) current = current.getCause();
        return current instanceof DdcKubernetesApiClient.Failure specific ? specific.kind().name() : "UNAVAILABLE";
    }
    private static List<WatchHandle> drain(NamespaceState state) { var result = List.copyOf(state.watches.values()); state.watches.clear(); return result; }
    private static void closeWatches(List<WatchHandle> handles) { for (var handle : handles) try { handle.close(); } catch (RuntimeException ignored) { /* already invalidated */ } }
    private WorkloadViewVO empty(TargetBindingVO target, StatusEnum status, @Nullable String reason, @Nullable Instant observed,
            @Nullable Instant expiry, @Nullable Instant invalidated) {
        return new WorkloadViewVO(target, status, reason, observed, expiry, invalidated, target.getWorkloadUid(), 0, 0, 0,
                List.of(), List.of(), Map.of(), Map.of());
    }

    @Override public boolean isRunning() { return running; }
    @Override public void stop() { close(); }
    @Override public void close() {
        synchronized (lifecycle) {
            if (closed) return; closed = true; running = false;
            for (var state : namespaces.values()) {
                List<WatchHandle> handles;
                synchronized (state) { ++state.epoch; state.bundle.set(state.bundle.get().unavailable(StatusEnum.CLOSED, "CLOSED", clock.instant())); handles = drain(state); }
                closeWatches(handles);
            }
            if (executor != null) executor.shutdownNow();
        }
    }

    public enum StatusEnum { INITIALIZING, RELISTING, FRESH, STALE, UNAVAILABLE, IDENTITY_CONFLICT, CLOSED }
    public record PodObservationVO(String podUid, String podName, String phase, boolean ready, String containerName,
                                   @Nullable String containerId, int restartCount, @Nullable Instant startedAt) { }
    public record ContainerIncarnationVO(String podUid, String containerName, String containerId) { }
    public record WorkloadViewVO(TargetBindingVO target, StatusEnum status, @Nullable String reasonCode,
            @Nullable Instant observedAt, @Nullable Instant validUntil, @Nullable Instant invalidatedAt,
            String workloadUid, long workloadGeneration, long observedGeneration, int desiredReplicas,
            List<PodObservationVO> currentPods, List<ContainerIncarnationVO> currentIncarnations,
            Map<String, String> templateAnnotations, Map<String, String> configMapReferences) {
        public WorkloadViewVO { currentPods = List.copyOf(currentPods); currentIncarnations = List.copyOf(currentIncarnations);
            templateAnnotations = Map.copyOf(templateAnnotations); configMapReferences = Map.copyOf(configMapReferences); }
        public boolean fresh() { return status == StatusEnum.FRESH; }
    }
    private static final class NamespaceState {
        private volatile long epoch; private boolean refreshing;
        private final AtomicReference<Bundle> bundle = new AtomicReference<>(new Bundle(Map.of(), Map.of(), StatusEnum.INITIALIZING, "INITIALIZING", null));
        private final EnumMap<ResourceKind, WatchHandle> watches = new EnumMap<>(ResourceKind.class);
    }
    private record Bundle(Map<ResourceKind, Map<String, JsonNode>> resources, Map<ResourceKind, Instant> verified,
                          StatusEnum status, @Nullable String reason, @Nullable Instant invalidatedAt) {
        @Nullable Instant observedAt() { return verified.size() == KINDS.size() ? verified.values().stream().min(Comparator.naturalOrder()).orElse(null) : null; }
        Bundle unavailable(StatusEnum next, String why, Instant at) { return new Bundle(resources, verified, next, why, at); }
    }
}
