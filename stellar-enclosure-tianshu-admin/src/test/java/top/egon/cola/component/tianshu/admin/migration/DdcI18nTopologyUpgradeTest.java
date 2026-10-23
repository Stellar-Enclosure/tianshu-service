package top.egon.cola.component.tianshu.admin.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        tianshu = manifest("db/egon-mp/repository-manifest.json");
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
        private int connections;
        private boolean tableExists;
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
                        if (sql.startsWith("SELECT to_regclass")) return result(List.<Object[]>of(new Object[]{tableExists}));
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
