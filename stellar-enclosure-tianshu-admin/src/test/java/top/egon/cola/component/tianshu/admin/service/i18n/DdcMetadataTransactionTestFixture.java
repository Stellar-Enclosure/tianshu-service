package top.egon.cola.component.tianshu.admin.service.i18n;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Exercises Spring transaction boundaries; it is not a PostgreSQL persistence double. */
final class DdcMetadataTransactionTestFixture extends AbstractPlatformTransactionManager {
    int commits;
    int rollbacks;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
    @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
    @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
}
