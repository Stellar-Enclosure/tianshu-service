package top.egon.cola.component.tianshu.autoconfigure;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.ConfigurableEnvironment;

import top.egon.cola.component.tianshu.service.binding.DdcBeanPostProcessor;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataLoader;
import top.egon.cola.component.tianshu.configdata.DdcConfigDataResource.BaselineMetadataVO;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotCoordinator;
import top.egon.cola.component.tianshu.service.config.DdcConfigSnapshotHttpClient;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore;
import top.egon.cola.component.tianshu.format.DdcConfigFormatStrategyRegistry;
import top.egon.cola.component.tianshu.service.refresh.DdcConfigurationPropertiesRebinder;
import top.egon.cola.component.tianshu.service.refresh.DdcYamlConfigApplier;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState;
import top.egon.cola.component.tianshu.service.binding.DdcValueBindingRegistry;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcTenantProperties;
import top.egon.cola.component.tianshu.api.refresh.DdcConfigApplierRegistry;
import top.egon.cola.component.tianshu.service.binding.DdcFieldBindingService;
import top.egon.cola.component.tianshu.service.refresh.DefaultDdcConfigApplierRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * 装配本地字段绑定与受管 HTTP 快照交付。 Configures local binding and managed HTTP snapshot delivery.
 */
