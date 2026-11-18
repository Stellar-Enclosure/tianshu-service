package top.egon.cola.component.tianshu.admin.persistence;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.annotation.MapperScan;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusAutoConfiguration;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaShardingAutoConfiguration;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.common.mybatis.interceptor.EgonColaLocalWriteGuardInnerInterceptor;
import top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor;
import top.egon.cola.component.common.mybatis.model.EgonModel;
import top.egon.cola.component.tianshu.admin.config.DdcConfigDeliveryPersistenceConfiguration;
import top.egon.cola.component.tianshu.admin.dao.DdcBizMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nPageMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSourceImportGrantMapper;
import top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli;
import top.egon.cola.component.tianshu.admin.model.po.DdcBizPO;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Complete production Mapper declarations and XML must satisfy the same startup contract. */
class DdcAdminMapperReadinessTest {
    private static final String DAO_PACKAGE = "top.egon.cola.component.tianshu.admin.dao";
    private static final String XML_LOCATIONS = "classpath*:mybatis/mapper/tianshu/**/*.xml";
    private static final List<String> REQUIRED_METHODS = List.of(
            "selectActiveById", "selectActiveByIds", "deleteVersionedById");
    private static final List<String> DOMAIN_MAPPERS = List.of(
            "DdcCatalogActivationProducerMapper", "DdcCatalogActivationProjectionMapper",
            "DdcI18nImportReceiptMapper", "DdcI18nPageMapper", "DdcI18nPublishedOverrideMapper",
            "DdcI18nSourceMessageMapper", "DdcI18nSourceVersionMapper", "DdcMetadataScopeMapper",
            "DdcResourceMetadataProjectionMapper", "DdcSourceImportGrantMapper");
    private static final String DATABASE = "tianshu_step20_mapper_readiness";
    private static final String FIXTURE_PREFIX = "TIANSHU_ADMIN_MAPPER_READINESS_IT_";
    private static final long TENANT = 20201L;
    private static final String ACTOR = "service:mapper-readiness";

    @TempDir
    Path scratch;

    @Test
    void completeProductionMapperRegistryProvidesEveryRequiredXmlOrExplicitDefault() throws Exception {
        Set<Class<?>> declarations = productionEgonMappers();
        Configuration configuration = productionXml();
        assertThat(declarations).extracting(Class::getSimpleName).contains(
                "DdcCatalogActivationProducerMapper", "DdcCatalogActivationProjectionMapper",
                "DdcI18nImportReceiptMapper", "DdcI18nPageMapper", "DdcI18nPublishedOverrideMapper",
                "DdcI18nSourceMessageMapper", "DdcI18nSourceVersionMapper", "DdcMetadataScopeMapper",
                "DdcResourceMetadataProjectionMapper", "DdcSourceImportGrantMapper",
                "DdcSiEmbeddingChunkMapper", "DdcSiReferenceMapper", "DdcConfigVersionMapper",
                "DdcConfigBootBindingMapper", "DdcConfigProcessReceiptMapper");
        assertThat(configuration.getMapperRegistry().getMappers()).containsAll(declarations);
        List<String> missing = new ArrayList<>();
        for (Class<?> mapper : declarations) {
            for (String method : REQUIRED_METHODS) {
                if (Arrays.stream(mapper.getMethods()).filter(candidate -> candidate.getName().equals(method))
                        .anyMatch(Method::isDefault)) {
                    continue;
                }
                String statement = mapper.getName() + '.' + method;
                if (!configuration.hasStatement(statement, false)
                        || configuration.getMappedStatement(statement, false).getResource() == null
                        || !configuration.getMappedStatement(statement, false).getResource().contains(".xml")) {
                    missing.add(statement);
                }
            }
        }
        assertThat(missing).as("complete Admin startup Mapper contract").isEmpty();
    }

