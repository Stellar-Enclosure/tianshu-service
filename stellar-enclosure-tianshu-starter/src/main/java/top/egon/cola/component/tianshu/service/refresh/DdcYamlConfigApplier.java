package top.egon.cola.component.tianshu.service.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.environment.DdcDynamicPropertySource;
import top.egon.cola.component.tianshu.format.DdcConfigFormatStrategyRegistry;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataLoader;
import top.egon.cola.component.tianshu.model.config.DdcConfigFormat;
import top.egon.cola.component.tianshu.api.refresh.DdcConfigApplier;
import top.egon.cola.component.tianshu.api.refresh.DdcConfigApplierRegistry;
import top.egon.cola.component.tianshu.service.binding.DdcFieldBindingService;
import top.egon.cola.component.tianshu.model.config.DdcConfigurationChangedEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.Instant;
import java.time.Clock;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 将动态下发的 YAML 配置事务式应用到 Spring 环境和运行时消费者。
 * Transactionally applies dynamically delivered YAML configuration to the Spring environment and runtime consumers.
 *
 * <p>本类先替换动态属性源，再刷新配置属性 Bean 和显式叶子应用器；任一步骤失败时恢复旧属性源并按逆序
 * 回滚已执行的叶子应用器。事件监听器失败只记录日志，不回滚已成功应用的配置。</p>
 *
 * <p>The dynamic property source is replaced before configuration-properties Beans and explicit leaf appliers are
 * refreshed. A failure restores the old source and rolls back completed leaf appliers in reverse order. Event-listener
 * failures are logged without rolling back successfully applied configuration.</p>
 */
@Slf4j
public class DdcYamlConfigApplier implements SmartInitializingSingleton {

    /**
     * 承载动态属性源并解析最终属性值的 Spring 环境。 Spring environment hosting the dynamic source and resolving final values.
     */
    private final ConfigurableEnvironment environment;

    /**
     * 解析显式配置键消费者的应用器注册表。 Registry resolving explicit configuration-key consumers.
     */
    private final DdcConfigApplierRegistry applierRegistry;

    /**
     * 管理字段级动态绑定的服务。 Service managing field-level dynamic bindings.
     */
    private final DdcFieldBindingService fieldBindingService;

    /**
     * 刷新配置属性 Bean 的重新绑定器。 Rebinder that refreshes configuration-properties Beans.
     */
    private final DdcConfigurationPropertiesRebinder rebinder;

    /**
     * 发布配置变化事件的 Spring 发布器。 Spring publisher for configuration-change events.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 允许的 YAML UTF-8 字节数上限。 Maximum allowed YAML size in UTF-8 bytes.
     */
    private final long maxConfigBytes;

    /**
     * 选择 YAML 解析实现的配置格式策略注册表。
     * Configuration-format strategy registry that selects the YAML parser implementation.
     */
    private final DdcConfigFormatStrategyRegistry formatStrategies;

    /**
     * 当前环境中的可替换 Tianshu 动态属性源。 Replaceable Tianshu dynamic property source in the current environment.
     */
    private final DdcDynamicPropertySource propertySource;
    private final Clock clock;
    private boolean applyingTypedSnapshot;
    private boolean rollbackIncomplete;

    /**
     * 创建 YAML 配置应用器并定位已由 ConfigData 安装的动态属性源。
     * Creates the YAML configuration applier and locates the dynamic source installed by ConfigData.
     *
     * @param environment         可配置 Spring 环境; configurable Spring environment
     * @param applierRegistry     配置应用器注册表; configuration applier registry
     * @param fieldBindingService 字段绑定服务; field binding service
     * @param rebinder            配置属性重新绑定器; configuration-properties rebinder
     * @param eventPublisher      应用事件发布器; application event publisher
     * @param maxConfigBytes        YAML UTF-8 字节数上限; maximum YAML size in UTF-8 bytes
     * @param formatStrategies    配置格式策略注册表; configuration-format strategy registry
     * @throws IllegalStateException 环境中缺少 Tianshu ConfigData 属性源时抛出; thrown when the Tianshu ConfigData source is absent
     */
    public DdcYamlConfigApplier(
            ConfigurableEnvironment environment,
            DdcConfigApplierRegistry applierRegistry,
            DdcFieldBindingService fieldBindingService,
            DdcConfigurationPropertiesRebinder rebinder,
            ApplicationEventPublisher eventPublisher,
            long maxConfigBytes,
            DdcConfigFormatStrategyRegistry formatStrategies) {
        this(environment, applierRegistry, fieldBindingService, rebinder, eventPublisher, maxConfigBytes, formatStrategies, Clock.systemUTC());
    }

