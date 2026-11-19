package top.egon.cola.component.tianshu.admin.service.publish;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigDispatchOutboxRouteDTO;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigDispatchEventConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishAckRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcRedisRepository;
import top.egon.cola.component.tianshu.model.event.DdcConfigDispatchPreparedEvent;

import java.time.Clock;

/** Enqueues the fixed native wake-up inside the caller's local publication transaction. */
@Slf4j
@Service("ddcPendingPublishDispatcher")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Validated
public class DdcPendingPublishDispatcher {
    @Qualifier("ddcConfigDispatchEventConverterImpl") private final DdcConfigDispatchEventConverter events;
    @Qualifier("transactionalOutbox") private final TransactionalOutbox outbox;
    @Qualifier("ddcConfigRolloutOutboxRoute") private final DdcConfigRolloutOutboxRouteDTO route;
    @Qualifier("egonColaValidationUtils") private final ValidationUtils validation;

    /** Historical construction is source-compatible; it cannot restore Redis delivery. */
    public DdcPendingPublishDispatcher(DdcConfigItemRepository items, DdcConfigVersionRepository versions,
            DdcPublishTaskRepository tasks, DdcPublishAckRepository acks, DdcRedisRepository redis,
            DdcPublishStateTransitionService transitions, DdcConfigDispatchEventConverter events,
            TransactionalOutbox outbox, DdcConfigDispatchOutboxRouteDTO oldRoute,
            TransactionTemplate transactions, Clock clock, ValidationUtils validation, DdcAdminProperties properties) {
        this(events, outbox, new DdcConfigRolloutOutboxRouteDTO("tianshu.config-rollout", "1"), validation);
    }

    public DdcPublishTaskBO enqueueNative(@Valid @NotNull DdcPublishTaskBO task) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !isNative(task)
                || task.getTenantId() == null || task.getTenantId() <= 0 || task.getConfigItemId() == null
                || task.getConfigItemId() <= 0 || task.getTargetVersion() == null || task.getTargetVersion() <= 0
                || task.getAttemptCount() == null || task.getAttemptCount() < 1) {
            throw new DdcAdminException("TIANSHU_NATIVE_PUBLICATION_TRANSACTION_REQUIRED");
        }
        var event = events.toNative(task);
        validation.validate(event);
        if (!event.isValid()) { throw new DdcAdminException("TIANSHU_CONFIG_ROLLOUT_EVENT_INVALID"); }
        outbox.enqueue(OutboxMessage.builder().messageId(event.getEventId()).idempotencyKey(event.getEventId())
                .channel(route.channel()).destination(route.destination(event)).payload(event)
                .contentType("application/json").schemaVersion(route.schemaVersion())
                .header("tenantId", event.getTenantId()).header("eventType", route.eventType()).build());
        return task;
    }

    public static boolean isNative(DdcPublishTaskBO task) {
        return task != null && task.getPlatformEvidence() != null && task.getPlatformEvidence().isObject()
                && task.getPlatformEvidence().path("schemaVersion").isIntegralNumber()
                && task.getPlatformEvidence().path("schemaVersion").intValue() == 1
                && task.getPlatformEvidence().path("targets").isArray();
    }

    public DdcPublishTaskBO dispatch(@NotBlank String changeId) { throw retired(); }
    DdcPublishTaskBO dispatch(DdcPublishTaskBO task) { throw retired(); }
    public boolean deliverPrepared(@Valid DdcConfigDispatchPreparedEvent event) { return false; }
    public void failPrepared(@Valid DdcConfigDispatchPreparedEvent event, @NotBlank String stage,
            @NotBlank String message) { throw retired(); }
    private static DdcAdminException retired() { return new DdcAdminException("TIANSHU_LEGACY_CONFIG_DISPATCH_RETIRED"); }
}
