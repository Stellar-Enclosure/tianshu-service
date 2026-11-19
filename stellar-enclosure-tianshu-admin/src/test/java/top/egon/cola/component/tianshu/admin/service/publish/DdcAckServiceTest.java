package top.egon.cola.component.tianshu.admin.service.publish;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.dao.DdcPublishAckMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcPublishAckPO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishResultVO;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishAckRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcPublishAckRepositoryImpl;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcAckServiceTest {

    @Test
    void archivedAcknowledgementRepositoryHasNoProductionMutationContract() {
        assertThat(Arrays.stream(DdcPublishAckRepository.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("findByChangeId");
        assertThat(Arrays.stream(DdcPublishTaskRepository.class.getMethods()).map(Method::getName))
                .doesNotContain("fenceObservedVersion", "fenceDispatchTargets", "updateAckSummary", "claimStaleForRecovery",
                        "transitionToPublishing", "updateCounters", "transitionToTerminal", "resetForRetry", "markRetryLeaseExpired", "update");
    }

    @Test
    void archivedAcknowledgementAdapterDoesNotExposeInheritedGenericWriters() {
        assertThat(Arrays.stream(DdcPublishAckRepositoryImpl.class.getMethods()).map(Method::getName))
                .doesNotContain("save", "saveBatch", "updateById", "insert", "insertAll", "update",
                        "fenceObservedVersion", "markIncompleteTimeout", "resetTarget", "resetTargets");
    }

    @Test
    void historicalTaskRemainsReadableAndNoRetiredTransportEntryCanBeCalled() {
        var archived = DdcPublishTaskBO.builder().changeId("123").status("SUCCESS").targetVersion(7L).build();
        assertThat(DdcPublishResultVO.from(archived).getStatus()).isEqualTo("SUCCESS");
        assertThat(DdcPendingPublishDispatcher.isNative(archived)).isFalse();
        assertThat(Arrays.stream(DdcPublishService.class.getMethods()).map(Method::getName)).doesNotContain("ack");
        assertThat(Arrays.stream(DdcPendingPublishDispatcher.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("dispatch", "deliverPrepared", "failPrepared");
    }

    @Test
    void inheritedMapperWritesIncludingBatchesFailBeforeAnySqlExecution() {
        var mapper = (DdcPublishAckMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{DdcPublishAckMapper.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new AssertionError("Retired ACK mapper must never execute SQL");
                });
        var row = new DdcPublishAckPO();
        List<Runnable> writes = List.of(
                () -> mapper.insert(row),
                () -> mapper.updateById(row),
                () -> mapper.update(row, null),
                () -> mapper.deleteById(row),
                () -> mapper.deleteById((Serializable) 9L),
                () -> mapper.deleteById((Object) row, true),
                () -> mapper.delete(null),
                () -> mapper.deleteByIds(List.of(9L)),
                () -> mapper.deleteByIds(List.of(9L), true),
                () -> mapper.deleteBatchIds(List.of(9L)),
                () -> mapper.insert(List.of(row), 1),
                () -> mapper.updateById(List.of(row), 1),
                () -> mapper.insertOrUpdate(List.of(row), null, 1),
                () -> mapper.deleteVersionedById(row),
                () -> mapper.restoreVersionedById(row));
        writes.forEach(write -> assertThatThrownBy(write::run).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("TIANSHU_LEGACY_ACK_READ_ONLY"));
    }
}
