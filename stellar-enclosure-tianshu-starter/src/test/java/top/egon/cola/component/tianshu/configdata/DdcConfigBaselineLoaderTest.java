package top.egon.cola.component.tianshu.configdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import top.egon.cola.component.tianshu.environment.DdcDynamicPropertySource;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.IncarnationVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.ScopeKeyVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.StateVO;

import static org.assertj.core.api.Assertions.assertThat;

class DdcConfigBaselineLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path directory;

    @Test
    void coldBootRestoresCompatibleAppliedLkgBeforeCreatingApplicationBeans() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        Path stateDirectory = directory.resolve("state");
        Instant now = Instant.now();
        var applied = snapshot(manifest, "44", true, Map.of("feature.enabled", MAPPER.getNodeFactory().booleanNode(false)), now.plusSeconds(300));
        writeLkg(stateDirectory, manifest, applied, now);
        Path stored = stateFile(stateDirectory);
        byte[] beforeBoot = Files.readAllBytes(stored);

        deliveryRunner(baseline, stateDirectory).withUserConfiguration(ColdStartConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ColdStartProbe.class).featureEnabled()).isFalse();
            var source = (DdcDynamicPropertySource) context.getEnvironment().getPropertySources().get("tianshu:application.yml");
            assertThat(source.snapshot().version()).isEqualTo(44L);
            assertThat(context.getEnvironment().getProperty("server.port", Integer.class)).isEqualTo(8090);
            var recovery = DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment()).recovery();
            assertThat(recovery.recovered()).isTrue();
            assertThat(recovery.isolated()).isFalse();
            assertThat(recovery.invalid()).isFalse();
            assertThat(recovery.lastVerifiedAt()).isEqualTo(now);
            assertThat(recovery.validUntil()).isEqualTo(applied.getValidUntil());
            assertThat(recovery.getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("hotValues", "hotSeed", "bootId", "reportIncarnation", "lastReportSequence", "pendingReceipt");
        });
        assertThat(Files.readAllBytes(stored)).isEqualTo(beforeBoot);
    }

    @Test
    void strictStartupDoesNotRestoreLkgAndMissingStateNeverCreatesFiles() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        Path stateDirectory = directory.resolve("state");
        Instant now = Instant.now();
        var applied = snapshot(manifest, "44", true, Map.of("feature.enabled", MAPPER.getNodeFactory().booleanNode(false)), now.plusSeconds(300));
        writeLkg(stateDirectory, manifest, applied, now);
        deliveryRunner(baseline, stateDirectory).withPropertyValues("egon.cola.component.tianshu.delivery.allow-offline-start=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("feature.enabled", Boolean.class)).isTrue();
                    assertThat(DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment()).recovery().recovered()).isFalse();
                });
        Path missing = directory.resolve("missing-state");
        Instant validity = now.plusSeconds(300);
        deliveryRunner(baseline, missing).withPropertyValues("egon.cola.component.tianshu.delivery.baseline-valid-until=" + validity)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var recovery = DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment()).recovery();
                    assertThat(recovery.recovered()).isFalse();
                    assertThat(recovery.invalid()).isFalse();
                    assertThat(recovery.validUntil()).isEqualTo(validity);
                });
        assertThat(Files.exists(missing)).isFalse();
    }

    @Test
    void incompatiblePodBindingRestartAndRuleValuesNeverReplaceTheBaseline() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        Instant now = Instant.now();
        var applied = snapshot(manifest, "44", true, Map.of("feature.enabled", MAPPER.getNodeFactory().booleanNode(false)), now.plusSeconds(300));
        var otherRestartDraft = new DdcConfigSnapshotDTO("1", manifest.getScope(), "44", true, "sha256:" + "f".repeat(64),
                applied.getContentSha256(), applied.getHotValues(), null, applied.getValidUntil());
        var otherRestart = new DdcConfigSnapshotDTO("1", otherRestartDraft.getScope(), "44", true, otherRestartDraft.getRestartDigest(),
                DdcConfigCanonicalJson.contentSha256(otherRestartDraft), otherRestartDraft.getHotValues(), null, otherRestartDraft.getValidUntil());
        var wrongType = snapshot(manifest, "44", true, Map.of("feature.enabled", MAPPER.getNodeFactory().textNode("false")), now.plusSeconds(300));
        var variants = List.of(
                new StateVO(key(manifest), 3L, manifest.getRestartDigest(), "other-pod", null, false, applied, now, null, 0L, null),
                new StateVO(key(manifest), 4L, manifest.getRestartDigest(), "pod-a", null, false, applied, now, null, 0L, null),
                new StateVO(key(manifest), 3L, otherRestart.getRestartDigest(), "pod-a", null, false, otherRestart, now, null, 0L, null),
                new StateVO(key(manifest), 3L, manifest.getRestartDigest(), "pod-a", null, false, wrongType, now, null, 0L, null));
        for (int index = 0; index < variants.size(); index++) {
            Path stateDirectory = directory.resolve("state-" + index);
            new DdcConfigLkgStore(stateDirectory).write(variants.get(index));
            deliveryRunner(baseline, stateDirectory).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getEnvironment().getProperty("feature.enabled", Boolean.class)).isTrue();
                var recovery = DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment()).recovery();
                assertThat(recovery.recovered()).isFalse();
                assertThat(recovery.invalid()).isTrue();
            });
        }
    }

    @Test
    void appliedLkgUsesTheExactScopeConfigAndStableTargetPartition() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        Instant now = Instant.now();
        var applied = snapshot(manifest, "44", true, Map.of("feature.enabled", MAPPER.getNodeFactory().booleanNode(false)), now.plusSeconds(300));
        var expected = key(manifest);
        var otherScope = new DdcConfigScopeVO("1002", "reading", "prod", "reader", "application.yml");
        var variants = List.of(new ScopeKeyVO(expected.scope(), "99", expected.bindingId(), expected.clusterId(), expected.kubernetesNamespace(), expected.workloadUid(), expected.containerName()),
                new ScopeKeyVO(expected.scope(), expected.configId(), "another-target", expected.clusterId(), expected.kubernetesNamespace(), expected.workloadUid(), expected.containerName()),
                new ScopeKeyVO(otherScope, expected.configId(), expected.bindingId(), expected.clusterId(), expected.kubernetesNamespace(), expected.workloadUid(), expected.containerName()));
        for (int index = 0; index < variants.size(); index++) {
            Path stateDirectory = directory.resolve("partition-" + index);
            var draft = new DdcConfigSnapshotDTO("1", variants.get(index).scope(), "44", true, applied.getRestartDigest(),
                    applied.getContentSha256(), applied.getHotValues(), null, applied.getValidUntil());
            var partitionApplied = new DdcConfigSnapshotDTO("1", draft.getScope(), "44", true, draft.getRestartDigest(),
                    DdcConfigCanonicalJson.contentSha256(draft), draft.getHotValues(), null, draft.getValidUntil());
            new DdcConfigLkgStore(stateDirectory).write(new StateVO(variants.get(index), 3L, manifest.getRestartDigest(), "pod-a",
                    null, false, partitionApplied, now, null, 0L, null));
            deliveryRunner(baseline, stateDirectory).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getEnvironment().getProperty("feature.enabled", Boolean.class)).isTrue();
                assertThat(DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment()).recovery().recovered()).isFalse();
            });
        }
    }

    @Test
    void recoveryNeverBypassesTrustedBaselineIntegrityAndRequiresDeploymentOwnedSettings() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        Path stateDirectory = directory.resolve("state");
        Instant now = Instant.now();
        writeLkg(stateDirectory, manifest, snapshot(manifest, "44", true,
                Map.of("feature.enabled", MAPPER.getNodeFactory().booleanNode(false)), now.plusSeconds(300)), now);
        Files.writeString(baseline, validBaseline().replace("8090", "8091"));
        deliveryRunner(baseline, stateDirectory).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_DIGEST_MISMATCH"));
        Files.writeString(baseline, validBaseline());
        runner(baseline).withPropertyValues("egon.cola.component.tianshu.delivery.enabled=true")
                .run(context -> assertFailure(context.getStartupFailure(), "CONFIG_RECOVERY_STATE_DIRECTORY_REQUIRED"));
        runner(baseline).withPropertyValues("egon.cola.component.tianshu.delivery.enabled=true",
                        "egon.cola.component.tianshu.delivery.state-directory=" + stateDirectory)
                .run(context -> assertFailure(context.getStartupFailure(), "CONFIG_RECOVERY_POD_UID_REQUIRED"));
        for (String override : List.of("state-directory=relative", "state-directory=/", "max-stale=0ms", "max-stale=-1ms",
                "baseline-valid-until=2026-11-18T12:00:00+00:00", "baseline-valid-until=invalidZ")) {
            deliveryRunner(baseline, stateDirectory).withPropertyValues("egon.cola.component.tianshu.delivery." + override)
                    .run(context -> assertFailure(context.getStartupFailure(), "CONFIG_RECOVERY_"));
        }
    }

    @Test
    void expiredOrStaleAppliedDataIsKnownButNotRecoveredWhileCorruptStateIsInvalid() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        Instant now = Instant.now();
        for (int index = 0; index < 2; index++) {
            Path stateDirectory = directory.resolve("expired-" + index);
            var applied = snapshot(manifest, "44", true, Map.of("feature.enabled", MAPPER.getNodeFactory().booleanNode(false)),
                    index == 0 ? now.minusSeconds(60) : now.plusSeconds(300));
            writeLkg(stateDirectory, manifest, applied, index == 0 ? now : now.minusSeconds(600));
            deliveryRunner(baseline, stateDirectory).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getEnvironment().getProperty("feature.enabled", Boolean.class)).isTrue();
                var recovery = DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment()).recovery();
                assertThat(recovery.recovered()).isFalse();
                assertThat(recovery.invalid()).isFalse();
            });
        }
        Path corruptDirectory = directory.resolve("corrupt");
        writeLkg(corruptDirectory, manifest, snapshot(manifest, "44", true,
                Map.of("feature.enabled", MAPPER.getNodeFactory().booleanNode(false)), now.plusSeconds(300)), now);
        Files.writeString(stateFile(corruptDirectory), "{broken");
        deliveryRunner(baseline, corruptDirectory).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("feature.enabled", Boolean.class)).isTrue();
            assertThat(DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment()).recovery().invalid()).isTrue();
        });
    }

    @Test
    void isolatedScopeAndDisabledFloorSuppressTheOldSeedBeforeAnyApplicationBeanExists() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        for (boolean isolated : List.of(true, false)) {
            Path stateDirectory = directory.resolve(isolated ? "isolated" : "disabled");
            new DdcConfigLkgStore(stateDirectory).write(new StateVO(key(manifest), 3L, manifest.getRestartDigest(), "older-pod",
                    isolated ? null : "44", isolated, null, null, null, 0L, null));
            deliveryRunner(baseline, stateDirectory).withPropertyValues("egon.cola.component.tianshu.delivery.allow-offline-start=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var source = (DdcDynamicPropertySource) context.getEnvironment().getPropertySources().get("tianshu:application.yml");
                        assertThat(source.snapshot().version()).isZero();
                        assertThat(source.snapshot().values()).isEmpty();
                        assertThat(context.getEnvironment().getProperty("feature.enabled")).isNull();
                        assertThat(context.getEnvironment().getProperty("server.port", Integer.class)).isEqualTo(8090);
                        var metadata = DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment());
                        assertThat(metadata.enabled()).isFalse();
                        assertThat(metadata.recovery().isolated()).isEqualTo(isolated);
                        assertThat(metadata.recovery().highestDisabledRevision()).isEqualTo(isolated ? null : "44");
                    });
        }
    }

    @Test
    void confirmedDisabledSnapshotSurvivesRestartAsEmptyHotWithoutRestoringPriorProcessState() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        var manifest = MAPPER.readValue(validBaseline(), DdcConfigBaselineVO.class);
        Instant now = Instant.now();
        Path stateDirectory = directory.resolve("disabled-applied");
        var applied = snapshot(manifest, "44", false, Map.of(), now.plusSeconds(300));
        new DdcConfigLkgStore(stateDirectory).write(new StateVO(key(manifest), 3L, manifest.getRestartDigest(), "pod-a", "44", false,
                applied, now, new IncarnationVO("pod-a", "containerd://old", "old-boot"), 7L, null));
        deliveryRunner(baseline, stateDirectory).run(context -> {
            assertThat(context).hasNotFailed();
            var source = (DdcDynamicPropertySource) context.getEnvironment().getPropertySources().get("tianshu:application.yml");
            assertThat(source.snapshot().version()).isEqualTo(44L);
            assertThat(source.snapshot().values()).isEmpty();
            var metadata = DdcConfigDataLoader.baselineMetadataOrNull(context.getEnvironment());
            assertThat(metadata.enabled()).isFalse();
            assertThat(metadata.recovery().recovered()).isTrue();
            assertThat(metadata.recovery().highestDisabledRevision()).isEqualTo("44");
        });
    }

    @Test
    void actualSpringConfigDataLoadsOnlyTheManagedLocalBaselineIntoSeparateRestartAndHotSources() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());

        runner(baseline).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8090);
            assertThat(environment.getProperty("feature.enabled", Boolean.class)).isTrue();
            var source = environment.getPropertySources().get("tianshu:application.yml");
            assertThat(source).isInstanceOf(DdcDynamicPropertySource.class);
            assertThat(((DdcDynamicPropertySource) source).snapshot().version()).isEqualTo(43L);
            assertThat(((DdcDynamicPropertySource) source).snapshot().values()).containsOnlyKeys("feature.enabled");
            assertThat(environment.getPropertySources().get("tianshu:baseline-restart")).isNotNull();
            assertThat(environment.getPropertySources().get("tianshu:baseline-metadata")).isNotNull();
            var metadata = DdcConfigDataLoader.baselineMetadataOrNull(environment);
            assertThat(metadata.scope().getTenantId()).isEqualTo("1001");
            assertThat(metadata.targetBinding().getBindingRevision()).isEqualTo("3");
            assertThat(metadata.rules()).hasSize(2);
            assertThat(metadata.getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("hotSeed", "hotValues", "restartValues");
            environment.getPropertySources().addLast(new MapPropertySource("image-defaults", Map.of("feature.enabled", false)));
            ((DdcDynamicPropertySource) source).replace(DdcDynamicPropertySource.Snapshot.fromTyped(
                    "application.yml", 44L, "sha256:" + "1".repeat(64), Map.of()));
            assertThat(environment.getProperty("feature.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8090);
        });
    }

    @Test
    void requiredMissingAndExplicitOptionalNeverDisableTheVerifiedBaseline() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        runner(baseline).withPropertyValues("spring.config.on-not-found=ignore").run(context ->
                assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_UNAVAILABLE"));
        Files.writeString(baseline, validBaseline());
        runner(baseline).withPropertyValues("spring.config.import=optional:tianshu-baseline:" + baseline).run(context ->
                assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_OPTIONAL_FORBIDDEN"));
    }

    @Test
    void rejectsRelativeRemoteAndUnconfiguredLocationsBeforeAnyFileCanBeRead() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        for (String value : List.of("baseline.json", "https://example.invalid/baseline.json", "file:" + baseline,
                "/different-mount/baseline.json", baseline + "?token=forbidden", baseline + "#fragment")) {
            runner(baseline).withPropertyValues("spring.config.import=tianshu-baseline:" + value)
                    .run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_PATH_"));
        }
    }

    @Test
    void comparesFrozenScopeAndEverySelectedBindingDimension() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        Files.writeString(baseline, validBaseline());
        for (String override : List.of("scope.tenant-id=1002", "scope.biz-code=other", "scope.env=staging", "scope.app-code=other")) {
            runner(baseline).withPropertyValues("egon.cola.component.tianshu.baseline." + override)
                    .run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_SCOPE_MISMATCH"));
        }
        for (String override : List.of("target.binding-id=other", "target.binding-revision=4", "target.cluster-id=other",
                "target.kubernetes-namespace=other", "target.workload-uid=other", "target.container-name=worker")) {
            runner(baseline).withPropertyValues("egon.cola.component.tianshu.baseline." + override)
                    .run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_TARGET_MISMATCH"));
        }
    }

    @Test
    void verifiesBothDigestsAndRejectsMalformedOrOversizedContent() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        String valid = validBaseline();
        for (String tampered : List.of(valid.replace("8090", "8091"), valid.replace("\"feature.enabled\":true", "\"feature.enabled\":false"))) {
            Files.writeString(baseline, tampered);
            runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_DIGEST_MISMATCH"));
        }
        for (String malformed : List.of(valid + " {}", valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"1\",\"schemaVersion\":\"1\""),
                valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":1"))) {
            Files.writeString(baseline, malformed);
            runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_INVALID"));
        }
        Files.write(baseline, new byte[] { '{', (byte) 0xc3, (byte) 0x28, '}' });
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_INVALID"));
        Files.writeString(baseline, " ".repeat(1024 * 1024 + 1));
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_SIZE_INVALID"));
        Files.writeString(baseline, "");
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_SIZE_INVALID"));
    }

    @Test
    void rejectsReservedRulesNestedReservedValuesAndWrongRuleModeOrTypeEvenWithRecomputedDigests() throws Exception {
        Path baseline = directory.resolve("baseline.json");
        ObjectNode forbiddenRule = (ObjectNode) MAPPER.readTree(validBaseline());
        var rules = (com.fasterxml.jackson.databind.node.ArrayNode) forbiddenRule.get("rules");
        rules.addObject().put("key", "spring.config.import").put("mode", "HOT").put("valueType", "STRING")
                .putNull("schemaRef").put("nullable", false).put("deletionAllowed", true);
        Files.writeString(baseline, seal(forbiddenRule));
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_RULE_RESERVED"));
        ObjectNode nested = (ObjectNode) MAPPER.readTree(validBaseline());
        ((com.fasterxml.jackson.databind.node.ArrayNode) nested.get("rules")).addObject().put("key", "spring")
                .put("mode", "HOT").put("valueType", "OBJECT").putNull("schemaRef").put("nullable", false).put("deletionAllowed", true);
        ((ObjectNode) nested.get("hotSeed")).putObject("spring").putObject("config").put("import", "https://example.invalid");
        Files.writeString(baseline, seal(nested));
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_INVALID"));
        ObjectNode wrongMode = (ObjectNode) MAPPER.readTree(validBaseline());
        ((ObjectNode) wrongMode.get("hotSeed")).put("server.port", 8090);
        Files.writeString(baseline, seal(wrongMode));
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_RULE_MISMATCH"));
        ObjectNode wrongType = (ObjectNode) MAPPER.readTree(validBaseline());
        ((ObjectNode) wrongType.get("hotSeed")).put("feature.enabled", "not-a-boolean");
        Files.writeString(baseline, seal(wrongType));
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_RULE_MISMATCH"));
    }

    @Test
    void readsKubeletAtomicGenerationSymlinkButRefusesEscapeFromTheConfiguredVolume() throws Exception {
        Path volume = Files.createDirectory(directory.resolve("volume"));
        Path generation = Files.createDirectory(volume.resolve("..generation"));
        Files.writeString(generation.resolve("baseline.json"), validBaseline());
        Files.createSymbolicLink(volume.resolve("..data"), Path.of("..generation"));
        Path baseline = Files.createSymbolicLink(volume.resolve("baseline.json"), Path.of("..data/baseline.json"));
        runner(baseline).run(context -> assertThat(context).hasNotFailed());
        Path outside = directory.resolve("outside.json");
        Files.writeString(outside, validBaseline());
        Files.delete(baseline);
        Files.createSymbolicLink(baseline, outside);
        runner(baseline).run(context -> assertFailure(context.getStartupFailure(), "CONFIG_BASELINE_PATH_OUTSIDE_MOUNT"));
    }

    private static void assertFailure(Throwable failure, String reason) {
        assertThat(failure).isNotNull();
        Throwable cursor = failure;
        boolean found = false;
        while (cursor != null) {
            if (cursor.getMessage() != null && cursor.getMessage().contains(reason)) { found = true; break; }
            cursor = cursor.getCause();
        }
        assertThat(found).as("stable baseline failure %s", reason).isTrue();
    }

    private static ApplicationContextRunner runner(Path baseline) {
        return new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=optional:classpath:/missing-configdata-fixture.yml",
                        "spring.config.import=tianshu-baseline:" + baseline,
                        "egon.cola.component.tianshu.baseline.file=" + baseline,
                        "egon.cola.component.tianshu.baseline.scope.tenant-id=1001",
                        "egon.cola.component.tianshu.baseline.scope.biz-code=reading",
                        "egon.cola.component.tianshu.baseline.scope.env=prod",
                        "egon.cola.component.tianshu.baseline.scope.app-code=reader",
                        "egon.cola.component.tianshu.baseline.scope.resource-name=application.yml",
                        "egon.cola.component.tianshu.baseline.target.binding-id=reader-prod",
                        "egon.cola.component.tianshu.baseline.target.binding-revision=3",
                        "egon.cola.component.tianshu.baseline.target.cluster-id=cluster-prod",
                        "egon.cola.component.tianshu.baseline.target.kubernetes-namespace=reader",
                        "egon.cola.component.tianshu.baseline.target.workload-uid=workload-a",
                        "egon.cola.component.tianshu.baseline.target.container-name=application");
    }

    private static String validBaseline() throws Exception {
        ObjectNode json = (ObjectNode) DdcConfigCanonicalJson.parse("""
                {"schemaVersion":"1","scope":{"tenantId":"1001","bizCode":"reading","env":"prod",
                 "appCode":"reader","resourceName":"application.yml"},"source":{"configId":"42","revision":"43"},
                 "enabled":true,"contentSha256":"sha256:%s","restartDigest":"sha256:%s",
                 "rules":[{"key":"feature.enabled","mode":"HOT","valueType":"BOOLEAN","schemaRef":null,"nullable":false,"deletionAllowed":true},
                          {"key":"server.port","mode":"RESTART","valueType":"INTEGER","schemaRef":null,"nullable":false,"deletionAllowed":false}],
                 "targetBindings":[{"bindingId":"reader-prod","bindingRevision":"3","clusterId":"cluster-prod",
                   "kubernetesNamespace":"reader","workloadKind":"Deployment","workloadName":"reader","workloadUid":"workload-a","containerName":"application"}],
                 "restartValues":{"server.port":8090},"hotSeed":{"feature.enabled":true},"startupDependencies":{},"secretReferences":{}}
                """.formatted("0".repeat(64), "0".repeat(64)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return seal(json);
    }

    private static void writeLkg(Path stateDirectory, DdcConfigBaselineVO baseline, DdcConfigSnapshotDTO applied,
            Instant lastVerifiedAt) throws Exception {
        new DdcConfigLkgStore(stateDirectory).write(new StateVO(key(baseline), 3L, baseline.getRestartDigest(), "pod-a", null, false,
                applied, lastVerifiedAt, null, 0L, null));
    }

    private static ScopeKeyVO key(DdcConfigBaselineVO baseline) {
        return new ScopeKeyVO(baseline.getScope(), "42", "reader-prod", "cluster-prod", "reader", "workload-a", "application");
    }

    private static DdcConfigSnapshotDTO snapshot(DdcConfigBaselineVO baseline, String revision, boolean enabled,
            Map<String, com.fasterxml.jackson.databind.JsonNode> values, Instant validUntil) {
        var draft = new DdcConfigSnapshotDTO("1", baseline.getScope(), revision, enabled, baseline.getRestartDigest(),
                "sha256:" + "0".repeat(64), values, null, validUntil);
        return new DdcConfigSnapshotDTO("1", draft.getScope(), revision, enabled, draft.getRestartDigest(),
                DdcConfigCanonicalJson.contentSha256(draft), values, null, validUntil);
    }

    private static Path stateFile(Path stateDirectory) throws Exception {
        try (var entries = Files.list(stateDirectory)) {
            return entries.filter(path -> path.getFileName().toString().endsWith(".json")).findFirst().orElseThrow();
        }
    }

    private static ApplicationContextRunner deliveryRunner(Path baseline, Path stateDirectory) {
        return runner(baseline).withPropertyValues("egon.cola.component.tianshu.delivery.enabled=true",
                "egon.cola.component.tianshu.delivery.state-directory=" + stateDirectory,
                "egon.cola.component.tianshu.delivery.pod-uid=pod-a",
                "egon.cola.component.tianshu.delivery.max-stale=5m",
                "egon.cola.component.tianshu.delivery.allow-offline-start=true");
    }

    @Configuration(proxyBeanMethods = false)
    static class ColdStartConfiguration {
        @Bean
        ColdStartProbe coldStartProbe(Environment environment) {
            return new ColdStartProbe(environment.getRequiredProperty("feature.enabled", Boolean.class));
        }
    }

    record ColdStartProbe(boolean featureEnabled) { }

    private static String seal(ObjectNode json) throws Exception {
        var draft = MAPPER.treeToValue(json, DdcConfigBaselineVO.class);
        json.put("restartDigest", DdcConfigCanonicalJson.restartDigest(draft));
        draft = MAPPER.treeToValue(json, DdcConfigBaselineVO.class);
        var online = new DdcConfigSnapshotDTO("1", draft.getScope(), draft.getSource().getRevision(), true,
                draft.getRestartDigest(), draft.getContentSha256(), draft.getHotSeed(), null, Instant.parse("2026-11-18T18:00:00Z"));
        json.put("contentSha256", DdcConfigCanonicalJson.contentSha256(online));
        return json.toString();
    }
}
