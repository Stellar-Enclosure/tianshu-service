package top.egon.cola.component.tianshu.admin.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nImportReceiptMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcMetadataScopeMapper;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nImportReceiptConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcMetadataScopeConverterImpl;
import top.egon.cola.component.tianshu.admin.model.po.DdcMetadataScopePO;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcI18nImportReceiptRepositoryImpl;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcMetadataScopeRepositoryImpl;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DdcI18nSourceImportRepositoryContractTest {
    private static final String NAMESPACE = "top.egon.cola.component.tianshu.admin.dao.";

    @AfterEach void cleanup() { MDC.clear(); }

    @Test void everySelectUsesTrustedTenantAndCatalogScopeOnLogicalTables() throws Exception {
        var configuration = configuration();
        Map<String, Object> parameters = Map.of("tenantId", 101L, "catalogScopeId", 501L, "producerId", "order-admin",
                "bindingDigest", "a".repeat(64), "sourceVersion", "b".repeat(64), "sourceId", 701L,
                "operationId", "32fb3f12-7ce5-4e19-8665-a5db0d9c1baa");
        for (String statement : List.of("DdcMetadataScopeMapper.selectByCatalogScopeId", "DdcMetadataScopeMapper.selectEnabledFence",
                "DdcSourceImportGrantMapper.findEnabled",
                "DdcI18nSourceVersionMapper.findByVersion", "DdcResourceMetadataProjectionMapper.findBySource",
                "DdcI18nSourceMessageMapper.findBySource", "DdcI18nImportReceiptMapper.findByOperation")) {
            var bound = configuration.getMappedStatement(NAMESPACE + statement).getBoundSql(parameters);
            String sql = bound.getSql().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
            assertThat(sql).as(statement).contains("tenant_id=?", "catalog_scope_id=?", "deleted_atisnull")
                    .doesNotContain("_t0", "order-admin", "32fb3f12");
            assertThat(bound.getParameterMappings()).extracting("property").contains("tenantId", "catalogScopeId");
        }
    }

    @Test void exactParentBindingsAreReadTogetherBeforeTheirVersionFences() throws Exception {
        var configuration = configuration();
        Map<String, Object> parameters = Map.of("tenantId", 101L, "catalogScopeId", 501L);
        String serialize = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper.selectByCatalogScopeId")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
        String parents = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper.selectEnabledFence")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
        assertThat(serialize).contains("FROM ddc_metadata_scope").doesNotContain("JOIN", "FOR UPDATE");
        assertThat(parents).contains("a.id=s.app_id", "n.id=s.namespace_id", "nb.id=s.binding_id", "n.namespace_code=s.namespace_code",
                "b.enabled=true", "e.enabled=true", "a.enabled=true", "n.enabled=true", "nb.enabled=true")
                .doesNotContain("FOR UPDATE", "FOR SHARE");
    }

    @Test void sourceGrantUsesReplicaCandidateAndAConditionalPrimaryFence() throws Exception {
        var configuration = configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), mock(javax.sql.DataSource.class)));
        var properties = new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties();
        var tenantGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor(() -> "system:test", properties);
        new org.springframework.context.annotation.CommonAnnotationBeanPostProcessor().postProcessBeforeInitialization(tenantGuard, "sqlGuard");
        var routeGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaLocalWriteGuardInnerInterceptor(
                query -> new top.egon.cola.component.common.mybatis.routing.EgonColaRouteResult(
                        List.of(new top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO("primary", "public", query.logicalTable())), "a".repeat(64)),
                tenantGuard, mock(top.egon.cola.component.common.mybatis.routing.EgonColaTwoLevelRouteStrategy.class), Map.of());
        var read = configuration.getMappedStatement(NAMESPACE + "DdcSourceImportGrantMapper.findEnabled");
        var readParameters = Map.<String, Object>of("tenantId", 101L, "catalogScopeId", 501L,
                "producerId", "producer", "bindingDigest", "a".repeat(64));
        assertThat(read.getBoundSql(readParameters).getSql().toLowerCase()).doesNotContain("for update", "for share");
        var statement = configuration.getMappedStatement(NAMESPACE + "DdcSourceImportGrantMapper.fenceEnabled");
        var parameters = Map.<String, Object>of("tenantId", 101L, "id", 9L, "expectedVersion", 7L,
                "actor", "system:test", "now", java.time.Instant.parse("2026-10-28T00:00:00Z"));
        String sql = statement.getBoundSql(parameters).getSql().toLowerCase().replaceAll("\\s+", " ");
        assertThat(sql).contains("tenant_id = ?", "id = ?", "version = ?", "version = version + 1", "enabled = true", "deleted_at is null");
        MDC.put("tenantId", "101"); MDC.put("userId", "system:test");
        var executor = mock(org.apache.ibatis.executor.Executor.class);
        var invocation = new org.apache.ibatis.plugin.Invocation(executor,
                org.apache.ibatis.executor.Executor.class.getMethod("update", org.apache.ibatis.mapping.MappedStatement.class, Object.class),
                new Object[]{statement, parameters});
        org.assertj.core.api.Assertions.assertThatCode(() ->
                new top.egon.cola.component.common.mybatis.interceptor.EgonColaOriginalSqlGuardInterceptor(properties).intercept(invocation))
                .doesNotThrowAnyException();
        var handler = configuration.newStatementHandler(executor, statement, parameters,
                org.apache.ibatis.session.RowBounds.DEFAULT, null, statement.getBoundSql(parameters));
        org.assertj.core.api.Assertions.assertThatCode(() -> routeGuard.beforePrepare(handler, null, null)).doesNotThrowAnyException();
    }

    @Test void metadataParentFencesUseTheTrustedTenantAndTheSamePrimaryGroup() throws Exception {
        var configuration = configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), mock(javax.sql.DataSource.class)));
        var properties = new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties();
        var tenantGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor(() -> "system:test", properties);
        new org.springframework.context.annotation.CommonAnnotationBeanPostProcessor().postProcessBeforeInitialization(tenantGuard, "sqlGuard");
        var routeGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaLocalWriteGuardInnerInterceptor(
                query -> new top.egon.cola.component.common.mybatis.routing.EgonColaRouteResult(
                        List.of(new top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO("primary", "public", query.logicalTable())), "a".repeat(64)),
                tenantGuard, mock(top.egon.cola.component.common.mybatis.routing.EgonColaTwoLevelRouteStrategy.class), Map.of());
        var read = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper.selectEnabledFence");
        var readParameters = Map.<String, Object>of("tenantId", 101L, "catalogScopeId", 501L);
        var parameters = Map.<String, Object>of("tenantId", 101L, "id", 9L, "expectedVersion", 7L,
                "actor", "system:test", "now", java.time.Instant.parse("2026-10-28T00:00:00Z"));
        MDC.put("tenantId", "101"); MDC.put("userId", "system:test");
        tenantGuard.beforeQuery(mock(org.apache.ibatis.executor.Executor.class), read, readParameters,
                org.apache.ibatis.session.RowBounds.DEFAULT, null, read.getBoundSql(readParameters));
        for (String operation : List.of("fenceBiz", "fenceNamespace", "fenceEnv", "fenceApp", "fenceBinding")) {
            var statement = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper." + operation);
            String sql = statement.getBoundSql(parameters).getSql().toLowerCase().replaceAll("\\s+", " ");
            assertThat(sql).startsWith("update ").contains("tenant_id = ?", "id = ?", "version = ?", "version = version + 1",
                    "enabled = true", "deleted_at is null").doesNotContain("select", "for update", "for share");
            var executor = mock(org.apache.ibatis.executor.Executor.class);
            var invocation = new org.apache.ibatis.plugin.Invocation(executor,
                    org.apache.ibatis.executor.Executor.class.getMethod("update", org.apache.ibatis.mapping.MappedStatement.class, Object.class),
                    new Object[]{statement, parameters});
            org.assertj.core.api.Assertions.assertThatCode(() ->
                    new top.egon.cola.component.common.mybatis.interceptor.EgonColaOriginalSqlGuardInterceptor(properties).intercept(invocation))
                    .doesNotThrowAnyException();
            var handler = configuration.newStatementHandler(executor, statement, parameters,
                    org.apache.ibatis.session.RowBounds.DEFAULT, null, statement.getBoundSql(parameters));
            org.assertj.core.api.Assertions.assertThatCode(() -> routeGuard.beforePrepare(handler, null, null)).doesNotThrowAnyException();
        }
    }

    @Test void metadataParentFenceAcrossPrimaryGroupsMarksTheTransactionRollbackOnly() throws Exception {
        var configuration = configuration();
        var source = mock(javax.sql.DataSource.class);
        var holder = new org.springframework.jdbc.datasource.ConnectionHolder(mock(java.sql.Connection.class));
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("test",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), source));
        var properties = new top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties();
        var tenantGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaTenantIdGuardInnerInterceptor(() -> "system:test", properties);
        new org.springframework.context.annotation.CommonAnnotationBeanPostProcessor().postProcessBeforeInitialization(tenantGuard, "sqlGuard");
        var routeGuard = new top.egon.cola.component.common.mybatis.interceptor.EgonColaLocalWriteGuardInnerInterceptor(
                query -> new top.egon.cola.component.common.mybatis.routing.EgonColaRouteResult(
                        List.of(new top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO(
                                query.logicalTable().equals("ddc_biz") ? "primary_a" : "primary_b", "public", query.logicalTable())), "a".repeat(64)),
                tenantGuard, mock(top.egon.cola.component.common.mybatis.routing.EgonColaTwoLevelRouteStrategy.class), Map.of());
        var parameters = Map.<String, Object>of("tenantId", 101L, "id", 9L, "expectedVersion", 7L,
                "actor", "system:test", "now", java.time.Instant.parse("2026-10-28T00:00:00Z"));
        MDC.put("tenantId", "101"); MDC.put("userId", "system:test");
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(source, holder);
        try {
            var biz = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper.fenceBiz");
            var namespace = configuration.getMappedStatement(NAMESPACE + "DdcMetadataScopeMapper.fenceNamespace");
            var first = configuration.newStatementHandler(mock(org.apache.ibatis.executor.Executor.class), biz, parameters,
                    org.apache.ibatis.session.RowBounds.DEFAULT, null, biz.getBoundSql(parameters));
            var second = configuration.newStatementHandler(mock(org.apache.ibatis.executor.Executor.class), namespace, parameters,
                    org.apache.ibatis.session.RowBounds.DEFAULT, null, namespace.getBoundSql(parameters));
            routeGuard.beforePrepare(first, null, null);
            assertThatThrownBy(() -> routeGuard.beforePrepare(second, null, null)).hasMessageContaining("LOCAL_WRITE_TARGET_MISMATCH");
            assertThat(holder.isRollbackOnly()).isTrue();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResourceIfPossible(source);
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test void staleSourceGrantRollsBackWithoutReturningAnAuthorization() {
        var mapper = mock(top.egon.cola.component.tianshu.admin.dao.DdcSourceImportGrantMapper.class);
        var row = top.egon.cola.component.tianshu.admin.model.po.DdcSourceImportGrantPO.builder().id(9L).version(7L).build();
        when(mapper.findEnabled(101L, 501L, "producer", "a".repeat(64))).thenReturn(row);
        when(mapper.fenceEnabled(eq(101L), eq(9L), eq(7L), eq("system:test"), any())).thenReturn(0);
        var repository = new top.egon.cola.component.tianshu.admin.repository.impl.DdcSourceImportGrantRepositoryImpl(
                mapper, new top.egon.cola.component.tianshu.admin.model.convertor.DdcSourceImportGrantConverterImpl(), null, null);
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        MDC.put("tenantId", "101"); MDC.put("userId", "system:test");

        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(manager).execute(
                status -> repository.lockEnabled(501L, "producer", "a".repeat(64))))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        verify(manager).rollback(any());
    }

    @Test void sourceGrantReceiptKeepsTheObservedBusinessVersionAfterFencing() {
        var mapper = mock(top.egon.cola.component.tianshu.admin.dao.DdcSourceImportGrantMapper.class);
        var row = top.egon.cola.component.tianshu.admin.model.po.DdcSourceImportGrantPO.builder()
                .id(9L).version(7L).tenantId(101L).catalogScopeId(501L).producerId("producer").enabled(true).build();
        when(mapper.findEnabled(101L, 501L, "producer", "a".repeat(64))).thenReturn(row);
        when(mapper.fenceEnabled(eq(101L), eq(9L), eq(7L), eq("system:test"), any())).thenReturn(1);
        var repository = new top.egon.cola.component.tianshu.admin.repository.impl.DdcSourceImportGrantRepositoryImpl(
                mapper, new top.egon.cola.component.tianshu.admin.model.convertor.DdcSourceImportGrantConverterImpl(), null, null);
        MDC.put("tenantId", "101"); MDC.put("userId", "system:test");

        assertThat(repository.lockEnabled(501L, "producer", "a".repeat(64))).get()
                .satisfies(grant -> assertThat(grant.getVersion()).isEqualTo(7L));
        verify(mapper).fenceEnabled(eq(101L), eq(9L), eq(7L), eq("system:test"), any());
    }

    @Test void repositoriesUseTheCurrentTrustedContextAndCannotPerformNakedOperationLookup() {
        DdcMetadataScopeMapper scopes = mock(DdcMetadataScopeMapper.class);
        DdcI18nImportReceiptMapper receipts = mock(DdcI18nImportReceiptMapper.class);
        var scopeRepository = new DdcMetadataScopeRepositoryImpl(scopes, new DdcMetadataScopeConverterImpl(), null, null);
        var receiptRepository = new DdcI18nImportReceiptRepositoryImpl(receipts, new DdcI18nImportReceiptConverterImpl(), null, null);
        when(scopes.selectByCatalogScopeId(101L, 501L)).thenReturn(DdcMetadataScopePO.builder().id(1L).tenantId(101L).catalogScopeId(501L).build());
        var snapshot = metadataSnapshot();
        when(scopes.selectEnabledFence(101L, 501L)).thenReturn(snapshot);
        when(scopes.fenceBiz(eq(101L), eq(11L), eq(1L), eq("system:test"), any())).thenReturn(1);
        when(scopes.fenceNamespace(eq(101L), eq(12L), eq(2L), eq("system:test"), any())).thenReturn(1);
        when(scopes.fenceEnv(eq(101L), eq(13L), eq(3L), eq("system:test"), any())).thenReturn(1);
        when(scopes.fenceApp(eq(101L), eq(14L), eq(4L), eq("system:test"), any())).thenReturn(1);
        when(scopes.fenceBinding(eq(101L), eq(15L), eq(5L), eq("system:test"), any())).thenReturn(1);
        MDC.put("tenantId", "101"); MDC.put("userId", "system:test");
        scopeRepository.lockEnabledTarget(501L);
        receiptRepository.findByOperation(501L, "order-admin", "operation");
        var order = inOrder(scopes);
        order.verify(scopes).selectByCatalogScopeId(101L, 501L);
        order.verify(scopes).selectEnabledFence(101L, 501L);
        order.verify(scopes).fenceBiz(eq(101L), eq(11L), eq(1L), eq("system:test"), any());
        order.verify(scopes).fenceNamespace(eq(101L), eq(12L), eq(2L), eq("system:test"), any());
        order.verify(scopes).fenceEnv(eq(101L), eq(13L), eq(3L), eq("system:test"), any());
        order.verify(scopes).fenceApp(eq(101L), eq(14L), eq(4L), eq("system:test"), any());
        order.verify(scopes).fenceBinding(eq(101L), eq(15L), eq(5L), eq("system:test"), any());
        verify(receipts).findByOperation(101L, 501L, "order-admin", "operation");
        MDC.remove("tenantId");
        assertThatThrownBy(() -> receiptRepository.findByOperation(501L, "order-admin", "operation")).isInstanceOf(IllegalStateException.class);
    }

    @Test void staleMetadataParentRollsBackAllEarlierFences() {
        var mapper = mock(DdcMetadataScopeMapper.class);
        when(mapper.selectByCatalogScopeId(101L, 501L)).thenReturn(DdcMetadataScopePO.builder().id(1L).tenantId(101L).catalogScopeId(501L).build());
        when(mapper.selectEnabledFence(101L, 501L)).thenReturn(metadataSnapshot());
        when(mapper.fenceBiz(eq(101L), eq(11L), eq(1L), eq("system:test"), any())).thenReturn(1);
        when(mapper.fenceNamespace(eq(101L), eq(12L), eq(2L), eq("system:test"), any())).thenReturn(0);
        var repository = new DdcMetadataScopeRepositoryImpl(mapper, new DdcMetadataScopeConverterImpl(), null, null);
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        MDC.put("tenantId", "101"); MDC.put("userId", "system:test");

        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(manager).execute(
                status -> repository.lockEnabledTarget(501L))).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        verify(manager).rollback(any());
        verify(mapper, never()).fenceEnv(any(), any(), any(), any(), any());
        verify(mapper, never()).fenceBinding(any(), any(), any(), any(), any());
    }

    private static top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataFenceSnapshotBO metadataSnapshot() {
        var value = new top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataFenceSnapshotBO();
        value.setScopeId(1L); value.setBizId(11L); value.setBizVersion(1L);
        value.setNamespaceId(12L); value.setNamespaceVersion(2L); value.setEnvId(13L); value.setEnvVersion(3L);
        value.setAppId(14L); value.setAppVersion(4L); value.setBindingId(15L); value.setBindingVersion(5L);
        return value;
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        for (String mapper : List.of("DdcMetadataScope", "DdcSourceImportGrant", "DdcI18nSourceVersion", "DdcResourceMetadataProjection", "DdcI18nSourceMessage", "DdcI18nImportReceipt")) {
            String resource = "mybatis/mapper/tianshu/" + mapper + "Mapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).isNotNull();
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
