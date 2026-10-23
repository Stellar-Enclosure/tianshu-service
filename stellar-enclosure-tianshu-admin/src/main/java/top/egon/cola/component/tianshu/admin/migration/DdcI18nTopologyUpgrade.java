package top.egon.cola.component.tianshu.admin.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.support.ResourcePatternResolver;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlManifestBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlTargetBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaPostgreDdlRunner;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** Explicit, resumable transition for the single page-language table addition. */
@RequiredArgsConstructor
final class DdcI18nTopologyUpgrade {
    static final String VERSION = "20261022_001";
    static final String TABLE = "ddc_i18n_page";
    private static final String SCRIPT = "db/egon-mp/V20261022_001__tianshu_page_i18n.sql";
    private static final List<String> VERSIONS = List.of("20260922_001", "20260928_001", "20261017_001",
            "20261018_001", VERSION);
    private static final List<String> SCHEMAS = List.of("egon_outbox", "public");

    private final DataSource primary;
    private final String primaryName;
    private final EgonColaDdlManifestBO tianshuManifest;
    private final EgonColaDdlManifestBO outboxManifest;
    private final String previousFingerprint;
    private final String currentFingerprint;
    private final EgonColaPostgreDdlRunner runner;
    private final ResourcePatternResolver resources;
    private final Duration lockTimeout;
    private final Duration statementTimeout;
    private final Runnable validatePreviousOutbox;
    private final Runnable validateInstalledPreviousTopology;
    private final Runnable validateCurrentTopology;

