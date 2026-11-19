package top.egon.cola.component.tianshu.admin.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.tianshu.admin.DynamicConfigCenterAdminApplication;
import top.egon.cola.component.tianshu.admin.config.DdcConfigPublicationPolicyConfiguration.RegisteredPolicies;
import top.egon.cola.component.tianshu.admin.config.DdcConfigPublicationPolicyConfiguration.RegisteredTargetVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The two explicit worker commands must never inherit the broad Admin component scan. */
class DdcConfigWorkerApplicationTest {
    private static final String CONFIGURATION = "top.egon.cola.component.tianshu.admin.config.DdcConfigWorkerApplicationConfiguration";
    private static final String PREFIX = "egon.cola.component.tianshu.admin.";
    @TempDir Path scratch;

    @Test
    void publisherUsesAnExplicitNonWebRootWithoutTheAdminScan() throws Exception {
        assertWorkerRoot("config-publisher", "PublisherApplication");
    }

    @Test
    void observerUsesADifferentExplicitNonWebRootWithoutTheAdminScan() throws Exception {
        assertWorkerRoot("config-observer", "ObserverApplication");
    }

    @Test
    void anUnknownWorkerCommandCannotFallBackToTheAdminApplication() throws Exception {
        Class<?> configuration = Class.forName(CONFIGURATION);
        var factory = configuration.getMethod("application", String.class);
        assertThatThrownBy(() -> {
            try { factory.invoke(null, "config-obsever"); }
            catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
        }).isInstanceOf(IllegalArgumentException.class).hasMessage("TIANSHU_CONFIG_WORKER_MODE_INVALID");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void observerHardLimitsPrecedeEnvironmentBindingAndCannotBeEnabledByDeploymentOverrides() throws Exception {
        var application = (SpringApplication) Class.forName(CONFIGURATION).getMethod("application", String.class)
                .invoke(null, "config-observer");
        var environment = new MockEnvironment().withProperty("spring.main.web-application-type", "servlet")
                .withProperty("spring.main.keep-alive", "false")
                .withProperty("spring.main.allow-bean-definition-overriding", "true")
                .withProperty("egon.cola.component.transactional-outbox.polling.enabled", "true")
                .withProperty("egon.cola.component.transactional-outbox.cleanup.enabled", "true")
                .withProperty("egon.cola.component.transactional-outbox.retry.recover-exhausted", "true")
                .withProperty("egon.cola.component.rpc.enabled", "true")
                .withProperty("egon.cola.component.tianshu.admin.config-delivery.enabled", "true");
        var listener = application.getListeners().stream().filter(value -> value.getClass().getName().endsWith("$WorkerBoundaryListener"))
                .findFirst().orElseThrow();
        assertThat(((Ordered) listener).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        ((ApplicationListener) listener).onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), application, new String[0], environment));
        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(environment.getProperty("spring.main.keep-alive", Boolean.class)).isTrue();
        for (String key : java.util.List.of("spring.main.allow-bean-definition-overriding",
                "egon.cola.component.transactional-outbox.polling.enabled", "egon.cola.component.transactional-outbox.cleanup.enabled",
                "egon.cola.component.transactional-outbox.retry.recover-exhausted", "egon.cola.component.rpc.enabled",
                "egon.cola.component.tianshu.admin.config-delivery.enabled")) {
            assertThat(environment.getProperty(key, Boolean.class)).as(key).isFalse();
        }
    }

    @Test
    void workerAutoConfigurationIsAnExactWhitelistWithoutWebSecurityRedisDdcOrRpcDiscovery() throws Exception {
        Class<?> persistence = Class.forName(CONFIGURATION + "$PersistenceApplication");
        var imported = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(persistence, ImportAutoConfiguration.class);
        assertThat(imported).isNotNull();
        assertThat(imported.classes()).extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("Jackson2AutoConfiguration", "ValidationAutoConfiguration", "AopAutoConfiguration",
                        "IdGeneratorAutoConfiguration", "EgonColaMybatisPlusAutoConfiguration", "EgonColaShardingAutoConfiguration",
                        "MybatisPlusInnerInterceptorAutoConfiguration", "MybatisPlusAutoConfiguration",
                        "DataSourceTransactionManagerAutoConfiguration", "TransactionAutoConfiguration",
                        "TransactionalOutboxAutoConfiguration", "OutboxStateMachineAutoConfiguration", "OutboxMybatisPlusAutoConfiguration");
    }

    @Test
    void theOrdinaryAdminScanCannotDiscoverEitherWorkerRoot() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        assertThat(scanner.findCandidateComponents("top.egon.cola.component.tianshu.admin.config"))
                .noneMatch(bean -> bean.getBeanClassName().startsWith(CONFIGURATION));
    }

    @Test
    void mixedOrMisplacedWorkerModesFailBeforeBootingAnyApplication() {
        assertThatThrownBy(() -> DynamicConfigCenterAdminApplication.main(new String[]{"--config-publisher", "--config-observer"}))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("TIANSHU_CONFIG_WORKER_MODE_INVALID");
        assertThatThrownBy(() -> DynamicConfigCenterAdminApplication.main(new String[]{"--spring.profiles.active=test", "--config-observer"}))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("TIANSHU_CONFIG_WORKER_MODE_INVALID");
    }

    @Test
    void roleCredentialsCannotFallBackToTheVerifierAndMixedClusterPoliciesFailClosed() throws Throwable {
        settingsEnvironment();
        var environment = new MockEnvironment().withProperty(PREFIX + "config-publisher.cluster-id", "cluster-a")
                .withProperty(PREFIX + "config-publisher.api-server", "https://127.0.0.1:44443")
                .withProperty(PREFIX + "config-delivery.api-ca-file", scratch.resolve("ca.crt").toString())
                .withProperty(PREFIX + "config-delivery.api-token-file", scratch.resolve("token").toString());
        assertThatThrownBy(() -> settings(environment, policies("cluster-a")))
                .isInstanceOf(IllegalStateException.class).hasMessage("TIANSHU_CONFIG_WORKER_PROPERTY_REQUIRED");
        assertThatThrownBy(() -> settings(settingsEnvironment(), policies("cluster-b")))
                .isInstanceOf(IllegalStateException.class).hasMessage("TIANSHU_CONFIG_WORKER_MIXED_CLUSTER_OR_WORKLOAD");
        assertThatThrownBy(() -> settings(settingsEnvironment(), new RegisteredPolicies(Map.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessage("TIANSHU_CONFIG_WORKER_SCOPE_INVALID");
    }

    @Test
    void staticIdsRequireExplicitLocalModeAndTheActualBoundPhysicalPrimaryMustBeLoopback() throws Throwable {
        var environment = settingsEnvironment();
        environment.setActiveProfiles("local");
        environment.withProperty(PREFIX + "config-worker.test-static-machine-id-enabled", "true")
                .withProperty("egon.cola.component.id.machine-id", "17");
        physical(environment, "jdbc:postgresql://127.0.0.1:45432/isolated_worker", "PRIMARY");
        Object settings = settings(environment, policies("cluster-a"));
        identityMode(environment, settings);
        // A harmless-looking Spring alias must not hide the real production physical datasource.
        environment.withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:45432/isolated_worker");
        physical(environment, "jdbc:postgresql://postgres.internal:5432/production", "PRIMARY");
        assertThatThrownBy(() -> identityMode(environment, settings)).hasMessage("TIANSHU_CONFIG_WORKER_TEST_STATIC_ID_FORBIDDEN");
        physical(environment, "jdbc:postgresql://127.0.0.1:45432/isolated_worker", "PRIMARY");
        environment.setActiveProfiles("production");
        assertThatThrownBy(() -> identityMode(environment, settings)).hasMessage("TIANSHU_CONFIG_WORKER_TEST_STATIC_ID_FORBIDDEN");
        environment.setActiveProfiles("local");
        environment.withProperty(PREFIX + "config-publisher.api-server", "https://kubernetes.default.svc");
        Object nonLoopbackApi = settings(environment, policies("cluster-a"));
        assertThatThrownBy(() -> identityMode(environment, nonLoopbackApi)).hasMessage("TIANSHU_CONFIG_WORKER_TEST_STATIC_ID_FORBIDDEN");
    }

    @Test
    void dynamicIdentityRequiresAnExplicitScopeAndNeverSilentlyUsesAFixedSlot() throws Throwable {
        var environment = settingsEnvironment();
        Object settings = settings(environment, policies("cluster-a"));
        assertThatThrownBy(() -> identityMode(environment, settings)).hasMessage("TIANSHU_CONFIG_WORKER_PROPERTY_REQUIRED");
        environment.withProperty("egon.cola.component.id.redis.env", "staging")
                .withProperty("egon.cola.component.id.redis.app", "tianshu-observer");
        identityMode(environment, settings);
        environment.withProperty("egon.cola.component.id.machine-id", "17");
        assertThatThrownBy(() -> identityMode(environment, settings)).hasMessage("TIANSHU_CONFIG_WORKER_DYNAMIC_ID_REQUIRED");
    }

    @Test
    void jacksonAliasKeepsExactlyOneBootManagedMapperAndItsConfiguredModules() throws Exception {
        jacksonRunner().withPropertyValues("spring.jackson2.serialization.indent-output=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ObjectMapper.class);
            var mapper = context.getBean("jacksonObjectMapper", ObjectMapper.class);
            assertThat(mapper).isSameAs(context.getBean("jackson2ObjectMapper"));
            assertThat(mapper.writeValueAsString(java.time.Instant.parse("2026-11-18T22:00:00Z")))
                    .isEqualTo("\"2026-11-18T22:00:00Z\"");
        });
    }

    @Test
    void jacksonAliasFailsClosedForAmbiguousMappersOrAnOccupiedContractName() throws Exception {
        jacksonRunner().withBean("mapperA", ObjectMapper.class, ObjectMapper::new)
                .withBean("mapperB", ObjectMapper.class, ObjectMapper::new).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("TIANSHU_CONFIG_WORKER_JSON_MAPPER_AMBIGUOUS");
                });
        jacksonRunner().withBean("jacksonObjectMapper", String.class, () -> "occupied").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("TIANSHU_CONFIG_WORKER_JSON_MAPPER_NAME_CONFLICT");
        });
    }

    private static ApplicationContextRunner jacksonRunner() throws Exception {
        var method = Class.forName(CONFIGURATION + "$PersistenceApplication").getDeclaredMethod("managedJacksonMapperAliases");
        method.setAccessible(true);
        var aliases = (BeanFactoryPostProcessor) method.invoke(null);
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(Jackson2AutoConfiguration.class))
                .withInitializer(context -> context.addBeanFactoryPostProcessor(aliases));
    }

    private MockEnvironment settingsEnvironment() throws Exception {
        Files.writeString(scratch.resolve("ca.crt"), "fixture file, no client is created by these value-boundary tests");
        Files.writeString(scratch.resolve("token"), "fixture-opaque-token");
        return new MockEnvironment().withProperty(PREFIX + "config-publisher.cluster-id", "cluster-a")
                .withProperty(PREFIX + "config-publisher.api-server", "https://127.0.0.1:44443")
                .withProperty(PREFIX + "config-publisher.api-ca-file", scratch.resolve("ca.crt").toString())
                .withProperty(PREFIX + "config-publisher.api-token-file", scratch.resolve("token").toString());
    }

    private static RegisteredPolicies policies(String cluster) {
        return new RegisteredPolicies(Map.of(), List.of(new RegisteredTargetVO(
                new DdcConfigScopeVO("1", "biz", "staging", "app", "application.yml"),
                new TargetBindingVO("binding-a", "1", cluster, "apps-prod", "Deployment", "reader", "workload-uid", "app"))));
    }

    private static void physical(MockEnvironment environment, String jdbc, String role) {
        String prefix = "egon.cola.component.mybatis-plus.sharding.data-sources[0].";
        environment.withProperty(prefix + "name", "control").withProperty(prefix + "logical-name", "control")
                .withProperty(prefix + "role", role).withProperty(prefix + "driver-class-name", "org.postgresql.Driver")
                .withProperty(prefix + "jdbc-url", jdbc).withProperty(prefix + "username", "isolated")
                .withProperty(prefix + "password", "isolated");
    }

    private static Object settings(MockEnvironment environment, RegisteredPolicies policies) throws Throwable {
        var method = Class.forName(CONFIGURATION).getDeclaredMethod("settingsFor", String.class,
                org.springframework.core.env.Environment.class, RegisteredPolicies.class);
        method.setAccessible(true);
        return invoke(method, "config-publisher", environment, policies);
    }

    private static void identityMode(MockEnvironment environment, Object settings) throws Throwable {
        var method = Class.forName(CONFIGURATION).getDeclaredMethod("validateIdentityMode",
                org.springframework.core.env.Environment.class, settings.getClass());
        method.setAccessible(true);
        invoke(method, environment, settings);
    }

    private static Object invoke(java.lang.reflect.Method method, Object... arguments) throws Throwable {
        try { return method.invoke(null, arguments); }
        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static void assertWorkerRoot(String role, String rootName) throws Exception {
        Class<?> configuration = Class.forName(CONFIGURATION);
        var application = (SpringApplication) configuration.getMethod("application", String.class).invoke(null, role);
        assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.NONE);
        assertThat(application.isKeepAlive()).isTrue();
        Class<?> root = Class.forName(CONFIGURATION + "$" + rootName);
        assertThat(application.getAllSources()).containsExactly(root);
        assertThat(root.getAnnotation(SpringBootApplication.class)).isNull();
        assertThat(root.getAnnotation(ComponentScan.class)).isNull();
        assertThat(root.getDeclaredAnnotations()).noneMatch(annotation ->
                annotation.annotationType().getName().equals("org.springframework.boot.autoconfigure.EnableAutoConfiguration"));
    }
}