    public DdcYamlConfigApplier(ConfigurableEnvironment environment, DdcConfigApplierRegistry applierRegistry,
            DdcFieldBindingService fieldBindingService, DdcConfigurationPropertiesRebinder rebinder,
            ApplicationEventPublisher eventPublisher, long maxConfigBytes,
            DdcConfigFormatStrategyRegistry formatStrategies, Clock clock) {
        this.environment = environment;
        this.applierRegistry = applierRegistry;
        this.fieldBindingService = fieldBindingService;
        this.rebinder = rebinder;
        this.eventPublisher = eventPublisher;
        this.maxConfigBytes = maxConfigBytes;
        this.formatStrategies = Objects.requireNonNull(
                formatStrategies,
                "formatStrategies"
        );
        this.propertySource = findPropertySource(environment);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 返回当前动态配置快照。
     * Returns the current dynamic configuration snapshot.
     *
     * @return 当前不可变快照; current immutable snapshot
     */
    public DdcDynamicPropertySource.Snapshot currentSnapshot() {
        return propertySource.snapshot();
    }

    /**
     * 在单例初始化完成后，将启动期加载的值按优先级应用到显式注册的运行时消费者。
     * Applies startup-loaded values to explicitly registered runtime consumers in priority order after singleton initialization.
     */
    @Override
    public synchronized void afterSingletonsInstantiated() {
        var metadata = DdcConfigDataLoader.baselineMetadataOrNull(environment);
        if (metadata != null) {
            assertWritable();
            applyingTypedSnapshot = true;
            try {
            rebinder.afterSingletonsInstantiated();
            var current = propertySource.snapshot();
            var empty = DdcDynamicPropertySource.Snapshot.fromTyped(current.resourceName(), current.version(), current.checksum(), Map.of());
            validateTypedRules(metadata.rules(), current, empty, true);
            var candidateEnvironment = preview(current);
            var beans = rebinder.prepare(candidateEnvironment, current.values().keySet(), Set.of());
            var fields = fieldBindingService.prepare(candidateEnvironment, current.values().keySet());
            var leaves = prepareTypedLeaves(metadata.rules(), current, empty, preview(empty), current.values().keySet());
            commitPrepared(current, current, beans, fields, leaves);
            return;
            } finally { applyingTypedSnapshot = false; }
        }
        DdcDynamicPropertySource.Snapshot snapshot =
                propertySource.snapshot();
        snapshot.values().keySet().stream()
                .filter(applierRegistry::hasExplicitRegistration)
                .sorted(Comparator
                        .comparingInt((String key) ->
                                applierRegistry.resolve(key).priority())
                        .thenComparing(String::compareTo))
                .forEach(key -> applierRegistry.resolve(key).apply(
                        key,
                        consumerValue(key),
                        snapshot.version()
                ));
    }

    /**
     * 解析并应用一个新 YAML 版本，失败时恢复此前的动态状态。
     * Parses and applies a new YAML version, restoring the previous dynamic state on failure.
     *
     * @param content  YAML 文本内容; YAML text content
     * @param version  配置版本; configuration version
     * @param changeId 发布变化标识; publication change identifier
     * @return 描述有效变化与刷新结果的事件; event describing effective changes and refresh results
     * @throws IllegalArgumentException 内容超限或无法解析时抛出; thrown when content exceeds the limit or cannot be parsed
     * @throws RuntimeException         运行时消费者应用失败时抛出; thrown when a runtime consumer fails to apply the change
     */
    public synchronized DdcConfigurationChangedEvent apply(
            String content,
            long version,
            @Nullable String changeId) {
        assertWritable();
        validateSize(content);
        DdcDynamicPropertySource candidate = load(content, version);
        DdcDynamicPropertySource.Snapshot previous =
                propertySource.snapshot();
        Diff rawDiff = diff(previous, candidate.snapshot());
        Map<String, String> previousResolved = resolved(rawDiff.changedKeys());

        propertySource.replace(candidate.snapshot());
        Diff effectiveDiff = effectiveDiff(rawDiff, previousResolved);
        Set<String> refreshedKeys = new LinkedHashSet<>();
        List<AppliedLeaf> appliedLeaves = new ArrayList<>();
        DdcFieldBindingService.RefreshResult fieldRefresh = null;
        try {
            refreshedKeys.addAll(rebinder.rebind(
                    effectiveDiff.changedKeys(),
                    effectiveDiff.removedKeys()
            ));
            fieldRefresh = fieldBindingService.refresh();
            applyLeaves(
                    effectiveDiff.changedKeys(),
                    previousResolved,
                    version,
                    refreshedKeys,
                    appliedLeaves
            );
        } catch (RuntimeException exception) {
            rollback(
                    previous,
                    effectiveDiff,
                    fieldRefresh,
                    appliedLeaves
            );
            throw exception;
        }

        Set<String> restartRequiredKeys =
                new LinkedHashSet<>(effectiveDiff.changedKeys());
        restartRequiredKeys.removeAll(refreshedKeys);
        DdcConfigurationChangedEvent event =
                new DdcConfigurationChangedEvent(
                        propertySource.snapshot().resourceName(),
                        version,
                        candidate.snapshot().checksum(),
                        effectiveDiff.changedKeys(),
                        effectiveDiff.addedKeys(),
                        effectiveDiff.updatedKeys(),
                        effectiveDiff.removedKeys(),
                        refreshedKeys,
                        restartRequiredKeys,
                        changeId
                );
        publish(event);
        return event;
    }

    /** The full baseline overload verifies the immutable restart combination without retaining its HOT seed. */
    public DdcConfigurationChangedEvent applySnapshot(@NotNull @Valid DdcConfigBaselineVO baseline,
                                                     @NotNull @Valid DdcConfigSnapshotDTO snapshot,
                                                     @Nullable String changeId) {
        Objects.requireNonNull(baseline, "baseline");
        if (!baseline.getRestartDigest().equals(DdcConfigCanonicalJson.restartDigest(baseline))) {
            throw new IllegalArgumentException("CONFIG_BASELINE_RESTART_DIGEST_INVALID");
        }
        return applySnapshot(baseline.getScope(), baseline.getRestartDigest(), baseline.getRules(), snapshot, changeId);
    }

    /** Complete HOT replacement. The caller persists APPLIED only after this method has returned successfully. */
    public synchronized DdcConfigurationChangedEvent applySnapshot(@NotNull @Valid DdcConfigScopeVO expectedScope,
            @NotNull String baselineRestartDigest, @NotNull List<DdcConfigBaselineVO.RuleVO> rules,
            @NotNull @Valid DdcConfigSnapshotDTO snapshot, @Nullable String changeId) {
        return applySnapshot(expectedScope, baselineRestartDigest, rules, snapshot, changeId, ignored -> { });
    }

    public synchronized DdcConfigurationChangedEvent applySnapshot(@NotNull @Valid DdcConfigScopeVO expectedScope,
            @NotNull String baselineRestartDigest, @NotNull List<DdcConfigBaselineVO.RuleVO> rules,
            @NotNull @Valid DdcConfigSnapshotDTO snapshot, @Nullable String changeId,
            java.util.function.Consumer<DdcConfigurationChangedEvent> persistenceBeforePublish) {
        assertWritable();
        applyingTypedSnapshot = true;
        try {
        Objects.requireNonNull(persistenceBeforePublish, "persistenceBeforePublish");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!expectedScope.equals(snapshot.getScope()) || !"1".equals(snapshot.getSchemaVersion())) {
            throw new IllegalArgumentException("CONFIG_SNAPSHOT_SCOPE_INVALID");
        }
        if (!baselineRestartDigest.equals(snapshot.getRestartDigest())) {
            throw new IllegalStateException("CONFIG_RESTART_REQUIRED");
        }
        if (!snapshot.getContentSha256().equals(DdcConfigCanonicalJson.contentSha256(snapshot))) {
            throw new IllegalArgumentException("CONFIG_SNAPSHOT_DIGEST_INVALID");
        }
        if (!snapshot.getValidUntil().isAfter(clock.instant())) throw new IllegalArgumentException("CONFIG_SNAPSHOT_EXPIRED");
        byte[] encoded = DdcConfigCanonicalJson.canonicalBytes(json(snapshot.getHotValues()));
        if (encoded.length > maxConfigBytes) throw new IllegalArgumentException("CONFIG_SNAPSHOT_TOO_LARGE");
        long revision = Long.parseLong(snapshot.getReleaseRevision());
        var previous = propertySource.snapshot();
        if (!expectedScope.getResourceName().equals(previous.resourceName())) throw new IllegalArgumentException("CONFIG_SNAPSHOT_RESOURCE_INVALID");
        if (revision < previous.version()) throw new IllegalArgumentException("CONFIG_SNAPSHOT_STALE");
        if (revision == previous.version() && !previous.checksum().equals(snapshot.getContentSha256())) {
            throw new IllegalArgumentException("CONFIG_SNAPSHOT_REVISION_CONFLICT");
        }
        var candidate = DdcDynamicPropertySource.Snapshot.fromTyped(expectedScope.getResourceName(), revision,
                snapshot.getContentSha256(), snapshot.getHotValues());
        validateTypedRules(rules, candidate, previous, snapshot.getEnabled());
        var delta = diff(previous, candidate);
        if (revision == previous.version()) {
            var event = typedEvent(candidate, delta, Set.of(), changeId);
            persistenceBeforePublish.accept(event);
            return event;
        }
        var candidateEnvironment = preview(candidate);
        var preparedBeans = rebinder.prepare(candidateEnvironment, delta.changedKeys(), delta.removedKeys());
        var preparedFields = fieldBindingService.prepare(candidateEnvironment, delta.changedKeys());
        var leaves = new ArrayList<>(prepareTypedLeaves(rules, candidate, previous, preview(previous), delta.changedKeys()));
        var event = typedEvent(candidate, delta, delta.changedKeys(), changeId);
        leaves.add(new PreparedLeaf() {
            @Override public void commit() { persistenceBeforePublish.accept(event); }
            @Override public void rollback() { }
        });
        commitPrepared(previous, candidate, preparedBeans, preparedFields, leaves);
        publish(event);
        return event;
        } finally {
            applyingTypedSnapshot = false;
        }
    }

