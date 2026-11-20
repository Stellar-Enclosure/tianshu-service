package top.egon.cola.component.tianshu.admin.kubernetes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.WorkloadViewVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Publishes already-collected evidence across process boundaries; it owns no Kubernetes API credential. */
@Slf4j @RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigWorkloadObservationExporter implements SmartLifecycle, AutoCloseable {
    @NonNull @Qualifier("ddcKubernetesObservationService") private final DdcKubernetesObservationService observer;
    @NonNull @Qualifier("ddcConfigWorkloadObservationRepository") private final DdcConfigWorkloadObservationRepository repository;
    @NonNull @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor tenants;
    @NonNull @Qualifier("ddcConfigWorkloadObservationBindings") private final BindingResolver bindings;
    @NonNull @Qualifier("ddcPublishClock") private final Clock clock;
    @NonNull @Qualifier("ddcConfigWorkloadObservationExportInterval") private final Duration interval;
    @NonNull @Qualifier("ddcObserverIdLeaseCheck") private final Runnable writeAuthority;
    private final Object lock = new Object();
    private final List<Assignment> assignments = new ArrayList<>();
    private volatile boolean running;
    private volatile boolean closed;
    private volatile boolean healthy;
    private volatile String lastErrorCode;
    private long epoch;
    private ScheduledExecutorService worker;

    @FunctionalInterface public interface BindingResolver { List<BindingVO> bindings(); }
    public record BindingVO(@NotNull @Valid DdcTenantContextDTO tenant, @NotNull @Valid TargetBindingVO target) {
        public BindingVO {
            java.util.Objects.requireNonNull(tenant, "tenant");
            java.util.Objects.requireNonNull(target, "target");
            if (tenant.actor().length() > 128) throw new IllegalArgumentException("TIANSHU_CONFIG_OBSERVATION_ACTOR_INVALID");
        }
    }

    public DdcConfigWorkloadObservationExporter(DdcKubernetesObservationService observer,
            DdcConfigWorkloadObservationRepository repository, DdcTenantContextExecutor tenants,
            BindingResolver bindings, Clock clock, Duration interval) {
        this(observer, repository, tenants, bindings, clock, interval, () -> { });
    }

    @Override public void start() {
        synchronized (lock) {
            if (running) return;
            if (closed || epoch != 0) throw new IllegalStateException("TIANSHU_CONFIG_OBSERVATION_EXPORTER_CLOSED");
            if (interval.compareTo(Duration.ofMillis(100)) < 0 || interval.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_OBSERVATION_EXPORT_INTERVAL_INVALID");
            }
            List<BindingVO> approved = List.copyOf(bindings.bindings());
            if (approved.isEmpty()) return;
            if (approved.size() > 128) throw new IllegalArgumentException("TIANSHU_CONFIG_OBSERVATION_BINDINGS_INVALID");
            Set<List<String>> keys = new HashSet<>();
            for (var binding : approved) {
                var target = binding.target();
                if (!keys.add(List.of(binding.tenant().tenantId().toString(), target.getBindingId(), target.getBindingRevision(),
                        target.getClusterId(), target.getKubernetesNamespace(), target.getWorkloadUid()))) {
                    throw new IllegalArgumentException("TIANSHU_CONFIG_OBSERVATION_BINDINGS_DUPLICATE");
                }
            }
            try {
                var first = approved.getFirst().tenant();
                epoch = tenants.call(first, () -> {
                    assertWriteAuthority();
                    return repository.nextWriterEpoch(first);
                });
                if (epoch <= 0) throw new IllegalStateException("TIANSHU_CONFIG_OBSERVATION_EPOCH_UNAVAILABLE");
                approved.forEach(binding -> assignments.add(new Assignment(binding)));
                // A higher process epoch invalidates all configured old views before this source can be healthy.
                for (var assignment : assignments) publish(assignment, unknown(assignment.binding.target(), StatusEnum.INITIALIZING, "INITIALIZING"));
            } catch (RuntimeException unavailable) {
                healthy = false;
                if (lastErrorCode == null) lastErrorCode = "TIANSHU_CONFIG_OBSERVATION_STARTUP_UNAVAILABLE";
                throw unavailable;
            }
            worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true)
                    .name("tianshu-workload-observation-export").factory());
            running = true;
            worker.scheduleWithFixedDelay(this::tick, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Explicit background entry. Administrative QUERY paths never call it. */
    public void exportOnce() {
        synchronized (lock) {
            if (!running || closed) throw new IllegalStateException("TIANSHU_CONFIG_OBSERVATION_EXPORTER_NOT_RUNNING");
            healthy = true;
            for (var assignment : assignments) {
                WorkloadViewVO view;
                try { view = observer.currentView(assignment.binding.target()); }
                catch (RuntimeException unavailable) {
                    view = unknown(assignment.binding.target(), StatusEnum.UNAVAILABLE, "SOURCE_UNAVAILABLE");
                }
                publish(assignment, view);
                if (!view.fresh()) {
                    healthy = false;
                    lastErrorCode = "TIANSHU_CONFIG_OBSERVATION_SOURCE_" + view.status().name();
                }
            }
            if (healthy) lastErrorCode = null;
        }
    }

    private void tick() {
        try { exportOnce(); }
        catch (RuntimeException failure) {
            healthy = false;
            if (lastErrorCode == null) lastErrorCode = "TIANSHU_CONFIG_OBSERVATION_EXPORT_UNAVAILABLE";
            log.warn("Workload observation export is unavailable; failureType={}", failure.getClass().getSimpleName());
        }
    }

    private void publish(Assignment assignment, WorkloadViewVO view) {
        long sequence = Math.incrementExact(assignment.sequence);
        assignment.sequence = sequence;
        var context = assignment.binding.tenant();
        var captured = clock.instant();
        AcceptanceEnum result = tenants.call(context, () -> {
            assertWriteAuthority();
            return repository.upsert(context, assignment.binding.target(), epoch, sequence, captured, view);
        });
        if (result == AcceptanceEnum.CONFLICT) {
            healthy = false;
            lastErrorCode = "TIANSHU_CONFIG_OBSERVATION_WRITER_FENCED";
            running = false;
            if (worker != null) worker.shutdown();
            throw new IllegalStateException(lastErrorCode);
        }
        if (result == AcceptanceEnum.STALE) healthy = false;
    }

    private void assertWriteAuthority() {
        try {
            writeAuthority.run();
        } catch (RuntimeException unavailable) {
            healthy = false;
            lastErrorCode = "TIANSHU_CONFIG_OBSERVATION_ID_LEASE_UNAVAILABLE";
            throw unavailable;
        }
    }

    private WorkloadViewVO unknown(TargetBindingVO target, StatusEnum status, String reason) {
        return new WorkloadViewVO(target, status, reason, null, null, clock.instant(), target.getWorkloadUid(),
                0, 0, 0, List.of(), List.of(), Map.of(), Map.of());
    }

    @Override public void stop() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            running = false;
            healthy = false;
            if (worker != null) worker.shutdownNow();
            for (var assignment : assignments) {
                if (epoch <= 0 || "TIANSHU_CONFIG_OBSERVATION_WRITER_FENCED".equals(lastErrorCode)) break;
                try { publish(assignment, unknown(assignment.binding.target(), StatusEnum.CLOSED, "CLOSED")); }
                catch (RuntimeException unavailable) {
                    log.warn("Workload observation shutdown invalidation unavailable; failureType={}", unavailable.getClass().getSimpleName());
                }
            }
        }
    }
    @Override public boolean isRunning() { return running; }
    public boolean isHealthy() { return healthy && running; }
    public String lastErrorCode() { return lastErrorCode; }
    @Override public void close() { stop(); }
    private static final class Assignment {
        private final BindingVO binding;
        private long sequence;
        private Assignment(BindingVO binding) { this.binding = binding; }
    }
}
