package top.egon.cola.component.tianshu.admin.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
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
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor;
import top.egon.cola.component.common.mybatis.model.EgonModel;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigVersionMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiBindingMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiEmbeddingChunkMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiReferenceMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiResourceMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiVersionMapper;
import top.egon.cola.component.tianshu.admin.dao.typehandler.DdcJsonbTypeHandler;
import top.egon.cola.component.tianshu.admin.dao.typehandler.DdcSiVectorTypeHandler;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiKindPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiBindingStatusPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiLifecycleStatusPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiReferenceTargetTypePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiSourceTypePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigVersionPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiBindingPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiEmbeddingChunkPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiReferencePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiResourcePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiVersionPO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiResourceConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiEnumConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceRepository;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcSiResourceRepositoryImpl;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;

/**
 * TEST-005：验证 SI 五张表 DDL/Manifest、纯 JDBC 1536 维 pgvector Handler、MyBatis XML 解析、
 * 历史回填、复合外键跨租户隔离、软删唯一索引以及 HNSW/RRF/FTS 实库检索契约。
 */
class DdcSiPersistenceIT {

    private static final String MANIFEST_PATH = "db/egon-mp/repository-manifest.json";
    private static final String SI_SCRIPT_PATH = "db/egon-mp/V20261116_001__tianshu_si_resource_governance.sql";

    @Test
    void frozenHistoryReadHasSeparateTenantScopedBoundedRepositoryContracts() throws Exception {
        var repository = top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository.class;
        assertThat(repository.getMethod("allocateVersionId").getReturnType()).isEqualTo(long.class);
        assertThat(repository.getMethod("findHistoryVersionByResourceAndNo", Long.class, Long.class, Long.class).getReturnType()).isEqualTo(Optional.class);
        assertThat(repository.getMethod("findHistoryVersionsByIds", Long.class, List.class).getReturnType()).isEqualTo(List.class);
        String xml;
        try (InputStream input = new ClassPathResource("mybatis/mapper/tianshu/DdcSiVersionMapper.xml").getInputStream()) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String next = xml.substring(xml.indexOf("<select id=\"selectNextVersionNo\""), xml.indexOf("</select>", xml.indexOf("<select id=\"selectNextVersionNo\"")));
        assertThat(next).doesNotContain("deleted_at IS NULL");
    }

