package top.egon.cola.component.tianshu.listener;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.redisson.client.handler.State;
import org.redisson.codec.Kryo5Codec;
import top.egon.cola.component.tianshu.format.DdcChecksum;
import top.egon.cola.component.tianshu.model.config.DdcPublishMessage;
import top.egon.cola.component.tianshu.model.config.DdcPublishTarget;
import top.egon.cola.component.tianshu.model.tenant.DdcTenantMessageDTO;
import top.egon.cola.component.tianshu.model.event.DdcConfigDispatchPreparedEvent;
import top.egon.cola.component.tianshu.state.DdcLocalConfigState;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Historical payload compatibility has no configuration callback or lease runtime. */
class DdcTenantCallbackTest {
    @Test
    void redissonDefaultCodecPreservesTypedPayloadInsideTenantEnvelope() throws Exception {
        Kryo5Codec codec = new Kryo5Codec();
        ByteBuf encoded = codec.getValueEncoder().encode(
                new DdcTenantMessageDTO<>(101L, preparedEvent(101L, publishMessage())));
        try {
            Object decoded = codec.getValueDecoder().decode(encoded, new State());

            assertThat(decoded).isInstanceOf(DdcTenantMessageDTO.class);
            DdcTenantMessageDTO<?> envelope = (DdcTenantMessageDTO<?>) decoded;
            assertThat(envelope.getTenantId()).isEqualTo(101L);
            assertThat(envelope.getPayload()).isInstanceOf(DdcConfigDispatchPreparedEvent.class);
            DdcConfigDispatchPreparedEvent event = (DdcConfigDispatchPreparedEvent) envelope.getPayload();
            assertThat(event.getEventId()).hasSize(64);
            assertThat(event.getTenantId()).isEqualTo(101L);
            assertThat(event.getMessage().getContent())
                    .isEqualTo("feature: true\n");
        } finally {
            encoded.release();
        }
    }

    @Test
    void publishMessageKeepsDefensiveTargetsAndDoesNotExposeContentInToString() {
        DdcPublishMessage message = publishMessage();
        List<DdcPublishTarget> targets = new java.util.ArrayList<>(message.getTargets());

        message.setTargets(targets);
        targets.clear();

        assertThat(message.getTargets()).containsExactly(new DdcPublishTarget("instance-1", "lease-1"));
        assertThatThrownBy(() -> message.getTargets().add(new DdcPublishTarget("instance-2", "lease-2")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(message.toString()).doesNotContain(message.getContent());
    }

    @Test
    void tenantSeparatesLocalConfigurationState() {
        var first = new DdcLocalConfigState(101L);
        var second = new DdcLocalConfigState(202L);
        first.updateVersion("application.yml", 7L);
        second.updateVersion("application.yml", 2L);
        assertThat(first.version("application.yml")).isEqualTo(7L);
        assertThat(second.version("application.yml")).isEqualTo(2L);
    }

    private DdcPublishMessage publishMessage() {
        DdcPublishMessage message = new DdcPublishMessage();
        message.setChangeId("change-1");
        message.setBizCode("retail");
        message.setEnv("dev");
        message.setAppCode("order");
        message.setResourceName("application.yml");
        message.setFormat("yaml");
        message.setContent("feature: true\n");
        message.setTargetVersion(2L);
        message.setPublishMode("SYNC_ALL_ACK");
        message.setOperator("user:test");
        message.setTimestamp(1790000000000L);
        message.setResourceChecksum(DdcChecksum.resource(
                message.getResourceName(), message.getFormat(), message.getContent()));
        message.setTargets(List.of(new DdcPublishTarget("instance-1", "lease-1")));
        message.setChecksum(DdcChecksum.sha256(message));
        return message;
    }

    private DdcConfigDispatchPreparedEvent preparedEvent(Long tenantId, DdcPublishMessage message) {
        DdcConfigDispatchPreparedEvent event = new DdcConfigDispatchPreparedEvent();
        event.setEventId(DdcConfigDispatchPreparedEvent.eventId(tenantId, message.getChangeId(), 0));
        event.setTenantId(tenantId);
        event.setSchemaVersion("1");
        event.setAttempt(0);
        event.setConfigId("810001");
        event.setExpectedPublishedVersion(1L);
        event.setMessage(message);
        event.setEventChecksum(event.calculateEventChecksum());
        return event;
    }

}
