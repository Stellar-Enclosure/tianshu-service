package top.egon.cola.component.tianshu.admin.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DdcProfileParityTest {

    private static final String OUTBOX_PREFIX = "egon.cola.component.transactional-outbox.";
    private static final List<String> ADMIN_PROFILES = List.of(
            "application.yml", "application-local.yml", "application-test.yml");
    private static final String TIANSHU_PREFIX = "egon.cola.component.tianshu.";
    private static final String RPC_CREDENTIALS_PROFILE =
            "yuheng-service/deployment/tianshu-rpc-credentials.yml";
    private static final List<String> DIRECT_CALLER_PROFILES = List.of(
            "tianshu-service/stellar-enclosure-tianshu-test/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-test/stellar-enclosure-yuheng-test-mcp-provider/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-test/stellar-enclosure-yuheng-test-rpc-consumer/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-test/stellar-enclosure-yuheng-test-tianquan-shoubing-backend/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-test/stellar-enclosure-yuheng-test-webflux-http-provider/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-test/stellar-enclosure-yuheng-test-rpc-provider/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-test/stellar-enclosure-yuheng-test-http-provider/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-biz-gateway/src/main/resources/application-operations.yml",
            "yuheng-service/stellar-enclosure-yuheng-biz-gateway/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-admin/src/main/resources/application.yml",
            "yuheng-service/stellar-enclosure-yuheng-admin/src/main/resources/application-local.yml",
            "yuheng-service/stellar-enclosure-yuheng-mcp-gateway/src/main/resources/application-operations.yml",
            "yuheng-service/stellar-enclosure-yuheng-mcp-gateway/src/main/resources/application.yml",
            "tianquan-shoubing-service/stellar-enclosure-tianquan-shoubing-admin/src/main/resources/application.yml",
            "tianquan-shoubing-service/stellar-enclosure-tianquan-shoubing-admin/src/main/resources/application-local.yml",
            "tianquan-jianshen-service/stellar-enclosure-tianquan-jianshen-admin/src/main/resources/application.yml",
            "tianquan-jianshen-service/stellar-enclosure-tianquan-jianshen-admin/src/main/resources/application-local.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-web/stellar-enclosure-source-web-starter/src/main/resources/application-prod.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-web/stellar-enclosure-source-web-starter/src/main/resources/application-test.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-web/stellar-enclosure-source-web-starter/src/main/resources/application-dev.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-web/stellar-enclosure-source-web-starter/src/main/resources/application.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-service/stellar-enclosure-source-service-starter/src/main/resources/application-prod.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-service/stellar-enclosure-source-service-starter/src/main/resources/application-test.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-service/stellar-enclosure-source-service-starter/src/main/resources/application-dev.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-service/stellar-enclosure-source-service-starter/src/main/resources/application.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-light/src/main/resources/application-prod.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-light/src/main/resources/application-test.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-light/src/main/resources/application-dev.yml",
            "Stellar-Enclosure-COLA/source-projects/stellar-enclosure-source-light/src/main/resources/application.yml");

    @Test
    void transactionalOutboxKeySetMatchesAcrossProfilesAndTestPollingIsDisabled() {
        Map<String, Properties> profiles = ADMIN_PROFILES.stream()
                .collect(Collectors.toMap(profile -> profile, this::load));
        Set<String> expectedKeys = keys(profiles.get("application.yml"));

        assertThat(keys(profiles.get("application-local.yml"))).isEqualTo(expectedKeys);
        assertThat(keys(profiles.get("application-test.yml"))).isEqualTo(expectedKeys);
        assertThat(profiles.get("application.yml").getProperty(OUTBOX_PREFIX + "enabled")).isEqualTo("true");
        assertThat(profiles.get("application-local.yml").getProperty(OUTBOX_PREFIX + "polling.enabled"))
                .isEqualTo("true");
        assertThat(profiles.get("application-test.yml").getProperty(OUTBOX_PREFIX + "polling.enabled"))
                .isEqualTo("false");
        assertThat(profiles.get("application.yml").getProperty(OUTBOX_PREFIX + "payload.max-bytes"))
                .isEqualTo("8MB");
        assertThat(profiles.get("application.yml").getProperty(OUTBOX_PREFIX + "retry.max-attempts"))
                .isEqualTo("10");
        assertThat(profiles.get("application.yml").getProperty(OUTBOX_PREFIX + "delivery.lease-duration"))
                .isEqualTo("60s");
    }

    @Test
    void directTianshuCallerProfilesRequireTenantAndRedisAclIdentityWithoutEnablingAiByDefault() {
        Path repositoryRoot = repositoryRoot();
        for (String profile : DIRECT_CALLER_PROFILES) {
            Properties properties = load(repositoryRoot.resolve(profile));
            assertThat(properties.getProperty(TIANSHU_PREFIX + "tenant-id"))
                    .as(profile + " tenant id")
                    .isEqualTo("${TIANSHU_TENANT_ID:}");
            assertThat(properties.getProperty(TIANSHU_PREFIX + "redis.username"))
                    .as(profile + " Redis ACL username")
                    .isEqualTo("${TIANSHU_REDIS_USERNAME:}");
            assertThat(properties.getProperty(TIANSHU_PREFIX + "prompt.enabled", "false"))
                    .as(profile + " Prompt default")
                    .isEqualTo("false");
            assertThat(properties.getProperty(TIANSHU_PREFIX + "skill.enabled", "false"))
                    .as(profile + " Skill default")
                    .isEqualTo("false");
        }
    }

    @Test
    void runtimeCredentialAllowsReadOnlyAiRpcForPromptAndSkill() {
        Properties adminProfile = load("application.yml");
        Properties callerOverride = load(repositoryRoot().resolve(RPC_CREDENTIALS_PROFILE));
        for (Properties profile : List.of(adminProfile, callerOverride)) {
            assertThat(profile.getProperty(TIANSHU_PREFIX + "admin.rpc.credentials[0].allowed-operations"))
                    .isNotNull().contains("CONFIG_PULL", "AI_READ")
                    .doesNotContain("MANAGEMENT_CONFIG_WRITE", "MANAGEMENT_PUBLISH");
        }
        assertThat(callerOverride.getProperty(TIANSHU_PREFIX + "admin.rpc.credentials[3].allowed-operations"))
                .isNotNull().contains("CONFIG_PULL", "AI_READ")
                .doesNotContain("MANAGEMENT_CONFIG_WRITE", "MANAGEMENT_PUBLISH");
    }

    private Properties load(String profile) {
        YamlPropertiesFactoryBean loader = new YamlPropertiesFactoryBean();
        loader.setResources(new ClassPathResource(profile));
        Properties properties = loader.getObject();
        if (properties == null) {
            throw new IllegalStateException("Cannot load Tianshu profile " + profile);
        }
        return properties;
    }

    private Properties load(Path profile) {
        YamlPropertiesFactoryBean loader = new YamlPropertiesFactoryBean();
        loader.setResources(new FileSystemResource(profile));
        Properties properties = loader.getObject();
        if (properties == null) {
            throw new IllegalStateException("Cannot load Tianshu caller profile " + profile);
        }
        return properties;
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isDirectory(current.resolve("Stellar-Enclosure-COLA/source-projects"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Cannot locate Stellar-Enclosure sibling workspace");
        return current;
    }

    private Set<String> keys(Properties properties) {
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(OUTBOX_PREFIX))
                .collect(Collectors.toSet());
    }
}