    @Test
    void frozenHistoryStatementsRemainTenantGuardedAndOnlyExplicitlyAllowlisted() throws Exception {
        var config = new MybatisConfiguration();
        String xml = "mybatis/mapper/tianshu/DdcSiVersionMapper.xml";
        try (InputStream input = new ClassPathResource(xml).getInputStream()) {
            new XMLMapperBuilder(input, config, xml, config.getSqlFragments()).parse();
        }
        var properties = new EgonColaMybatisPlusProperties();
        var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("egon-tianshu-persistence.yml"));
        Map<String, Object> values = new java.util.HashMap<>();
        yaml.getObject().forEach((key, value) -> values.put(key.toString(), value));
        new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(values))
                .bind(EgonColaMybatisPlusProperties.PREFIX + ".lifecycle",
                        org.springframework.boot.context.properties.bind.Bindable.ofInstance(properties.getLifecycle()));
        assertThat(properties.getLifecycle().getStatementPolicies().get(DdcSiVersionMapper.class.getName() + ".selectHistoryByIds").getScope())
                .isEqualTo(EgonColaMybatisPlusProperties.LifecycleScopeEnum.ID_BATCH);
        assertThat(properties.getLifecycle().getStatementPolicies().get(DdcSiVersionMapper.class.getName() + ".selectHistoryByIds").getKeyColumn())
                .isEqualTo("id");
        var guard = new EgonColaTenantIdGuardInnerInterceptor(() -> "system:si-test", properties);
        new org.springframework.context.annotation.CommonAnnotationBeanPostProcessor()
                .postProcessBeforeInitialization(guard, "siHistoryGuard");
        var previous = org.slf4j.MDC.getCopyOfContextMap();
        EgonColaTenantIdProvider.initialize("tenantId");
        org.slf4j.MDC.put("tenantId", "42");
        try {
            for (String name : List.of("selectNextVersionNo", "selectHistoryByResourceIdAndVersionNo", "selectHistoryByIds")) {
                var statement = config.getMappedStatement(DdcSiVersionMapper.class.getName() + "." + name);
                Map<String, Object> parameters = new java.util.HashMap<>(Map.of("tenantId", 42L,
                        "resourceId", 52L, "versionNo", 7L, "ids", List.of(62L, 63L)));
                assertThatCode(() -> guard.beforeQuery(null, statement, parameters,
                        org.apache.ibatis.session.RowBounds.DEFAULT, null, statement.getBoundSql(parameters)))
                        .as(name).doesNotThrowAnyException();
                parameters.put("tenantId", 43L);
                assertThatThrownBy(() -> guard.beforeQuery(null, statement, parameters,
                        org.apache.ibatis.session.RowBounds.DEFAULT, null, statement.getBoundSql(parameters)))
                        .hasMessage("TENANT_CONTEXT_MISMATCH");
                parameters.put("tenantId", 42L);
                if (!name.equals("selectHistoryByIds")) {
                    parameters.put("resourceId", null);
                    assertThatThrownBy(() -> guard.beforeQuery(null, statement, parameters,
                            org.apache.ibatis.session.RowBounds.DEFAULT, null, statement.getBoundSql(parameters)))
                            .hasMessage("LIFECYCLE_KEY_REQUIRED");
                }
            }
            var unregistered = new EgonColaTenantIdGuardInnerInterceptor(() -> "system:si-test",
                    new EgonColaMybatisPlusProperties());
            var history = config.getMappedStatement(DdcSiVersionMapper.class.getName() + ".selectHistoryByIds");
            var parameters = Map.of("tenantId", 42L, "ids", List.of(62L));
            assertThatThrownBy(() -> unregistered.beforeQuery(null, history, parameters,
                    org.apache.ibatis.session.RowBounds.DEFAULT, null, history.getBoundSql(parameters)))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            if (previous == null) {
                org.slf4j.MDC.clear();
            } else {
                org.slf4j.MDC.setContextMap(previous);
            }
        }
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var method = DdcSiVersionMapper.class.getMethod("selectHistoryByIds", Long.class, List.class);
            var validator = validatorFactory.getValidator().forExecutables();
            var mapper = mock(DdcSiVersionMapper.class);
            assertThat(validator.validateParameters(mapper, method, new Object[]{42L, List.of(62L)},
                    EgonColaModelValidationGroups.Query.class)).isEmpty();
            for (List<Long> invalid : List.of(List.<Long>of(), List.of(0L),
                    java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList())) {
                assertThat(validator.validateParameters(mapper, method, new Object[]{42L, invalid},
                        EgonColaModelValidationGroups.Query.class)).isNotEmpty();
            }
        }
    }

    @Test
    void repositoryPreservesAllocatedIdentityAndRejectsMissingSnapshotAndUnsafeBatch() {
        var mapper = mock(DdcSiVersionMapper.class);
        var converter = mock(DdcSiResourceConverter.class);
        var generator = mock(EgonColaIdentifierGenerator.class);
        var repository = new DdcSiResourceRepositoryImpl(mock(DdcSiResourceMapper.class), mapper,
                mock(DdcSiBindingMapper.class), mock(DdcSiReferenceMapper.class), mock(DdcNamespaceRepository.class),
                converter, mock(DdcSiEnumConverter.class), new EgonColaMybatisPlusProperties(), generator);
        when(generator.nextId(any(DdcSiVersionPO.class))).thenReturn(901L, 0L);
        assertThat(repository.allocateVersionId()).isEqualTo(901L);
        assertThatThrownBy(repository::allocateVersionId).hasMessage("TIANSHU_SI_VERSION_ID_UNAVAILABLE");
        assertThatThrownBy(() -> repository.insertVersion(DdcSiVersionBO.builder().id(901L).build()))
                .hasMessage("TIANSHU_SI_VERSION_SNAPSHOT_REQUIRED");
        assertThatThrownBy(() -> repository.insertVersion(DdcSiVersionBO.builder()
                .dependencyManifestJson(new ObjectMapper().createObjectNode()).build()))
                .hasMessage("TIANSHU_SI_VERSION_SNAPSHOT_REQUIRED");
        var candidate = DdcSiVersionBO.builder().id(901L)
                .dependencyManifestJson(new ObjectMapper().createObjectNode().put("schemaVersion", "1")).build();
        var row = new DdcSiVersionPO();
        row.setId(901L);
        row.setDependencyManifestJson(candidate.getDependencyManifestJson());
        when(converter.toVersionPo(candidate)).thenReturn(row);
        when(mapper.insert(row)).thenReturn(1);
        when(converter.toVersionBo(row)).thenReturn(candidate);
        assertThat(repository.insertVersion(candidate).getId()).isEqualTo(901L);
        verify(mapper).insert(row);
        when(mapper.selectNextVersionNo(42L, 52L)).thenReturn(8L, 0L);
        assertThat(repository.allocateNextVersionNo(42L, 52L)).isEqualTo(8L);
        assertThatThrownBy(() -> repository.allocateNextVersionNo(42L, 52L))
                .hasMessage("TIANSHU_SI_VERSION_NUMBER_UNAVAILABLE");
        assertThat(repository.findHistoryVersionsByIds(42L, List.of())).isEmpty();
        for (List<Long> invalid : List.of(List.of(1L, 1L), List.of(0L),
                java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList())) {
            assertThatThrownBy(() -> repository.findHistoryVersionsByIds(42L, invalid))
                    .hasMessage("TIANSHU_SI_HISTORY_BATCH_INVALID");
        }
        verify(mapper, never()).selectHistoryByIds(anyLong(), anyList());
    }

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
        assertThat(manifest.scripts()).filteredOn(script -> "20261116_001".equals(script.version()))
                .singleElement().satisfies(script -> {
                    assertThat(script.path()).isEqualTo(SI_SCRIPT_PATH);
                    assertThat(script.sha256()).isEqualTo(actualSha256);
                });

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
    void configAndSiVersionMappersBindTheManifestOnGeneratedInsertAndExplicitRead() throws Exception {
        var mapper = new ObjectMapper();
        var config = new MybatisConfiguration();
        assertManifestMapping(config, DdcConfigVersionMapper.class,
                new DdcConfigVersionPO().setManifestJson(mapper.createObjectNode().put("schemaVersion", "1")),
                "manifestJson", "manifest_json");
        assertManifestMapping(config, DdcSiVersionMapper.class,
                new DdcSiVersionPO().setDependencyManifestJson(mapper.createObjectNode().put("schemaVersion", "1")),
                "dependencyManifestJson", "dependency_manifest_json");
    }

    private static void assertManifestMapping(MybatisConfiguration config, Class<?> mapperType, EgonModel po,
                                              String property, String column) throws Exception {
        String xml = "mybatis/mapper/tianshu/" + mapperType.getSimpleName() + ".xml";
        try (InputStream input = new ClassPathResource(xml).getInputStream()) {
            new XMLMapperBuilder(input, config, xml, config.getSqlFragments()).parse();
        }
        assertThat(TableInfoHelper.getTableInfo(po.getClass()).getFieldList()).filteredOn(f -> f.getProperty().equals(property))
                .singleElement().satisfies(field -> {
                    assertThat(field.getColumn()).isEqualTo(column);
                    assertThat(field.getJdbcType()).isSameAs(JdbcType.OTHER);
                    assertThat(field.getUpdateStrategy().name()).isEqualTo("NEVER");
                });
        String namespace = mapperType.getName();
        var insert = config.getMappedStatement(namespace + ".insert").getBoundSql(po);
        assertThat(insert.getSql()).contains(column);
        assertThat(insert.getParameterMappings()).filteredOn(m -> m.getProperty().equals(property))
                .singleElement().satisfies(mapping -> {
                    assertThat(mapping.getJdbcType()).isSameAs(JdbcType.OTHER);
                    assertThat(mapping.getTypeHandler()).isInstanceOf(DdcJsonbTypeHandler.class);
                });
        var lookup = config.getMappedStatement(namespace + ".selectActiveById");
        assertThat(lookup.getBoundSql(Map.of("tenantId", 42L, "id", 52L)).getSql()).contains("v." + column);
        assertThat(lookup.getResultMaps().getFirst().getResultMappings()).filteredOn(m -> m.getProperty().equals(property))
                .singleElement().satisfies(mapping -> {
                    assertThat(mapping.getJdbcType()).isSameAs(JdbcType.OTHER);
                    assertThat(mapping.getTypeHandler()).isInstanceOf(DdcJsonbTypeHandler.class);
                });
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
            var fixedPublicScripts = manifest.scripts().subList(manifest.scripts().size() - 2, manifest.scripts().size());
            assertThat(fixedPublicScripts).extracting(EgonColaDdlManifestBO.ScriptBO::version)
                    .containsExactly("20261118_001", "20261119_001");
            assertThat(fixedPublicScripts).extracting(EgonColaDdlManifestBO.ScriptBO::path).containsExactly(
                    "db/egon-mp/V20261118_001__tianshu_native_config_rollout.sql",
                    "db/egon-mp/V20261119_001__tianshu_service_observation.sql");
            // These two migrations own fixed public relations, outside this SI random-schema fixture.
            manifest = new EgonColaDdlManifestBO(manifest.family(),
                    manifest.scripts().subList(0, manifest.scripts().size() - 2), manifest.freshBaselineVersion());
            var ddlRunner = new EgonColaPostgreDdlRunner(new ValidationUtils(vf.getValidator()),
                    new PathMatchingResourcePatternResolver(), Clock.systemUTC(),
                    Duration.ofSeconds(10), Duration.ofSeconds(30));
            List<EgonColaDdlResult> results = ddlRunner.run(List.of(new EgonColaDdlTargetBO(
                    "tianshu-si-it", schema, EgonColaDdlTargetBO.RoleEnum.MASTER_DATA, ds, manifest, "b".repeat(64))));
            assertThat(results).isNotEmpty();
            assertJsonbRoundTrip(conn);

            conn.setAutoCommit(false);
            try {
                long tenantId = ThreadLocalRandom.current().nextLong(10_000_000L, Long.MAX_VALUE);
                Savepoint sp = conn.setSavepoint();
                try (PreparedStatement badRef = conn.prepareStatement(
                        "INSERT INTO ddc_si_version_t0 (id, tenant_id, create_user_id, create_time, update_user_id, "
                                + "update_time, deleted_at, version, resource_id, version_no, kind, spec_json, digest_sha256, "
                                + "index_status, published_time, dependency_manifest_json) "
                                + "VALUES (?, ?, 'it', CURRENT_TIMESTAMP, 'it', CURRENT_TIMESTAMP, NULL, 0, "
                                + "?, 1, 'AGENT', '{}', ?, 'PENDING', ?, '{\"schemaVersion\":\"1\"}'::jsonb)")) {
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

                assertFrozenHistorySqlAgainstPostgres(conn, tenantId);

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

    private static void assertFrozenHistorySqlAgainstPostgres(Connection connection, long tenantId) throws Exception {
        try (var namespace = connection.prepareStatement("""
                INSERT INTO ddc_namespace_t0 (id, tenant_id, create_user_id, create_time, update_user_id,
                    update_time, version, public_id, namespace, created_at, updated_at, namespace_code, biz_code)
                VALUES (7001, ?, 'it', CURRENT_TIMESTAMP, 'it', CURRENT_TIMESTAMP, 0,
                    'history-ns', 'history-ns', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'history', 'it')
                """);
             var resource = connection.prepareStatement("""
                INSERT INTO ddc_si_resource_t0 (id, tenant_id, create_user_id, create_time, update_user_id,
                    update_time, version, biz_code, namespace_id, env_code, app_code, kind, resource_key,
                    display_name, lifecycle_status, source_type, spec_json, content_sha256)
                VALUES (8001, ?, 'it', CURRENT_TIMESTAMP, 'it', CURRENT_TIMESTAMP, 0, 'it', 7001,
                    'prod', '', 'AGENT', 'history-agent', 'History agent', 'ARCHIVED', 'CONSOLE', '{}', ?)
                """);
             var version = connection.prepareStatement("""
                INSERT INTO ddc_si_version_t0 (id, tenant_id, create_user_id, create_time, update_user_id,
                    update_time, deleted_at, version, resource_id, version_no, kind, spec_json, digest_sha256,
                    index_status, published_time, dependency_manifest_json)
                VALUES (?, ?, 'it', CURRENT_TIMESTAMP, 'it', CURRENT_TIMESTAMP, ?, 0, 8001, ?,
                    'AGENT', '{}', ?, 'PENDING', CURRENT_TIMESTAMP, ?)
                """)) {
            namespace.setLong(1, tenantId);
            namespace.executeUpdate();
            resource.setLong(1, tenantId);
            resource.setString(2, "a".repeat(64));
            resource.executeUpdate();
            version.setLong(1, 9002L);
            version.setLong(2, tenantId);
            version.setObject(3, java.time.LocalDateTime.parse("2026-11-16T10:00:00"));
            version.setLong(4, 9L);
            version.setString(5, "a".repeat(64));
            new DdcJsonbTypeHandler().setNonNullParameter(version, 6, new ObjectMapper()
                    .readTree("{\"schemaVersion\":\"1\",\"root\":{\"envCode\":\"dev\"}}"), JdbcType.OTHER);
            version.executeUpdate();
            version.setLong(1, 9003L);
            version.setNull(3, Types.TIMESTAMP);
            version.setLong(4, 2L);
            new DdcJsonbTypeHandler().setNonNullParameter(version, 6,
                    new ObjectMapper().readTree("{\"schemaVersion\":\"1\"}"), JdbcType.OTHER);
            version.executeUpdate();
        }
        var configuration = new MybatisConfiguration();
        String path = "mybatis/mapper/tianshu/DdcSiVersionMapper.xml";
        try (var input = new ClassPathResource(path).getInputStream()) {
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var parameters = Map.<String, Object>of("tenantId", tenantId, "resourceId", 8001L,
                "versionNo", 9L, "ids", List.of(9003L, 9002L));
        var next = configuration.getMappedStatement(DdcSiVersionMapper.class.getName() + ".selectNextVersionNo");
        try (var query = physicalVersionQuery(connection, next, parameters); var rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isEqualTo(10L);
        }
        var history = configuration.getMappedStatement(
                DdcSiVersionMapper.class.getName() + ".selectHistoryByResourceIdAndVersionNo");
        try (var query = physicalVersionQuery(connection, history, parameters); var rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("id")).isEqualTo(9002L);
            assertThat(rows.getTimestamp("deleted_at")).isNotNull();
            assertThat(rows.getString("env_code")).isEqualTo("dev");
            assertThat(rows.next()).isFalse();
        }
        var batch = configuration.getMappedStatement(DdcSiVersionMapper.class.getName() + ".selectHistoryByIds");
        try (var query = physicalVersionQuery(connection, batch, parameters); var rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("id")).isEqualTo(9002L);
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("id")).isEqualTo(9003L);
            assertThat(rows.getString("env_code")).isNull();
            assertThat(rows.next()).isFalse();
        }
        var foreignTenant = new java.util.HashMap<>(parameters);
        foreignTenant.put("tenantId", tenantId == Long.MAX_VALUE ? 1L : tenantId + 1L);
        try (var query = physicalVersionQuery(connection, batch, foreignTenant); var rows = query.executeQuery()) {
            assertThat(rows.next()).isFalse();
        }
    }

    private static PreparedStatement physicalVersionQuery(Connection connection,
            org.apache.ibatis.mapping.MappedStatement statement, Map<String, Object> parameters) throws SQLException {
        var sql = statement.getBoundSql(parameters);
        // Use the owned schema's physical table; the production MappedStatement is separately checked with the tenant guard.
        var query = connection.prepareStatement(sql.getSql().replaceAll("\\bddc_si_version\\b", "ddc_si_version_t0"));
        try {
            new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, parameters, sql)
                    .setParameters(query);
            return query;
        } catch (RuntimeException failure) {
            try {
                query.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static void assertJsonbRoundTrip(Connection connection) throws Exception {
        var handler = new DdcJsonbTypeHandler();
        var value = new ObjectMapper().readTree("{\"schemaVersion\":\"1\",\"label\":\"配置\",\"nodes\":[]}");
        try (var statement = connection.prepareStatement("SELECT ? AS manifest, pg_typeof(?)::text, NULL::jsonb")) {
            handler.setNonNullParameter(statement, 1, value, JdbcType.OTHER);
            handler.setNonNullParameter(statement, 2, value, JdbcType.OTHER);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(2)).isEqualTo("jsonb");
                assertThat(handler.getNullableResult(rows, "manifest")).isEqualTo(value);
                assertThat(handler.getNullableResult(rows, 3)).isNull();
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
