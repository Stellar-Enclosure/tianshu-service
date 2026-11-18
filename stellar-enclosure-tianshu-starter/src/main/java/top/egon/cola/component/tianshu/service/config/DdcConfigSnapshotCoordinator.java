package top.egon.cola.component.tianshu.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource.BaselineMetadataVO;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.service.refresh.DdcYamlConfigApplier;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.IncarnationVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.ScopeKeyVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.StateVO;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState.DeliveryObservationVO;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState.DeliveryStatusEnum;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes verified snapshots, local application, durable receipt intent, and transport. */
@Slf4j
public final class DdcConfigSnapshotCoordinator implements ApplicationListener<ApplicationEvent>, AutoCloseable {

    private static final ObjectMapper JSON = Jackson2ObjectMapperBuilder.json().build();
    private static final Set<String> RESTART_CODES = Set.of("CONFIG_RESTART_REQUIRED", "CONFIG_HOT_CONSUMER_UNKNOWN",
            "CONFIG_HOT_REMOVAL_REQUIRES_RESTART", "CONFIG_HOT_FIELD_EXPRESSION_UNSUPPORTED", "CONFIG_HOT_BEAN_STATE_UNSUPPORTED");
    private final BaselineMetadataVO baseline;
    private final PolicyVO policy;
    private final ScopeKeyVO scopeKey;
    private final String bootId = UUID.randomUUID().toString();
    private final DdcConfigSnapshotHttpClient client;
    private final DdcConfigLkgStore store;
    private final DdcYamlConfigApplier applier;
    private final DdcLocalConfigState observations;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean publishedReady = new AtomicBoolean();

    private volatile Instant readyUntil = Instant.EPOCH;
    private volatile boolean awaitingAuthority = true;
    private boolean initialized;
    private boolean storageIndeterminate;
    private StateVO state;
    @Nullable private IncarnationVO incarnation;
    @Nullable private String etag;
    @Nullable private ResultKeyVO acknowledgedResult;
    private int consecutiveFailures;

    public DdcConfigSnapshotCoordinator(BaselineMetadataVO baseline, PolicyVO policy,
            DdcConfigSnapshotHttpClient client, DdcConfigLkgStore store, DdcYamlConfigApplier applier,
            DdcLocalConfigState observations, ApplicationEventPublisher publisher, Clock clock) {
        this.baseline = Objects.requireNonNull(baseline);
        this.policy = Objects.requireNonNull(policy);
        this.client = Objects.requireNonNull(client);
        this.store = Objects.requireNonNull(store);
        this.applier = Objects.requireNonNull(applier);
        this.observations = Objects.requireNonNull(observations);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        var target = baseline.targetBinding();
        this.scopeKey = new ScopeKeyVO(baseline.scope(), baseline.source().getConfigId(), target.getBindingId(),
                target.getClusterId(), target.getKubernetesNamespace(), target.getWorkloadUid(), target.getContainerName());
        this.state = newState(null, false, null, null, 0L, null);
        var executor = new ScheduledThreadPoolExecutor(2, task -> {
            var thread = new Thread(task, "ddc-config-delivery");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        this.scheduler = executor;
    }

    public record PolicyVO(String podUid, Duration maxStale, Duration pollInterval, boolean allowOfflineStart,
                            @Nullable Instant baselineValidUntil) {
        public PolicyVO {
            if (podUid == null || !podUid.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
                throw new IllegalArgumentException("CONFIG_DELIVERY_POD_UID_INVALID");
            }
            requireWindow(maxStale, Duration.ofSeconds(1), Duration.ofDays(1));
            requireWindow(pollInterval, Duration.ofSeconds(1), Duration.ofMinutes(10));
        }
        private static void requireWindow(Duration value, Duration minimum, Duration maximum) {
            if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
                throw new IllegalArgumentException("CONFIG_DELIVERY_DURATION_INVALID");
            }
        }
    }

    public boolean ready() {
        return !closed.get() && !awaitingAuthority && clock.instant().isBefore(readyUntil);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextClosedEvent closedEvent && belongsToApplication(closedEvent.getApplicationContext())) {
            close();
        } else if (event instanceof ApplicationReadyEvent readyEvent && belongsToApplication(readyEvent.getApplicationContext())
                && started.compareAndSet(false, true)) {
            synchronized (this) {
                initialize();
            }
            updateAvailability(true);
            scheduler.scheduleWithFixedDelay(() -> updateAvailability(false), 0, 500, TimeUnit.MILLISECONDS);
            scheduler.execute(this::scheduledPoll);
        } else if (event instanceof AvailabilityChangeEvent<?> availability
                && availability.getState() == ReadinessState.ACCEPTING_TRAFFIC && !ready()) {
            // Boot announces ACCEPTING_TRAFFIC after ApplicationReadyEvent; this scope may still be unverified.
            updateAvailability(true);
        }
    }