    private void assertWritable() {
        if (rollbackIncomplete) throw new IllegalStateException("CONFIG_HOT_ROLLBACK_FAILED");
        if (applyingTypedSnapshot) throw new IllegalStateException("CONFIG_HOT_REENTRANT_APPLY");
    }

    private DdcConfigurationChangedEvent typedEvent(DdcDynamicPropertySource.Snapshot snapshot, Diff delta,
                                                   Set<String> refreshed, String changeId) {
        return new DdcConfigurationChangedEvent(snapshot.resourceName(), snapshot.version(), snapshot.checksum(),
                delta.changedKeys(), delta.addedKeys(), delta.updatedKeys(), delta.removedKeys(), refreshed, Set.of(), changeId);
    }

    private void validateTypedRules(List<DdcConfigBaselineVO.RuleVO> rules, DdcDynamicPropertySource.Snapshot candidate,
                                    DdcDynamicPropertySource.Snapshot previous, boolean enabled) {
        var byKey = ruleMap(rules);
        var oldValues = previous.values();
        var newValues = candidate.values();
        var keys = new LinkedHashSet<>(oldValues.keySet());
        keys.addAll(newValues.keySet());
        for (var key : keys) {
            var rule = byKey.get(key);
            if (rule == null || rule.getMode() != DdcConfigBaselineVO.ReloadModeEnum.HOT) throw new IllegalStateException("CONFIG_RESTART_REQUIRED");
            if (!applierRegistry.hasExplicitRegistration(key) && !rebinder.supportsKey(key) && !fieldBindingService.supportsKey(key)) {
                throw new IllegalStateException("CONFIG_HOT_CONSUMER_UNKNOWN");
            }
            if (!newValues.containsKey(key)) {
                if (enabled && !rule.getDeletionAllowed()) throw new IllegalArgumentException("CONFIG_HOT_DELETION_FORBIDDEN");
                continue;
            }
            var value = json(newValues.get(key));
            if (!rule.acceptsValue(value)) throw new IllegalArgumentException("CONFIG_HOT_TYPE_INVALID");
            if (value.isNull()) {
                if (!rule.getNullable() || !applierRegistry.hasExplicitRegistration(key)
                        || !(applierRegistry.resolve(key) instanceof TypedLeafApplier typed) || !typed.supportsExplicitNull()) {
                    throw new IllegalStateException("CONFIG_HOT_EXPLICIT_NULL_UNSUPPORTED");
                }
                if (rebinder.consumesKey(key) || fieldBindingService.consumesKey(key)) {
                    throw new IllegalStateException("CONFIG_HOT_EXPLICIT_NULL_UNSUPPORTED");
                }
            }
            if (rule.getSchemaRef() != null && (!applierRegistry.hasExplicitRegistration(key)
                    || !(applierRegistry.resolve(key) instanceof TypedLeafApplier typed) || !typed.supportsSchema(rule.getSchemaRef()))) {
                throw new IllegalStateException("CONFIG_HOT_SCHEMA_UNSUPPORTED");
            }
        }
    }

