package top.egon.cola.component.tianshu.admin.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.config.DdcServiceObservationBindings;
import top.egon.cola.component.tianshu.admin.controller.register.DdcRegistryAdminController;
import top.egon.cola.component.tianshu.admin.dao.DdcServiceObservationMapper;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ListPage;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.Mode;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ResourceKind;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.ScopeVO;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesApiClient.WatchHandle;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcServiceObservationExporter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAppBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcBizBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcEnvBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcServiceObservationConverter;
import top.egon.cola.component.tianshu.admin.model.po.DdcServiceObservationPO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO.EndpointVO;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcServiceObservationRepositoryImpl;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigService;
import top.egon.cola.component.tianshu.admin.service.management.DdcManagementFacade;
import top.egon.cola.component.tianshu.admin.service.management.DdcRegistryAdminPageService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcAppService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcBizService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcNamespaceEnvAppBindingService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository.AcceptanceEnum.*;

class DdcServiceObservationRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(1001L, "tenant-1001", "observer:test");
    private static final DdcServiceObservationBindingVO BINDING = binding("service-uid", "tenant-1001", Set.of("workload-one", "workload-two"));
    private final DdcServiceObservationMapper mapper = mock(DdcServiceObservationMapper.class);
    private final EgonColaIdentifierGenerator ids = mock(EgonColaIdentifierGenerator.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final AtomicReference<DdcServiceObservationPO> stored = new AtomicReference<>();
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
    private final java.sql.Connection connection = mock(java.sql.Connection.class);
    private final java.sql.Statement statement = mock(java.sql.Statement.class);
    private final java.sql.ResultSet epochs = mock(java.sql.ResultSet.class);
    private DdcServiceObservationConverter converter;
    private DdcServiceObservationRepositoryImpl repository;
    @TempDir private Path scratch;

    @BeforeEach
    void prepare() throws Exception {
        MDC.put("tenantId", TENANT.tenantId().toString());
        MDC.put("userId", TENANT.actor());
        validator.afterPropertiesSet();
        converter = (DdcServiceObservationConverter) Class.forName(DdcServiceObservationConverter.class.getName() + "Impl")
                .getConstructor().newInstance();
        repository = new DdcServiceObservationRepositoryImpl(mapper, converter, new EgonColaMybatisPlusProperties(), ids,
                new DdcTenantContextExecutor(), transactions, new ObjectMapper().findAndRegisterModules(), clock, validator, dataSource);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(transactions.getPropagationBehavior()).thenReturn(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        when(transactions.getIsolationLevel()).thenReturn(TransactionDefinition.ISOLATION_READ_COMMITTED);
        when(transactions.getTimeout()).thenReturn(8);
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
        var numbers = new AtomicLong(9000);
        when(ids.nextId(any())).thenAnswer(invocation -> numbers.incrementAndGet());
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT nextval('public.ddc_service_observation_epoch')")).thenReturn(epochs);
        when(epochs.next()).thenReturn(true, false, true, false);
        when(epochs.getLong(1)).thenReturn(10L, 11L);
        when(mapper.selectActive(any())).thenAnswer(invocation -> stored.get());
        when(mapper.selectActiveForUpdate(any())).thenAnswer(invocation -> stored.get());
        when(mapper.insertIfAbsent(any())).thenAnswer(invocation -> stored.compareAndSet(null, copy(invocation.getArgument(0))) ? 1 : 0);
        when(mapper.advance(any(), anyLong(), anyLong(), anyLong())).thenAnswer(invocation -> {
            var current = stored.get();
            var row = copy(invocation.getArgument(0));
            if (current == null || current.getVersion() != (long) invocation.getArgument(1)
                    || current.getWriterEpoch() != (long) invocation.getArgument(2)
                    || current.getReportSeq() != (long) invocation.getArgument(3)) return 0;
            row.setVersion(current.getVersion() + 1);
            stored.set(row);
            return 1;
        });
    }

    @AfterEach
    void close() {
        validator.close();
        MDC.clear();
    }

    @Test
    void exposesASeparateDurableServiceObservationAuthority() throws Exception {
        Class<?> repository = Class.forName("top.egon.cola.component.tianshu.admin.repository.DdcServiceObservationRepository");
        assertThat(repository.getMethods()).extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("nextWriterEpoch", "upsert", "findCurrent");
        assertThatCode(this.repository::validateTransactionConfiguration).doesNotThrowAnyException();
        when(transactions.isReadOnly()).thenReturn(true);
        assertThatThrownBy(this.repository::validateTransactionConfiguration)
                .hasMessage("TIANSHU_SERVICE_OBSERVATION_PRIMARY_TRANSACTION_REQUIRED");
    }

    @Test
    void exactPayloadReplaysAndIncreasingSequenceAdvanceWhileContradictionsCannotReplaceFacts() {
        var first = fresh("pod-a", NOW.minusSeconds(1));
        assertThat(repository.upsert(TENANT, BINDING, 10, 1, NOW, first)).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, BINDING, 10, 1, NOW, first)).isEqualTo(SAME);
        assertThat(repository.upsert(TENANT, BINDING, 10, 1, NOW, fresh("pod-b", NOW.minusSeconds(1)))).isEqualTo(CONFLICT);
        assertThat(repository.upsert(TENANT, BINDING, 10, 2, NOW, fresh("pod-b", NOW.minusSeconds(1)))).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, BINDING, 10, 1, NOW, first)).isEqualTo(STALE);
        assertThat(repository.findCurrent(TENANT, BINDING).orElseThrow().endpoints().getFirst().podUid()).isEqualTo("pod-b");
        assertThat(stored.get().getIdentityTenantId()).isEqualTo("tenant-1001");
        assertThat(stored.get().getServiceUid()).isEqualTo("service-uid");
    }

    @Test
    void canonicalWorkloadSetOrderIsStableAndEveryPhysicalBindingPinRemainsImmutable() {
        var one = binding("service-uid", "tenant-1001", new LinkedHashSet<>(List.of("workload-one", "workload-two")));
        var two = binding("service-uid", "tenant-1001", new LinkedHashSet<>(List.of("workload-two", "workload-one")));
        var view = fresh("pod-a", NOW.minusSeconds(1));
        assertThat(repository.upsert(TENANT, one, 10, 1, NOW, view)).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, two, 10, 1, NOW, view)).isEqualTo(SAME);
        var changed = binding("another-service-uid", "tenant-1001", one.workloadUids());
        assertThat(repository.upsert(TENANT, changed, 11, 1, NOW, view)).isEqualTo(CONFLICT);
        assertThat(repository.findCurrent(TENANT, changed)).isEmpty();
        assertThatThrownBy(() -> repository.findCurrent(TENANT, binding("service-uid", "other-identity", one.workloadUids())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mapper, times(3)).selectActiveForUpdate(any());
    }

    @Test
    void durableEpochFencesThePreviousProcessAndClosesEveryFixedSequenceResource() throws Exception {
        assertThat(repository.nextWriterEpoch(TENANT)).isEqualTo(10);
        assertThat(repository.nextWriterEpoch(TENANT)).isEqualTo(11);
        repository.upsert(TENANT, BINDING, 10, 20, NOW, fresh("old", NOW.minusSeconds(1)));
        assertThat(repository.upsert(TENANT, BINDING, 11, 1, NOW, fresh("new", NOW.minusSeconds(1)))).isEqualTo(ADVANCED);
        assertThat(repository.upsert(TENANT, BINDING, 10, 21, NOW, unknown(StatusEnum.CLOSED))).isEqualTo(CONFLICT);
        assertThat(repository.findCurrent(TENANT, BINDING).orElseThrow().fresh()).isTrue();
        verify(statement, times(2)).setQueryTimeout(8);
        verify(statement, times(2)).close();
        verify(epochs, times(2)).close();
        verify(connection, times(2)).close();
    }

    @Test
    void disconnectFloorSurvivesNewWritersUntilANewerRelistAndExpiryDoesNotWrite() {
        var first = fresh("pod-a", NOW.minusSeconds(1));
        repository.upsert(TENANT, BINDING, 10, 1, NOW, first);
        assertThat(repository.upsert(TENANT, BINDING, 10, 2, NOW, unknown(StatusEnum.UNAVAILABLE))).isEqualTo(ADVANCED);
        assertThat(repository.findCurrent(TENANT, BINDING).orElseThrow().fresh()).isFalse();
        assertThat(repository.upsert(TENANT, BINDING, 11, 1, NOW, first)).isEqualTo(STALE);
        now.set(NOW.plusSeconds(2));
        assertThat(repository.upsert(TENANT, BINDING, 11, 2, now.get(), fresh("pod-b", NOW.plusSeconds(1)))).isEqualTo(ADVANCED);
        now.set(NOW.plusSeconds(60));
        clearInvocations(mapper);
        assertThat(repository.findCurrent(TENANT, BINDING)).isEmpty();
        verify(mapper, never()).advance(any(), anyLong(), anyLong(), anyLong());
        verify(mapper, never()).insertIfAbsent(any());
    }

    @Test
    void tenantMismatchFutureEvidenceAndTamperingCannotCreateAnAvailableView() {
        assertThatThrownBy(() -> repository.findCurrent(new DdcTenantContextDTO(2002L, "other", "observer:test"), BINDING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.upsert(TENANT, BINDING, 10, 1, NOW.plusSeconds(1), fresh("pod-a", NOW)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
        repository.upsert(TENANT, BINDING, 10, 1, NOW, fresh("pod-a", NOW.minusSeconds(1)));
        ((ObjectNode) stored.get().getViewJson().get("view")).put("serviceResourceVersion", "tampered");
        assertThatThrownBy(() -> repository.findCurrent(TENANT, BINDING)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TIANSHU_SERVICE_OBSERVATION_INVALID");
    }

    @Test
    void sequenceAndCasFailureRemainUnavailableAndNeverExposeStorageCredentials() throws Exception {
        when(connection.createStatement()).thenThrow(new java.sql.SQLException("sensitive-db"));
        assertThatThrownBy(() -> repository.nextWriterEpoch(TENANT)).isInstanceOf(DdcServiceObservationRepository.StoreUnavailableException.class)
                .hasMessage("TIANSHU_SERVICE_OBSERVATION_STORE_UNAVAILABLE").hasNoCause();
        repository.upsert(TENANT, BINDING, 10, 1, NOW, fresh("pod-a", NOW.minusSeconds(1)));
        when(mapper.advance(any(), anyLong(), anyLong(), anyLong())).thenReturn(0);
        assertThatThrownBy(() -> repository.upsert(TENANT, BINDING, 10, 2, NOW, fresh("pod-b", NOW.minusSeconds(1))))
                .isInstanceOf(DdcServiceObservationRepository.StoreUnavailableException.class).hasNoCause();
    }

    @Test
    void genericMapperWritesAndUnscopedReadsCannotBypassTheExactServiceBoundary() {
        var exact = mock(DdcServiceObservationMapper.class, CALLS_REAL_METHODS);
        var row = new DdcServiceObservationPO();
        assertThatThrownBy(() -> exact.insert(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exact.updateById(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exact.selectActiveById(1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exact.selectActiveByIds(List.of(1L))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exact.deleteVersionedById(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exact.restoreVersionedById(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> exact.insert(List.of(row), 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TIANSHU_SERVICE_OBSERVATION_IT_ENABLED", matches = "true")
    void postgresPersistsMonotonicFencesAndUtcFactsAcrossIndependentShardingContexts() throws Exception {
        String url = required("TIANSHU_SERVICE_OBSERVATION_IT_JDBC_URL");
        String user = required("TIANSHU_SERVICE_OBSERVATION_IT_DB_USERNAME");
        String password = required("TIANSHU_SERVICE_OBSERVATION_IT_DB_PASSWORD");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianshu_step33_service_observation")) {
            throw new IllegalStateException("TIANSHU_DISPOSABLE_SERVICE_OBSERVATION_DATABASE_REQUIRED");
        }
        var physical = new org.postgresql.ds.PGSimpleDataSource();
        physical.setURL(url); physical.setUser(user); physical.setPassword(password); physical.setSocketTimeout(10);
        try (var connection = physical.getConnection(); var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT current_database(),inet_server_addr()::text,NOT pg_is_in_recovery(),current_setting('server_version_num')::int")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("tianshu_step33_service_observation");
            assertThat(result.getString(2)).startsWith("127.0.0.1");
            assertThat(result.getBoolean(3)).isTrue(); assertThat(result.getInt(4)).isEqualTo(180003);
            var readiness = Class.forName("top.egon.cola.component.tianshu.admin.config.DdcCloudDeliveryReadyValidator")
                    .getDeclaredMethod("validateServiceObservation", java.sql.Connection.class);
            readiness.setAccessible(true);
            assertThatCode(() -> readiness.invoke(null, connection)).doesNotThrowAnyException();
        }
        var binding = new DdcServiceObservationBindingVO(BINDING.tenantId(), BINDING.identityTenantId(), BINDING.bizCode(),
                BINDING.namespaceCode(), BINDING.env(), BINDING.appCode(), "fixture-" + java.util.UUID.randomUUID(), BINDING.bindingRevision(),
                BINDING.clusterId(), BINDING.kubernetesNamespace(), BINDING.serviceName(), BINDING.serviceUid(), BINDING.portName(),
                BINDING.servicePort(), BINDING.protocol(), BINDING.logicalServiceName(), BINDING.group(), BINDING.version(), BINDING.workloadUids());
        var firstEpoch = new AtomicLong();
        var rowIds = new AtomicLong(System.currentTimeMillis() * 1000);
        pgRunner(url, user, password, rowIds).run(first -> {
            assertThat(first).hasNotFailed();
            var repository = first.getBean(DdcServiceObservationRepository.class);
            var contexts = first.getBean(DdcTenantContextExecutor.class);
            long epoch = contexts.call(TENANT, () -> repository.nextWriterEpoch(TENANT));
            firstEpoch.set(epoch);
            var candidate = fresh("pod-a", NOW.minusSeconds(1));
            assertThat(contexts.call(TENANT, () -> repository.upsert(TENANT, binding, epoch, 1, NOW, candidate))).isEqualTo(ADVANCED);
            assertThat(contexts.call(TENANT, () -> repository.upsert(TENANT, binding, epoch, 1, NOW, candidate))).isEqualTo(SAME);
        });
        pgRunner(url, user, password, rowIds).run(second -> {
            assertThat(second).hasNotFailed();
            var repository = second.getBean(DdcServiceObservationRepository.class);
            var contexts = second.getBean(DdcTenantContextExecutor.class);
            long next = contexts.call(TENANT, () -> repository.nextWriterEpoch(TENANT));
            assertThat(next).isGreaterThan(firstEpoch.get());
            var newer = fresh("pod-b", NOW.minusSeconds(1));
            assertThat(contexts.call(TENANT, () -> repository.upsert(TENANT, binding, next, 1, NOW, newer))).isEqualTo(ADVANCED);
            assertThat(contexts.call(TENANT, () -> repository.upsert(TENANT, binding, firstEpoch.get(), 99, NOW, unknown(StatusEnum.CLOSED))))
                    .isEqualTo(CONFLICT);
            assertThat(contexts.call(TENANT, () -> repository.findCurrent(TENANT, binding)).orElseThrow().endpoints().getFirst().podUid())
                    .isEqualTo("pod-b");
            assertThat(contexts.call(TENANT, () -> repository.upsert(TENANT, binding, next, 2, NOW, unknown(StatusEnum.UNAVAILABLE))))
                    .isEqualTo(ADVANCED);
            assertThat(contexts.call(TENANT, () -> repository.upsert(TENANT, binding, next, 3, NOW, newer))).isEqualTo(STALE);
        });
        try (var connection = physical.getConnection(); var query = connection.prepareStatement(
                "SELECT count(*),max(version),min(recorded_at) FROM public.ddc_service_observation_t0 WHERE tenant_id=? AND binding_id=?")) {
            query.setLong(1, TENANT.tenantId()); query.setString(2, binding.bindingId());
            try (var result = query.executeQuery()) {
                assertThat(result.next()).isTrue(); assertThat(result.getLong(1)).isEqualTo(1);
                assertThat(result.getLong(2)).isEqualTo(2);
                assertThat(result.getObject(3, java.time.OffsetDateTime.class).toInstant()).isEqualTo(NOW);
            }
            try (var corrupt = connection.prepareStatement(
                    "UPDATE public.ddc_service_observation_t0 SET report_seq=report_seq+1 WHERE tenant_id=? AND binding_id=?")) {
                corrupt.setLong(1, TENANT.tenantId()); corrupt.setString(2, binding.bindingId());
                assertThatThrownBy(corrupt::executeUpdate).isInstanceOfSatisfying(java.sql.SQLException.class,
                        failure -> assertThat(failure.getSQLState()).isEqualTo("23514"));
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TIANSHU_SERVICE_OBSERVATION_IT_ENABLED", matches = "true")
    void registryReadsKubernetesObservationsAcrossIndependentContextsAndExpiresWithoutQueryWrites() throws Exception {
        String url = required("TIANSHU_SERVICE_OBSERVATION_IT_JDBC_URL");
        String user = required("TIANSHU_SERVICE_OBSERVATION_IT_DB_USERNAME");
        String password = required("TIANSHU_SERVICE_OBSERVATION_IT_DB_PASSWORD");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianshu_step33_service_observation")) {
            throw new IllegalStateException("TIANSHU_DISPOSABLE_SERVICE_OBSERVATION_DATABASE_REQUIRED");
        }
        var binding = new DdcServiceObservationBindingVO(TENANT.tenantId(), TENANT.identityTenantId(), "reading", "default",
                "prod", "orders", "registry-" + UUID.randomUUID(), 2L, "cluster-a", "apps-prod", "orders", "service-uid",
                "grpc", 9090, "grpc", "OrdersService", "orders", "1.0.0", Set.of("workload-one"));
        Path path = scratch.resolve("service-bindings.json");
        var json = new ObjectMapper();
        byte[] document = json.writeValueAsBytes(Map.of("schemaVersion", "1", "bindings", List.of(binding)));
        Files.write(path, document);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document));
        var pg = new org.postgresql.ds.PGSimpleDataSource();
        pg.setURL(url); pg.setUser(user); pg.setPassword(password); pg.setSocketTimeout(10);
        var rowIds = new AtomicLong(System.currentTimeMillis() * 1000);
        var api = serviceApi(json);
        var namespace = Set.of(binding.kubernetesNamespace());
        pgRunner(url, user, password, rowIds).run(writer -> {
            assertThat(writer).hasNotFailed();
            var writerRepository = writer.getBean(DdcServiceObservationRepository.class);
            var writerTenants = writer.getBean(DdcTenantContextExecutor.class);
            pgRunner(url, user, password, rowIds)
                    .withUserConfiguration(DdcServiceObservationBindings.class)
                    .withBean("ddcTenantRegistry", DdcTenantRegistry.class, () -> new DdcTenantRegistry(
                            new DdcTenantAdminProperties().setBindings(List.of(new DdcTenantBindingDTO(
                                    TENANT.tenantId(), TENANT.identityTenantId(), true))), new DdcAdminProperties()))
                    .withPropertyValues(DdcServiceObservationBindings.PREFIX + ".bindings-file=" + path,
                            DdcServiceObservationBindings.PREFIX + ".bindings-sha256=" + sha256)
                    .run(reader -> {
                        assertThat(reader).hasNotFailed();
                        var readerRepository = reader.getBean(DdcServiceObservationRepository.class);
                        assertThat(readerRepository).isNotSameAs(writerRepository);
                        var inventory = reader.getBean(DdcServiceObservationBindings.class);
                        assertThat(inventory.bindings()).containsExactly(binding);
                        var facade = new DdcManagementFacade(mock(DdcConfigService.class), mock(DdcPublishService.class),
                                mock(DdcPublishTaskRepository.class), mock(DdcPublishAckRepository.class), scopeGate(),
                                mock(DdcNamespaceEnvAppBindingService.class), mock(DdcBizService.class), mock(DdcAppService.class),
                                inventory, readerRepository, reader.getBean(DdcTenantRegistry.class), clock);
                        var mvc = MockMvcBuilders.standaloneSetup(new DdcRegistryAdminController(facade,
                                new DdcRegistryAdminPageService(facade))).build();
                        try (var trusted = reader.getBean(DdcTenantContextExecutor.class).bind(TENANT);
                             var observer = new DdcKubernetesObservationService(api, clock, binding.clusterId(), namespace,
                                     Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofSeconds(5))) {
                            try (var exporter = new DdcServiceObservationExporter(observer, writerRepository, writerTenants,
                                    List.of(binding), clock, Duration.ofSeconds(30))) {
                                exporter.start();
                                mvc.perform(registryInstances(binding)).andExpect(status().isServiceUnavailable());
                                now.set(NOW.plusSeconds(1));
                                observer.refresh();
                                assertThat(observer.currentServiceView(binding).fresh()).isTrue();
                                exporter.exportOnce();

                                clearInvocations(api);
                                mvc.perform(registryInstances(binding)).andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.instances[0].metadata.podUid").value("pod-uid"))
                                        .andExpect(jsonPath("$.data.instances[0].metadata.ready").value("true"))
                                        .andExpect(jsonPath("$.data.instances[0].metadata.serving").doesNotExist())
                                        .andExpect(jsonPath("$.data.instances[0].port").value(19090))
                                        .andExpect(jsonPath("$.data.instances[0].host").doesNotExist());
                                mvc.perform(get("/api/v1/tianshu/registry/services")).andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.services[0].serviceId").value(binding.bindingId()));
                                assertThat(observationVersion(pg, binding)).isEqualTo(1L);

                                now.set(NOW.plusSeconds(11));
                                mvc.perform(registryInstances(binding)).andExpect(status().isServiceUnavailable())
                                        .andExpect(jsonPath("$.status").value("TIANSHU_SERVICE_OBSERVATION_UNAVAILABLE"));
                                mvc.perform(get("/api/v1/tianshu/registry/services/page").param("pageNo", "1").param("pageSize", "10"))
                                        .andExpect(status().isServiceUnavailable());
                                verifyNoInteractions(api);
                                assertThat(observationVersion(pg, binding)).isEqualTo(1L);
                            }
                            assertThat(readerRepository.findCurrent(TENANT, binding).orElseThrow().status()).isEqualTo(StatusEnum.CLOSED);
                            assertThat(observationVersion(pg, binding)).isEqualTo(2L);
                        }
                    });
        });
    }

    private static DdcScopeGate scopeGate() {
        var businesses = mock(DdcBizRepository.class);
        var applications = mock(DdcAppRepository.class);
        var environments = mock(DdcEnvRepository.class);
        var namespaces = mock(DdcNamespaceRepository.class);
        when(businesses.findByBizCode("reading")).thenReturn(Optional.of(DdcBizBO.builder().enabled(true).build()));
        when(applications.findByBizCodeAndAppCode("reading", "orders")).thenReturn(Optional.of(DdcAppBO.builder().enabled(true).build()));
        when(environments.findByEnvCode("prod")).thenReturn(Optional.of(DdcEnvBO.builder().enabled(true).build()));
        when(namespaces.findByBizCodeAndNamespaceCode("reading", "default"))
                .thenReturn(Optional.of(DdcNamespaceBO.builder().enabled(true).build()));
        return new DdcScopeGate(businesses, applications, environments, namespaces);
    }

    private static DdcKubernetesApiClient serviceApi(ObjectMapper json) throws Exception {
        Map<ResourceKind, List<JsonNode>> resources = Map.of(
                ResourceKind.SERVICE, List.of(json.readTree("""
                        {"apiVersion":"v1","kind":"Service","metadata":{"name":"orders","namespace":"apps-prod","uid":"service-uid","resourceVersion":"1"},
                         "spec":{"selector":{"app":"orders"},"ports":[{"name":"grpc","protocol":"TCP","port":9090,"targetPort":19090}]}}
                        """)),
                ResourceKind.STATEFUL_SET, List.of(json.readTree("""
                        {"apiVersion":"apps/v1","kind":"StatefulSet","metadata":{"name":"orders","namespace":"apps-prod","uid":"workload-one","resourceVersion":"1"}}
                        """)),
                ResourceKind.POD, List.of(json.readTree("""
                        {"apiVersion":"v1","kind":"Pod","metadata":{"name":"orders-0","namespace":"apps-prod","uid":"pod-uid","resourceVersion":"1",
                          "labels":{"app":"orders"},"ownerReferences":[{"apiVersion":"apps/v1","kind":"StatefulSet","name":"orders","uid":"workload-one","controller":true}]}}
                        """)),
                ResourceKind.ENDPOINT_SLICE, List.of(json.readTree("""
                        {"apiVersion":"discovery.k8s.io/v1","kind":"EndpointSlice","metadata":{"name":"orders-abc","namespace":"apps-prod","uid":"slice-uid","resourceVersion":"1",
                          "labels":{"kubernetes.io/service-name":"orders"},"ownerReferences":[{"apiVersion":"v1","kind":"Service","name":"orders","uid":"service-uid","controller":true}]},
                          "addressType":"IPv4","ports":[{"name":"grpc","protocol":"TCP","port":19090}],
                          "endpoints":[{"addresses":["192.0.2.45"],"targetRef":{"kind":"Pod","name":"orders-0","namespace":"apps-prod","uid":"pod-uid"},"conditions":{"ready":true}}]}
                        """)));
        var api = mock(DdcKubernetesApiClient.class);
        when(api.scope()).thenReturn(new ScopeVO(Mode.OBSERVER, Set.of("apps-prod"), Set.of()));
        when(api.list(any(ResourceKind.class), eq("apps-prod"), isNull(), eq(500)))
                .thenAnswer(call -> new ListPage("1", null, resources.getOrDefault(call.getArgument(0), List.of())));
        when(api.watch(any(ResourceKind.class), eq("apps-prod"), eq("1"), eq(Duration.ofSeconds(5)), any()))
                .thenAnswer(call -> {
                    var watch = mock(WatchHandle.class);
                    when(watch.completion()).thenReturn(new CompletableFuture<>());
                    return watch;
                });
        return api;
    }

    private static MockHttpServletRequestBuilder registryInstances(DdcServiceObservationBindingVO binding) {
        return get("/api/v1/tianshu/registry/instances").param("bizCode", binding.bizCode())
                .param("appCode", binding.appCode()).param("env", binding.env()).param("serviceKind", "RPC_PROVIDER")
                .param("protocol", binding.protocol()).param("serviceName", binding.logicalServiceName())
                .param("bindingId", binding.bindingId());
    }

    private static long observationVersion(javax.sql.DataSource pg, DdcServiceObservationBindingVO binding) throws Exception {
        try (var connection = pg.getConnection(); var query = connection.prepareStatement(
                "SELECT version FROM public.ddc_service_observation_t0 WHERE tenant_id=? AND binding_id=?")) {
            query.setLong(1, binding.tenantId());
            query.setString(2, binding.bindingId());
            try (var row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                long version = row.getLong(1);
                assertThat(row.next()).isFalse();
                return version;
            }
        }
    }

    private org.springframework.boot.test.context.runner.ApplicationContextRunner pgRunner(
            String url, String user, String password, AtomicLong rowIds) {
        return new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration.class,
                        top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusAutoConfiguration.class,
                        top.egon.cola.component.common.mybatis.autoconfigure.EgonColaShardingAutoConfiguration.class,
                        com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration.class,
                        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
                        org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
                        org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration.class))
                .withUserConfiguration(ServiceMapperConfiguration.class, DdcTenantContextExecutor.class, DdcServiceObservationRepositoryImpl.class)
                .withBean("ddcServiceObservationConverterImpl", DdcServiceObservationConverter.class, () -> converter)
                .withBean("jacksonObjectMapper", ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean("ddcPublishClock", Clock.class, () -> clock)
                .withBean("egonColaIdentifierGenerator", EgonColaIdentifierGenerator.class, () -> {
                    var generator = mock(EgonColaIdentifierGenerator.class);
                    when(generator.nextId(any())).thenAnswer(invocation -> rowIds.incrementAndGet());
                    return generator;
                }).withPropertyValues(
                        "spring.application.name=service-observation-fixture",
                        "egon.cola.component.mybatis-plus.enabled=true",
                        "egon.cola.component.mybatis-plus.ddl.enabled=false",
                        "egon.cola.component.mybatis-plus.sharding.enabled=true",
                        "egon.cola.component.mybatis-plus.sharding.mode=SHARDING",
                        "egon.cola.component.mybatis-plus.sharding.config-style=STRATEGY",
                        "egon.cola.component.mybatis-plus.sharding.transaction-default-type=LOCAL",
                        "egon.cola.component.mybatis-plus.sharding.data-sources[0].name=tianshu_0",
                        "egon.cola.component.mybatis-plus.sharding.data-sources[0].logical-name=tianshu_0",
                        "egon.cola.component.mybatis-plus.sharding.data-sources[0].role=PRIMARY",
                        "egon.cola.component.mybatis-plus.sharding.data-sources[0].driver-class-name=org.postgresql.Driver",
                        "egon.cola.component.mybatis-plus.sharding.data-sources[0].jdbc-url=" + url,
                        "egon.cola.component.mybatis-plus.sharding.data-sources[0].username=" + user,
                        "egon.cola.component.mybatis-plus.sharding.data-sources[0].password=" + password,
                        "egon.cola.component.mybatis-plus.sharding.tables.ddc_service_observation.type=STANDARD_TENANT_ID",
                        "mybatis-plus.mapper-locations=classpath*:mybatis/mapper/tianshu/DdcServiceObservationMapper.xml");
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ServiceMapperConfiguration {
        @org.springframework.context.annotation.Bean("ddcServiceObservationMapper")
        org.mybatis.spring.mapper.MapperFactoryBean<DdcServiceObservationMapper> serviceMapper(
                @org.springframework.beans.factory.annotation.Qualifier("sqlSessionFactory") org.apache.ibatis.session.SqlSessionFactory factory) {
            var mapper = new org.mybatis.spring.mapper.MapperFactoryBean<>(DdcServiceObservationMapper.class);
            mapper.setSqlSessionFactory(factory);
            return mapper;
        }

        @org.springframework.context.annotation.Bean("ddcServiceObservationTransactionTemplate")
        TransactionTemplate serviceTransactions(@org.springframework.beans.factory.annotation.Qualifier("transactionManager")
                org.springframework.transaction.PlatformTransactionManager manager) {
            var template = new TransactionTemplate(manager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            template.setTimeout(8);
            return template;
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("SERVICE_OBSERVATION_FIXTURE_SETTING_REQUIRED");
        return value;
    }

    private DdcServiceObservationPO copy(DdcServiceObservationPO row) {
        return converter.toSource(converter.toTarget(row));
    }

    private static DdcServiceObservationBindingVO binding(String uid, String identity, Set<String> workloads) {
        return new DdcServiceObservationBindingVO(1001L, identity, "reading", "default", "prod", "orders", "orders-service", 2,
                "cluster-a", "apps-prod", "orders", uid, "grpc", 9090, "grpc", "OrdersService", "orders", "1.0.0", workloads);
    }

    private static DdcServiceObservationViewVO fresh(String pod, Instant observed) {
        return new DdcServiceObservationViewVO(StatusEnum.FRESH, null, observed, observed.plusSeconds(30), "service-rv",
                List.of(new EndpointVO(pod, "orders-0", 19090, true, true, false)));
    }

    private static DdcServiceObservationViewVO unknown(StatusEnum status) {
        return new DdcServiceObservationViewVO(status, "OBSERVATION_UNAVAILABLE", null, null, null, List.of());
    }
}
