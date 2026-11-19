package top.egon.cola.component.tianshu.admin.service.lease;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.*;
import top.egon.cola.component.tianshu.admin.model.bo.DdcInstanceBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcInstanceResultConverterImpl;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcInstanceRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyResolver;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyVO;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishTaskQueryService.WorkloadObservationReader;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DdcInstanceAdminServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("42", "infra", "prod", "api", "application.yml");
    private final DdcInstanceRepository history = mock(DdcInstanceRepository.class);
    private final PublicationPolicyResolver policies = mock(PublicationPolicyResolver.class);
    private final WorkloadObservationReader reader = mock(WorkloadObservationReader.class);
    private final DdcTenantRegistry tenants = mock(DdcTenantRegistry.class);
    private final TargetBindingVO target = target("binding-a", "workload-a");

    @BeforeEach void bindContext() {
        EgonColaTenantIdProvider.initialize("tenantId");
        MDC.put("tenantId", "42"); MDC.put("userId", "user:operator");
        when(tenants.requireEnabledTenant(42L)).thenReturn(new DdcTenantBindingDTO(42L, "identity-42", true));
        when(policies.require(any(), any())).thenReturn(policy(List.of(target)));
    }
    @AfterEach void clearContext() { MDC.clear(); }

    @Test void historicalReadsAreExplicitAndSeparatedFromCurrentWorkloads() throws Exception {
        assertThat(DdcInstanceAdminService.class.getDeclaredMethod("historyList", String.class, String.class, String.class)).isNotNull();
        assertThat(DdcInstanceAdminService.class.getDeclaredMethod("historyPage", String.class, String.class, String.class, PageQuery.class)).isNotNull();
        assertThat(Arrays.stream(DdcInstanceAdminService.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("register", "heartbeat", "offline");
    }

    @Test void currentQueryUsesOnlyAuthorizedKubernetesFactsAndNoHistoricalOnlineRows() {
        WorkloadViewVO view = fresh(target, NOW.plusSeconds(20));
        when(reader.currentView(target)).thenReturn(view);
        assertThat(service(true).list("infra", "prod", "api")).containsExactly(view);
        verify(policies).require(argThat(context -> context.tenantId() == 42L
                && context.identityTenantId().equals("identity-42") && context.actor().equals("user:operator")), eq(SCOPE));
        verifyNoInteractions(history);
    }

    @Test void expiredFactsCannotKeepPodsCurrentEvenWhenStoredStatusSaysFresh() {
        when(reader.currentView(target)).thenReturn(fresh(target, NOW));
        WorkloadViewVO view = service(true).list("infra", "prod", "api").getFirst();
        assertThat(view.status()).isEqualTo(StatusEnum.STALE);
        assertThat(view.reasonCode()).isEqualTo("OBSERVATION_EXPIRED");
        assertThat(view.currentPods()).isEmpty(); assertThat(view.currentIncarnations()).isEmpty();
        verifyNoInteractions(history);
    }

    @Test void absentReaderAndStoreFailureRemainUnknownWithoutLegacyFallback() {
        assertThat(service(false).list("infra", "prod", "api").getFirst().status()).isEqualTo(StatusEnum.UNAVAILABLE);
        when(reader.currentView(target)).thenThrow(new IllegalStateException("synthetic-sensitive-store-detail"));
        WorkloadViewVO failed = service(true).list("infra", "prod", "api").getFirst();
        assertThat(failed.status()).isEqualTo(StatusEnum.UNAVAILABLE);
        assertThat(failed.reasonCode()).isEqualTo("CONFIG_CURRENT_EVIDENCE_UNAVAILABLE");
        assertThat(failed.toString()).doesNotContain("synthetic-sensitive");
        verifyNoInteractions(history);
    }

    @Test void staleIdentityConflictOrMissingFactsNeverExposeCurrentPods() {
        when(reader.currentView(target)).thenReturn(new WorkloadViewVO(target, StatusEnum.UNAVAILABLE,
                "WORKLOAD_ABSENT", NOW.minusSeconds(1), NOW.plusSeconds(20), NOW, "workload-a", 0, 0, 0,
                List.of(), List.of(), Map.of(), Map.of()));
        assertThat(service(true).list("infra", "prod", "api").getFirst().reasonCode()).isEqualTo("WORKLOAD_ABSENT");
        when(reader.currentView(target)).thenReturn(new WorkloadViewVO(target, StatusEnum.FRESH, null,
                NOW.minusSeconds(1), NOW.plusSeconds(20), null, "replacement-uid", 1, 1, 1,
                List.of(), List.of(), Map.of(), Map.of()));
        var conflict = service(true).list("infra", "prod", "api").getFirst();
        assertThat(conflict.status()).isEqualTo(StatusEnum.IDENTITY_CONFLICT);
        assertThat(conflict.currentPods()).isEmpty();
        verifyNoInteractions(history);
    }

    @Test void observationForAnotherBindingAndFutureEvidenceAreRejected() {
        when(reader.currentView(target)).thenReturn(fresh(target("other", "other-uid"), NOW.plusSeconds(20)));
        assertThat(service(true).list("infra", "prod", "api").getFirst().status()).isEqualTo(StatusEnum.UNAVAILABLE);
        when(reader.currentView(target)).thenReturn(new WorkloadViewVO(target, StatusEnum.FRESH, null,
                NOW.plusSeconds(1), NOW.plusSeconds(20), null, "workload-a", 1, 1, 1,
                List.of(), List.of(), Map.of(), Map.of()));
        assertThat(service(true).list("infra", "prod", "api").getFirst().reasonCode()).isEqualTo("CONFIG_CURRENT_EVIDENCE_INVALID");
        verifyNoInteractions(history);
    }

    @Test void paginationIsDeterministicAndCountsConfiguredTargetsRatherThanLeaseRows() {
        TargetBindingVO second = target("binding-b", "workload-b");
        when(policies.require(any(), any())).thenReturn(policy(List.of(second, target)));
        when(reader.currentView(target)).thenReturn(fresh(target, NOW.plusSeconds(20)));
        when(reader.currentView(second)).thenReturn(fresh(second, NOW.plusSeconds(20)));
        var page = service(true).page("infra", "prod", "api", new PageQuery(2, 1));
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(view -> view.target().getBindingId()).containsExactly("binding-b");
        assertThat(service(true).page("infra", "prod", "api", new PageQuery(3, 1)).getRecords()).isEmpty();
        verifyNoInteractions(history);
    }

    @Test void historyQueriesPreserveOriginalTelemetryWithoutClaimingCurrentAvailability() {
        DdcInstanceBO row = DdcInstanceBO.builder().publicId("archive-1").instanceId("old-process")
                .status("ONLINE").leaseId("old-lease").runtimeMetadata(Map.of("last", "old-value")).build();
        when(history.findHistoricalByScope("infra", "prod", "api")).thenReturn(List.of(row));
        var result = service(true).historyList("infra", "prod", "api").getFirst();
        assertThat(result.getStatus()).isEqualTo("ONLINE");
        assertThat(result.getLeaseId()).isEqualTo("old-lease");
        result.setStatus("client-change");
        assertThat(row.getStatus()).isEqualTo("ONLINE");
        verifyNoInteractions(policies, reader);
    }

    @Test void historyPageUsesOnlyTheExplicitTenantScopedArchivePort() {
        PageQuery query = new PageQuery(2, 10);
        when(history.findHistoricalByScope("infra", "prod", "api", query)).thenReturn(DdcPageResult.<DdcInstanceBO>builder()
                .records(List.of()).total(17).pageNo(2).pageSize(10).build());
        var page = service(true).historyPage("infra", "prod", "api", query);
        assertThat(page.getTotal()).isEqualTo(17); assertThat(page.getPageNo()).isEqualTo(2);
        verifyNoInteractions(policies, reader);
    }

    @Test void unauthorizedTenantOrScopeCannotSelectTargetsOrReadHistory() {
        MDC.clear();
        assertThatThrownBy(() -> service(true).list("infra", "prod", "api")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(history, policies, reader);
    }

    @Test void unregisteredScopeOrDuplicateBindingsCannotReachTheObserver() {
        when(policies.require(any(), any())).thenReturn(policy(List.of(target, target)));
        assertThatThrownBy(() -> service(true).list("infra", "prod", "api"))
                .hasMessage("TIANSHU_CURRENT_INSTANCE_SCOPE_UNVERIFIED");
        verifyNoInteractions(history, reader);
    }

    private DdcInstanceAdminService service(boolean hasReader) {
        var beans = new StaticListableBeanFactory(hasReader ? Map.of("ddcWorkloadObservationReader", reader) : Map.of());
        return new DdcInstanceAdminService(history, new DdcInstanceResultConverterImpl(), policies,
                beans.getBeanProvider(WorkloadObservationReader.class), tenants, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private static PublicationPolicyVO policy(List<TargetBindingVO> targets) {
        return new PublicationPolicyVO(SCOPE, List.of(), targets, "dependency-1", Map.of(), Map.of());
    }
    private static TargetBindingVO target(String id, String uid) {
        return new TargetBindingVO(id, "1", "cluster-a", "runtime", "Deployment", "api", uid, "app");
    }
    private static WorkloadViewVO fresh(TargetBindingVO target, Instant expires) {
        var pod = new PodObservationVO("pod-uid", "api-pod", "Running", true, "app", "containerd://current", 0, NOW.minusSeconds(10));
        return new WorkloadViewVO(target, StatusEnum.FRESH, null, NOW.minusSeconds(10), expires, null,
                target.getWorkloadUid(), 3, 3, 1, List.of(pod),
                List.of(new ContainerIncarnationVO("pod-uid", "app", "containerd://current")), Map.of(), Map.of());
    }
}
