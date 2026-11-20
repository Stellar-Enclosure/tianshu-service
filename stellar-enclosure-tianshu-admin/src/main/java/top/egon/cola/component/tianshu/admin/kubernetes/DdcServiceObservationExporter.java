package top.egon.cola.component.tianshu.admin.kubernetes;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum;
import top.egon.cola.component.tianshu.admin.repository.DdcServiceObservationRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Stores observations from the already authorized worker scope for the separate Admin process. */
@Slf4j
@RequiredArgsConstructor
public class DdcServiceObservationExporter implements SmartLifecycle, AutoCloseable {
    private static final String WRITER_FENCED = "TIANSHU_SERVICE_OBSERVATION_WRITER_FENCED";
    @NonNull private final DdcKubernetesObservationService observer;
    @NonNull private final DdcServiceObservationRepository repository;
    @NonNull private final DdcTenantContextExecutor tenants;
    @NonNull private final List<DdcServiceObservationBindingVO> bindings;
    @NonNull private final Clock clock;
    @NonNull private final Duration interval;
    @NonNull private final Runnable writeAuthority;
    private final List<Assignment> assignments = new ArrayList<>();
    private volatile boolean running;
    private volatile Instant readyUntil = Instant.MIN;
    private boolean started;
    private boolean closed;
    private boolean fenced;
    private ScheduledExecutorService worker;

    public DdcServiceObservationExporter(DdcKubernetesObservationService observer,
            DdcServiceObservationRepository repository, DdcTenantContextExecutor tenants,
            List<DdcServiceObservationBindingVO> bindings, Clock clock, Duration interval) {
        this(observer, repository, tenants, bindings, clock, interval, () -> { });
    }

    @Override public synchronized void start() {
        if (running || bindings.isEmpty()) return;
        if (started || closed) throw new IllegalStateException("TIANSHU_SERVICE_OBSERVATION_EXPORTER_CLOSED");
        if (interval.compareTo(Duration.ofMillis(100)) < 0 || interval.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("TIANSHU_SERVICE_OBSERVATION_EXPORT_INTERVAL_INVALID");
        }
        started = true;
        bindings.forEach(binding -> assignments.add(new Assignment(binding)));
        Map<Long, Long> epochs = new HashMap<>();
        for (Assignment assignment : assignments) {
            var tenant = assignment.tenant;
            assignment.epoch = epochs.computeIfAbsent(tenant.tenantId(), ignored -> tenants.call(tenant, () -> {
                writeAuthority.run();
                return repository.nextWriterEpoch(tenant);
            }));
            if (assignment.epoch <= 0) throw new IllegalStateException("TIANSHU_SERVICE_OBSERVATION_EPOCH_UNAVAILABLE");
            publish(assignment, unknown(StatusEnum.INITIALIZING, "INITIALIZING"));
        }
        worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true)
                .name("tianshu-service-observation-export").factory());
        running = true;
        worker.scheduleWithFixedDelay(this::tick, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Explicit worker entry using the same path as the scheduled export. */
    public synchronized void exportOnce() {
        if (!running) return;
        Instant freshUntil = Instant.MAX;
        for (Assignment assignment : assignments) {
            DdcServiceObservationViewVO observation;
            try {
                observation = observer.currentServiceView(assignment.binding);
            } catch (RuntimeException failure) {
                observation = unknown(StatusEnum.UNAVAILABLE, "SOURCE_UNAVAILABLE");
            }
            try {
                AcceptanceEnum acceptance = publish(assignment, observation);
                Instant validUntil = observation.validUntil();
                if (!observation.fresh() || validUntil == null
                        || (acceptance != AcceptanceEnum.ADVANCED && acceptance != AcceptanceEnum.SAME)) {
                    freshUntil = Instant.MIN;
                } else if (validUntil.isBefore(freshUntil)) {
                    freshUntil = validUntil;
                }
            } catch (RuntimeException failure) {
                readyUntil = Instant.MIN;
                freshUntil = Instant.MIN;
                if (fenced) throw failure;
                log.warn("Service observation export failed; failureType={}", failure.getClass().getSimpleName());
            }
        }
        readyUntil = freshUntil;
    }

    private void tick() {
        try {
            exportOnce();
        } catch (RuntimeException failure) {
            log.warn("Service observation export stopped; failureType={}", failure.getClass().getSimpleName());
        }
    }

    private AcceptanceEnum publish(Assignment assignment, DdcServiceObservationViewVO view) {
        long sequence = Math.incrementExact(assignment.sequence);
        assignment.sequence = sequence;
        var tenant = assignment.tenant;
        var capturedAt = clock.instant();
        AcceptanceEnum acceptance = tenants.call(tenant, () -> {
            writeAuthority.run();
            return repository.upsert(tenant, assignment.binding, assignment.epoch, sequence, capturedAt, view);
        });
        if (acceptance == AcceptanceEnum.CONFLICT) {
            fenced = true;
            running = false;
            if (worker != null) worker.shutdown();
            throw new IllegalStateException(WRITER_FENCED);
        }
        return acceptance;
    }

    private static DdcServiceObservationViewVO unknown(StatusEnum status, String reason) {
        return new DdcServiceObservationViewVO(status, reason, null, null, null, List.of());
    }

    @Override public synchronized void stop() {
        if (closed) return;
        closed = true;
        running = false;
        if (worker != null) worker.shutdown();
        for (Assignment assignment : assignments) {
            if (fenced) break;
            if (assignment.epoch <= 0) continue;
            try {
                publish(assignment, unknown(StatusEnum.CLOSED, "CLOSED"));
            } catch (RuntimeException failure) {
                log.warn("Service observation shutdown invalidation failed; failureType={}", failure.getClass().getSimpleName());
            }
        }
    }

    @Override public boolean isRunning() { return running; }

    public boolean isReady() {
        return bindings.isEmpty() || (running && clock.instant().isBefore(readyUntil));
    }
    @Override public void close() { stop(); }

    private static final class Assignment {
        private final DdcServiceObservationBindingVO binding;
        private final DdcTenantContextDTO tenant;
        private long epoch;
        private long sequence;

        private Assignment(DdcServiceObservationBindingVO binding) {
            this.binding = binding;
            this.tenant = new DdcTenantContextDTO(binding.tenantId(), binding.identityTenantId(), "system:service-observer");
        }
    }
}
