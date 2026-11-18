package top.egon.cola.component.tianshu.autoconfigure;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.tianshu.api.client.DdcConfigClient;
import top.egon.cola.component.tianshu.api.client.DdcServiceRegistryClient;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataLoader;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotCoordinator;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcTenantProperties;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;
import top.egon.cola.component.tianshu.format.DdcYamlConfigFormatStrategy;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState;
import top.egon.cola.component.tianshu.api.refresh.DdcConfigApplierRegistry;
import top.egon.cola.component.tianshu.service.lifecycle.DdcAckDelivery;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcAckDeliveryProperties;
import top.egon.cola.component.tianshu.service.refresh.DefaultDdcConfigApplierRegistry;
import top.egon.cola.component.tianshu.service.binding.DdcFieldBindingService;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DdcAutoConfigurationTest {

    private final ValidationUtils validationUtils = new ValidationUtils(
            Validation.buildDefaultValidatorFactory().getValidator());

    @BeforeAll
    static void bindTheProcessWideEngine() {
        SnowflakeIdGenerator.initialize(0L, Duration.ofMillis(5));
    }

    @Test
    void automaticConfigurationsUseExplicitBeanWiring() {
        assertThat(DdcAutoConfiguration.class.isAnnotationPresent(ComponentScan.class))
                .isFalse();
        assertThat(DdcRedisAutoConfiguration.class.isAnnotationPresent(ComponentScan.class))
                .isFalse();
        assertThat(DdcRegistryAutoConfiguration.class.isAnnotationPresent(ComponentScan.class))
                .isFalse();

        assertThatThrownBy(() -> Class.forName(
                "top.egon.cola.component.tianshu.autoconfigure.DdcAutoConfig"
        )).isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "top.egon.cola.component.tianshu.autoconfigure.DdcRedisAutoConfig"
        )).isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "top.egon.cola.component.tianshu.autoconfigure.DdcRegistryAutoConfig"
        )).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void nativeDeliveryWiresOnlyNativeTransportAndBootCannotOverrideItsReadinessGate(@TempDir Path temp) throws Exception {
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
        var sources = new DdcConfigDataLoader().load(mock(ConfigDataLoaderContext.class),
                new DdcConfigDataResource(path, scope, expected)).getPropertySources();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DdcAutoConfiguration.class, DdcRedisAutoConfiguration.class,
                        DdcRegistryAutoConfiguration.class, ApplicationAvailabilityAutoConfiguration.class))
                .withInitializer(context -> sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source)))
                .withBean("egonColaValidationUtils", ValidationUtils.class, () -> validationUtils)
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
                            .doesNotHaveBean(RedissonClient.class).doesNotHaveBean(DdcAckDelivery.class)
                            .doesNotHaveBean("ddcLeaseSessionHolder").doesNotHaveBean("ddcRuntimeCoordinator");
                    var availability = context.getBean(ApplicationAvailability.class);
                    AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
                    AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
                    assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
                    assertThat(availability.getLivenessState()).isEqualTo(LivenessState.CORRECT);
                });
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DdcRedisAutoConfiguration.class,
                    DdcAutoConfiguration.class
            ))
            .withBean("egonColaValidationUtils", ValidationUtils.class, () -> validationUtils)
            .withBean(
                    IdpServiceOAuth2Client.class,
                    () -> mock(IdpServiceOAuth2Client.class)
            );

    private final ApplicationContextRunner redisContextRunner =
            new ApplicationContextRunner(NonStartingApplicationContext::new)
                    .withConfiguration(AutoConfigurations.of(
                            DdcRedisAutoConfiguration.class,
                            DdcAutoConfiguration.class
                    ))
                    .withBean("egonColaValidationUtils", ValidationUtils.class,
                            () -> validationUtils)
                    .withBean(
                            DdcConfigClient.class,
                            () -> mock(DdcConfigClient.class)
                    )
                    .withBean(
                            IdpServiceOAuth2Client.class,
                            () -> mock(IdpServiceOAuth2Client.class)
                    )
                    .withInitializer(context -> context.getEnvironment()
                            .getPropertySources().addFirst(
                                    new DdcYamlConfigFormatStrategy()
                                            .empty("application.yml")
                            ));

    @Test
    void doesNotCreateBeansWhenDisabled() {
        contextRunner.withPropertyValues("egon.cola.component.tianshu.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DdcLocalConfigState.class);
                    assertThat(context).doesNotHaveBean(DdcFieldBindingService.class);
                });
    }

    @Test
    void doesNotCreateBeansWhenEnableFlagIsMissing() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(DdcConfigClient.class));
    }

    @Test
    void warnsWhenRemoteLifecycleIsDisabled(CapturedOutput output) {
        contextRunner.withBean(
                        DdcConfigClient.class,
                        () -> mock(DdcConfigClient.class)
                )
                .withPropertyValues(
                        "egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.redis.enabled=false"
                )
                .run(context -> assertThat(output).contains(
                        "Tianshu remote lifecycle is disabled because "
                                + "egon.cola.component.tianshu.redis.enabled=false; "
                                + "no registration, pull, subscription, heartbeat, or ACK will run"
                ));
    }

    @Test
    void createsCoreBeansWhenEnabled() {
        AtomicReference<DdcAckDelivery> delivery = new AtomicReference<>();
        contextRunner.withBean(
                        DdcConfigClient.class,
                        () -> mock(DdcConfigClient.class)
                )
                .withPropertyValues(
                        "egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.redis.enabled=false",
                        "egon.cola.component.tianshu.app-code=demo",
                        "egon.cola.component.tianshu.env=dev",
                        "egon.cola.component.tianshu.namespace=default")
                .run(context -> {
                    assertThat(context).hasSingleBean(DdcProperties.class);
                    assertThat(context).hasSingleBean(DdcAckDeliveryProperties.class);
                    assertThat(context).hasSingleBean(DdcAckDelivery.class);
                    assertThat(context).hasSingleBean(DdcFieldBindingService.class);
                    assertThat(context).hasSingleBean(DdcLocalConfigState.class);
                    assertThat(context).hasSingleBean(DdcConfigApplierRegistry.class);
                    assertThat(context).hasSingleBean(DdcConfigClient.class);
                    assertThat(context.getBean(DdcAckDelivery.class).isRunning())
                            .isTrue();
                    delivery.set(context.getBean(DdcAckDelivery.class));
                    assertThat(context.getBean(DefaultDdcConfigApplierRegistry.class).frozen()).isTrue();
                });
        assertThat(delivery.get().isRunning()).isFalse();
        assertThat(delivery.get().isWorkerTerminated()).isTrue();
    }

    @Test
    void createsDedicatedRedisClientWhenApplicationClientExists() throws Exception {
        RedissonClient applicationClient = mock(RedissonClient.class);
        RedissonClient dedicatedClient = dedicatedClient();
        AtomicReference<Config> configuredRedis = new AtomicReference<>();

        try (MockedStatic<Redisson> redisson = mockStatic(Redisson.class)) {
            redisson.when(() -> Redisson.create(org.mockito.ArgumentMatchers.any(Config.class)))
                    .thenAnswer(invocation -> {
                        configuredRedis.set(invocation.getArgument(0));
                        return dedicatedClient;
                    });
            redisContextRunner.withBean(
                            "applicationRedissonClient",
                            RedissonClient.class,
                            () -> applicationClient
                    )
                    .withPropertyValues(
                            "egon.cola.component.tianshu.enabled=true",
                            "egon.cola.component.tianshu.redis.enabled=true",
                            "egon.cola.component.tianshu.tenant-id=101",
                            "egon.cola.component.tianshu.redis.username=tenant-user"
                    )
                    .run(context -> {
                        assertThat(context.getBean("ddcRedissonClient"))
                                .isSameAs(dedicatedClient);
                        assertThat(context.getBean("applicationRedissonClient"))
                                .isSameAs(applicationClient);
                        assertThat(configuredRedis.get().toYAML())
                                .contains("username: \"tenant-user\"");
                    });
        }
    }

    @Test
    void refusesToCreateRedisClientWithoutTenantOrTenantAclUsername() {
        redisContextRunner.withPropertyValues(
                        "egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.redis.enabled=true",
                        "egon.cola.component.tianshu.redis.username=tenant-user")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("egon.cola.component.tianshu.tenant-id must be a positive Long");
                });

        redisContextRunner.withPropertyValues(
                        "egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.redis.enabled=true",
                        "egon.cola.component.tianshu.tenant-id=101")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("egon.cola.component.tianshu.redis.username is required when Redis is enabled");
                });
    }

    @Test
    void retainsUserProvidedDedicatedRedisClient() {
        RedissonClient dedicatedClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(dedicatedClient.getTopic(anyString())).thenReturn(topic);

        redisContextRunner.withBean(
                        "ddcRedissonClient",
                        RedissonClient.class,
                        () -> dedicatedClient
                )
                .withPropertyValues(
                        "egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.redis.enabled=true",
                        "egon.cola.component.tianshu.tenant-id=101",
                        "egon.cola.component.tianshu.redis.username=tenant-user"
                )
                .run(context -> {
                    assertThat(context.getBean("ddcRedissonClient"))
                            .isSameAs(dedicatedClient);
                });
    }

    @Test
    void retainsUserProvidedAdminClient() {
        DdcConfigClient client = mock(DdcConfigClient.class);

        contextRunner
                .withBean(DdcConfigClient.class, () -> client)
                .withPropertyValues(
                        "egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.redis.enabled=false"
                )
                .run(context -> assertThat(context.getBean(DdcConfigClient.class))
                        .isSameAs(client));
    }

    @Test
    void enabledRuntimeFailsFastWithoutConfigPort() {
        contextRunner.withPropertyValues(
                        "egon.cola.component.tianshu.enabled=true",
                        "egon.cola.component.tianshu.tenant-id=42",
                        "egon.cola.component.tianshu.redis.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessage(
                                    "Required DdcConfigClient Port is missing; add "
                                            + "top.egon:stellar-enclosure-component-rpc-tianshu-adapter"
                            );
                });
    }

    @Test
    void subscribesOnlyToPhysicalV4TenantTopic() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        DdcProperties properties = new DdcProperties();
        properties.setBizCode("retail");
        properties.setEnv("dev");
        properties.setAppCode("order");
        properties.setNamespace("namespace-a");
        when(client.getTopic(DdcRedisKeys.topic(101L, "retail", "dev", "order")))
                .thenReturn(topic);

        DdcTenantProperties tenantProperties = new DdcTenantProperties();
        tenantProperties.setTenantId(101L);
        RTopic result = new DdcAutoConfiguration().ddcRedisTopic(client, properties, tenantProperties);

        assertThat(result).isSameAs(topic);
        verify(client).getTopic(DdcRedisKeys.topic(101L, "retail", "dev", "order"));
    }

    private RedissonClient dedicatedClient() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(client.getTopic(anyString())).thenReturn(topic);
        return client;
    }

    private static final class NonStartingApplicationContext
            extends AnnotationConfigApplicationContext {

        @Override
        protected void finishRefresh() {
        }
    }
}
