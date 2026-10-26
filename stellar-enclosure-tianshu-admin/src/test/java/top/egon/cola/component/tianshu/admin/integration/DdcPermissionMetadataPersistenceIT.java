package top.egon.cola.component.tianshu.admin.integration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static top.egon.cola.component.tianshu.admin.integration.DdcPermissionMetadataPostgresFixture.*;

class DdcPermissionMetadataPersistenceIT {
    @Test void immutableSourcesAndExactScopeReceiptBindingsAreEnforcedByPostgreSQL() throws Exception {
        try (Connection connection = open()) {
            try {
                long tenant = id();
                var scope = scope(connection, tenant);
                var other = scope(connection, tenant);
                long source = source(connection, scope);
                long grant = grant(connection, scope);
                long otherGrant = grant(connection, other);
                receipt(connection, scope, source, grant, METADATA);
                rejected(connection, "55000", () -> execute(connection,
                        "UPDATE ddc_i18n_source_version_t0 SET canonical_manifest_json='{}' WHERE id=?", source));
                rejected(connection, "55000", () -> execute(connection, "DELETE FROM ddc_i18n_source_version_t0 WHERE id=?", source));
                rejected(connection, "23503", () -> receipt(connection, scope, source, otherGrant, METADATA));
                rejected(connection, "23503", () -> receipt(connection, scope, source, grant, "1".repeat(64)));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test void importGrantIdentityCannotChangeAndEveryEnabledChangeRequiresTheNextVersion() throws Exception {
        try (Connection connection = open()) {
            try {
                var scope = scope(connection, id());
                long grant = grant(connection, scope);
                rejected(connection, "55000", () -> execute(connection,
                        "UPDATE ddc_source_import_grant_t0 SET enabled=false WHERE id=?", grant));
                rejected(connection, "55000", () -> execute(connection,
                        "UPDATE ddc_source_import_grant_t0 SET producer_id='another',version=version+1 WHERE id=?", grant));
                assertThat(execute(connection, "UPDATE ddc_source_import_grant_t0 SET enabled=false,version=version+1 WHERE id=? AND version=0", grant)).isEqualTo(1);
                assertThat(execute(connection, "UPDATE ddc_source_import_grant_t0 SET enabled=true,version=version+1 WHERE id=? AND version=0", grant)).isZero();
            } finally {
                connection.rollback();
            }
        }
    }

    @Test void publicationCopiesTheSavedDraftAtomicallyAndCannotBeRewrittenOrCleared() throws Exception {
        try (Connection connection = open()) {
            try {
                var scope = scope(connection, id());
                long source = source(connection, scope);
                descriptor(connection, scope, source);
                long page = page(connection, scope);
                var invalid = connection.setSavepoint();
                long stale = publication(connection, scope, page, 1L);
                assertThatThrownBy(() -> publish(connection, scope, page, stale))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("55000"));
                connection.rollback(invalid);
                long current = publication(connection, scope, page, 0L);
                assertThat(publish(connection, scope, page, current)).isEqualTo(1);
                rejected(connection, "55000", () -> execute(connection,
                        "UPDATE ddc_i18n_published_override_t0 SET translations_json='{}' WHERE id=?", current));
                rejected(connection, "55000", () -> execute(connection,
                        "UPDATE ddc_i18n_page_t0 SET published_translations='{}',version=version+1 WHERE id=?", page));
                rejected(connection, "55000", () -> execute(connection,
                        "UPDATE ddc_i18n_page_t0 SET current_override_id=NULL,version=version+1 WHERE id=?", page));
            } finally {
                connection.rollback();
            }
        }
    }

    private static void rejected(Connection connection, String state, SqlAction action) throws Exception {
        var savepoint = connection.setSavepoint();
        try {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo(state));
        } finally {
            connection.rollback(savepoint);
        }
    }

    @FunctionalInterface private interface SqlAction { void run() throws Exception; }
}
