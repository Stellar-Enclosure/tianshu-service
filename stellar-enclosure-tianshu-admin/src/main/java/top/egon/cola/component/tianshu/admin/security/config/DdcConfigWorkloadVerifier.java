package top.egon.cola.component.tianshu.admin.security.config;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** OAuth authorization is established by the servlet chain before this independent Pod proof is considered. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigWorkloadVerifier {
    public static final String READ_SCOPE = "tianshu:config:read";
    public static final String RECEIPT_SCOPE = "tianshu:config:receipt:write";
    @Qualifier("ddcKubernetesApiClient")
    private final DdcKubernetesApiClient api;
    @Qualifier("ddcTenantRegistry")
    private final DdcTenantRegistry registry;
    @Qualifier("ddcConfigWorkloadBindings")
    private final BindingsVO bindings;

    public DdcConfigWorkloadIdentityVO verify(@NotNull ServiceIdentityPrincipal principal,
                                              @NotBlank String projectedPodToken, @NotBlank String bootId) {
        if (principal == null) throw invalidProof();
        if (!principal.expiresAt().isAfter(Instant.now())) throw invalidProof();
        if (principal.scopeContext() != ServiceTokenContext.TENANT
                || (!principal.scopes().contains(READ_SCOPE) && !principal.scopes().contains(RECEIPT_SCOPE))) {
            throw denied();
        }
        if (projectedPodToken == null || !projectedPodToken.matches("[A-Za-z0-9._~+/-]{1,16384}=*")) throw invalidProof();
        if (bootId == null || !bootId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw invalidProof();
        var target = bindings.targets().stream().filter(value -> value.matches(principal)).findFirst().orElseThrow(DdcConfigWorkloadVerifier::denied);
        final DdcTenantContextDTO tenant;
        try {
            var registered = registry.requireByIdentityTenantId(principal.tenantId());
            if (!target.scope().getTenantId().equals(Long.toString(registered.getTenantId()))) throw denied();
            tenant = new DdcTenantContextDTO(registered.getTenantId(), registered.getIdentityTenantId(),
                    "service:" + principal.credentialId());
        } catch (IllegalArgumentException unregistered) {
            throw denied();
        }
        try {
            // No cache, forwarded Pod coordinates, token claims decoding or caller-selected Kubernetes path.
            var review = api.tokenReview(projectedPodToken, bindings.audience());
            requireKind(review, "authentication.k8s.io/v1", "TokenReview");
            var status = review.path("status");
            if (!status.isObject() || !status.path("authenticated").isBoolean()) throw unavailable();
            if (status.hasNonNull("error") && !status.path("error").asText().isBlank()) throw unavailable();
            if (!status.path("authenticated").booleanValue()) throw invalidProof();
            var audiences = status.path("audiences");
            if (!audiences.isArray() || audiences.size() != 1
                    || !audiences.get(0).isTextual() || !bindings.audience().equals(audiences.get(0).textValue())) throw invalidProof();
            var user = status.path("user");
            if (!text(user, "username").equals("system:serviceaccount:" + target.k8sNamespace() + ":" + target.serviceAccountName())) throw denied();
            String podName = proofExtra(user, "authentication.kubernetes.io/pod-name");
            String podUid = proofExtra(user, "authentication.kubernetes.io/pod-uid");
            var pod = api.pod(target.k8sNamespace(), podName);
            requireKind(pod, "v1", "Pod");
            var podMetadata = metadata(pod, target.k8sNamespace(), podName);
            if (!podUid.equals(text(podMetadata, "uid")) || podMetadata.hasNonNull("deletionTimestamp")) throw invalidProof();
            if (!target.serviceAccountName().equals(text(pod.path("spec"), "serviceAccountName"))) throw denied();
            var labels = podMetadata.path("labels");
            for (var selector : target.podSelector().entrySet()) {
                if (!selector.getValue().equals(text(labels, selector.getKey()))) throw denied();
            }
            String containerId = runningContainer(pod, target.containerName());
            var podOwner = controller(podMetadata, "ReplicaSet");
            var replicaSet = api.replicaSet(target.k8sNamespace(), text(podOwner, "name"));
            requireKind(replicaSet, "apps/v1", "ReplicaSet");
            var replicaMetadata = metadata(replicaSet, target.k8sNamespace(), text(podOwner, "name"));
            if (!text(podOwner, "uid").equals(text(replicaMetadata, "uid"))) throw invalidProof();
            if (replicaMetadata.hasNonNull("deletionTimestamp")) throw unavailable();
            var deploymentOwner = controller(replicaMetadata, "Deployment");
            if (!target.deploymentName().equals(text(deploymentOwner, "name"))
                    || !target.workloadUid().equals(text(deploymentOwner, "uid"))) throw denied();
            var deployment = api.deployment(target.k8sNamespace(), target.deploymentName());
            requireKind(deployment, "apps/v1", "Deployment");
            var deploymentMetadata = metadata(deployment, target.k8sNamespace(), target.deploymentName());
            if (!target.workloadUid().equals(text(deploymentMetadata, "uid"))) throw denied();
            if (deploymentMetadata.hasNonNull("deletionTimestamp")) throw unavailable();
            var selector = deployment.path("spec").path("selector");
            var expressions = selector.path("matchExpressions");
            if (!expressions.isMissingNode() && (!expressions.isArray() || !expressions.isEmpty())) throw denied();
            var matchLabels = selector.path("matchLabels");
            if (!matchLabels.isObject() || matchLabels.size() != target.podSelector().size()) throw denied();
            for (var entry : target.podSelector().entrySet()) {
                if (!entry.getValue().equals(text(matchLabels, entry.getKey()))) throw denied();
            }
            long generation = positiveLong(deploymentMetadata, "generation");
            if (positiveLong(deployment.path("status"), "observedGeneration") < generation) throw unavailable();
            return new DdcConfigWorkloadIdentityVO(tenant, target.scope(), target.targetBindingId(),
                    target.targetBindingRevision(), target.clusterId(), target.k8sNamespace(), target.workloadUid(),
                    podUid, target.containerName(), containerId, bootId);
        } catch (DdcKubernetesApiClient.NotFound absent) {
            throw invalidProof();
        } catch (DdcKubernetesApiClient.Unavailable authorityFailure) {
            throw unavailable();
        } catch (IllegalArgumentException malformedProof) {
            throw invalidProof();
        }
    }

    private static String runningContainer(JsonNode pod, String name) {
        if (!"Running".equals(text(pod.path("status"), "phase"))) throw unavailable();
        var declared = pod.path("spec").path("containers");
        if (!declared.isArray()) throw unavailable();
        int declaredMatches = 0;
        for (var value : declared) if (name.equals(text(value, "name"))) declaredMatches++;
        if (declaredMatches != 1) throw denied();
        var statuses = pod.path("status").path("containerStatuses");
        if (!statuses.isArray()) throw unavailable();
        String result = null;
        for (var value : statuses) {
            if (!name.equals(text(value, "name"))) continue;
            if (result != null || !value.path("state").path("running").isObject()) throw unavailable();
            String startedAt = text(value.path("state").path("running"), "startedAt");
            try { Instant.parse(startedAt); }
            catch (java.time.DateTimeException malformedTime) { throw unavailable(); }
            result = text(value, "containerID");
        }
        if (result == null) throw unavailable();
        return result;
    }

    private static JsonNode controller(JsonNode metadata, String kind) {
        var owners = metadata.path("ownerReferences");
        if (!owners.isArray()) throw unavailable();
        JsonNode controller = null;
        for (var owner : owners) {
            if (!owner.path("controller").isBoolean() || !owner.path("controller").booleanValue()) continue;
            if (controller != null) throw unavailable();
            controller = owner;
        }
        if (controller == null || !"apps/v1".equals(text(controller, "apiVersion"))
                || !kind.equals(text(controller, "kind"))) throw denied();
        text(controller, "name");
        text(controller, "uid");
        return controller;
    }

    private static JsonNode metadata(JsonNode object, String namespace, String name) {
        var metadata = object.path("metadata");
        if (!namespace.equals(text(metadata, "namespace")) || !name.equals(text(metadata, "name"))) throw unavailable();
        text(metadata, "uid");
        text(metadata, "resourceVersion");
        return metadata;
    }

    private static void requireKind(JsonNode object, String version, String kind) {
        if (object == null || !object.isObject() || !version.equals(text(object, "apiVersion"))
                || !kind.equals(text(object, "kind"))) throw unavailable();
    }

    private static String proofExtra(JsonNode user, String name) {
        var value = user.path("extra").path(name);
        if (!value.isArray() || value.size() != 1 || !value.get(0).isTextual()
                || value.get(0).textValue().isBlank() || value.get(0).textValue().length() > 253) throw invalidProof();
        return value.get(0).textValue();
    }

    private static String text(JsonNode node, String name) {
        var value = node.path(name);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 512) throw unavailable();
        return value.textValue();
    }

    private static long positiveLong(JsonNode node, String name) {
        var value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw unavailable();
        return value.longValue();
    }

    private static Rejected invalidProof() { return new Rejected(HttpStatus.UNAUTHORIZED, "CONFIG_WORKLOAD_PROOF_INVALID"); }
    private static Rejected denied() { return new Rejected(HttpStatus.FORBIDDEN, "CONFIG_WORKLOAD_TARGET_DENIED"); }
    private static Rejected unavailable() { return new Rejected(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_WORKLOAD_AUTHORITY_UNAVAILABLE"); }

    public static final class Rejected extends RuntimeException {
        private final HttpStatus status;
        private final String errorCode;
        public Rejected(HttpStatus status, String errorCode) {
            super(errorCode, null, false, false);
            this.status = Objects.requireNonNull(status, "status");
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        }
        public HttpStatus status() { return status; }
        public String errorCode() { return errorCode; }
    }

    public record BindingsVO(@NotBlank String audience, @NotNull @Valid List<BindingVO> targets) {
        public BindingsVO {
            fixedToken(audience, 512);
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
            if (targets.isEmpty()) throw new IllegalArgumentException("CONFIG_WORKLOAD_BINDINGS_REQUIRED");
            Set<String> selectors = new HashSet<>();
            Set<String> identifiers = new HashSet<>();
            for (var target : targets) {
                if (!selectors.add(target.identityTenantId() + "\u0000" + target.clientId())
                        || !identifiers.add(target.targetBindingId())) {
                    throw new IllegalArgumentException("CONFIG_WORKLOAD_BINDING_AMBIGUOUS");
                }
            }
        }
    }

    public record BindingVO(@NotBlank String targetBindingId, @Positive long targetBindingRevision,
                            @NotBlank String identityTenantId, @NotBlank String clientId,
                            @NotNull Set<String> credentialIds, @NotBlank String appId,
                            @NotBlank String sourceBizCode, @NotBlank String sourceAppCode, @NotBlank String sourceEnvironment,
                            @NotNull URI resourceUri, @NotNull @Valid DdcConfigScopeVO scope,
                            @NotBlank String clusterId, @NotBlank String k8sNamespace, @NotBlank String serviceAccountName,
                            @NotBlank String deploymentName, @NotBlank String workloadUid, @NotBlank String containerName,
                            @NotNull Map<String, String> podSelector) {
        public BindingVO {
            for (String value : new String[]{targetBindingId, identityTenantId, clientId, appId, sourceBizCode,
                    sourceAppCode, sourceEnvironment, clusterId, workloadUid}) fixedToken(value, 128);
            if (targetBindingRevision <= 0) throw new IllegalArgumentException("CONFIG_TARGET_BINDING_REVISION_INVALID");
            credentialIds = Set.copyOf(Objects.requireNonNull(credentialIds, "credentialIds"));
            if (credentialIds.isEmpty()) throw new IllegalArgumentException("CONFIG_WORKLOAD_CREDENTIALS_REQUIRED");
            credentialIds.forEach(value -> fixedToken(value, 128));
            Objects.requireNonNull(scope, "scope");
            if (!scope.getBizCode().equals(sourceBizCode) || !scope.getAppCode().equals(sourceAppCode)
                    || !scope.getEnv().equals(sourceEnvironment)) throw new IllegalArgumentException("CONFIG_WORKLOAD_SCOPE_MISMATCH");
            if (resourceUri == null || !"https".equals(resourceUri.getScheme()) || resourceUri.getHost() == null
                    || resourceUri.getRawUserInfo() != null || resourceUri.getRawQuery() != null
                    || resourceUri.getRawFragment() != null) throw new IllegalArgumentException("CONFIG_WORKLOAD_RESOURCE_INVALID");
            for (String value : new String[]{k8sNamespace, serviceAccountName, deploymentName, containerName}) {
                if (value == null || value.length() > 63 || !value.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                    throw new IllegalArgumentException("CONFIG_WORKLOAD_NAME_INVALID");
                }
            }
            podSelector = Map.copyOf(Objects.requireNonNull(podSelector, "podSelector"));
            if (podSelector.isEmpty() || podSelector.size() > 32) throw new IllegalArgumentException("CONFIG_WORKLOAD_SELECTOR_INVALID");
            podSelector.forEach((key, value) -> { fixedToken(key, 253); fixedToken(value, 63); });
        }
        private boolean matches(ServiceIdentityPrincipal principal) {
            return identityTenantId.equals(principal.tenantId()) && clientId.equals(principal.clientId())
                    && credentialIds.contains(principal.credentialId()) && appId.equals(principal.appId())
                    && sourceBizCode.equals(principal.sourceBizCode()) && sourceAppCode.equals(principal.sourceAppCode())
                    && sourceEnvironment.equals(principal.sourceEnvironment()) && resourceUri.equals(principal.resourceUri());
        }
    }

    private static void fixedToken(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || "*".equals(value)
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException("CONFIG_WORKLOAD_BINDING_INVALID");
        }
    }
}
