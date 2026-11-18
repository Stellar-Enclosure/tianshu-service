package top.egon.cola.component.tianshu.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource.BaselineMetadataVO;
import top.egon.cola.component.tianshu.environment.DdcDynamicPropertySource;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.format.DdcConfigFormatStrategyRegistry;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.ReloadModeEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.service.binding.DdcFieldBindingService;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotCoordinator;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient.DeliveryFailure;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient.Kind;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient.SnapshotResultVO;
import top.egon.cola.component.tianshu.service.refresh.DdcConfigurationPropertiesRebinder;
import top.egon.cola.component.tianshu.service.refresh.DdcYamlConfigApplier;
import top.egon.cola.component.tianshu.service.refresh.DefaultDdcConfigApplierRegistry;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.ScopeKeyVO;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState.DeliveryStatusEnum;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcConfigSnapshotCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-11-18T20:00:00Z");
    private static final String RESTART = "sha256:" + "a".repeat(64);
    private static final String OTHER_RESTART = "sha256:" + "b".repeat(64);
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("19", "orders", "test", "invoice", "application.yml");
    private static final ScopeKeyVO KEY = new ScopeKeyVO(SCOPE, "811", "billing", "test-cluster", "runtime", "deploy", "app");
    @TempDir Path temp;

    @Test
    void configurationDeliveryHasAnExplicitSinglePollAndReadinessBoundary() throws Exception {
        var coordinator = DdcConfigSnapshotCoordinator.class;
        assertThat(coordinator.getMethod("pollOnce").getReturnType()).isEqualTo(Void.TYPE);
        assertThat(coordinator.getMethod("ready").getReturnType()).isEqualTo(Boolean.TYPE);
    }

    @Test
    void appliesThenCommitsExactPendingBeforePostAndUsesConditionalRequestOnlyAfterComplete200() {
        try (var h = new Harness(store())) {
            var target = snapshot(SCOPE, 2L, RESTART, true, "new");
            when(h.http.snapshot(anyString(), isNull())).thenReturn(full(target, "first-proof", "containerd://one"));
            doAnswer(invocation -> {
                DdcConfigReceiptCommand command = invocation.getArgument(2);
                var disk = h.store.read(KEY).orElseThrow();
                assertThat(h.applier.currentSnapshot().version()).isEqualTo(2L);
                assertThat(h.leaf.get()).isEqualTo("new");
                assertThat(disk.applied().getReleaseRevision()).isEqualTo("2");
                assertThat(disk.pendingReceipt().path("receiptDigest").textValue()).isEqualTo(command.getReceiptDigest());
                assertThat(disk.pendingReceipt().has("deliveryToken")).isFalse();
                assertThat(command.getReportSeq()).isEqualTo("1");
                assertThat(command.getStatus()).isEqualTo(DdcConfigReceiptStatusEnum.APPLIED);
                assertThat(invocation.<String>getArgument(1)).isEqualTo("pod-a/containerd://one/" + command.getBootId() + "/2/1");
                return null;
            }).when(h.http).report(anyString(), anyString(), any());

            h.coordinator.pollOnce();
            assertThat(h.coordinator.ready()).isTrue();
            assertThat(h.store.read(KEY).orElseThrow().pendingReceipt()).isNull();

            var etag = DdcConfigCanonicalJson.etag(target);
            h.clock.advance(Duration.ofSeconds(30));
            when(h.http.snapshot(anyString(), eq(etag))).thenReturn(new SnapshotResultVO(null, etag,
                    NOW.plusSeconds(400), "renewed-proof", "containerd://one"));
            h.coordinator.pollOnce();
            assertThat(h.store.read(KEY).orElseThrow().applied().getValidUntil()).isEqualTo(NOW.plusSeconds(400));
            assertThat(h.state.deliveryObservation().verifiedAt()).isEqualTo(NOW.plusSeconds(30));
            verify(h.http, times(1)).report(anyString(), anyString(), any());
        }
    }

    @Test
    void uncertainReportRetriesExactlyTheSameSequenceKeyAndDigestWithFreshDeliveryProof() {
        try (var h = new Harness(store())) {
            var target = snapshot(SCOPE, 2L, RESTART, true, "new");
            String etag = DdcConfigCanonicalJson.etag(target);
            when(h.http.snapshot(anyString(), isNull())).thenReturn(full(target, "token-a", "containerd://one"));
            when(h.http.snapshot(anyString(), eq(etag))).thenReturn(new SnapshotResultVO(null, etag,
                    NOW.plusSeconds(340), "token-b", "containerd://one"));
            var keys = new ArrayList<String>();
            var reports = new ArrayList<DdcConfigReceiptCommand>();
            doAnswer(invocation -> {
                keys.add(invocation.getArgument(1));
                reports.add(invocation.getArgument(2));
                if (reports.size() == 1) throw new DeliveryFailure(Kind.UNAVAILABLE, "CONFIG_DELIVERY_UNAVAILABLE");
                return null;
            }).when(h.http).report(anyString(), anyString(), any());

            h.coordinator.pollOnce();
            var pending = h.store.read(KEY).orElseThrow().pendingReceipt();
            assertThat(pending).isNotNull();
            h.clock.advance(Duration.ofSeconds(10));
            h.coordinator.pollOnce();
            assertThat(keys).hasSize(2).containsOnly(keys.getFirst());
            assertThat(reports).extracting(DdcConfigReceiptCommand::getReceiptDigest)
                    .containsExactly(pending.path("receiptDigest").textValue(), pending.path("receiptDigest").textValue());
            assertThat(reports).extracting(DdcConfigReceiptCommand::getDeliveryToken).containsExactly("token-a", "token-b");
            assertThat(h.store.read(KEY).orElseThrow().pendingReceipt()).isNull();
        }
    }

    @Test
    void restartFencePreservesCompletePreviousValuesAndDoesNotReannounceUnchangedFailureAfter204() {
        try (var h = new Harness(store())) {
            var target = snapshot(SCOPE, 2L, OTHER_RESTART, true, "unsafe-hot");
            when(h.http.snapshot(anyString(), isNull())).thenReturn(full(target, "first-proof", "containerd://one"));
            h.coordinator.pollOnce();
            h.coordinator.pollOnce();
            assertThat(h.applier.currentSnapshot().version()).isEqualTo(1L);
            assertThat(h.source.getProperty("feature.text")).isEqualTo("base");
            assertThat(h.state.deliveryObservation().status()).isEqualTo(DeliveryStatusEnum.RESTART_REQUIRED);
            verify(h.http, times(1)).report(anyString(), anyString(), argThat(report ->
                    report.getStatus() == DdcConfigReceiptStatusEnum.RESTART_REQUIRED
                            && report.getAppliedRevision().equals("1")
                            && report.getReportSeq().equals("1")));
        }
    }

    @Test
    void transientAuthorityOutageCannotExtendReadinessBeyondLastVerifiedMaximumStale() {
        try (var h = new Harness(store())) {
            var target = snapshot(SCOPE, 2L, RESTART, true, "new");
            when(h.http.snapshot(anyString(), isNull())).thenReturn(full(target, "first-proof", "containerd://one"));
            h.coordinator.pollOnce();
            when(h.http.snapshot(anyString(), eq(DdcConfigCanonicalJson.etag(target))))
                    .thenThrow(new DeliveryFailure(Kind.UNAVAILABLE, "CONFIG_DELIVERY_UNAVAILABLE"));
            h.clock.advance(Duration.ofSeconds(40));
            h.coordinator.pollOnce();
            assertThat(h.coordinator.ready()).isTrue();
            h.clock.advance(Duration.ofSeconds(30));
            h.coordinator.pollOnce();
            assertThat(h.coordinator.ready()).isFalse();
            assertThat(h.applier.currentSnapshot().version()).isEqualTo(2L);
        }
    }

    @Test
    void revocationIsDurableWhile401OnlySuspendsAndCrossScopeProofNeverWritesOrApplies() {
        try (var h = new Harness(store())) {
            var target = snapshot(SCOPE, 2L, RESTART, true, "new");
            when(h.http.snapshot(anyString(), isNull())).thenReturn(full(target, "first", "containerd://one"));
            h.coordinator.pollOnce();
            doThrow(new DeliveryFailure(Kind.UNAUTHENTICATED, "CONFIG_DELIVERY_AUTHENTICATION_REQUIRED"))
                    .when(h.http).snapshot(anyString(), eq(DdcConfigCanonicalJson.etag(target)));
            h.coordinator.pollOnce();
            assertThat(h.coordinator.ready()).isFalse();
            assertThat(h.store.read(KEY).orElseThrow().isolated()).isFalse();

            doThrow(new DeliveryFailure(Kind.FORBIDDEN, "CONFIG_AUTHORIZATION_REVOKED"))
                    .when(h.http).snapshot(anyString(), isNull());
            h.coordinator.pollOnce();
            var isolated = h.store.read(KEY).orElseThrow();
            assertThat(isolated.isolated()).isTrue();
            assertThat(isolated.pendingReceipt()).isNull();
            assertThat(isolated.applied()).isNull();

            var foreign = snapshot(new DdcConfigScopeVO("20", "orders", "test", "invoice", "application.yml"),
                    3L, RESTART, true, "foreign");
            doReturn(full(foreign, "unrelated", "containerd://one")).when(h.http).snapshot(anyString(), isNull());
            h.coordinator.pollOnce();
            assertThat(h.store.read(KEY).orElseThrow().isolated()).isTrue();
            assertThat(h.applier.currentSnapshot().version()).isEqualTo(2L);
            verify(h.http, times(1)).report(anyString(), anyString(), any());
        }
    }

    @Test
    void aNewBootNeverUsesPreviousPendingIdentityEvenWhenTheContentIsTheSame() {
        var files = store();
        var target = snapshot(SCOPE, 2L, RESTART, true, "new");
        String oldBoot;
        try (var old = new Harness(files)) {
            when(old.http.snapshot(anyString(), isNull())).thenReturn(full(target, "first", "containerd://one"));
            doThrow(new DeliveryFailure(Kind.UNAVAILABLE, "CONFIG_DELIVERY_UNAVAILABLE"))
                    .when(old.http).report(anyString(), anyString(), any());
            old.coordinator.pollOnce();
            oldBoot = files.read(KEY).orElseThrow().reportIncarnation().bootId();
        }

        try (var next = new Harness(files)) {
            when(next.http.snapshot(anyString(), isNull())).thenReturn(full(target, "renewed", "containerd://two"));
            next.coordinator.pollOnce();
            var state = files.read(KEY).orElseThrow();
            assertThat(state.reportIncarnation().bootId()).isNotEqualTo(oldBoot);
            assertThat(state.reportIncarnation().containerId()).isEqualTo("containerd://two");
            verify(next.http).report(eq(state.reportIncarnation().bootId()),
                    eq("pod-a/containerd://two/" + state.reportIncarnation().bootId() + "/2/1"),
                    argThat(report -> report.getReportSeq().equals("1")));
        }
    }

    @Test
    void disabledRevisionCannotResurrectEarlierEnabledValues() {
        try (var h = new Harness(store())) {
            var disabled = snapshot(SCOPE, 2L, RESTART, false, "");
            when(h.http.snapshot(anyString(), isNull())).thenReturn(full(disabled, "disable", "containerd://one"));
            h.coordinator.pollOnce();
            assertThat(h.store.read(KEY).orElseThrow().disabledRevision()).isEqualTo("2");
            assertThat(h.source.getPropertyNames()).isEmpty();
            var earlier = snapshot(SCOPE, 1L, RESTART, true, "base");
            when(h.http.snapshot(anyString(), eq(DdcConfigCanonicalJson.etag(disabled))))
                    .thenReturn(full(earlier, "earlier", "containerd://one"));
            h.coordinator.pollOnce();
            assertThat(h.source.getPropertyNames()).isEmpty();
            assertThat(h.coordinator.ready()).isFalse();
            assertThat(h.store.read(KEY).orElseThrow().applied().getEnabled()).isFalse();
        }
    }

    @Test
    void unknownDirectoryDurabilityRollsBackMemoryAndWithholdsTheCandidateReceipt() throws Exception {
        var ctor = DdcConfigLkgStore.class.getDeclaredConstructor(Path.class, Consumer.class);
        ctor.setAccessible(true);
        var injectOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        var fault = ctor.newInstance(temp.resolve("fault"), (Consumer<String>) phase -> {
            if ("BEFORE_DIRECTORY_FORCE".equals(phase) && injectOnce.getAndSet(false)) {
                throw new java.io.UncheckedIOException(new java.io.IOException("fixture"));
            }
        });
        try (var h = new Harness(fault)) {
            var target = snapshot(SCOPE, 2L, RESTART, true, "new");
            when(h.http.snapshot(anyString(), isNull())).thenReturn(full(target, "first", "containerd://one"));
            h.coordinator.pollOnce();
            assertThat(h.applier.currentSnapshot().version()).isEqualTo(1L);
            assertThat(h.source.getProperty("feature.text")).isEqualTo("base");
            assertThat(h.coordinator.ready()).isFalse();
            assertThat(h.state.deliveryObservation().status()).isEqualTo(DeliveryStatusEnum.STORAGE_UNAVAILABLE);
            verify(h.http, never()).report(anyString(), anyString(), any());

            var pending = fault.read(KEY).orElseThrow().pendingReceipt();
            h.clock.advance(Duration.ofSeconds(5));
            h.coordinator.pollOnce();
            assertThat(h.applier.currentSnapshot().version()).isEqualTo(2L);
            assertThat(h.coordinator.ready()).isTrue();
            assertThat(fault.read(KEY).orElseThrow().pendingReceipt()).isNull();
            verify(h.http).report(anyString(), anyString(), argThat(report ->
                    report.getReportSeq().equals("1")
                            && report.getReceiptDigest().equals(pending.path("receiptDigest").textValue())));
        }
    }

    private DdcConfigLkgStore store() {
        return new DdcConfigLkgStore(temp.resolve("state"));
    }

    private static SnapshotResultVO full(DdcConfigSnapshotDTO dto, String token, String container) {
        return new SnapshotResultVO(dto, DdcConfigCanonicalJson.etag(dto), dto.getValidUntil(), token, container);
    }

    private static DdcConfigSnapshotDTO snapshot(DdcConfigScopeVO scope, long revision, String restart,
                                                  boolean enabled, String text) {
        Map<String, JsonNode> values = enabled ? Map.of("feature.text", JsonNodeFactory.instance.textNode(text)) : Map.of();
        var preliminary = new DdcConfigSnapshotDTO("1", scope, Long.toString(revision), enabled,
                restart, RESTART, values, null, NOW.plusSeconds(300));
        return new DdcConfigSnapshotDTO("1", scope, Long.toString(revision), enabled, restart,
                DdcConfigCanonicalJson.contentSha256(preliminary), values, null, preliminary.getValidUntil());
    }

    private static final class Harness implements AutoCloseable {
        private final DdcConfigSnapshotHttpClient http = mock(DdcConfigSnapshotHttpClient.class);
        private final MutableClock clock = new MutableClock();
        private final AtomicReference<String> leaf = new AtomicReference<>("base");
        private final DdcLocalConfigState state = new DdcLocalConfigState(19L);
        private final DdcConfigLkgStore store;
        private final DdcDynamicPropertySource source;
        private final DdcYamlConfigApplier applier;
        private final DdcConfigSnapshotCoordinator coordinator;

        Harness(DdcConfigLkgStore store) {
            this.store = store;
            var base = snapshot(SCOPE, 1L, RESTART, true, "base");
            var target = new DdcConfigBaselineVO.TargetBindingVO("billing", "2", "test-cluster",
                    "runtime", "Deployment", "invoice", "deploy", "app");
            var rule = new DdcConfigBaselineVO.RuleVO("feature.text", ReloadModeEnum.HOT, "STRING", null, false, true);
            var metadata = new BaselineMetadataVO(SCOPE, new DdcConfigBaselineVO.SourceVO("811", "1"), true,
                    base.getContentSha256(), RESTART, List.of(rule), target, null);
            source = new DdcDynamicPropertySource("tianshu:application.yml",
                    DdcDynamicPropertySource.Snapshot.fromTyped("application.yml", 1, base.getContentSha256(), base.getHotValues()));
            var env = new StandardEnvironment();
            env.getPropertySources().addFirst(source);
            var registry = new DefaultDdcConfigApplierRegistry((key, value, version) -> { });
            registry.registerExact("feature.text", (key, value, version) -> leaf.set(value));
            applier = new DdcYamlConfigApplier(env, registry, mock(DdcFieldBindingService.class),
                    mock(DdcConfigurationPropertiesRebinder.class), event -> { }, 1048576,
                    DdcConfigFormatStrategyRegistry.defaults(), clock);
            var policy = new DdcConfigSnapshotCoordinator.PolicyVO("pod-a", Duration.ofMinutes(1), Duration.ofSeconds(30), false, null);
            coordinator = new DdcConfigSnapshotCoordinator(metadata, policy, http, store, applier, state, event -> { }, clock);
        }
        @Override public void close() { coordinator.close(); }
    }

    private static final class MutableClock extends Clock {
        private Instant time = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
        void advance(Duration timeElapsed) { time = time.plus(timeElapsed); }
    }
}
