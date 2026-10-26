package top.egon.cola.component.tianshu.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcConfigRpcProvider;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcManagementRpcProvider;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcRegistryRpcProvider;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcHmacCredentialRegistry;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcRpcSecurityConfiguration;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigFacade;
import top.egon.cola.component.tianshu.admin.service.management.DdcManagementFacade;
import top.egon.cola.component.tianshu.admin.service.lease.DdcResourceAdmissionRevocationService;
import top.egon.cola.component.tianshu.admin.service.registry.DdcRegistryFacade;
import top.egon.cola.component.tianshu.api.client.DdcConfigClient;
import top.egon.cola.component.tianshu.api.client.DdcServiceRegistryClient;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties;
import top.egon.cola.component.rpc.config.EgonRpcAutoConfig;
import top.egon.cola.component.rpc.config.EgonRpcProperties;
import top.egon.cola.component.rpc.tianshu.autoconfigure.DdcRpcAutoConfiguration;
import top.egon.cola.component.rpc.tianshu.autoconfigure.DdcRpcProperties;
import top.egon.cola.component.rpc.tianshu.client.DdcRpcClientHandle;
import top.egon.cola.component.rpc.provider.lifecycle.RpcProviderLifecycle;
import top.egon.cola.component.rpc.provider.registration.RpcProviderRegistrationMode;
import top.egon.cola.component.rpc.provider.registration.RpcProviderRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Guards the shipped {@code application.yml} against placeholders that no property source
 * can resolve.
 *
 * <p>The Actuator info version is the load-bearing case: {@code info.app.version: ${sdk.version}}
 * only resolves because {@code application.yml} declares
 * {@code spring.config.import: classpath:META-INF/egon-cola-tianshu.properties}, and that file is
 * Maven-filtered in the starter module. Drop the import, rename the key, or lose the filtering
 * and the admin stops starting — with no other test in this module noticing, since they all
 * either override the property or use a {@code @WebMvcTest} slice.
 *
 * <p>Scope: this loads Boot's configuration machinery against the real YAML, not the full bean
 * graph — the admin's Redis-backed beans are mandatory and cannot start without a live Redis.
 * {@code Holder} is deliberately un-annotated so that component scanning cannot pick it up and
 * so that it is not a second {@code @SpringBootConfiguration} candidate for the other tests.
 */
@SpringBootTest(classes = DdcAdminContextSmokeTest.Holder.class)
@ActiveProfiles("test")
@Import({InfoContributorAutoConfiguration.class, InfoEndpointAutoConfiguration.class})
class DdcAdminContextSmokeTest {

    static class Holder {
    }

