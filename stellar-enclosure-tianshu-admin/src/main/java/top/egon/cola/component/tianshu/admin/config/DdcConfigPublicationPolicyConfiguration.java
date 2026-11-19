package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliverySecurityConfiguration;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier.BindingVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyResolver;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The policy installed with the deployment is the authority for publication rules, references and target UIDs. */
@Slf4j
@Configuration(value = "ddcConfigPublicationPolicyConfiguration", proxyBeanMethods = false)
public class DdcConfigPublicationPolicyConfiguration {
    public static final String PREFIX = "egon.cola.component.tianshu.admin.config-publication";
    private static final int MAX_POLICY_BYTES = 1024 * 1024;
    private static final int MAX_PUBLICATION_SCOPES = 128;
    private static final int MAX_TARGETS_PER_SCOPE = 32;

    @Bean("ddcConfigPublicationPolicies")
    RegisteredPolicies ddcConfigPublicationPolicies(@Qualifier("environment") Environment environment,
            @Qualifier("jacksonObjectMapper") ObjectMapper json,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants) {
        String file = environment.getProperty(PREFIX + ".policy-file", "");
        String sha256 = environment.getProperty(PREFIX + ".policy-sha256", "");
        if (file.isBlank() && sha256.isBlank()) {
            return new RegisteredPolicies(Map.of(), List.of());
        }
        var workloadBindings = Binder.get(environment)
                .bind(DdcConfigDeliverySecurityConfiguration.PREFIX + ".bindings", Bindable.listOf(BindingVO.class))
                .orElse(List.of());
        var policies = load(Path.of(file), sha256, workloadBindings, tenants, json);
        log.info("Loaded pinned Tianshu configuration policy. scopes={} targets={}",
                policies.byScope().size(), policies.targets().size());
        return policies;
    }

    @Bean("ddcConfigPublicationPolicyResolver")
    PublicationPolicyResolver ddcConfigPublicationPolicyResolver(
            @Qualifier("ddcConfigPublicationPolicies") RegisteredPolicies policies,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants) {
        return (trusted, scope) -> requirePolicy(policies, tenants, trusted, scope);
    }

    private PublicationPolicyVO requirePolicy(RegisteredPolicies policies, DdcTenantRegistry tenants,
            DdcTenantContextDTO trusted, DdcConfigScopeVO scope) {
        if (trusted == null || scope == null || !scope.getTenantId().equals(Long.toString(trusted.tenantId()))
                || !trusted.identityTenantId().equals(tenants.requireEnabledTenant(trusted.tenantId()).getIdentityTenantId())) {
            throw invalid("TIANSHU_CONFIG_PUBLICATION_SCOPE_UNVERIFIED");
        }
        var policy = policies.byScope().get(scope);
        if (policy == null) {
            throw invalid("TIANSHU_CONFIG_PUBLICATION_SCOPE_UNREGISTERED");
        }
        return policy;
    }