    private static Map<String, DdcConfigBaselineVO.RuleVO> ruleMap(List<DdcConfigBaselineVO.RuleVO> rules) {
        Map<String, DdcConfigBaselineVO.RuleVO> result = new LinkedHashMap<>();
        for (var rule : List.copyOf(rules)) if (result.putIfAbsent(rule.getKey(), rule) != null) {
            throw new IllegalArgumentException("CONFIG_RULE_KEY_DUPLICATE");
        }
        return result;
    }

    private List<PreparedLeaf> prepareTypedLeaves(List<DdcConfigBaselineVO.RuleVO> rules,
            DdcDynamicPropertySource.Snapshot candidate, DdcDynamicPropertySource.Snapshot previous,
            ConfigurableEnvironment previousEnvironment, Set<String> changedKeys) {
        var byKey = ruleMap(rules);
        var candidateEnvironment = preview(candidate);
        var nextValues = candidate.values();
        var oldValues = previous.values();
        List<PreparedLeaf> prepared = new ArrayList<>();
        changedKeys.stream().filter(applierRegistry::hasExplicitRegistration)
                .sorted(Comparator.comparingInt((String key) -> applierRegistry.resolve(key).priority()).thenComparing(String::compareTo))
                .forEach(key -> {
                    var applier = applierRegistry.resolve(key);
                    var next = leafValue(nextValues, candidateEnvironment, key);
                    var old = leafValue(oldValues, previousEnvironment, key);
                    if (applier instanceof TypedLeafApplier typed) {
                        prepared.add(Objects.requireNonNull(typed.prepare(key, next, old, byKey.get(key), candidate.version()), "preparedLeaf"));
                    } else {
                        if (next.present() && (next.value().isNull() || next.value().isContainerNode())) {
                            throw new IllegalStateException("CONFIG_HOT_TYPED_LEAF_REQUIRED");
                        }
                        prepared.add(new PreparedLeaf() {
                            @Override public void commit() { applier.apply(key, next.resolvedValue(), candidate.version()); }
                            @Override public void rollback() { applier.apply(key, old.resolvedValue(), previous.version()); }
                        });
                    }
                });
        return List.copyOf(prepared);
    }

