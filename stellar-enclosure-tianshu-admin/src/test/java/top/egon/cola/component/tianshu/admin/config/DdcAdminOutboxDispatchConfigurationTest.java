package top.egon.cola.component.tianshu.admin.config;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.outbox.autoconfigure.OutboxMpStorageProperties;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.deadletter.OutboxDeadLetterNotifier;
import top.egon.cola.component.outbox.delivery.DeliveryFailureClassifier;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.component.outbox.dispatch.OutboxDispatcher;
import top.egon.cola.component.outbox.dispatch.OutboxWorkerIdentity;
import top.egon.cola.component.outbox.observability.OutboxMetrics;
import top.egon.cola.component.outbox.retry.OutboxRetryPolicy;
import top.egon.cola.component.outbox.statemachine.OutboxLifecycleService;
import top.egon.cola.component.outbox.store.OutboxStore;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcAdminOutboxDispatchConfigurationTest {
    private final DdcConfigRolloutOutboxRouteDTO route = new DdcConfigRolloutOutboxRouteDTO("tianshu.config-rollout", "1");
    private final OutboxStore store = mock(OutboxStore.class);
    private final TransactionalOutboxProperties flags = new TransactionalOutboxProperties();
    private final DeliveryHandler business = handler("tianshu.ai-publication");
    private final DeliveryHandler publisher = handler(route.channel());
    private final DeliveryHandlerRegistry registered = new DeliveryHandlerRegistry(List.of(business, publisher));

    @Test
    void producerCanValidateNativeWakeupWhileAdminPollsOnlyBusinessChannels() {
        assertThat(registered.required(route.channel())).isSameAs(publisher);
        when(store.claimDue(any(), anyInt(), any(), any())).thenReturn(List.of());
        dispatcher().submitDue();
        verify(store).claimDue(eq(Set.of("tianshu.ai-publication")), anyInt(), any(), any());
        assertThat(registered.channels()).containsExactlyInAnyOrder("tianshu.ai-publication", route.channel());
    }

    @Test
    void rejectsAnUnscopedRecoveryThatWouldIgnoreRoleChannels() {
        flags.getRetry().setRecoverExhausted(true);
        assertThatThrownBy(this::dispatcher).hasMessage("TIANSHU_ADMIN_UNSCOPED_OUTBOX_RECOVERY_FORBIDDEN");
        verifyNoInteractions(store);
    }

    @Test
    void aManagedAdminCannotClaimAnyChannelAfterLosingTheIdLease() {
        var identity = new OutboxWorkerIdentity("admin-test", () -> {
            throw new IllegalStateException("lease unavailable");
        });
        var dispatcher = dispatcher(identity);
        dispatcher.submitDue();
        dispatcher.submitMessageIds(List.of("pending-outbox-message"));
        verifyNoInteractions(store);
    }

    private OutboxDispatcher dispatcher() {
        return dispatcher(new OutboxWorkerIdentity("admin-test"));
    }

    private OutboxDispatcher dispatcher(OutboxWorkerIdentity identity) {
        return new DdcAdminOutboxDispatchConfiguration().outboxDispatcher(
                store, registered, route, mock(DeliveryFailureClassifier.class), mock(OutboxRetryPolicy.class),
                mock(OutboxDeadLetterNotifier.class), mock(OutboxMetrics.class), identity, Runnable::run,
                flags, Clock.systemUTC(), mock(OutboxLifecycleService.class), new OutboxMpStorageProperties());
    }

    private static DeliveryHandler handler(String channel) {
        var handler = mock(DeliveryHandler.class);
        when(handler.channel()).thenReturn(channel);
        return handler;
    }
}
