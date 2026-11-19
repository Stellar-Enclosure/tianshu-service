package top.egon.cola.component.tianshu.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
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
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DdcConfigSnapshotServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-18T20:00:00Z");
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("101", "reader", "test", "app", "application.yml");
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(101L, "reader-tenant", "service:fixture");
    private static final DdcConfigWorkloadIdentityVO WORKLOAD = new DdcConfigWorkloadIdentityVO(TENANT, SCOPE,
            "binding-a", 7, "cluster-test", "reader-apps", "deployment-uid", "pod-uid",
            "application", "containerd://0123", "test-boot");
    private static final String RESTART = "sha256:" + "b".repeat(64);
    private static final String YAML = """
            config:
              explicit-null: null
              text: ""
              amount: 40
              secret-ref: "bootstrap-only"
            """;

    private final DdcConfigItemRepository items = mock(DdcConfigItemRepository.class);
    private final DdcConfigVersionRepository versions = mock(DdcConfigVersionRepository.class);
    private final DdcConfigBootBindingRepository boots = mock(DdcConfigBootBindingRepository.class);
    private final DdcConfigDeliveryTokenService tokens = mock(DdcConfigDeliveryTokenService.class);
    private final DdcConfigSnapshotService service = new DdcConfigSnapshotService(items, versions, boots,
            new DdcTenantContextExecutor(), tokens, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

    @Test
    void existingEtagStillGetsFullSnapshotOnFirstBootAndLaterGets304WithNewProof() {
        var manifest = manifest(true);
        stubPublication(manifest, YAML, 42L);
        var boot = DdcConfigBootBindingBO.builder().id(17L).build();
        when(boots.findConfirmed(eq(TENANT), any())).thenReturn(Optional.empty(), Optional.of(boot));
        when(boots.createOrConfirm(eq(TENANT), any())).thenReturn(boot);
        when(tokens.issue(eq(WORKLOAD), eq(17L), eq(441L), any())).thenReturn("first-proof", "renewed-proof");
        String etag = "\"cfg-r42-" + manifest.get("contentSha256").textValue().substring(7) + "\"";

        var first = service.snapshot(WORKLOAD, etag);
        assertThat(first.notModified()).isFalse();
        assertThat(first.etag()).isEqualTo(etag);
        assertThat(first.content().getValidUntil()).isEqualTo(NOW.plusSeconds(300));
        assertThat(first.content().getHotValues()).containsOnlyKeys("config.amount", "config.explicit-null", "config.text");
        assertThat(first.content().getHotValues().get("config.explicit-null").isNull()).isTrue();
        assertThat(first.content().getHotValues().get("config.text").textValue()).isEmpty();
        assertThat(first.content().getHotValues().get("config.amount").intValue()).isEqualTo(40);
        assertThat(first.deliveryToken()).isEqualTo("first-proof");
        assertThat(first.toString()).doesNotContain("first-proof", "amount");

        var second = service.snapshot(WORKLOAD, etag);
        assertThat(second.notModified()).isTrue();
        assertThat(second.deliveryToken()).isEqualTo("renewed-proof");
        verify(boots, times(1)).createOrConfirm(eq(TENANT), any());
    }

    @Test
    void malformedFixedContentOrMissingTargetRejectsBeforeAnyBootWrite() {
        var manifest = manifest(true);
        stubPublication(manifest, YAML.replace("40","41"), 42L);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        ((ObjectNode) manifest.withArray("targetBindings").get(0)).put("bindingRevision", "8");
        stubPublication(manifest, YAML, 42L);
        assertStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(boots, tokens);
    }

    @Test
    void conditionalDisabledReleaseStillHasNewRevisionAndEmptyHotProjection() {
        stubPublication(manifest(false), YAML, 42L);
        var snapshot = service.requireCurrent(WORKLOAD).snapshot();
        assertThat(snapshot.getEnabled()).isFalse();
        assertThat(snapshot.getReleaseRevision()).isEqualTo("42");
        assertThat(snapshot.getHotValues()).isEmpty();
    }

    @Test
    void invalidYamlAndAmbiguousFlattenedPathsNeverBecomeAnExecutableSnapshot() {
        stubPublication(manifest(true), "config: {amount: 40, amount: 40}", 42L);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        stubPublication(manifest(true), "config: {amount: 40}\nconfig.amount: 40", 42L);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(boots, tokens);
    }

    @Test
    void aHotTreeCannotRevealASeparatelyClassifiedRestartValue() {
        var rules = JSON.createArrayNode();
        rule(rules, "config", "HOT", "OBJECT", false);
        rule(rules, "config.secret-ref", "RESTART", "STRING", false);
        assertThatThrownBy(() -> DdcConfigSnapshotService.projectHotValues(YAML, true, rules))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("TIANSHU_CONFIG_RULES_OVERLAP");
    }

    private void assertStatus(HttpStatus status) {
        assertThatThrownBy(() -> service.snapshot(WORKLOAD, null))
                .isInstanceOfSatisfying(DdcConfigSnapshotService.Rejected.class,
                        failure -> assertThat(failure.status()).isEqualTo(status));
    }

    private void stubPublication(ObjectNode manifest, String yaml, long revision) {
        when(items.findByBizCodeAndEnvAndAppCodeAndResourceName("reader", "test", "app", "application.yml"))
                .thenReturn(Optional.of(item().setPublishedVersion(revision)));
        when(versions.findByConfigItemIdAndRevision(441L, revision))
                .thenReturn(Optional.of(DdcConfigVersionBO.builder().tenantId(101L).configItemId(441L)
                        .configRevision(revision).bizCode("reader").env("test").appCode("app")
                        .resourceName("application.yml").format("YAML").manifestJson(manifest).newContent(yaml).build()));
    }

    private static ObjectNode manifest(boolean enabled) {
        var target = new DdcConfigBaselineVO.TargetBindingVO("binding-a", "7", "cluster-test", "reader-apps",
                "Deployment", "reader", "deployment-uid", "application");
        var policy = new DdcConfigManifestFactory.PublicationPolicyVO(SCOPE, List.of(
                new DdcConfigBaselineVO.RuleVO("config.amount", DdcConfigBaselineVO.ReloadModeEnum.HOT,
                        "INTEGER", null, false, true),
                new DdcConfigBaselineVO.RuleVO("config.explicit-null", DdcConfigBaselineVO.ReloadModeEnum.HOT,
                        "STRING", null, true, true),
                new DdcConfigBaselineVO.RuleVO("config.text", DdcConfigBaselineVO.ReloadModeEnum.HOT,
                        "STRING", null, false, true)), List.of(target), null, Map.of(), Map.of());
        return (ObjectNode) new DdcConfigManifestFactory(JSON).freeze(TENANT, item(), 42L, YAML, enabled, policy).manifest();
    }

    private static DdcConfigItemBO item() {
        return DdcConfigItemBO.builder().id(441L).tenantId(101L).bizCode("reader").env("test").appCode("app")
                .resourceName("application.yml").format("YAML").enabled(true).deleted(false)
                .currentVersion(42L).publishedVersion(42L).build();
    }

    private static void rule(ArrayNode rules, String key, String mode, String type, boolean nullable) {
        rules.addObject().put("key", key).put("mode", mode).put("valueType", type).putNull("schemaRef")
                .put("nullable", nullable).put("deletionAllowed", true);
    }

}
