package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliveryTokenService;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigSnapshotService;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DdcConfigManifestFactoryTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(101L, "reader", "actor");
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("101", "reader", "test", "app", "application.yml");
    private static final String YAML = "config: {amount: 40, text: '', explicit-null: null}\nserver: {port: 8080}\n";
    private static final DdcConfigBaselineVO.TargetBindingVO TARGET = new DdcConfigBaselineVO.TargetBindingVO(
            "binding-a", "7", "cluster-test", "reader-apps", "Deployment", "reader", "deployment-uid", "application");
    private final DdcConfigManifestFactory factory = new DdcConfigManifestFactory(JSON);

    @Test
    void nativePublicationRequiresTheSharedManifestFactoryAndOwnedHotSchemaFence() throws Exception {
        Class<?> factory = Class.forName("top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory");
        assertThat(factory.getField("HOT_SCHEMA_DEPENDENCY_KEY").get(null))
                .isEqualTo("tianshu.config-hot-schema");
    }

    @Test
    void frozenMetadataMaterializesOnlyItsFixedTypedBodyAndKeepsSecretsAsReferences() {
        var frozen = freeze(42, YAML, true, policy(defaultRules()));
        var result = factory.materialize(version(42, YAML, frozen), SCOPE);
        assertThat(result.baseline().getRestartValues().get("server.port").intValue()).isEqualTo(8080);
        assertThat(result.baseline().getHotSeed().get("config.amount").intValue()).isEqualTo(40);
        assertThat(result.baseline().getHotSeed().get("config.explicit-null").isNull()).isTrue();
        assertThat(result.baseline().getHotSeed().get("config.text").textValue()).isEmpty();
        assertThat(result.baseline().getSource().getConfigId()).isEqualTo("441");
        assertThat(result.manifest().has("hotSeed")).isFalse();
        assertThat(result.manifest().has("restartValues")).isFalse();
        assertThat(result.manifest().path("schemaVersion").isTextual()).isTrue();
        assertThat(result.manifest().path("secretReferences").path("database").size()).isEqualTo(2);
        assertThat(result.baseline().getRestartDigest()).isEqualTo(DdcConfigCanonicalJson.restartDigest(result.baseline()));
        assertThat(result.baseline().getContentSha256()).isEqualTo(DdcConfigCanonicalJson.contentSha256(result.baseline()));
        ((ObjectNode) result.manifest()).put("enabled", false);
        assertThat(result.manifest().get("enabled").booleanValue()).isTrue();
        assertThat(result.toString()).doesNotContain("amount", "secret", "8080");
    }

    @Test
    void hotValueChangesKeepRestartFenceButFullHotSchemaOrRestartValuesRequireNewBaseline() {
        var first = freeze(42, YAML, true, policy(defaultRules()));
        var hotValue = freeze(43, YAML.replace("amount: 40", "amount: 41"), true, policy(defaultRules()));
        var restartValue = freeze(43, YAML.replace("8080", "8081"), true, policy(defaultRules()));
        var changed = new java.util.ArrayList<>(defaultRules());
        changed.set(0, rule("config.amount", "INTEGER", false, false));
        var hotSchema = freeze(43, YAML, true, policy(changed));
        assertThat(hotValue.baseline().getRestartDigest()).isEqualTo(first.baseline().getRestartDigest());
        assertThat(hotValue.baseline().getContentSha256()).isNotEqualTo(first.baseline().getContentSha256());
        assertThat(restartValue.baseline().getRestartDigest()).isNotEqualTo(first.baseline().getRestartDigest());
        assertThat(hotSchema.baseline().getRestartDigest()).isNotEqualTo(first.baseline().getRestartDigest());
        var reversed = new java.util.ArrayList<>(defaultRules());
        java.util.Collections.reverse(reversed);
        assertThat(freeze(42, YAML, true, policy(reversed)).manifest()).isEqualTo(first.manifest());
    }

    @Test
    void disabledVersionRetainsRestartValuesButAlwaysClearsItsHotProjection() {
        var active = freeze(42, YAML, true, policy(defaultRules()));
        var disabled = freeze(43, YAML, false, policy(defaultRules()));
        assertThat(disabled.baseline().getHotSeed()).isEmpty();
        assertThat(disabled.baseline().getRestartValues()).isEqualTo(active.baseline().getRestartValues());
        assertThat(disabled.baseline().getRestartDigest()).isEqualTo(active.baseline().getRestartDigest());
        assertThat(disabled.baseline().getContentSha256()).isNotEqualTo(active.baseline().getContentSha256());
        assertThat(factory.materialize(version(43, YAML, disabled), SCOPE).baseline().getEnabled()).isFalse();
        assertThatThrownBy(() -> factory.materialize(version(43, null, disabled), SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownedSchemaDependencyCannotBeOverriddenAndPartialLegacyMetadataNeverMaterializes() {
        var base = policy(defaultRules());
        var malicious = new DdcConfigManifestFactory.PublicationPolicyVO(SCOPE, base.rules(), base.targetBindings(),
                null, Map.of(DdcConfigManifestFactory.HOT_SCHEMA_DEPENDENCY_KEY, "sha256:" + "0".repeat(64)), Map.of());
        assertThatThrownBy(() -> freeze(42, YAML, true, malicious))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("TIANSHU_CONFIG_HOT_SCHEMA_DEPENDENCY_RESERVED");
        assertThatThrownBy(() -> factory.materialize(version(42, YAML, null), SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        var frozen = freeze(42, YAML, true, base);
        var row = version(42, YAML, frozen);
        ((ObjectNode) row.getManifestJson()).withObject("/startupDependencies")
                .remove(DdcConfigManifestFactory.HOT_SCHEMA_DEPENDENCY_KEY);
        assertThatThrownBy(() -> factory.materialize(row, SCOPE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metadataBodySourceScopeAndRestartTamperingAreAllRejected() {
        var frozen = freeze(42, YAML, true, policy(defaultRules()));
        for (String field : List.of("contentSha256", "restartDigest")) {
            var row = version(42, YAML, frozen);
            ((ObjectNode) row.getManifestJson()).put(field, "sha256:" + "f".repeat(64));
            assertThatThrownBy(() -> factory.materialize(row, SCOPE)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> factory.materialize(version(42, YAML.replace("8080", "8081"), frozen), SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.materialize(version(43, YAML, frozen), SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.materialize(version(42, YAML, frozen).setTenantId(102L), SCOPE))
                .isInstanceOf(IllegalArgumentException.class);
        var extra = version(42, YAML, frozen);
        ((ObjectNode) extra.getManifestJson()).put("secretValue", "must-not-be-a-contract");
        assertThatThrownBy(() -> factory.materialize(extra, SCOPE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservedKeysDuplicateYamlAndCrossReloadSubtreesAreRejected() {
        assertThatThrownBy(() -> freeze(42, YAML + "spring.config.import: remote\n", true, policy(defaultRules())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> freeze(42, "config: {amount: 1, amount: 2}", true, policy(defaultRules())))
                .isInstanceOf(IllegalArgumentException.class);
        var overlap = List.of(rule("config", "OBJECT", false, true),
                new DdcConfigBaselineVO.RuleVO("config.amount", DdcConfigBaselineVO.ReloadModeEnum.RESTART,
                        "INTEGER", null, false, true));
        assertThatThrownBy(() -> freeze(42, YAML, true, policy(overlap)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("TIANSHU_CONFIG_RULES_OVERLAP");
        assertThatThrownBy(() -> freeze(42, YAML.replace("40", "wrong"), true, policy(defaultRules())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void declaredSecretSlotsRejectBodyValuesRulesAndRelaxedPropertyAliases() {
        for (String body : List.of("database: must-not-enter-a-configmap\n", "database: {password: hidden}\n",
                "DATABASE: hidden\n")) {
            assertThatThrownBy(() -> freeze(42, body, true, policy(List.of())))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("TIANSHU_CONFIG_SECRET_SLOT_VALUE_FORBIDDEN");
        }
        assertThatThrownBy(() -> freeze(42, YAML, true, policy(List.of(rule("database", "STRING", false, true)))))
                .hasMessage("TIANSHU_CONFIG_SECRET_SLOT_VALUE_FORBIDDEN");
        var secretChild = new DdcConfigManifestFactory.PublicationPolicyVO(SCOPE, List.of(rule("connection", "OBJECT", false, true)),
                List.of(TARGET), null, Map.of(), Map.of("connection.password",new DdcConfigBaselineVO.SecretReferenceVO("db-access","7")));
        assertThatThrownBy(() -> freeze(42,"connection: {user: public-name}\n",true,secretChild))
                .hasMessage("TIANSHU_CONFIG_SECRET_SLOT_VALUE_FORBIDDEN");
        var relaxed = new DdcConfigManifestFactory.PublicationPolicyVO(SCOPE,List.of(),List.of(TARGET),null,Map.of(),
                Map.of("database-password",new DdcConfigBaselineVO.SecretReferenceVO("db-access","7")));
        assertThatThrownBy(() -> freeze(42,"databasePassword: hidden\n",true,relaxed))
                .hasMessage("TIANSHU_CONFIG_SECRET_SLOT_VALUE_FORBIDDEN");
    }

    @Test
    void relaxedAliasesCannotSplitOneSpringPropertyAcrossHotAndRestartOrLeakAParentTree() {
        var hotRule = policy(List.of(rule("feature.value-size", "INTEGER", false, true)));
        assertThatThrownBy(() -> freeze(42,"feature: {valueSize: 4}\n",true,hotRule))
                .hasMessage("TIANSHU_CONFIG_RULES_ALIAS_AMBIGUOUS");
        assertThatThrownBy(() -> freeze(42,"feature: {value-size: 4, valueSize: 5}\n",true,policy(List.of())))
                .hasMessage("TIANSHU_CONFIG_RULES_ALIAS_AMBIGUOUS");
        assertThatThrownBy(() -> freeze(42,"Feature: {nested: 4}\n",true,policy(List.of(rule("feature","OBJECT",false,true)))))
                .hasMessage("TIANSHU_CONFIG_RULES_ALIAS_AMBIGUOUS");
        var exact=freeze(42,"feature: {value-size: 4}\n",true,hotRule);
        assertThat(exact.baseline().getHotSeed()).containsOnlyKeys("feature.value-size");
    }

    @Test
    void onlyTheExactNativeDisabledCurrentPointerCanBeServedFromASoftDeletedItem() {
        var items = mock(DdcConfigItemRepository.class);
        var versions = mock(DdcConfigVersionRepository.class);
        var boots = mock(DdcConfigBootBindingRepository.class);
        var tokens = mock(DdcConfigDeliveryTokenService.class);
        var service = new DdcConfigSnapshotService(items, versions, boots, new DdcTenantContextExecutor(), tokens,
                Clock.systemUTC(), Duration.ofMinutes(5), factory);
        var workload = new DdcConfigWorkloadIdentityVO(TENANT, SCOPE, "binding-a", 7, "cluster-test", "reader-apps",
                "deployment-uid", "pod-uid", "application", "containerd://fixture", "boot-1");
        var tombstone = item().setDeleted(true).setEnabled(false).setCurrentVersion(43L).setPublishedVersion(43L);
        when(items.findByBizCodeAndEnvAndAppCodeAndResourceName("reader", "test", "app", "application.yml"))
                .thenReturn(Optional.of(tombstone));
        var disabled = freeze(43, YAML, false, policy(defaultRules()));
        when(versions.findByConfigItemIdAndRevision(441L, 43L)).thenReturn(Optional.of(version(43, YAML, disabled)));
        assertThat(service.requireCurrent(workload).snapshot().getEnabled()).isFalse();
        tombstone.setCurrentVersion(44L);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.requireCurrent(workload));
        tombstone.setCurrentVersion(43L).setEnabled(true);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.requireCurrent(workload));
        tombstone.setEnabled(false);
        when(versions.findByConfigItemIdAndRevision(441L, 43L)).thenReturn(Optional.of(version(43, YAML, null)));
        assertUnavailable(() -> service.requireCurrent(workload));
        var enabled = freeze(43, YAML, true, policy(defaultRules()));
        when(versions.findByConfigItemIdAndRevision(441L, 43L)).thenReturn(Optional.of(version(43, YAML, enabled)));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.requireCurrent(workload));
        verifyNoInteractions(boots, tokens);
    }

    private static void assertUnavailable(Runnable action) {
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, action);
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DdcConfigSnapshotService.Rejected.class,
                failure -> assertThat(failure.status()).isEqualTo(status));
    }

    private DdcConfigManifestFactory.FrozenConfigVO freeze(long revision, String yaml, boolean enabled,
            DdcConfigManifestFactory.PublicationPolicyVO policy) {
        return factory.freeze(TENANT, item(), revision, yaml, enabled, policy);
    }

    private static DdcConfigItemBO item() {
        return DdcConfigItemBO.builder().id(441L).tenantId(101L).publicId("public-config-not-the-source-id")
                .bizCode("reader").env("test").appCode("app").resourceName("application.yml").format("YAML")
                .enabled(true).deleted(false).currentVersion(42L).publishedVersion(42L).build();
    }

    private static DdcConfigVersionBO version(long revision, String yaml, DdcConfigManifestFactory.FrozenConfigVO frozen) {
        return DdcConfigVersionBO.builder().configItemId(441L).tenantId(101L).configRevision(revision)
                .bizCode("reader").env("test").appCode("app").resourceName("application.yml").format("YAML")
                .newContent(yaml).manifestJson(frozen == null ? null : frozen.manifest()).build();
    }

    private static DdcConfigManifestFactory.PublicationPolicyVO policy(List<DdcConfigBaselineVO.RuleVO> rules) {
        return new DdcConfigManifestFactory.PublicationPolicyVO(SCOPE, rules, List.of(TARGET), null,
                Map.of("driver", "version-1"), Map.of("database", new DdcConfigBaselineVO.SecretReferenceVO("db-access", "7")));
    }

    private static List<DdcConfigBaselineVO.RuleVO> defaultRules() {
        return List.of(rule("config.amount", "INTEGER", false, true), rule("config.text", "STRING", false, true),
                rule("config.explicit-null", "STRING", true, true));
    }

    private static DdcConfigBaselineVO.RuleVO rule(String key, String type, boolean nullable, boolean deletionAllowed) {
        return new DdcConfigBaselineVO.RuleVO(key, DdcConfigBaselineVO.ReloadModeEnum.HOT, type, null, nullable, deletionAllowed);
    }
}