    static RegisteredPolicies load(Path path, String sha256, List<BindingVO> verifiedBindings,
            DdcTenantRegistry tenants, ObjectMapper json) {
        if (path == null || !path.isAbsolute() || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
            throw invalid("TIANSHU_CONFIG_PUBLICATION_POLICY_PIN_REQUIRED");
        }
        var strict = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        strict.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        strict.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        strict.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(16).maxStringLength(16_384).maxNumberLength(32).build());
        try (var file = Files.newInputStream(path)) {
            byte[] bytes = file.readNBytes(MAX_POLICY_BYTES + 1);
            if (bytes.length < 2 || bytes.length > MAX_POLICY_BYTES
                    || !MessageDigest.isEqual(HexFormat.of().parseHex(sha256), sha256(bytes))) {
                throw invalid("TIANSHU_CONFIG_PUBLICATION_POLICY_HASH_MISMATCH");
            }
            PublicationPolicyFileVO document = strict.readValue(bytes, PublicationPolicyFileVO.class);
            if (document == null || !"1".equals(document.schemaVersion()) || document.policies() == null
                    || document.policies().isEmpty() || document.policies().size() > MAX_PUBLICATION_SCOPES) {
                throw invalid("TIANSHU_CONFIG_PUBLICATION_POLICY_INVALID");
            }
            return register(document.policies(), verifiedBindings, tenants);
        } catch (IOException failure) {
            throw invalid("TIANSHU_CONFIG_PUBLICATION_POLICY_UNAVAILABLE");
        }
    }

    private static RegisteredPolicies register(List<PublicationPolicyVO> policies, List<BindingVO> verifier,
            DdcTenantRegistry tenants) {
        Map<String, BindingVO> verifiedById = new LinkedHashMap<>();
        for (BindingVO binding : verifier) {
            if (verifiedById.putIfAbsent(binding.targetBindingId(), binding) != null) {
                throw invalid("TIANSHU_CONFIG_PUBLICATION_BINDING_AMBIGUOUS");
            }
        }
        Map<DdcConfigScopeVO, PublicationPolicyVO> byScope = new LinkedHashMap<>();
        Map<DeploymentVO, DdcConfigScopeVO> deploymentOwners = new LinkedHashMap<>();
        var usedIds = new HashSet<String>();
        var targets = new ArrayList<RegisteredTargetVO>();
        for (var policy : policies) {
            if (policy == null || policy.targetBindings().isEmpty()
                    || policy.targetBindings().size() > MAX_TARGETS_PER_SCOPE
                    || byScope.putIfAbsent(policy.scope(), policy) != null) {
                throw invalid("TIANSHU_CONFIG_PUBLICATION_POLICY_INVALID");
            }
            long ownerId = Long.parseLong(policy.scope().getTenantId());
            String ownerIdentity = tenants.requireEnabledTenant(ownerId).getIdentityTenantId();
            for (var target : policy.targetBindings()) {
                var candidate = verifiedById.get(target.getBindingId());
                if (candidate == null || !usedIds.add(target.getBindingId())
                        || !sameTarget(candidate, policy.scope(), ownerIdentity, target)) {
                    throw invalid("TIANSHU_CONFIG_PUBLICATION_TARGET_UNVERIFIED");
                }
                var deployment = new DeploymentVO(target.getClusterId(), target.getKubernetesNamespace(),
                        target.getWorkloadName(), target.getWorkloadUid());
                var existing = deploymentOwners.putIfAbsent(deployment, policy.scope());
                if (existing != null && !existing.equals(policy.scope())) {
                    throw invalid("TIANSHU_CONFIG_PUBLICATION_TARGET_MULTIPLE_SCOPES");
                }
                targets.add(new RegisteredTargetVO(policy.scope(), target));
            }
        }
        return new RegisteredPolicies(byScope, targets);
    }

    private static boolean sameTarget(BindingVO verified, DdcConfigScopeVO scope, String ownerIdentity,
            TargetBindingVO target) {
        return verified.scope().equals(scope) && verified.identityTenantId().equals(ownerIdentity)
                && Long.toString(verified.targetBindingRevision()).equals(target.getBindingRevision())
                && "Deployment".equals(target.getWorkloadKind())
                && verified.clusterId().equals(target.getClusterId())
                && verified.k8sNamespace().equals(target.getKubernetesNamespace())
                && verified.deploymentName().equals(target.getWorkloadName())
                && verified.workloadUid().equals(target.getWorkloadUid())
                && verified.containerName().equals(target.getContainerName());
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 support is missing", impossible);
        }
    }

    private static IllegalStateException invalid(String code) {
        return new IllegalStateException(code);
    }

    private record DeploymentVO(String clusterId, String namespace, String name, String uid) { }

    public record PublicationPolicyFileVO(String schemaVersion, List<PublicationPolicyVO> policies) { }

    public record RegisteredTargetVO(DdcConfigScopeVO scope, TargetBindingVO target) { }

    public record RegisteredPolicies(Map<DdcConfigScopeVO, PublicationPolicyVO> byScope, List<RegisteredTargetVO> targets) {
        public RegisteredPolicies {
            byScope = Map.copyOf(byScope);
            targets = List.copyOf(targets);
        }
    }
}
