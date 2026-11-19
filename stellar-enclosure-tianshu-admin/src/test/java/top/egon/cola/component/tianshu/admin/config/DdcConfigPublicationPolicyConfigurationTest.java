package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.egon.cola.component.tianshu.admin.config.DdcConfigPublicationPolicyConfiguration.PublicationPolicyFileVO;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier.BindingVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DdcConfigPublicationPolicyConfigurationTest {
    private static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("7011", "example", "staging", "demo", "application.yml");
    private static final String IDENTITY = "identity-7011";
    private static final BindingVO REGISTERED = new BindingVO("fixed-1", 9, IDENTITY, "client-1",
            Set.of("cred-1"), "app-1", "example", "demo", "staging", URI.create("https://tianshu.example.invalid"),
            SCOPE, "cluster-1", "platform-prod", "config-consumer", "tianshu-app-stable", "uid-a",
            "app", Map.of("app", "tianshu-demo"));
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DdcTenantRegistry tenants = mock(DdcTenantRegistry.class);
    @TempDir private Path directory;

    @Test
    void loadsExactPinnedRevisionAndResolvesOnlyTrustedOwnerScope() throws Exception {
        setupTenant();
        var expected = policy("uid-a");
        Path policy = write(expected);
        var registry = DdcConfigPublicationPolicyConfiguration.load(policy, sha256(policy),
                List.of(REGISTERED), tenants, json);
        assertThat(registry.targets()).singleElement().satisfies(target -> {
            assertThat(target.scope()).isEqualTo(SCOPE);
            assertThat(target.target().getWorkloadUid()).isEqualTo("uid-a");
        });
        var resolver = new DdcConfigPublicationPolicyConfiguration().ddcConfigPublicationPolicyResolver(registry, tenants);
        assertThat(resolver.require(new DdcTenantContextDTO(7011L, IDENTITY, "test"), SCOPE)).isEqualTo(expected);
        assertThatThrownBy(() -> resolver.require(new DdcTenantContextDTO(7011L, "other", "test"), SCOPE))
                .hasMessage("TIANSHU_CONFIG_PUBLICATION_SCOPE_UNVERIFIED");
        assertThatThrownBy(() -> resolver.require(new DdcTenantContextDTO(7011L, IDENTITY, "test"),
                new DdcConfigScopeVO("7011", "example", "staging", "other", "application.yml")))
                .hasMessage("TIANSHU_CONFIG_PUBLICATION_SCOPE_UNREGISTERED");
    }

    @Test
    void refusesUnpinnedChangesAndMismatchWithFixedWorkloadIdentity() throws Exception {
        setupTenant();
        Path policy = write(policy("recreated-uid"));
        assertThatThrownBy(() -> DdcConfigPublicationPolicyConfiguration.load(policy, "0".repeat(64),
                List.of(REGISTERED), tenants, json))
                .hasMessage("TIANSHU_CONFIG_PUBLICATION_POLICY_HASH_MISMATCH");
        String actual = sha256(policy);
        assertThatThrownBy(() -> DdcConfigPublicationPolicyConfiguration.load(policy, actual,
                List.of(REGISTERED), tenants, json))
                .hasMessage("TIANSHU_CONFIG_PUBLICATION_TARGET_UNVERIFIED");
    }

    @Test
    void rejectsDuplicateAndUnregisteredTargets() throws Exception {
        setupTenant();
        Path policy = write(policy("uid-a"));
        String actual = sha256(policy);
        assertThatThrownBy(() -> DdcConfigPublicationPolicyConfiguration.load(policy, actual,
                List.of(REGISTERED, REGISTERED), tenants, json))
                .hasMessage("TIANSHU_CONFIG_PUBLICATION_BINDING_AMBIGUOUS");
        assertThatThrownBy(() -> DdcConfigPublicationPolicyConfiguration.load(policy, actual,
                List.of(), tenants, json))
                .hasMessage("TIANSHU_CONFIG_PUBLICATION_TARGET_UNVERIFIED");
    }

    private void setupTenant() {
        when(tenants.requireEnabledTenant(7011L)).thenReturn(DdcTenantBindingDTO.builder()
                .tenantId(7011L).identityTenantId(IDENTITY).enabled(true).build());
    }

    private PublicationPolicyVO policy(String uid) {
        return new PublicationPolicyVO(SCOPE, List.of(), List.of(new TargetBindingVO(
                "fixed-1", "9", "cluster-1", "platform-prod", "Deployment", "tianshu-app-stable", uid, "app")),
                null, Map.of(), Map.of());
    }

    private Path write(PublicationPolicyVO declaration) throws Exception {
        Path path = directory.resolve("publication.json");
        json.writeValue(path.toFile(), new PublicationPolicyFileVO("1", List.of(declaration)));
        return path;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
