package top.egon.cola.component.tianshu.admin.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcPermissionMetadataBootstrapTest {
    private static final String OPERATION = "ef236f7a-3840-4664-afc5-7c2690765c09";
    private static final String DIGEST = "a".repeat(64);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temporary;

    @Test
    void oneTransactionCreatesOnlyExplicitTargetAndPublisherGrantAndDurableReceipt() throws Exception {
        var fixture = database(null, false);
        String receipt = new DdcPermissionMetadataBootstrapRepository("metadata", CLOCK).initialize(fixture.connection(), command(), DIGEST, null);
        assertEquals(8, fixture.sql().stream().filter(sql -> sql.startsWith("INSERT INTO")).count());
        assertTrue(fixture.sql().stream().noneMatch(sql -> sql.contains("permission_definition") || sql.contains("role_") || sql.contains("activation_projection")));
        assertEquals("B0", DdcPermissionMetadataBootstrapCli.object(receipt).get("stage"));
        assertEquals(DIGEST, DdcPermissionMetadataBootstrapCli.object(receipt).get("commandDigest"));
        assertEquals(command().sourceImportGrants().getFirst().bindingDigest(),
                ((Map<?, ?>)((List<?>)((Map<?, ?>)DdcPermissionMetadataBootstrapCli.object(receipt).get("facts")).get("sourceImportGrants")).getFirst()).get("bindingDigest"));
        verify(fixture.connection()).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        verify(fixture.connection()).commit(); verify(fixture.connection(), never()).rollback();
        for (String sql : fixture.sql()) if (sql.startsWith("SELECT id")) assertTrue(sql.contains("WHERE tenant_id=?"));
    }

    @Test
    void receiptResumePerformsNoBusinessWritesAndRejectsDifferentCommand() throws Exception {
        String original = "{\"operationId\":\"" + OPERATION + "\",\"commandDigest\":\"" + DIGEST + "\",\"stage\":\"B0\"}";
        var success = database(original, false);
        assertEquals(original, new DdcPermissionMetadataBootstrapRepository("metadata", CLOCK).initialize(success.connection(), command(), DIGEST, null));
        assertTrue(success.sql().stream().noneMatch(sql -> sql.startsWith("INSERT")));
        var conflict = database(original, false);
        assertThrows(IllegalStateException.class, () -> new DdcPermissionMetadataBootstrapRepository("metadata", CLOCK)
                .initialize(conflict.connection(), command(), "b".repeat(64), null));
        verify(conflict.connection()).rollback(); verify(conflict.connection(), never()).commit();
    }

    @Test
    void localReceiptWithoutMatchingSqlHistoryCannotRecreateCommittedFacts() throws Exception {
        String original = "{\"operationId\":\"" + OPERATION + "\",\"commandDigest\":\"" + DIGEST + "\",\"stage\":\"B0\"}";
        for (String stored : new String[]{null, original.replace("B0", "different-receipt")}) {
            var fixture = database(stored, false);
            assertThrows(IllegalStateException.class, () -> new DdcPermissionMetadataBootstrapRepository("metadata", CLOCK)
                    .initialize(fixture.connection(), command(), DIGEST, original));
            assertTrue(fixture.sql().stream().noneMatch(sql -> sql.startsWith("INSERT")));
            verify(fixture.connection()).rollback(); verify(fixture.connection(), never()).commit();
        }
        var matching = database(original, false);
        assertEquals(original, new DdcPermissionMetadataBootstrapRepository("metadata", CLOCK)
                .initialize(matching.connection(), command(), DIGEST, original));
    }

    @Test
    void privateInputsRejectSharedWritableDirectoriesAndUntrustedAncestors() throws Exception {
        Path shared = Files.createDirectory(temporary.resolve("shared"));
        Path privateChild = Files.createDirectory(shared.resolve("owned"));
        Files.setPosixFilePermissions(privateChild, PosixFilePermissions.fromString("rwx------"));
        Path file = privateChild.resolve("input"); Files.writeString(file, "private input");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxrwxrwx"));
        try {
            assertThrows(java.io.IOException.class, () -> DdcPermissionMetadataBootstrapCli.privateBytes(file, 100));
            assertThrows(java.io.IOException.class, () -> DdcPermissionMetadataBootstrapCli.writeReceipt(privateChild.resolve("receipt"), "{}"));
        } finally { Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwx------")); }
        assertEquals("private input", new String(DdcPermissionMetadataBootstrapCli.privateBytes(file, 100), StandardCharsets.UTF_8));
    }

    @Test
    void mutableAncestorAliasCannotChooseAnotherPrivateInput() throws Exception {
        Path safe = Files.createDirectories(temporary.resolve("safe/child"));
        Files.setPosixFilePermissions(safe, PosixFilePermissions.fromString("rwx------"));
        Path file = safe.resolve("input"); Files.writeString(file, "private input");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        Path shared = Files.createDirectory(temporary.resolve("mutable-alias"));
        Files.createSymbolicLink(shared.resolve("alias"), safe.getParent());
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxrwxrwx"));
        try {
            assertThrows(java.io.IOException.class, () -> DdcPermissionMetadataBootstrapCli.privateBytes(shared.resolve("alias/child/input"), 100));
        } finally { Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwx------")); }
        assertEquals("private input", new String(DdcPermissionMetadataBootstrapCli.privateBytes(file, 100), StandardCharsets.UTF_8));
    }

    @Test
    void auditFailureRollsBackAllTargetAndGrantCreation() throws Exception {
        var fixture = database(null, true);
        assertThrows(SQLException.class, () -> new DdcPermissionMetadataBootstrapRepository("metadata", CLOCK).initialize(fixture.connection(), command(), DIGEST, null));
        verify(fixture.connection()).rollback(); verify(fixture.connection(), never()).commit();
    }

    @Test
    void existingIdentityConflictCannotBeOverwrittenOrReenabled() throws Exception {
        var fixture = database(null, false);
        PreparedStatement mismatch = mock(PreparedStatement.class);
        ResultSet row = mock(ResultSet.class);
        when(mismatch.executeQuery()).thenReturn(row); when(row.next()).thenReturn(true); when(row.getLong(1)).thenReturn(999L);
        when(fixture.connection().prepareStatement(startsWith("SELECT id, deleted_at"))).thenReturn(mismatch);
        assertThrows(IllegalStateException.class, () -> new DdcPermissionMetadataBootstrapRepository("metadata", CLOCK).initialize(fixture.connection(), command(), DIGEST, null));
        verify(fixture.connection()).rollback(); verify(fixture.connection(), never()).commit();
        assertTrue(fixture.sql().stream().noneMatch(sql -> sql.startsWith("UPDATE") || sql.startsWith("DELETE")));
    }

    @Test
    void strictCommandRejectsUnknownFieldsDuplicateKeysWildcardsAndIncompleteTargets() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String source = mapper.writeValueAsString(command());
        assertEquals(command(), DdcPermissionMetadataBootstrapCli.readCommand(source.getBytes(StandardCharsets.UTF_8)));
        for (String invalid : List.of(source.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                source.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"grantAllRoles\":true"),
                source.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"),
                source.replace("\"id\":\"108\"", "\"id\":108"),
                source.replace("tianshu-java", "*"), source.replace("\"tenantId\":\"20\"", "\"tenantId\":\"0\""))) {
            assertThrows(Exception.class, () -> DdcPermissionMetadataBootstrapCli.readCommand(invalid.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(IllegalArgumentException.class, () -> DdcPermissionMetadataBootstrapCli.arguments(new String[]{"--token", "secret", "--receipt-file", "/tmp/receipt"}));
        assertThrows(IllegalArgumentException.class, () -> new DdcPermissionMetadataBootstrapRepository("public;DROP", CLOCK));
    }

    @Test
    void ownerOnlyFilesAndAtomicReceiptCannotLeakOrReplaceAnotherOperation() throws Exception {
        Path file = temporary.resolve("command.json");
        Files.writeString(file, "private-fixture"); Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        assertEquals("private-fixture", new String(DdcPermissionMetadataBootstrapCli.privateBytes(file, 100), StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(java.io.IOException.class, () -> DdcPermissionMetadataBootstrapCli.privateBytes(file, 100));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        Path link = temporary.resolve("link"); Files.createSymbolicLink(link, file);
        assertThrows(java.io.IOException.class, () -> DdcPermissionMetadataBootstrapCli.privateBytes(link, 100));
        Path receipt = temporary.resolve("receipt.json");
        DdcPermissionMetadataBootstrapCli.writeReceipt(receipt, "{\"stage\":\"B0\"}");
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(receipt));
        DdcPermissionMetadataBootstrapCli.writeReceipt(receipt, "{\"stage\":\"B0\"}");
        assertThrows(java.io.IOException.class, () -> DdcPermissionMetadataBootstrapCli.writeReceipt(receipt, "another-operation"));
        assertEquals("{\"stage\":\"B0\"}\n", Files.readString(receipt));
    }

    @Test
    void commandFailureIsSanitizedAndNeverFallsThroughToApplicationStartup() throws Exception {
        Path file = temporary.resolve("invalid.json"); Files.writeString(file, "secret-must-not-appear");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        var failure = assertThrows(IllegalStateException.class, () -> top.egon.cola.component.tianshu.admin.DynamicConfigCenterAdminApplication.main(
                new String[]{"bootstrap-permission-metadata", "--command-file", file.toString(), "--receipt-file", temporary.resolve("out.json").toString()}));
        assertFalse(failure.toString().contains("secret-must-not-appear")); assertNull(failure.getCause());
        assertThrows(IllegalArgumentException.class, () -> top.egon.cola.component.tianshu.admin.DynamicConfigCenterAdminApplication.main(new String[]{"bootstrap-permission-typo"}));
    }

    private DdcPermissionMetadataBootstrapCommand command() {
        return new DdcPermissionMetadataBootstrapCommand(1, OPERATION, new CatalogScope("10", "20", "xingyuan", "tianshu", "default", "test"),
                temporary.resolve("database.json").toString(), "operator-1", "ddc-business-1", "ddc-app-1",
                new DdcPermissionMetadataBootstrapCommand.RowIds("101", "102", "103", "104", "105", "106", "107"),
                List.of(new DdcPermissionMetadataBootstrapCommand.SourceGrant("108", "tianshu-java", "https://idp.example/tenant", "rbac-publisher", "https://metadata.example")));
    }

    private Fixture database(String original, boolean auditFails) throws Exception {
        Connection connection = mock(Connection.class); List<String> queries = new ArrayList<>();
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0); queries.add(sql);
            PreparedStatement statement = mock(PreparedStatement.class); ResultSet rows = mock(ResultSet.class);
            when(statement.executeQuery()).thenReturn(rows); when(rows.next()).thenReturn(false);
            when(statement.executeUpdate()).thenReturn(1);
            if (sql.startsWith("SELECT operation_type") && original != null) {
                when(rows.next()).thenReturn(true, false); when(rows.getString(1)).thenReturn("PAC_BOOTSTRAP_METADATA_B0"); when(rows.getString(2)).thenReturn(original);
            }
            if (auditFails && sql.startsWith("INSERT INTO") && sql.contains("ddc_operation_log_t0")) {
                when(statement.executeUpdate()).thenThrow(new SQLException("audit unavailable"));
            }
            return statement;
        });
        return new Fixture(connection, queries);
    }
    private record Fixture(Connection connection, List<String> sql) { }
}
