package top.egon.cola.component.tianshu.admin.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.operationlog.EgonOperationLogAutoConfiguration.TargetProperties;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogDestination;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogEvent;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPhaseEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.store.OutboxStore;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.tianshu.admin.service.audit.DdcOperationLogMetrics;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcOperationLogOutboxAdapterTest {
    private static final String ADAPTER = "top.egon.cola.component.tianshu.admin.outbox.DdcOperationLogOutboxAdapter";
    private static final Instant NOW = Instant.parse("2026-11-23T06:00:00Z");
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    private final ValidationUtils validation = new ValidationUtils(Validation.buildDefaultValidatorFactory().getValidator());
    private final OperationLogRepository target = mock(OperationLogRepository.class);
    private final DdcTenantRegistry tenants = mock(DdcTenantRegistry.class);
    private final OutboxStore store = mock(OutboxStore.class);
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    private final DdcOperationLogMetrics metrics = metrics(registry);
    private final TargetProperties properties = new TargetProperties().setTransactionTimeout(Duration.ofSeconds(5));

    @AfterEach void clearContext() { MDC.clear(); }

    @Test void observesOnlyCommittedNewSourceEventsWithoutPayloadDimensions() throws Exception {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var outbox = mock(TransactionalOutbox.class);
        when(outbox.enqueue(any())).thenReturn(new top.egon.cola.component.outbox.api.OutboxReceipt("101", "audit:23:101", true));
        when(store.prepareGeneratedMessageId(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var metricsType = Class.forName("top.egon.cola.component.tianshu.admin.service.audit.DdcOperationLogMetrics");
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory(Map.of("registry", registry));
        var metrics = metricsType.getConstructor(org.springframework.beans.factory.ObjectProvider.class)
                .newInstance(beans.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        var source = (OperationLogDestination) Class.forName(ADAPTER + "$ProducerStrategy")
                .getConstructor(TransactionalOutbox.class, ValidationUtils.class, OutboxStore.class, metricsType)
                .newInstance(outbox, validation, store, metrics);
        var sourceDataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:sqlite::memory:", "", "");
        var transactions = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(sourceDataSource));
        transactions.executeWithoutResult(status -> {
            source.append(event());
            assertThat(registry.find("tianshu.operation.log.audit.accepted").counter()).isNull();
        });
        assertThat(registry.get("tianshu.operation.log.audit.accepted").counter().count()).isEqualTo(1);
        transactions.executeWithoutResult(status -> {
            source.append(event());
            status.setRollbackOnly();
        });
        assertThat(registry.get("tianshu.operation.log.audit.accepted").counter().count()).isEqualTo(1);
        when(outbox.enqueue(any())).thenReturn(new top.egon.cola.component.outbox.api.OutboxReceipt("101", "audit:23:101", false));
        transactions.executeWithoutResult(status -> source.append(event()));
        assertThat(registry.get("tianshu.operation.log.audit.accepted").counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }

    @Test void consumerCountsCommittedDuplicateConflictAndMissingPartitionAtTheirConfirmedBoundaries() throws Exception {
        var handler = handler();
        org.mockito.Mockito.doReturn(true, false).when(target).acceptAndConfirm(any());
        assertThat(handler.deliver(context(event())).kind()).isEqualTo(DeliveryResult.Kind.SUCCESS);
        assertThat(registry.get("tianshu.operation.log.audit.committed").counter().count()).isEqualTo(1);
        assertThat(handler.deliver(context(event())).kind()).isEqualTo(DeliveryResult.Kind.SUCCESS);
        assertThat(registry.get("tianshu.operation.log.audit.committed").counter().count()).isEqualTo(1);
        assertThat(registry.get("tianshu.operation.log.duplicate.same").counter().count()).isEqualTo(1);

        doThrow(new OperationLogRepository.PermanentRejectionException("OPERATION_LOG_RECEIPT_CONFLICT"))
                .when(target).acceptAndConfirm(any());
        assertThat(handler.deliver(context(event())).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        assertThat(registry.get("tianshu.operation.log.duplicate.conflict").counter().count()).isEqualTo(1);

        doThrow(new org.springframework.dao.DataIntegrityViolationException("safe diagnostic",
                new org.postgresql.util.PSQLException(new org.postgresql.util.ServerErrorMessage(
                        "C23514\0Mninguna partición aloja la fila\0RExecFindPartition\0\0"))))
                .when(target).acceptAndConfirm(any());
        assertThat(handler.deliver(context(event())).kind()).isEqualTo(DeliveryResult.Kind.RETRYABLE_FAILURE);
        assertThat(registry.get("tianshu.operation.log.partition.missing").counter().count()).isEqualTo(1);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("safe diagnostic",
                new java.sql.SQLException("new row violates check constraint", "23514")))
                .when(target).acceptAndConfirm(any());
        assertThat(handler.deliver(context(event())).kind()).isEqualTo(DeliveryResult.Kind.RETRYABLE_FAILURE);
        assertThat(registry.get("tianshu.operation.log.partition.missing").counter().count()).isEqualTo(1);
        assertThat(registry.get("tianshu.operation.log.audit.committed").counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }

    @Test void optionalOrFailedMetricRegistriesDoNotChangeBusinessResults() {
        var empty = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        var missing = new DdcOperationLogMetrics(empty.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
        org.assertj.core.api.Assertions.assertThatCode(missing::accepted).doesNotThrowAnyException();
        var broken = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(broken.getIfAvailable()).thenThrow(new IllegalStateException("registry unavailable"));
        @SuppressWarnings("unchecked")
        var isolated = new DdcOperationLogMetrics((org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry>) broken);
        org.assertj.core.api.Assertions.assertThatCode(isolated::committed).doesNotThrowAnyException();
    }

    @Test void acceptOnlyAfterCommitAndAlwaysRestoreTheCallingTenant() throws Exception {
        var event = event();
        MDC.put("tenantId", "77"); MDC.put("userId", "prior-user");
        doAnswer(invocation -> {
            assertThat(MDC.get("tenantId")).isEqualTo("23");
            assertThat(MDC.get("userId")).isEqualTo("system:operation-log:101");
            return true; // repository returns only after its target transaction commits
        }).when(target).acceptAndConfirm(any());
        assertThat(handler().deliver(context(event)).kind()).isEqualTo(DeliveryResult.Kind.SUCCESS);
        verify(target).acceptAndConfirm(event);
        assertThat(MDC.get("tenantId")).isEqualTo("77");
        assertThat(MDC.get("userId")).isEqualTo("prior-user");
    }

    @Test void retryCommitUnknownPreservesTheOriginalEventAndRestoresTenant() throws Exception {
        doThrow(new org.springframework.transaction.TransactionSystemException("commit outcome unknown"))
                .when(target).acceptAndConfirm(any());
        var handler = handler(); var envelope = context(event());
        assertThat(handler.deliver(envelope).kind()).isEqualTo(DeliveryResult.Kind.RETRYABLE_FAILURE);
        assertThat(handler.deliver(envelope).kind()).isEqualTo(DeliveryResult.Kind.RETRYABLE_FAILURE);
        var captured = org.mockito.ArgumentCaptor.forClass(OperationLogEvent.class);
        verify(target, org.mockito.Mockito.times(2)).acceptAndConfirm(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(value -> assertThat(value.getEventId()).isEqualTo(101L));
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(registry.find("tianshu.operation.log.audit.committed").counter()).isNull();
        assertThat(registry.find("tianshu.operation.log.duplicate.same").counter()).isNull();
    }

    @Test void permanentEnvelopeConflictDoesNotTouchTheTarget() throws Exception {
        var good = context(event());
        var invalid = new DeliveryContext("different-message", good.channel(), good.destination(), good.payload(),
                good.contentType(), good.schemaVersion(), good.headers(), null, 1, 12, good.deadline());
        assertThat(handler().deliver(invalid).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        verifyNoInteractions(target);
    }

    @Test void rejectsUnknownFieldsSourceHeaderAndNumericIdWireCoercions() throws Exception {
        var good = context(event()); var handler = handler();
        for (String payload : java.util.List.of(good.payload().replace("\"eventId\":\"101\"", "\"eventId\":101"),
                good.payload().replace("\"tenantId\":\"23\"", "\"tenantId\":23"),
                good.payload().replaceFirst("\\{", "{\"unregistered\":true,"))) {
            assertThat(handler.deliver(copy(good, payload, good.headers(), good.deadline())).kind())
                    .isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        }
        assertThat(handler.deliver(copy(good, good.payload(), Map.of("eventId", "101", "tenantId", "23",
                "sourceService", "untrusted-service"), good.deadline())).kind())
                .isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        verifyNoInteractions(target);
    }

    @Test void actionModuleAndSafeDetailsAreRevalidatedBeforePersistence() throws Exception {
        var handler = handler();
        var malformed = event().setModuleCode("I18N");
        assertThat(handler.deliver(context(malformed)).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        malformed = event(); malformed.getDetails().put("secret", "must never be stored");
        assertThat(handler.deliver(context(malformed)).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        malformed = event(); malformed.getDetails().put("revision", 2);
        assertThat(handler.deliver(context(malformed)).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        verifyNoInteractions(target);
    }

    @Test void rejectsUnknownTenantAndReceiptConflictsPermanently() throws Exception {
        var handler = handler();
        when(tenants.requireEnabledTenant(23L)).thenThrow(new IllegalArgumentException("TENANT_ID_UNREGISTERED"));
        assertThat(handler.deliver(context(event())).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        verifyNoInteractions(target);
        enableTenant();
        doThrow(new OperationLogRepository.PermanentRejectionException("OPERATION_LOG_RECEIPT_CONFLICT"))
                .when(target).acceptAndConfirm(any());
        assertThat(handler.deliver(context(event())).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test void insufficientDeadlineBudgetDoesNotStartATargetTransaction() throws Exception {
        var handler = handler(); var good = context(event());
        assertThat(handler.deliver(copy(good, good.payload(), good.headers(), NOW.plusSeconds(1))).kind())
                .isEqualTo(DeliveryResult.Kind.RETRYABLE_FAILURE);
        assertThat(handler.deliver(copy(good, good.payload(), good.headers(), NOW.minusSeconds(1))).kind())
                .isEqualTo(DeliveryResult.Kind.RETRYABLE_FAILURE);
        verifyNoInteractions(target);
    }

    @Test void sourceBuildsStableEnvelopeAndRejectsUnsafeFactsBeforeEnqueue() throws Exception {
        var outbox = mock(TransactionalOutbox.class);
        var store = mock(OutboxStore.class);
        when(store.prepareGeneratedMessageId(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var source = (OperationLogDestination) Class.forName(ADAPTER + "$ProducerStrategy")
                .getConstructor(TransactionalOutbox.class, ValidationUtils.class, OutboxStore.class, DdcOperationLogMetrics.class)
                .newInstance(outbox, validation, store, metrics);
        source.append(event());
        var captured = org.mockito.ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).enqueue(captured.capture());
        var envelope = captured.getValue();
        assertThat(envelope.messageId()).isEqualTo("101");
        assertThat(envelope.idempotencyKey()).isEqualTo("audit:23:101");
        assertThat(envelope.channel()).isEqualTo("tianshu.operation-log");
        assertThat(envelope.destination()).isEqualTo("tianshu-audit");
        assertThat(envelope.schemaVersion()).isEqualTo("1");
        assertThat(envelope.headers()).containsEntry("tenantId", "23").containsEntry("eventId", "101")
                .containsEntry("sourceService", "tianshu-admin");
        var invalid = event(); invalid.getDetails().put("token", "unsafe");
        assertThatThrownBy(() -> source.append(invalid)).isInstanceOf(IllegalArgumentException.class);
        verify(outbox).enqueue(any());
    }

    @Test void sourceAndConsumerKeepStableTransportAffinityForMultiplePrimaryGroups() throws Exception {
        var outbox = mock(TransactionalOutbox.class);
        var handler = handler(java.util.List.of(0, 7));
        when(store.prepareGeneratedMessageId(any())).thenAnswer(invocation ->
                top.egon.cola.component.outbox.api.OutboxShardIdentity.forSlot(7, invocation.getArgument(0)));
        var source = (OperationLogDestination) Class.forName(ADAPTER + "$ProducerStrategy")
                .getConstructor(TransactionalOutbox.class, ValidationUtils.class, OutboxStore.class, DdcOperationLogMetrics.class)
                .newInstance(outbox, validation, store, metrics);
        source.append(event());
        var captured = org.mockito.ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).enqueue(captured.capture());
        assertThat(captured.getValue().messageId()).isEqualTo("s1_00000007_101");
        assertThat(captured.getValue().idempotencyKey()).isEqualTo("s1_00000007_audit:23:101");
        var good = context(event());
        var sharded = new DeliveryContext(captured.getValue().messageId(), good.channel(), good.destination(), good.payload(),
                good.contentType(), good.schemaVersion(), good.headers(), null, 1, 12, good.deadline());
        assertThat(handler.deliver(sharded).kind()).isEqualTo(DeliveryResult.Kind.SUCCESS);
        var unknown = new DeliveryContext("s1_00000008_101", good.channel(), good.destination(), good.payload(),
                good.contentType(), good.schemaVersion(), good.headers(), null, 1, 12, good.deadline());
        assertThat(handler.deliver(unknown).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
    }

    @Test void registeredDetailValuesRejectUnregisteredCodesAndNonCanonicalNumbers() throws Exception {
        var handler = handler();
        for (String revision : java.util.List.of("01", "-1", "9223372036854775808")) {
            var bad = event(); bad.getDetails().put("revision", revision);
            assertThat(handler.deliver(context(bad)).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        }
        var bad = event().setActionCode("TIANSHU_CONFIG_PUBLISH");
        bad.getDetails().removeAll().put("publishStatus", "PUBLISHING");
        assertThat(handler.deliver(context(bad)).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        bad.getDetails().removeAll().put("publishStage", "arbitrary-unregistered-code");
        assertThat(handler.deliver(context(bad)).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        verifyNoInteractions(target);
    }

    @Test void publisherProducerConfigurationDoesNotRegisterAClaimHandlerOrTargetResources() throws Exception {
        var serializer = mock(top.egon.cola.component.outbox.serialization.OutboxMessageSerializer.class);
        when(serializer.serialize(any(), any())).thenReturn(
                new top.egon.cola.component.outbox.serialization.SerializedOutboxPayload(json.writeValueAsString(event()), 1024));
        when(store.prepareGeneratedMessageId(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(store.enqueue(any())).thenReturn(new top.egon.cola.component.outbox.api.OutboxReceipt("101", "audit:23:101", true));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration.class))
                .withUserConfiguration(Class.forName(ADAPTER + "$ProducerConfiguration"))
                .withPropertyValues("egon.cola.component.mybatis-plus.operation-log.enabled=true")
                .withBean("jacksonObjectMapper", ObjectMapper.class, () -> json)
                .withBean("egonColaValidationUtils", ValidationUtils.class, () -> validation)
                .withBean("outboxStore", OutboxStore.class, () -> store)
                .withBean("outboxMessageValidator", top.egon.cola.component.outbox.validation.OutboxMessageValidator.class,
                        () -> mock(top.egon.cola.component.outbox.validation.OutboxMessageValidator.class))
                .withBean("outboxMessageSerializer", top.egon.cola.component.outbox.serialization.OutboxMessageSerializer.class, () -> serializer)
                .withBean("outboxIdGenerator", top.egon.cola.component.outbox.api.OutboxIdGenerator.class,
                        () -> mock(top.egon.cola.component.outbox.api.OutboxIdGenerator.class))
                .withBean("outboxTransactionGuard", top.egon.cola.component.outbox.transaction.OutboxTransactionGuard.class,
                        () -> mock(top.egon.cola.component.outbox.transaction.OutboxTransactionGuard.class))
                .withBean("outboxAfterCommitBuffer", top.egon.cola.component.outbox.transaction.OutboxAfterCommitBuffer.class,
                        () -> mock(top.egon.cola.component.outbox.transaction.OutboxAfterCommitBuffer.class))
                .withBean("outboxMetrics", top.egon.cola.component.outbox.observability.OutboxMetrics.class,
                        () -> mock(top.egon.cola.component.outbox.observability.OutboxMetrics.class))
                .withBean("outboxStateMachineOutboxProperties", top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OperationLogDestination.class)
                            .doesNotHaveBean(OperationLogRepository.class).doesNotHaveBean(javax.sql.DataSource.class)
                            .doesNotHaveBean(DeliveryHandler.class).doesNotHaveBean(TransactionalOutbox.class);
                    assertThat(((org.springframework.beans.factory.support.AbstractBeanDefinition) context.getBeanFactory()
                            .getBeanDefinition("ddcOperationLogProducerRegistry")).isDefaultCandidate()).isFalse();
                    context.getBean(OperationLogDestination.class).append(event());
                    verify(store).enqueue(any());
                });
    }

    @Test void adminCannotEnableAnAcceptanceTimeoutThatExceedsTheDeliveryBudget() throws Exception {
        Class<?> configuration = Class.forName(ADAPTER + "$AdminConsumptionConfiguration");
        var factory = java.util.Arrays.stream(configuration.getDeclaredMethods())
                .filter(method -> method.getName().equals("handler")).findFirst().orElseThrow();
        assertThat(factory.trySetAccessible()).isTrue();
        var outbox = new top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties();
        outbox.getDelivery().setTimeout(Duration.ofSeconds(5));
        assertThatThrownBy(() -> factory.invoke(configuration.getConstructor().newInstance(), json, validation, target, tenants,
                new DdcTenantContextExecutor(), Clock.fixed(NOW, ZoneOffset.UTC), properties, java.util.List.of(0), outbox, metrics))
                .hasCauseInstanceOf(IllegalStateException.class).hasStackTraceContaining("TIANSHU_OPERATION_LOG_DELIVERY_BUDGET_INVALID");
        verifyNoInteractions(target);
    }

    private DeliveryHandler handler() throws Exception { return handler(java.util.List.of(0)); }
    private DeliveryHandler handler(java.util.List<Integer> slots) throws Exception {
        enableTenant();
        return (DeliveryHandler) Class.forName(ADAPTER + "$OperationLogDeliveryHandler")
                .getConstructor(ObjectMapper.class, ValidationUtils.class, OperationLogRepository.class,
                        DdcTenantRegistry.class, DdcTenantContextExecutor.class, Clock.class, TargetProperties.class, java.util.List.class, DdcOperationLogMetrics.class)
                .newInstance(json, validation, target, tenants, new DdcTenantContextExecutor(),
                        Clock.fixed(NOW, ZoneOffset.UTC), properties, slots, metrics);
    }
    private static DdcOperationLogMetrics metrics(io.micrometer.core.instrument.MeterRegistry registry) {
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory(Map.of("registry", registry));
        return new DdcOperationLogMetrics(beans.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class));
    }
    private void enableTenant() {
        org.mockito.Mockito.doReturn(new DdcTenantBindingDTO(23L, "tenant-23", true)).when(tenants).requireEnabledTenant(23L);
    }
    private OperationLogEvent event() {
        return OperationLogEvent.builder().schemaVersion(1).eventId(101L).operationId("op-101").tenantId(23L)
                .sourceService("tianshu-admin").moduleCode("CONFIG").actionCode("TIANSHU_CONFIG_UPDATE")
                .resourceType("CONFIG").resourceId("config-01").actorType("USER").actorId("user-352")
                .triggerType("HTTP").phase(OperationLogPhaseEnum.COMMAND).outcome(OperationLogOutcomeEnum.SUCCEEDED)
                .httpMethod("PUT").requestUrl("https://tianshu.example.invalid/api/configs/config-01")
                .routePattern("/api/configs/{id}").handlerMethod("example.ConfigService#update(java.lang.String)")
                .occurredAt(NOW.minusSeconds(10)).durationMs(3L).details(json.createObjectNode().put("revision", "2")).build();
    }
    private DeliveryContext context(OperationLogEvent event) throws Exception {
        return new DeliveryContext(Long.toString(event.getEventId()), "tianshu.operation-log", "tianshu-audit",
                json.writeValueAsString(event), "application/json", "1", Map.of("eventId", Long.toString(event.getEventId()),
                "tenantId", Long.toString(event.getTenantId()), "sourceService", event.getSourceService()), null,
                1, 12, NOW.plusSeconds(30));
    }
    private static DeliveryContext copy(DeliveryContext source, String payload, Map<String,String> headers, Instant deadline) {
        return new DeliveryContext(source.messageId(), source.channel(), source.destination(), payload, source.contentType(),
                source.schemaVersion(), headers, source.traceId(), source.attempt(), source.maxAttempts(), deadline);
    }
}
