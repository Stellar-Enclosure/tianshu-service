package top.egon.cola.component.tianshu.admin.kubernetes;

import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.*;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import org.slf4j.MDC;
import org.junit.jupiter.api.AfterEach;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DdcConfigWorkloadObservationExporterTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(1001L, "tenant-1001", "observer:test");
    private static final TargetBindingVO TARGET = new TargetBindingVO("orders-target", "2", "cluster-a", "apps-prod", "Deployment", "orders", "workload-uid", "app");
    @AfterEach void cleanup() { MDC.clear(); }
    @Test
    void exporterOwnsLifecycleAndBackgroundExportWithoutQuerySideEffects() throws Exception {
        Class<?> exporter = Class.forName("top.egon.cola.component.tianshu.admin.kubernetes.DdcConfigWorkloadObservationExporter");
        assertThat(SmartLifecycle.class.isAssignableFrom(exporter)).isTrue();
        assertThat(exporter.getMethod("exportOnce")).isNotNull();
    }

    @Test void exportsEpochSequenceInvalidationAndShutdownWithTrustedRestoredTenantContext() throws Exception {
        var observer = mock(DdcKubernetesObservationService.class);
        var repository = mock(DdcConfigWorkloadObservationRepository.class);
        var source = new AtomicReference<>(view(StatusEnum.FRESH));
        when(observer.currentView(TARGET)).thenAnswer(invocation -> source.get());
        when(repository.nextWriterEpoch(TENANT)).thenReturn(30L);
        var delivered = new CopyOnWriteArrayList<DeliveryVO>();
        var first = new CountDownLatch(1);
        when(repository.upsert(any(), any(), anyLong(), anyLong(), any(), any())).thenAnswer(invocation -> {
            assertThat(MDC.get("tenantId")).isEqualTo("1001");
            assertThat(MDC.get("userId")).isEqualTo(TENANT.actor());
            WorkloadViewVO view = invocation.getArgument(5);
            delivered.add(new DeliveryVO(invocation.getArgument(2), invocation.getArgument(3), view.status()));
            if (view.fresh()) first.countDown();
            return AcceptanceEnum.ADVANCED;
        });
        var exporter = new DdcConfigWorkloadObservationExporter(observer, repository, new DdcTenantContextExecutor(),
                () -> List.of(new DdcConfigWorkloadObservationExporter.BindingVO(TENANT, TARGET)), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        MDC.put("tenantId", "9009"); MDC.put("userId", "original");
        try {
            exporter.start(); exporter.start();
            assertThat(first.await(3, TimeUnit.SECONDS)).isTrue();
            exporter.exportOnce();
            assertThat(exporter.isHealthy()).isTrue();
            source.set(view(StatusEnum.UNAVAILABLE));
            exporter.exportOnce();
            assertThat(exporter.isHealthy()).isFalse();
            assertThat(exporter.lastErrorCode()).contains("SOURCE_UNAVAILABLE");
            assertThat(MDC.get("tenantId")).isEqualTo("9009");
        } finally { exporter.close(); }
        assertThat(delivered.getFirst().status()).isEqualTo(StatusEnum.INITIALIZING);
        assertThat(delivered.getLast().status()).isEqualTo(StatusEnum.CLOSED);
        assertThat(delivered).extracting(DeliveryVO::epoch).containsOnly(30L);
        for (int i = 0; i < delivered.size(); i++) assertThat(delivered.get(i).sequence()).isEqualTo(i + 1);
        verify(repository, times(1)).nextWriterEpoch(TENANT);
        verify(observer, never()).close();
    }

    @Test void missingSequenceFailsStartupClosedAndDoesNotInventAnEpoch() {
        var repository = mock(DdcConfigWorkloadObservationRepository.class);
        when(repository.nextWriterEpoch(TENANT)).thenThrow(new DdcConfigWorkloadObservationRepository.StoreUnavailableException());
        var exporter = new DdcConfigWorkloadObservationExporter(mock(DdcKubernetesObservationService.class), repository,
                new DdcTenantContextExecutor(), () -> List.of(new DdcConfigWorkloadObservationExporter.BindingVO(TENANT, TARGET)),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        assertThatThrownBy(exporter::start).isInstanceOf(DdcConfigWorkloadObservationRepository.StoreUnavailableException.class);
        assertThat(exporter.isRunning()).isFalse();
        assertThat(exporter.isHealthy()).isFalse();
        assertThat(exporter.lastErrorCode()).isEqualTo("TIANSHU_CONFIG_OBSERVATION_STARTUP_UNAVAILABLE");
        verify(repository, never()).upsert(any(), any(), anyLong(), anyLong(), any(), any());
        exporter.close();
    }

    @Test void aServiceOnlyWorkerCanLeaveTheWorkloadExporterIdle() {
        var observer = mock(DdcKubernetesObservationService.class);
        var repository = mock(DdcConfigWorkloadObservationRepository.class);
        try (var exporter = new DdcConfigWorkloadObservationExporter(observer, repository,
                new DdcTenantContextExecutor(), List::of, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(2))) {
            exporter.start();
            assertThat(exporter.isRunning()).isFalse();
        }
        verifyNoInteractions(observer, repository);
    }

    @Test void olderSourceConflictStopsWithoutReclaimOrClobberingNewerShutdownState() {
        var repository = mock(DdcConfigWorkloadObservationRepository.class);
        when(repository.nextWriterEpoch(TENANT)).thenReturn(10L);
        when(repository.upsert(any(), any(), anyLong(), anyLong(), any(), any())).thenReturn(AcceptanceEnum.CONFLICT);
        var exporter = new DdcConfigWorkloadObservationExporter(mock(DdcKubernetesObservationService.class), repository,
                new DdcTenantContextExecutor(), () -> List.of(new DdcConfigWorkloadObservationExporter.BindingVO(TENANT, TARGET)),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        assertThatThrownBy(exporter::start).isInstanceOf(IllegalStateException.class);
        assertThat(exporter.lastErrorCode()).isEqualTo("TIANSHU_CONFIG_OBSERVATION_WRITER_FENCED");
        exporter.close();
        verify(repository, times(1)).nextWriterEpoch(TENANT);
        verify(repository, times(1)).upsert(any(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void unavailableAuthorityBlocksTheWriterEpochAndStartupInvalidation() {
        var repository = mock(DdcConfigWorkloadObservationRepository.class);
        var observer = mock(DdcKubernetesObservationService.class);
        try (var exporter = managedExporter(observer, repository, new AtomicBoolean(false))) {
            assertThatThrownBy(exporter::start).hasMessage("lease unavailable");
            assertThat(exporter.isHealthy()).isFalse();
        }
        verifyNoInteractions(repository);
    }

    @Test
    void lostAuthorityStopsPeriodicAndShutdownWrites() throws Exception {
        var observer = mock(DdcKubernetesObservationService.class);
        var repository = mock(DdcConfigWorkloadObservationRepository.class);
        var lease = new AtomicBoolean(true);
        var initialExport = new CountDownLatch(1);
        when(observer.currentView(TARGET)).thenReturn(view(StatusEnum.FRESH));
        when(repository.nextWriterEpoch(TENANT)).thenReturn(30L);
        when(repository.upsert(any(), any(), anyLong(), anyLong(), any(), any())).thenAnswer(invocation -> {
            WorkloadViewVO observation = invocation.getArgument(5);
            if (observation.fresh()) initialExport.countDown();
            return AcceptanceEnum.ADVANCED;
        });
        try (var exporter = managedExporter(observer, repository, lease)) {
            exporter.start();
            assertThat(initialExport.await(3, TimeUnit.SECONDS)).isTrue();
            lease.set(false);
            clearInvocations(repository);
            assertThatThrownBy(exporter::exportOnce).hasMessage("lease unavailable");
            assertThat(exporter.isHealthy()).isFalse();
        }
        verifyNoInteractions(repository);
    }

    private static DdcConfigWorkloadObservationExporter managedExporter(
            DdcKubernetesObservationService observer, DdcConfigWorkloadObservationRepository repository,
            AtomicBoolean lease) {
        return new DdcConfigWorkloadObservationExporter(observer, repository, new DdcTenantContextExecutor(),
                () -> List.of(new DdcConfigWorkloadObservationExporter.BindingVO(TENANT, TARGET)),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30), () -> {
                    if (!lease.get()) throw new IllegalStateException("lease unavailable");
                });
    }

    private static WorkloadViewVO view(StatusEnum status) {
        return new WorkloadViewVO(TARGET, status, status == StatusEnum.FRESH ? null : "SOURCE_UNAVAILABLE",
                NOW.minusSeconds(1), NOW.plusSeconds(60), status == StatusEnum.FRESH ? null : NOW, "workload-uid", 5, 5, 0,
                List.of(), List.of(), Map.of(), Map.of());
    }
    private record DeliveryVO(long epoch,long sequence,StatusEnum status) {}
}
