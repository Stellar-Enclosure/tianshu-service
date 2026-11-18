package top.egon.cola.component.tianshu.admin.service.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliveryTokenService;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.environment.DdcReservedConfigurationKeys;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Validated
@RequiredArgsConstructor
public class DdcConfigSnapshotService {

    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder().build())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_CONFIG_BYTES = 1_048_576;
    private static final int MAX_PATHS = 10_000;

    @NonNull @Qualifier("ddcConfigItemRepository") private final DdcConfigItemRepository items;
    @NonNull @Qualifier("ddcConfigVersionRepository") private final DdcConfigVersionRepository versions;
    @NonNull @Qualifier("ddcConfigBootBindingRepository") private final DdcConfigBootBindingRepository bootBindings;
    @NonNull @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor tenantContext;
    @NonNull @Qualifier("ddcConfigDeliveryTokenService") private final DdcConfigDeliveryTokenService deliveryTokens;
    @NonNull @Qualifier("ddcConfigDeliveryClock") private final Clock clock;
    @NonNull @Qualifier("ddcConfigSnapshotValidity") private final Duration snapshotValidity;

    public Snapshot snapshot(@Valid @NotNull DdcConfigWorkloadIdentityVO workload, String ifNoneMatch) {
        Published published = requireCurrent(workload);
        try {
            return tenantContext.call(workload.tenant(), () -> {
                var expected = DdcConfigBootBindingBO.builder().clusterId(workload.clusterId())
                        .k8sNamespace(workload.k8sNamespace()).workloadUid(workload.workloadUid())
                        .podUid(workload.podUid()).containerName(workload.containerName())
                        .containerId(workload.containerId()).bootId(workload.bootId()).build();
                var previous = bootBindings.findConfirmed(workload.tenant(), expected);
                long fence = previous.orElseGet(() -> bootBindings.createOrConfirm(workload.tenant(), expected)).getId();
                DdcConfigSnapshotDTO content = published.snapshot();
                String etag = DdcConfigCanonicalJson.etag(content);
                String token = deliveryTokens.issue(workload, fence, published.configId(), content);
                return new Snapshot(content, etag, token, previous.isPresent() && etag.equals(ifNoneMatch));
            });
        } catch (DdcConfigBootBindingRepository.BindingConflictException conflict) {
            throw new Rejected(HttpStatus.CONFLICT, "TIANSHU_CONFIG_BOOT_BINDING_CONFLICT");
        } catch (DdcConfigBootBindingRepository.BindingUnavailableException | DataAccessException
                 | TransactionException | DdcConfigDeliveryTokenService.Unavailable unavailable) {
            throw unavailable();
        }
    }

    public Published requireCurrent(@Valid @NotNull DdcConfigWorkloadIdentityVO workload) {
        try {
            return tenantContext.call(workload.tenant(), () -> readPublished(workload));
        } catch (DataAccessException | TransactionException failure) {
            log.debug("Could not read the authoritative published configuration; failureType={}",
                    failure.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private Published readPublished(DdcConfigWorkloadIdentityVO workload) {
        var scope = workload.scope();
        var item = items.findByBizCodeAndEnvAndAppCodeAndResourceName(
                        scope.getBizCode(), scope.getEnv(), scope.getAppCode(), scope.getResourceName())
                .filter(row -> !Boolean.TRUE.equals(row.getDeleted()))
                .filter(row -> row.getPublishedVersion() != null && row.getPublishedVersion() > 0)
                .orElseThrow(() -> new Rejected(HttpStatus.NOT_FOUND, "TIANSHU_CONFIG_NOT_PUBLISHED"));
        long configId = item.getId();
        Long revision = item.getPublishedVersion();
        var version = versions.findByConfigItemIdAndRevision(configId, revision)
                .orElseThrow(DdcConfigSnapshotService::unavailable);
        JsonNode manifest = version.getManifestJson();
        if (manifest == null || !manifest.isObject() || !textEquals(manifest, "schemaVersion", "1")
                || !textEquals(manifest.path("source"), "configId", Long.toString(configId))
                || !textEquals(manifest.path("source"), "revision", Long.toString(revision))
                || !Objects.equals(manifest.get("scope"), JSON.valueToTree(scope))
                || !manifest.path("enabled").isBoolean() || !manifest.path("targetBindings").isArray()) {
            throw unavailable();
        }
        boolean authorized = false;
        for (JsonNode target : manifest.path("targetBindings")) {
            if (matchesTarget(target, workload)) {
                if (authorized) {
                    throw unavailable();
                }
                authorized = true;
            }
        }
        if (!authorized) {
            throw new Rejected(HttpStatus.FORBIDDEN, "TIANSHU_CONFIG_TARGET_NOT_AUTHORIZED");
        }
        try {
            var enabled = manifest.get("enabled").booleanValue();
            var dependency = manifest.path("dependencySnapshotId");
            if (!dependency.isNull() && !dependency.isTextual()) {
                throw unavailable();
            }
            var snapshot = new DdcConfigSnapshotDTO("1", scope, Long.toString(revision), enabled,
                    manifest.path("restartDigest").textValue(), manifest.path("contentSha256").textValue(),
                    projectHotValues(version.getNewContent(), enabled, manifest.path("rules")),
                    dependency.textValue(), clock.instant().plus(snapshotValidity));
            if (!snapshot.getContentSha256().equals(DdcConfigCanonicalJson.contentSha256(snapshot))) {
                throw unavailable();
            }
            return new Published(configId, snapshot, manifest.deepCopy());
        } catch (IOException | IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    /** Defines the typed projection shared with creation of an immutable release manifest. */
    public static Map<String, JsonNode> projectHotValues(String yaml, boolean enabled, JsonNode rules) throws IOException {
        if (!rules.isArray() || rules.size() > MAX_PATHS) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_INVALID");
        }
        Map<String, JsonNode> paths = enabled ? flatProperties(yaml) : Map.of();
        var seen = new HashSet<String>();
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode jsonRule : rules) {
            var rule = JSON.treeToValue(jsonRule, DdcConfigBaselineVO.RuleVO.class);
            if (rule == null || !seen.add(rule.getKey()) || DdcReservedConfigurationKeys.isReserved(rule.getKey())) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_INVALID");
            }
            if (enabled && rule.getMode() == DdcConfigBaselineVO.ReloadModeEnum.HOT) {
                JsonNode value = paths.get(rule.getKey());
                validateHotValue(rule, value);
                if (value != null) {
                    result.put(rule.getKey(), value.deepCopy());
                }
            }
        }
        var classified = new java.util.TreeSet<>(seen);
        for (var entry : result.entrySet()) {
            String path = entry.getKey();
            if (entry.getValue().isContainerNode()
                    && (hasPrefix(classified, path + ".") || hasPrefix(classified, path + "["))) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_OVERLAP");
            }
        }
        return result;
    }

    private static Map<String, JsonNode> flatProperties(String yaml) throws IOException {
        if (yaml == null || yaml.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_BODY_INVALID");
        }
        JsonNode source = YAML.readTree(yaml);
        if (source == null || !source.isObject()) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_BODY_INVALID");
        }
        Map<String, JsonNode> paths = new LinkedHashMap<>();
        flatten(source, "", paths, 0);
        return paths;
    }

    private static void validateHotValue(DdcConfigBaselineVO.RuleVO rule, JsonNode value) {
        if (value == null && rule.getDeletionAllowed()
                || value != null && value.isNull() && rule.getNullable()) {
            return;
        }
        boolean valid = value != null && switch (rule.getValueType()) {
            case "STRING", "DECIMAL_STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "INTEGER" -> value.isIntegralNumber();
            case "NUMBER" -> value.isNumber();
            case "OBJECT" -> value.isObject();
            case "ARRAY" -> value.isArray();
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_RULE_VALUE_INVALID");
        }
    }

    private static boolean hasPrefix(java.util.NavigableSet<String> keys, String prefix) {
        String next = keys.ceiling(prefix);
        return next != null && next.startsWith(prefix);
    }

    private static void flatten(JsonNode node, String path, Map<String, JsonNode> paths, int depth) {
        if (depth > 64 || paths.size() >= MAX_PATHS || path.length() > 256) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_BODY_LIMIT");
        }
        if (!path.isEmpty() && paths.putIfAbsent(path, node) != null) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_PATH_AMBIGUOUS");
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> flatten(field.getValue(),
                    path.isEmpty() ? field.getKey() : path + "." + field.getKey(), paths, depth + 1));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                flatten(node.get(index), path + "[" + index + "]", paths, depth + 1);
            }
        }
    }

    private static boolean matchesTarget(JsonNode target, DdcConfigWorkloadIdentityVO workload) {
        return textEquals(target, "bindingId", workload.targetBindingId())
                && textEquals(target, "bindingRevision", Long.toString(workload.targetBindingRevision()))
                && textEquals(target, "clusterId", workload.clusterId())
                && textEquals(target, "kubernetesNamespace", workload.k8sNamespace())
                && textEquals(target, "workloadKind", "Deployment")
                && textEquals(target, "workloadUid", workload.workloadUid())
                && textEquals(target, "containerName", workload.containerName());
    }

    private static boolean textEquals(JsonNode node, String name, String expected) {
        return expected.equals(node.path(name).textValue());
    }

    private static Rejected unavailable() {
        return new Rejected(HttpStatus.SERVICE_UNAVAILABLE, "TIANSHU_CONFIG_DELIVERY_UNAVAILABLE");
    }

    public record Snapshot(DdcConfigSnapshotDTO content, String etag, String deliveryToken, boolean notModified) {
        @Override public String toString() {
            return "ConfigSnapshot[etag=" + etag + ", notModified=" + notModified + "]";
        }
    }

    public record Published(long configId, DdcConfigSnapshotDTO snapshot, JsonNode manifest) {
        @Override public String toString() {
            return "PublishedConfig[configId=" + configId + ", revision=" + snapshot.getReleaseRevision() + "]";
        }
    }

    public static final class Rejected extends RuntimeException {
        private final HttpStatus status;
        private final String errorCode;

        public Rejected(HttpStatus status, String errorCode) {
            super(errorCode);
            this.status = status;
            this.errorCode = errorCode;
        }

        public HttpStatus status() { return status; }

        public String errorCode() { return errorCode; }
    }
}
