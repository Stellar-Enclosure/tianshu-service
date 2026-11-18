package top.egon.cola.component.tianshu.configdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.environment.DdcDynamicPropertySource;
import top.egon.cola.component.tianshu.environment.DdcReservedConfigurationKeys;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore;

/** Validates one offline baseline and initializes the sole complete HOT source plus immutable RESTART values. */
@Slf4j
public final class DdcConfigDataLoader implements ConfigDataLoader<DdcConfigDataResource> {

    public static final String HOT_PROPERTY_SOURCE_NAME = "tianshu:application.yml";
    public static final String RESTART_PROPERTY_SOURCE_NAME = "tianshu:baseline-restart";
    public static final String METADATA_PROPERTY_SOURCE_NAME = "tianshu:baseline-metadata";
    private static final int MAX_BASELINE_BYTES = 1024 * 1024;
    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

    @Override
    public ConfigData load(ConfigDataLoaderContext context, DdcConfigDataResource resource) {
        byte[] bytes = null;
        try {
            bytes = read(resource.getBaselineFile());
            JsonNode parsed = DdcConfigCanonicalJson.parse(bytes);
            DdcConfigBaselineVO baseline = MAPPER.treeToValue(parsed, DdcConfigBaselineVO.class);
            if (!sameScope(resource.getExpectedScope(), baseline.getScope())) {
                throw new IllegalStateException("CONFIG_BASELINE_SCOPE_MISMATCH");
            }
            var target = baseline.getTargetBindings().stream().filter(binding -> matches(resource.getExpectedTarget(), binding))
                    .findFirst().orElseThrow(() -> new IllegalStateException("CONFIG_BASELINE_TARGET_MISMATCH"));
            if (!DdcConfigCanonicalJson.restartDigest(baseline).equals(baseline.getRestartDigest())
                    || !DdcConfigCanonicalJson.contentSha256(baseline).equals(baseline.getContentSha256())) {
                throw new IllegalStateException("CONFIG_BASELINE_DIGEST_MISMATCH");
            }
            validateRules(baseline);
            long revision = Long.parseLong(baseline.getSource().getRevision());
            var restart = new MapPropertySource(RESTART_PROPERTY_SOURCE_NAME, DdcDynamicPropertySource.Snapshot
                    .fromTyped("application.yml", revision, baseline.getRestartDigest(), baseline.getRestartValues()).rawValues());
            var selected = recover(resource, baseline);
            var hot = new DdcDynamicPropertySource(HOT_PROPERTY_SOURCE_NAME, selected.snapshot());
            DdcReservedConfigurationKeys.validate(restart);
            DdcReservedConfigurationKeys.validate(hot);
            var metadata = new DdcConfigDataResource.BaselineMetadataVO(baseline.getScope(), baseline.getSource(), selected.enabled(),
                    selected.snapshot().checksum(), baseline.getRestartDigest(), baseline.getRules(), target,
                    selected.dependencySnapshotId(), selected.recovery());
            return new ConfigData(List.of(hot, restart, new MetadataPropertySource(metadata)),
                    ConfigData.Option.IGNORE_IMPORTS, ConfigData.Option.IGNORE_PROFILES);
        } catch (JsonProcessingException invalid) {
            // Invalid wire shapes are distinct from an unavailable mounted file; omit source-bearing causes.
            throw new IllegalStateException("CONFIG_BASELINE_INVALID");
        } catch (IOException invalid) {
            throw new IllegalStateException("CONFIG_BASELINE_UNAVAILABLE");
        } catch (IllegalArgumentException invalid) {
            // JSON parsing errors may retain complete source text. Never expose that material through causes.
            throw new IllegalStateException("CONFIG_BASELINE_INVALID");
        } finally {
            if (bytes != null) { Arrays.fill(bytes, (byte) 0); }
        }
    }

