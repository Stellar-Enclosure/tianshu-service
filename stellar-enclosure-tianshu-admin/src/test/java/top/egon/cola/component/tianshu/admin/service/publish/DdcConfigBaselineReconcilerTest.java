package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.DeploymentKey;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.Failure;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.FailureKind;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ResourceSnapshot;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.SourceVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcConfigBaselineReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-11-18T20:20:00Z");
    private static final String CONFIG_MAP_PREFIX = "tianshu-baseline-";
    private static final String EMPTY_DIGEST = "sha256:" + "0".repeat(64);
    private static final String WORKLOAD_UID = "workload-uid";
    private static final String NAMESPACE = "platform-prod";
    private static final DeploymentKey WORKLOAD = new DeploymentKey(NAMESPACE, "tianshu-app-stable");
    private static final TargetBindingVO TARGET = new TargetBindingVO(
            "binding-1", "5", "cluster-1", NAMESPACE, "Deployment", WORKLOAD.name(), WORKLOAD_UID, "app");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DdcKubernetesApiClient kube = mock(DdcKubernetesApiClient.class);
    private final DdcConfigBaselineReconciler reconciler = new DdcConfigBaselineReconciler(
            kube, json, Clock.fixed(NOW, ZoneOffset.UTC), "cluster-1", (scope, target) -> TARGET.equals(target));

    @Test
    void hotOnVerifiedBaselineAtZeroReplicasNeverRollsTheWorkload() throws Exception {
        var mounted = baseline("42", 12, 100);
        var desired = baseline("43", 12, 200);
        var map = configMap("cm-v42-uid", mounted);
        when(kube.getDeployment(WORKLOAD)).thenReturn(workload(WORKLOAD_UID, "rv-a", 6, map.name(), mounted));
        when(kube.getConfigMap(NAMESPACE, map.name())).thenReturn(Optional.of(map));

        var results = reconciler.reconcile(desired, () -> true);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("APPLIED");
            assertThat(result.configMapUid()).isEqualTo("cm-v42-uid");
            assertThat(result.workloadGeneration()).isEqualTo("6");
            assertThat(result.observedAt()).isEqualTo(NOW);
        });
        verify(kube).getDeployment(WORKLOAD);
        verify(kube).getConfigMap(NAMESPACE, map.name());
        org.mockito.Mockito.verifyNoMoreInteractions(kube);
    }

    @Test
    void createThenPatchConflictRetriesAgainstTheSameImmutableArtifact() throws Exception {
        var mounted = baseline("42", 12, 100);
        var desired = baseline("43", 16, 200);
        var old = configMap("cm-v42-uid", mounted);
        var candidate = configMap("cm-v43-uid", desired);
        when(kube.getDeployment(WORKLOAD))
                .thenReturn(workload(WORKLOAD_UID, "rv-a", 6, old.name(), mounted),
                        workload(WORKLOAD_UID, "rv-b", 6, old.name(), mounted));
        when(kube.getConfigMap(NAMESPACE, old.name())).thenReturn(Optional.of(old));
        when(kube.getConfigMap(NAMESPACE, candidate.name())).thenReturn(Optional.empty(), Optional.of(candidate));
        when(kube.createImmutableConfigMap(eq(NAMESPACE), eq(candidate.name()), anyString())).thenReturn(candidate);
        when(kube.patchDeploymentBaseline(WORKLOAD, WORKLOAD_UID, "rv-a", candidate.name(), annotations(desired)))
                .thenThrow(new Failure(FailureKind.CONFLICT));
        when(kube.patchDeploymentBaseline(WORKLOAD, WORKLOAD_UID, "rv-b", candidate.name(), annotations(desired)))
                .thenReturn(workload(WORKLOAD_UID, "rv-c", 7, candidate.name(), desired));

        assertThat(reconciler.reconcile(desired, () -> true)).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.errorCode()).isEqualTo("TIANSHU_CONFIG_KUBERNETES_CONFLICT");
        });
        assertThat(reconciler.reconcile(desired, () -> true)).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("APPLIED");
            assertThat(result.resourceVersion()).isEqualTo("rv-c");
            assertThat(result.workloadGeneration()).isEqualTo("7");
        });
        verify(kube).createImmutableConfigMap(NAMESPACE, candidate.name(), document(desired));
    }

    @Test
    void changedCurrentIntentAfterArtifactCreateNeverPatchesTheOldIntent() throws Exception {
        var desired = baseline("43", 16, 200);
        var candidate = configMap("cm-v43-uid", desired);
        when(kube.getDeployment(WORKLOAD))
                .thenReturn(workload(WORKLOAD_UID, "rv-a", 6, "seed-placeholder", null));
        when(kube.getConfigMap(NAMESPACE, candidate.name())).thenReturn(Optional.empty());
        when(kube.createImmutableConfigMap(NAMESPACE, candidate.name(), document(desired))).thenReturn(candidate);
        var currentReads = new AtomicInteger();

        assertThat(reconciler.reconcile(desired, () -> currentReads.incrementAndGet() < 3)).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("SUPERSEDED"));
        verify(kube).getDeployment(WORKLOAD);
        verify(kube).getConfigMap(NAMESPACE, candidate.name());
        verify(kube).createImmutableConfigMap(NAMESPACE, candidate.name(), document(desired));
        org.mockito.Mockito.verifyNoMoreInteractions(kube);
    }

    @Test
    void sameNamedRecreatedWorkloadIsNeverAdopted() {
        var desired = baseline("43", 16, 200);
        when(kube.getDeployment(WORKLOAD))
                .thenReturn(workload("replacement-uid", "rv-a", 1, "seed-placeholder", null));

        assertThat(reconciler.reconcile(desired, () -> true)).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.errorCode()).isEqualTo("TIANSHU_CONFIG_WORKLOAD_UID_CONFLICT");
        });
        verify(kube).getDeployment(WORKLOAD);
        org.mockito.Mockito.verifyNoMoreInteractions(kube);
    }

    @Test
    void alreadySupersededEventDoesNotAccessKubernetes() {
        assertThat(reconciler.reconcile(baseline("43", 16, 200), () -> false)).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("SUPERSEDED"));
        verifyNoInteractions(kube);
    }

    @Test
    void previouslyFrozenTargetReassignedByInstallationPolicyNeverTouchesKubernetes() {
        var writer = new DdcConfigBaselineReconciler(kube, json, Clock.fixed(NOW, ZoneOffset.UTC), "cluster-1",
                (scope, target) -> "reassigned-app".equals(scope.getAppCode()));
        assertThat(writer.reconcile(baseline("43", 16, 200), () -> true)).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.errorCode()).isEqualTo("TIANSHU_CONFIG_TARGET_OUTSIDE_PUBLISHER_SCOPE");
        });
        verifyNoInteractions(kube);
    }

    private DdcConfigBaselineVO baseline(String revision, int pool, int limit) {
        var builder = DdcConfigBaselineVO.builder().schemaVersion("1")
                .scope(new DdcConfigScopeVO("7001", "example", "staging", "api", "application.yml"))
                .source(new SourceVO("101", revision)).enabled(true).rules(List.of()).targetBindings(List.of(TARGET))
                .restartValues(Map.of("app.pool", IntNode.valueOf(pool)))
                .hotSeed(Map.of("app.limit", IntNode.valueOf(limit)))
                .startupDependencies(Map.of()).secretReferences(Map.of())
                .restartDigest(EMPTY_DIGEST).contentSha256(EMPTY_DIGEST);
        String restart = DdcConfigCanonicalJson.restartDigest(builder.build());
        var candidates = builder.restartDigest(restart);
        return candidates.contentSha256(DdcConfigCanonicalJson.contentSha256(candidates.build())).build();
    }

    private String document(DdcConfigBaselineVO baseline) {
        return new String(DdcConfigCanonicalJson.canonicalBytes(json.valueToTree(baseline)), StandardCharsets.UTF_8);
    }

    private ResourceSnapshot configMap(String uid, DdcConfigBaselineVO baseline) throws Exception {
        String document = document(baseline);
        String name = CONFIG_MAP_PREFIX + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
        var root = json.createObjectNode().put("kind", "ConfigMap").put("immutable", true);
        root.putObject("data").put("baseline.json", document);
        return new ResourceSnapshot(NAMESPACE, name, uid, "cm-rv", 0, root);
    }

    private ResourceSnapshot workload(String uid, String rv, long generation, String name, DdcConfigBaselineVO baseline) {
        var root = json.createObjectNode().put("kind", "Deployment");
        var spec = root.putObject("spec").put("replicas", 0);
        var template = spec.putObject("template");
        if (baseline != null) {
            var annotations = template.putObject("metadata").putObject("annotations");
            annotations(baseline).forEach(annotations::put);
        }
        template.putObject("spec").putArray("volumes").addObject()
                .put("name", "tianshu-baseline").putObject("configMap").put("name", name);
        return new ResourceSnapshot(NAMESPACE, WORKLOAD.name(), uid, rv, generation, root);
    }

    private Map<String, String> annotations(DdcConfigBaselineVO baseline) {
        return Map.of("stellar.io/config-revision", baseline.getSource().getRevision(),
                "stellar.io/config-content-sha256", baseline.getContentSha256().substring(7),
                "stellar.io/config-restart-digest", baseline.getRestartDigest().substring(7));
    }
}
