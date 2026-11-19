package top.egon.cola.component.tianshu.admin.service.publish;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcPublishServiceFailureTest {
    @Test void committedOutcomeIsRecoveredAfterCommitAcknowledgementLoss() {
        try (var ignored = new DdcTenantContextExecutor().bind(new DdcTenantContextDTO(101L,"registered","user:test"))) {
            var fixture = new DdcPublishPreparationTest.Fixture();
            var selected = fixture.transactions.getTransactionManager();
            var ambiguous = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return selected.getTransaction(definition); }
                @Override public void commit(TransactionStatus status) {
                    selected.commit(status);
                    throw new org.springframework.transaction.TransactionSystemException("synthetic lost commit acknowledgement");
                }
                @Override public void rollback(TransactionStatus status) { selected.rollback(status); }
            };
            var service = fixture.createService(new TransactionTemplate(ambiguous));
            var result = service.publish(DdcPublishPreparationTest.request("123",1),"test");
            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(fixture.item.getPublishedVersion()).isEqualTo(2L);
            assertThat(fixture.taskRows.get("123").getStatus()).isEqualTo("PENDING");
            assertThat(fixture.messages).hasSize(1);
        }
    }
    @Test void failedPrepareDoesNotInsertAFakeTaskOrOverwriteExistingOutcomes() {
        try (var ignored = new DdcTenantContextExecutor().bind(new DdcTenantContextDTO(101L,"registered","user:test"))) {
            var fixture = new DdcPublishPreparationTest.Fixture();
            when(fixture.items.findForPublishByBizCodeAndEnvAndAppCodeAndResourceName(any(),any(),any(),any()))
                    .thenThrow(new IllegalStateException("synthetic database failure"));
            assertThatThrownBy(() -> fixture.service.publish(DdcPublishPreparationTest.request("123",1),"test"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("synthetic database failure");
            verify(fixture.tasks,never()).insert(any());
            verify(fixture.outbox,never()).enqueue(any());
        }
    }
}
