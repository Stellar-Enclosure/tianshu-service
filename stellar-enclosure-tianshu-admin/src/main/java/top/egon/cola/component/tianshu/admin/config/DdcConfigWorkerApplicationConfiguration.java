package top.egon.cola.component.tianshu.admin.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.AliasRegistry;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.id.autoconfigure.IdGeneratorAutoConfiguration;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusAutoConfiguration;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaShardingAutoConfiguration;
import top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties;
import top.egon.cola.component.outbox.autoconfigure.OutboxMybatisPlusAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxStateMachineAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxAutoConfiguration;
import top.egon.cola.component.outbox.dispatch.OutboxDispatcher;
import top.egon.cola.component.outbox.event.OutboxCommittedEvent;
import top.egon.cola.component.outbox.event.OutboxCommittedEventListener;
import top.egon.cola.component.tianshu.admin.config.DdcConfigPublicationPolicyConfiguration.RegisteredPolicies;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigItemMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigVersionMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigWorkloadObservationMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcPublishTaskMapper;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcConfigWorkloadObservationExporter;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcConfigWorkloadObservationExporter.BindingResolver;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcConfigWorkloadObservationExporter.BindingVO;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.DeploymentKey;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.Mode;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ScopeVO;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigItemConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigItemConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigVersionConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigVersionConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigWorkloadObservationConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigWorkloadObservationConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskConverterImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigItemRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigVersionRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcConfigWorkloadObservationRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcPublishTaskRepositoryImpl;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.notification.DdcConfigDispatchDeliveryHandler;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigBaselineReconciler;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
import top.egon.cola.component.tianshu.admin.service.publish.DdcNativeConfigRolloutService;
import top.egon.cola.component.tianshu.redis.DdcRedisClientFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit roots only: these lite configurations cannot be discovered by the ordinary Admin component scan. */
public final class DdcConfigWorkerApplicationConfiguration {
    private static final String ADMIN = "egon.cola.component.tianshu.admin.";
    private static final String WORKER = ADMIN + "config-worker";
    private static final String OUTBOX = "egon.cola.component.transactional-outbox.";
    private static final String ID = "egon.cola.component.id.";
    private DdcConfigWorkerApplicationConfiguration() { }

