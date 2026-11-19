package top.egon.cola.component.tianshu.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataLoader;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource.BaselineMetadataVO;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.format.DdcConfigFormatStrategyRegistry;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.service.binding.DdcBeanPostProcessor;
import top.egon.cola.component.tianshu.service.binding.DdcFieldBindingService;
import top.egon.cola.component.tianshu.service.binding.DdcValueBindingRegistry;
import top.egon.cola.component.tianshu.service.refresh.DdcConfigurationPropertiesRebinder;
import top.egon.cola.component.tianshu.service.refresh.DdcYamlConfigApplier;
import top.egon.cola.component.tianshu.service.refresh.DefaultDdcConfigApplierRegistry;
import top.egon.cola.component.tianshu.test.service.SampleConfigService;

class DdcSampleRefreshFlowTest {
    static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("1001", "retail", "dev", "demo-app", "application.yml");
    @TempDir Path temp;

    @Test
    void verifiedCompleteHotSnapshotRefreshesRealSampleFieldsAndRemovalRestoresTheirDefaults() throws Exception {
        try (var h = new Harness(temp)) {
            assertThat(h.sample.getRateLimit()).isEqualTo(100);
            var changed = h.snapshot(2,
                Map.of("downgrade-switch", JsonNodeFactory.instance.booleanNode(true),
                    "order.rate-limit.permits-per-second", JsonNodeFactory.instance.numberNode(200)));
            h.applier.applySnapshot(h.baseline, changed, "sample-2");
            assertThat(h.sample.getRateLimit()).isEqualTo(200);
            assertThat(h.sample.getDowngradeSwitch()).isTrue();
            h.applier.applySnapshot(h.baseline, h.snapshot(3, Map.of()), "sample-3");
            assertThat(h.sample.getRateLimit()).isEqualTo(100);
            assertThat(h.sample.getDowngradeSwitch()).isFalse();
            assertThat(h.applier.currentSnapshot().version()).isEqualTo(3);
        }
    }

    @Test
    void invalidDigestRestartAndConversionKeepTheLastEffectiveSampleValues() throws Exception {
        try (var h = new Harness(temp)) {
            var accepted =
                h.snapshot(2, Map.of("order.rate-limit.permits-per-second", JsonNodeFactory.instance.numberNode(250)));
            h.applier.applySnapshot(h.baseline, accepted, "sample-2");
            var next =
                h.snapshot(3, Map.of("order.rate-limit.permits-per-second", JsonNodeFactory.instance.numberNode(300)));
            var wrongDigest = new DdcConfigSnapshotDTO("1", SCOPE, "3", true, next.getRestartDigest(),
                "sha256:"
                    + "0".repeat(64),
                next.getHotValues(), null, next.getValidUntil());
            assertThatThrownBy(() -> h.applier.applySnapshot(h.baseline, wrongDigest, "bad-digest"))
                .hasMessage("CONFIG_SNAPSHOT_DIGEST_INVALID");
            var wrongRestart = new DdcConfigSnapshotDTO("1", SCOPE, "3", true,
                "sha256:"
                    + "b".repeat(64),
                next.getContentSha256(), next.getHotValues(), null, next.getValidUntil());
            assertThatThrownBy(() -> h.applier.applySnapshot(h.baseline, wrongRestart, "bad-restart"))
                .hasMessage("CONFIG_RESTART_REQUIRED");
            assertThatThrownBy(()
                                   -> h.applier.applySnapshot(h.baseline,
                                       h.snapshot(3,
                                           Map.of("order.rate-limit.permits-per-second",
                                               JsonNodeFactory.instance.numberNode(Long.MAX_VALUE))),
                                       "bad-conversion"))
                .isInstanceOf(RuntimeException.class);
            assertThat(h.sample.getRateLimit()).isEqualTo(250);
            assertThat(h.applier.currentSnapshot().version()).isEqualTo(2);
        }
    }

    /** Real local loader, binding service, rebinder and applier; no fabricated remote ACK or registration. */
    static final class Harness implements AutoCloseable {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final DdcConfigBaselineVO baseline;
        final BaselineMetadataVO metadata;
        final SampleConfigService sample;
        final DdcYamlConfigApplier applier;