    private boolean belongsToApplication(ApplicationContext context) {
        return !(publisher instanceof ApplicationContext application) || application == context;
    }

    private void scheduledPoll() {
        if (closed.get()) return;
        try {
            pollOnce();
        } catch (RuntimeException failure) {
            log.warn("Configuration delivery poll failed; failureType={}", failure.getClass().getSimpleName());
        } finally {
            if (!closed.get()) {
                long interval = policy.pollInterval().toMillis();
                long cap = Math.max(interval, Duration.ofMinutes(5).toMillis());
                long backedOff = Math.min(cap, interval << Math.min(consecutiveFailures, 5));
                long jitter = ThreadLocalRandom.current().nextLong(-backedOff / 5, backedOff / 5 + 1);
                scheduler.schedule(this::scheduledPoll, backedOff + jitter, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** One complete poll and at most one receipt; scheduled and test calls use the same serialized path. */
    public synchronized void pollOnce() {
        if (closed.get()) return;
        initialize();
        try {
            var response = client.snapshot(bootId, etag);
            establishIncarnation(response.containerId());
            if (response.snapshot() == null) {
                confirmUnchanged(response);
            } else {
                acceptFresh(response);
            }
            deliverPending(response);
            consecutiveFailures = 0;
        } catch (DdcConfigSnapshotHttpClient.DeliveryFailure failure) {
            consecutiveFailures++;
            handleRemoteFailure(failure);
        } catch (DdcConfigLkgStore.LkgStoreException failure) {
            consecutiveFailures++;
            storageIndeterminate |= failure.durabilityIndeterminate();
            forceHold(DeliveryStatusEnum.STORAGE_UNAVAILABLE, failure.code());
        } catch (RuntimeException failure) {
            consecutiveFailures++;
            forceHold(DeliveryStatusEnum.INVALID, "CONFIG_DELIVERY_RESULT_INVALID");
            log.debug("Configuration delivery stopped this poll; failureType={}", failure.getClass().getSimpleName());
        } finally {
            updateAvailability(false);
        }
    }

    private void initialize() {
        if (initialized) return;
        initialized = true;
        var recovery = baseline.recovery();
        try {
            var existing = store.read(scopeKey).orElse(null);
            boolean isolated = recovery.isolated() || existing != null && existing.isolated();
            String floor = maximumRevision(recovery.highestDisabledRevision(), existing == null ? null : existing.disabledRevision());
            var snapshot = !isolated && recovery.recovered()
                    && existing != null && existing.applied() != null
                    && existing.applied().getReleaseRevision().equals(Long.toString(applier.currentSnapshot().version()))
                    && existing.applied().getContentSha256().equals(applier.currentSnapshot().checksum())
                    ? existing.applied() : null;
            var previousVerification = snapshot == null ? null : existing.lastVerifiedAt();
            state = newState(floor, isolated, snapshot, previousVerification, 0L, null);
        } catch (DdcConfigLkgStore.LkgStoreException failure) {
            forceHold(DeliveryStatusEnum.STORAGE_UNAVAILABLE, failure.code());
            return;
        }
        if (state.isolated()) {
            forceHold(DeliveryStatusEnum.ISOLATED, "CONFIG_SCOPE_ISOLATED");
        } else if (policy.allowOfflineStart() && !recovery.invalid() && state.applied() != null) {
            allowUntil(earliest(state.applied().getValidUntil(), state.lastVerifiedAt().plus(policy.maxStale())));
            observe(DeliveryStatusEnum.STALE, state.applied().getReleaseRevision(), "CONFIG_LKG_AWAITING_CONFIRMATION");
        } else if (policy.allowOfflineStart() && !recovery.invalid() && policy.baselineValidUntil() != null
                && applier.currentSnapshot().version() > 0 && state.disabledRevision() == null) {
            allowUntil(policy.baselineValidUntil());
            observe(DeliveryStatusEnum.BASELINE, baseline.source().getRevision(), "CONFIG_BASELINE_AWAITING_CONFIRMATION");
        } else {
            observe(DeliveryStatusEnum.WAITING_FOR_AUTHORITY, baseline.source().getRevision(), null);
        }
    }

    private void establishIncarnation(String containerId) {
        if (incarnation != null && !incarnation.containerId().equals(containerId)) {
            throw new IllegalStateException("CONFIG_DELIVERY_CONTAINER_CHANGED");
        }
        incarnation = new IncarnationVO(policy.podUid(), containerId, bootId);
    }

    private void confirmUnchanged(DdcConfigSnapshotHttpClient.SnapshotResultVO response) {
        var applied = state.applied();
        if (applied == null || etag == null || !etag.equals(response.etag()) || state.isolated()
                || !clock.instant().isBefore(response.validUntil())) {
            throw new IllegalArgumentException("CONFIG_UNCONDITIONAL_SNAPSHOT_REQUIRED");
        }
        var confirmed = new DdcConfigSnapshotDTO(applied.getSchemaVersion(), applied.getScope(), applied.getReleaseRevision(),
                applied.getEnabled(), applied.getRestartDigest(), applied.getContentSha256(), applied.getHotValues(),
                applied.getDependencySnapshotId(), response.validUntil());
        var now = clock.instant();
        var candidate = newState(state.disabledRevision(), false, confirmed, now, state.lastReportSequence(), state.pendingReceipt());
        store.write(candidate);
        state = candidate;
        updateReady(confirmed, now, response.etag());
    }

    private void acceptFresh(DdcConfigSnapshotHttpClient.SnapshotResultVO response) {
        var snapshot = Objects.requireNonNull(response.snapshot());
        if (!baseline.scope().equals(snapshot.getScope()) || !snapshot.getContentSha256().equals(DdcConfigCanonicalJson.contentSha256(snapshot))
                || !DdcConfigCanonicalJson.etag(snapshot).equals(response.etag()) || !response.validUntil().equals(snapshot.getValidUntil())
                || !clock.instant().isBefore(snapshot.getValidUntil())) {
            throw new IllegalArgumentException("CONFIG_SNAPSHOT_RESULT_INVALID");
        }
        if (!allowedAfterDisable(snapshot)) {
            forceHold(DeliveryStatusEnum.INVALID, "CONFIG_DISABLED_REVISION_FENCE");
            return;
        }
        var pending = state.pendingReceipt();
        if (pending != null && sameReportedTarget(pending, snapshot)
                && !DdcConfigReceiptStatusEnum.fromCode(pending.path("status").textValue()).isSuccessful()) {
            // Keep an unconfirmed result byte-for-byte through an interrupted transport attempt.
            return;
        }
        if (!snapshot.getRestartDigest().equals(baseline.restartDigest())) {
            persistFailure(snapshot, DdcConfigReceiptStatusEnum.RESTART_REQUIRED, "CONFIG_RESTART_REQUIRED");
            return;
        }
        Instant now = clock.instant();
        try {
            applier.applySnapshot(baseline.scope(), baseline.restartDigest(), baseline.rules(), snapshot,
                    snapshot.getReleaseRevision(), ignored -> persistSuccess(snapshot, now));
        } catch (DdcConfigLkgStore.LkgStoreException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            String reason = failure.getMessage();
            if ("CONFIG_HOT_ROLLBACK_FAILED".equals(reason)) {
                forceHold(DeliveryStatusEnum.STORAGE_UNAVAILABLE, "CONFIG_HOT_ROLLBACK_FAILED");
                return;
            }
            boolean requiresRestart = reason != null && RESTART_CODES.contains(reason);
            persistFailure(snapshot, requiresRestart ? DdcConfigReceiptStatusEnum.RESTART_REQUIRED
                    : DdcConfigReceiptStatusEnum.REJECTED, requiresRestart ? reason : "CONFIG_HOT_SNAPSHOT_REJECTED");
            return;
        }
        updateReady(snapshot, now, response.etag());
    }

    private void persistSuccess(DdcConfigSnapshotDTO snapshot, Instant now) {
        JsonNode pending = state.pendingReceipt();
        long sequence = state.lastReportSequence();
        if (storageIndeterminate) {
            // After fresh authority and the re-application, reconcile the exact visible pending body.
            // Persist it again before offering it to the network. A read-back alone does not prove durability.
            var onDisk = store.read(scopeKey).orElse(null);
            if (onDisk != null && incarnation != null && incarnation.equals(onDisk.reportIncarnation())
                    && onDisk.lastReportSequence() >= sequence) {
                sequence = onDisk.lastReportSequence();
                pending = onDisk.pendingReceipt();
            }
        }
        var status = snapshot.getEnabled() ? DdcConfigReceiptStatusEnum.APPLIED : DdcConfigReceiptStatusEnum.DISABLED_APPLIED;
        boolean alreadyReported = new ResultKeyVO(snapshot.getReleaseRevision(), snapshot.getContentSha256(),
                status.getCode(), null).equals(acknowledgedResult);
        if (alreadyReported) {
            pending = null;
        } else if (pending == null || !sameReportedTarget(pending, snapshot)
                || !status.getCode().equals(pending.path("status").textValue())) {
            sequence = Math.addExact(sequence, 1L);
            pending = receipt(snapshot, status, null, now, sequence);
        }
        var candidate = newState(disabledFloor(snapshot), false, snapshot, now, sequence, pending);
        store.writeAfterVerifiedSnapshot(candidate);
        state = candidate;
        storageIndeterminate = false;
    }

    private void persistFailure(DdcConfigSnapshotDTO desired, DdcConfigReceiptStatusEnum status, String reason) {
        var result = new ResultKeyVO(desired.getReleaseRevision(), desired.getContentSha256(), status.getCode(), reason);
        if (!result.equals(acknowledgedResult)) {
            long sequence = Math.addExact(state.lastReportSequence(), 1L);
            var pending = receipt(desired, status, reason, clock.instant(), sequence);
            var candidate = newState(disabledFloor(desired), state.isolated(),
                    desired.getEnabled() ? state.applied() : null, desired.getEnabled() ? state.lastVerifiedAt() : null, sequence, pending);
            store.write(candidate);
            state = candidate;
        }
        etag = null;
        if (!desired.getEnabled() || state.isolated() || storageIndeterminate) {
            forceHold(status == DdcConfigReceiptStatusEnum.RESTART_REQUIRED
                    ? DeliveryStatusEnum.RESTART_REQUIRED : DeliveryStatusEnum.INVALID, reason);
        } else {
            observe(status == DdcConfigReceiptStatusEnum.RESTART_REQUIRED
                    ? DeliveryStatusEnum.RESTART_REQUIRED : DeliveryStatusEnum.INVALID, desired.getReleaseRevision(), reason);
        }
    }

    private void deliverPending(DdcConfigSnapshotHttpClient.SnapshotResultVO response) {
        var pending = state.pendingReceipt();
        var target = response.snapshot() == null ? state.applied() : response.snapshot();
        if (pending == null || storageIndeterminate || incarnation == null || state.isolated()
                || target == null || !sameReportedTarget(pending, target)) return;
        // The durable object is credential-free. Construct the authenticated wire command only for this call.
        var report = JSON.convertValue(pending, DdcConfigReceiptCommand.class).setDeliveryToken(response.deliveryToken());
        String idempotencyKey = policy.podUid() + "/" + incarnation.containerId() + "/" + bootId + "/"
                + report.getReleaseRevision() + "/" + report.getReportSeq();
        client.report(bootId, idempotencyKey, report);
        acknowledgedResult = new ResultKeyVO(report.getReleaseRevision(), report.getContentSha256(),
                report.getStatus().getCode(), report.getErrorCode());
        var acknowledged = newState(state.disabledRevision(), false, state.applied(), state.lastVerifiedAt(),
                state.lastReportSequence(), null);
        store.write(acknowledged);
        state = acknowledged;
    }

    private record ResultKeyVO(String revision, String digest, String status, @Nullable String reason) { }

    private void handleRemoteFailure(DdcConfigSnapshotHttpClient.DeliveryFailure failure) {
        switch (failure.kind()) {
            case FORBIDDEN -> {
                forceHold(DeliveryStatusEnum.ISOLATED, failure.errorCode());
                var isolated = newState(state.disabledRevision(), true, null, null, state.lastReportSequence(), null);
                state = isolated;
                acknowledgedResult = null;
                try {
                    store.write(isolated);
                } catch (DdcConfigLkgStore.LkgStoreException persistence) {
                    storageIndeterminate |= persistence.durabilityIndeterminate();
                    observe(DeliveryStatusEnum.STORAGE_UNAVAILABLE, null, persistence.code());
                }
            }
            case UNAVAILABLE -> observe(DeliveryStatusEnum.STALE, null, failure.errorCode());
            case UNAUTHENTICATED -> forceHold(DeliveryStatusEnum.WAITING_FOR_AUTHORITY, failure.errorCode());
            case CONFLICT, INVALID_RESPONSE, NOT_FOUND -> forceHold(DeliveryStatusEnum.INVALID, failure.errorCode());
        }
    }

    private JsonNode receipt(DdcConfigSnapshotDTO desired, DdcConfigReceiptStatusEnum status,
            @Nullable String reason, Instant now, long sequence) {
        var applied = applier.currentSnapshot();
        String revision = status.isSuccessful() ? desired.getReleaseRevision()
                : applied.version() > 0 ? Long.toString(applied.version()) : null;
        String digest = status.isSuccessful() ? desired.getContentSha256()
                : revision != null && applied.checksum().matches("sha256:[0-9a-f]{64}") ? applied.checksum() : null;
        var report = DdcConfigReceiptCommand.builder().schemaVersion("1").bootId(bootId)
                .releaseRevision(desired.getReleaseRevision()).reportSeq(Long.toString(sequence))
                .appliedRevision(digest == null ? null : revision).contentSha256(desired.getContentSha256())
                .appliedContentSha256(digest).baselineRestartDigest(baseline.restartDigest())
                .status(status).reportedAt(now).appliedAt(status.isSuccessful() ? now : null).errorCode(reason).build();
        report.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(report));
        ObjectNode node = JSON.valueToTree(report);
        node.remove("deliveryToken");
        return node;
    }

    private StateVO newState(@Nullable String disabledRevision, boolean isolated, @Nullable DdcConfigSnapshotDTO applied,
            @Nullable Instant verified, long sequence, @Nullable JsonNode pending) {
        return new StateVO(scopeKey, Long.parseLong(baseline.targetBinding().getBindingRevision()), baseline.restartDigest(),
                policy.podUid(), disabledRevision, isolated, applied, verified, incarnation, sequence, pending);
    }

    private boolean allowedAfterDisable(DdcConfigSnapshotDTO snapshot) {
        if (state.disabledRevision() == null) return true;
        long revision = Long.parseLong(snapshot.getReleaseRevision());
        long floor = Long.parseLong(state.disabledRevision());
        return revision > floor || revision == floor && !snapshot.getEnabled();
    }

    private String disabledFloor(DdcConfigSnapshotDTO desired) {
        if (desired.getEnabled()) return state.disabledRevision();
        long floor = state.disabledRevision() == null ? 0 : Long.parseLong(state.disabledRevision());
        return Long.toString(Math.max(floor, Long.parseLong(desired.getReleaseRevision())));
    }

    @Nullable
    private static String maximumRevision(@Nullable String left, @Nullable String right) {
        if (left == null) return right;
        if (right == null) return left;
        return Long.parseLong(left) >= Long.parseLong(right) ? left : right;
    }

    private boolean sameReportedTarget(JsonNode pending, DdcConfigSnapshotDTO snapshot) {
        return snapshot.getReleaseRevision().equals(pending.path("releaseRevision").textValue())
                && snapshot.getContentSha256().equals(pending.path("contentSha256").textValue());
    }

    private void updateReady(DdcConfigSnapshotDTO snapshot, Instant now, String strongEtag) {
        if (storageIndeterminate) return;
        etag = strongEtag;
        allowUntil(earliest(snapshot.getValidUntil(), now.plus(policy.maxStale())));
        observe(DeliveryStatusEnum.READY, snapshot.getReleaseRevision(), null);
    }

    private void allowUntil(Instant until) {
        readyUntil = until;
        awaitingAuthority = false;
    }

    private void forceHold(DeliveryStatusEnum status, @Nullable String reason) {
        awaitingAuthority = true;
        readyUntil = Instant.EPOCH;
        etag = null;
        observe(status, null, reason);
    }

    private void observe(DeliveryStatusEnum status, @Nullable String targetRevision, @Nullable String code) {
        long appliedVersion = applier.currentSnapshot().version();
        observations.updateDeliveryObservation(new DeliveryObservationVO(status,
                appliedVersion > 0 ? Long.toString(appliedVersion) : null, targetRevision, state.lastVerifiedAt(),
                state.applied() == null ? null : state.applied().getValidUntil(), ready(), code));
    }

    private void updateAvailability(boolean force) {
        boolean allowed = ready();
        if (publishedReady.getAndSet(allowed) != allowed || force) {
            if (!allowed && !closed.get() && !awaitingAuthority) {
                var previous = observations.deliveryObservation();
                observations.updateDeliveryObservation(new DeliveryObservationVO(DeliveryStatusEnum.STALE,
                        previous.appliedRevision(), previous.targetRevision(), previous.verifiedAt(), previous.validUntil(),
                        false, "CONFIG_VALIDITY_EXPIRED"));
            }
            AvailabilityChangeEvent.publish(publisher, this, allowed ? ReadinessState.ACCEPTING_TRAFFIC : ReadinessState.REFUSING_TRAFFIC);
        }
    }

    private static Instant earliest(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduler.shutdownNow();
        }
    }
}
