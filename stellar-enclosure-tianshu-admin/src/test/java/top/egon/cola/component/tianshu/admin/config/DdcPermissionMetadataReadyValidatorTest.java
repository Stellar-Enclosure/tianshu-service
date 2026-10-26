package top.egon.cola.component.tianshu.admin.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DdcPermissionMetadataReadyValidatorTest {
    @Test void validatesTheMigrationContractWithoutExecutingSchemaChanges() throws Exception {
        assertThatCode(() -> DdcPermissionMetadataReadyValidator.validate(connection(null))).doesNotThrowAnyException();
    }

    @Test void refusesAReceiptForeignKeyThatOmitsTheCatalogScope() throws Exception {
        assertThatThrownBy(() -> DdcPermissionMetadataReadyValidator.validate(connection("cross-scope-fk")))
                .hasMessageContaining("FOREIGN_KEY_MISMATCH", "fk_i18n_import_receipt_source");
    }

    @Test void refusesMissingAndDisabledImmutableTriggers() throws Exception {
        assertThatThrownBy(() -> DdcPermissionMetadataReadyValidator.validate(connection("missing-trigger")))
                .hasMessageContaining("TRIGGER_MISSING");
        assertThatThrownBy(() -> DdcPermissionMetadataReadyValidator.validate(connection("disabled-trigger")))
                .hasMessageContaining("TRIGGER_MISMATCH");
    }

    @Test void refusesAFunctionBodyThatNoLongerEnforcesImmutability() throws Exception {
        assertThatThrownBy(() -> DdcPermissionMetadataReadyValidator.validate(connection("changed-function")))
                .hasMessageContaining("FUNCTION_MISMATCH");
    }

    @Test void refusesAnInvalidMetadataLookupIndex() throws Exception {
        assertThatThrownBy(() -> DdcPermissionMetadataReadyValidator.validate(connection("invalid-index")))
                .hasMessageContaining("INDEX_MISMATCH");
    }

    private static Connection connection(String corruption) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(any(), eq("public"), anyString(), any(String[].class))).thenAnswer(call -> {
            ResultSet rows = mock(ResultSet.class);
            when(rows.next()).thenReturn(true);
            when(rows.getString("TABLE_NAME")).thenReturn(call.getArgument(2));
            return rows;
        });
        when(metadata.getColumns(any(), eq("public"), anyString(), any())).thenAnswer(call -> {
            var names = DdcPermissionMetadataSchemaFixture.columns(call.getArgument(2)).stream().toList();
            ResultSet rows = mock(ResultSet.class);
            int[] position = {-1};
            when(rows.next()).thenAnswer(next -> ++position[0] < names.size());
            when(rows.getString("COLUMN_NAME")).thenAnswer(read -> names.get(position[0]));
            return rows;
        });
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet rows = DdcPermissionMetadataSchemaFixture.rows(call.getArgument(0), corruption);
            when(statement.executeQuery()).thenReturn(rows);
            return statement;
        });
        return connection;
    }
}