    @Test
    void unusedGenericOperationsRejectAndSpecializedXmlRemainsRegistered() throws Exception {
        Configuration configuration = productionXml();
        for (Class<?> mapper : productionEgonMappers()) {
            Object proxy = Proxy.newProxyInstance(mapper.getClassLoader(), new Class<?>[]{mapper},
                    (target, method, arguments) -> {
                        if (method.isDefault()) return InvocationHandler.invokeDefault(target, method, arguments);
                        throw new AssertionError("A generic denial must not dispatch specialized SQL");
                    });
            assertClosedOperations(mapper, proxy);
        }
        for (var specialized : Map.ofEntries(
                Map.entry("DdcCatalogActivationProducerMapper", "findByProjection"),
                Map.entry("DdcCatalogActivationProjectionMapper", "findByVersion"),
                Map.entry("DdcI18nImportReceiptMapper", "findByOperation"),
                Map.entry("DdcI18nPageMapper", "lockById"),
                Map.entry("DdcI18nPublishedOverrideMapper", "findApplicableEntries"),
                Map.entry("DdcI18nSourceMessageMapper", "findBySource"),
                Map.entry("DdcI18nSourceVersionMapper", "findByVersion"),
                Map.entry("DdcMetadataScopeMapper", "fenceBinding"),
                Map.entry("DdcResourceMetadataProjectionMapper", "findActivatedResources"),
                Map.entry("DdcSourceImportGrantMapper", "fenceEnabled"),
                Map.entry("DdcSiReferenceMapper", "deleteBySource"),
                Map.entry("DdcSiEmbeddingChunkMapper", "deleteByVersionId")).entrySet()) {
            String id = DAO_PACKAGE + '.' + specialized.getKey() + '.' + specialized.getValue();
            assertThat(configuration.hasStatement(id, false)).as(id).isTrue();
            assertThat(configuration.getMappedStatement(id, false).getResource()).contains(".xml");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TIANSHU_ADMIN_MAPPER_READINESS_IT_ENABLED", matches = "true")
    void fullSpringPersistenceStartsWithTheManagedSchemaAndEnforcesRealMapperBoundaries() throws Exception {
        String url = requiredFixture("JDBC_URL");
        String user = requiredFixture("DB_USERNAME");
        String password = requiredFixture("DB_PASSWORD");
        var physical = ownedEmptyPrimary(url, user, password);
        initializeManagedSchema(url, user, password);
        persistenceRunner(url, user, password).run(context -> {
            assertThat(context).hasNotFailed().hasBean("egonColaMybatisPlusContractValidator");
            var factory = context.getBean("sqlSessionFactory", SqlSessionFactory.class);
            var configuration = factory.getConfiguration();
            assertThat(configuration.getMapperRegistry().getMappers()).containsAll(productionEgonMappers());
            assertThat(configuration.getInterceptors().getLast()).isSameAs(context.getBean("egonColaOriginalSqlGuardInterceptor"));
            assertThat(configuration.getInterceptors()).anySatisfy(plugin -> assertThat(plugin)
                    .isSameAs(context.getBean("egonColaModelValidationInterceptor")));
            var plus = context.getBean("mybatisPlusInterceptor", MybatisPlusInterceptor.class);
            assertThat(plus.getInterceptors()).extracting(Object::getClass).containsExactly(
                    EgonColaTenantIdGuardInnerInterceptor.class, BlockAttackInnerInterceptor.class,
                    TenantLineInnerInterceptor.class, OptimisticLockerInnerInterceptor.class,
                    EgonColaLocalWriteGuardInnerInterceptor.class, PaginationInnerInterceptor.class);
            var global = GlobalConfigUtils.getGlobalConfig(configuration);
            assertThat(global.getMetaObjectHandler()).isSameAs(context.getBean("egonColaMetaObjectHandler"));
            assertThat(global.getIdentifierGenerator()).isSameAs(context.getBean("egonColaIdentifierGenerator"));
            assertThat(factory.getConfiguration().getEnvironment().getDataSource()).isSameAs(context.getBean("dataSource"));
            var transactions = new TransactionTemplate(context.getBean("transactionManager", PlatformTransactionManager.class));
            var previous = MDC.getCopyOfContextMap();
            try {
                MDC.put("tenantId", Long.toString(TENANT));
                MDC.put("userId", ACTOR);
                for (Class<?> mapper : productionEgonMappers()) assertClosedOperations(mapper, context.getBean(mapper));
                var businesses = context.getBean(DdcBizMapper.class);
                transactions.executeWithoutResult(status -> assertThat(businesses.insert(business(2020001L))).isEqualTo(1));
                assertThat(businesses.selectActiveByBizCode(TENANT, "readiness-2020001")).satisfies(row -> {
                    assertThat(row).isNotNull();
                    assertThat(row.getTenantId()).isEqualTo(TENANT);
                    assertThat(row.getCreateUserId()).isEqualTo(ACTOR);
                    assertThat(row.getUpdateUserId()).isEqualTo(ACTOR);
                });
                assertThat(context.getBean(DdcI18nPageMapper.class).selectPageById(TENANT, Long.MAX_VALUE)).isNull();
                var grants = context.getBean(DdcSourceImportGrantMapper.class);
                assertThat(grants.findEnabled(TENANT, 2020002L, "readiness", "a".repeat(64))).isNull();
                transactions.executeWithoutResult(status -> assertThat(grants.fenceEnabled(
                        TENANT, 2020002L, 0L, ACTOR, Instant.now())).isZero());
                var rollback = new IllegalStateException("readiness transaction rollback");
                assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    assertThat(businesses.insert(business(2020003L))).isEqualTo(1);
                    throw rollback;
                })).isSameAs(rollback);
                assertThat(businesses.selectActiveById(TENANT, 2020003L)).isNull();
                MDC.put("tenantId", Long.toString(TENANT + 1));
                assertThatThrownBy(() -> businesses.selectActiveByBizCode(TENANT, "readiness-2020001"))
                        .hasStackTraceContaining("TENANT_CONTEXT_MISMATCH");
                MDC.remove("tenantId");
                assertThatThrownBy(() -> context.getBean(DdcI18nPageMapper.class).selectPageById(TENANT, Long.MAX_VALUE))
                        .hasStackTraceContaining("TENANT_CONTEXT_MISSING");
            } finally {
                if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
            }
        });
        try (var connection = physical.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id,tenant_id,create_user_id,update_user_id FROM public.ddc_biz_t0 ORDER BY id")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isEqualTo(2020001L);
            assertThat(rows.getLong(2)).isEqualTo(TENANT);
            assertThat(rows.getString(3)).isEqualTo(ACTOR);
            assertThat(rows.getString(4)).isEqualTo(ACTOR);
            assertThat(rows.next()).as("rolled-back insert must not survive context close").isFalse();
        }
    }

    private static void assertClosedOperations(Class<?> mapper, Object proxy) throws Exception {
        List<String> methods = DOMAIN_MAPPERS.contains(mapper.getSimpleName()) ? REQUIRED_METHODS
                : Set.of("DdcSiEmbeddingChunkMapper", "DdcSiReferenceMapper").contains(mapper.getSimpleName())
                ? List.of("deleteVersionedById") : List.of();
        for (String operation : methods) {
            Method method = Arrays.stream(mapper.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(operation) && candidate.isDefault() && !candidate.isBridge())
                    .findFirst().orElseThrow(() -> new AssertionError("Explicit generic denial missing: " + mapper.getName() + '.' + operation));
            Object argument = validGenericArgument(method);
            assertThatThrownBy(() -> method.invoke(proxy, argument)).as(mapper.getName() + '.' + operation)
                    .hasCauseInstanceOf(UnsupportedOperationException.class);
        }
    }

    private static Object validGenericArgument(Method method) throws Exception {
        if (method.getName().equals("selectActiveById")) return 1L;
        if (method.getName().equals("selectActiveByIds")) return List.of(1L);
        var row = (EgonModel<?>) method.getParameterTypes()[0].getConstructor().newInstance();
        row.setId(1L);
        row.setTenantId(TENANT);
        row.setVersion(0L);
        row.setCreateUserId(ACTOR);
        row.setUpdateUserId(ACTOR);
        row.setCreateTime(Instant.now());
        row.setUpdateTime(Instant.now());
        return row;
    }

    private static DdcBizPO business(long id) {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        return DdcBizPO.builder().id(id).publicId("readiness-" + id).bizCode("readiness-" + id)
                .bizName("Mapper readiness").enabled(true).createdAt(now).updatedAt(now).build();
    }

    private static ApplicationContextRunner persistenceRunner(String url, String user, String password) {
        return new ApplicationContextRunner().withInitializer(context -> {
            try {
                new YamlPropertySourceLoader().load("production-persistence", new ClassPathResource("egon-tianshu-persistence.yml"))
                        .forEach(context.getEnvironment().getPropertySources()::addLast);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Production persistence YAML required", error);
            }
        }).withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class,
                EgonColaMybatisPlusAutoConfiguration.class, EgonColaShardingAutoConfiguration.class,
                MybatisPlusInnerInterceptorAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class))
                .withUserConfiguration(CompleteMapperConfiguration.class, DdcConfigDeliveryPersistenceConfiguration.class)
                .withPropertyValues("TIANSHU_JDBC_URL=" + url, "TIANSHU_DB_USERNAME=" + user, "TIANSHU_DB_PASSWORD=" + password,
                        "mybatis-plus.mapper-locations=" + XML_LOCATIONS + ",classpath*:mapper/outbox/**/*.xml");
    }

    private void initializeManagedSchema(String url, String user, String password) throws Exception {
        String yaml;
        try (var input = new ClassPathResource("egon-tianshu-persistence.yml").getInputStream()) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var json = new ObjectMapper();
        yaml = yaml.replace("\"${TIANSHU_JDBC_URL:${spring.datasource.url}}\"", json.writeValueAsString(url))
                .replace("\"${TIANSHU_DB_USERNAME:${spring.datasource.username}}\"", json.writeValueAsString(user))
                .replace("\"${TIANSHU_DB_PASSWORD:${spring.datasource.password}}\"", json.writeValueAsString(password));
        Path target = Files.createFile(scratch.resolve("target.yml"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(target, yaml);
        DdcPersistenceMigrationCli.main(new String[]{"initialize", "--target-config", target.toString()});
    }

    private static PGSimpleDataSource ownedEmptyPrimary(String url, String user, String password) throws Exception {
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/" + DATABASE))
            throw new IllegalStateException("Dedicated mapper-readiness loopback database required");
        var physical = new PGSimpleDataSource();
        physical.setURL(url);
        physical.setUser(user);
        physical.setPassword(password);
        physical.setSocketTimeout(15);
        try (var connection = physical.getConnection(); var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT current_database(),host(inet_server_addr()),pg_is_in_recovery(),"
                     + "(SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind IN ('r','p')),"
                     + "to_regclass('egon_outbox.ddl_history') IS NULL")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString(1)).isEqualTo(DATABASE);
            assertThat(row.getString(2)).isEqualTo("127.0.0.1");
            assertThat(row.getBoolean(3)).isFalse();
            assertThat(row.getLong(4)).as("harness must reset this dedicated database between runs").isZero();
            assertThat(row.getBoolean(5)).isTrue();
        }
        return physical;
    }

    private static String requiredFixture(String suffix) {
        String value = Objects.requireNonNull(System.getenv(FIXTURE_PREFIX + suffix), "explicit private fixture configuration required");
        if (value.isBlank()) throw new IllegalStateException("Explicit private fixture configuration required");
        return value;
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @MapperScan(basePackages = DAO_PACKAGE, lazyInitialization = "true")
    static class CompleteMapperConfiguration {
    }

    private static Set<Class<?>> productionEgonMappers() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent() && definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(EgonColaMapper.class));
        Set<Class<?>> declarations = new LinkedHashSet<>();
        for (var definition : scanner.findCandidateComponents(DAO_PACKAGE)) {
            declarations.add(Class.forName(definition.getBeanClassName(), false,
                    DdcAdminMapperReadinessTest.class.getClassLoader()));
        }
        assertThat(declarations).isNotEmpty();
        return declarations;
    }

    private static Configuration productionXml() throws Exception {
        var configuration = new MybatisConfiguration();
        var resources = new PathMatchingResourcePatternResolver().getResources(XML_LOCATIONS);
        assertThat(resources).isNotEmpty();
        for (var resource : resources) {
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(input, configuration, resource.getURL().toString(),
                        configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