    void upgrade() {
        validateManifests();
        try {
            if (inspect(true) == State.CURRENT) {
                validateCurrentTopology.run();
                return;
            }
            validatePreviousOutbox.run();
            var results = runner.run(List.of(new EgonColaDdlTargetBO(primaryName, "public",
                    EgonColaDdlTargetBO.RoleEnum.MASTER_DATA, primary, tianshuManifest, previousFingerprint)));
            DdcPersistenceMigrationCli.validateTianshuResults(results, tianshuManifest, primaryName);
            // The DDL runner commits the new SQL and its old-fingerprint history row together.
            // If a later step fails, this exact intermediate state is accepted on the next invocation.
            validateInstalledPreviousTopology.run();
            promoteFingerprint();
            validateCurrentTopology.run();
        } catch (RuntimeException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("TIANSHU_I18N_UPGRADE_COMMIT_UNKNOWN")) {
                throw failure;
            }
            // A concurrent explicit upgrade may have completed between preflight and the old-topology checks.
            try {
                if (inspect(false) == State.CURRENT) {
                    validateCurrentTopology.run();
                    return;
                }
            } catch (RuntimeException confirmationFailure) {
                if (failure != confirmationFailure) failure.addSuppressed(confirmationFailure);
            }
            throw failure;
        }
    }

    private void validateManifests() {
        if (!"tianshu".equals(tianshuManifest.family()) || !"component-outbox".equals(outboxManifest.family())
                || !VERSIONS.equals(tianshuManifest.scripts().stream().map(EgonColaDdlManifestBO.ScriptBO::version).toList())
                || !"20261018_001".equals(tianshuManifest.freshBaselineVersion())
                || !SCRIPT.equals(tianshuManifest.scripts().getLast().path())
                || previousFingerprint == null || !previousFingerprint.matches("[0-9a-f]{64}")
                || currentFingerprint == null || !currentFingerprint.matches("[0-9a-f]{64}")
                || previousFingerprint.equals(currentFingerprint)
                || lockTimeout == null || lockTimeout.isZero() || lockTimeout.isNegative()
                || statementTimeout == null || statementTimeout.isZero() || statementTimeout.isNegative()) {
            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_PLAN_INVALID");
        }
        for (var manifest : List.of(tianshuManifest, outboxManifest)) {
            for (var script : manifest.scripts()) {
                try {
                    var matches = resources.getResources("classpath*:" + script.path());
                    if (matches.length != 1) throw new IllegalStateException("TIANSHU_I18N_UPGRADE_RESOURCE_AMBIGUOUS");
                    try (var input = matches[0].getInputStream()) {
                        byte[] bytes = input.readNBytes(10 * 1024 * 1024 + 1);
                        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024
                                || !script.sha256().equals(HexFormat.of().formatHex(digest(bytes)))) {
                            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_CHECKSUM_MISMATCH");
                        }
                    }
                } catch (IOException failure) {
                    throw new IllegalStateException("TIANSHU_I18N_UPGRADE_RESOURCE_UNREADABLE", failure);
                }
            }
        }
    }

    private State inspect(boolean allowMissingNewScript) {
        try (Connection connection = primary.getConnection()) {
            requireOwnedConnection(connection);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                validatePrimary(connection);
                return readState(connection, allowMissingNewScript).state();
            } finally {
                connection.rollback();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_HISTORY_UNAVAILABLE", failure);
        }
    }

    private HistoryPair readState(Connection connection, boolean allowMissingNewScript) throws SQLException {
        History outbox = readHistory(connection, "egon_outbox", outboxManifest, false);
        History tianshu = readHistory(connection, "public", tianshuManifest, allowMissingNewScript);
        if (!outbox.fingerprint().equals(tianshu.fingerprint())) {
            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_MIXED_FINGERPRINTS");
        }
        State state;
        if (currentFingerprint.equals(tianshu.fingerprint()) && tianshu.complete()) {
            state = State.CURRENT;
        } else if (previousFingerprint.equals(tianshu.fingerprint())) {
            state = State.PREVIOUS;
        } else {
            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_FINGERPRINT_MISMATCH");
        }
        try (PreparedStatement statement = statement(connection,
                "SELECT to_regclass('public.ddc_i18n_page_t0') IS NOT NULL");
             ResultSet row = statement.executeQuery()) {
            if (!row.next() || row.getBoolean(1) != tianshu.complete()) {
                throw new IllegalStateException("TIANSHU_I18N_UPGRADE_TABLE_HISTORY_MISMATCH");
            }
        }
        return new HistoryPair(state, outbox.count(), tianshu.count());
    }

    private History readHistory(Connection connection, String schema, EgonColaDdlManifestBO manifest,
                                boolean allowMissingNewScript) throws SQLException {
        try (PreparedStatement statement = statement(connection,
                "SELECT script,type,version,checksum,route_fingerprint,tenant_id FROM " + schema + ".ddl_history ORDER BY version");
             ResultSet rows = statement.executeQuery()) {
            int index = -1;
            int count = 0;
            String fingerprint = null;
            while (rows.next()) {
                if (index == -1) index = manifest.startingIndex(rows.getString(3));
                if (index >= manifest.scripts().size()) throw historyMismatch();
                var script = manifest.scripts().get(index++);
                long tenantId = rows.getLong(6);
                boolean nullTenant = rows.wasNull();
                if (!script.path().equals(rows.getString(1)) || !"SQL".equals(rows.getString(2))
                        || !script.version().equals(rows.getString(3)) || !script.sha256().equals(rows.getString(4))
                        || tenantId != 0 || nullTenant) throw historyMismatch();
                String rowFingerprint = rows.getString(5);
                if (rowFingerprint == null || !rowFingerprint.matches("[0-9a-f]{64}")
                        || fingerprint != null && !fingerprint.equals(rowFingerprint)) throw historyMismatch();
                fingerprint = rowFingerprint;
                count++;
            }
            boolean complete = index == manifest.scripts().size();
            if (count == 0 || !complete && !(allowMissingNewScript && index == manifest.scripts().size() - 1)) {
                throw historyMismatch();
            }
            return new History(fingerprint, count, complete);
        }
    }

    private void promoteFingerprint() {
        SQLException uncertainCommit = null;
        boolean attemptedCommit = false;
        try (Connection connection = primary.getConnection()) {
            requireOwnedConnection(connection);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                configureAndLock(connection);
                HistoryPair pair = readState(connection, false);
                if (pair.state() == State.CURRENT) {
                    connection.rollback();
                    return;
                }
                for (String schema : SCHEMAS) {
                    try (PreparedStatement update = statement(connection,
                            "UPDATE " + schema + ".ddl_history SET route_fingerprint = ? WHERE route_fingerprint = ?")) {
                        update.setString(1, currentFingerprint);
                        update.setString(2, previousFingerprint);
                        int expected = "egon_outbox".equals(schema) ? pair.outboxCount() : pair.tianshuCount();
                        if (update.executeUpdate() != expected) {
                            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_HISTORY_CAS_FAILED");
                        }
                    }
                }
                attemptedCommit = true;
                try {
                    connection.commit();
                } catch (SQLException failure) {
                    uncertainCommit = failure;
                    rollback(connection, failure);
                }
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException failure) {
            if (!attemptedCommit) throw new IllegalStateException("TIANSHU_I18N_UPGRADE_PROMOTION_FAILED", failure);
            if (uncertainCommit == null) uncertainCommit = failure;
            else if (uncertainCommit != failure) uncertainCommit.addSuppressed(failure);
        }
        if (uncertainCommit != null && !confirmPromotion(uncertainCommit)) {
            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_COMMIT_UNKNOWN; rerun upgrade-i18n to verify or resume", uncertainCommit);
        }
    }

    private boolean confirmPromotion(SQLException original) {
        // Close the uncertain connection first; a fresh connection must acquire both locks before observing commit outcome.
        try (Connection connection = primary.getConnection()) {
            requireOwnedConnection(connection);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                configureAndLock(connection);
                return readState(connection, false).state() == State.CURRENT;
            } finally {
                connection.rollback();
            }
        } catch (SQLException | RuntimeException failure) {
            if (original != failure) original.addSuppressed(failure);
            return false;
        }
    }

    private void configureAndLock(Connection connection) throws SQLException {
        validatePrimary(connection);
        try (PreparedStatement settings = statement(connection,
                "SELECT set_config('lock_timeout', ?, true), set_config('statement_timeout', ?, true)")) {
            settings.setString(1, lockTimeout.toMillis() + "ms");
            settings.setString(2, statementTimeout.toMillis() + "ms");
            settings.execute();
        }
        for (String schema : SCHEMAS) {
            try (PreparedStatement lock = statement(connection, "SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, ByteBuffer.wrap(digest(("egon-ddl:" + schema).getBytes(StandardCharsets.UTF_8))).getLong());
                lock.execute();
            }
        }
        for (String schema : SCHEMAS) {
            try (PreparedStatement lock = statement(connection,
                    "LOCK TABLE " + schema + ".ddl_history IN SHARE ROW EXCLUSIVE MODE")) {
                lock.execute();
            }
        }
    }

    private void validatePrimary(Connection connection) throws SQLException {
        if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()) || connection.isReadOnly()) {
            throw new IllegalStateException("TIANSHU_I18N_UPGRADE_PRIMARY_REQUIRED");
        }
        try (PreparedStatement statement = statement(connection,
                "SELECT current_schema(), pg_is_in_recovery(), current_setting('transaction_read_only')::boolean");
             ResultSet row = statement.executeQuery()) {
            if (!row.next() || !"public".equals(row.getString(1)) || row.getBoolean(2) || row.getBoolean(3)) {
                throw new IllegalStateException("TIANSHU_I18N_UPGRADE_PRIMARY_REQUIRED");
            }
        }
    }

    private PreparedStatement statement(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout((int) Math.min(Integer.MAX_VALUE, Math.max(1, statementTimeout.toSeconds())));
        return statement;
    }

    private void requireOwnedConnection(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) throw new IllegalStateException("TIANSHU_I18N_UPGRADE_OWN_CONNECTION_REQUIRED");
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException failure) { if (original != failure) original.addSuppressed(failure); }
    }

    private static IllegalStateException historyMismatch() {
        return new IllegalStateException("TIANSHU_I18N_UPGRADE_MANAGED_HISTORY_MISMATCH");
    }

    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private enum State { PREVIOUS, CURRENT }
    private record History(String fingerprint, int count, boolean complete) { }
    private record HistoryPair(State state, int outboxCount, int tianshuCount) { }
}
