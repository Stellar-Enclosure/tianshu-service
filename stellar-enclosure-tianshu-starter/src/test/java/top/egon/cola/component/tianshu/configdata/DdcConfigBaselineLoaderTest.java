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
import org.springframework.core.env.MapPropertySource;
import top.egon.cola.component.tianshu.environment.DdcDynamicPropertySource;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;

import static org.assertj.core.api.Assertions.assertThat;

class DdcConfigBaselineLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path directory;

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