    private static ValueVO leafValue(Map<String, Object> values, ConfigurableEnvironment environment, String key) {
        if (values.containsKey(key)) {
            var value = json(values.get(key));
            String resolved = value.isNull() ? null : value.isTextual() ? value.textValue()
                    : new String(DdcConfigCanonicalJson.canonicalBytes(value), StandardCharsets.UTF_8);
            return new ValueVO(true, value, resolved);
        }
        return new ValueVO(false, null, environment.getProperty(key));
    }

    private void commitPrepared(DdcDynamicPropertySource.Snapshot previous, DdcDynamicPropertySource.Snapshot candidate,
            @Nullable DdcConfigurationPropertiesRebinder.PreparedRebind beans,
            @Nullable DdcFieldBindingService.PreparedRefresh fields, List<PreparedLeaf> leaves) {
        int attempted = 0;
        propertySource.replace(candidate);
        try {
            if (beans != null) beans.commit();
            if (fields != null) fields.commit();
            for (var leaf : leaves) { attempted++; leaf.commit(); }
        } catch (RuntimeException failure) {
            propertySource.replace(previous);
            for (int index = attempted - 1; index >= 0; index--) {
                try { leaves.get(index).rollback(); }
                catch (RuntimeException rollbackFailure) { rollbackIncomplete = true; failure.addSuppressed(rollbackFailure); }
            }
            if (fields != null) try { fields.rollback(); }
            catch (RuntimeException rollbackFailure) { rollbackIncomplete = true; failure.addSuppressed(rollbackFailure); }
            if (beans != null) try { beans.rollback(); }
            catch (RuntimeException rollbackFailure) { rollbackIncomplete = true; failure.addSuppressed(rollbackFailure); }
            if (rollbackIncomplete) throw new IllegalStateException("CONFIG_HOT_ROLLBACK_FAILED", failure);
            throw failure;
        }
    }

    private ConfigurableEnvironment preview(DdcDynamicPropertySource.Snapshot snapshot) {
        var result = new StandardEnvironment();
        for (var source : result.getPropertySources()) result.getPropertySources().remove(source.getName());
        result.setConversionService(environment.getConversionService());
        result.setActiveProfiles(environment.getActiveProfiles());
        result.setDefaultProfiles(environment.getDefaultProfiles());
        for (var source : environment.getPropertySources()) {
            if (!"configurationProperties".equals(source.getName())) result.getPropertySources().addLast(previewSource(source, snapshot));
        }
        return result;
    }

