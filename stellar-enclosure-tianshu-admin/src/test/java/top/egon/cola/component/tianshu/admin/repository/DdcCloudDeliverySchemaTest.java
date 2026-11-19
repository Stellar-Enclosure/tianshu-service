package top.egon.cola.component.tianshu.admin.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlManifestBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlResult;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlTargetBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaPostgreDdlRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The cloud delivery migration must extend the existing managed lineage, never replace it. */
class DdcCloudDeliverySchemaTest {

    private static final String SQL = "db/egon-mp/V20261117_001__tianshu_cloud_delivery_manifests.sql";
    private static final String MANIFEST = "db/egon-mp/repository-manifest.json";
    private static final List<String> HISTORICAL_VERSIONS = List.of(
            "20260922_001", "20260928_001", "20261017_001", "20261018_001",
            "20261022_001", "20261023_001", "20261028_001", "20261116_001");
    private static final List<String> HISTORICAL_CHECKSUMS = List.of(
            "c2fdefad7c3ed1244757f9b6e4d78df82ad51d9e4870e9e47eda5f57e9370678",
            "3700ddc8c7653c275cd0dd8d53ff6d7ae449d99f95da13e2ca34b52da80a1a9b",
            "ea388ac5115c1e6e35604cf52da16599c17e8be43fbe41d0e8cddb3d3a2ecac6",
            "acdf95ef39c35ce673558b6027b4641bbc7f0cad674e7e902c7c695f81b3a6b1",
            "7fa986be12478e6c5df90ad17328409b969616f5f9ff5d7897a858d4ba3421c9",
            "5772a82da5f28e3e2a188737fce57aa4d8ade2c9c8a53f205fad6dde6690280a",
            "5d8dae6b95cd51b22407802c80583e788819300079942e7726d2798cffdde04e",
            "06a0b00da3064d97c85f88be37377a742d03c8203791129ed04906b6dcae2df1");

    @Test
    void appendsCloudDeliveryMigrationWithoutRewritingAnyHistoricalScript() throws Exception {
        JsonNode manifest;
        try (var input = getClass().getClassLoader().getResourceAsStream(MANIFEST)) {
            assertThat(input).as("managed DDL manifest").isNotNull();
            manifest = new ObjectMapper().readTree(input);
        }
        assertThat(manifest.path("family").asText()).isEqualTo("tianshu");
        assertThat(manifest.path("freshBaselineVersion").asText()).isEqualTo("20261018_001");
        JsonNode scripts = manifest.path("scripts");
        assertThat(scripts.size()).as("verified cloud delivery prefix").isGreaterThanOrEqualTo(9);
        for (int index = 0; index < HISTORICAL_VERSIONS.size(); index++) {
            JsonNode entry = scripts.get(index);
            assertThat(entry.path("version").asText()).isEqualTo(HISTORICAL_VERSIONS.get(index));
            assertThat(entry.path("sha256").asText()).isEqualTo(HISTORICAL_CHECKSUMS.get(index));
            assertThat(sha256(resource(entry.path("path").asText())))
                    .as(HISTORICAL_VERSIONS.get(index)).isEqualTo(HISTORICAL_CHECKSUMS.get(index));
        }
        JsonNode appended = scripts.get(8);
        assertThat(appended.path("version").asText()).isEqualTo("20261117_001");
        assertThat(appended.path("path").asText()).isEqualTo(SQL);
        byte[] migration = resource(SQL);
        assertThat(appended.path("sha256").asText()).isEqualTo(sha256(migration));
        assertThat(new String(migration, StandardCharsets.UTF_8)).doesNotContain(
                "DROP TABLE", "TRUNCATE", "CREATE TABLE IF NOT EXISTS", "public.", "UPDATE ddc_publish_task");
    }

