package top.egon.cola.component.tianshu.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import top.egon.cola.component.outbox.autoconfigure.OutboxMpStorageProperties;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.deadletter.OutboxDeadLetterNotifier;
import top.egon.cola.component.outbox.delivery.DeliveryFailureClassifier;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.component.outbox.dispatch.OutboxDispatcher;
import top.egon.cola.component.outbox.dispatch.OutboxWorkerIdentity;
import top.egon.cola.component.outbox.observability.OutboxMetrics;
import top.egon.cola.component.outbox.retry.OutboxRetryPolicy;
import top.egon.cola.component.outbox.statemachine.OutboxLifecycleService;
import top.egon.cola.component.outbox.store.OutboxStore;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;

import java.time.Clock;

/** Producers validate against the complete registry. Only the independent Publisher may claim configuration wakeups. */
@Slf4j
@Configuration(value = "ddcAdminOutboxDispatchConfiguration", proxyBeanMethods = false)
public class DdcAdminOutboxDispatchConfiguration {
    @Bean("outboxDispatcher")
    OutboxDispatcher outboxDispatcher(
            @Qualifier("outboxStore") OutboxStore store,
            @Qualifier("deliveryHandlerRegistry") DeliveryHandlerRegistry producers,
            @Qualifier("ddcConfigRolloutOutboxRoute") DdcConfigRolloutOutboxRouteDTO configRoute,
            @Qualifier("deliveryFailureClassifier") DeliveryFailureClassifier failureClassifier,
            @Qualifier("outboxRetryPolicy") OutboxRetryPolicy retryPolicy,
            @Qualifier("outboxDeadLetterNotifier") OutboxDeadLetterNotifier deadLetterNotifier,
            @Qualifier("outboxMetrics") OutboxMetrics metrics,
            @Qualifier("outboxWorkerIdentity") OutboxWorkerIdentity workerIdentity,
            @Qualifier("outboxDeliveryExecutor") TaskExecutor taskExecutor,
            @Qualifier("outboxStateMachineOutboxProperties") TransactionalOutboxProperties properties,
            @Qualifier("outboxStateMachineClock") Clock clock,
            @Qualifier("outboxLifecycleService") OutboxLifecycleService lifecycleService,
            @Qualifier("outboxMpStorageProperties") OutboxMpStorageProperties storageProperties) {
        if (properties.getRetry().isRecoverExhausted()) {
            throw new IllegalStateException("TIANSHU_ADMIN_UNSCOPED_OUTBOX_RECOVERY_FORBIDDEN");
        }
        var dispatchers = adminDeliveryRegistry(producers, configRoute.channel());
        log.info("Tianshu Admin Outbox claims are restricted to {} registered channels", dispatchers.channels().size());
        return new OutboxDispatcher(store, dispatchers, failureClassifier, retryPolicy, deadLetterNotifier,
                metrics, workerIdentity, taskExecutor, properties, clock, lifecycleService, storageProperties);
    }

    static DeliveryHandlerRegistry adminDeliveryRegistry(DeliveryHandlerRegistry registered, String nativeConfigChannel) {
        return new DeliveryHandlerRegistry(registered.channels().stream()
                .filter(channel -> !nativeConfigChannel.equals(channel)).map(registered::required).toList());
    }
}
