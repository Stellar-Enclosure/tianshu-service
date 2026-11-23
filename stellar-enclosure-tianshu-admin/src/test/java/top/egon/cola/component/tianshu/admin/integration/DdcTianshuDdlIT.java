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
    private static final String PAC_OPERATION = "ef236f7a-3840-4664-afc5-7c2690765c09";
    private static final String PAC_DIGEST = "a".repeat(64);

    @Test
    void pacMigrationPreservesOriginalTextUtcAndIgnoresOrdinaryHistory() throws Exception {
        withPacSchema(fixture -> {
            String original = "{\r\n  \"operationId\":\"" + PAC_OPERATION + "\", \"commandDigest\":\""
                    + PAC_DIGEST + "\", \"stage\":\"B0\", \"note\":\"雪😀\", \"nested\":"
                    + "[".repeat(15) + "0" + "]".repeat(15) + "\r\n}\n";
            insertLegacyReceipt(fixture.source(), 1, "pac-b0:" + PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0", original, false);
            insertLegacyReceipt(fixture.source(), 2, "ordinary-entry", "CONFIG_SAVE", "{invalid ordinary JSON", false);
            var target = new EgonColaDdlTargetBO("pac", fixture.schema(), RoleEnum.MASTER_DATA,
                    interruptCommit(fixture.source(), 1, true), fixture.current(), routeFingerprint());
            runner().run(List.of(target));
            runner().run(List.of(new EgonColaDdlTargetBO("pac", fixture.schema(), RoleEnum.MASTER_DATA,
                    fixture.source(), fixture.current(), routeFingerprint())));
            DdcDatabaseReadyValidator.validateTianshuSchema(fixture.source(), routeFingerprint(), fixture.current());
            assertThatThrownBy(() -> DdcDatabaseReadyValidator.validateTianshuSchema(
                    fixture.source(), routeFingerprint(), fixture.previous()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("TIANSHU_DDL_HISTORY_PREFIX_MISMATCH");
            try (var connection = fixture.source().getConnection(); var query = connection.createStatement();
                 var rows = query.executeQuery("SELECT tenant_id,operation_id,command_digest,receipt,recorded_at "
                         + "FROM ddc_metadata_bootstrap_receipt_t0")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(20);
                assertThat(rows.getString(2)).isEqualTo(PAC_OPERATION);
                assertThat(rows.getString(3)).isEqualTo(PAC_DIGEST);
                assertThat(rows.getString(4)).isEqualTo(original);
                assertThat(rows.getObject(5, java.time.OffsetDateTime.class).toInstant())
                        .isEqualTo(java.time.Instant.parse("2026-10-25T23:59:59.123456Z"));
                assertThat(rows.next()).isFalse();
                query.execute("DROP TABLE ddc_operation_log_t0");
                assertThat(resumeBootstrap(connection, fixture.schema(), original)).isEqualTo(original);
            }
            String nextOperation = "cccccccc-cccc-cccc-cccc-cccccccccccc";
            try (var connection = fixture.source().getConnection(); var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE ddc_metadata_bootstrap_receipt_t0 ADD CONSTRAINT fixture_receipt_failure "
                        + "CHECK(operation_id<>'"+nextOperation+"')");
                assertThatThrownBy(() -> initializeBootstrap(connection,fixture.schema(),nextOperation,null))
                        .isInstanceOf(InvocationTargetException.class)
                        .rootCause().isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getSQLState()).isEqualTo("23514"));
                try (var rows = statement.executeQuery("SELECT (SELECT count(*) FROM ddc_biz_t0), "
                        + "(SELECT count(*) FROM ddc_metadata_bootstrap_receipt_t0)")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isZero();
                    assertThat(rows.getInt(2)).isEqualTo(1);
                }
                connection.setAutoCommit(true);
                statement.execute("ALTER TABLE ddc_metadata_bootstrap_receipt_t0 DROP CONSTRAINT fixture_receipt_failure");
                String created = initializeBootstrap(connection,fixture.schema(),nextOperation,null);
                assertThat(initializeBootstrap(connection,fixture.schema(),nextOperation,created)).isEqualTo(created);
            }
            assertThatThrownBy(() -> runner().run(List.of(new EgonColaDdlTargetBO("old", fixture.schema(),
                    RoleEnum.MASTER_DATA, fixture.source(), fixture.previous(), routeFingerprint()))))
                    .isInstanceOf(IllegalStateException.class).hasStackTraceContaining("MANIFEST_PREFIX_MISMATCH");
        });
    }

    @Test
    void everyInvalidPacCandidateRollsBackTheNewTableAndHistory() throws Exception {
        String valid = "{\"operationId\":\""+PAC_OPERATION+"\",\"commandDigest\":\""+PAC_DIGEST+"\"}";
        List<BadPacCandidate> candidates = List.of(
                new BadPacCandidate("ordinary-key", "PAC_BOOTSTRAP_METADATA_B0", valid, false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "CONFIG_SAVE", valid, false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0", valid, true),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0", "{broken", false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0", valid.replace(PAC_OPERATION,
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0", valid.replace(PAC_DIGEST,
                        "A".repeat(64)), false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0", null, false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0",
                        valid.substring(0,valid.length()-1)+",\"note\":\""+"雪".repeat(349_526)+"\"}", false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0",
                        valid.substring(0,valid.length()-1)+",\"nested\":"+"[".repeat(16)+"0"+"]".repeat(16)+"}", false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0",
                        valid.substring(0,valid.length()-1)+",\"nested\":{\"x\":1,\"x\":2}}", false),
                new BadPacCandidate("pac-b0:"+PAC_OPERATION, "PAC_BOOTSTRAP_METADATA_B0",
                        valid.substring(0,valid.length()-1)+",\"note\":\""+"😀".repeat(524_289)+"\"}", false));
        for (BadPacCandidate bad : candidates) {
            withPacSchema(fixture -> {
                insertLegacyReceipt(fixture.source(), 1, "pac-b0:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                        "PAC_BOOTSTRAP_METADATA_B0", valid.replace(PAC_OPERATION, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), false);
                insertLegacyReceipt(fixture.source(), 2, bad.key(), bad.type(), bad.receipt(), bad.deleted());
                assertThatThrownBy(() -> runner().run(List.of(new EgonColaDdlTargetBO("pac", fixture.schema(),
                        RoleEnum.MASTER_DATA, fixture.source(), fixture.current(), routeFingerprint()))))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("DDL_TARGET_FAILED");
                try (var connection = fixture.source().getConnection(); var query = connection.createStatement();
                     var rows = query.executeQuery("SELECT to_regclass('"+fixture.schema()+".ddc_metadata_bootstrap_receipt_t0') IS NULL, "
                             + "NOT EXISTS(SELECT 1 FROM ddl_history WHERE version='20261123_001'), "
                             + "(SELECT count(*) FROM ddc_operation_log_t0)")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                    assertThat(rows.getBoolean(2)).isTrue();
                    assertThat(rows.getInt(3)).isEqualTo(2);
                }
            });
        }
    }

    private static void withPacSchema(PacFixtureCheck check) throws Exception {
        var current = manifest();
        assertThat(current.scripts().getLast().version()).isEqualTo("20261123_001");
        var previous = new EgonColaDdlManifestBO(current.family(), current.scripts().subList(0,current.scripts().size()-1),
                current.freshBaselineVersion());
        try (OwnedDatabase database = freshDatabase()) {
            var source = database.source();
            runner().run(List.of(new EgonColaDdlTargetBO("pac","public",RoleEnum.MASTER_DATA,source,previous,routeFingerprint())));
            check.verify(new PacFixture(source,"public",previous,current));
        }
    }

    private static void insertLegacyReceipt(DataSource source, long id, String key, String type, String receipt,
                                            boolean deleted) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO ddc_operation_log_t0(id,tenant_id,create_user_id,create_time,update_user_id,update_time,version,"
                        + "public_id,operation_type,operation_content,created_at,deleted_at) "
                        + "VALUES (?,20,'fixture',clock_timestamp(),'fixture',clock_timestamp(),0,?,?,?,"
                        + "timestamp '2026-10-25 23:59:59.123456',?)")) {
            statement.setLong(1,id); statement.setString(2,key); statement.setString(3,type); statement.setString(4,receipt);
            statement.setObject(5,deleted ? java.time.LocalDateTime.parse("2026-10-26T00:00:00") : null);
            statement.executeUpdate();
        }
    }

    private static String resumeBootstrap(Connection connection, String schema, String original) throws Exception {
        return initializeBootstrap(connection,schema,PAC_OPERATION,original);
    }

    private static String initializeBootstrap(Connection connection,String schema,String operation,String expected) throws Exception {
        String json = """
                {"schemaVersion":1,"operationId":"%s","target":{"catalogScopeId":"10","tenantId":"20",
                "businessCode":"xingyuan","applicationCode":"tianshu","namespaceCode":"default","environment":"test"},
                "connectionFile":"/tmp/unused-pac-it.json","actorId":"operator-1","ddcBusinessId":"ddc-business-1",
                "ddcApplicationId":"ddc-app-1","ids":{"business":"101","environment":"102","application":"103",
                "namespace":"104","binding":"105","metadataScope":"106","audit":"107"},
                "sourceImportGrants":[{"id":"108","producerId":"tianshu-java","issuer":"https://idp.example/tenant",
                "clientId":"rbac-publisher","resourceUri":"https://metadata.example"}]}
                """.formatted(operation);
        Class<?> cli = Class.forName("top.egon.cola.component.tianshu.admin.bootstrap.DdcPermissionMetadataBootstrapCli");
        var parse = cli.getDeclaredMethod("readCommand",byte[].class); parse.setAccessible(true);
        Object command = parse.invoke(null,(Object)json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Class<?> repository = Class.forName("top.egon.cola.component.tianshu.admin.bootstrap.DdcPermissionMetadataBootstrapRepository");
        var constructor = repository.getDeclaredConstructor(String.class,Clock.class); constructor.setAccessible(true);
        var initialize = repository.getDeclaredMethod("initialize",Connection.class,command.getClass(),String.class,String.class);
        initialize.setAccessible(true);
        return (String)initialize.invoke(constructor.newInstance(schema,Clock.systemUTC()),connection,command,PAC_DIGEST,expected);
    }

    private record PacFixture(PGSimpleDataSource source,String schema,EgonColaDdlManifestBO previous,EgonColaDdlManifestBO current) { }
    private record BadPacCandidate(String key,String type,String receipt,boolean deleted) { }
    @FunctionalInterface private interface PacFixtureCheck { void verify(PacFixture fixture) throws Exception; }

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void blankSchemaInitializesAndReentersWhileDriftIsRejectedAndUnknownCommitIsVerified() throws Exception {
        try (OwnedDatabase database = freshDatabase()) {
            PGSimpleDataSource dataSource = database.source();
            String schema = "public";
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
        }
    }

    @Test
    void physicalPrimaryReadinessRejectsSameNameWeakenedBootTenantConstraint() throws Exception {
        try (OwnedDatabase database = freshDatabase()) {
        PGSimpleDataSource primary = database.source();
        EgonColaDdlManifestBO manifest = manifest();
        String fingerprint = routeFingerprint();
        runner().run(List.of(new EgonColaDdlTargetBO("tianshu-it", "public", RoleEnum.MASTER_DATA,
                primary, manifest, fingerprint)));
        DdcDatabaseReadyValidator.validateTianshuSchema(primary, fingerprint, manifest);
        try (var connection = primary.getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ddc_metadata_bootstrap_receipt_t0 RENAME CONSTRAINT "
                    + "ck_metadata_bootstrap_receipt_length TO fixture_original_length");
            try {
                for (String weak : List.of("char_length(receipt)<=1048576", "NOT(char_length(receipt) BETWEEN 1 AND 1048576)",
                        "char_length(receipt)>=1048576 AND char_length(receipt)<=1")) {
                    statement.execute("ALTER TABLE ddc_metadata_bootstrap_receipt_t0 ADD CONSTRAINT "
                            + "ck_metadata_bootstrap_receipt_length CHECK("+weak+")");
                    try {
                        assertThatThrownBy(() -> DdcDatabaseReadyValidator.validateTianshuSchema(primary,fingerprint,manifest))
                                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TIANSHU_PAC_RECEIPT_CONSTRAINT_MISMATCH");
                    } finally {
                        statement.execute("ALTER TABLE ddc_metadata_bootstrap_receipt_t0 DROP CONSTRAINT ck_metadata_bootstrap_receipt_length");
                    }
                }
            } finally {
                statement.execute("ALTER TABLE ddc_metadata_bootstrap_receipt_t0 RENAME CONSTRAINT "
                        + "fixture_original_length TO ck_metadata_bootstrap_receipt_length");
            }
        }
        DdcDatabaseReadyValidator.validateTianshuSchema(primary,fingerprint,manifest);

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
    }

    @Test
    void multiTargetRunResumesAfterSecondTargetRollsBackNewSqlAndItsHistory() throws Exception {
        try (OwnedDatabase databaseA = freshDatabase(); OwnedDatabase databaseB = freshDatabase()) {
            var manifest = manifest();
            var previous = new EgonColaDdlManifestBO(manifest.family(), manifest.scripts().subList(0, manifest.scripts().size() - 1),
                    manifest.freshBaselineVersion());
            var first = databaseA.source();
            var second = databaseB.source();
            String schema = "public";
            try (Connection a = first.getConnection(); Connection b = second.getConnection()) {
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
                        .singleElement().satisfies(result -> assertThat(result.version()).isEqualTo("20261123_001"));
                assertLatestMigration(b, schema, true);
            }
        }
    }

    private static OwnedDatabase freshDatabase() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("TIANSHU_AI_PERSISTENCE_IT_ENABLED")),
                "requires an explicitly configured disposable PostgreSQL DDL target");
        var admin = dataSource();
        assertThat(admin.getServerNames()).containsExactly("127.0.0.1");
        assertThat(admin.getDatabaseName()).isEqualTo("postgres");
        String name = "tianshu_pac_it_"+UUID.randomUUID().toString().replace("-", "");
        try (var connection = admin.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE "+name);
        }
        var source = dataSource();
        source.setDatabaseName(name);
        source.setCurrentSchema("public");
        return new OwnedDatabase(admin,source,name);
    }

    private record OwnedDatabase(PGSimpleDataSource admin,PGSimpleDataSource source,String name) implements AutoCloseable {
        @Override public void close() throws SQLException {
            try (var connection = admin.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE "+name);
            }
        }
    }

    private static void assertLatestMigration(Connection connection, String schema, boolean applied) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL, EXISTS (SELECT 1 FROM "
                + schema + ".ddl_history WHERE version='20261123_001')")) {
            statement.setString(1, schema + ".ddc_metadata_bootstrap_receipt_t0");
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
