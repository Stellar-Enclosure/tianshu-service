package top.egon.cola.component.tianshu.admin.service.publish;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublishFailureRecorderTest {
    @Test void recoveryUsesAFreshTransactionAndReadsTheActualCommittedTaskWithoutMutation() throws Exception {
        var repository=mock(DdcPublishTaskRepository.class);
        var committed=DdcPublishTaskBO.builder().changeId("123").status("SUCCESS").build();
        when(repository.findByChangeId("123")).thenReturn(Optional.of(committed));
        assertThat(new PublishFailureRecorder(repository).findCommitted("123").orElseThrow()).isSameAs(committed);
        var contract=PublishFailureRecorder.class.getMethod("findCommitted",String.class).getAnnotation(Transactional.class);
        assertThat(contract.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        verify(repository).findByChangeId("123");
        verifyNoMoreInteractions(repository);
    }
    @Test void archivedFailureHookCannotCreateOrRewriteATask() {
        var repository=mock(DdcPublishTaskRepository.class);
        var recorder=new PublishFailureRecorder(repository);
        assertThatThrownBy(() -> recorder.recordFailure("123","biz","test","app","application.yml","hidden"))
                .hasMessage("TIANSHU_LEGACY_PREPARATION_FAILURE_WRITE_RETIRED");
        verifyNoInteractions(repository);
    }
}