    private PropertySource<?> previewSource(PropertySource<?> source, DdcDynamicPropertySource.Snapshot snapshot) {
        if (source == propertySource) return new DdcDynamicPropertySource(source.getName(), snapshot);
        if (source instanceof CompositePropertySource composite) {
            var copied = new CompositePropertySource(source.getName());
            for (var nested : composite.getPropertySources()) copied.addPropertySource(previewSource(nested, snapshot));
            return copied;
        }
        return source;
    }

    private static JsonNode json(Object value) {
        var factory = JsonNodeFactory.instance;
        if (value == null) return factory.nullNode();
        if (value instanceof JsonNode node) return node.deepCopy();
        if (value instanceof String text) return factory.textNode(text);
        if (value instanceof Boolean flag) return factory.booleanNode(flag);
        if (value instanceof BigDecimal number) return factory.numberNode(number);
        if (value instanceof BigInteger number) return factory.numberNode(number);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) return factory.numberNode(((Number) value).intValue());
        if (value instanceof Long number) return factory.numberNode(number);
        if (value instanceof Float || value instanceof Double) return factory.numberNode(((Number) value).doubleValue());
        if (value instanceof Map<?, ?> map) {
            var result = factory.objectNode();
            map.forEach((key, item) -> { if (!(key instanceof String text)) throw new IllegalArgumentException("CONFIG_HOT_JSON_INVALID"); result.set((String) key, json(item)); });
            return result;
        }
        if (value instanceof List<?> list) {
            var result = factory.arrayNode();
            list.forEach(item -> result.add(json(item)));
            return result;
        }
        throw new IllegalArgumentException("CONFIG_HOT_JSON_INVALID");
    }

    /** A prepared participant validates without mutation, then commits and can restore its actual prior runtime state. */
    public interface PreparedLeaf {
        void commit();
        void rollback();
    }

    public interface TypedLeafApplier extends DdcConfigApplier {
        PreparedLeaf prepare(String key, ValueVO next, ValueVO previous, DdcConfigBaselineVO.RuleVO rule, long version);
        default boolean supportsExplicitNull() { return false; }
        default boolean supportsSchema(String schemaRef) { return false; }
        @Override default void apply(String key, @Nullable String value, long version) {
            throw new IllegalStateException("CONFIG_HOT_TYPED_ENTRY_REQUIRED");
        }
    }

    public record ValueVO(boolean present, @Nullable JsonNode value, @Nullable String resolvedValue) {
        public ValueVO {
            if (present != (value != null)) throw new IllegalArgumentException("CONFIG_HOT_VALUE_PRESENCE_INVALID");
            value = value == null ? null : value.deepCopy();
        }
        @Override public JsonNode value() { return value == null ? null : value.deepCopy(); }
    }

    /**
     * 将 YAML 文本加载为候选动态属性源。
     * Loads YAML text into a candidate dynamic property source.
     *
     * @param content YAML 文本内容; YAML text content
     * @param version 配置版本; configuration version
     * @return 候选动态属性源; candidate dynamic property source
     * @throws IllegalArgumentException YAML 无法解析时抛出; thrown when the YAML cannot be parsed
     */
    private DdcDynamicPropertySource load(String content, long version) {
        try {
            DdcDynamicPropertySource.Snapshot snapshot =
                    propertySource.snapshot();
            return formatStrategies.get(snapshot.format()).load(
                    snapshot.resourceName(),
                    content,
                    version
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Tianshu YAML resource cannot be parsed",
                    exception
            );
        }
    }

    /**
     * 校验 YAML 内容的 UTF-8 字节数未超过配置上限。
     * Validates that the YAML content does not exceed the configured UTF-8 byte limit.
     *
     * @param content YAML 文本内容，可为 {@code null}; YAML text content, possibly {@code null}
     * @throws IllegalArgumentException 内容超限时抛出; thrown when the content exceeds the limit
     */
    private void validateSize(@Nullable String content) {
        int size = content == null
                ? 0
                : content.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxConfigBytes) {
            throw new IllegalArgumentException(
                    "Tianshu YAML resource exceeds the UTF-8 limit of "
                            + maxConfigBytes + " bytes"
            );
        }
    }

    /**
     * 按属性源原始值计算新增、更新和删除差异。
     * Computes added, updated, and removed differences using raw property-source values.
     *
     * @param previous  旧快照; previous snapshot
     * @param candidate 候选快照; candidate snapshot
     * @return 原始差异; raw difference
     */
    private Diff diff(DdcDynamicPropertySource.Snapshot previous,
                      DdcDynamicPropertySource.Snapshot candidate) {
        Map<String, Object> oldValues = previous.values();
        Map<String, Object> newValues = candidate.values();
        Set<String> keys = new LinkedHashSet<>(oldValues.keySet());
        keys.addAll(newValues.keySet());
        Set<String> changed = new LinkedHashSet<>();
        Set<String> added = new LinkedHashSet<>();
        Set<String> updated = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        for (String key : keys) {
            boolean oldPresent = oldValues.containsKey(key);
            boolean newPresent = newValues.containsKey(key);
            if (oldPresent && newPresent
                    && Objects.equals(oldValues.get(key), newValues.get(key))) {
                continue;
            }
            changed.add(key);
            if (!oldPresent) {
                added.add(key);
            } else if (!newPresent) {
                removed.add(key);
            } else {
                updated.add(key);
            }
        }
        return new Diff(changed, added, updated, removed);
    }

    /**
     * 移除因更高优先级属性源覆盖而未改变最终解析值的原始差异。
     * Removes raw differences whose final resolved values remain unchanged because of higher-priority property sources.
     *
     * @param rawDiff          原始属性源差异; raw property-source difference
     * @param previousResolved 变化前的最终解析值; final resolved values before replacement
     * @return 对运行时实际可见的有效差异; effective difference visible to runtime consumers
     */
    private Diff effectiveDiff(Diff rawDiff,
                               Map<String, String> previousResolved) {
        Set<String> changed = new LinkedHashSet<>();
        Set<String> added = new LinkedHashSet<>();
        Set<String> updated = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        for (String key : rawDiff.changedKeys()) {
            if (Objects.equals(
                    previousResolved.get(key),
                    consumerValue(key)
            )) {
                continue;
            }
            changed.add(key);
            if (rawDiff.addedKeys().contains(key)) {
                added.add(key);
            } else if (rawDiff.removedKeys().contains(key)) {
                removed.add(key);
            } else {
                updated.add(key);
            }
        }
        return new Diff(changed, added, updated, removed);
    }

    /**
     * 读取指定键在当前 Spring 环境中的最终解析值。
     * Reads final resolved values for the specified keys from the current Spring environment.
     *
     * @param keys 配置键集合; configuration keys
     * @return 保持键迭代顺序的解析值映射; resolved value map preserving key iteration order
     */
    private Map<String, String> resolved(Set<String> keys) {
        Map<String, String> values = new LinkedHashMap<>();
        keys.forEach(key -> values.put(
                key,
                consumerValue(key)
        ));
        return values;
    }

    /**
     * 返回运行时消费者应看到的值。显式叶子应用器接收属性源原始值，避免把 JSON、模板等领域数据中的
     * Spring 占位符语法再次解释；普通配置消费者仍读取 Spring 最终解析值。
     * Returns the value visible to a runtime consumer. Explicit leaf appliers receive the raw property-source value
     * so Spring placeholder syntax inside JSON or templates remains domain data; regular consumers still use the
     * final Spring-resolved value.
     *
     * @param key 配置键; configuration key
     * @return 消费者值，键不存在时为 {@code null}; consumer value, or {@code null} when absent
     */
    @Nullable
    private String consumerValue(String key) {
        if (!applierRegistry.hasExplicitRegistration(key)) {
            return environment.getProperty(key);
        }
        Object rawValue = propertySource.getProperty(key);
        return rawValue == null ? null : String.valueOf(rawValue);
    }

    /**
     * 按应用器优先级和配置键顺序应用具有动态消费者的叶子配置。
     * Applies leaf configuration having dynamic consumers in applier-priority and key order.
     *
     * @param changedKeys      有效变化键; effectively changed keys
     * @param previousResolved 变化前解析值; resolved values before the change
     * @param version          配置版本; configuration version
     * @param refreshedKeys    已完成运行时刷新的键集合; keys already refreshed at runtime
     * @param appliedLeaves    成功应用并用于失败回滚的记录; successful applications recorded for rollback
     */
    private void applyLeaves(Set<String> changedKeys,
                             Map<String, String> previousResolved,
                             long version,
                             Set<String> refreshedKeys,
                             List<AppliedLeaf> appliedLeaves) {
        changedKeys.stream()
                .filter(applierRegistry::hasExplicitRegistration)
                .sorted(Comparator
                        .comparingInt((String key) ->
                                applierRegistry.resolve(key).priority())
                        .thenComparing(String::compareTo))
                .forEach(key -> {
                    DdcConfigApplier applier =
                            applierRegistry.resolve(key);
                    applier.apply(
                            key,
                            consumerValue(key),
                            version
                    );
                    appliedLeaves.add(new AppliedLeaf(
                            key,
                            previousResolved.get(key),
                            applier
                    ));
                    refreshedKeys.add(key);
                });
    }

    /**
     * 恢复旧属性源，并按逆序尽力回滚叶子应用器、字段和配置属性 Bean。
     * Restores the previous source and best-effort rolls back leaf appliers, fields, and configuration-properties beans in reverse order.
     *
     * @param previous      旧动态配置快照; previous dynamic configuration snapshot
     * @param diff          需要回滚的有效差异; effective difference to roll back
     * @param fieldRefresh  已完成字段刷新，字段刷新前失败时为 {@code null}; completed field refresh, or {@code null} when field refresh failed before completion
     * @param appliedLeaves 已成功应用的叶子记录; successfully applied leaf records
     */
    private void rollback(DdcDynamicPropertySource.Snapshot previous,
                          Diff diff,
                          @Nullable DdcFieldBindingService.RefreshResult fieldRefresh,
                          List<AppliedLeaf> appliedLeaves) {
        propertySource.replace(previous);
        for (int index = appliedLeaves.size() - 1; index >= 0; index--) {
            AppliedLeaf applied = appliedLeaves.get(index);
            try {
                applied.applier().apply(
                        applied.key(),
                        applied.previousValue(),
                        previous.version()
                );
            } catch (RuntimeException rollbackFailure) {
                log.warn(
                        "Tianshu config leaf rollback failed for key={} failureType={}",
                        applied.key(),
                        rollbackFailure.getClass().getSimpleName()
                );
            }
        }
        if (fieldRefresh != null) {
            fieldBindingService.rollback(fieldRefresh);
        }
        try {
            rebinder.rebind(diff.changedKeys(), Set.of());
        } catch (RuntimeException rollbackFailure) {
            log.warn("Tianshu configuration properties rollback failed; failureType={}",
                    rollbackFailure.getClass().getSimpleName());
        }
    }

    /**
     * 发布配置变化事件，隔离并记录监听器异常。
     * Publishes a configuration-change event while isolating and logging listener failures.
     *
     * @param event 配置变化事件; configuration-change event
     */
    private void publish(DdcConfigurationChangedEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException exception) {
            log.warn(
                    "Tianshu configuration change listener failed for version={} failureType={}",
                    event.version(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * 在环境属性源树中定位规范名称的 Tianshu 动态属性源。
     * Locates the canonically named Tianshu dynamic property source in the environment property-source tree.
     *
     * @param environment 可配置 Spring 环境; configurable Spring environment
     * @return Tianshu 动态属性源; Tianshu dynamic property source
     * @throws IllegalStateException 未导入 Tianshu ConfigData 资源时抛出; thrown when the Tianshu ConfigData resource was not imported
     */
    private DdcDynamicPropertySource findPropertySource(
            ConfigurableEnvironment environment) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            DdcDynamicPropertySource found = findPropertySource(source);
            if (found != null) {
                return found;
            }
        }
        throw new IllegalStateException(
                "Tianshu ConfigData source is missing; add "
                        + "spring.config.import=tianshu:application.yml"
        );
    }

    /**
     * 递归搜索单个属性源及其组合子源。
     * Recursively searches one property source and any nested composite sources.
     *
     * @param source 待搜索的属性源; property source to search
     * @return 匹配的动态属性源，未找到时为 {@code null}; matching dynamic source, or {@code null} when absent
     */
    @Nullable
    private DdcDynamicPropertySource findPropertySource(
            PropertySource<?> source) {
        if (source instanceof DdcDynamicPropertySource dynamic) {
            return dynamic;
        }
        if (source instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested
                    : composite.getPropertySources()) {
                DdcDynamicPropertySource found = findPropertySource(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 表示一次配置快照的分类差异。
     * Categorized difference between configuration snapshots.
     *
     * @param changedKeys 所有变化键; all changed keys
     * @param addedKeys   新增键; added keys
     * @param updatedKeys 更新键; updated keys
     * @param removedKeys 删除键; removed keys
     */
    private record Diff(
            Set<String> changedKeys,
            Set<String> addedKeys,
            Set<String> updatedKeys,
            Set<String> removedKeys
    ) {
    }

    /**
     * 保存已应用叶子的回滚信息。
     * Stores rollback information for an applied leaf.
     *
     * @param key           配置键; configuration key
     * @param previousValue 变化前最终解析值; final resolved value before the change
     * @param applier       执行应用和回滚的应用器; applier used for application and rollback
     */
    private record AppliedLeaf(
            String key,
            @Nullable String previousValue,
            DdcConfigApplier applier
    ) {
    }
}
