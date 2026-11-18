package top.egon.cola.component.tianshu.service.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.annotation.DdcRefreshable;
import top.egon.cola.component.tianshu.annotation.DdcValue;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataLoader;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource;
import top.egon.cola.component.tianshu.environment.DdcDynamicPropertySource;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.format.DdcConfigFormatStrategyRegistry;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.RuleVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.ReloadModeEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.model.config.DdcConfigurationChangedEvent;
import top.egon.cola.component.tianshu.service.binding.DdcFieldBindingService;
import top.egon.cola.component.tianshu.service.binding.DdcValueBindingRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcYamlConfigApplierTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final String PLACEHOLDER = "sha256:" + "0".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("7", "trade", "prod", "orders", "application.yml");
    @TempDir Path directory;

    @Test
    void completeTypedSnapshotHasAnExplicitTransactionalEntryPoint() throws Exception {
        var method = DdcYamlConfigApplier.class.getMethod("applySnapshot", DdcConfigBaselineVO.class,
                DdcConfigSnapshotDTO.class, String.class);
        assertThat(method.getReturnType()).isEqualTo(DdcConfigurationChangedEvent.class);
    }

    @Test
    void replacesWholeHotMapAndDeletedBeanAndLeafUseNormalSpringFallback() {
        try (var h = new Harness(initial())) {
            var label = new AtomicReference<>("remote");
            h.registry.registerExact("sample.label", (key, value, version) -> label.set(value));
            var event = h.apply(snapshot(2, Map.of("sample.count", JSON.numberNode(30)), true), rules());
            assertThat(h.source.snapshot().values()).containsOnlyKeys("sample.count");
            assertThat(h.properties().getCount()).isEqualTo(30);
            assertThat(h.properties().getLabel()).isEqualTo("local");
            assertThat(label).hasValue("local");
            assertThat(event.removedKeys()).containsExactly("sample.label");
            assertThat(event.restartRequiredKeys()).isEmpty();
            assertThat(h.events).containsExactly(event);
        }
    }

    @Test
    void failedValidationGroupsAndUnknownWritableKeyLeaveEveryConsumerUntouched() {
        try (var h = new Harness(initial())) {
            var old = h.source.snapshot();
            assertThatThrownBy(() -> h.apply(snapshot(2, Map.of("sample.count", JSON.numberNode(-1),
                    "sample.label", JSON.textNode("new")), true), rules()))
                    .isInstanceOfSatisfying(jakarta.validation.ConstraintViolationException.class, failure ->
                            assertThat(failure.getConstraintViolations()).anySatisfy(violation ->
                                    assertThat(violation.getPropertyPath().toString()).isEqualTo("count")));
            assertThat(h.source.snapshot()).isSameAs(old);
            assertThat(h.properties().getCount()).isEqualTo(20);
            assertThat(h.properties().getLabel()).isEqualTo("remote");
            var unknown = new ArrayList<>(rules());
            unknown.add(rule("sample.unknown", "string", false, true));
            var values = new LinkedHashMap<>(initial());
            values.put("sample.unknown", JSON.textNode("unsafe"));
            assertThatThrownBy(() -> h.apply(snapshot(2, values, true), unknown)).hasMessage("CONFIG_HOT_CONSUMER_UNKNOWN");
            assertThat(h.source.snapshot()).isSameAs(old);
            assertThat(h.events).isEmpty();
        }
    }

    @Test
    void persistenceFailureRollsBackBeansFieldsLeavesAndSourceWithoutPublishingSuccess() {
        var values = new LinkedHashMap<>(initial());
        values.put("leaf.value", JSON.textNode("old"));
        try (var h = new Harness(values)) {
            var leaf = new AtomicReference<>("old");
            h.registry.registerExact("leaf.value", (key, value, version) -> leaf.set(value));
            var field = new CountField();
            field.count = 20;
            h.fields.bind("countField", field, CountField.class);
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("leaf.value", "string", false, true));
            var nextValues = new LinkedHashMap<>(values);
            nextValues.put("sample.count", JSON.numberNode(30));
            nextValues.put("sample.label", JSON.textNode("new"));
            nextValues.put("leaf.value", JSON.textNode("new"));
            var next = snapshot(2, nextValues, true);
            var old = h.source.snapshot();
            assertThatThrownBy(() -> h.applier.applySnapshot(SCOPE, DIGEST, allRules, next, "r2", event -> {
                assertThat(h.properties().getCount()).isEqualTo(30);
                assertThat(field.count).isEqualTo(30);
                assertThat(leaf).hasValue("new");
                throw new IllegalStateException("disk unavailable");
            })).hasMessage("disk unavailable");
            assertThat(h.source.snapshot()).isSameAs(old);
            assertThat(h.properties().getCount()).isEqualTo(20);
            assertThat(h.properties().getLabel()).isEqualTo("remote");
            assertThat(field.count).isEqualTo(20);
            assertThat(leaf).hasValue("old");
            assertThat(h.events).isEmpty();
            h.apply(next, allRules);
            assertThat(h.properties().getCount()).isEqualTo(30);
        }
    }

    @Test
    void partiallyMutatingLeafAndAllEarlierLeavesRollBackInReverseOrder() {
        var values = new LinkedHashMap<>(initial());
        values.put("leaf.alpha", JSON.textNode("old"));
        values.put("leaf.beta", JSON.textNode("old"));
        try (var h = new Harness(values)) {
            var calls = new ArrayList<String>();
            h.registry.registerExact("leaf.alpha", (key, value, version) -> calls.add("alpha:" + value));
            h.registry.registerExact("leaf.beta", (key, value, version) -> {
                calls.add("beta:" + value);
                if ("new".equals(value)) throw new IllegalStateException("consumer failed");
            });
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("leaf.alpha", "string", false, true));
            allRules.add(rule("leaf.beta", "string", false, true));
            values.put("leaf.alpha", JSON.textNode("new"));
            values.put("leaf.beta", JSON.textNode("new"));
            assertThatThrownBy(() -> h.apply(snapshot(2, values, true), allRules)).hasMessage("consumer failed");
            assertThat(calls).containsExactly("alpha:new", "beta:new", "beta:old", "alpha:old");
            assertThat(h.source.snapshot().version()).isEqualTo(1);
            assertThat(h.events).isEmpty();
        }
    }

    @Test
    void exactSnapshotReconfirmationPersistsAgainWithoutRepeatingConsumersOrEvent() {
        try (var h = new Harness(initial())) {
            var calls = new AtomicInteger();
            h.registry.registerExact("sample.label", (key, value, version) -> calls.incrementAndGet());
            var nextValues = new LinkedHashMap<>(initial());
            nextValues.put("sample.label", JSON.textNode("new"));
            var next = snapshot(2, nextValues, true);
            h.apply(next, rules());
            var persisted = new AtomicInteger();
            h.applier.applySnapshot(SCOPE, DIGEST, rules(), next, null, event -> persisted.incrementAndGet());
            assertThat(calls).hasValue(1);
            assertThat(persisted).hasValue(1);
            assertThat(h.events).hasSize(1);
            assertThatThrownBy(() -> h.apply(snapshot(1, initial(), true), rules())).hasMessage("CONFIG_SNAPSHOT_STALE");
            assertThatThrownBy(() -> h.apply(snapshot(2, initial(), true), rules())).hasMessage("CONFIG_SNAPSHOT_REVISION_CONFLICT");
        }
    }

    @Test
    void nullMissingAndEmptyRemainDistinctForAnExplicitTypedLeaf() {
        try (var h = new Harness(initial())) {
            var runtime = new AtomicReference<DdcYamlConfigApplier.ValueVO>();
            h.registry.registerExact("leaf.value", typed(runtime, true));
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("leaf.value", "string", true, true));
            var values = new LinkedHashMap<>(initial());
            values.put("leaf.value", JSON.nullNode());
            h.apply(snapshot(2, values, true), allRules);
            assertThat(runtime.get().present()).isTrue();
            assertThat(runtime.get().value().isNull()).isTrue();
            assertThat(h.source.snapshot().rawValues()).containsEntry("leaf.value", null);
            values.put("leaf.value", JSON.textNode(""));
            h.apply(snapshot(3, values, true), allRules);
            assertThat(runtime.get().present()).isTrue();
            assertThat(runtime.get().value().textValue()).isEmpty();
            values.remove("leaf.value");
            h.apply(snapshot(4, values, true), allRules);
            assertThat(runtime.get().present()).isFalse();
            assertThat(runtime.get().value()).isNull();
            assertThat(runtime.get().resolvedValue()).isEqualTo("fallback");
        }
    }

    @Test
    void nullCannotLeakThroughOrdinarySpringConsumerEvenWithTypedLeafApproval() {
        try (var h = new Harness(initial())) {
            h.registry.registerExact("sample.label", typed(new AtomicReference<>(), true));
            var values = new LinkedHashMap<>(initial());
            values.put("sample.label", JSON.nullNode());
            assertThatThrownBy(() -> h.apply(snapshot(2, values, true), List.of(rules().get(0), rule("sample.label", "string", true, true))))
                    .hasMessage("CONFIG_HOT_EXPLICIT_NULL_UNSUPPORTED");
            assertThat(h.properties().getLabel()).isEqualTo("remote");
            assertThat(h.source.snapshot().version()).isEqualTo(1);
        }
    }

    @Test
    void unknownNullSchemaAndWrongJsonTypeFailBeforeSourceReplacement() {
        try (var h = new Harness(initial())) {
            var values = new LinkedHashMap<>(initial());
            values.put("sample.count", JSON.textNode("30"));
            var wrongType = snapshot(2, values, true);
            assertThatThrownBy(() -> h.apply(wrongType, rules())).hasMessage("CONFIG_HOT_TYPE_INVALID");
            h.registry.registerExact("leaf.value", (key, value, version) -> { });
            values = new LinkedHashMap<>(initial());
            values.put("leaf.value", JSON.nullNode());
            var candidate = snapshot(2, values, true);
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("leaf.value", "string", true, true));
            assertThatThrownBy(() -> h.apply(candidate, allRules)).hasMessage("CONFIG_HOT_EXPLICIT_NULL_UNSUPPORTED");
            values.put("leaf.value", JSON.textNode("next"));
            var schemaCandidate = snapshot(2, values, true);
            allRules.set(2, new RuleVO("leaf.value", ReloadModeEnum.HOT, "STRING", "schema-v1", false, true));
            assertThatThrownBy(() -> h.apply(schemaCandidate, allRules)).hasMessage("CONFIG_HOT_SCHEMA_UNSUPPORTED");
            assertThat(h.source.snapshot().version()).isEqualTo(1);
        }
    }

    @Test
    void restartDigestScopeAndDigestMismatchNeverApplyPartialHotChanges() {
        try (var h = new Harness(initial())) {
            var next = snapshot(2, initial(), true);
            assertThatThrownBy(() -> h.applier.applySnapshot(SCOPE, PLACEHOLDER, rules(), next, null)).hasMessage("CONFIG_RESTART_REQUIRED");
            var otherScope = new DdcConfigScopeVO("8", "trade", "prod", "orders", "application.yml");
            assertThatThrownBy(() -> h.applier.applySnapshot(otherScope, DIGEST, rules(), next, null)).hasMessage("CONFIG_SNAPSHOT_SCOPE_INVALID");
            var wrongDigest = new DdcConfigSnapshotDTO("1", SCOPE, "2", true, DIGEST, PLACEHOLDER, initial(), null, NOW.plusSeconds(300));
            assertThatThrownBy(() -> h.apply(wrongDigest, rules())).hasMessage("CONFIG_SNAPSHOT_DIGEST_INVALID");
            var restartRules = new ArrayList<>(rules());
            restartRules.set(0, new RuleVO("sample.count", ReloadModeEnum.RESTART, "INTEGER", null, false, true));
            assertThatThrownBy(() -> h.apply(next, restartRules)).hasMessage("CONFIG_RESTART_REQUIRED");
            assertThat(h.source.snapshot().version()).isEqualTo(1);
            assertThat(h.events).isEmpty();
        }
    }

    @Test
    void disabledSnapshotRemovesAllHotOverridesAndCannotBeResurrectedByOlderRevision() {
        try (var h = new Harness(initial())) {
            h.apply(snapshot(2, Map.of(), false), rules());
            assertThat(h.source.snapshot().values()).isEmpty();
            assertThat(h.properties().getCount()).isEqualTo(10);
            assertThat(h.properties().getLabel()).isEqualTo("local");
            assertThatThrownBy(() -> h.apply(snapshot(1, initial(), true), rules())).hasMessage("CONFIG_SNAPSHOT_STALE");
        }
    }

    @Test
    void fieldConversionAndConstraintValidationArePreparedBeforeAnyLiveWrite() {
        try (var h = new Harness(initial())) {
            var field = new LimitField();
            h.fields.bind("limitField", field, LimitField.class);
            var values = new LinkedHashMap<>(initial());
            values.put("field.limit", JSON.numberNode(0));
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("field.limit", "integer", false, true));
            assertThatThrownBy(() -> h.apply(snapshot(2, values, true), allRules)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
            assertThat(field.limit).isEqualTo(10);
            assertThat(h.source.snapshot().version()).isEqualTo(1);
            assertThat(h.events).isEmpty();
        }
    }

    @Test
    void prepareCannotReenterAndEventListenerFailureDoesNotUndoCommittedState() {
        try (var h = new Harness(initial())) {
            var values = new LinkedHashMap<>(initial());
            values.put("leaf.value", JSON.textNode("new"));
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("leaf.value", "string", false, true));
            var next = snapshot(2, values, true);
            h.registry.registerExact("leaf.value", new DdcYamlConfigApplier.TypedLeafApplier() {
                @Override public DdcYamlConfigApplier.PreparedLeaf prepare(String key, DdcYamlConfigApplier.ValueVO nextValue,
                        DdcYamlConfigApplier.ValueVO previous, RuleVO rule, long revision) {
                    assertThatThrownBy(() -> h.apply(next, allRules)).hasMessage("CONFIG_HOT_REENTRANT_APPLY");
                    return new DdcYamlConfigApplier.PreparedLeaf() {
                        @Override public void commit() { }
                        @Override public void rollback() { }
                    };
                }
            });
            h.failListener = true;
            h.apply(next, allRules);
            assertThat(h.source.snapshot().version()).isEqualTo(2);
            assertThat(h.events).hasSize(1);
        }
    }

    @Test
    void actualVerifiedLocalBaselineDispatchesTypedNullSeedAndBlocksStartupReentry() throws Exception {
        try (var h = new Harness(initial())) {
            var seed = new LinkedHashMap<>(initial());
            seed.put("leaf.value", JSON.nullNode());
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("leaf.value", "string", true, true));
            installBaseline(h, seed, allRules);
            var applied = new AtomicReference<DdcYamlConfigApplier.ValueVO>();
            h.registry.registerExact("leaf.value", new DdcYamlConfigApplier.TypedLeafApplier() {
                @Override public boolean supportsExplicitNull() { return true; }
                @Override public DdcYamlConfigApplier.PreparedLeaf prepare(String key, DdcYamlConfigApplier.ValueVO next,
                        DdcYamlConfigApplier.ValueVO previous, RuleVO rule, long version) {
                    assertThatThrownBy(() -> h.apply(snapshot(2, initial(), true), allRules)).hasMessage("CONFIG_HOT_REENTRANT_APPLY");
                    return new DdcYamlConfigApplier.PreparedLeaf() {
                        @Override public void commit() { applied.set(next); }
                        @Override public void rollback() { applied.set(previous); }
                    };
                }
            });
            h.applier.afterSingletonsInstantiated();
            assertThat(applied.get().present()).isTrue();
            assertThat(applied.get().value().isNull()).isTrue();
            assertThat(h.events).isEmpty();
        }
    }

    @Test
    void actualLocalBaselineValidatesDdcValueConstraintsBeforeItCanBeConfirmed() throws Exception {
        try (var h = new Harness(initial())) {
            var seed = new LinkedHashMap<>(initial());
            seed.put("field.limit", JSON.numberNode(0));
            var allRules = new ArrayList<>(rules());
            allRules.add(rule("field.limit", "integer", false, true));
            installBaseline(h, seed, allRules);
            var field = new LimitField();
            h.fields.bind("limitField", field, LimitField.class);
            assertThatThrownBy(h.applier::afterSingletonsInstantiated).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
            assertThat(field.limit).isEqualTo(10);
            assertThat(h.events).isEmpty();
        }
    }

    private void installBaseline(Harness h, Map<String, JsonNode> seed, List<RuleVO> rules) throws Exception {
        var target = new DdcConfigBaselineVO.TargetBindingVO("target-1", "1", "cluster-1", "apps-prod", "Deployment", "orders", "deployment-uid", "app");
        var base = baseline(seed, rules, target, PLACEHOLDER, PLACEHOLDER);
        String restart = DdcConfigCanonicalJson.restartDigest(base);
        var prepared = baseline(seed, rules, target, restart, PLACEHOLDER);
        var verified = baseline(seed, rules, target, restart, DdcConfigCanonicalJson.contentSha256(prepared));
        var file = Files.createTempDirectory(directory, "baseline-").resolve("baseline.json");
        Files.write(file, org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build().writeValueAsBytes(verified));
        var loaded = new DdcConfigDataLoader().load(null, new DdcConfigDataResource(file, SCOPE,
                new DdcConfigDataResource.ExpectedTargetVO("target-1", 1, "cluster-1", "apps-prod", "deployment-uid", "app")));
        for (var source : loaded.getPropertySources()) {
            if (source instanceof DdcDynamicPropertySource dynamic) h.source.replace(dynamic.snapshot());
            else h.context.getEnvironment().getPropertySources().addFirst(source);
        }
    }

    private static DdcConfigBaselineVO baseline(Map<String, JsonNode> seed, List<RuleVO> rules,
            DdcConfigBaselineVO.TargetBindingVO target, String restart, String digest) {
        return DdcConfigBaselineVO.builder().schemaVersion("1").scope(SCOPE).source(new DdcConfigBaselineVO.SourceVO("1", "1"))
                .enabled(true).contentSha256(digest).restartDigest(restart).rules(rules).targetBindings(List.of(target))
                .restartValues(Map.of()).hotSeed(seed).startupDependencies(Map.of()).secretReferences(Map.of()).dependencySnapshotId(null).build();
    }

    private static DdcYamlConfigApplier.TypedLeafApplier typed(AtomicReference<DdcYamlConfigApplier.ValueVO> runtime, boolean nullable) {
        return new DdcYamlConfigApplier.TypedLeafApplier() {
            @Override public boolean supportsExplicitNull() { return nullable; }
            @Override public DdcYamlConfigApplier.PreparedLeaf prepare(String key, DdcYamlConfigApplier.ValueVO next,
                    DdcYamlConfigApplier.ValueVO previous, RuleVO rule, long version) {
                var old = runtime.get();
                return new DdcYamlConfigApplier.PreparedLeaf() {
                    @Override public void commit() { runtime.set(next); }
                    @Override public void rollback() { runtime.set(old); }
                };
            }
        };
    }

    private static Map<String, JsonNode> initial() { return Map.of("sample.count", JSON.numberNode(20), "sample.label", JSON.textNode("remote")); }
    private static RuleVO rule(String key, String type, boolean nullable, boolean deletionAllowed) {
        return new RuleVO(key, ReloadModeEnum.HOT, type.toUpperCase(java.util.Locale.ROOT), null, nullable, deletionAllowed);
    }
    private static List<RuleVO> rules() { return List.of(rule("sample.count", "integer", false, true), rule("sample.label", "string", false, true)); }
    private static DdcConfigSnapshotDTO snapshot(long revision, Map<String, JsonNode> values, boolean enabled) {
        var provisional = new DdcConfigSnapshotDTO("1", SCOPE, Long.toString(revision), enabled, DIGEST, PLACEHOLDER, values, null, NOW.plusSeconds(300));
        return new DdcConfigSnapshotDTO("1", SCOPE, Long.toString(revision), enabled, DIGEST,
                DdcConfigCanonicalJson.contentSha256(provisional), values, null, NOW.plusSeconds(300));
    }

    private static final class Harness implements AutoCloseable {
        private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        private final DdcDynamicPropertySource source;
        private final DefaultDdcConfigApplierRegistry registry = new DefaultDdcConfigApplierRegistry((key, value, version) -> { });
        private final DdcFieldBindingService fields;
        private final DdcYamlConfigApplier applier;
        private final List<DdcConfigurationChangedEvent> events = new ArrayList<>();
        private boolean failListener;
        private Harness(Map<String, JsonNode> values) {
            source = new DdcDynamicPropertySource("tianshu:application.yml", DdcDynamicPropertySource.Snapshot.fromTyped("application.yml", 1, PLACEHOLDER, values));
            context.getEnvironment().getPropertySources().addFirst(source);
            context.getEnvironment().getPropertySources().addLast(new MapPropertySource("local", Map.of("sample.count", 10, "sample.label", "local", "leaf.value", "fallback")));
            context.register(PropertiesConfiguration.class);
            context.refresh();
            var rebinder = new DdcConfigurationPropertiesRebinder(context, context.getBean(ConfigurationPropertiesBindingPostProcessor.class), context.getEnvironment());
            rebinder.afterSingletonsInstantiated();
            fields = new DdcFieldBindingService(new DdcValueBindingRegistry(), context.getBeanFactory());
            applier = new DdcYamlConfigApplier(context.getEnvironment(), registry, fields, rebinder, event -> {
                events.add((DdcConfigurationChangedEvent) event);
                if (failListener) throw new IllegalStateException("listener failed");
            }, 1024 * 1024, DdcConfigFormatStrategyRegistry.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));
        }
        private SampleProperties properties() { return context.getBean(SampleProperties.class); }
        private DdcConfigurationChangedEvent apply(DdcConfigSnapshotDTO snapshot, List<RuleVO> rules) {
            return applier.applySnapshot(SCOPE, DIGEST, rules, snapshot, null);
        }
        @Override public void close() { context.close(); }
    }

    interface RefreshChecks { }
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SampleProperties.class)
    static class PropertiesConfiguration { }
    @DdcRefreshable
    @Validated(RefreshChecks.class)
    @ConfigurationProperties("sample")
    static class SampleProperties {
        @Min(value = 1, groups = RefreshChecks.class)
        private int count;
        private String label;
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
    static class CountField { @DdcValue("${sample.count:10}") private int count; }
    static class LimitField { @DdcValue("${field.limit:10}") @Min(1) private int limit = 10; }
}
