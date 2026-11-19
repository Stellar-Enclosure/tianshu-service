package top.egon.cola.component.tianshu.admin.service.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.*;
import top.egon.cola.component.tianshu.admin.model.bo.*;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskResultConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishTaskResult;
import top.egon.cola.component.tianshu.admin.repository.*;
import top.egon.cola.component.tianshu.admin.security.tenancy.*;
import top.egon.cola.component.tianshu.model.config.*;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.*;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishTaskQueryRequest;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcPublishTaskQueryServiceTest {

    @Test
    void normalizesFiltersAndUsesRepositoryPageQuery() {
        DdcPublishTaskRepository repository = mock(DdcPublishTaskRepository.class);
        DdcPublishTaskBO task = DdcPublishTaskBO.builder().changeId("change-1").build();
        when(repository.search(
                eq("infra"), eq("prod"), eq("yuheng"), eq("FAILED"),
                eq("019"), any(PageQuery.class))).thenReturn(DdcPageResult.<DdcPublishTaskBO>builder()
                .records(List.of(task)).total(1).pageNo(1).pageSize(10).build());
        DdcPublishTaskQueryService service =
                new DdcPublishTaskQueryService(repository);
        DdcPublishTaskQueryRequest request = new DdcPublishTaskQueryRequest();
        request.setBizCode(" infra ");
        request.setEnv(" prod ");
        request.setAppCode(" yuheng ");
        request.setStatus(" FAILED ");
        request.setChangeId(" 019 ");

        var page = service.page(request, new PageQuery(1, 10));

        assertThat(page.getRecords()).containsExactly(task);
        var pageQuery = org.mockito.ArgumentCaptor.forClass(PageQuery.class);
        verify(repository).search(
                eq("infra"), eq("prod"), eq("yuheng"), eq("FAILED"),
                eq("019"), pageQuery.capture());
        assertThat(pageQuery.getValue().pageNo()).isEqualTo(1);
        assertThat(pageQuery.getValue().pageSize()).isEqualTo(10);
    }
    @Test
    void archivedAckSuccessIsNotCurrentProcessEvidence() throws Exception {
        var repository = mock(DdcPublishTaskRepository.class);
        var task = DdcPublishTaskBO.builder().changeId("archive-1").status("SUCCESS")
                .targetCount(2).ackCount(2).build();
        when(repository.findByChangeId("archive-1")).thenReturn(java.util.Optional.of(task));
        var service = new DdcPublishTaskQueryService(repository);
        var method = DdcPublishTaskQueryService.class.getMethod("detailResult", String.class);
        Object result = ((java.util.Optional<?>) method.invoke(service, "archive-1")).orElseThrow();
        assertThat(result.getClass().getMethod("getProcessState").invoke(result)).isEqualTo("UNKNOWN");
        assertThat(result.getClass().getMethod("getHistoryOnly").invoke(result)).isEqualTo(Boolean.TRUE);
        verify(repository).findByChangeId("archive-1");
    }

    @Test
    void currentBootLookupDoesNotRequireAnUnobservedBootId() throws Exception {
        assertThat(top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository.class
                .getMethod("findCurrentBindings",
                    top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO.class,
                    String.class, String.class, String.class, List.class).getReturnType())
                .isEqualTo(List.class);
    }

    @AfterEach void clearTrustedContext() { MDC.clear(); SecurityContextHolder.clearContext(); }

    @Test void currentExactReceiptConvergesIndependentlyOfPodReady() throws Exception {
        var f = new NativeFixture(); f.pods.set(0, f.pod("pod-1", "containerd://one", false));
        var result = f.result();
        assertThat(result.getHistoryOnly()).isFalse();
        assertThat(result.getPlatformState()).isEqualTo("APPLIED");
        assertThat(result.getProcessState()).isEqualTo("APPLIED");
        assertThat(result.getRolloutState()).isEqualTo("PENDING");
        assertThat(result.getTargets().getFirst().getCurrentProcesses().getFirst().getLastReportedAt()).isEqualTo(f.now.minusSeconds(3600));
        verify(f.items, times(2)).findActiveById(10L);
        verify(f.versions).findByConfigItemIdAndRevision(10L, 43L);
        verify(f.boots, never()).createOrConfirm(any(), any());
    }

    @Test void unchangedReceiptDoesNotExpireWithoutAnExplicitRefreshContract() throws Exception {
        var f = new NativeFixture(); f.rows.getFirst().setLastReceivedAt(f.now.minus(Duration.ofDays(30)));
        assertThat(f.result().getProcessState()).isEqualTo("APPLIED");
    }

    @Test void scaleUpPendingContainerRemainsInDenominatorWithoutInventingBoot() throws Exception {
        var f = new NativeFixture(); f.desired = 2; f.pods.add(f.pod("pod-2", null, false));
        var target = f.result().getTargets().getFirst();
        assertThat(target.getProcessState()).isEqualTo("PENDING");
        assertThat(target.getCurrentCount()).isEqualTo(2);
        assertThat(target.getAppliedCount()).isEqualTo(1);
        assertThat(target.getWaitingCount()).isEqualTo(1);
        assertThat(target.getCurrentProcesses().get(1).getBootId()).isNull();
    }

    @Test void nonRunningContainerRetainsDenominatorWithoutBootOrReceiptAuthority() throws Exception {
        var f = new NativeFixture();
        f.pods.set(0, new PodObservationVO("pod-1", "pod-1", "Unknown", false, "application", "containerd://one", 0, f.now.minusSeconds(10)));
        assertThat(f.result().getProcessState()).isEqualTo("PENDING");
        verifyNoInteractions(f.boots, f.receipts);
    }

    @Test void zeroReplicaPublicationWaitsForConsumersRatherThanReportingApplied() throws Exception {
        var f = new NativeFixture(); f.desired = 0; f.pods.clear();
        var result = f.result();
        assertThat(result.getProcessState()).isEqualTo("PUBLISHED_WAITING_FOR_CONSUMERS");
        verifyNoInteractions(f.boots, f.receipts);
    }

    @Test void staleOrExpiredObservationCannotUseStoredSuccess() throws Exception {
        var f = new NativeFixture(); f.status = StatusEnum.STALE;
        assertThat(f.result().getProcessState()).isEqualTo("UNKNOWN");
        f.status = StatusEnum.FRESH; f.validUntil = f.now;
        assertThat(f.result().getProcessState()).isEqualTo("UNKNOWN");
        verifyNoInteractions(f.boots, f.receipts);
    }

    @Test void observerTargetMismatchCannotReadTechnicalBindings() throws Exception {
        var f = new NativeFixture(); f.observedTarget = new TargetBindingVO("foreign", "1", "cluster-1", "apps", "Deployment", "reader", "workload-1", "application");
        assertThat(f.result().getProcessState()).isEqualTo("UNKNOWN");
        verifyNoInteractions(f.boots, f.receipts);
    }

    @Test void currentContainerRestartExcludesPreviousContainerReceipt() throws Exception {
        var f = new NativeFixture(); f.pods.set(0, f.pod("pod-1", "containerd://two", true));
        f.bindings.clear();
        var target = f.result().getTargets().getFirst();
        assertThat(target.getAppliedCount()).isZero();
        assertThat(target.getProcessState()).isEqualTo("PENDING");
        verifyNoInteractions(f.receipts);
    }

    @Test void wrongTargetDigestOrBootFromReadPortNeverBecomesCurrentSuccess() throws Exception {
        var f = new NativeFixture(); f.rows.getFirst().setAppliedDigest("c".repeat(64));
        assertThat(f.result().getProcessState()).isEqualTo("UNKNOWN");
        f.rows.getFirst().setAppliedDigest(f.digest()).setBootId("old-boot");
        assertThat(f.result().getProcessState()).isEqualTo("UNKNOWN");
    }

    @Test void newerDesiredBeforeOrDuringProjectionMakesOldTaskSuperseded() throws Exception {
        var f = new NativeFixture(); f.item.setPublishedVersion(44L);
        assertThat(f.result().getProcessState()).isEqualTo("SUPERSEDED");
        verifyNoInteractions(f.boots, f.receipts);
        f.item.setPublishedVersion(43L);
        doAnswer(call -> { f.item.setPublishedVersion(44L); return f.view(); }).when(f.reader).currentView(any());
        assertThat(f.result().getProcessState()).isEqualTo("SUPERSEDED");
    }

    @Test void missingTrustedTenantOrChangedScopeCannotAccessTechnicalBoots() throws Exception {
        var f = new NativeFixture(); MDC.put("tenantId", "999");
        assertThat(f.result().getProcessState()).isEqualTo("UNKNOWN");
        verifyNoInteractions(f.boots, f.receipts);
    }

    @Test void unavailableReceiptStoreDoesNotFallBackToOldAckCounters() throws Exception {
        var f = new NativeFixture(); f.task.setAckCount(100).setTargetCount(100);
        doThrow(new DdcConfigProcessReceiptRepository.ReceiptStoreUnavailableException()).when(f.receipts).readCurrentByTarget(any(), any(), anyList(), anyInt());
        assertThat(f.result().getProcessState()).isEqualTo("UNKNOWN");
    }

    @Test void allGetQueriesShareProjectionAndRemainReadOnly() throws Exception {
        var f = new NativeFixture();
        when(f.tasks.findAll()).thenReturn(List.of(f.task));
        when(f.tasks.search(any(), any(), any(), any(), any(), any())).thenReturn(DdcPageResult.<DdcPublishTaskBO>builder()
                .records(List.of(f.task)).total(1).pageNo(1).pageSize(10).build());
        assertThat(f.service.listResults().getFirst().getProcessState()).isEqualTo("APPLIED");
        assertThat(f.service.pageResults(null, new PageQuery(1, 10)).getRecords().getFirst().getProcessState()).isEqualTo("APPLIED");
        assertThat(f.result().getProcessState()).isEqualTo("APPLIED");
        verify(f.tasks, never()).update(any()); verify(f.tasks, never()).insert(any());
        verify(f.items, never()).advancePublishedVersion(any(), any(), any(), any());
        verify(f.receipts, never()).accept(any(), any()); verify(f.boots, never()).createOrConfirm(any(), any());
    }

    static final class NativeFixture {
        final Instant now = Instant.parse("2026-11-18T22:00:00Z");
        final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        final DdcTenantContextDTO context = new DdcTenantContextDTO(101L, "tenant-one", "user:operator");
        final DdcConfigScopeVO scope = new DdcConfigScopeVO("101", "reading", "prod", "reader", "application.yml");
        final TargetBindingVO target = new TargetBindingVO("reader-target", "1", "cluster-1", "apps", "Deployment", "reader", "workload-1", "application");
        final DdcPublishTaskRepository tasks = mock(DdcPublishTaskRepository.class);
        final DdcConfigItemRepository items = mock(DdcConfigItemRepository.class);
        final DdcConfigVersionRepository versions = mock(DdcConfigVersionRepository.class);
        final DdcConfigBootBindingRepository boots = mock(DdcConfigBootBindingRepository.class);
        final DdcConfigProcessReceiptRepository receipts = mock(DdcConfigProcessReceiptRepository.class);
        final DdcPublishTaskQueryService.WorkloadObservationReader reader = mock(DdcPublishTaskQueryService.WorkloadObservationReader.class);
        final DdcConfigItemBO item;
        final DdcPublishTaskBO task;
        final DdcConfigBaselineVO baseline;
        final List<PodObservationVO> pods = new ArrayList<>();
        final List<DdcConfigBootBindingBO> bindings = new ArrayList<>();
        final List<DdcConfigProcessReceiptBO> rows = new ArrayList<>();
        final DdcPublishTaskQueryService service;
        TargetBindingVO observedTarget = target;
        int desired = 1; StatusEnum status = StatusEnum.FRESH; Instant validUntil = now.plusSeconds(30);
        NativeFixture() throws Exception {
            item = DdcConfigItemBO.builder().id(10L).tenantId(101L).publicId("config-public").bizCode("reading").env("prod")
                    .appCode("reader").resourceName("application.yml").format("yaml").publishedVersion(43L).currentVersion(43L).build();
            var factory = new DdcConfigManifestFactory(json);
            String yaml = "reader:\n  limit: 40\n";
            var policy = new DdcConfigManifestFactory.PublicationPolicyVO(scope,
                    List.of(new RuleVO("reader.limit", ReloadModeEnum.HOT, "INTEGER", null, false, true)), List.of(target), null, Map.of(), Map.of());
            var frozen = factory.freeze(context, item, 43L, yaml, true, policy); baseline = frozen.baseline();
            var version = DdcConfigVersionBO.builder().tenantId(101L).configItemId(10L).configRevision(43L).configId("config-public")
                    .bizCode("reading").env("prod").appCode("reader").resourceName("application.yml").format("yaml").newContent(yaml).manifestJson(frozen.manifest()).build();
            var evidence = json.createObjectNode().put("schemaVersion", 1);
            evidence.putArray("targets").addObject().put("bindingId", "reader-target").put("bindingRevision", "1")
                    .put("clusterId", "cluster-1").put("namespace", "apps").put("workloadUid", "workload-1")
                    .put("configMapName", configMap()).put("configMapUid", "config-map-uid").put("workloadGeneration", "3")
                    .put("resourceVersion", "opaque:rv").put("observedAt", now.minusSeconds(5).toString()).put("status", "APPLIED").putNull("errorCode");
            task = DdcPublishTaskBO.builder().publicId("task-public").tenantId(101L).changeId("change-current").configItemId(10L)
                    .configId("config-public").bizCode("reading").env("prod").appCode("reader").resourceName("application.yml")
                    .targetVersion(43L).status("SUCCESS").platformEvidence(evidence).build();
            pods.add(pod("pod-1", "containerd://one", true));
            bindings.add(DdcConfigBootBindingBO.builder().id(100L).clusterId("cluster-1").k8sNamespace("apps").workloadUid("workload-1")
                    .podUid("pod-1").containerName("application").containerId("containerd://one").bootId("boot-1").build());
            rows.add(DdcConfigProcessReceiptBO.builder().tenantId(101L).configId(10L).configRevision(43L).scopeJson(json.valueToTree(scope))
                    .targetBindingId("reader-target").targetRevision(1L).clusterId("cluster-1").k8sNamespace("apps").workloadUid("workload-1")
                    .podUid("pod-1").containerName("application").containerId("containerd://one").bootId("boot-1")
                    .contentDigest(digest()).appliedRevision(43L).appliedDigest(digest()).restartDigest(baseline.getRestartDigest().substring(7))
                    .result("APPLIED").reportSeq(2L).receiptDigest("e".repeat(64)).appliedAt(now.minusSeconds(3601)).lastReceivedAt(now.minusSeconds(3600)).build());
            when(tasks.findByChangeId("change-current")).thenReturn(Optional.of(task));
            when(items.findActiveById(10L)).thenAnswer(call -> Optional.of(item));
            when(versions.findByConfigItemIdAndRevision(10L, 43L)).thenReturn(Optional.of(version));
            when(boots.findCurrentBindings(any(), anyString(), anyString(), anyString(), anyList())).thenAnswer(call -> List.copyOf(bindings));
            when(receipts.readCurrentByTarget(any(), any(), anyList(), anyInt())).thenAnswer(call -> List.copyOf(rows));
            when(reader.currentView(any())).thenAnswer(call -> view());
            var registry = mock(DdcTenantRegistry.class); when(registry.resolveUserContext("tenant-one", "operator")).thenReturn(context);
            var principal = mock(Rbac3UserDetails.class); when(principal.tenantId()).thenReturn("tenant-one"); when(principal.identitySub()).thenReturn("operator");
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of()));
            MDC.put("tenantId", "101"); MDC.put("userId", "user:operator");
            var beans = new StaticListableBeanFactory(); beans.addBean("reader", reader);
            service = new DdcPublishTaskQueryService(tasks, items, versions, receipts, boots, Mappers.getMapper(DdcPublishTaskResultConverter.class),
                    factory, registry, beans.getBeanProvider(DdcPublishTaskQueryService.WorkloadObservationReader.class), Clock.fixed(now, ZoneOffset.UTC), json);
        }
        WorkloadViewVO view() {
            var incarnations = pods.stream().filter(p -> p.containerId() != null && p.startedAt() != null && "Running".equals(p.phase()))
                    .map(p -> new ContainerIncarnationVO(p.podUid(), p.containerName(), p.containerId())).toList();
            return new WorkloadViewVO(observedTarget, status, null, now.minusSeconds(1), validUntil, null, "workload-1", 3L, 3L, desired,
                    pods, incarnations, Map.of("stellar.io/config-restart-digest", baseline.getRestartDigest()), Map.of("tianshu-baseline", configMap()));
        }
        PodObservationVO pod(String uid, String container, boolean ready) { return new PodObservationVO(uid, uid, "Running", ready, "application", container, 0, container == null ? null : now.minusSeconds(7200)); }
        String configMap() { return "tianshu-baseline-" + "d".repeat(64); }
        String digest() { return baseline.getContentSha256().substring(7); }
        DdcPublishTaskResult result() { return service.detailResult("change-current").orElseThrow(); }
    }

}
