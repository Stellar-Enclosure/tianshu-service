package top.egon.cola.component.tianshu.admin.service.publish;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutRequestedEvent;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcPublishDispatchConsistencyTest {
    @Test void eventCannotBeEnqueuedOutsideTheOwningPublicationTransaction() {
        try(var ignored=new DdcTenantContextExecutor().bind(new DdcTenantContextDTO(101L,"registered","user:test"))) {
            var fixture=new DdcPublishPreparationTest.Fixture();
            fixture.service.publish(DdcPublishPreparationTest.request("123",1),"test");
            int before=fixture.messages.size();
            assertThatThrownBy(() -> fixture.dispatcher.enqueueNative(fixture.taskRows.get("123")))
                    .hasMessage("TIANSHU_NATIVE_PUBLICATION_TRANSACTION_REQUIRED");
            assertThat(fixture.messages).hasSize(before);
        }
    }
    @Test void nativeEventIdentityIsStableForOneAttemptAndSeparatesTenantVersionAndRetry() {
        String first=DdcConfigRolloutRequestedEvent.eventId("101","441","42","123",1);
        assertThat(first).isEqualTo(DdcConfigRolloutRequestedEvent.eventId("101","441","42","123",1));
        assertThat(first).isNotEqualTo(DdcConfigRolloutRequestedEvent.eventId("102","441","42","123",1));
        assertThat(first).isNotEqualTo(DdcConfigRolloutRequestedEvent.eventId("101","441","43","123",1));
        assertThat(first).isNotEqualTo(DdcConfigRolloutRequestedEvent.eventId("101","441","42","123",2));
    }
}
