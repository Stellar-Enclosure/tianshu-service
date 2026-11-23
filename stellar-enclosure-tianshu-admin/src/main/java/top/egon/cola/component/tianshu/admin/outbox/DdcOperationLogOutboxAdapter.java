package top.egon.cola.component.tianshu.admin.outbox;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.operationlog.EgonOperationLogAutoConfiguration.TargetProperties;
import top.egon.cola.component.common.mybatis.operationlog.EgonOperationLogProperties;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogDestination;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogEvent;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogWriteModeEnum;
import top.egon.cola.component.common.mybatis.routing.EgonColaRoutingProfileBO;
import top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties;
import top.egon.cola.component.outbox.api.OutboxIdGenerator;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.OutboxShardIdentity;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.common.exception.OutboxValidationException;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.outbox.observability.OutboxMetrics;
import top.egon.cola.component.outbox.persistence.OutboxShardRouting;
import top.egon.cola.component.outbox.serialization.OutboxMessageSerializer;
import top.egon.cola.component.outbox.store.OutboxStore;
import top.egon.cola.component.outbox.transaction.DefaultTransactionalOutbox;
import top.egon.cola.component.outbox.transaction.OutboxAfterCommitBuffer;
import top.egon.cola.component.outbox.transaction.OutboxTransactionGuard;
import top.egon.cola.component.outbox.validation.OutboxMessageValidator;
import top.egon.cola.component.tianshu.admin.service.audit.DdcOperationLogMetrics;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** The source port never exposes a consuming handler; only Admin imports target acceptance. */
public final class DdcOperationLogOutboxAdapter {
    public static final String CHANNEL = "tianshu.operation-log";
    public static final String DESTINATION = "tianshu-audit";
    public static final String SCHEMA_VERSION = "1";
    private static final Set<String> TRUSTED_SOURCES = Set.of("tianshu-admin", "tianshu-config-publisher");
    private static final Map<String, ActionDefinition> ACTIONS = registeredActions();
    private static final Set<String> PUBLISH_RESULTS = Set.of("SUCCESS", "PLATFORM_APPLIED", "FAILED", "TIMEOUT",
            "SUPERSEDED", "PARTIAL_SUCCESS", "UNKNOWN");
    private static final Set<String> DECIMAL_FIELDS = Set.of("revision", "targetRevision", "pageId", "publishedVersion",
            "templateId", "version", "catalogScopeId", "changeId");
    private static final Set<String> TEXT_FIELDS = Set.of("operationId", "eventId", "tenantId", "sourceService", "moduleCode",
            "actionCode", "resourceType", "resourceId", "bizCode", "namespaceCode", "env", "appCode", "actorType", "actorId",
            "actorName", "triggerType", "phase", "outcome", "reasonCode", "httpMethod", "requestUrl", "routePattern",
            "handlerMethod", "traceId", "requestId", "occurredAt");
    private static final Duration COMMIT_RESERVE = Duration.ofMillis(100);
    private DdcOperationLogOutboxAdapter() { }

