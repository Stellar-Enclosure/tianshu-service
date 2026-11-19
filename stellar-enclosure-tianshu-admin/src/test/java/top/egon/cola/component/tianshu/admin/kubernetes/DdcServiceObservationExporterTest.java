package top.egon.cola.component.tianshu.admin.kubernetes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum;
import top.egon.cola.component.tianshu.admin.repository.DdcServiceObservationRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcServiceObservationExporterTest {
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final DdcServiceObservationBindingVO SERVICE = binding(7011, "orders");
    private final DdcKubernetesObservationService observer = mock(DdcKubernetesObservationService.class);
    private final DdcServiceObservationRepository repository = mock(DdcServiceObservationRepository.class);

    @AfterEach
    void clearTenant() {
        MDC.clear();
    }

    @Test
    void exportsUnderPerTenantEpochsAndRetiresOnlyItsOwnObservations() {
        var anotherService = binding(7011, "billing");
        var otherTenant = binding(7022, "catalog");
        var bindings = List.of(SERVICE, anotherService, otherTenant);
        var reports = new ArrayList<ReportVO>();
        when(repository.nextWriterEpoch(any())).thenAnswer(invocation -> {
            String tenant = MDC.get("tenantId");
            assertThat(tenant).isIn("7011", "7022");
            return Long.parseLong(tenant);
        });
        when(repository.upsert(any(), any(), anyLong(), anyLong(), any(), any())).thenAnswer(invocation -> {
            DdcServiceObservationBindingVO binding = invocation.getArgument(1);
            DdcServiceObservationViewVO view = invocation.getArgument(5);
            assertThat(MDC.get("tenantId")).isEqualTo(Long.toString(binding.tenantId()));
            assertThat(MDC.get("userId")).isEqualTo("system:service-observer");
            assertThat((Instant) invocation.getArgument(4)).isEqualTo(NOW);
            reports.add(new ReportVO(binding.bindingId(), invocation.getArgument(2), invocation.getArgument(3), view.status()));
            return AcceptanceEnum.ADVANCED;
        });
        var fresh = new DdcServiceObservationViewVO(StatusEnum.FRESH, null, NOW.minusSeconds(1),
                NOW.plusSeconds(5), "rv-1", List.of());
        for (var binding : bindings) when(observer.currentServiceView(binding)).thenReturn(fresh);
        MDC.put("tenantId", "9999");
        try (var exporter = exporter(bindings)) {
            exporter.start();
            exporter.start();
            exporter.exportOnce();
            when(observer.currentServiceView(SERVICE)).thenThrow(new IllegalStateException());
            exporter.exportOnce();
        }
        assertThat(MDC.get("tenantId")).isEqualTo("9999");
        verify(repository, times(2)).nextWriterEpoch(any());
        assertThat(reports.stream().filter(report -> SERVICE.bindingId().equals(report.bindingId())).toList()).containsExactly(
                new ReportVO("orders", 7011, 1, StatusEnum.INITIALIZING),
                new ReportVO("orders", 7011, 2, StatusEnum.FRESH),
                new ReportVO("orders", 7011, 3, StatusEnum.UNAVAILABLE),
                new ReportVO("orders", 7011, 4, StatusEnum.CLOSED));
        assertThat(reports.stream().filter(report -> "catalog".equals(report.bindingId())).toList())
                .extracting(ReportVO::epoch).containsOnly(7022L);
    }

    @Test
    void aFencedWriterStopsWithoutReacquiringTheEpochOrPublishingShutdown() {
        when(repository.nextWriterEpoch(any())).thenReturn(10L);
        when(repository.upsert(any(), any(), anyLong(), anyLong(), any(), any())).thenReturn(AcceptanceEnum.CONFLICT);
        try (var exporter = exporter(List.of(SERVICE))) {
            assertThatThrownBy(exporter::start).hasMessage("TIANSHU_SERVICE_OBSERVATION_WRITER_FENCED");
            assertThat(exporter.isRunning()).isFalse();
            exporter.exportOnce();
        }
        verify(repository).nextWriterEpoch(any());
        verify(repository).upsert(any(), any(), anyLong(), anyLong(), any(), any());
        verifyNoInteractions(observer);
    }

    @Test
    void noLocalBindingNeedsNeitherAnEpochNorAnObservation() {
        try (var exporter = exporter(List.of())) {
            exporter.start();
            exporter.exportOnce();
            assertThat(exporter.isRunning()).isFalse();
        }
        verifyNoInteractions(repository, observer);
    }

    private DdcServiceObservationExporter exporter(List<DdcServiceObservationBindingVO> bindings) {
        return new DdcServiceObservationExporter(observer, repository, new DdcTenantContextExecutor(), bindings,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
    }

    private static DdcServiceObservationBindingVO binding(long tenant, String id) {
        return new DdcServiceObservationBindingVO(tenant, "identity-" + tenant, "example", "default", "prod", "orders",
                id, 1, "cluster-a", "apps-prod", id, "uid-" + id, "grpc", 9090, "grpc", id,
                "", "v1", Set.of("workload-uid"));
    }

    private record ReportVO(String bindingId, long epoch, long sequence, StatusEnum status) { }
}