    private static RecoverySelectionVO recover(DdcConfigDataResource resource, DdcConfigBaselineVO baseline) {
        var settings = resource.getRecoverySettings();
        var initial = DdcDynamicPropertySource.Snapshot.fromTyped(baseline.getScope().getResourceName(),
                Long.parseLong(baseline.getSource().getRevision()), baseline.getContentSha256(), baseline.getHotSeed());
        var facts = DdcConfigDataResource.RecoveryVO.none(settings.baselineValidUntil());
        if (!settings.enabled()) { return selection(initial, baseline, facts); }
        var expected = resource.getExpectedTarget();
        var key = new DdcConfigLkgStore.ScopeKeyVO(baseline.getScope(), baseline.getSource().getConfigId(), expected.bindingId(),
                expected.clusterId(), expected.kubernetesNamespace(), expected.workloadUid(), expected.containerName());
        DdcConfigLkgStore.StateVO stored;
        try {
            var state = new DdcConfigLkgStore(settings.stateDirectory()).read(key);
            if (state.isEmpty()) { return selection(initial, baseline, facts); }
            stored = state.orElseThrow();
        } catch (DdcConfigLkgStore.LkgStoreException invalid) {
            return selection(initial, baseline, new DdcConfigDataResource.RecoveryVO(false, false, true, null,
                    settings.baselineValidUntil(), null));
        }
        String disabledRevision = stored.disabledRevision();
        facts = new DdcConfigDataResource.RecoveryVO(false, stored.isolated(), false, null, settings.baselineValidUntil(), disabledRevision);
        // Revocation and a later disabled revision remain authoritative even when an applied snapshot has expired.
        if (stored.isolated() || forbiddenByFloor(initial.version(), baseline.getEnabled(), disabledRevision)) {
            initial = DdcDynamicPropertySource.Snapshot.fromTyped(initial.resourceName(), 0L, initial.checksum(), Map.of());
        }
        if (stored.isolated()) { return new RecoverySelectionVO(initial, false, baseline.getDependencySnapshotId(), facts); }
        if (stored.applied() == null) {
            return new RecoverySelectionVO(initial, initial.version() == 0 ? false : baseline.getEnabled(), baseline.getDependencySnapshotId(), facts);
        }
        var applied = stored.applied();
        try {
            if (stored.bindingRevision() != expected.bindingRevision() || !settings.podUid().equals(stored.podUid())
                    || !stored.restartDigest().equals(baseline.getRestartDigest()) || !sameScope(baseline.getScope(), applied.getScope())
                    || !applied.getRestartDigest().equals(baseline.getRestartDigest())
                    || !applied.getContentSha256().equals(DdcConfigCanonicalJson.contentSha256(applied))) {
                throw new IllegalArgumentException("CONFIG_RECOVERY_INCOMPATIBLE");
            }
            Map<String, DdcConfigBaselineVO.RuleVO> rules = new LinkedHashMap<>();
            baseline.getRules().forEach(rule -> rules.put(rule.getKey(), rule));
            validateValues(applied.getHotValues(), DdcConfigBaselineVO.ReloadModeEnum.HOT, rules);
            if (applied.getEnabled()) {
                baseline.getHotSeed().keySet().forEach(name -> {
                    if (!applied.getHotValues().containsKey(name) && !rules.get(name).getDeletionAllowed()) {
                        throw new IllegalArgumentException("CONFIG_RECOVERY_DELETION_FORBIDDEN");
                    }
                });
            }
            long revision = Long.parseLong(applied.getReleaseRevision());
            var candidate = DdcDynamicPropertySource.Snapshot.fromTyped(initial.resourceName(), revision,
                    applied.getContentSha256(), applied.getHotValues());
            Instant now = Instant.now();
            if (!settings.allowOfflineStart() || revision < Long.parseLong(baseline.getSource().getRevision())
                    || forbiddenByFloor(revision, applied.getEnabled(), disabledRevision)
                    || stored.lastVerifiedAt() == null || stored.lastVerifiedAt().isAfter(now)
                    || !applied.getValidUntil().isAfter(now) || !stored.lastVerifiedAt().plus(settings.maxStale()).isAfter(now)) {
                return new RecoverySelectionVO(initial, initial.version() == 0 ? false : baseline.getEnabled(), baseline.getDependencySnapshotId(), facts);
            }
            facts = new DdcConfigDataResource.RecoveryVO(true, false, false, stored.lastVerifiedAt(), applied.getValidUntil(), disabledRevision);
            return new RecoverySelectionVO(candidate, applied.getEnabled(), applied.getDependencySnapshotId(), facts);
        } catch (RuntimeException invalid) {
            facts = new DdcConfigDataResource.RecoveryVO(false, false, true, null, settings.baselineValidUntil(), disabledRevision);
            return new RecoverySelectionVO(initial, initial.version() == 0 ? false : baseline.getEnabled(), baseline.getDependencySnapshotId(), facts);
        }
    }

