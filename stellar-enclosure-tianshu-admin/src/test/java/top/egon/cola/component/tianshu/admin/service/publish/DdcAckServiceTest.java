package top.egon.cola.component.tianshu.admin.service.publish;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishResultVO;
import top.egon.cola.component.tianshu.model.config.DdcAckRequest;
import top.egon.cola.component.tianshu.model.event.DdcConfigDispatchPreparedEvent;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DdcAckServiceTest {
    @Test void historicalTaskCanStillBeReadButLegacyAckCannotChangeAnyOutcome() {
        var archived=DdcPublishTaskBO.builder().changeId("123").status("SUCCESS").targetVersion(7L).build();
        assertThat(DdcPublishResultVO.from(archived).getStatus()).isEqualTo("SUCCESS");
        assertThat(DdcPendingPublishDispatcher.isNative(archived)).isFalse();
        var fixture=new DdcPublishPreparationTest.Fixture();
        assertThatThrownBy(() -> fixture.service.ack(new DdcAckRequest()))
                .hasMessage("TIANSHU_LEGACY_CONFIG_ACK_RETIRED");
        verifyNoInteractions(fixture.tasks,fixture.versions,fixture.outbox);
    }
    @Test void historicalDispatchEnvelopeCannotRunRedisOrAdvanceAPublishedPointer() {
        var fixture=new DdcPublishPreparationTest.Fixture();
        assertThat(fixture.dispatcher.deliverPrepared(new DdcConfigDispatchPreparedEvent())).isFalse();
        assertThatThrownBy(() -> fixture.dispatcher.dispatch("123"))
                .hasMessage("TIANSHU_LEGACY_CONFIG_DISPATCH_RETIRED");
        assertThat(fixture.item.getPublishedVersion()).isEqualTo(1L);
        verifyNoInteractions(fixture.outbox,fixture.items,fixture.tasks);
    }
}