    /** Immutable action value shared by source projection, target validation and query validation. */
    public record ActionDefinition(String moduleCode, String resourceType, Set<String> detailFields, Set<String> integerFields) {
        public ActionDefinition {
            detailFields = Set.copyOf(detailFields);
            integerFields = Set.copyOf(integerFields);
            if (!detailFields.containsAll(integerFields)) throw invalid();
        }
    }
    public static Map<String, ActionDefinition> actions() { return ACTIONS; }
    public static ActionDefinition requireAction(String actionCode) {
        var definition = actionCode == null ? null : ACTIONS.get(actionCode);
        if (definition == null) throw invalid();
        return definition;
    }
    public static void validateAction(OperationLogEvent event) {
        if (event == null) throw invalid();
        var action = requireAction(event.getActionCode());
        if (!Objects.equals(action.moduleCode(), event.getModuleCode())
                || !Objects.equals(action.resourceType(), event.getResourceType()) || event.getDetails() == null) throw invalid();
        event.getDetails().properties().forEach(field -> {
            JsonNode value = field.getValue();
            if (!action.detailFields().contains(field.getKey()) || value == null || value.isNull()) throw invalid();
            if (action.integerFields().contains(field.getKey())) {
                if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0
                        || ("attempt".equals(field.getKey()) && value.intValue() < 1)) throw invalid();
            } else {
                if (!value.isTextual() || value.textValue().isBlank()) throw invalid();
                String text = value.textValue();
                if (DECIMAL_FIELDS.contains(field.getKey()) && !canonicalDecimal(text,
                        Set.of("revision", "publishedVersion", "targetRevision", "version").contains(field.getKey()))) throw invalid();
                if (Set.of("publishStatus", "publishStage").contains(field.getKey()) && !PUBLISH_RESULTS.contains(text)) throw invalid();
                if ("range".equals(field.getKey()) && !Set.of("PAGE", "FILTERED").contains(text)) throw invalid();
                if ("type".equals(field.getKey()) && !Set.of("PERSONAL", "SYSTEM").contains(text)) throw invalid();
            }
        });
    }
    private static boolean canonicalDecimal(String value, boolean zeroAllowed) {
        if (value == null || !value.matches(zeroAllowed ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}")) return false;
        try { return Long.parseLong(value) >= (zeroAllowed ? 0 : 1); }
        catch (NumberFormatException invalid) { return false; }
    }
    private static Map<String, ActionDefinition> registeredActions() {
        var actions = new LinkedHashMap<String, ActionDefinition>();
        for (String action : List.of("UPSERT", "CREATE", "UPDATE", "DELETE", "ROLLBACK")) {
            actions.put("TIANSHU_CONFIG_" + action, new ActionDefinition("CONFIG", "CONFIG", Set.of("revision"), Set.of()));
        }
        actions.put("TIANSHU_CONFIG_PUBLISH", new ActionDefinition("CONFIG", "CONFIG",
                Set.of("changeId", "targetRevision", "attempt", "publishStatus", "publishStage"), Set.of("attempt")));
        for (String action : List.of("CREATE", "SAVE_DRAFT", "PUBLISH")) {
            actions.put("TIANSHU_I18N_" + action, new ActionDefinition("I18N", "I18N_PAGE",
                    Set.of("pageId", "revision", "publishedVersion"), Set.of()));
        }
        actions.put("TIANSHU_METADATA_SOURCE_IMPORT", new ActionDefinition("I18N", "METADATA_SOURCE",
                Set.of("catalogScopeId", "producerVersion", "sourceVersion", "receiptId"), Set.of()));
        for (String action : List.of("CREATE", "UPDATE", "DELETE")) {
            actions.put("TIANSHU_LIST_TEMPLATE_" + action, new ActionDefinition("LIST", "LIST_TEMPLATE",
                    Set.of("templateId", "type", "version"), Set.of()));
        }
        actions.put("TIANSHU_NAMESPACE_EXPORT_GENERATED", new ActionDefinition("NAMESPACE", "NAMESPACE",
                Set.of("range", "templateId", "rows"), Set.of("rows")));
        return Map.copyOf(actions);
    }

    @Configuration(value = "ddcOperationLogProducerConfiguration", proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = EgonOperationLogProperties.PREFIX, name = "enabled", havingValue = "true")
    @Import(DdcOperationLogMetrics.class)
    public static class ProducerConfiguration {
        /** This registry is deliberately not a candidate for the Dispatcher/Coordinator registry. */
        @Bean(value = "ddcOperationLogProducerRegistry", defaultCandidate = false)
        DeliveryHandlerRegistry producerRegistry() { return new DeliveryHandlerRegistry(List.of(new ProducerRouteHandler())); }

        @Bean("operationLogDestination")
        OperationLogDestination destination(
                @Qualifier("outboxMessageValidator") OutboxMessageValidator validator,
                @Qualifier("outboxMessageSerializer") OutboxMessageSerializer serializer,
                @Qualifier("outboxIdGenerator") OutboxIdGenerator ids,
                @Qualifier("jacksonObjectMapper") ObjectMapper json,
                @Qualifier("outboxTransactionGuard") OutboxTransactionGuard guard,
                @Qualifier("outboxStore") OutboxStore store,
                @Qualifier("ddcOperationLogProducerRegistry") DeliveryHandlerRegistry producerRegistry,
                @Qualifier("outboxAfterCommitBuffer") OutboxAfterCommitBuffer buffer,
                @Qualifier("outboxMetrics") OutboxMetrics metrics,
                @Qualifier("outboxStateMachineOutboxProperties") TransactionalOutboxProperties properties,
                @Qualifier("egonColaValidationUtils") ValidationUtils validation,
                @Qualifier("ddcOperationLogMetrics") DdcOperationLogMetrics auditMetrics) {
            // Keep the dedicated port local: registering another TransactionalOutbox would alter the core producer bean.
            var producer = new DefaultTransactionalOutbox(validator, serializer, ids, json, guard, store,
                    producerRegistry, buffer, metrics, properties);
            return new ProducerStrategy(producer, validation, store, auditMetrics);
        }
    }

    @Configuration(value = "ddcOperationLogAdminConsumptionConfiguration", proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = EgonOperationLogProperties.PREFIX + ".target", name = "enabled", havingValue = "true")
    @Import(DdcOperationLogMetrics.class)
    public static class AdminConsumptionConfiguration {
        @Bean("ddcOperationLogSourceSlots")
        List<Integer> sourceSlots(@Qualifier("dataSource") DataSource source,
                @Qualifier("egon.cola.component.mybatis-plus.sharding-top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties")
                EgonColaShardingProperties sharding,
                @Qualifier("egonColaRoutingProfiles") Map<String, EgonColaRoutingProfileBO> profiles) {
            return List.copyOf(new OutboxShardRouting(source, sharding, profiles).slots());
        }

        @Bean("ddcOperationLogDeliveryHandler")
        DeliveryHandler handler(@Qualifier("jacksonObjectMapper") ObjectMapper json,
                @Qualifier("egonColaValidationUtils") ValidationUtils validation,
                @Qualifier("operationLogRepository") OperationLogRepository target,
                @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants,
                @Qualifier("ddcTenantContextExecutor") DdcTenantContextExecutor contexts,
                @Qualifier("egonColaMybatisPlusClock") Clock clock,
                @Qualifier("operationLogTargetProperties") TargetProperties properties,
                @Qualifier("ddcOperationLogSourceSlots") List<Integer> sourceSlots,
                @Qualifier("outboxStateMachineOutboxProperties") TransactionalOutboxProperties outbox,
                @Qualifier("ddcOperationLogMetrics") DdcOperationLogMetrics auditMetrics) {
            if (properties.getTransactionTimeout() == null || properties.getTransactionTimeout().isNegative()
                    || properties.getTransactionTimeout().isZero() || outbox.getDelivery().getTimeout() == null
                    || properties.getTransactionTimeout().plus(COMMIT_RESERVE).compareTo(outbox.getDelivery().getTimeout()) >= 0) {
                throw new IllegalStateException("TIANSHU_OPERATION_LOG_DELIVERY_BUDGET_INVALID");
            }
            return new OperationLogDeliveryHandler(json, validation, target, tenants, contexts, clock, properties, sourceSlots, auditMetrics);
        }
    }

    @Slf4j
    @Validated
    @RequiredArgsConstructor(onConstructor_ = @Autowired)
    public static class ProducerStrategy implements OperationLogDestination {
        @Qualifier("ddcOperationLogProducerOutbox") private final TransactionalOutbox outbox;
        @Qualifier("egonColaValidationUtils") private final ValidationUtils validation;
        @Qualifier("outboxStore") private final OutboxStore store;
        @Qualifier("ddcOperationLogMetrics") private final DdcOperationLogMetrics auditMetrics;
        @Override public OperationLogWriteModeEnum mode() { return OperationLogWriteModeEnum.OUTBOX; }
        @Override public void append(OperationLogEvent event) {
            validation.validate(event, Default.class, OperationLogEvent.Recorded.class);
            validateAction(event);
            if (!TRUSTED_SOURCES.contains(event.getSourceService())) throw invalid();
            String eventId = Long.toString(event.getEventId());
            String messageId = store.prepareGeneratedMessageId(eventId);
            String key = store.prepareGeneratedMessageId("audit:" + event.getTenantId() + ':' + eventId);
            var receipt = outbox.enqueue(OutboxMessage.builder().messageId(messageId).idempotencyKey(key)
                    .channel(CHANNEL).destination(DESTINATION).schemaVersion(SCHEMA_VERSION).payload(event)
                    .headers(Map.of("eventId", eventId, "tenantId", Long.toString(event.getTenantId()),
                            "sourceService", event.getSourceService())).traceId(event.getTraceId()).build());
            if (receipt != null && receipt.created() && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        auditMetrics.accepted();
                    }
                });
            }
        }
    }

    @Slf4j
    @Validated
    @RequiredArgsConstructor(onConstructor_ = @Autowired)
    public static class OperationLogDeliveryHandler implements DeliveryHandler {
        @Qualifier("jacksonObjectMapper") private final ObjectMapper json;
        @Qualifier("egonColaValidationUtils") private final ValidationUtils validation;
        @Qualifier("operationLogRepository") private final OperationLogRepository target;
        @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;
        @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor contexts;
        @Qualifier("egonColaMybatisPlusClock") private final Clock clock;
        @Qualifier("operationLogTargetProperties") private final TargetProperties properties;
        @Qualifier("ddcOperationLogSourceSlots") private final List<Integer> sourceSlots;
        @Qualifier("ddcOperationLogMetrics") private final DdcOperationLogMetrics auditMetrics;

        @Override public String channel() { return CHANNEL; }
        @Override public void validateDestination(String destination) {
            if (!DESTINATION.equals(destination)) throw new OutboxValidationException("TIANSHU_OPERATION_LOG_DESTINATION_INVALID");
        }
        @Override public DeliveryResult deliver(DeliveryContext context) {
            final OperationLogEvent event;
            final DdcTenantContextDTO trusted;
            try {
                event = parse(context);
                var binding = tenants.requireEnabledTenant(event.getTenantId());
                trusted = new DdcTenantContextDTO(binding.getTenantId(), binding.getIdentityTenantId(),
                        "system:operation-log:" + event.getEventId());
            } catch (IOException | ConstraintViolationException | IllegalArgumentException | OutboxValidationException invalid) {
                return DeliveryResult.permanentFailure("TIANSHU_OPERATION_LOG_EVENT_INVALID", "invalid operation log envelope");
            }
            if (context.deadline() == null || properties.getTransactionTimeout() == null
                    || clock.instant().plus(properties.getTransactionTimeout()).plus(COMMIT_RESERVE).isAfter(context.deadline())
                    || TransactionSynchronizationManager.isActualTransactionActive()) {
                return DeliveryResult.retryableFailure("TIANSHU_OPERATION_LOG_BUDGET_UNAVAILABLE", "target transaction budget unavailable");
            }
            try {
                boolean created = contexts.call(trusted, () -> target.acceptAndConfirm(event));
                if (created) {
                    auditMetrics.committed();
                } else {
                    auditMetrics.duplicateSame();
                }
                return DeliveryResult.success();
            } catch (OperationLogRepository.PermanentRejectionException rejected) {
                if ("OPERATION_LOG_RECEIPT_CONFLICT".equals(rejected.getStatus())) {
                    auditMetrics.duplicateConflict();
                }
                return DeliveryResult.permanentFailure("TIANSHU_OPERATION_LOG_REJECTED", "operation log fact rejected");
            } catch (RuntimeException unavailable) {
                if (missingPartition(unavailable)) {
                    auditMetrics.partitionMissing();
                }
                log.warn("Operation log acceptance requires retry; eventId={}, failureType={}",
                        event.getEventId(), unavailable.getClass().getSimpleName());
                return DeliveryResult.retryableFailure("TIANSHU_OPERATION_LOG_ACCEPT_RETRY", "target acceptance unavailable");
            }
        }
        private static boolean missingPartition(Throwable failure) {
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
            for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
                if (!(cause instanceof java.sql.SQLException sql) || !"23514".equals(sql.getSQLState())) {
                    continue;
                }
                if (sql instanceof PSQLException postgres && postgres.getServerErrorMessage() != null) {
                    if ("ExecFindPartition".equals(postgres.getServerErrorMessage().getRoutine())) {
                        return true;
                    }
                } else if (sql.getMessage() != null && sql.getMessage().contains("no partition of relation")) {
                    return true;
                }
            }
            return false;
        }

        private OperationLogEvent parse(DeliveryContext context) throws IOException {
            if (context == null || !CHANNEL.equals(context.channel()) || !DESTINATION.equals(context.destination())
                    || !"application/json".equals(context.contentType()) || !SCHEMA_VERSION.equals(context.schemaVersion())
                    || context.payload() == null || context.payload().getBytes(StandardCharsets.UTF_8).length > 16384) throw invalid();
            JsonNode tree = json.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(context.payload());
            if (tree == null || !tree.isObject() || !tree.path("eventId").isTextual() || !tree.path("tenantId").isTextual()
                    || !tree.path("schemaVersion").isIntegralNumber() || tree.path("schemaVersion").intValue() != 1
                    || !decimal(tree.path("eventId").textValue()) || !decimal(tree.path("tenantId").textValue())) throw invalid();
            tree.properties().forEach(field -> {
                if (TEXT_FIELDS.contains(field.getKey()) && !field.getValue().isNull() && !field.getValue().isTextual()) throw invalid();
            });
            if (!tree.path("details").isObject() || !tree.path("durationMs").isIntegralNumber()
                    || !tree.path("durationMs").canConvertToLong()) throw invalid();
            var event = json.readerFor(OperationLogEvent.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(tree);
            OperationLogEvent fact = (OperationLogEvent) event;
            validation.validate(fact, Default.class, OperationLogEvent.Recorded.class);
            validateAction(fact);
            if (!TRUSTED_SOURCES.contains(fact.getSourceService())
                    || !matchesMessageId(context.messageId(), Long.toString(fact.getEventId()))
                    || !Objects.equals(context.headers().get("tenantId"), Long.toString(fact.getTenantId()))
                    || !Objects.equals(context.headers().get("eventId"), Long.toString(fact.getEventId()))
                    || !Objects.equals(context.headers().get("sourceService"), fact.getSourceService())) throw invalid();
            return fact;
        }
        private boolean matchesMessageId(String transport, String eventId) {
            Map<Integer, String> slots = sourceSlots.stream().collect(Collectors.toUnmodifiableMap(
                    slot -> slot, slot -> "source-" + slot));
            int slot = OutboxShardIdentity.slotOf(transport, slots);
            return (slots.size() == 1 && eventId.equals(transport))
                    || OutboxShardIdentity.forSlot(slot, eventId).equals(transport);
        }
        private static boolean decimal(String value) {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) return false;
            try { return Long.parseLong(value) > 0; } catch (NumberFormatException invalid) { return false; }
        }
    }
    @Slf4j
    private static class ProducerRouteHandler implements DeliveryHandler {
        @Override public String channel() { return CHANNEL; }
        @Override public void validateDestination(String destination) {
            if (!DESTINATION.equals(destination)) throw new OutboxValidationException("TIANSHU_OPERATION_LOG_DESTINATION_INVALID");
        }
        @Override public DeliveryResult deliver(DeliveryContext context) {
            throw new IllegalStateException("TIANSHU_OPERATION_LOG_PRODUCER_ONLY");
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANSHU_OPERATION_LOG_EVENT_INVALID"); }
}