    private static boolean forbiddenByFloor(long revision, boolean enabled, @Nullable String floor) {
        return floor != null && (revision < Long.parseLong(floor) || enabled && revision == Long.parseLong(floor));
    }

    private static RecoverySelectionVO selection(DdcDynamicPropertySource.Snapshot snapshot, DdcConfigBaselineVO baseline,
            DdcConfigDataResource.RecoveryVO recovery) {
        return new RecoverySelectionVO(snapshot, baseline.getEnabled(), baseline.getDependencySnapshotId(), recovery);
    }

    private record RecoverySelectionVO(DdcDynamicPropertySource.Snapshot snapshot, boolean enabled,
            @Nullable String dependencySnapshotId, DdcConfigDataResource.RecoveryVO recovery) { }

    @Nullable
    public static DdcConfigDataResource.BaselineMetadataVO baselineMetadataOrNull(ConfigurableEnvironment environment) {
        PropertySource<?> source = environment.getPropertySources().get(METADATA_PROPERTY_SOURCE_NAME);
        return source instanceof MetadataPropertySource metadata ? metadata.getSource() : null;
    }

    private static byte[] read(Path file) throws IOException {
        Path root = file.getParent().toRealPath();
        Path resolved = file.toRealPath();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CONFIG_BASELINE_PATH_OUTSIDE_MOUNT");
        }
        try (var input = Files.newInputStream(resolved, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BASELINE_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BASELINE_BYTES) {
                Arrays.fill(bytes, (byte) 0);
                throw new IllegalStateException("CONFIG_BASELINE_SIZE_INVALID");
            }
            return bytes;
        }
    }

    private static void validateRules(DdcConfigBaselineVO baseline) {
        Map<String, DdcConfigBaselineVO.RuleVO> rules = new LinkedHashMap<>();
        baseline.getRules().forEach(rule -> {
            if (DdcReservedConfigurationKeys.isReserved(rule.getKey())) {
                throw new IllegalStateException("CONFIG_BASELINE_RULE_RESERVED");
            }
            rules.put(rule.getKey(), rule);
        });
        validateValues(baseline.getRestartValues(), DdcConfigBaselineVO.ReloadModeEnum.RESTART, rules);
        validateValues(baseline.getHotSeed(), DdcConfigBaselineVO.ReloadModeEnum.HOT, rules);
    }

    private static void validateValues(Map<String, JsonNode> values, DdcConfigBaselineVO.ReloadModeEnum mode,
            Map<String, DdcConfigBaselineVO.RuleVO> rules) {
        values.forEach((key, value) -> {
            DdcConfigBaselineVO.RuleVO rule = rules.get(key);
            if (rule == null || rule.getMode() != mode || !rule.acceptsValue(value)) {
                throw new IllegalStateException("CONFIG_BASELINE_RULE_MISMATCH");
            }
        });
    }

    private static boolean matches(DdcConfigDataResource.ExpectedTargetVO expected, DdcConfigBaselineVO.TargetBindingVO actual) {
        return expected.bindingId().equals(actual.getBindingId())
                && Long.toString(expected.bindingRevision()).equals(actual.getBindingRevision())
                && expected.clusterId().equals(actual.getClusterId()) && expected.kubernetesNamespace().equals(actual.getKubernetesNamespace())
                && expected.workloadUid().equals(actual.getWorkloadUid()) && expected.containerName().equals(actual.getContainerName());
    }

    private static boolean sameScope(DdcConfigScopeVO expected, DdcConfigScopeVO actual) {
        return expected.getTenantId().equals(actual.getTenantId()) && expected.getBizCode().equals(actual.getBizCode())
                && expected.getEnv().equals(actual.getEnv()) && expected.getAppCode().equals(actual.getAppCode())
                && expected.getResourceName().equals(actual.getResourceName());
    }

    private static final class MetadataPropertySource extends PropertySource<DdcConfigDataResource.BaselineMetadataVO> {
        private MetadataPropertySource(DdcConfigDataResource.BaselineMetadataVO metadata) {
            super(METADATA_PROPERTY_SOURCE_NAME, metadata);
        }

        @Override
        @Nullable
        public Object getProperty(String name) { return null; }
    }
}