    @org.springframework.test.context.DynamicPropertySource
    static void requiredDeploymentFixture(org.springframework.test.context.DynamicPropertyRegistry values) {
        // Required deployment trust/scope values are explicit test input, never production fallback defaults.
        Map.ofEntries(
                Map.entry("TIANSHU_TLS_KEY_STORE", "classpath:test-only.p12"),
                Map.entry("TIANSHU_TLS_KEY_STORE_PASSWORD", "test-only-password"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_A_ISSUER", "https://idp.example/tenant-a"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_A_JWKS_URI", "https://idp.example/tenant-a/oauth2/jwks"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_A_REALM_KEY", "tenant-a"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_A_ID", "10"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_A_TRUST_VERSION", "1"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_B_ISSUER", "https://idp.example/tenant-b"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_B_JWKS_URI", "https://idp.example/tenant-b/oauth2/jwks"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_B_REALM_KEY", "tenant-b"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_B_ID", "20"),
                Map.entry("TIANQUAN_SHOUBING_TENANT_B_TRUST_VERSION", "1"),
                Map.entry("TIANQUAN_SHOUBING_PLATFORM_ISSUER", "https://idp.example/platform"),
                Map.entry("TIANQUAN_SHOUBING_PLATFORM_JWKS_URI", "https://idp.example/platform/oauth2/jwks"),
                Map.entry("TIANQUAN_SHOUBING_PLATFORM_TRUST_VERSION", "1"),
                Map.entry("TIANQUAN_SHOUBING_AUTHORITY_BASE_URI", "https://idp.example"),
                Map.entry("TIANSHU_IDENTITY_TENANT_A_REGISTRATION_ID", "tenant-a-authority"),
                Map.entry("TIANSHU_IDENTITY_TENANT_B_REGISTRATION_ID", "tenant-b-authority"),
                Map.entry("TIANSHU_IDENTITY_PLATFORM_REGISTRATION_ID", "platform-authority"),
                Map.entry("TIANSHU_RESOURCE_URI", "https://resource.example/tianshu"),
                Map.entry("TIANQUAN_SHOUBING_IDENTITY_RESOURCE_URI", "https://resource.example/identity"),
                Map.entry("TIANSHU_CATALOG_SCOPE_ID", "30"),
                Map.entry("TIANSHU_CATALOG_TENANT_ID", "10"),
                Map.entry("TIANSHU_CATALOG_NAMESPACE", "default"),
                Map.entry("DEPLOYMENT_ENV", "test")
        ).forEach((name, value) -> values.add(name, () -> value));
    }

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired
    private InfoEndpoint infoEndpoint;

    @Test
    void actuatorInfoVersionResolvesToAConcreteValue() {
        String version = environment.getProperty(
                "info.app.version"
        );

        assertThat(version)
                .as("Actuator info version must be supplied by the build, not left as a placeholder")
                .isNotBlank()
                .doesNotContain("${")
                .doesNotContain("@");
        assertThat(environment.getProperty("management.info.env.enabled", Boolean.class))
                .as("Actuator environment info contributor must expose info.app.version")
                .isTrue();
        assertThat(infoEndpoint.info())
                .containsEntry("app", Map.of(
                        "name", "stellar-enclosure-tianshu-admin",
                        "version", version
                ));
    }

    @Test
    void everyShippedPropertyResolves() {
        List<String> unresolved = new ArrayList<>();

        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)
                    || !source.getName().contains("application")) {
                continue;
            }
            for (String key : enumerable.getPropertyNames()) {
                try {
                    String value = environment.getProperty(key);
                    if (value != null && value.contains("${")) {
                        unresolved.add(key + " -> " + value);
                    }
                } catch (IllegalArgumentException ex) {
                    unresolved.add(key + " -> " + ex.getMessage());
                }
            }
        }

        assertThat(unresolved)
                .as("shipped configuration must not contain unresolvable placeholders")
                .isEmpty();
    }

    @Test
    void adminRpcServerStartsWithoutDiscoveringOrRegisteringItself() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DdcRpcAutoConfiguration.class,
                        EgonRpcAutoConfig.class
                ))
                .withUserConfiguration(AdminRpcTestConfiguration.class)
                .withPropertyValues(
                        "spring.application.name=stellar-enclosure-tianshu-admin",
                        "egon.cola.component.tianshu.enabled=false",
                        "egon.cola.component.tianshu.registry.enabled=false",
                        "egon.cola.component.tianshu.admin.security.local-dev=true",
                        "egon.cola.component.tianshu.admin.rpc.signature-enabled=false",
                        "egon.cola.component.rpc.enabled=true",
                        "egon.cola.component.rpc.provider.enabled=true",
                        "egon.cola.component.rpc.provider.port=0",
                        "egon.cola.component.rpc.provider.registration-mode=DISABLED",
                        "egon.cola.component.rpc.consumer.enabled=false",
                        "egon.cola.component.rpc.tls.development-plaintext=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RpcProviderLifecycle.class);
                    assertThat(context.getBean(RpcProviderLifecycle.class)
                            .boundPort()).isPositive();
                    assertThat(context).doesNotHaveBean(RpcProviderRegistry.class);
                    assertThat(context).doesNotHaveBean(DdcConfigClient.class);
                    assertThat(context).doesNotHaveBean(
                            DdcServiceRegistryClient.class);
                    assertThat(context).doesNotHaveBean(DdcRpcClientHandle.class);
                    assertThat(context.getEnvironment().getProperty(
                            "egon.cola.component.tianshu.rpc.target")).isNull();
                    EgonRpcProperties rpc = context.getBean(
                            EgonRpcProperties.class);
                    assertThat(rpc.getProvider().getRegistrationMode())
                            .isEqualTo(RpcProviderRegistrationMode.DISABLED);
                    assertThat(rpc.getConsumer().isEnabled()).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            DdcAdminProperties.class,
            DdcProperties.class,
            DdcRpcProperties.class,
            DdcTenantAdminProperties.class
    })
    @Import({DdcRpcSecurityConfiguration.class, DdcTenantRegistry.class,
            DdcTenantContextExecutor.class})
    static class AdminRpcTestConfiguration {

        @Bean
        DdcHmacCredentialRegistry ddcHmacCredentialRegistry(
                DdcAdminProperties properties) {
            return new DdcHmacCredentialRegistry(properties);
        }

        @Bean
        DdcConfigFacade ddcConfigFacade() {
            return mock(DdcConfigFacade.class);
        }

        @Bean
        DdcRegistryFacade ddcRegistryFacade() {
            return mock(DdcRegistryFacade.class);
        }

        @Bean
        DdcManagementFacade ddcManagementFacade() {
            return mock(DdcManagementFacade.class);
        }

        @Bean
        DdcResourceAdmissionRevocationService ddcResourceAdmissionRevocationService() {
            return mock(DdcResourceAdmissionRevocationService.class);
        }

        @Bean
        DdcConfigRpcProvider ddcConfigRpcProvider(
                DdcConfigFacade facade,
                DdcProperties ddcProperties,
                DdcRpcProperties rpcProperties) {
            return new DdcConfigRpcProvider(
                    facade, ddcProperties, rpcProperties);
        }

        @Bean
        DdcRegistryRpcProvider ddcRegistryRpcProvider(
                DdcRegistryFacade facade,
                DdcRpcProperties rpcProperties) {
            return new DdcRegistryRpcProvider(facade, rpcProperties);
        }

        @Bean
        DdcManagementRpcProvider ddcManagementRpcProvider(
                DdcManagementFacade facade,
                DdcResourceAdmissionRevocationService revocations,
                DdcProperties ddcProperties,
                DdcRpcProperties rpcProperties) {
            return new DdcManagementRpcProvider(
                    facade, revocations, ddcProperties, rpcProperties);
        }
    }
}