    @Test
    void postgresEnforcesHistoricalNullRulesReceiptIsolationAndOneImmutableBootBinding() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("TIANSHU_CLOUD_DELIVERY_SCHEMA_IT_ENABLED")),
                "requires the explicitly authorized disposable PostgreSQL 18.3 cloud-schema fixture");
        PGSimpleDataSource source = fixtureDataSource();
        String schema = "cloud_delivery_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try (Connection owner = source.getConnection(); var statement = owner.createStatement();
                var validators = Validation.buildDefaultValidatorFactory()) {
            try (var version = statement.executeQuery("SHOW server_version_num")) {
                assertThat(version.next()).isTrue();
                assertThat(version.getString(1)).isEqualTo("180003");
            }
            assertThat(owner.getCatalog()).isEqualTo("tianshu_step18_schema");
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
            try {
                source.setCurrentSchema(schema);
                EgonColaDdlManifestBO full;
                try (var input = getClass().getClassLoader().getResourceAsStream(MANIFEST)) {
                    full = new ObjectMapper().readValue(input, EgonColaDdlManifestBO.class);
                }
                var historical = new EgonColaDdlManifestBO(full.family(), full.scripts().subList(0, 8), full.freshBaselineVersion());
                var runner = new EgonColaPostgreDdlRunner(new ValidationUtils(validators.getValidator()),
                        new PathMatchingResourcePatternResolver(), Clock.systemUTC(), Duration.ofSeconds(10), Duration.ofSeconds(30));
                runner.run(List.of(target(source, schema, historical)));
                try (Connection connection = source.getConnection()) {
                    seedHistoricalRows(connection);
                }
                List<EgonColaDdlResult> applied = runner.run(List.of(target(source, schema, full)));
                assertThat(applied.stream().filter(result -> result.status() == EgonColaDdlResult.StatusEnum.APPLIED)
                        .map(EgonColaDdlResult::version).toList()).containsExactly("20261117_001");
                assertThat(runner.run(List.of(target(source, schema, full))))
                        .allSatisfy(result -> assertThat(result.status()).isEqualTo(EgonColaDdlResult.StatusEnum.SKIPPED));
                try (Connection connection = source.getConnection()) {
                    assertCatalogue(connection);
                    assertHistoricalAndManifestRules(connection);
                    assertReceiptRules(connection);
                }
                assertBootBindingRaceAndImmutability(source);
            } finally {
                statement.execute("DROP SCHEMA \"" + schema + "\" CASCADE");
            }
        }
    }

    private static EgonColaDdlTargetBO target(PGSimpleDataSource source, String schema, EgonColaDdlManifestBO manifest) {
        return new EgonColaDdlTargetBO("tianshu-cloud-schema-it", schema,
                EgonColaDdlTargetBO.RoleEnum.MASTER_DATA, source, manifest, "c".repeat(64));
    }

    private static PGSimpleDataSource fixtureDataSource() {
        String url = required("TIANSHU_CLOUD_DELIVERY_SCHEMA_IT_JDBC_URL");
        URI location;
        try {
            location = URI.create(url.startsWith("jdbc:") ? url.substring(5) : "");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("CLOUD_SCHEMA_DISPOSABLE_TARGET_REQUIRED");
        }
        if (!"postgresql".equals(location.getScheme()) || !"127.0.0.1".equals(location.getHost())
                || location.getPort() < 1 || !"/tianshu_step18_schema".equals(location.getPath())
                || location.getRawQuery() != null || location.getFragment() != null || location.getUserInfo() != null
                || !Files.isRegularFile(Path.of("/tmp/stellar-cilium-execution/step18-pg.active"))) {
            throw new IllegalStateException("CLOUD_SCHEMA_DISPOSABLE_TARGET_REQUIRED");
        }
        var source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(required("TIANSHU_CLOUD_DELIVERY_SCHEMA_IT_DB_USERNAME"));
        source.setPassword(required("TIANSHU_CLOUD_DELIVERY_SCHEMA_IT_DB_PASSWORD"));
        source.setConnectTimeout(5);
        source.setSocketTimeout(30);
        return source;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("CLOUD_SCHEMA_FIXTURE_CONFIGURATION_REQUIRED");
        }
        return value;
    }

    private static void seedHistoricalRows(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO ddc_config_item_t0
                      (id,tenant_id,create_user_id,create_time,update_user_id,update_time,public_id,app_code,env,
                       config_key,value_type,current_version,created_at,updated_at,biz_code)
                    VALUES (100,1001,'schema-it',CURRENT_TIMESTAMP,'schema-it',CURRENT_TIMESTAMP,'item-100','reader','prod',
                            'application.yml','YAML',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'reading')
                    """);
            statement.execute(configVersion(110, 1, null));
            statement.execute("""
                    INSERT INTO ddc_namespace_t0
                      (id,tenant_id,create_user_id,create_time,update_user_id,update_time,public_id,namespace,
                       created_at,updated_at,namespace_code,biz_code)
                    VALUES (200,1001,'schema-it',CURRENT_TIMESTAMP,'schema-it',CURRENT_TIMESTAMP,'ns-200','runtime',
                            CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'runtime','reading')
                    """);
            statement.execute("""
                    INSERT INTO ddc_si_resource_t0
                      (id,tenant_id,create_user_id,create_time,update_user_id,update_time,biz_code,namespace_id,
                       env_code,app_code,kind,resource_key,display_name,lifecycle_status,source_type,spec_json,content_sha256)
                    VALUES (210,1001,'schema-it',CURRENT_TIMESTAMP,'schema-it',CURRENT_TIMESTAMP,'reading',200,
                            'prod','reader','AGENT','schema-agent','Schema Agent','DRAFT','CONSOLE','{}',repeat('a',64))
                    """);
            statement.execute(siVersion(220, 1, null));
        }
    }

    private static String configVersion(long id, long revision, String manifest) {
        String extraColumn = manifest == null ? "" : ",manifest_json";
        String extraValue = manifest == null ? "" : ",'" + manifest.replace("'", "''") + "'::jsonb";
        return "INSERT INTO ddc_config_version_t0 (id,tenant_id,create_user_id,create_time,update_user_id,update_time,"
                + "public_id,config_id,app_code,env,config_key,config_revision,created_at" + extraColumn + ") VALUES ("
                + id + ",1001,'schema-it',CURRENT_TIMESTAMP,'schema-it',CURRENT_TIMESTAMP,'cv-" + id
                + "',100,'reader','prod','application.yml'," + revision + ",CURRENT_TIMESTAMP" + extraValue + ")";
    }

    private static String siVersion(long id, long revision, String manifest) {
        String extraColumn = manifest == null ? "" : ",dependency_manifest_json";
        String extraValue = manifest == null ? "" : ",'" + manifest.replace("'", "''") + "'::jsonb";
        return "INSERT INTO ddc_si_version_t0 (id,tenant_id,create_user_id,create_time,update_user_id,update_time,"
                + "resource_id,version_no,kind,spec_json,digest_sha256,published_time" + extraColumn + ") VALUES ("
                + id + ",1001,'schema-it',CURRENT_TIMESTAMP,'schema-it',CURRENT_TIMESTAMP,210," + revision
                + ",'AGENT','{}',repeat('a',64),CURRENT_TIMESTAMP" + extraValue + ")";
    }

    private static void assertCatalogue(Connection connection) throws SQLException {
        Map<String, String> receipt = columns(connection, "ddc_config_process_receipt_t0");
        assertThat(receipt.keySet()).containsExactlyInAnyOrder("id", "tenant_id", "create_user_id", "create_time",
                "update_user_id", "update_time", "deleted_at", "version", "config_id", "config_revision", "scope_json",
                "target_binding_id", "target_revision", "cluster_id", "k8s_namespace", "workload_uid", "pod_uid",
                "container_name", "container_id", "boot_id", "content_digest", "applied_revision", "applied_digest",
                "restart_digest", "result", "reason_code", "reason_message", "report_seq", "receipt_digest",
                "reported_at", "applied_at", "last_received_at");
        assertThat(receipt).containsEntry("scope_json", "jsonb").containsEntry("reported_at", "timestamp(6) with time zone")
                .containsEntry("deleted_at", "timestamp(6) without time zone").containsEntry("content_digest", "character(64)");
        assertThat(columns(connection, "ddc_config_boot_binding_t0").keySet()).containsExactlyInAnyOrder(
                "id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version",
                "cluster_id", "pod_uid", "container_name", "container_id", "boot_id", "workload_uid", "k8s_namespace");
        try (var query = connection.prepareStatement("""
                SELECT conname,convalidated FROM pg_constraint
                WHERE conname IN ('ck_config_version_manifest_shape','ck_config_version_manifest_required',
                  'ck_si_version_dependency_manifest_shape','ck_si_version_dependency_manifest_required')
                  AND connamespace=current_schema()::regnamespace
                """)) {
            Map<String, Boolean> constraints = new LinkedHashMap<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) { constraints.put(rows.getString(1), rows.getBoolean(2)); }
            }
            assertThat(constraints).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "ck_config_version_manifest_shape", true, "ck_config_version_manifest_required", false,
                    "ck_si_version_dependency_manifest_shape", true, "ck_si_version_dependency_manifest_required", false));
        }
        try (var query = connection.createStatement(); var rows = query.executeQuery("""
                SELECT indexname FROM pg_indexes WHERE schemaname=current_schema()
                AND tablename IN ('ddc_config_process_receipt_t0','ddc_config_boot_binding_t0')
                """)) {
            List<String> indexes = new ArrayList<>();
            while (rows.next()) { indexes.add(rows.getString(1)); }
            assertThat(indexes).containsExactlyInAnyOrder("pk_config_process_receipt", "uk_config_process_receipt_incarnation",
                    "ix_config_process_receipt_target", "ix_config_process_receipt_retention",
                    "pk_config_boot_binding", "uk_config_boot_binding_incarnation");
        }
    }

    private static Map<String, String> columns(Connection connection, String table) throws SQLException {
        try (var query = connection.prepareStatement("SELECT attname,format_type(atttypid,atttypmod) FROM pg_attribute "
                + "WHERE attrelid=?::regclass AND attnum>0 AND NOT attisdropped ORDER BY attnum")) {
            query.setString(1, table);
            Map<String, String> columns = new LinkedHashMap<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) { columns.put(rows.getString(1), rows.getString(2)); }
            }
            return columns;
        }
    }

    private static void assertHistoricalAndManifestRules(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT manifest_json FROM ddc_config_version_t0 WHERE id=110 UNION ALL "
                        + "SELECT dependency_manifest_json FROM ddc_si_version_t0 WHERE id=220")) {
            int preserved = 0;
            while (rows.next()) { assertThat(rows.getObject(1)).isNull(); preserved++; }
            assertThat(preserved).isEqualTo(2);
        }
        connection.setAutoCommit(false);
        try {
            reject(connection, "23514", () -> execute(connection, "UPDATE ddc_config_version_t0 SET version=version+1 WHERE id=110"));
            reject(connection, "23514", () -> execute(connection, "UPDATE ddc_si_version_t0 SET index_status='READY' WHERE id=220"));
            reject(connection, "23514", () -> execute(connection, configVersion(112, 3, null)));
            reject(connection, "23514", () -> execute(connection, siVersion(222, 3, null)));
            execute(connection, configVersion(111, 2, "{\"schemaVersion\":\"1\"}"));
            execute(connection, siVersion(221, 2, "{\"schemaVersion\":\"1\"}"));
            for (String invalid : List.of("{}", "[]", "null", "{\"schemaVersion\":1}", "{\"schemaVersion\":null}",
                    "{\"schemaVersion\":\"0\"}", "{\"schemaVersion\":\"01\"}", "{\"schemaVersion\":\"9223372036854775808\"}",
                    "{\"schemaVersion\":\"1\",\"padding\":\"" + "界".repeat(350_000) + "\"}")) {
                reject(connection, "23514", () -> setJson(connection, "UPDATE ddc_config_version_t0 SET manifest_json=?::jsonb WHERE id=111", invalid));
                reject(connection, "23514", () -> setJson(connection, "UPDATE ddc_si_version_t0 SET dependency_manifest_json=?::jsonb WHERE id=221", invalid));
            }
            connection.commit();
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private static void assertReceiptRules(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try {
            String receipt = """
                    INSERT INTO ddc_config_process_receipt_t0
                      (id,tenant_id,create_user_id,create_time,update_user_id,update_time,config_id,config_revision,
                       scope_json,target_binding_id,target_revision,cluster_id,k8s_namespace,workload_uid,pod_uid,
                       container_name,container_id,boot_id,content_digest,applied_revision,applied_digest,restart_digest,
                       result,report_seq,receipt_digest,reported_at,applied_at,last_received_at)
                    VALUES (300,1001,'schema-it',CURRENT_TIMESTAMP,'schema-it',CURRENT_TIMESTAMP,100,2,
                            '{}','reader-prod',1,'cluster-prod','reader','workload-1','pod-1','application',
                            'containerd://one','boot-1',repeat('a',64),2,repeat('a',64),repeat('b',64),
                            'APPLIED',1,repeat('c',64),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """;
            execute(connection, receipt);
            reject(connection, "23505", () -> execute(connection, receipt.replace("(300,1001", "(301,1001")));
            reject(connection, "23503", () -> execute(connection, receipt.replace("(300,1001", "(302,1002")));
            for (String mutation : List.of("report_seq=0", "deleted_at=CURRENT_TIMESTAMP", "pod_uid='非ASCII'",
                    "container_id=''", "k8s_namespace='Invalid_Name'", "applied_revision=1", "applied_digest=NULL",
                    "applied_at=NULL", "reason_code='UNEXPECTED'", "result='UNKNOWN'", "scope_json='[]'::jsonb",
                    "receipt_digest='bad'", "last_received_at=create_time-INTERVAL '1 second'")) {
                reject(connection, "23514", () -> execute(connection, "UPDATE ddc_config_process_receipt_t0 SET " + mutation + " WHERE id=300"));
            }
            execute(connection, "UPDATE ddc_config_process_receipt_t0 SET result='RESTART_REQUIRED',reason_code='RESTART_DIGEST_MISMATCH',"
                    + "applied_revision=1,applied_at=NULL WHERE id=300");
            reject(connection, "23514", () -> execute(connection, "UPDATE ddc_config_process_receipt_t0 SET applied_revision=NULL WHERE id=300"));
            connection.commit();
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private static void assertBootBindingRaceAndImmutability(PGSimpleDataSource source) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> claimBoot(source, start, 400, "boot-a"));
            var second = workers.submit(() -> claimBoot(source, start, 401, "boot-b"));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "23505");
        }
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT id,boot_id FROM ddc_config_boot_binding_t0")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isIn(400L, 401L);
                assertThat(rows.getString(2)).isIn("boot-a", "boot-b");
                assertThat(rows.next()).isFalse();
            }
            connection.setAutoCommit(false);
            try {
                reject(connection, "55000", () -> execute(connection, "UPDATE ddc_config_boot_binding_t0 SET boot_id='replacement'"));
                reject(connection, "55000", () -> execute(connection, "UPDATE ddc_config_boot_binding_t0 SET id=id+100"));
                reject(connection, "23514", () -> execute(connection, bootInsert(402, "boot-c").replace("402,0", "402,1")));
                reject(connection, "23514", () -> execute(connection, bootInsert(402, "boot-c").replace("'pod-race'", "''")));
                reject(connection, "23514", () -> execute(connection, bootInsert(402, "boot-c").replace("'containerd://race'", "'非ASCII'")));
                reject(connection, "23502", () -> execute(connection, bootInsert(402, "boot-c").replace("'workload-race'", "NULL")));
                connection.rollback();
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static String claimBoot(PGSimpleDataSource source, CountDownLatch start, long id, String boot) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try (Connection connection = source.getConnection()) {
            execute(connection, bootInsert(id, boot));
            return "CREATED";
        } catch (SQLException failure) {
            if (!"23505".equals(failure.getSQLState())) { throw failure; }
            return failure.getSQLState();
        }
    }

    private static String bootInsert(long id, String boot) {
        return "INSERT INTO ddc_config_boot_binding_t0 (id,tenant_id,create_user_id,create_time,update_user_id,update_time,"
                + "cluster_id,pod_uid,container_name,container_id,boot_id,workload_uid,k8s_namespace) VALUES ("
                + id + ",0,'schema-it',CURRENT_TIMESTAMP,'schema-it',CURRENT_TIMESTAMP,'cluster-prod','pod-race',"
                + "'application','containerd://race','" + boot + "','workload-race','reader')";
    }

    private static int execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { return statement.executeUpdate(sql); }
    }

    private static void setJson(Connection connection, String sql, String json) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, json);
            statement.executeUpdate();
        }
    }

    private static void reject(Connection connection, String state, org.assertj.core.api.ThrowableAssert.ThrowingCallable action)
            throws SQLException {
        var savepoint = connection.setSavepoint();
        try {
            assertThatThrownBy(action).isInstanceOf(SQLException.class)
                    .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo(state));
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private static byte[] resource(String path) throws Exception {
        try (var input = DdcCloudDeliverySchemaTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("managed resource %s", path).isNotNull();
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
