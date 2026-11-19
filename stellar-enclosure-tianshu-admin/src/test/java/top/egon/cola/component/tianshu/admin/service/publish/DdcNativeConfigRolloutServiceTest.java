package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigBaselineReconciler.TargetResultVO;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.FrozenConfigVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcNativeConfigRolloutServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-19T00:30:00Z");
    private static final LocalDateTime UTC_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final String CHANGE = "rollout-43";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DdcConfigItemRepository items = mock(DdcConfigItemRepository.class);
    private final DdcConfigVersionRepository versions = mock(DdcConfigVersionRepository.class);
    private final DdcPublishTaskRepository tasks = mock(DdcPublishTaskRepository.class);
    private final DdcConfigManifestFactory factory = mock(DdcConfigManifestFactory.class);
    private final DdcConfigBaselineReconciler baselineReconciler = mock(DdcConfigBaselineReconciler.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final DdcNativeConfigRolloutService service = new DdcNativeConfigRolloutService(
            items, versions, tasks, factory, baselineReconciler, transactions, json, Clock.fixed(NOW, ZoneOffset.UTC));
    private final ObjectNode marker = json.createObjectNode().put("schemaVersion", 1);
    private final DdcPublishTaskBO task = DdcPublishTaskBO.builder().tenantId(7001L).id(77L).changeId(CHANGE)
            .configItemId(101L).targetVersion(43L).attemptCount(1).version(9L)
            .bizCode("example").env("staging").appCode("api").resourceName("application.yml").status("PENDING")
            .platformEvidence(marker).build();

    @BeforeEach
    void establishTrustedContext() {
        marker.putArray("targets");
        MDC.put("tenantId", "7001");
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(new SimpleTransactionStatus()));
    }

    @AfterEach
    void clearContext() {
        MDC.remove("tenantId");
    }

    @Test
    void rejectsMissingTenantOrOlderAttemptBeforeAnyKubernetesAccess() {
        MDC.remove("tenantId");
        assertThat(service.reconcileNative(CHANGE, 101, 43, 1, "event")).isFalse();
        verifyNoInteractions(tasks, items, versions, baselineReconciler);
        MDC.put("tenantId", "7001");
        when(tasks.findByChangeId(CHANGE)).thenReturn(Optional.of(task));
        assertThat(service.reconcileNative(CHANGE, 101, 43, 2, "different-attempt")).isFalse();
        verifyNoInteractions(items, versions, baselineReconciler);
    }

    @Test
    void newerCommittedDesiredSupersedesWithoutTouchingKubernetes() {
        setupTaskAtRevision(44L);
        when(tasks.updateNativeEvidence(task, marker, "SUPERSEDED", null, UTC_NOW)).thenReturn(1);
        assertThat(service.reconcileNative(CHANGE, 101, 43, 1, "event-43")).isFalse();
        verify(tasks).updateNativeEvidence(task, marker, "SUPERSEDED", null, UTC_NOW);
        verifyNoInteractions(versions, baselineReconciler);
    }

    @Test
    void technicalDeliveryPersistsPlatformEvidenceAndDoesNotClaimProcessApplication() {
        setupTaskAtRevision(43L);
        prepareArtifact("APPLIED");
        when(tasks.updateNativeEvidence(eq(task), any(), eq("PLATFORM_APPLIED"), isNull(), eq(UTC_NOW))).thenReturn(1);

        assertThat(service.reconcileNative(CHANGE, 101, 43, 1, "event-43")).isTrue();

        var captured = org.mockito.ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(tasks).updateNativeEvidence(eq(task), captured.capture(), eq("PLATFORM_APPLIED"), isNull(), eq(UTC_NOW));
        assertThat(captured.getValue().path("targets").path(0).path("configMapUid").textValue()).isEqualTo("cm-uid");
        assertThat(captured.getValue().path("targets").path(0).has("appliedRevision")).isFalse();
    }

    @Test
    void transientWorkloadFailureIsPersistedBeforeRetryingOutbox() {
        setupTaskAtRevision(43L);
        prepareArtifact("PENDING");
        when(tasks.updateNativeEvidence(eq(task), any(), eq("PUBLISHING"), isNull(), eq(UTC_NOW))).thenReturn(1);

        assertThatThrownBy(() -> service.reconcileNative(CHANGE, 101, 43, 1, "event-43"))
                .hasMessage("TIANSHU_CONFIG_ROLLOUT_WILL_RETRY");
        verify(tasks).updateNativeEvidence(eq(task), any(), eq("PUBLISHING"), isNull(), eq(UTC_NOW));
    }

    private void setupTaskAtRevision(long revision) {
        var item = DdcConfigItemBO.builder().id(101L).publishedVersion(revision).build();
        when(tasks.findByChangeId(CHANGE)).thenReturn(Optional.of(task));
        when(tasks.findForUpdateByChangeId(CHANGE)).thenReturn(Optional.of(task));
        when(items.findByBizCodeAndEnvAndAppCodeAndResourceName("example", "staging", "api", "application.yml"))
                .thenReturn(Optional.of(item));
        when(items.findForPublishByBizCodeAndEnvAndAppCodeAndResourceName("example", "staging", "api", "application.yml"))
                .thenReturn(Optional.of(item));
    }

    private void prepareArtifact(String outcome) {
        var release = mock(DdcConfigBaselineVO.class);
        var version = DdcConfigVersionBO.builder().build();
        when(versions.findByConfigItemIdAndRevision(101L, 43L)).thenReturn(Optional.of(version));
        when(factory.materialize(eq(version), any())).thenReturn(new FrozenConfigVO(marker, release));
        var result = new TargetResultVO("binding-1", "1", "cluster-a", "platform-prod", "workload-uid",
                "baseline", "cm-uid", "7", "rv-7", NOW, outcome, null);
        when(baselineReconciler.reconcile(eq(release), any())).thenAnswer(invocation -> {
            assertThat(invocation.<BooleanSupplier>getArgument(1).getAsBoolean()).isTrue();
            return List.of(result);
        });
    }
}
