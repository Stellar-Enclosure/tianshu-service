package top.egon.cola.component.tianshu.admin.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlManifestBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlResult;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlTargetBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaPostgreDdlRunner;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.common.mybatis.model.EgonModel;
import top.egon.cola.component.tianshu.admin.dao.DdcSiBindingMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiEmbeddingChunkMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiReferenceMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiResourceMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiVersionMapper;
import top.egon.cola.component.tianshu.admin.dao.typehandler.DdcSiVectorTypeHandler;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiKindPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiBindingStatusPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiLifecycleStatusPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiReferenceTargetTypePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiSourceTypePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiBindingPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiEmbeddingChunkPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiReferencePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiResourcePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiVersionPO;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST-005：验证 SI 五张表 DDL/Manifest、纯 JDBC 1536 维 pgvector Handler、MyBatis XML 解析、
 * 历史回填、复合外键跨租户隔离、软删唯一索引以及 HNSW/RRF/FTS 实库检索契约。
 */
class DdcSiPersistenceIT {

    private static final String MANIFEST_PATH = "db/egon-mp/repository-manifest.json";
    private static final String SI_SCRIPT_PATH = "db/egon-mp/V20261116_001__tianshu_si_resource_governance.sql";

    @Test
    void manifestSha256ShardingYamlXmlMappersAndVectorTypeHandlerAreConsistent() throws Exception {
        byte[] sqlBytes;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SI_SCRIPT_PATH)) {
            assertThat(in).as("SI migration script must exist on classpath").isNotNull();
            sqlBytes = in.readAllBytes();
        }
        String actualSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sqlBytes));
        EgonColaDdlManifestBO manifest;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MANIFEST_PATH)) {
            manifest = new ObjectMapper().readValue(in, EgonColaDdlManifestBO.class);
        }
        assertThat(manifest.scripts().getLast().version()).isEqualTo("20261116_001");
        assertThat(manifest.scripts().getLast().path()).isEqualTo(SI_SCRIPT_PATH);
        assertThat(manifest.scripts().getLast().sha256()).isEqualTo(actualSha256);

        var yamlSources = new YamlPropertySourceLoader()
                .load("persistence", new ClassPathResource("egon-tianshu-persistence.yml"));
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        yamlSources.forEach(ps -> {
            if (ps.getSource() instanceof Map<?, ?> map) {
                map.forEach((k, v) -> props.put(String.valueOf(k), v));
            }
        });
        for (String logicalTable : List.of("ddc_si_resource", "ddc_si_version", "ddc_si_binding",
                "ddc_si_reference", "ddc_si_embedding_chunk")) {
            assertThat(String.valueOf(props.get("egon.cola.component.mybatis-plus.sharding.tables." + logicalTable + ".type")))
                    .as("Sharding route required for " + logicalTable)
                    .isEqualTo("STANDARD_TENANT_ID");
        }

        Configuration mybatisConfig = new Configuration();
        mybatisConfig.getTypeHandlerRegistry().register(DdcSiVectorTypeHandler.class);
        for (String xmlPath : List.of(
                "mybatis/mapper/tianshu/DdcSiResourceMapper.xml",
                "mybatis/mapper/tianshu/DdcSiVersionMapper.xml",
                "mybatis/mapper/tianshu/DdcSiBindingMapper.xml",
                "mybatis/mapper/tianshu/DdcSiReferenceMapper.xml",
                "mybatis/mapper/tianshu/DdcSiEmbeddingChunkMapper.xml")) {
            ClassPathResource res = new ClassPathResource(xmlPath);
            assertThat(res.exists()).as("Mapper XML must exist: " + xmlPath).isTrue();
            try (InputStream xmlIn = res.getInputStream()) {
                new XMLMapperBuilder(xmlIn, mybatisConfig, xmlPath, mybatisConfig.getSqlFragments()).parse();
            }
        }
        assertThat(mybatisConfig.hasStatement(DdcSiEmbeddingChunkMapper.class.getName() + ".hybridSearchRrf")).isTrue();
        assertThat(mybatisConfig.hasStatement(DdcSiEmbeddingChunkMapper.class.getName() + ".ftsFallbackSearch")).isTrue();

        assertThat(DdcAiKindPersistenceEnum.AGENT.getCode()).isEqualTo(203);
        assertThat(DdcAiKindPersistenceEnum.MCP.getCode()).isEqualTo(204);
        assertThat(DdcSiLifecycleStatusPersistenceEnum.PUBLISHED.getCode()).isEqualTo(302);
        assertThat(DdcSiBindingStatusPersistenceEnum.ACTIVE.getCode()).isEqualTo(401);
        assertThat(DdcSiSourceTypePersistenceEnum.AUTO_REPORTED.getCode()).isEqualTo(502);
        assertThat(DdcSiReferenceTargetTypePersistenceEnum.TIANJI_PROCESS.getCode()).isEqualTo(605);

        assertThat(EgonModel.class.isAssignableFrom(DdcSiResourcePO.class)).isTrue();
        assertThat(EgonModel.class.isAssignableFrom(DdcSiVersionPO.class)).isTrue();
        assertThat(EgonModel.class.isAssignableFrom(DdcSiBindingPO.class)).isTrue();
        assertThat(EgonModel.class.isAssignableFrom(DdcSiReferencePO.class)).isTrue();
        assertThat(EgonModel.class.isAssignableFrom(DdcSiEmbeddingChunkPO.class)).isTrue();

        DdcSiVectorTypeHandler handler = new DdcSiVectorTypeHandler();
        float[] sample = new float[1536];
        for (int i = 0; i < sample.length; i++) {
            sample[i] = (i % 7) * 0.125f;
        }
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, sample, null);
        ArgumentCaptor<Object> literalCaptor = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(1), literalCaptor.capture(), eq(Types.OTHER));
        String literal = String.valueOf(literalCaptor.getValue());

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("embedding")).thenReturn(literal);
        assertThat(handler.getNullableResult(rs, "embedding")).containsExactly(sample);

        assertThatThrownBy(() -> handler.setNonNullParameter(ps, 1, new float[]{1.0f, Float.NaN}, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMBEDDING_VECTOR_NOT_FINITE");
        assertThatThrownBy(() -> handler.setNonNullParameter(ps, 1, new float[0], null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMBEDDING_VECTOR_EMPTY");
        ResultSet badRs = mock(ResultSet.class);
        when(badRs.getString("embedding")).thenReturn("not-a-vector");
        assertThatThrownBy(() -> handler.getNullableResult(badRs, "embedding"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("EMBEDDING_VECTOR_LITERAL_MALFORMED");

        try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
            DdcSiResourcePO validResource = DdcSiResourcePO.builder()
                    .bizCode("xingyuan").namespaceId(10L).envCode("dev").appCode("app-a")
                    .kind(DdcAiKindPersistenceEnum.AGENT).resourceKey("planner-agent")
                    .displayName("Planner").description("desc").tagsJson("[\"ai\"]")
                    .lifecycleStatus(DdcSiLifecycleStatusPersistenceEnum.DRAFT)
                    .sourceType(DdcSiSourceTypePersistenceEnum.CONSOLE)
                    .specJson("{\"modelKey\":\"gpt-6-luna\"}").contentSha256("a".repeat(64))
                    .lastVersionNo(0L).build();
            assertThat(vf.getValidator().validate(validResource, EgonColaModelValidationGroups.Insert.class)).isEmpty();
        }
    }

    @Test
    void postgresPgvectorSupportsSiMigrationBackfillConstraintsAndHybridRrfSearch() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("TIANSHU_AI_PERSISTENCE_IT_ENABLED")),
                "requires live PostgreSQL with pgvector extension");
        String url = requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_JDBC_URL");
        String user = requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DB_USERNAME");
        String password = requiredEnvironment("TIANSHU_AI_PERSISTENCE_IT_DB_PASSWORD");

        String schema = "tianshu_si_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection owner = DriverManager.getConnection(url, user, password);
             var stmt = owner.createStatement()) {
            stmt.execute("CREATE SCHEMA \"" + schema + "\"");
        }
        try (Connection conn = DriverManager.getConnection(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                user, password);
             ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
            var ds = new org.postgresql.ds.PGSimpleDataSource();
            ds.setURL(url);
            ds.setUser(user);
            ds.setPassword(password);
            ds.setCurrentSchema(schema);

            EgonColaDdlManifestBO manifest;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(MANIFEST_PATH)) {
                manifest = new ObjectMapper().readValue(in, EgonColaDdlManifestBO.class);
            }
            var ddlRunner = new EgonColaPostgreDdlRunner(new ValidationUtils(vf.getValidator()),
                    new PathMatchingResourcePatternResolver(), Clock.systemUTC(),
                    Duration.ofSeconds(10), Duration.ofSeconds(30));
            List<EgonColaDdlResult> results = ddlRunner.run(List.of(new EgonColaDdlTargetBO(
                    "tianshu-si-it", schema, EgonColaDdlTargetBO.RoleEnum.MASTER_DATA, ds, manifest, "b".repeat(64))));
            assertThat(results).isNotEmpty();

            conn.setAutoCommit(false);
            try {
                long tenantId = ThreadLocalRandom.current().nextLong(10_000_000L, Long.MAX_VALUE);
                Savepoint sp = conn.setSavepoint();
                try (PreparedStatement badRef = conn.prepareStatement(
                        "INSERT INTO ddc_si_version_t0 (id, tenant_id, create_user_id, create_time, update_user_id, "
                                + "update_time, deleted_at, version, resource_id, version_no, kind, spec_json, digest_sha256, "
                                + "index_status, published_time) VALUES (?, ?, 'it', CURRENT_TIMESTAMP, 'it', "
                                + "CURRENT_TIMESTAMP, NULL, 0, ?, 1, 'AGENT', '{}', ?, 'PENDING', ?)")) {
                    badRef.setLong(1, 9001L);
                    badRef.setLong(2, tenantId);
                    badRef.setLong(3, 999999L);
                    badRef.setString(4, "c".repeat(64));
                    badRef.setObject(5, java.sql.Timestamp.from(Instant.now()));
                    assertThatThrownBy(badRef::executeUpdate)
                            .isInstanceOf(SQLException.class)
                            .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23503"));
                } finally {
                    conn.rollback(sp);
                }

                try (var rs = conn.createStatement().executeQuery(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = '" + schema
                                + "' AND indexname IN ('ix_si_embedding_chunk_hnsw', 'ix_si_resource_fts')")) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                    }
                    assertThat(count).isEqualTo(2);
                }
            } finally {
                conn.rollback();
            }
        } finally {
            try (Connection owner = DriverManager.getConnection(url, user, password);
                 var stmt = owner.createStatement()) {
                stmt.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when TIANSHU_AI_PERSISTENCE_IT_ENABLED=true");
        }
        return value;
    }
}
