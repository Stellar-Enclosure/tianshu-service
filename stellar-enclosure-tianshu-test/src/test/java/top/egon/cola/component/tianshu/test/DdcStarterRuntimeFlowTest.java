package top.egon.cola.component.tianshu.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotCoordinator;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient.SnapshotResultVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState;

class DdcStarterRuntimeFlowTest {
    @TempDir Path temp;

    @Test
    void currentSdkAppliesTheSampleThenDurablyRecordsItsReceiptWithoutAdminOrRegistration() throws Exception {
        try (var h = new DdcSampleRefreshFlowTest.Harness(temp)) {
            var http = mock(DdcConfigSnapshotHttpClient.class);
            var store = new DdcConfigLkgStore(temp.resolve("state"));
            var observation = new DdcLocalConfigState(1001L);
            var policy = new DdcConfigSnapshotCoordinator.PolicyVO(
                "pod-a", Duration.ofMinutes(1), Duration.ofSeconds(30), false, null);
            var snapshot =
                h.snapshot(2, Map.of("order.rate-limit.permits-per-second", JsonNodeFactory.instance.numberNode(250)));
            when(http.snapshot(anyString(), isNull()))
                .thenReturn(new SnapshotResultVO(snapshot, DdcConfigCanonicalJson.etag(snapshot),
                    snapshot.getValidUntil(), "fixture-delivery-proof", "containerd://current"));
            var key = new DdcConfigLkgStore.ScopeKeyVO(
                DdcSampleRefreshFlowTest.SCOPE, "42", "demo-local", "test-cluster", "sample", "workload-a", "app");
            doAnswer(invocation -> {
                DdcConfigReceiptCommand receipt = invocation.getArgument(2);
                var durable = store.read(key).orElseThrow();
                assertThat(h.sample.getRateLimit()).isEqualTo(250);
                assertThat(durable.applied().getReleaseRevision()).isEqualTo("2");
                assertThat(durable.pendingReceipt().path("receiptDigest").textValue())
                    .isEqualTo(receipt.getReceiptDigest());
                assertThat(durable.pendingReceipt().has("deliveryToken")).isFalse();
                assertThat(receipt.getStatus()).isEqualTo(DdcConfigReceiptStatusEnum.APPLIED);
                assertThat(receipt.getReportSeq()).isEqualTo("1");
                assertThat(receipt.getAppliedRevision()).isEqualTo("2");
                return null;
            })
                .when(http)
                .report(anyString(), anyString(), any());
            try (var coordinator = new DdcConfigSnapshotCoordinator(h.metadata, policy, http, store, h.applier,
                     observation, event -> {}, Clock.fixed(DdcSampleRefreshFlowTest.NOW, ZoneOffset.UTC))) {
                assertThat(coordinator.ready()).isFalse();
                coordinator.pollOnce();
                assertThat(coordinator.ready()).isTrue();
                assertThat(h.sample.getRateLimit()).isEqualTo(250);
                assertThat(store.read(key).orElseThrow().pendingReceipt()).isNull();
                verify(http).snapshot(anyString(), isNull());
                verify(http).report(anyString(), anyString(), any());
                assertThat(h.context.containsBean("ddcConfigService")).isFalse();
                assertThat(h.context.containsBean("ddcRuntimeCoordinator")).isFalse();
                coordinator.close();
                clearInvocations(http);
                coordinator.pollOnce();
                assertThat(coordinator.ready()).isFalse();
                verifyNoInteractions(http);
            }
        }
    }
}