        Harness(Path directory) throws Exception {
            ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
            var document = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(
                """
                    {"schemaVersion":"1","scope":{"tenantId":"1001","bizCode":"retail","env":"dev",
                     "appCode":"demo-app","resourceName":"application.yml"},"source":{"configId":"42","revision":"1"},
                     "enabled":true,"contentSha256":"sha256:%s","restartDigest":"sha256:%s",
                     "rules":[{"key":"downgrade-switch","mode":"HOT","valueType":"BOOLEAN","schemaRef":null,"nullable":false,"deletionAllowed":true},
                              {"key":"order.rate-limit.permits-per-second","mode":"HOT","valueType":"INTEGER","schemaRef":null,"nullable":false,"deletionAllowed":true}],
                     "targetBindings":[{"bindingId":"demo-local","bindingRevision":"1","clusterId":"test-cluster",
                      "kubernetesNamespace":"sample","workloadKind":"Deployment","workloadName":"demo-app","workloadUid":"workload-a","containerName":"app"}],
                     "restartValues":{},"hotSeed":{"downgrade-switch":false,"order.rate-limit.permits-per-second":100},
                     "startupDependencies":{},"secretReferences":{}}
                    """.formatted("0".repeat(64), "0".repeat(64)));
            var preliminary = json.treeToValue(document, DdcConfigBaselineVO.class);
            document.put("restartDigest", DdcConfigCanonicalJson.restartDigest(preliminary));
            preliminary = json.treeToValue(document, DdcConfigBaselineVO.class);
            var base = new DdcConfigSnapshotDTO("1", SCOPE, "1", true, preliminary.getRestartDigest(),
                preliminary.getContentSha256(), preliminary.getHotSeed(), null, NOW.plusSeconds(300));
            document.put("contentSha256", DdcConfigCanonicalJson.contentSha256(base));
            baseline = json.treeToValue(document, DdcConfigBaselineVO.class);
            Path file = directory.resolve("baseline.json");
            Files.writeString(file, document.toString());
            var target = new DdcConfigDataResource.ExpectedTargetVO(
                "demo-local", 1L, "test-cluster", "sample", "workload-a", "app");
            var loaded = new DdcConfigDataLoader().load(null, new DdcConfigDataResource(file, SCOPE, target));
            var sources = loaded.getPropertySources();
            for (int i = sources.size() - 1; i >= 0; i--)
                context.getEnvironment().getPropertySources().addFirst(sources.get(i));
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            context.getBeanFactory().addEmbeddedValueResolver(context.getEnvironment()::resolveRequiredPlaceholders);
            ConfigurationPropertiesBindingPostProcessor.register(context);
            context.registerBean("ddcValueBindingRegistry", DdcValueBindingRegistry.class);
            context.registerBean("ddcFieldBindingService", DdcFieldBindingService.class,
                ()
                    -> new DdcFieldBindingService(
                        context.getBean(DdcValueBindingRegistry.class), context.getBeanFactory()));
            context.registerBean("ddcBeanPostProcessor", DdcBeanPostProcessor.class);
            context.registerBean("sampleConfigService", SampleConfigService.class);
            context.refresh();
            sample = context.getBean(SampleConfigService.class);
            var registry = new DefaultDdcConfigApplierRegistry((key, value, version) -> {});
            registry.freeze();
            var rebinder = new DdcConfigurationPropertiesRebinder(
                context, context.getBean(ConfigurationPropertiesBindingPostProcessor.class), context.getEnvironment());
            applier = new DdcYamlConfigApplier(context.getEnvironment(), registry,
                context.getBean(DdcFieldBindingService.class), rebinder,
                event -> {}, 1_048_576, DdcConfigFormatStrategyRegistry.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));
            applier.afterSingletonsInstantiated();
            metadata = DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment());
        }

        DdcConfigSnapshotDTO snapshot(long revision, Map<String, JsonNode> values) {
            var preliminary =
                new DdcConfigSnapshotDTO("1", SCOPE, Long.toString(revision), true, baseline.getRestartDigest(),
                    "sha256:"
                        + "0".repeat(64),
                    values, null, NOW.plusSeconds(300));
            return new DdcConfigSnapshotDTO("1", SCOPE, Long.toString(revision), true, baseline.getRestartDigest(),
                DdcConfigCanonicalJson.contentSha256(preliminary), values, null, preliminary.getValidUntil());
        }

        @Override
        public void close() {
            context.close();
        }
    }
}
