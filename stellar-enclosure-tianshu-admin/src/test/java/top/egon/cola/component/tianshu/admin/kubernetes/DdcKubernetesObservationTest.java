package top.egon.cola.component.tianshu.admin.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.Failure;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.FailureKind;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ListPage;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.Mode;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ResourceKind;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ScopeVO;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.WatchEvent;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.WatchEventType;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.WatchHandle;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO.EndpointVO;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcKubernetesObservationTest {
    private static final Instant NOW = Instant.parse("2026-11-18T22:00:00Z");
    private static final String NAMESPACE = "apps-prod";
    private static final String DIGEST = "a".repeat(64);
    private static final TargetBindingVO TARGET = new TargetBindingVO("reader-binding", "1", "cluster-a", NAMESPACE,
            "Deployment", "reader", "deployment-uid", "app");

    @Test
    void serviceProjectionIsLocalAndFreshZeroEndpointsDoesNotInventReachability() {
        try (var fixture = new Fixture()) {
            assertThat(fixture.observer.currentServiceView(serviceBinding()).status()).isEqualTo(StatusEnum.INITIALIZING);
            fixture.items.put(ResourceKind.SERVICE, List.of(service()));
            fixture.observer.refresh();
            clearInvocations(fixture.api);
            var view = fixture.observer.currentServiceView(serviceBinding());
            assertThat(view.fresh()).isTrue();
            assertThat(view.endpoints()).isEmpty();
            assertThat(view.serviceResourceVersion()).isEqualTo("10");
            assertThat(view.observedAt()).isEqualTo(NOW);
            assertThat(view.validUntil()).isEqualTo(NOW.plusSeconds(30));
            verifyNoInteractions(fixture.api);
        }
    }

    @Test
    void serviceProjectionPreservesRawConditionsAndDeduplicatesDualStackWithoutAddresses() {
        try (var fixture = new Fixture()) {
            fixture.items.put(ResourceKind.SERVICE, List.of(service()));
            var ipv4 = endpointSlice("slice-v4", "IPv4", "running-pod", "reader-running", 18080);
            var ipv6 = endpointSlice("slice-v6", "IPv6", "running-pod", "reader-running", 18080);
            fixture.items.put(ResourceKind.ENDPOINT_SLICE, List.of(ipv4, ipv6));
            fixture.observer.refresh();
            var view = fixture.observer.currentServiceView(serviceBinding());
            assertThat(view.fresh()).isTrue();
            assertThat(view.endpoints()).containsExactly(new EndpointVO("running-pod", "reader-running", 18080, true, null, null));
            assertThat(view.toString()).doesNotContain("10.20.30.40", "2001:db8::1");
            assertThatThrownBy(() -> view.endpoints().clear()).isInstanceOf(UnsupportedOperationException.class);
            ((ObjectNode) ipv6.path("endpoints").get(0).path("conditions")).put("ready", false);
            fixture.session(ResourceKind.ENDPOINT_SLICE).emit(WatchEventType.MODIFIED, ipv6);
            assertInvalidService(fixture);
        }
    }

    @Test
    void associatedSliceLabelDoesNotAuthorizeForeignOrMissingControllerOwnership() {
        for (String mutation : List.of("foreign-owner", "missing-owner", "wrong-label", "wrong-owner-api", "two-controllers")) {
            try (var fixture = serviceFixture()) {
                var slice = endpointSlice("slice-v4", "IPv4", "running-pod", "reader-running", 18080);
                var metadata = (ObjectNode) slice.path("metadata");
                switch (mutation) {
                    case "foreign-owner" -> ((ObjectNode) metadata.path("ownerReferences").get(0)).put("uid", "foreign-service");
                    case "missing-owner" -> metadata.remove("ownerReferences");
                    case "wrong-label" -> ((ObjectNode) metadata.path("labels")).put("kubernetes.io/service-name", "other");
                    case "wrong-owner-api" -> ((ObjectNode) metadata.path("ownerReferences").get(0)).put("apiVersion", "apps/v1");
                    default -> ((com.fasterxml.jackson.databind.node.ArrayNode) metadata.path("ownerReferences")).add(metadata.path("ownerReferences").get(0).deepCopy());
                }
                fixture.items.put(ResourceKind.ENDPOINT_SLICE, List.of(slice)); fixture.observer.refresh();
                assertInvalidService(fixture);
            }
        }
    }

    @Test
    void serviceProjectionRejectsForeignMissingOrRecreatedPodAndUnverifiedWorkloadChain() {
        for (String mutation : List.of("foreign-namespace", "wrong-name", "wrong-uid", "missing-target", "foreign-workload", "missing-replica", "workload-name", "selector-mismatch")) {
            try (var fixture = serviceFixture()) {
                var slice = endpointSlice("slice-v4", "IPv4", "running-pod", "reader-running", 18080);
                var reference = (ObjectNode) slice.path("endpoints").get(0).path("targetRef");
                switch (mutation) {
                    case "foreign-namespace" -> reference.put("namespace", "other");
                    case "wrong-name" -> reference.put("name", "other");
                    case "wrong-uid" -> reference.put("uid", "recreated");
                    case "missing-target" -> ((ObjectNode) slice.path("endpoints").get(0)).remove("targetRef");
                    case "missing-replica" -> fixture.items.put(ResourceKind.REPLICA_SET, List.of());
                    case "foreign-workload" -> {
                        var replica = replicaSet("reader-rs", "replica-uid"); owner(replica, "Deployment", "other", "other-uid");
                        fixture.items.put(ResourceKind.REPLICA_SET, List.of(replica));
                    }
                    case "workload-name" -> {
                        var replica = replicaSet("reader-rs", "replica-uid"); owner(replica, "Deployment", "wrong-name", "deployment-uid");
                        fixture.items.put(ResourceKind.REPLICA_SET, List.of(replica));
                    }
                    default -> ((ObjectNode) fixture.items.get(ResourceKind.POD).getFirst().path("metadata").path("labels")).put("app", "other");
                }
                fixture.items.put(ResourceKind.ENDPOINT_SLICE, List.of(slice)); fixture.observer.refresh();
                assertInvalidService(fixture);
            }
        }
    }

    @Test
    void serviceIdentityPortProtocolAndEndpointConditionMismatchNeverYieldFreshEvidence() {
        for (String mutation : List.of("service-uid", "service-name", "service-port", "service-protocol", "slice-port", "slice-protocol", "condition")) {
            try (var fixture = serviceFixture()) {
                var service = (ObjectNode) fixture.items.get(ResourceKind.SERVICE).getFirst();
                var slice = endpointSlice("slice-v4", "IPv4", "running-pod", "reader-running", 18080);
                switch (mutation) {
                    case "service-uid" -> ((ObjectNode) service.path("metadata")).put("uid", "replacement");
                    case "service-name" -> ((ObjectNode) service.path("metadata")).put("name", "other");
                    case "service-port" -> ((ObjectNode) service.path("spec").path("ports").get(0)).put("port", 9999);
                    case "service-protocol" -> ((ObjectNode) service.path("spec").path("ports").get(0)).put("protocol", "UDP");
                    case "slice-port" -> ((ObjectNode) slice.path("ports").get(0)).put("port", 9999);
                    case "slice-protocol" -> ((ObjectNode) slice.path("ports").get(0)).put("protocol", "UDP");
                    default -> ((ObjectNode) slice.path("endpoints").get(0).path("conditions")).put("ready", "true");
                }
                fixture.items.put(ResourceKind.ENDPOINT_SLICE, List.of(slice)); fixture.observer.refresh();
                var view = fixture.observer.currentServiceView(serviceBinding());
                assertThat(view.fresh()).as(mutation).isFalse(); assertThat(view.endpoints()).isEmpty();
            }
        }
    }

    @Test
    void namedTargetPortIsVerifiedPerPodAndMissingOrAmbiguousDeclarationFailsClosed() {
        try (var fixture = serviceFixture()) {
            ((ObjectNode) fixture.items.get(ResourceKind.SERVICE).getFirst().path("spec").path("ports").get(0)).put("targetPort", "application");
            var pod = (ObjectNode) fixture.items.get(ResourceKind.POD).getFirst();
            var container = (ObjectNode) pod.path("spec").path("containers").get(0);
            container.putArray("ports").addObject().put("name", "application").put("containerPort", 19080).put("protocol", "TCP");
            var pendingContainer = (ObjectNode) fixture.items.get(ResourceKind.POD).getLast().path("spec").path("containers").get(0);
            pendingContainer.putArray("ports").addObject().put("name", "application").put("containerPort", 19081).put("protocol", "TCP");
            var pendingSlice = endpointSlice("slice-pending", "IPv4", "pending-pod", "reader-pending", 19081);
            ((ObjectNode) pendingSlice.path("endpoints").get(0).path("conditions")).put("ready", false).put("serving", true).put("terminating", true);
            var unrelatedSlice = endpointSlice("other-service", "IPv4", "unknown-pod", "other", 99);
            ((ObjectNode) unrelatedSlice.path("metadata").path("labels")).put("kubernetes.io/service-name", "other");
            ((ObjectNode) unrelatedSlice.path("metadata").path("ownerReferences").get(0)).put("name", "other").put("uid", "other-uid");
            fixture.items.put(ResourceKind.ENDPOINT_SLICE, List.of(endpointSlice("slice-v4", "IPv4", "running-pod", "reader-running", 19080), pendingSlice, unrelatedSlice));
            fixture.observer.refresh();
            assertThat(fixture.observer.currentServiceView(serviceBinding()).endpoints()).containsExactly(
                    new EndpointVO("pending-pod", "reader-pending", 19081, false, true, true),
                    new EndpointVO("running-pod", "reader-running", 19080, true, null, null));
            container.remove("ports");
            fixture.session(ResourceKind.POD).emit(WatchEventType.MODIFIED, pod); assertInvalidService(fixture);
            var ports = container.putArray("ports");
            ports.addObject().put("name", "application").put("containerPort", 19080);
            ports.addObject().put("name", "application").put("containerPort", 19080);
            fixture.session(ResourceKind.POD).emit(WatchEventType.MODIFIED, pod); assertInvalidService(fixture);
        }
    }

    @Test
    void serviceProjectionResolvesDefaultTargetPortAndStatefulOwnerWithoutInferringConditions() {
        try (var fixture = serviceFixture()) {
            ((ObjectNode) fixture.items.get(ResourceKind.SERVICE).getFirst().path("spec").path("ports").get(0)).remove("targetPort");
            var stateful = resource("StatefulSet", "reader-stateful", "deployment-uid");
            fixture.items.put(ResourceKind.STATEFUL_SET, List.of(stateful));
            var pod = pod("stateful-pod", "reader-stateful-0", false); owner(pod, "StatefulSet", "reader-stateful", "deployment-uid");
            fixture.items.put(ResourceKind.POD, List.of(pod));
            var slice = endpointSlice("slice-v4", "IPv4", "stateful-pod", "reader-stateful-0", 80);
            ((ObjectNode) slice.path("endpoints").get(0)).remove("conditions");
            fixture.items.put(ResourceKind.ENDPOINT_SLICE, List.of(slice)); fixture.observer.refresh();
            var view = fixture.observer.currentServiceView(serviceBinding());
            assertThat(view.fresh()).isTrue();
            assertThat(view.endpoints()).containsExactly(new EndpointVO("stateful-pod", "reader-stateful-0", 80, null, null, null));
        }
    }

    @Test
    void staleWatchFailureRelistAndClosedServiceProjectionNeverRetainFreshEndpoints() {
        try (var fixture = serviceFixture()) {
            fixture.items.put(ResourceKind.ENDPOINT_SLICE, List.of(endpointSlice("slice-v4", "IPv4", "running-pod", "reader-running", 18080)));
            fixture.observer.refresh();
            doAnswer(call -> {
                var view = fixture.observer.currentServiceView(serviceBinding());
                assertThat(view.status()).isEqualTo(StatusEnum.RELISTING); assertThat(view.endpoints()).isEmpty();
                return new ListPage("20", null, fixture.items.get(ResourceKind.SERVICE));
            }).when(fixture.api).list(eq(ResourceKind.SERVICE), eq(NAMESPACE), nullable(String.class), eq(500));
            fixture.observer.refresh();
            fixture.clock.advance(Duration.ofSeconds(30));
            assertThat(fixture.observer.currentServiceView(serviceBinding()).status()).isEqualTo(StatusEnum.STALE);
            assertThat(fixture.observer.currentServiceView(serviceBinding()).endpoints()).isEmpty();
            fixture.observer.refresh();
            fixture.session(ResourceKind.POD).completed.completeExceptionally(new Failure(FailureKind.GONE));
            assertThat(fixture.observer.currentServiceView(serviceBinding()).reasonCode()).isEqualTo("GONE");
            assertThat(fixture.observer.currentServiceView(serviceBinding()).endpoints()).isEmpty();
            fixture.observer.close();
            assertThat(fixture.observer.currentServiceView(serviceBinding()).status()).isEqualTo(StatusEnum.CLOSED);
        }
    }

    private static void assertInvalidService(Fixture fixture) {
        var view = fixture.observer.currentServiceView(serviceBinding());
        assertThat(view.status()).isEqualTo(StatusEnum.UNAVAILABLE);
        assertThat(view.reasonCode()).isEqualTo("INVALID_SERVICE_OBSERVATION"); assertThat(view.endpoints()).isEmpty();
    }

    private static Fixture serviceFixture() {
        var fixture = new Fixture(); fixture.items.put(ResourceKind.SERVICE, List.of(service())); return fixture;
    }

    private static DdcServiceObservationBindingVO serviceBinding() {
        return new DdcServiceObservationBindingVO(7, "tenant-seven", "business", "namespace", "prod", "reader", "service-binding", 1,
                "cluster-a", NAMESPACE, "reader-http", "service-uid", "http", 80, "http", "reader", "business", "1.0.0", Set.of("deployment-uid"));
    }

    private static ObjectNode service() {
        var service = resource("Service", "reader-http", "service-uid");
        var spec = service.putObject("spec"); spec.putObject("selector").put("app", "reader");
        spec.putArray("ports").addObject().put("name", "http").put("protocol", "TCP").put("port", 80).put("targetPort", 18080);
        return service;
    }

    private static ObjectNode endpointSlice(String name, String addressType, String podUid, String podName, int port) {
        var slice = resource("EndpointSlice", name, name + "-uid");
        var metadata = (ObjectNode) slice.path("metadata"); metadata.putObject("labels").put("kubernetes.io/service-name", "reader-http");
        metadata.putArray("ownerReferences").addObject().put("apiVersion", "v1").put("kind", "Service")
                .put("name", "reader-http").put("uid", "service-uid").put("controller", true);
        slice.put("addressType", addressType); slice.putArray("ports").addObject().put("name", "http").put("protocol", "TCP").put("port", port);
        var endpoint = slice.putArray("endpoints").addObject();
        endpoint.putArray("addresses").add("IPv4".equals(addressType) ? "10.20.30.40" : "2001:db8::1");
        endpoint.putObject("targetRef").put("apiVersion", "v1").put("kind", "Pod").put("namespace", NAMESPACE).put("name", podName).put("uid", podUid);
        endpoint.putObject("conditions").put("ready", true);
        return slice;
    }

    @Test
    void initialViewIsUnknownAndLookupNeverFetchesKubernetes() {
        try (var fixture = new Fixture()) {
            clearInvocations(fixture.api);
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.status()).isEqualTo(StatusEnum.INITIALIZING);
            assertThat(view.fresh()).isFalse();
            assertThat(view.currentIncarnations()).isEmpty();
            assertThat(view.observedAt()).isNull();
            verifyNoInteractions(fixture.api);
        }
    }

    @Test
    void completeSixKindBundlePreservesPendingPodAndGenerationLagWithoutInventingRolloutSuccess() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.fresh()).isTrue();
            assertThat(view.workloadGeneration()).isEqualTo(3);
            assertThat(view.observedGeneration()).isEqualTo(2);
            assertThat(view.desiredReplicas()).isEqualTo(2);
            assertThat(view.currentPods()).extracting(DdcKubernetesObservationService.PodObservationVO::podUid)
                    .containsExactly("pending-pod", "running-pod");
            assertThat(view.currentPods().getFirst().containerId()).isNull();
            assertThat(view.currentPods().getFirst().phase()).isEqualTo("Pending");
            assertThat(view.currentIncarnations()).containsExactly(new DdcKubernetesObservationService.ContainerIncarnationVO(
                    "running-pod", "app", "containerd://first"));
            assertThat(view.templateAnnotations()).containsEntry("stellar.io/config-restart-digest", "sha256:" + DIGEST);
            assertThat(view.configMapReferences()).containsExactlyEntriesOf(java.util.Map.of("baseline", "reader-config-r43"));
            assertThat(view.observedAt()).isEqualTo(NOW);
            assertThat(view.validUntil()).isEqualTo(NOW.plusSeconds(30));
            assertThat(view.invalidatedAt()).isNull();
            assertThatThrownBy(() -> view.currentPods().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> view.templateAnnotations().clear()).isInstanceOf(UnsupportedOperationException.class);
            for (var kind : ResourceKind.values()) verify(fixture.api).list(kind, NAMESPACE, null, 500);
            clearInvocations(fixture.api);
            fixture.observer.currentView(TARGET);
            verifyNoInteractions(fixture.api);
        }
    }

    @Test
    void listFailureInvalidatesWholeBundleAndNeverPublishesPartialReplacement() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            fixture.clock.advance(Duration.ofSeconds(1));
            fixture.items.put(ResourceKind.POD, List.of(pod("new-pod", "reader-new", false)));
            doAnswer(call -> { throw new Failure(FailureKind.FORBIDDEN); }).when(fixture.api)
                    .list(eq(ResourceKind.ENDPOINT_SLICE), eq(NAMESPACE), nullable(String.class), eq(500));
            fixture.observer.refresh();
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.status()).isEqualTo(StatusEnum.UNAVAILABLE);
            assertThat(view.reasonCode()).isEqualTo("FORBIDDEN");
            assertThat(view.invalidatedAt()).isEqualTo(NOW.plusSeconds(1));
            assertThat(view.currentPods()).extracting(DdcKubernetesObservationService.PodObservationVO::podUid)
                    .containsExactly("pending-pod", "running-pod");
            assertThat(fixture.sessions).allMatch(session -> session.closed.get());
        }
    }

    @Test
    void relistHasNoMixedBundleAndPagedCollectionMustHaveOneResourceVersion() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            var during = new ArrayList<StatusEnum>();
            doAnswer(call -> {
                during.add(fixture.observer.currentView(TARGET).status());
                return call.getArgument(2) == null
                        ? new ListPage("20", "next-page", List.of(pod("running-pod", "reader-running", true)))
                        : new ListPage("20", null, List.of(pod("pending-pod", "reader-pending", false)));
            }).when(fixture.api).list(eq(ResourceKind.POD), eq(NAMESPACE), nullable(String.class), eq(500));
            fixture.observer.refresh();
            assertThat(during).containsExactly(StatusEnum.RELISTING, StatusEnum.RELISTING);
            assertThat(fixture.observer.currentView(TARGET).fresh()).isTrue();
            verify(fixture.api).list(ResourceKind.POD, NAMESPACE, "next-page", 500);
            doAnswer(call -> call.getArgument(2) == null ? new ListPage("30", "next-page", List.of())
                    : new ListPage("31", null, List.of())).when(fixture.api)
                    .list(eq(ResourceKind.POD), eq(NAMESPACE), nullable(String.class), eq(500));
            fixture.observer.refresh();
            assertThat(fixture.observer.currentView(TARGET).fresh()).isFalse();
        }
    }

    @Test
    void repeatedContinuationIsRejectedWithABoundedNumberOfRequests() {
        try (var fixture = new Fixture()) {
            when(fixture.api.list(ResourceKind.POD, NAMESPACE, null, 500)).thenReturn(new ListPage("10", "same", List.of()));
            when(fixture.api.list(ResourceKind.POD, NAMESPACE, "same", 500)).thenReturn(new ListPage("10", "same", List.of()));
            fixture.observer.refresh();
            assertThat(fixture.observer.currentView(TARGET).status()).isEqualTo(StatusEnum.UNAVAILABLE);
            verify(fixture.api, times(1)).list(ResourceKind.POD, NAMESPACE, "same", 500);
            assertThat(fixture.sessions).isEmpty();
        }
    }

    @Test
    void goneAndNormalWatchEofInvalidateUntilACompleteRelistAndLateEpochCannotRevive() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            Session oldPods = fixture.session(ResourceKind.POD);
            fixture.clock.advance(Duration.ofSeconds(2));
            oldPods.completed.completeExceptionally(new Failure(FailureKind.GONE));
            assertThat(fixture.observer.currentView(TARGET).reasonCode()).isEqualTo("GONE");
            assertThat(fixture.sessions).allMatch(session -> session.closed.get());
            fixture.observer.refresh();
            assertThat(fixture.observer.currentView(TARGET).fresh()).isTrue();
            oldPods.emit(WatchEventType.DELETED, pod("running-pod", "reader-running", true));
            assertThat(fixture.observer.currentView(TARGET).currentPods()).hasSize(2);
            fixture.session(ResourceKind.POD).completed.complete(null);
            assertThat(fixture.observer.currentView(TARGET).reasonCode()).isEqualTo("WATCH_DISCONNECTED");
        }
    }

    @Test
    void containerRestartReplacesOldIncarnationButNotPodIdentity() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            ObjectNode restarted = pod("running-pod", "reader-running", true);
            ObjectNode status = (ObjectNode) restarted.path("status").path("containerStatuses").get(0);
            status.put("containerID", "containerd://second").put("restartCount", 1);
            fixture.session(ResourceKind.POD).emit(WatchEventType.MODIFIED, restarted);
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.currentPods()).hasSize(2);
            assertThat(view.currentIncarnations()).containsExactly(new DdcKubernetesObservationService.ContainerIncarnationVO(
                    "running-pod", "app", "containerd://second"));
            assertThat(view.currentPods().getLast().restartCount()).isEqualTo(1);
        }
    }

    @Test
    void sameNamePodRecreationDoesNotLetLateDeleteRemoveNewUid() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            var stream = fixture.session(ResourceKind.POD);
            stream.emit(WatchEventType.ADDED, pod("replacement-pod", "reader-running", false));
            stream.emit(WatchEventType.DELETED, pod("running-pod", "reader-running", true));
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.currentPods()).extracting(DdcKubernetesObservationService.PodObservationVO::podUid)
                    .containsExactly("pending-pod", "replacement-pod");
            assertThat(view.currentIncarnations()).isEmpty();
        }
    }

    @Test
    void currentOwnedOldReplicaSetPodsRemainButTerminalAndDeletingPodsExitDenominator() {
        try (var fixture = new Fixture()) {
            var oldReplica = replicaSet("old-rs", "old-rs-uid");
            var oldPod = pod("old-pod", "old-pod", true);
            owner(oldPod, "ReplicaSet", "old-rs", "old-rs-uid");
            var terminal = pod("terminal-pod", "terminal-pod", true);
            ((ObjectNode) terminal.path("status")).put("phase", "Succeeded");
            var deleting = pod("deleting-pod", "deleting-pod", true);
            ((ObjectNode) deleting.path("metadata")).put("deletionTimestamp", NOW.toString());
            fixture.items.put(ResourceKind.REPLICA_SET, List.of(replicaSet("reader-rs", "replica-uid"), oldReplica));
            fixture.items.put(ResourceKind.POD, List.of(oldPod, terminal, deleting));
            fixture.observer.refresh();
            assertThat(fixture.observer.currentView(TARGET).currentPods())
                    .extracting(DdcKubernetesObservationService.PodObservationVO::podUid).containsExactly("old-pod");
        }
    }

    @Test
    void missingReplicaSetForSelectedPodIsUnknownInsteadOfAnEmptySuccessfulDenominator() {
        try (var fixture = new Fixture()) {
            fixture.items.put(ResourceKind.REPLICA_SET, List.of());
            fixture.observer.refresh();
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.status()).isEqualTo(StatusEnum.UNAVAILABLE);
            assertThat(view.reasonCode()).isEqualTo("INVALID_WORKLOAD_OBSERVATION");
            assertThat(view.fresh()).isFalse();
        }
    }

    @Test
    void changedWorkloadUidAndCrossScopeBindingsAreNeverMatchedByName() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            var replaced = deployment();
            ((ObjectNode) replaced.path("metadata")).put("uid", "replacement-workload");
            fixture.session(ResourceKind.DEPLOYMENT).emit(WatchEventType.ADDED, replaced);
            assertThat(fixture.observer.currentView(TARGET).status()).isEqualTo(StatusEnum.IDENTITY_CONFLICT);
            var other = new TargetBindingVO("reader-binding", "1", "other-cluster", NAMESPACE,
                    "Deployment", "reader", "deployment-uid", "app");
            clearInvocations(fixture.api);
            assertThat(fixture.observer.currentView(other).reasonCode()).isEqualTo("OUT_OF_SCOPE");
            verifyNoInteractions(fixture.api);
        }
    }

    @Test
    void zeroReplicasIsARealFreshObservationWithoutAnyClaimOfAppliedConsumers() {
        try (var fixture = new Fixture()) {
            var zero = deployment();
            ((ObjectNode) zero.path("spec")).put("replicas", 0);
            fixture.items.put(ResourceKind.DEPLOYMENT, List.of(zero));
            fixture.items.put(ResourceKind.POD, List.of());
            fixture.observer.refresh();
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.fresh()).isTrue();
            assertThat(view.desiredReplicas()).isZero();
            assertThat(view.currentPods()).isEmpty();
            assertThat(view.currentIncarnations()).isEmpty();
        }
    }

    @Test
    void statefulSetUsesExactDirectControllerUidAndIncludesPendingPod() {
        try (var fixture = new Fixture()) {
            var stateful = deployment();
            stateful.put("kind", "StatefulSet");
            ((ObjectNode) stateful.path("metadata")).put("name", "reader-stateful").put("uid", "stateful-uid");
            var pending = pod("stateful-pod", "reader-stateful-0", false);
            owner(pending, "StatefulSet", "reader-stateful", "stateful-uid");
            fixture.items.put(ResourceKind.STATEFUL_SET, List.of(stateful));
            fixture.items.put(ResourceKind.POD, List.of(pending));
            fixture.observer.refresh();
            var target = new TargetBindingVO("stateful-binding", "1", "cluster-a", NAMESPACE,
                    "StatefulSet", "reader-stateful", "stateful-uid", "app");
            var view = fixture.observer.currentView(target);
            assertThat(view.fresh()).isTrue();
            assertThat(view.currentPods()).hasSize(1);
            assertThat(view.currentIncarnations()).isEmpty();
        }
    }

    @Test
    void oneKindBookmarkCannotRefreshTheOtherFiveKindsAndExactExpiryIsStale() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            fixture.clock.advance(Duration.ofSeconds(29));
            fixture.session(ResourceKind.POD).emit(WatchEventType.BOOKMARK, JsonNodeFactory.instance.objectNode());
            assertThat(fixture.observer.currentView(TARGET).observedAt()).isEqualTo(NOW);
            fixture.clock.advance(Duration.ofSeconds(1));
            var view = fixture.observer.currentView(TARGET);
            assertThat(view.status()).isEqualTo(StatusEnum.STALE);
            assertThat(view.invalidatedAt()).isEqualTo(NOW.plusSeconds(30));
        }
    }

    @Test
    void malformedWatchAndUnknownModificationInvalidateWholeView() {
        try (var fixture = new Fixture()) {
            fixture.observer.refresh();
            fixture.session(ResourceKind.POD).emit(WatchEventType.MODIFIED, pod("unseen-pod", "unseen-pod", true));
            assertThat(fixture.observer.currentView(TARGET).reasonCode()).isEqualTo("INVALID_RESPONSE");
            fixture.observer.refresh();
            var malformed = pod("running-pod", "reader-running", true);
            ((ObjectNode) malformed.path("metadata")).put("namespace", "unapproved");
            fixture.session(ResourceKind.POD).emit(WatchEventType.MODIFIED, malformed);
            assertThat(fixture.observer.currentView(TARGET).fresh()).isFalse();
        }
    }

    @Test
    void rolesAndWholeSecondWatchTimeoutAreValidatedBeforeAnyNetworkRequest() {
        var api = mock(DdcKubernetesApiClient.class);
        when(api.scope()).thenReturn(new ScopeVO(Mode.PUBLISHER, Set.of(NAMESPACE),
                Set.of(new DdcKubernetesApiClient.DeploymentKey(NAMESPACE, "reader"))));
        assertThatThrownBy(() -> observer(api, new MutableClock())).hasMessage("CONFIG_OBSERVATION_SCOPE_INVALID");
        when(api.scope()).thenReturn(new ScopeVO(Mode.OBSERVER, Set.of(NAMESPACE), Set.of()));
        assertThatThrownBy(() -> new DdcKubernetesObservationService(api, new MutableClock(), "cluster-a", Set.of(NAMESPACE),
                Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofMillis(1500)))
                .hasMessage("CONFIG_OBSERVATION_SCOPE_INVALID");
    }

    @Test
    void lifecycleStartsRealRefreshClosesEveryWatchAndLateEventsCannotReviveClosedState() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.observer.start();
            long limit = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (fixture.sessions.size() < ResourceKind.values().length && System.nanoTime() < limit) Thread.sleep(5);
            assertThat(fixture.sessions).hasSize(ResourceKind.values().length);
            assertThat(fixture.observer.isRunning()).isTrue();
            assertThat(fixture.observer.currentView(TARGET).fresh()).isTrue();
            fixture.observer.close();
            assertThat(fixture.observer.isRunning()).isFalse();
            assertThat(fixture.sessions).allMatch(session -> session.closed.get());
            fixture.session(ResourceKind.POD).emit(WatchEventType.ADDED, pod("late", "late", true));
            fixture.observer.refresh();
            assertThat(fixture.observer.currentView(TARGET).status()).isEqualTo(StatusEnum.CLOSED);
            assertThatThrownBy(fixture.observer::start).hasMessage("CONFIG_OBSERVATION_CLOSED");
        }
    }

    private static DdcKubernetesObservationService observer(DdcKubernetesApiClient api, Clock clock) {
        return new DdcKubernetesObservationService(api, clock, "cluster-a", Set.of(NAMESPACE), Duration.ofSeconds(30),
                Duration.ofSeconds(10), Duration.ofSeconds(5));
    }

    private static ObjectNode resource(String kind, String name, String uid) {
        var result = JsonNodeFactory.instance.objectNode();
        result.put("apiVersion", Set.of("Pod", "Service").contains(kind) ? "v1"
                : "EndpointSlice".equals(kind) ? "discovery.k8s.io/v1" : "apps/v1");
        result.put("kind", kind);
        result.putObject("metadata").put("name", name).put("namespace", NAMESPACE).put("uid", uid).put("resourceVersion", "10");
        return result;
    }

    private static ObjectNode deployment() {
        var result = resource("Deployment", "reader", "deployment-uid");
        ((ObjectNode) result.path("metadata")).put("generation", 3);
        var spec = result.putObject("spec");
        spec.put("replicas", 2);
        spec.putObject("selector").putObject("matchLabels").put("app", "reader");
        var template = spec.putObject("template");
        template.putObject("metadata").putObject("annotations").put("stellar.io/config-revision", "43")
                .put("stellar.io/config-content-sha256", DIGEST).put("stellar.io/config-restart-digest", DIGEST);
        template.putObject("spec").putArray("volumes").addObject().put("name", "baseline")
                .putObject("configMap").put("name", "reader-config-r43");
        result.putObject("status").put("observedGeneration", 2);
        return result;
    }

    private static ObjectNode replicaSet(String name, String uid) {
        var result = resource("ReplicaSet", name, uid);
        owner(result, "Deployment", "reader", "deployment-uid");
        return result;
    }

    private static ObjectNode pod(String uid, String name, boolean running) {
        var result = resource("Pod", name, uid);
        ((ObjectNode) result.path("metadata")).putObject("labels").put("app", "reader");
        owner(result, "ReplicaSet", "reader-rs", "replica-uid");
        result.putObject("spec").putArray("containers").addObject().put("name", "app");
        var status = result.putObject("status").put("phase", running ? "Running" : "Pending");
        status.putArray("conditions").addObject().put("type", "Ready").put("status", running ? "True" : "False");
        if (running) status.putArray("containerStatuses").addObject().put("name", "app").put("containerID", "containerd://first")
                .put("restartCount", 0).putObject("state").putObject("running").put("startedAt", NOW.minusSeconds(1).toString());
        return result;
    }

    private static void owner(ObjectNode resource, String kind, String name, String uid) {
        ((ObjectNode) resource.path("metadata")).putArray("ownerReferences").addObject()
                .put("apiVersion", "apps/v1").put("kind", kind).put("name", name).put("uid", uid).put("controller", true);
    }

    private static final class Fixture implements AutoCloseable {
        final DdcKubernetesApiClient api = mock(DdcKubernetesApiClient.class);
        final MutableClock clock = new MutableClock();
        final EnumMap<ResourceKind, List<JsonNode>> items = new EnumMap<>(ResourceKind.class);
        final List<Session> sessions = new CopyOnWriteArrayList<>();
        final DdcKubernetesObservationService observer;
        Fixture() {
            for (var kind : ResourceKind.values()) items.put(kind, List.of());
            items.put(ResourceKind.DEPLOYMENT, List.of(deployment()));
            items.put(ResourceKind.REPLICA_SET, List.of(replicaSet("reader-rs", "replica-uid")));
            items.put(ResourceKind.POD, List.of(pod("running-pod", "reader-running", true), pod("pending-pod", "reader-pending", false)));
            when(api.scope()).thenReturn(new ScopeVO(Mode.OBSERVER, Set.of(NAMESPACE), Set.of()));
            when(api.list(any(ResourceKind.class), eq(NAMESPACE), nullable(String.class), eq(500)))
                    .thenAnswer(call -> new ListPage("10", null, items.get(call.<ResourceKind>getArgument(0))));
            when(api.watch(any(ResourceKind.class), eq(NAMESPACE), anyString(), eq(Duration.ofSeconds(5)), any()))
                    .thenAnswer(call -> {
                        var session = new Session(call.getArgument(0), call.getArgument(4));
                        sessions.add(session);
                        return session;
                    });
            observer = observer(api, clock);
        }
        Session session(ResourceKind kind) { return sessions.stream().filter(session -> session.kind == kind).reduce((a, b) -> b).orElseThrow(); }
        @Override public void close() { observer.close(); }
    }

    private static final class Session implements WatchHandle {
        final ResourceKind kind;
        final Consumer<WatchEvent> receiver;
        final CompletableFuture<Void> completed = new CompletableFuture<>();
        final AtomicBoolean closed = new AtomicBoolean();
        Session(ResourceKind kind, Consumer<WatchEvent> receiver) { this.kind = kind; this.receiver = receiver; }
        void emit(WatchEventType type, JsonNode object) {
            receiver.accept(new WatchEvent(type, object.path("metadata").path("resourceVersion").asText("10"), object));
        }
        @Override public CompletionStage<Void> completion() { return completed; }
        @Override public void close() { closed.set(true); completed.complete(null); }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
