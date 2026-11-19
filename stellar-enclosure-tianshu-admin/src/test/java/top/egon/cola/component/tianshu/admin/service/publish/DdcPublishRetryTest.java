package top.egon.cola.component.tianshu.admin.service.publish;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutRequestedEvent;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcPublishRetryTest {
    @Test void nativeRetryQueuesTheSameImmutableVersionWithTheNextAttemptOnly() {
        try(var ignored=new DdcTenantContextExecutor().bind(new DdcTenantContextDTO(101L,"registered","user:test"))) {
            var fixture=new DdcPublishPreparationTest.Fixture();
            fixture.service.publish(DdcPublishPreparationTest.request("123",1),"test");
            var task=fixture.taskRows.get("123");
            task.setStatus("FAILED");
            when(fixture.tasks.resetNativeForRetry(eq(task),any(),any())).thenReturn(1);
            var retried=fixture.service.retry("123","test");
            assertThat(retried.getTargetVersion()).isEqualTo(2L);
            assertThat(retried.getAttemptCount()).isEqualTo(2);
            assertThat(fixture.versionRows).hasSize(1);
            var first=(DdcConfigRolloutRequestedEvent)fixture.messages.getFirst().payload();
            var next=(DdcConfigRolloutRequestedEvent)fixture.messages.getLast().payload();
            assertThat(next.getEventId()).isNotEqualTo(first.getEventId());
            assertThat(next.getConfigId()).isEqualTo(first.getConfigId());
            assertThat(next.getRevision()).isEqualTo(first.getRevision());
            verify(fixture.tasks).resetNativeForRetry(eq(task), eq("test"), any());
        }
    }
    @Test void anOlderDesiredRevisionCannotBeRetriedAfterANewerPublication() {
        try(var ignored=new DdcTenantContextExecutor().bind(new DdcTenantContextDTO(101L,"registered","user:test"))) {
            var fixture=new DdcPublishPreparationTest.Fixture();
            fixture.service.publish(DdcPublishPreparationTest.request("123",1),"test");
            fixture.taskRows.get("123").setStatus("FAILED");
            fixture.service.publish(DdcPublishPreparationTest.request("124",2),"test");
            assertThatThrownBy(() -> fixture.service.retry("123","test")).hasMessageContaining("not current and retryable");
            assertThat(fixture.messages).hasSize(2);
            verify(fixture.tasks,never()).resetNativeForRetry(any(),any(),any());
        }
    }
}
