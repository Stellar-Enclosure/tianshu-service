package top.egon.cola.component.tianshu.admin.service.publish;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishAckRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DdcPublishAckSummaryConcurrencyTest {
    @Test
    void successSummaryCannotBypassThePublishedPointerCheck() {
        assertThatThrownBy(() -> new top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckSummaryBO(
                1, 1, 0, 0, 0, "SUCCESS", null, null, "ANY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentAckSummariesFromTheSameTaskVersionHaveOneWinner() throws Exception {
        var tasks = mock(DdcPublishTaskRepository.class);
        var acks = mock(DdcPublishAckRepository.class);
        var barrier = new CyclicBarrier(2);
        var won = new AtomicBoolean();
        when(acks.findByChangeId("change")).thenReturn(List.of(ack(1L, null), ack(2L, null)));
        when(tasks.updateAckSummary(any(), any(), any())).thenAnswer(call -> {
            barrier.await(5, TimeUnit.SECONDS);
            return won.compareAndSet(false, true) ? 1 : 0;
        });
        var service = new DdcPublishStateTransitionService(tasks, mock(DdcConfigItemRepository.class), acks,
                new PublishResourceLockRegistry(), new PublishCompletionWaiterRegistry(),
                Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC));

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> update(service, ack(1L, "SUCCESS")));
            var second = workers.submit(() -> update(service, ack(2L, "SUCCESS")));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        verify(tasks, times(2)).updateAckSummary(any(), any(), any());
        verify(tasks, never()).findForUpdateByChangeId(anyString());
    }

    private static boolean update(DdcPublishStateTransitionService service, DdcPublishAckBO ack) {
        var task = DdcPublishTaskBO.builder().id(11L).version(4L).changeId("change")
                .status("PUBLISHING").targetCount(2).build();
        org.slf4j.MDC.put("tenantId", "101");
        try {
            service.refreshAfterAck(task, ack);
            return true;
        } catch (OptimisticLockingFailureException conflict) {
            return false;
        } finally {
            org.slf4j.MDC.remove("tenantId");
        }
    }

    private static DdcPublishAckBO ack(long id, String status) {
        return DdcPublishAckBO.builder().id(id).ackStatus(status).build();
    }
}
