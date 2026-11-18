package top.egon.cola.component.tianshu.admin.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlManifestBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlResult;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlTargetBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlTargetBO.RoleEnum;
import top.egon.cola.component.common.mybatis.ddl.EgonColaPostgreDdlRunner;
import top.egon.cola.component.tianshu.admin.config.DdcDatabaseReadyValidator;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcTianshuDdlIT {

    private static final String MANIFEST_RESOURCE = "db/egon-mp/repository-manifest.json";
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void blankSchemaInitializesAndReentersWhileDriftIsRejectedAndUnknownCommitIsVerified() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("TIANSHU_AI_PERSISTENCE_IT_ENABLED")),
                "requires an explicitly configured disposable PostgreSQL DDL target");
        PGSimpleDataSource dataSource = dataSource();
        String schema = "tianshu_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        boolean schemaCreated = false;
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
            schemaCreated = true;
        }

        try {
            dataSource.setCurrentSchema(schema);
            EgonColaDdlManifestBO manifest = manifest();
            int expectedSteps = manifest.scripts().size() - manifest.startingIndex(null);
            EgonColaDdlTargetBO target = new EgonColaDdlTargetBO("tianshu-it", schema, RoleEnum.MASTER_DATA,
                    interruptCommit(dataSource, expectedSteps, true), manifest, routeFingerprint());
            List<EgonColaDdlResult> initialized = runner().run(List.of(target));
            assertThat(initialized).hasSize(expectedSteps)
                    .allSatisfy(result -> assertThat(result.status()).isEqualTo(EgonColaDdlResult.StatusEnum.APPLIED));

            EgonColaDdlTargetBO normalTarget = new EgonColaDdlTargetBO("tianshu-it", schema,
                    RoleEnum.MASTER_DATA, dataSource, manifest, routeFingerprint());
            assertThat(runner().run(List.of(normalTarget)))
                    .allSatisfy(result -> assertThat(result.status()).isEqualTo(EgonColaDdlResult.StatusEnum.SKIPPED));

            EgonColaDdlManifestBO.ScriptBO lastScript = manifest.scripts().getLast();
            try (Connection connection = dataSource.getConnection(); var update = connection.prepareStatement(
                    "UPDATE ddl_history SET checksum = ? WHERE script = ? AND type = 'SQL'")) {
                update.setString(1, "0".repeat(64));
                update.setString(2, lastScript.path());
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            assertThatThrownBy(() -> runner().run(List.of(normalTarget)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DDL_TARGET_FAILED");
            try (Connection connection = dataSource.getConnection(); var update = connection.prepareStatement(
                    "UPDATE ddl_history SET checksum = ? WHERE script = ? AND type = 'SQL'")) {
                update.setString(1, lastScript.sha256());
                update.setString(2, lastScript.path());
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
        } finally {
            dataSource.setCurrentSchema("public");
            if (schemaCreated) {
                try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA \"" + schema + "\" CASCADE");
                }
            }
        }
    }

    @Test
    void physicalPrimaryReadinessRejectsSameNameWeakenedBootTenantConstraint() throws Exception {
        String url = System.getenv("TIANSHU_CLOUD_DELIVERY_READINESS_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "requires a dedicated readiness database");
        assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianshu_step18_readiness");
        var primary = new PGSimpleDataSource();
        primary.setURL(url);
        primary.setUser(requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DDL_DB_USERNAME"));
        primary.setPassword(requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DDL_DB_PASSWORD"));
        try (Connection connection = primary.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_database(), inet_server_addr()::text")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("tianshu_step18_readiness");
            assertThat(rows.getString(2)).startsWith("127.0.0.1");
        }
        EgonColaDdlManifestBO manifest = manifest();
        String fingerprint = routeFingerprint();
        runner().run(List.of(new EgonColaDdlTargetBO("tianshu-it", "public", RoleEnum.MASTER_DATA,
                primary, manifest, fingerprint)));
        DdcDatabaseReadyValidator.validateTianshuSchema(primary, fingerprint, manifest);

        String rename = "ALTER TABLE public.ddc_config_boot_binding_t0 RENAME CONSTRAINT ";
        String table = "ALTER TABLE public.ddc_config_boot_binding_t0 ";
        String name = "ck_config_boot_binding_technical_tenant";
        try (Connection connection = primary.getConnection(); var statement = connection.createStatement()) {
            statement.execute(rename + name + " TO it_original_technical_tenant");
            try {
                statement.execute(table + "ADD CONSTRAINT " + name + " CHECK (id > 0)");
                try {
                    assertThatThrownBy(() -> DdcDatabaseReadyValidator.validateTianshuSchema(primary, fingerprint, manifest))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining(name);
                } finally {
                    statement.execute(table + "DROP CONSTRAINT " + name);
                }
            } finally {
                statement.execute(rename + "it_original_technical_tenant TO " + name);
            }
        }
        DdcDatabaseReadyValidator.validateTianshuSchema(primary, fingerprint, manifest);
    }

    @Test
    void multiTargetRunResumesAfterSecondPrimaryRollsBackNewSqlAndItsHistory() throws Exception {
        String port = System.getenv("TIANSHU_CLOUD_DELIVERY_MULTITARGET_PORT");
        Assumptions.assumeTrue(port != null && !port.isBlank(), "requires two dedicated migration test databases");
        assertThat(port).matches("[0-9]{1,5}");
        var manifest = manifest();
        var previous = new EgonColaDdlManifestBO(manifest.family(), manifest.scripts().subList(0, manifest.scripts().size() - 1),
                manifest.freshBaselineVersion());
        var first = ownedTarget(port, "tianshu_step18_ddl_a");
        var second = ownedTarget(port, "tianshu_step18_ddl_b");
        String schema = "tianshu_multi_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection a = first.getConnection(); var statementA = a.createStatement();
             Connection b = second.getConnection(); var statementB = b.createStatement()) {
            try {
                statementA.execute("CREATE SCHEMA " + schema);
                statementB.execute("CREATE SCHEMA " + schema);
                String fingerprint = routeFingerprint();
                var oldFirst = new EgonColaDdlTargetBO("a", schema, RoleEnum.MASTER_DATA, first, previous, fingerprint);
                var oldSecond = new EgonColaDdlTargetBO("b", schema, RoleEnum.MASTER_DATA, second, previous, fingerprint);
                runner().run(List.of(oldFirst, oldSecond));
                var currentFirst = new EgonColaDdlTargetBO("a", schema, RoleEnum.MASTER_DATA, first, manifest, fingerprint);
                var failingSecond = new EgonColaDdlTargetBO("b", schema, RoleEnum.MASTER_DATA,
                        interruptCommit(second, 1, false), manifest, fingerprint);
                assertThatThrownBy(() -> runner().run(List.of(currentFirst, failingSecond)))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("DDL_COMMIT_UNKNOWN");
                assertLatestMigration(a, schema, true);
                assertLatestMigration(b, schema, false);

                var currentSecond = new EgonColaDdlTargetBO("b", schema, RoleEnum.MASTER_DATA, second, manifest, fingerprint);
                assertThat(runner().run(List.of(currentFirst, currentSecond)))
                        .filteredOn(result -> result.status() == EgonColaDdlResult.StatusEnum.APPLIED)
                        .singleElement().satisfies(result -> assertThat(result.version()).isEqualTo("20261117_001"));
                assertLatestMigration(b, schema, true);
            } finally {
                try {
                    statementB.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                } finally {
                    statementA.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }

    private static PGSimpleDataSource ownedTarget(String port, String database) throws SQLException {
        var target = new PGSimpleDataSource();
        target.setURL("jdbc:postgresql://127.0.0.1:" + port + '/' + database);
        target.setUser(requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DDL_DB_USERNAME"));
        target.setPassword(requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DDL_DB_PASSWORD"));
        try (Connection connection = target.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_database(), inet_server_addr()::text")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(database);
            assertThat(rows.getString(2)).startsWith("127.0.0.1");
        }
        return target;
    }

    private static void assertLatestMigration(Connection connection, String schema, boolean applied) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL, EXISTS (SELECT 1 FROM "
                + schema + ".ddl_history WHERE version='20261117_001')")) {
            statement.setString(1, schema + ".ddc_config_boot_binding_t0");
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isEqualTo(applied);
                assertThat(rows.getBoolean(2)).isEqualTo(applied);
            }
        }
    }

    private static EgonColaPostgreDdlRunner runner() {
        return new EgonColaPostgreDdlRunner(new ValidationUtils(VALIDATOR_FACTORY.getValidator()),
                new PathMatchingResourcePatternResolver(), Clock.systemUTC(), Duration.ofSeconds(10),
                Duration.ofSeconds(30));
    }

    private static EgonColaDdlManifestBO manifest() throws Exception {
        try (var input = DdcTianshuDdlIT.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing Tianshu DDL manifest resource");
            return new ObjectMapper().readValue(input, EgonColaDdlManifestBO.class);
        }
    }

    private static PGSimpleDataSource dataSource() {
        String url = requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DDL_JDBC_URL");
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("TIANSHU_AI_PERSISTENCE_IT_DDL_JDBC_URL must target PostgreSQL");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DDL_DB_USERNAME"));
        dataSource.setPassword(requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DDL_DB_PASSWORD"));
        return dataSource;
    }

    private static String routeFingerprint() {
        String value = System.getenv("TIANSHU_AI_PERSISTENCE_IT_DDL_ROUTE_FINGERPRINT");
        return value == null || value.isBlank() ? "a".repeat(64) : value;
    }

    private static DataSource interruptCommit(DataSource delegate, int commitToLose, boolean commitReachedServer) {
        AtomicInteger commits = new AtomicInteger();
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    Object result = invoke(delegate, method, args);
                    if ("getConnection".equals(method.getName()) && result instanceof Connection connection) {
                        return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                new Class<?>[]{Connection.class}, (connectionProxy, connectionMethod, connectionArgs) -> {
                                    if ("commit".equals(connectionMethod.getName())
                                            && commits.incrementAndGet() == commitToLose) {
                                        if (commitReachedServer) {
                                            connection.commit();
                                        }
                                        throw new SQLException("connection lost during commit", "08006");
                                    }
                                    return invoke(connection, connectionMethod, connectionArgs);
                                });
                    }
                    return result;
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for this IT");
        return value;
    }
}