@AutoConfiguration(before = ApplicationAvailabilityAutoConfiguration.class)
@EnableConfigurationProperties({
        DdcProperties.class,
        DdcTenantProperties.class
})
@ConditionalOnProperty(prefix = "egon.cola.component.tianshu", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DdcAutoConfiguration {
    public static final String DELIVERY_PREFIX = "egon.cola.component.tianshu.delivery";
    private static final String PROPERTIES_BEAN = "egon.cola.component.tianshu-top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties";

    /**
     * 创建可刷新 {@code @DdcValue} 字段注册表。 Creates the refreshable {@code @DdcValue} field registry.
     *
     * @return 字段绑定注册表。 field-binding registry
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static DdcValueBindingRegistry ddcValueBindingRegistry() {
        return new DdcValueBindingRegistry();
    }

    /**
     * 创建通过 Spring 原生依赖解析管线刷新 {@code @DdcValue} 字段的服务。
     * Creates the service that refreshes {@code @DdcValue} fields through Spring's native dependency-resolution pipeline.
     *
     * @param registry    字段绑定注册表。 field-binding registry
     * @param beanFactory Spring BeanFactory。 Spring bean factory
     * @return 字段绑定服务。 field-binding service
    */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static DdcFieldBindingService ddcFieldBindingService(
            DdcValueBindingRegistry registry,
            ConfigurableListableBeanFactory beanFactory) {
        return new DdcFieldBindingService(registry, beanFactory);
    }

    /**
     * 创建可由应用替换的进程内配置状态存储。
     * Creates the replaceable in-process configuration state store.
     *
     * @return 本地配置状态。 local configuration state
     */
    @Bean(name = "ddcLocalConfigState")
    @ConditionalOnMissingBean
    public DdcLocalConfigState ddcLocalConfigState(
            @Qualifier("egon.cola.component.tianshu-top.egon.cola.component.tianshu.autoconfigure.properties.DdcTenantProperties")
            DdcTenantProperties tenantProperties) {
        return new DdcLocalConfigState(tenantProperties.getTenantId());
    }

    /**
     * 创建当前版本共享的 YAML-only 配置格式策略注册表。
     * Creates the shared YAML-only configuration-format strategy registry for the current version.
     *
     * @return 配置格式策略注册表; configuration-format strategy registry
     */
    @Bean
    @ConditionalOnMissingBean
    public DdcConfigFormatStrategyRegistry ddcConfigFormatStrategyRegistry() {
        return DdcConfigFormatStrategyRegistry.defaults();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanFactoryPostProcessor ddcNativeDeliveryRequirement(ConfigurableEnvironment environment) {
        return beanFactory -> {
            if (!"true".equalsIgnoreCase(environment.getProperty(DELIVERY_PREFIX + ".enabled"))) {
                throw new IllegalStateException("CONFIG_NATIVE_DELIVERY_REQUIRED");
            }
        };
    }

    /**
     * 创建默认配置应用器注册表。 Creates the default configuration-applier registry.
     *
     * @return 默认应用器注册表。 default applier registry
     */
    @Bean
    public DefaultDdcConfigApplierRegistry ddcConfigApplierRegistry() {
        return new DefaultDdcConfigApplierRegistry(
                (key, value, version) -> {
                }
        );
    }

    /**
     * 创建在所有单例完成装配后冻结应用器注册表的回调。 Creates a callback that freezes the applier registry after singleton initialization.
     *
     * @param registry 待冻结的注册表。 registry to freeze
     * @return 单例初始化完成回调。 singleton-initialization callback
     */
    @Bean
    public SmartInitializingSingleton ddcConfigApplierRegistryFreezer(
            DefaultDdcConfigApplierRegistry registry) {
        return registry::freeze;
    }

    /**
     * 创建配置属性 Bean 的重新绑定器。 Creates the rebinder for configuration-properties beans.
     *
     * @param applicationContext   应用上下文。 application context
     * @param bindingPostProcessor 配置属性绑定后处理器。 configuration-properties binding post-processor
     * @param environment          Spring 环境。 Spring environment
     * @return 配置属性重新绑定器。 configuration-properties rebinder
     */
    @Bean
    public DdcConfigurationPropertiesRebinder
    ddcConfigurationPropertiesRebinder(
            ApplicationContext applicationContext,
            ConfigurationPropertiesBindingPostProcessor bindingPostProcessor,
            ConfigurableEnvironment environment) {
        return new DdcConfigurationPropertiesRebinder(
                applicationContext,
                bindingPostProcessor,
                environment
        );
    }

    @Bean(name = "ddcConfigDeliveryBaseline")
    @ConditionalOnProperty(prefix = DELIVERY_PREFIX, name = "enabled", havingValue = "true")
    BaselineMetadataVO ddcConfigDeliveryBaseline(ConfigurableEnvironment environment) {
        var baseline = DdcConfigDataLoader.baselineMetadataOrNull(environment);
        if (baseline == null) {
            throw new IllegalStateException("CONFIG_DELIVERY_BASELINE_REQUIRED");
        }
        return baseline;
    }

    @Bean(name = "ddcConfigDeliveryApplier")
    @ConditionalOnProperty(prefix = DELIVERY_PREFIX, name = "enabled", havingValue = "true")
    DdcYamlConfigApplier ddcConfigDeliveryApplier(ConfigurableEnvironment environment,
            @Qualifier("ddcConfigApplierRegistry") DdcConfigApplierRegistry registry,
            @Qualifier("ddcFieldBindingService") DdcFieldBindingService fields,
            @Qualifier("ddcConfigurationPropertiesRebinder") DdcConfigurationPropertiesRebinder rebinder,
            ApplicationEventPublisher publisher, @Qualifier(PROPERTIES_BEAN) DdcProperties properties,
            @Qualifier("ddcConfigFormatStrategyRegistry") DdcConfigFormatStrategyRegistry strategies) {
        return new DdcYamlConfigApplier(environment, registry, fields, rebinder, publisher, properties.getMaxConfigBytes(), strategies);
    }

    @Bean(name = "ddcConfigDeliveryLkgStore")
    @ConditionalOnProperty(prefix = DELIVERY_PREFIX, name = "enabled", havingValue = "true")
    DdcConfigLkgStore ddcConfigDeliveryLkgStore(@Qualifier(PROPERTIES_BEAN) DdcProperties properties) {
        return new DdcConfigLkgStore(Objects.requireNonNull(properties.getDelivery().getStateDirectory(), "CONFIG_STATE_DIRECTORY_REQUIRED"));
    }

    @Bean(name = "ddcConfigSnapshotHttpClient", destroyMethod = "close")
    @ConditionalOnProperty(prefix = DELIVERY_PREFIX, name = "enabled", havingValue = "true")
    DdcConfigSnapshotHttpClient ddcConfigSnapshotHttpClient(@Qualifier(PROPERTIES_BEAN) DdcProperties properties,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth) {
        var delivery = properties.getDelivery();
        String issuer = Objects.requireNonNull(delivery.getExpectedIssuer(), "CONFIG_DELIVERY_ISSUER_REQUIRED").toASCIIString();
        var read = configTokenRequest(delivery, "tianshu:config:read");
        var receipt = configTokenRequest(delivery, "tianshu:config:receipt:write");
        return new DdcConfigSnapshotHttpClient(delivery.getServiceOrigin(), delivery.getWorkloadTokenFile(),
                oauth, read, receipt, issuer, delivery.getRequestTimeout());
    }

    private static IdpServiceTokenRequest configTokenRequest(DdcProperties.Delivery delivery, String scope) {
        return new IdpServiceTokenRequest(delivery.getRegistrationId(), delivery.getAppId(), delivery.getCanonicalAudience(),
                ServiceTokenContext.TENANT, delivery.getIdentityTenantId(), Set.of(scope));
    }

    @Bean(name = "ddcConfigSnapshotCoordinator", destroyMethod = "close")
    @ConditionalOnProperty(prefix = DELIVERY_PREFIX, name = "enabled", havingValue = "true")
    DdcConfigSnapshotCoordinator ddcConfigSnapshotCoordinator(
            @Qualifier("ddcConfigDeliveryBaseline") BaselineMetadataVO baseline,
            @Qualifier("ddcConfigSnapshotHttpClient") DdcConfigSnapshotHttpClient client,
            @Qualifier("ddcConfigDeliveryLkgStore") DdcConfigLkgStore store,
            @Qualifier("ddcConfigDeliveryApplier") DdcYamlConfigApplier applier,
            @Qualifier("ddcLocalConfigState") DdcLocalConfigState local, ApplicationEventPublisher publisher,
            @Qualifier(PROPERTIES_BEAN) DdcProperties properties) {
        var delivery = properties.getDelivery();
        var policy = new DdcConfigSnapshotCoordinator.PolicyVO(delivery.getPodUid(), delivery.getMaxStale(),
                delivery.getPollInterval(), delivery.isAllowOfflineStart(), delivery.getBaselineValidUntil());
        return new DdcConfigSnapshotCoordinator(baseline, policy, client, store, applier, local, publisher, Clock.systemUTC());
    }

    @Bean(name = "ddcDeliveryApplicationAvailability")
    @Primary
    @ConditionalOnProperty(prefix = DELIVERY_PREFIX, name = "enabled", havingValue = "true")
    ApplicationAvailabilityBean ddcDeliveryApplicationAvailability(
            @Qualifier("ddcConfigSnapshotCoordinator") DdcConfigSnapshotCoordinator coordinator) {
        return new ApplicationAvailabilityBean() {
            @Override
            public <S extends AvailabilityState> S getState(Class<S> stateType) {
                if (stateType == ReadinessState.class && !coordinator.ready()) {
                    return stateType.cast(ReadinessState.REFUSING_TRAFFIC);
                }
                return super.getState(stateType);
            }
        };
    }

    /**
     * 创建静态 Bean 后处理器，在 Bean 初始化期间发现并绑定 Tianshu 字段。 Creates the static bean post-processor that discovers and binds Tianshu fields during bean initialization.
     *
     * @param fieldBindingService 字段绑定服务。 field-binding service
     * @return Tianshu Bean 后处理器。 Tianshu bean post-processor
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static DdcBeanPostProcessor ddcBeanPostProcessor(DdcFieldBindingService fieldBindingService) {
        return new DdcBeanPostProcessor(fieldBindingService);
    }

}