    public static SpringApplication application(String role) {
        if (role == null) throw new IllegalArgumentException("TIANSHU_CONFIG_WORKER_MODE_INVALID");
        Class<?> root = switch (role) {
            case "config-publisher" -> PublisherApplication.class;
            case "config-observer" -> ObserverApplication.class;
            default -> throw new IllegalArgumentException("TIANSHU_CONFIG_WORKER_MODE_INVALID");
        };
        var application = new SpringApplication(root);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setKeepAlive(true);
        application.setApplicationContextFactory(ApplicationContextFactory.ofContextClass(AnnotationConfigApplicationContext.class));
        application.setAdditionalProfiles(role);
        application.addListeners(new WorkerBoundaryListener(role));
        application.addInitializers(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("tianshu-config-worker-boundary", boundary(role))));
        return application;
    }

    /** Install the role limits before Boot's environment post-processors bind SpringApplication itself. */
    private record WorkerBoundaryListener(String role) implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
            event.getEnvironment().getPropertySources().addFirst(new MapPropertySource("tianshu-config-worker-boundary", boundary(role)));
        }
    }

    private static Map<String, Object> boundary(String role) {
        var values = new LinkedHashMap<String, Object>();
        values.put("spring.main.web-application-type", "none");
        values.put("spring.main.keep-alive", true);
        values.put("spring.main.allow-bean-definition-overriding", false);
        values.put(WORKER + ".mode", role);
        values.put("egon.cola.component.rpc.enabled", false);
        values.put("egon.cola.component.tianshu.enabled", false);
        values.put("egon.cola.component.tianshu.delivery.enabled", false);
        values.put(ADMIN + "config-delivery.enabled", false);
        values.put(OUTBOX + "annotation.enabled", false);
        values.put(OUTBOX + "retry.recover-exhausted", false);
        values.put(OUTBOX + "cleanup.enabled", false);
        if ("config-observer".equals(role)) values.put(OUTBOX + "polling.enabled", false);
        return Map.copyOf(values);
    }

    @Import({PersistenceApplication.class, DdcConfigItemRepositoryImpl.class, DdcConfigVersionRepositoryImpl.class,
            DdcPublishTaskRepositoryImpl.class, DdcConfigDispatchDeliveryHandler.class})
    public static class PublisherApplication {
        @Bean("ddcConfigWorkerSettings") WorkerSettings settings(Environment environment,
                @Qualifier("ddcConfigPublicationPolicies") RegisteredPolicies policies) {
            return settingsFor("config-publisher", environment, policies);
        }
        @Bean("ddcConfigPublisherClusterId") String clusterId(@Qualifier("ddcConfigWorkerSettings") WorkerSettings settings) { return settings.clusterId(); }
        @Bean(name = "ddcKubernetesConfigPublisherApiClient", destroyMethod = "close")
        DdcKubernetesApiClient publisherApi(@Qualifier("ddcConfigWorkerSettings") WorkerSettings settings,
                @Qualifier("jacksonObjectMapper") ObjectMapper json) { return settings.client(json, Mode.PUBLISHER); }
        @Bean("ddcConfigItemMapper") DdcConfigItemMapper items(@Qualifier("sqlSessionTemplate") SqlSessionTemplate sessions) { return sessions.getMapper(DdcConfigItemMapper.class); }
        @Bean("ddcConfigVersionMapper") DdcConfigVersionMapper versions(@Qualifier("sqlSessionTemplate") SqlSessionTemplate sessions) { return sessions.getMapper(DdcConfigVersionMapper.class); }
        @Bean("ddcPublishTaskMapper") DdcPublishTaskMapper tasks(@Qualifier("sqlSessionTemplate") SqlSessionTemplate sessions) { return sessions.getMapper(DdcPublishTaskMapper.class); }
        @Bean("ddcConfigItemConverterImpl") DdcConfigItemConverter itemConverter() { return new DdcConfigItemConverterImpl(); }
        @Bean("ddcConfigVersionConverterImpl") DdcConfigVersionConverter versionConverter() { return new DdcConfigVersionConverterImpl(); }
        @Bean("ddcPublishTaskConverterImpl") DdcPublishTaskConverter taskConverter() { return new DdcPublishTaskConverterImpl(); }
        @Bean("ddcConfigManifestFactory") DdcConfigManifestFactory manifests(@Qualifier("jacksonObjectMapper") ObjectMapper json) { return new DdcConfigManifestFactory(json); }
        @Bean("ddcConfigBaselineReconciler") DdcConfigBaselineReconciler baselines(
                @Qualifier("ddcKubernetesConfigPublisherApiClient") DdcKubernetesApiClient api,
                @Qualifier("jacksonObjectMapper") ObjectMapper json, @Qualifier("ddcPublishClock") Clock clock,
                @Qualifier("ddcConfigPublisherClusterId") String clusterId,
                @Qualifier("ddcConfigPublicationPolicies") RegisteredPolicies policies) {
            return new DdcConfigBaselineReconciler(api, json, clock, clusterId, (scope, target) -> {
                var configured = policies.byScope().get(scope);
                return configured != null && configured.targetBindings().contains(target);
            });
        }
        @Bean("ddcNativeConfigRolloutService") DdcNativeConfigRolloutService rollouts(
                @Qualifier("ddcConfigItemRepository") top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository items,
                @Qualifier("ddcConfigVersionRepository") top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository versions,
                @Qualifier("ddcPublishTaskRepository") top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository tasks,
                @Qualifier("ddcConfigManifestFactory") DdcConfigManifestFactory manifests,
                @Qualifier("ddcConfigBaselineReconciler") DdcConfigBaselineReconciler baselines,
                @Qualifier("ddcNativeRolloutTransactionTemplate") TransactionTemplate transactions,
                @Qualifier("jacksonObjectMapper") ObjectMapper json, @Qualifier("ddcPublishClock") Clock clock) {
            return new DdcNativeConfigRolloutService(items, versions, tasks, manifests, baselines, transactions, json, clock);
        }
        @Bean("ddcConfigRolloutOutboxRoute") DdcConfigRolloutOutboxRouteDTO route() { return new DdcConfigRolloutOutboxRouteDTO("tianshu.config-rollout", "1"); }
        @Bean("ddcConfigPublisherNodeIdentityGuard") SmartInitializingSingleton nodeIdentity(Environment environment) {
            return () -> { if (!required(environment, OUTBOX + "node-id").matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) throw invalid("NODE_ID_INVALID"); };
        }
    }

    @Import({PersistenceApplication.class, DdcConfigWorkloadObservationRepositoryImpl.class})
    public static class ObserverApplication {
        @Bean("ddcConfigWorkerSettings") WorkerSettings settings(Environment environment,
                @Qualifier("ddcConfigPublicationPolicies") RegisteredPolicies policies) {
            return settingsFor("config-observer", environment, policies);
        }
        @Bean(name = "ddcKubernetesConfigObserverApiClient", destroyMethod = "close")
        DdcKubernetesApiClient observerApi(@Qualifier("ddcConfigWorkerSettings") WorkerSettings settings,
                @Qualifier("jacksonObjectMapper") ObjectMapper json) { return settings.client(json, Mode.OBSERVER); }
        @Bean(name = "ddcKubernetesObservationService", destroyMethod = "close")
        DdcKubernetesObservationService observations(@Qualifier("ddcKubernetesConfigObserverApiClient") DdcKubernetesApiClient api,
                @Qualifier("ddcConfigWorkerSettings") WorkerSettings settings, @Qualifier("ddcPublishClock") Clock clock,
                Environment environment) {
            String prefix = ADMIN + "config-observer.";
            return new DdcKubernetesObservationService(api, clock, settings.clusterId(), settings.namespaces(),
                    duration(environment, prefix + "max-stale", Duration.ofSeconds(60)),
                    duration(environment, prefix + "relist-interval", Duration.ofSeconds(20)),
                    duration(environment, prefix + "watch-timeout", Duration.ofSeconds(30)));
        }
        @Bean("ddcConfigWorkloadObservationMapper") DdcConfigWorkloadObservationMapper observationMapper(@Qualifier("sqlSessionTemplate") SqlSessionTemplate sessions) { return sessions.getMapper(DdcConfigWorkloadObservationMapper.class); }
        @Bean("ddcConfigWorkloadObservationConverterImpl") DdcConfigWorkloadObservationConverter converter() { return new DdcConfigWorkloadObservationConverterImpl(); }
        @Bean("ddcConfigWorkloadObservationExportInterval") Duration exportInterval(Environment environment) { return duration(environment, ADMIN + "config-observer.export-interval", Duration.ofSeconds(2)); }
        @Bean(name = "ddcConfigWorkloadObservationExporter", destroyMethod = "close")
        DdcConfigWorkloadObservationExporter exporter(@Qualifier("ddcKubernetesObservationService") DdcKubernetesObservationService observer,
                @Qualifier("ddcConfigWorkloadObservationRepository") top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository repository,
                @Qualifier("ddcTenantContextExecutor") DdcTenantContextExecutor tenants,
                @Qualifier("ddcConfigWorkloadObservationBindings") BindingResolver bindings,
                @Qualifier("ddcPublishClock") Clock clock, @Qualifier("ddcConfigWorkloadObservationExportInterval") Duration interval) {
            return new DdcConfigWorkloadObservationExporter(observer, repository, tenants, bindings, clock, interval);
        }
        @Bean("ddcConfigWorkloadObservationBindings") BindingResolver observationBindings(
                @Qualifier("ddcConfigPublicationPolicies") RegisteredPolicies policies,
                @Qualifier("ddcConfigWorkerSettings") WorkerSettings settings,
                @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants) {
            List<BindingVO> fixed = policies.targets().stream().map(registered -> {
                var tenant = tenants.requireEnabledTenant(Long.parseLong(registered.scope().getTenantId()));
                return new BindingVO(new DdcTenantContextDTO(tenant.getTenantId(), tenant.getIdentityTenantId(), "system:config-observer"), registered.target());
            }).toList();
            return () -> fixed;
        }
        /** Observer validates the real Outbox metadata but cannot claim a committed message. */
        @Bean("outboxCommittedEventListener") OutboxCommittedEventListener noDelivery(OutboxDispatcher dispatcher) {
            return new OutboxCommittedEventListener(dispatcher) {
                @Override public void onApplicationEvent(org.springframework.context.PayloadApplicationEvent<OutboxCommittedEvent> event) { }
            };
        }
    }

    @ImportAutoConfiguration({Jackson2AutoConfiguration.class, ValidationAutoConfiguration.class, AopAutoConfiguration.class,
            IdGeneratorAutoConfiguration.class, EgonColaMybatisPlusAutoConfiguration.class, EgonColaShardingAutoConfiguration.class,
            MybatisPlusInnerInterceptorAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class,
            TransactionalOutboxAutoConfiguration.class, OutboxStateMachineAutoConfiguration.class, OutboxMybatisPlusAutoConfiguration.class})
    @EnableConfigurationProperties({DdcAdminProperties.class, DdcTenantAdminProperties.class})
    @Import({DdcTenantRegistry.class, DdcTenantContextExecutor.class, DdcConfigPublicationPolicyConfiguration.class,
            DdcConfigDeliveryPersistenceConfiguration.class, DdcDatabaseReadyValidator.class})
    public static class PersistenceApplication {
        /** Boot 4 names its managed Jackson 2 mapper jackson2ObjectMapper; application qualifiers stay stable. */
        @Bean static BeanFactoryPostProcessor managedJacksonMapperAliases() {
            return beans -> {
                String[] names = beans.getBeanNamesForType(ObjectMapper.class, true, false);
                if (names.length != 1 || !(beans instanceof AliasRegistry aliases)) throw invalid("JSON_MAPPER_AMBIGUOUS");
                String actual = names[0];
                if ("jacksonObjectMapper".equals(actual)
                        || java.util.Arrays.asList(beans.getAliases(actual)).contains("jacksonObjectMapper")) return;
                if (beans.containsBean("jacksonObjectMapper")) throw invalid("JSON_MAPPER_NAME_CONFLICT");
                aliases.registerAlias(actual, "jacksonObjectMapper");
            };
        }
        @Bean({"ddcPublishClock", "egonColaMybatisPlusClock"}) Clock clock() { return Clock.systemUTC(); }
        @Bean("ddcConfigWorkerIdReadiness") SmartInitializingSingleton idReady(Environment environment,
                @Qualifier("ddcConfigWorkerSettings") WorkerSettings settings,
                ObjectProvider<IdGeneratorAutoConfiguration> generators) {
            return () -> { validateIdentityMode(environment, settings); generators.getObject().assertIssuanceAvailable(); };
        }
        @Bean(name = "redissonClient", destroyMethod = "shutdown")
        @ConditionalOnProperty(prefix = WORKER, name = "test-static-machine-id-enabled", havingValue = "false", matchIfMissing = true)
        RedissonClient identityRedis(Environment environment, @Qualifier("ddcConfigWorkerSettings") WorkerSettings settings) {
            validateIdentityMode(environment, settings);
            String prefix = WORKER + ".id-redis.";
            var nodes = Binder.get(environment).bind(prefix + "nodes", Bindable.listOf(String.class)).orElse(List.of());
            if (nodes.isEmpty() || nodes.size() > 32 || Set.copyOf(nodes).size() != nodes.size()) throw invalid("ID_REDIS_NODES_INVALID");
            for (String node : nodes) {
                URI uri;
                try { uri = URI.create(node); } catch (IllegalArgumentException malformed) { throw invalid("ID_REDIS_NODES_INVALID"); }
                if (!"redis".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                        || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !uri.getRawPath().isEmpty()) throw invalid("ID_REDIS_NODES_INVALID");
            }
            String username = required(environment, prefix + "username");
            if (!username.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw invalid("ID_REDIS_ACL_INVALID");
            String password = secret(absolute(environment, prefix + "password-file"));
            return DdcRedisClientFactory.create("CLUSTER", nodes, null, null, 0, password, username, 0);
        }
    }

    private static WorkerSettings settingsFor(String role, Environment environment, RegisteredPolicies policies) {
        String prefix = ADMIN + role + ".";
        String cluster = required(environment, prefix + "cluster-id");
        if (!cluster.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") || policies.targets().isEmpty()) throw invalid("SCOPE_INVALID");
        var namespaces = new LinkedHashSet<String>(); var deployments = new LinkedHashSet<DeploymentKey>();
        for (var registered : policies.targets()) {
            var target = registered.target();
            if (!cluster.equals(target.getClusterId()) || !"Deployment".equals(target.getWorkloadKind())) throw invalid("MIXED_CLUSTER_OR_WORKLOAD");
            namespaces.add(target.getKubernetesNamespace());
            deployments.add(new DeploymentKey(target.getKubernetesNamespace(), target.getWorkloadName()));
        }
        URI server;
        try { server = URI.create(required(environment, prefix + "api-server")); }
        catch (IllegalArgumentException malformed) { throw invalid("API_ORIGIN_INVALID"); }
        if (!"https".equals(server.getScheme()) || server.getHost() == null || server.getRawUserInfo() != null
                || server.getRawQuery() != null || server.getRawFragment() != null
                || !(server.getRawPath().isEmpty() || "/".equals(server.getRawPath()))) throw invalid("API_ORIGIN_INVALID");
        return new WorkerSettings(cluster, server, absolute(environment, prefix + "api-ca-file"),
                absolute(environment, prefix + "api-token-file"), duration(environment, prefix + "api-timeout", Duration.ofSeconds(5)),
                environment.getProperty(prefix + "api-max-response-bytes", Integer.class, 4 * 1024 * 1024),
                Set.copyOf(namespaces), Set.copyOf(deployments));
    }

    private static void validateIdentityMode(Environment environment, WorkerSettings settings) {
        boolean fixed = environment.getProperty(WORKER + ".test-static-machine-id-enabled", Boolean.class, false);
        Long slot = environment.getProperty(ID + "machine-id", Long.class);
        if (!environment.getProperty(ID + "enabled", Boolean.class, true)) throw invalid("ID_DISABLED");
        if (fixed) {
            var physical = Binder.get(environment).bind(EgonColaShardingProperties.PREFIX + ".data-sources",
                    Bindable.listOf(EgonColaShardingProperties.PhysicalDataSourceProperties.class)).orElse(List.of());
            if (slot == null || slot < 0 || slot > 1023 || !environment.acceptsProfiles(Profiles.of("local", "test"))
                    || physical.size() != 1 || physical.getFirst().role() != EgonColaShardingProperties.DataSourceRoleEnum.PRIMARY
                    || !physical.getFirst().jdbcUrl().matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost|\\[::1])(?::[0-9]{1,5})?/[A-Za-z0-9_-]+")
                    || !Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(settings.server().getHost())) throw invalid("TEST_STATIC_ID_FORBIDDEN");
        } else {
            if (slot != null || !environment.getProperty(ID + "redis.enabled", Boolean.class, true)) throw invalid("DYNAMIC_ID_REQUIRED");
            required(environment, ID + "redis.env"); required(environment, ID + "redis.app");
        }
    }

    private static String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank() || !value.equals(value.trim())) throw invalid("PROPERTY_REQUIRED");
        return value;
    }
    private static Path absolute(Environment environment, String key) {
        Path path = Path.of(required(environment, key));
        if (!path.isAbsolute() || !Files.isRegularFile(path) || !Files.isReadable(path)) throw invalid("PROJECTED_FILE_REQUIRED");
        return path;
    }
    private static Duration duration(Environment environment, String key, Duration defaultValue) { return environment.getProperty(key, Duration.class, defaultValue); }
    private static String secret(Path path) {
        try (var file = Files.newInputStream(path)) {
            byte[] bytes = file.readNBytes(16_385);
            try {
                if (bytes.length == 0 || bytes.length > 16_384) throw invalid("ID_REDIS_SECRET_INVALID");
                String value = new String(bytes, StandardCharsets.UTF_8).replaceFirst("\\r?\\n$", "");
                if (value.isBlank() || value.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) throw invalid("ID_REDIS_SECRET_INVALID");
                return value;
            } finally { java.util.Arrays.fill(bytes, (byte) 0); }
        } catch (IOException unavailable) { throw invalid("ID_REDIS_SECRET_UNAVAILABLE"); }
    }
    private static IllegalStateException invalid(String reason) { return new IllegalStateException("TIANSHU_CONFIG_WORKER_" + reason); }

    record WorkerSettings(String clusterId, URI server, Path ca, Path token, Duration timeout, int maxResponseBytes,
                          Set<String> namespaces, Set<DeploymentKey> deployments) {
        DdcKubernetesApiClient client(ObjectMapper json, Mode mode) {
            return new DdcKubernetesApiClient(server, ca, token, json, timeout, maxResponseBytes,
                    new ScopeVO(mode, namespaces, mode == Mode.PUBLISHER ? deployments : Set.of()));
        }
    }
}
