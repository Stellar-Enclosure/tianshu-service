package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.DeploymentKey;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.Failure;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.FailureKind;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ResourceSnapshot;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Reconciles one immutable release without retaining Kubernetes or database locks between attempts. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigBaselineReconciler {
    private static final String VOLUME = "tianshu-baseline";
    private static final String NAME_PREFIX = "tianshu-baseline-";
    private static final String BASELINE_KEY = "baseline.json";
    private static final String REVISION = "stellar.io/config-revision";
    private static final String CONTENT = "stellar.io/config-content-sha256";
    private static final String RESTART = "stellar.io/config-restart-digest";
    private static final int MAX_BASELINE_BYTES = 900 * 1024;
    private static final int MAX_TARGETS = 32;

    @NonNull @Qualifier("ddcKubernetesConfigPublisherApiClient") private final DdcKubernetesApiClient kubernetes;
    @NonNull @Qualifier("jacksonObjectMapper") private final ObjectMapper json;
    @NonNull @Qualifier("ddcPublishClock") private final Clock clock;
    @NonNull @Qualifier("ddcConfigPublisherClusterId") private final String clusterId;
    @NonNull @Qualifier("ddcConfigPublisherTargetAuthority") private final PublisherTargetAuthority authority;

    public List<TargetResultVO> reconcile(@Valid @NotNull DdcConfigBaselineVO baseline,
            @NotNull BooleanSupplier stillCurrent) {
        ensureOutsideTransaction();
        if (baseline.getTargetBindings().isEmpty() || baseline.getTargetBindings().size() > MAX_TARGETS
                || !validDigests(baseline)) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_BASELINE_INVALID");
        }
        byte[] serialized = DdcConfigCanonicalJson.canonicalBytes(json.valueToTree(baseline));
        if (serialized.length > MAX_BASELINE_BYTES) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_BASELINE_TOO_LARGE");
        }
        String body = new String(serialized, StandardCharsets.UTF_8);
        String candidateName = nameOf(serialized);
        List<TargetResultVO> results = new ArrayList<>();
        for (TargetBindingVO target : baseline.getTargetBindings()) {
            try {
                results.add(reconcileTarget(baseline, target, body, candidateName, stillCurrent));
            } catch (Failure failure) {
                results.add(result(target, null, null, retryable(failure.kind()) ? "PENDING" : "FAILED",
                        "TIANSHU_CONFIG_KUBERNETES_" + failure.kind().name()));
            } catch (BaselineConflict conflict) {
                results.add(result(target, null, null, "FAILED", conflict.getMessage()));
            }
        }
        return List.copyOf(results);
    }

    private TargetResultVO reconcileTarget(DdcConfigBaselineVO desired, TargetBindingVO target, String body,
            String candidateName, BooleanSupplier stillCurrent) {
        if (!clusterId.equals(target.getClusterId()) || !"Deployment".equals(target.getWorkloadKind())
                || !authority.allows(desired.getScope(), target)) {
            throw conflict("TIANSHU_CONFIG_TARGET_OUTSIDE_PUBLISHER_SCOPE");
        }
        if (!stillCurrent.getAsBoolean()) {
            return result(target, null, null, "SUPERSEDED", "TIANSHU_CONFIG_SUPERSEDED");
        }
        var key = new DeploymentKey(target.getKubernetesNamespace(), target.getWorkloadName());
        var workload = kubernetes.getDeployment(key);
        requireWorkloadUid(workload, target);
        String mountedName = mountedConfigMapName(workload);
        JsonNode annotations = workload.document().path("spec").path("template").path("metadata").path("annotations");
        long desiredRevision = Long.parseLong(desired.getSource().getRevision());
        long workloadRevision = revision(annotations.path(REVISION));
        if (workloadRevision > desiredRevision) {
            throw conflict("TIANSHU_CONFIG_WORKLOAD_REVISION_AHEAD");
        }

        Optional<ResourceSnapshot> mounted = portableName(mountedName)
                ? kubernetes.getConfigMap(key.namespace(), mountedName) : Optional.empty();
        if (mounted.isPresent() && compatibleMountedBaseline(mounted.orElseThrow(), mountedName, target, desired)) {
            // HOT stays on the verified current immutable startup baseline, including at zero replicas.
            if (workloadRevision == desiredRevision
                    && (!hex(desired.getContentSha256()).equals(annotations.path(CONTENT).textValue())
                    || !hex(desired.getRestartDigest()).equals(annotations.path(RESTART).textValue()))) {
                throw conflict("TIANSHU_CONFIG_WORKLOAD_REVISION_PAYLOAD_CONFLICT");
            }
            return stillCurrent.getAsBoolean()
                    ? result(target, mounted.orElseThrow(), workload, "APPLIED", null)
                    : result(target, null, null, "SUPERSEDED", "TIANSHU_CONFIG_SUPERSEDED");
        }
        if (workloadRevision == desiredRevision && !candidateName.equals(mountedName)) {
            throw conflict("TIANSHU_CONFIG_WORKLOAD_REVISION_PAYLOAD_CONFLICT");
        }

        if (!stillCurrent.getAsBoolean()) {
            return result(target, null, null, "SUPERSEDED", "TIANSHU_CONFIG_SUPERSEDED");
        }
        var artifact = ensureArtifact(key.namespace(), candidateName, body);
        if (!stillCurrent.getAsBoolean()) {
            return result(target, artifact, null, "SUPERSEDED", "TIANSHU_CONFIG_SUPERSEDED");
        }
        Map<String, String> desiredAnnotations = Map.of(REVISION, desired.getSource().getRevision(),
                CONTENT, hex(desired.getContentSha256()), RESTART, hex(desired.getRestartDigest()));
        var updated = kubernetes.patchDeploymentBaseline(key, workload.uid(), workload.resourceVersion(),
                candidateName, desiredAnnotations);
        requireWorkloadUid(updated, target);
        JsonNode updatedAnnotations = updated.document().path("spec").path("template").path("metadata").path("annotations");
        if (!candidateName.equals(mountedConfigMapName(updated))
                || !desiredAnnotations.entrySet().stream().allMatch(
                entry -> entry.getValue().equals(updatedAnnotations.path(entry.getKey()).textValue()))) {
            throw conflict("TIANSHU_CONFIG_WORKLOAD_WRITE_NOT_CONFIRMED");
        }
        if (!stillCurrent.getAsBoolean()) {
            return result(target, artifact, updated, "SUPERSEDED", "TIANSHU_CONFIG_SUPERSEDED");
        }
        return result(target, artifact, updated, "APPLIED", null);
    }

    private ResourceSnapshot ensureArtifact(String namespace, String name, String body) {
        var existing = kubernetes.getConfigMap(namespace, name);
        ResourceSnapshot artifact;
        if (existing.isPresent()) {
            artifact = existing.orElseThrow();
        } else {
            try {
                artifact = kubernetes.createImmutableConfigMap(namespace, name, body);
            } catch (Failure collision) {
                if (collision.kind() != FailureKind.CONFLICT) {
                    throw collision;
                }
                artifact = kubernetes.getConfigMap(namespace, name).orElseThrow(() -> collision);
            }
        }
        if (!body.equals(immutableBody(artifact, name))) {
            throw conflict("TIANSHU_CONFIG_IMMUTABLE_ARTIFACT_CONFLICT");
        }
        return artifact;
    }

    private boolean compatibleMountedBaseline(ResourceSnapshot current, String mountedName, TargetBindingVO target,
            DdcConfigBaselineVO desired) {
        try {
            String body = immutableBody(current, mountedName);
            JsonNode payload = DdcConfigCanonicalJson.parse(body.getBytes(StandardCharsets.UTF_8));
            DdcConfigBaselineVO old = json.treeToValue(payload, DdcConfigBaselineVO.class);
            if (!desired.getScope().equals(old.getScope())
                    || !desired.getSource().getConfigId().equals(old.getSource().getConfigId())) {
                throw conflict("TIANSHU_CONFIG_WORKLOAD_BASELINE_OWNER_CONFLICT");
            }
            return Long.parseLong(old.getSource().getRevision()) <= Long.parseLong(desired.getSource().getRevision())
                    && old.getTargetBindings().contains(target) && validDigests(old)
                    && desired.getRestartDigest().equals(old.getRestartDigest());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalid) {
            throw conflict("TIANSHU_CONFIG_MOUNTED_BASELINE_INVALID");
        }
    }

    private static String immutableBody(ResourceSnapshot object, String expectedName) {
        var document = object.document();
        var data = document.path("data");
        String body = data.path(BASELINE_KEY).textValue();
        if (!expectedName.equals(object.name()) || !document.path("immutable").isBoolean()
                || !document.path("immutable").booleanValue() || !data.isObject() || data.size() != 1
                || document.has("binaryData") || body == null || body.isEmpty()
                || body.getBytes(StandardCharsets.UTF_8).length > MAX_BASELINE_BYTES
                || !expectedName.equals(nameOf(body.getBytes(StandardCharsets.UTF_8)))) {
            throw conflict("TIANSHU_CONFIG_IMMUTABLE_ARTIFACT_CONFLICT");
        }
        return body;
    }

    private static String mountedConfigMapName(ResourceSnapshot workload) {
        var volumes = workload.document().path("spec").path("template").path("spec").path("volumes");
        String name = null;
        for (JsonNode volume : volumes) {
            if (!VOLUME.equals(volume.path("name").textValue())) {
                continue;
            }
            if (name != null || !volume.path("configMap").path("name").isTextual()) {
                throw conflict("TIANSHU_CONFIG_WORKLOAD_VOLUME_CONFLICT");
            }
            name = volume.path("configMap").path("name").textValue();
        }
        if (name == null || name.isBlank()) {
            throw conflict("TIANSHU_CONFIG_WORKLOAD_VOLUME_CONFLICT");
        }
        return name;
    }

    private static boolean validDigests(DdcConfigBaselineVO baseline) {
        return baseline.getRestartDigest().equals(DdcConfigCanonicalJson.restartDigest(baseline))
                && baseline.getContentSha256().equals(DdcConfigCanonicalJson.contentSha256(baseline));
    }

    private static boolean portableName(String name) {
        return name.matches("tianshu-baseline-[0-9a-f]{64}");
    }

    private static String nameOf(byte[] bytes) {
        try {
            return NAME_PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 support is missing", impossible);
        }
    }

    private static long revision(JsonNode annotation) {
        if (annotation.isMissingNode()) {
            return 0;
        }
        String text = annotation.textValue();
        try {
            if (text != null && text.matches("[1-9][0-9]{0,18}")) {
                return Long.parseLong(text);
            }
        } catch (NumberFormatException ignored) {
            // An overflowing generation cannot become an ordering fence.
        }
        throw conflict("TIANSHU_CONFIG_WORKLOAD_REVISION_INVALID");
    }

    private static void requireWorkloadUid(ResourceSnapshot workload, TargetBindingVO target) {
        if (!target.getWorkloadUid().equals(workload.uid()) || workload.generation() < 1
                || !target.getKubernetesNamespace().equals(workload.namespace())
                || !target.getWorkloadName().equals(workload.name())) {
            throw conflict("TIANSHU_CONFIG_WORKLOAD_UID_CONFLICT");
        }
    }

    private static String hex(String prefixedDigest) {
        return prefixedDigest.substring("sha256:".length());
    }

    private static void ensureOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TIANSHU_CONFIG_KUBERNETES_TRANSACTION_FORBIDDEN");
        }
    }

    private static boolean retryable(FailureKind kind) {
        return kind == FailureKind.CONFLICT || kind == FailureKind.GONE || kind == FailureKind.THROTTLED
                || kind == FailureKind.UNAVAILABLE;
    }

    private TargetResultVO result(TargetBindingVO target, ResourceSnapshot artifact, ResourceSnapshot workload,
            String status, String errorCode) {
        log.debug("Tianshu config rollout. bindingId={} status={} errorCode={}", target.getBindingId(), status, errorCode);
        return new TargetResultVO(target.getBindingId(), target.getBindingRevision(), target.getClusterId(),
                target.getKubernetesNamespace(), target.getWorkloadUid(), artifact == null ? null : artifact.name(),
                artifact == null ? null : artifact.uid(), workload == null ? null : Long.toString(workload.generation()),
                workload == null ? null : workload.resourceVersion(), clock.instant(), status, errorCode);
    }

    private static BaselineConflict conflict(String code) {
        return new BaselineConflict(code);
    }

    private static final class BaselineConflict extends IllegalStateException {
        private BaselineConflict(String code) {
            super(code);
        }
    }

    public record TargetResultVO(String bindingId, String bindingRevision, String clusterId, String namespace,
            String workloadUid, String configMapName, String configMapUid, String workloadGeneration,
            String resourceVersion, Instant observedAt, String status, String errorCode) { }

    @FunctionalInterface
    public interface PublisherTargetAuthority {
        boolean allows(DdcConfigScopeVO scope, TargetBindingVO target);
    }
}
