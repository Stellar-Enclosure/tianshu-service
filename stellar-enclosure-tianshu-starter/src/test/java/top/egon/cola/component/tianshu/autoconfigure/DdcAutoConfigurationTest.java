package top.egon.cola.component.tianshu.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.egon.cola.component.tianshu.api.client.DdcConfigClient;
import top.egon.cola.component.tianshu.api.client.DdcServiceRegistryClient;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataLoader;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotCoordinator;
import top.egon.cola.component.tianshu.service.binding.DdcFieldBindingService;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DdcAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdcRedisAutoConfiguration.class, DdcAutoConfiguration.class));

    @Test
    void enabledConfigurationCannotFallBackToRetiredRuntimeWhenNativeDeliveryIsDisabled() {
        contextRunner.withBean(DdcConfigClient.class, () -> mock(DdcConfigClient.class))
                .withPropertyValues("egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.redis.enabled=false",
                        "egon.cola.component.tianshu.delivery.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("CONFIG_NATIVE_DELIVERY_REQUIRED");
                });
    }

    @Test
    void nativeDeliveryRequiresDeploymentBaselineBeforeCreatingTransport() {
        contextRunner.withPropertyValues("egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.delivery.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("CONFIG_DELIVERY_BASELINE_REQUIRED");
                });
    }

    @Test
    void automaticConfigurationsUseExplicitBeanWiringAndRetiredTypesAreAbsent() {
        assertThat(DdcAutoConfiguration.class.isAnnotationPresent(ComponentScan.class)).isFalse();
        assertThat(DdcRedisAutoConfiguration.class.isAnnotationPresent(ComponentScan.class)).isFalse();
        for (String type : List.of("autoconfigure.DdcRegistryAutoConfiguration",
                "autoconfigure.properties.DdcAckDeliveryProperties", "service.lifecycle.DdcRuntimeCoordinator",
                "service.lifecycle.DdcInstanceService", "service.lifecycle.DdcAckDelivery",
                "service.lifecycle.DdcInstanceIdentityFactory", "listener.config.DdcConfigChangeListener",
                "service.refresh.DdcRefreshService", "state.DdcLeaseSessionHolder",
                "listener.registry.DdcRegistrySubscriptionCoordinator", "service.registry.DdcRegistrySnapshotLoader",
                "state.DdcActiveRegistrationIndex")) {
            assertThatThrownBy(() -> Class.forName("top.egon.cola.component.tianshu." + type))
                    .as("retired runtime %s", type).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void noConfigurationRuntimeStartsWithoutExplicitOptIn() {
        contextRunner.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(DdcConfigSnapshotCoordinator.class).doesNotHaveBean(DdcFieldBindingService.class)
                .doesNotHaveBean(RedissonClient.class));
        contextRunner.withPropertyValues("egon.cola.component.tianshu.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(DdcConfigSnapshotCoordinator.class).doesNotHaveBean(DdcFieldBindingService.class));
    }

    @Test
    void nativeDeliveryWiresOnlyNativeTransportAndBootCannotOverrideItsReadinessGate(@TempDir Path temp) throws Exception {
        var sources = baseline(temp).getPropertySources();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DdcAutoConfiguration.class, DdcRedisAutoConfiguration.class,
                        ApplicationAvailabilityAutoConfiguration.class))
                .withInitializer(context -> sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source)))
                .withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> mock(IdpServiceOAuth2Client.class))
                .withPropertyValues("egon.cola.component.tianshu.enabled=true", "egon.cola.component.tianshu.redis.enabled=true",
                        "egon.cola.component.tianshu.registry.enabled=true", "egon.cola.component.tianshu.delivery.enabled=true",
                        "egon.cola.component.tianshu.delivery.state-directory=" + temp.resolve("state"),
                        "egon.cola.component.tianshu.delivery.pod-uid=pod-a",
                        "egon.cola.component.tianshu.delivery.registration-id=invoice-cfg",
                        "egon.cola.component.tianshu.delivery.app-id=invoice",
                        "egon.cola.component.tianshu.delivery.identity-tenant-id=orders-tenant",
                        "egon.cola.component.tianshu.delivery.expected-issuer=https://identity.example.test/tenants/orders",
                        "egon.cola.component.tianshu.delivery.canonical-audience=https://tianshu.example.test")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DdcConfigSnapshotCoordinator.class)
                            .doesNotHaveBean(DdcConfigClient.class).doesNotHaveBean(DdcServiceRegistryClient.class)
                            .doesNotHaveBean(RedissonClient.class).doesNotHaveBean("ddcAckDelivery")
                            .doesNotHaveBean("ddcLeaseSessionHolder").doesNotHaveBean("ddcRuntimeCoordinator");
                    var availability = context.getBean(ApplicationAvailability.class);
                    AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
                    AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
                    assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
                    assertThat(availability.getLivenessState()).isEqualTo(LivenessState.CORRECT);
                });
    }

    @Test
    void malformedDeliveryOptInCannotStartAConfigurationRuntime() {
        contextRunner.withPropertyValues("egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.delivery.enabled=invalid")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("CONFIG_NATIVE_DELIVERY_REQUIRED");
                });
    }

    @Test
    void nativeDeliveryCannotStartWithoutServiceOAuthClientOrRegistration(@TempDir Path temp) throws Exception {
        var sources = baseline(temp).getPropertySources();
        var runner = contextRunner.withInitializer(context -> sources.forEach(source ->
                        context.getEnvironment().getPropertySources().addLast(source)))
                .withPropertyValues("egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.delivery.enabled=true",
                        "egon.cola.component.tianshu.delivery.state-directory=" + temp.resolve("state"),
                        "egon.cola.component.tianshu.delivery.pod-uid=pod-a",
                        "egon.cola.component.tianshu.delivery.app-id=invoice",
                        "egon.cola.component.tianshu.delivery.identity-tenant-id=orders-tenant",
                        "egon.cola.component.tianshu.delivery.expected-issuer=https://identity.example.test/tenants/orders",
                        "egon.cola.component.tianshu.delivery.canonical-audience=https://tianshu.example.test");
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("idpServiceOAuth2Client");
        });
        runner.withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class,
                        () -> mock(IdpServiceOAuth2Client.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("registrationId");
                });
    }

    private org.springframework.boot.context.config.ConfigData baseline(Path temp) throws Exception {
        var path = temp.resolve("baseline.json");
        var scope = new DdcConfigScopeVO("19", "orders", "test", "invoice", "application.yml");
        var target = new DdcConfigBaselineVO.TargetBindingVO("target", "1", "test-cluster", "runtime",
                "Deployment", "invoice", "workload-uid", "invoice");
        String placeholder = "sha256:" + "a".repeat(64);
        var builder = DdcConfigBaselineVO.builder().schemaVersion("1").scope(scope)
                .source(new DdcConfigBaselineVO.SourceVO("81", "1")).enabled(true).restartValues(Map.of())
                .hotSeed(Map.of()).startupDependencies(Map.of()).secretReferences(Map.of()).rules(List.of())
                .targetBindings(List.of(target)).restartDigest(placeholder).contentSha256(placeholder);
        builder.restartDigest(DdcConfigCanonicalJson.restartDigest(builder.build()));
        var baseline = builder.contentSha256(DdcConfigCanonicalJson.contentSha256(builder.build())).build();
        Jackson2ObjectMapperBuilder.json().build().writeValue(path.toFile(), baseline);
        var expected = new DdcConfigDataResource.ExpectedTargetVO("target", 1, "test-cluster", "runtime", "workload-uid", "invoice");
        return new DdcConfigDataLoader().load(mock(ConfigDataLoaderContext.class),
                new DdcConfigDataResource(path, scope, expected));

    }

}
