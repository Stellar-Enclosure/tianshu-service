package top.egon.cola.component.tianshu.admin.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.config.DdcPermissionMetadataSchemaFixture;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProducerPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProjectionPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nImportReceiptPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nPublishedOverridePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceMessagePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceVersionPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcMetadataScopePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcResourceMetadataProjectionPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSourceImportGrantPO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DdcPermissionMetadataSchemaTest {
    private static final List<Class<?>> MODELS = List.of(DdcMetadataScopePO.class, DdcSourceImportGrantPO.class,
            DdcI18nSourceVersionPO.class, DdcResourceMetadataProjectionPO.class, DdcI18nSourceMessagePO.class,
            DdcI18nImportReceiptPO.class, DdcCatalogActivationProjectionPO.class, DdcCatalogActivationProducerPO.class,
            DdcI18nPublishedOverridePO.class);
    private static final Set<String> BASE = Set.of("id", "tenant_id", "create_user_id", "create_time", "update_user_id", "update_time", "deleted_at", "version");

    @Test void theSingleFrozenMigrationIsAppendedWithoutReplacingTheFreshBaseline() throws Exception {
        String sql = DdcPermissionMetadataSchemaFixture.sql();
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
        assertThat(checksum).isEqualTo("5772a82da5f28e3e2a188737fce57aa4d8ade2c9c8a53f205fad6dde6690280a");
        try (var input = getClass().getClassLoader().getResourceAsStream("db/egon-mp/repository-manifest.json")) {
            var manifest = new ObjectMapper().readTree(input);
            assertThat(manifest.path("freshBaselineVersion").asText()).isEqualTo("20261018_001");
            assertThat(manifest.path("scripts").size()).isEqualTo(6);
            var current = manifest.path("scripts").get(5);
            assertThat(current.path("version").asText()).isEqualTo("20261023_001");
            assertThat(current.path("sha256").asText()).isEqualTo(checksum);
        }
        assertThat(sql).doesNotContain("DROP TABLE", "TRUNCATE", "CREATE TABLE IF NOT EXISTS");
    }

    @Test void everyPersistedMetadataFieldUsesAnExplicitTenantRoutedLogicalTable() throws Exception {
        String routing;
        try (var input = getClass().getClassLoader().getResourceAsStream("egon-tianshu-persistence.yml")) {
            routing = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (Class<?> model : MODELS) {
            String logical = model.getAnnotation(TableName.class).value();
            assertThat(logical).doesNotEndWith("_t0");
            assertThat(routing).contains(logical + ": { type: STANDARD_TENANT_ID }");
            Set<String> columns = DdcPermissionMetadataSchemaFixture.columns(logical + "_t0");
            assertThat(columns).containsAll(BASE);
            columns.removeAll(BASE);
            Set<String> modelFields = Arrays.stream(model.getDeclaredFields()).map(field -> field.getAnnotation(TableField.class))
                    .filter(java.util.Objects::nonNull).map(TableField::value).collect(Collectors.toSet());
            assertThat(modelFields).as(model.getSimpleName()).containsExactlyInAnyOrderElementsOf(columns);
        }
    }
}
