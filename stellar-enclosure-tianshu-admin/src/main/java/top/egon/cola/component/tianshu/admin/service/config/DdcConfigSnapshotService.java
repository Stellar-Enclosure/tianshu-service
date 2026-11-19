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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
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
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
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
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
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
    @NonNull @Qualifier("ddcConfigManifestFactory") private final DdcConfigManifestFactory manifests;

    /** Source-compatible local construction; production assembly supplies the shared factory Bean. */
    public DdcConfigSnapshotService(DdcConfigItemRepository items, DdcConfigVersionRepository versions,
            DdcConfigBootBindingRepository bootBindings, DdcTenantContextExecutor tenantContext,
            DdcConfigDeliveryTokenService deliveryTokens, Clock clock, Duration snapshotValidity) {
        this(items, versions, bootBindings, tenantContext, deliveryTokens, clock, snapshotValidity,
                new DdcConfigManifestFactory(JSON));
    }

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
                .filter(row -> row.getPublishedVersion() != null && row.getPublishedVersion() > 0)
                .orElseThrow(() -> new Rejected(HttpStatus.NOT_FOUND, "TIANSHU_CONFIG_NOT_PUBLISHED"));
        long configId = item.getId();
        Long revision = item.getPublishedVersion();
        if (Boolean.TRUE.equals(item.getDeleted()) && (!Boolean.FALSE.equals(item.getEnabled())
                || !Objects.equals(item.getCurrentVersion(), revision))) {
            throw new Rejected(HttpStatus.NOT_FOUND, "TIANSHU_CONFIG_NOT_PUBLISHED");
        }
        var version = versions.findByConfigItemIdAndRevision(configId, revision)
                .orElseThrow(DdcConfigSnapshotService::unavailable);
        final DdcConfigManifestFactory.FrozenConfigVO frozen;
        try {
            if (!Objects.equals(version.getConfigItemId(), configId)
                    || !Objects.equals(version.getConfigRevision(), revision)) { throw unavailable(); }
            frozen = manifests.materialize(version, scope);
            // A legacy tombstone cannot expose an old enabled publication. Only its exact native disable is readable.
            if (Boolean.TRUE.equals(item.getDeleted()) && Boolean.TRUE.equals(frozen.baseline().getEnabled())) {
                throw new Rejected(HttpStatus.NOT_FOUND, "TIANSHU_CONFIG_NOT_PUBLISHED");
            }
        } catch (IllegalArgumentException failure) {
            throw unavailable();
        }
        JsonNode manifest = frozen.manifest();
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
        var baseline = frozen.baseline();
        var snapshot = new DdcConfigSnapshotDTO("1", scope, Long.toString(revision), baseline.getEnabled(),
                baseline.getRestartDigest(), baseline.getContentSha256(), baseline.getHotSeed(),
                baseline.getDependencySnapshotId(), clock.instant().plus(snapshotValidity));
        return new Published(configId, snapshot, manifest.deepCopy());
    }

    /** Defines the typed projection shared with creation of an immutable release manifest. */
    public static Map<String, JsonNode> projectHotValues(String yaml, boolean enabled, JsonNode rules) throws IOException {
        if (!rules.isArray() || rules.size() > MAX_PATHS) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_INVALID");
        }
        List<DdcConfigBaselineVO.RuleVO> declared = new ArrayList<>();
        for (JsonNode rule : rules) { declared.add(JSON.treeToValue(rule, DdcConfigBaselineVO.RuleVO.class)); }
        return projectValues(yaml, enabled, declared).hotValues();
    }

    /** One pure classifier is shared by publication and exact-version materialization. */
    public static ConfigProjectionVO projectValues(String yaml, boolean enabled,
            List<DdcConfigBaselineVO.RuleVO> declared) throws IOException {
        if (declared == null || declared.size() > MAX_PATHS) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_INVALID");
        }
        Map<String, JsonNode> paths = flatProperties(yaml);
        var seen = new HashSet<String>();
        List<DdcConfigBaselineVO.RuleVO> effective = new ArrayList<>(declared);
        for (var rule : declared) {
            if (rule == null || !seen.add(rule.getKey()) || DdcReservedConfigurationKeys.isReserved(rule.getKey())) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_INVALID");
            }
            if (!java.util.Set.of("STRING", "DECIMAL_STRING", "BOOLEAN", "INTEGER", "NUMBER", "OBJECT", "ARRAY")
                    .contains(rule.getValueType())) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_INVALID");
            }
        }
        var classified = new java.util.TreeSet<>(seen);
        for (String path : classified) {
            if (hasPrefix(classified, path + ".") || hasPrefix(classified, path + "[")) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_OVERLAP");
            }
        }
        for (var entry : paths.entrySet()) {
            String path = entry.getKey();
            if (DdcReservedConfigurationKeys.isReserved(path)) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_INVALID");
            }
            JsonNode value = entry.getValue();
            boolean covered = isClassified(path, seen);
            if (!covered && (!value.isContainerNode() || value.isEmpty())) {
                effective.add(new DdcConfigBaselineVO.RuleVO(path, DdcConfigBaselineVO.ReloadModeEnum.RESTART,
                        valueType(value), null, value.isNull(), true));
            }
        }
        effective.sort(Comparator.comparing(DdcConfigBaselineVO.RuleVO::getKey));
        validateDistinctPropertySlots(effective);
        Map<String, JsonNode> restart = new LinkedHashMap<>();
        Map<String, JsonNode> hot = new LinkedHashMap<>();
        for (var rule : effective) {
            JsonNode value = paths.get(rule.getKey());
            if (!rule.acceptsValue(value)) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULE_VALUE_INVALID");
            }
            if (value != null && rule.getMode() == DdcConfigBaselineVO.ReloadModeEnum.RESTART) {
                restart.put(rule.getKey(), value.deepCopy());
            } else if (value != null && enabled) {
                hot.put(rule.getKey(), value.deepCopy());
            }
        }
        return new ConfigProjectionVO(effective, restart, hot);
    }

    private static boolean isClassified(String path, java.util.Set<String> classified) {
        if (classified.contains(path)) { return true; }
        for (int index = 0; index < path.length(); index++) {
            if ((path.charAt(index) == '.' || path.charAt(index) == '[')
                    && classified.contains(path.substring(0, index))) { return true; }
        }
        return false;
    }

    private static void validateDistinctPropertySlots(List<DdcConfigBaselineVO.RuleVO> rules) {
        var slots = new HashSet<ConfigurationPropertyName>();
        for (var rule : rules) {
            var key = ConfigurationPropertyName.adapt(rule.getKey().replace('_', '.'), '.');
            if (key.isEmpty() || !slots.add(key)) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_ALIAS_AMBIGUOUS");
            }
        }
        for (var key : slots) {
            for (var parent = key.getParent(); !parent.isEmpty(); parent = parent.getParent()) {
                if (slots.contains(parent)) {
                    throw new IllegalArgumentException("TIANSHU_CONFIG_RULES_ALIAS_AMBIGUOUS");
                }
            }
        }
    }

    private static String valueType(JsonNode value) {
        if (value.isNull() || value.isTextual()) { return "STRING"; }
        if (value.isBoolean()) { return "BOOLEAN"; }
        if (value.isIntegralNumber()) { return "INTEGER"; }
        if (value.isNumber()) { return "NUMBER"; }
        if (value.isObject()) { return "OBJECT"; }
        if (value.isArray()) { return "ARRAY"; }
        throw new IllegalArgumentException("TIANSHU_CONFIG_RULE_VALUE_INVALID");
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

    public record ConfigProjectionVO(List<DdcConfigBaselineVO.RuleVO> rules,
            Map<String, JsonNode> restartValues, Map<String, JsonNode> hotValues) {
        public ConfigProjectionVO {
            rules = List.copyOf(rules);
            restartValues = copyValues(restartValues);
            hotValues = copyValues(hotValues);
        }
        @Override public Map<String, JsonNode> restartValues() { return copyValues(restartValues); }
        @Override public Map<String, JsonNode> hotValues() { return copyValues(hotValues); }
        private static Map<String, JsonNode> copyValues(Map<String, JsonNode> source) {
            Map<String, JsonNode> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(key, value.deepCopy()));
            return java.util.Collections.unmodifiableMap(copy);
        }
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
