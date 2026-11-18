package top.egon.cola.component.tianshu.admin.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import jakarta.validation.Validation;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties;
import top.egon.cola.component.common.mybatis.sharding.bootstrap.EgonColaShardingYamlLoader;
import top.egon.cola.component.common.mybatis.sharding.bootstrap.EgonColaShardingTopologyValidator;
import top.egon.cola.component.common.mybatis.sharding.strategy.EgonColaShardingStrategyFactory;
import top.egon.cola.component.common.mybatis.sharding.strategy.EgonColaSingleTableShardingStrategy;
import top.egon.cola.component.common.mybatis.sharding.strategy.EgonColaStandardTenantIdShardingStrategy;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlManifestBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlResult;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlTargetBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaPostgreDdlRunner;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Clock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Properties;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcI18nTopologyUpgradeTest {
    private static final String OLD = "1".repeat(64);
    private static final String CURRENT = "2".repeat(64);
    private EgonColaDdlManifestBO tianshu;
    private EgonColaDdlManifestBO outbox;
    private Store store;
    private final EgonColaPostgreDdlRunner runner = mock(EgonColaPostgreDdlRunner.class);
    private final Runnable oldOutboxReady = mock(Runnable.class);
    private final Runnable oldTopologyReady = mock(Runnable.class);
    private final Runnable newTopologyReady = mock(Runnable.class);

    @BeforeEach void setup() throws Exception {
        tianshu = manifest("migration/tianshu-i18n-20261022-manifest.json");
        outbox = manifest("db/egon-outbox-mp/manifest.json");
        store = new Store();
        store.rows.put("public", history(tianshu, 0, false, OLD));
        store.rows.put("egon_outbox", history(outbox, 0, true, OLD));
        when(runner.run(anyList())).thenAnswer(call -> {
            EgonColaDdlTargetBO target = call.<List<EgonColaDdlTargetBO>>getArgument(0).getFirst();
            assertThat(target.routeFingerprint()).isEqualTo(OLD);
            assertThat(target.dataSource()).isSameAs(store.source);
            int start = tianshu.startingIndex(store.rows.get("public").getFirst().version());
            boolean applied = !store.tableExists;
            if (applied) {
                store.rows.get("public").add(row(tianshu.scripts().getLast(), OLD));
                store.tableExists = true;
            }
            return results(start, applied);
        });
    }

    @Test void rejectsPermissionMetadataManifestInsteadOfReusingSingleTableUpgrade() throws Exception {
        tianshu = manifest("db/egon-mp/repository-manifest.json");
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessage("TIANSHU_I18N_UPGRADE_PLAN_INVALID");
        verifyNoInteractions(runner, oldOutboxReady, oldTopologyReady, newTopologyReady);
    }

    @Test void listTemplateUpgradeAcceptsOnlyTheCompletePriorHistoryAndPromotesBothFingerprints() throws Exception {
        tianshu = DdcPersistenceMigrationCli.manifestForMode(DdcPersistenceMigrationCli.Mode.UPGRADE_LIST_TEMPLATES,
                manifest("db/egon-mp/repository-manifest.json"));
        store.rows.put("public", history(tianshu, 3, false, OLD));
        upgrade().listTemplates().upgrade();
        assertThat(store.rows.get("public")).extracting(Row::version)
                .containsExactly("20261018_001", "20261022_001", "20261023_001", "20261028_001");
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(CURRENT));
        assertThat(store.rows.get("egon_outbox")).allMatch(row -> row.fingerprint().equals(CURRENT));
        verify(newTopologyReady).run();
    }

    @Test void listTemplateUpgradeRejectsPartiallyPresentTablesBeforeDdl() throws Exception {
        tianshu = DdcPersistenceMigrationCli.manifestForMode(DdcPersistenceMigrationCli.Mode.UPGRADE_LIST_TEMPLATES,
                manifest("db/egon-mp/repository-manifest.json"));
        store.rows.put("public", history(tianshu, 3, false, OLD));
        store.partialTables = true;
        assertThatThrownBy(() -> upgrade().listTemplates().upgrade()).hasMessageContaining("TABLE_HISTORY_MISMATCH");
        verifyNoInteractions(runner);
    }

    @Test void cloudDeliveryPromotesOnlyTheVerifiedEightScriptPredecessorAndBothHistories() throws Exception {
        cloudPredecessor();
        upgrade().cloudDelivery().upgrade();
        assertThat(store.rows.get("public")).extracting(Row::version).containsExactly(
                "20261018_001", "20261022_001", "20261023_001", "20261028_001", "20261116_001", "20261117_001");
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(CURRENT));
        assertThat(store.rows.get("egon_outbox")).allMatch(row -> row.fingerprint().equals(CURRENT));
        assertThat(store.tableChecks).allSatisfy(sql -> assertThat(sql)
                .contains("public.ddc_config_process_receipt_t0", "public.ddc_config_boot_binding_t0")
                .doesNotContain("ddc_config_boot_binding_t0_t0"));
        assertThat(store.updateSql).containsExactly(
                "UPDATE egon_outbox.ddl_history SET route_fingerprint = ? WHERE route_fingerprint = ?",
                "UPDATE public.ddl_history SET route_fingerprint = ? WHERE route_fingerprint = ?");
        verify(oldTopologyReady).run();
        verify(newTopologyReady).run();
    }

    @Test void cloudDeliveryRejectsRewrittenPredecessorAndPartialHistoryBeforeDdl() throws Exception {
        cloudPredecessor();
        var scripts = new ArrayList<>(tianshu.scripts());
        var old = scripts.get(7);
        scripts.set(7, new EgonColaDdlManifestBO.ScriptBO(old.version(), old.path(), "0".repeat(64)));
        tianshu = new EgonColaDdlManifestBO("tianshu", scripts, "20261018_001");
        assertThatThrownBy(() -> upgrade().cloudDelivery().upgrade()).hasMessageContaining("PREDECESSOR_MANIFEST_MISMATCH");
        verifyNoInteractions(store.source, runner);
        cloudPredecessor();
        store.rows.get("public").removeLast();
        assertThatThrownBy(() -> upgrade().cloudDelivery().upgrade()).hasMessageContaining("MANAGED_HISTORY_MISMATCH");
        verifyNoInteractions(runner);
    }

    @Test void cloudDeliveryRejectsPartiallyPresentNewTablesAndInvalidNewMetadata() throws Exception {
        cloudPredecessor();
        store.partialTables = true;
        assertThatThrownBy(() -> upgrade().cloudDelivery().upgrade()).hasMessageContaining("TABLE_HISTORY_MISMATCH");
        verifyNoInteractions(runner);
        store.partialTables = false;
        doThrow(new IllegalStateException("CLOUD_METADATA_INVALID")).when(oldTopologyReady).run();
        assertThatThrownBy(() -> upgrade().cloudDelivery().upgrade()).hasMessage("CLOUD_METADATA_INVALID");
        assertThat(store.rows.get("public")).hasSize(6).allMatch(row -> row.fingerprint().equals(OLD));
        assertThat(store.rows.get("egon_outbox")).allMatch(row -> row.fingerprint().equals(OLD));
        assertThat(store.updateSql).isEmpty();
    }

    @Test void cloudDeliveryRetainsOldFingerprintsAfterCasFailureThenResumesWithoutSqlRewrite() throws Exception {
        cloudPredecessor();
        store.failPublicUpdate = true;
        assertThatThrownBy(() -> upgrade().cloudDelivery().upgrade()).hasMessageContaining("HISTORY_CAS_FAILED");
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(OLD));
        assertThat(store.rows.get("egon_outbox")).allMatch(row -> row.fingerprint().equals(OLD));
        store.failPublicUpdate = false;
        upgrade().cloudDelivery().upgrade();
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(CURRENT));
        clearInvocations(runner, oldOutboxReady, oldTopologyReady, newTopologyReady);
        upgrade().cloudDelivery().upgrade();
        verifyNoInteractions(runner, oldOutboxReady, oldTopologyReady);
        verify(newTopologyReady).run();
    }

    private void cloudPredecessor() throws Exception {
        tianshu = manifest("db/egon-mp/repository-manifest.json");
        store.rows.put("public", history(tianshu, 3, false, OLD));
    }

    @Test void upgradesFullLegacyHistoryAndUpdatesOnlyBothFingerprintsAtomically() throws Exception {
        upgrade().upgrade();
        assertThat(store.rows.get("public")).hasSize(5).allMatch(row -> row.fingerprint().equals(CURRENT));
        assertThat(store.rows.get("egon_outbox")).allMatch(row -> row.fingerprint().equals(CURRENT));
        assertThat(store.events).containsSubsequence("lock:" + key("egon_outbox"), "lock:" + key("public"),
                "table-lock:egon_outbox", "table-lock:public", "update:egon_outbox", "update:public", "commit");
        assertThat(store.updateSql).containsExactly(
                "UPDATE egon_outbox.ddl_history SET route_fingerprint = ? WHERE route_fingerprint = ?",
                "UPDATE public.ddl_history SET route_fingerprint = ? WHERE route_fingerprint = ?");
        var order = inOrder(oldOutboxReady, runner, oldTopologyReady, newTopologyReady);
        order.verify(oldOutboxReady).run();
        order.verify(runner).run(anyList());
        order.verify(oldTopologyReady).run();
        order.verify(newTopologyReady).run();
        for (int index = 0; index < tianshu.scripts().size(); index++) {
            assertThat(store.rows.get("public").get(index)).isEqualTo(row(tianshu.scripts().get(index), CURRENT));
        }
    }

    @Test void acceptsDeclaredFreshBaselineSuffixWithoutInventingOldHistory() {
        store.rows.put("public", history(tianshu, 3, false, OLD));
        upgrade().upgrade();
        assertThat(store.rows.get("public")).extracting(Row::version).containsExactly("20261018_001", "20261022_001");
    }

    @Test void resumesAfterNewSqlCommittedWithOldFingerprints() {
        store.rows.put("public", history(tianshu, 3, true, OLD));
        store.tableExists = true;
        upgrade().upgrade();
        assertThat(store.rows.get("public")).hasSize(2).allMatch(row -> row.fingerprint().equals(CURRENT));
        verify(runner).run(anyList());
    }

    @Test void alreadyUpgradedStateOnlyValidatesAndDoesNotRewriteHistory() {
        store.rows.put("public", history(tianshu, 3, true, CURRENT));
        store.rows.put("egon_outbox", history(outbox, 0, true, CURRENT));
        store.tableExists = true;
        upgrade().upgrade();
        verifyNoInteractions(runner, oldOutboxReady, oldTopologyReady);
        verify(newTopologyReady).run();
        assertThat(store.updateSql).isEmpty();
    }

    @Test void rejectsIncompleteOlderHistoryBeforeCallingRunner() {
        store.rows.get("public").removeLast();
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("MANAGED_HISTORY_MISMATCH");
        verifyNoInteractions(runner);
        assertThat(store.updateSql).isEmpty();
    }

    @Test void rejectsUnknownVersionChecksumAndNontechnicalTenant() {
        Row first = store.rows.get("public").getFirst();
        for (Row invalid : List.of(new Row(first.script(), first.type(), "20260923_001", first.checksum(), OLD, 0L),
                new Row(first.script(), first.type(), first.version(), "0".repeat(64), OLD, 0L),
                new Row(first.script(), first.type(), first.version(), first.checksum(), OLD, 42L))) {
            store.rows.get("public").set(0, invalid);
            assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("MANAGED_HISTORY_MISMATCH");
        }
        verifyNoInteractions(runner);
    }

    @Test void rejectsEmptyUnmanagedHistoryMixedAndUnrecognizedFingerprints() {
        store.rows.put("public", new ArrayList<>());
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("MANAGED_HISTORY_MISMATCH");
        store.rows.put("public", history(tianshu, 0, false, CURRENT));
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("MIXED_FINGERPRINTS");
        store.rows.put("public", history(tianshu, 0, false, "3".repeat(64)));
        store.rows.put("egon_outbox", history(outbox, 0, true, "3".repeat(64)));
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("FINGERPRINT_MISMATCH");
        store.rows.put("public", history(tianshu, 0, false, OLD));
        store.rows.get("public").set(1, store.rows.get("public").get(1).withFingerprint(CURRENT));
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("MANAGED_HISTORY_MISMATCH");
        verifyNoInteractions(runner);
    }

    @Test void orphanNewTableAndMissingPublishedTableCannotBeAdopted() {
        store.tableExists = true;
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("TABLE_HISTORY_MISMATCH");
        store.tableExists = false;
        store.rows.put("public", history(tianshu, 0, true, OLD));
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("TABLE_HISTORY_MISMATCH");
        verifyNoInteractions(runner);
    }

    @Test void validatesClasspathChecksumsEvenWhenAlreadyUpgraded() {
        var scripts = new ArrayList<>(tianshu.scripts());
        var original = scripts.getFirst();
        scripts.set(0, new EgonColaDdlManifestBO.ScriptBO(original.version(), original.path(), "0".repeat(64)));
        tianshu = new EgonColaDdlManifestBO("tianshu", scripts, "20261018_001");
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("CHECKSUM_MISMATCH");
        verifyNoInteractions(store.source, runner);
    }

    @Test void casFailureRollsBackBothSchemasButRetainsCommittedSqlForRetry() {
        store.failPublicUpdate = true;
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("HISTORY_CAS_FAILED");
        assertThat(store.rows.get("egon_outbox")).allMatch(row -> row.fingerprint().equals(OLD));
        assertThat(store.rows.get("public")).hasSize(5).allMatch(row -> row.fingerprint().equals(OLD));
        assertThat(store.tableExists).isTrue();
        verifyNoInteractions(newTopologyReady);
        store.failPublicUpdate = false;
        upgrade().upgrade();
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(CURRENT));
    }

    @Test void confirmsCommitAfterLostResponseUsingFreshLockedConnection() {
        store.failCommitAfterApplying = true;
        upgrade().upgrade();
        assertThat(store.connections).isEqualTo(3);
        assertThat(store.events).containsSubsequence("commit", "close:2", "open:3", "table-lock:egon_outbox", "table-lock:public");
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(CURRENT));
        verify(newTopologyReady).run();
    }

    @Test void unconfirmedCommitFailsClosedAndCanBeResumed() {
        store.failCommitBeforeApplying = true;
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("UPGRADE_COMMIT_UNKNOWN");
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(OLD));
        verifyNoInteractions(newTopologyReady);
        store.failCommitBeforeApplying = false;
        upgrade().upgrade();
        assertThat(store.rows.get("public")).allMatch(row -> row.fingerprint().equals(CURRENT));
    }

    @Test void unavailableConfirmationCannotClaimSuccessfulUpgrade() {
        store.failCommitAfterApplying = true;
        store.failConfirmationConnection = true;
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessageContaining("UPGRADE_COMMIT_UNKNOWN");
        verifyNoInteractions(newTopologyReady);
        store.failConfirmationConnection = false;
        upgrade().upgrade();
        verify(newTopologyReady).run();
    }

    @Test void concurrentCompletedUpgradeIsSuccessOnlyAfterCompleteCurrentValidation() {
        doAnswer(call -> {
            store.rows.put("public", history(tianshu, 0, true, CURRENT));
            store.rows.put("egon_outbox", history(outbox, 0, true, CURRENT));
            store.tableExists = true;
            throw new IllegalStateException("ROUTE_FINGERPRINT_MISMATCH");
        }).when(runner).run(anyList());
        upgrade().upgrade();
        verify(newTopologyReady).run();
        assertThat(store.updateSql).isEmpty();
    }

    @Test void structuralFailureIsNeverHiddenByCurrentHistory() {
        store.rows.put("public", history(tianshu, 0, true, CURRENT));
        store.rows.put("egon_outbox", history(outbox, 0, true, CURRENT));
        store.tableExists = true;
        doThrow(new IllegalStateException("NEW_SCHEMA_INVALID")).when(newTopologyReady).run();
        assertThatThrownBy(() -> upgrade().upgrade()).hasMessage("NEW_SCHEMA_INVALID");
        verifyNoInteractions(runner);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TIANSHU_CLOUD_UPGRADE_IT_ENABLED", matches = "true")
    void privatePostgresqlCloudUpgradeResumesAndAtomicallyPromotesBothRealHistories() throws Exception {
        Properties fixture = privateFixture();
        var primary = new DriverManagerDataSource(fixture.getProperty("jdbcUrl"),
                fixture.getProperty("username"), fixture.getProperty("password"));
        // This test never drops a schema or a database. The external private harness owns teardown/reset.
        try (var connection = primary.getConnection(); var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT current_database(),pg_is_in_recovery(),current_setting('server_version'),"
                     + "(SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind IN ('r','p')),"
                     + "to_regclass('egon_outbox.ddl_history') IS NULL")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString(1)).isEqualTo("tianshu_step18_upgrade");
            assertThat(row.getBoolean(2)).isFalse();
            assertThat(row.getString(3)).startsWith("18.3");
            assertThat(row.getLong(4)).as("fixture must start empty; harness must reset between runs").isZero();
            assertThat(row.getBoolean(5)).isTrue();
        }
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var validation = new ValidationUtils(validators.getValidator());
            var environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("private-upgrade-fixture", Map.of(
                    "TIANSHU_JDBC_URL", fixture.getProperty("jdbcUrl"), "TIANSHU_DB_USERNAME", fixture.getProperty("username"),
                    "TIANSHU_DB_PASSWORD", fixture.getProperty("password"))));
            new YamlPropertySourceLoader().load("tianshu-persistence", new ClassPathResource("egon-tianshu-persistence.yml"))
                    .forEach(environment.getPropertySources()::addLast);
            var current = Binder.get(environment).bind(EgonColaShardingProperties.PREFIX, Bindable.of(EgonColaShardingProperties.class)).orElseThrow(() -> new IllegalStateException("Missing Tianshu sharding properties"));
            var previous = Binder.get(environment).bind(EgonColaShardingProperties.PREFIX, Bindable.of(EgonColaShardingProperties.class)).orElseThrow(() -> new IllegalStateException("Missing Tianshu sharding properties"));
            var loader = new EgonColaShardingYamlLoader(new EgonColaShardingStrategyFactory(validation, Map.of(
                    "STANDARD_TENANT_ID", new EgonColaStandardTenantIdShardingStrategy(),
                    "SINGLE", new EgonColaSingleTableShardingStrategy())), new DefaultResourceLoader(), environment);
            var validator = new EgonColaShardingTopologyValidator(validation);
            var topology = validator.validate(current, loader.load(current));
            var prior = DdcPersistenceMigrationCli.priorCloudDeliveryTopology(current, previous, topology, loader, validator);
            var full = manifest("db/egon-mp/repository-manifest.json");
            var predecessor = new EgonColaDdlManifestBO(full.family(), full.scripts().subList(0, 8), full.freshBaselineVersion());
            var actualRunner = new EgonColaPostgreDdlRunner(validation, new PathMatchingResourcePatternResolver(),
                    Clock.systemUTC(), Duration.ofSeconds(3), Duration.ofSeconds(30));
            actualRunner.run(List.of(new EgonColaDdlTargetBO("tianshu_0", "public", EgonColaDdlTargetBO.RoleEnum.MASTER_DATA,
                    primary, predecessor, prior.fingerprint())));
            actualRunner.run(List.of(new EgonColaDdlTargetBO("tianshu_0", "egon_outbox", EgonColaDdlTargetBO.RoleEnum.MASTER_DATA,
                    primary, outbox, prior.fingerprint())));
            assertThat(realHistory(primary, "public")).containsExactlyElementsOf(history(predecessor, 3, true, prior.fingerprint()));
            assertThat(realHistory(primary, "egon_outbox")).containsExactlyElementsOf(history(outbox, 0, true, prior.fingerprint()));

            Path target = protectedTargetConfiguration(fixture);
            try {
                try (var connection = primary.getConnection(); var statement = connection.createStatement()) {
                    statement.execute("CREATE FUNCTION public.step18_fail_fingerprint() RETURNS trigger LANGUAGE plpgsql AS $$"
                            + "BEGIN IF NEW.route_fingerprint IS DISTINCT FROM OLD.route_fingerprint THEN "
                            + "RAISE EXCEPTION 'step18 injected promotion failure'; END IF; RETURN NEW; END; $$");
                    statement.execute("CREATE TRIGGER step18_fail_fingerprint BEFORE UPDATE ON public.ddl_history "
                            + "FOR EACH ROW EXECUTE FUNCTION public.step18_fail_fingerprint()");
                }
                try {
                    assertThatThrownBy(() -> DdcPersistenceMigrationCli.main(new String[]{"upgrade-cloud-delivery", "--target-config", target.toString()}))
                            .hasMessageContaining("PROMOTION_FAILED");
                    // SQL + its new history row committed under the predecessor fingerprint; neither history was promoted.
                    assertThat(realHistory(primary, "public")).containsExactlyElementsOf(history(full, 3, true, prior.fingerprint()));
                    assertThat(realHistory(primary, "egon_outbox")).containsExactlyElementsOf(history(outbox, 0, true, prior.fingerprint()));
                    try (var connection = primary.getConnection(); var statement = connection.createStatement();
                         var row = statement.executeQuery("SELECT to_regclass('public.ddc_config_process_receipt_t0') IS NOT NULL,"
                                 + "to_regclass('public.ddc_config_boot_binding_t0') IS NOT NULL")) {
                        assertThat(row.next()).isTrue();
                        assertThat(row.getBoolean(1)).isTrue();
                        assertThat(row.getBoolean(2)).isTrue();
                    }
                } finally {
                    try (var connection = primary.getConnection(); var statement = connection.createStatement()) {
                        statement.execute("DROP TRIGGER step18_fail_fingerprint ON public.ddl_history");
                        statement.execute("DROP FUNCTION public.step18_fail_fingerprint()");
                    }
                }
                DdcPersistenceMigrationCli.main(new String[]{"upgrade-cloud-delivery", "--target-config", target.toString()});
                var tianshuRows = realHistory(primary, "public");
                var outboxRows = realHistory(primary, "egon_outbox");
                assertThat(tianshuRows).containsExactlyElementsOf(history(full, 3, true, topology.fingerprint()));
                assertThat(outboxRows).containsExactlyElementsOf(history(outbox, 0, true, topology.fingerprint()));
                DdcPersistenceMigrationCli.main(new String[]{"upgrade-cloud-delivery", "--target-config", target.toString()});
                assertThat(realHistory(primary, "public")).containsExactlyElementsOf(tianshuRows);
                assertThat(realHistory(primary, "egon_outbox")).containsExactlyElementsOf(outboxRows);
            } finally {
                Files.deleteIfExists(target);
            }
        }
    }

    private static Properties privateFixture() throws Exception {
        String configured = System.getenv("TIANSHU_CLOUD_UPGRADE_IT_CONFIG");
        assertThat(configured).as("explicit enabled run requires a private fixture config").isNotBlank();
        Path path = Path.of(configured);
        assertThat(path.isAbsolute() && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)).isTrue();
        assertThat(Files.getPosixFilePermissions(path)).containsOnly(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(Files.size(path)).isBetween(1L, 16384L);
        Properties fixture = new Properties();
        try (var input = Files.newInputStream(path)) { fixture.load(input); }
        assertThat(fixture.getProperty("allowSchemaChanges")).isEqualTo("true");
        assertThat(fixture.getProperty("expectedDatabase")).isEqualTo("tianshu_step18_upgrade");
        assertThat(fixture.getProperty("jdbcUrl")).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianshu_step18_upgrade(?:\\?[^\\r\\n]*)?");
        assertThat(fixture.getProperty("username")).isNotBlank();
        assertThat(fixture.getProperty("password")).isNotNull();
        return fixture;
    }

    private static Path protectedTargetConfiguration(Properties fixture) throws Exception {
        String yaml;
        try (var input = new ClassPathResource("egon-tianshu-persistence.yml").getInputStream()) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var mapper = new ObjectMapper();
        yaml = yaml.replace("\"${TIANSHU_JDBC_URL:${spring.datasource.url}}\"", mapper.writeValueAsString(fixture.getProperty("jdbcUrl")))
                .replace("\"${TIANSHU_DB_USERNAME:${spring.datasource.username}}\"", mapper.writeValueAsString(fixture.getProperty("username")))
                .replace("\"${TIANSHU_DB_PASSWORD:${spring.datasource.password}}\"", mapper.writeValueAsString(fixture.getProperty("password")));
        Path target = Files.createTempFile("tianshu-step18-upgrade-", ".yml");
        Files.setPosixFilePermissions(target, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        Files.writeString(target, yaml);
        return target;
    }

    private static List<Row> realHistory(DataSource source, String schema) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT script,type,version,checksum,route_fingerprint,tenant_id FROM " + schema + ".ddl_history ORDER BY version")) {
            while (result.next()) rows.add(new Row(result.getString(1), result.getString(2), result.getString(3),
                    result.getString(4), result.getString(5), result.getLong(6)));
        }
        return rows;
    }

    private DdcI18nTopologyUpgrade upgrade() {
        return new DdcI18nTopologyUpgrade(store.source, "tianshu_0", tianshu, outbox, OLD, CURRENT, runner,
                new PathMatchingResourcePatternResolver(), Duration.ofSeconds(2), Duration.ofSeconds(5),
                oldOutboxReady, oldTopologyReady, newTopologyReady);
    }

    private List<EgonColaDdlResult> results(int start, boolean applied) {
        return tianshu.scripts().subList(start, tianshu.scripts().size()).stream().map(script -> new EgonColaDdlResult(
                "tianshu_0", "public", script.version(), script.sha256(), applied && script.version().equals("20261022_001")
                ? EgonColaDdlResult.StatusEnum.APPLIED : EgonColaDdlResult.StatusEnum.SKIPPED, Duration.ZERO)).toList();
    }

    private static EgonColaDdlManifestBO manifest(String resource) throws Exception {
        try (var input = DdcI18nTopologyUpgradeTest.class.getClassLoader().getResourceAsStream(resource)) {
            return new ObjectMapper().readValue(input, EgonColaDdlManifestBO.class);
        }
    }

    private static List<Row> history(EgonColaDdlManifestBO manifest, int start, boolean complete, String fingerprint) {
        int end = manifest.scripts().size() - (complete ? 0 : 1);
        return new ArrayList<>(manifest.scripts().subList(start, end).stream().map(script -> row(script, fingerprint)).toList());
    }

    private static Row row(EgonColaDdlManifestBO.ScriptBO script, String fingerprint) {
        return new Row(script.path(), "SQL", script.version(), script.sha256(), fingerprint, 0L);
    }

    private static long key(String schema) throws Exception {
        return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(("egon-ddl:" + schema).getBytes(StandardCharsets.UTF_8))).getLong();
    }

    private record Row(String script, String type, String version, String checksum, String fingerprint, Long tenant) {
        Row withFingerprint(String fingerprint) { return new Row(script, type, version, checksum, fingerprint, tenant); }
    }

    /** JDBC simulation retains transaction-local history until commit, so partial updates are observable in tests. */
    private static final class Store {
        private final DataSource source = mock(DataSource.class);
        private Map<String, List<Row>> rows = new LinkedHashMap<>();
        private final List<String> events = new ArrayList<>();
        private final List<String> updateSql = new ArrayList<>();
        private final List<String> tableChecks = new ArrayList<>();
        private int connections;
        private boolean tableExists;
        private boolean partialTables;
        private boolean failPublicUpdate;
        private boolean failCommitAfterApplying;
        private boolean failCommitBeforeApplying;
        private boolean failConfirmationConnection;

        Store() throws Exception {
            when(source.getConnection()).thenAnswer(call -> {
                int id = ++connections;
                if (failConfirmationConnection && id >= 3) throw new SQLException("confirmation unavailable");
                events.add("open:" + id);
                Connection connection = mock(Connection.class);
                DatabaseMetaData metadata = mock(DatabaseMetaData.class);
                when(connection.getMetaData()).thenReturn(metadata);
                when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
                when(connection.getAutoCommit()).thenReturn(true);
                Map<String, List<Row>> transaction = new LinkedHashMap<>();
                doAnswer(start -> { rows.forEach((schema, values) -> transaction.put(schema, new ArrayList<>(values))); return null; })
                        .when(connection).setAutoCommit(false);
                doAnswer(commit -> {
                    events.add("commit");
                    if (failCommitBeforeApplying) throw new SQLException("commit not acknowledged");
                    rows = new LinkedHashMap<>(transaction);
                    if (failCommitAfterApplying) { failCommitAfterApplying = false; throw new SQLException("commit response lost"); }
                    return null;
                }).when(connection).commit();
                doAnswer(rollback -> { events.add("rollback"); return null; }).when(connection).rollback();
                doAnswer(close -> { events.add("close:" + id); return null; }).when(connection).close();
                when(connection.prepareStatement(anyString())).thenAnswer(prepare -> {
                    String sql = prepare.getArgument(0);
                    PreparedStatement statement = mock(PreparedStatement.class);
                    Map<Integer, Object> arguments = new HashMap<>();
                    doAnswer(bind -> { arguments.put(bind.getArgument(0), bind.getArgument(1)); return null; })
                            .when(statement).setString(anyInt(), anyString());
                    doAnswer(bind -> { arguments.put(bind.getArgument(0), bind.getArgument(1)); return null; })
                            .when(statement).setLong(anyInt(), anyLong());
                    when(statement.execute()).thenAnswer(execute -> {
                        if (sql.contains("pg_advisory_xact_lock")) events.add("lock:" + arguments.get(1));
                        if (sql.startsWith("LOCK TABLE")) events.add("table-lock:" + (sql.contains("egon_outbox") ? "egon_outbox" : "public"));
                        return true;
                    });
                    when(statement.executeQuery()).thenAnswer(query -> {
                        if (sql.startsWith("SELECT current_schema")) return result(List.<Object[]>of(new Object[]{"public", false, false}));
                        if (sql.startsWith("SELECT to_regclass")) {
                            tableChecks.add(sql);
                            return result(List.<Object[]>of(new Object[]{tableExists, partialTables || tableExists, tableExists, tableExists}));
                        }
                        String schema = sql.contains("egon_outbox.ddl_history") ? "egon_outbox" : "public";
                        return result(transaction.get(schema).stream().map(row -> new Object[]{row.script(), row.type(), row.version(),
                                row.checksum(), row.fingerprint(), row.tenant()}).toList());
                    });
                    when(statement.executeUpdate()).thenAnswer(update -> {
                        String schema = sql.contains("egon_outbox.ddl_history") ? "egon_outbox" : "public";
                        events.add("update:" + schema);
                        updateSql.add(sql);
                        if (failPublicUpdate && schema.equals("public")) return 0;
                        int count = (int) transaction.get(schema).stream().filter(row -> row.fingerprint().equals(arguments.get(2))).count();
                        transaction.put(schema, transaction.get(schema).stream().map(row -> row.fingerprint().equals(arguments.get(2))
                                ? row.withFingerprint((String) arguments.get(1)) : row).toList());
                        return count;
                    });
                    return statement;
                });
                return connection;
            });
        }

        private ResultSet result(List<Object[]> values) throws Exception {
            ResultSet result = mock(ResultSet.class);
            int[] index = {-1};
            when(result.next()).thenAnswer(call -> ++index[0] < values.size());
            when(result.getString(anyInt())).thenAnswer(call -> (String) values.get(index[0])[(int) call.getArgument(0) - 1]);
            when(result.getBoolean(anyInt())).thenAnswer(call -> (Boolean) values.get(index[0])[(int) call.getArgument(0) - 1]);
            when(result.getLong(anyInt())).thenAnswer(call -> (Long) values.get(index[0])[(int) call.getArgument(0) - 1]);
            return result;
        }
    }
}
