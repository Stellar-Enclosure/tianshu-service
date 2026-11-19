package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.OutboxReceipt;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutRequestedEvent;
import top.egon.cola.component.tianshu.admin.model.bo.*;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishRequest;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigDispatchEventConverterImpl;
import top.egon.cola.component.tianshu.admin.repository.*;
import top.egon.cola.component.tianshu.admin.security.tenancy.*;
import top.egon.cola.component.tianshu.admin.service.config.DdcYamlConfigValidator;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DdcPublishPreparationTest {
    private Fixture fixture;
    private DdcTenantContextExecutor.Scope scope;
    @BeforeEach void setUp() {
        scope = new DdcTenantContextExecutor().bind(new DdcTenantContextDTO(101L, "registered", "user:test"));
        fixture = new Fixture();
    }
    @AfterEach void tearDown() { scope.close(); }

    @Test void nativePublicationIsAcceptedWhenNoRedisLeaseOrCurrentReplicaExists() {
        var result = fixture.service.publish(request("123", 1), "test");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getTargetCount()).isZero();
        assertThat(fixture.item.getCurrentVersion()).isEqualTo(2L);
        assertThat(fixture.item.getPublishedVersion()).isEqualTo(2L);
        var task = fixture.taskRows.get("123");
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getPlatformEvidence().path("schemaVersion").intValue()).isEqualTo(1);
        assertThat(fixture.manifests.materialize(fixture.versionRows.get(2L), Fixture.SCOPE).baseline().getSource().getRevision()).isEqualTo("2");
        assertThat(fixture.messages).hasSize(1);
        assertThat(fixture.messages.getFirst().channel()).isEqualTo("tianshu.config-rollout");
        assertThat(fixture.messages.getFirst().payload()).isInstanceOf(DdcConfigRolloutRequestedEvent.class);
        verify(fixture.audit).insert(any());
    }

    @Test void outboxFailureRollsBackVersionTaskPointerAndAuditIntentTogether() {
        doThrow(new IllegalStateException("synthetic outbox failure")).when(fixture.outbox).enqueue(any());
        assertThatThrownBy(() -> fixture.service.publish(request("123", 1), "test"))
                .isInstanceOf(IllegalStateException.class).hasMessage("synthetic outbox failure");
        assertThat(fixture.item.getCurrentVersion()).isEqualTo(1L);
        assertThat(fixture.item.getPublishedVersion()).isEqualTo(1L);
        assertThat(fixture.versionRows).isEmpty();
        assertThat(fixture.taskRows).isEmpty();
        assertThat(fixture.messages).isEmpty();
    }

    @Test void repeatedChangeIdReturnsTheExactCommittedPublicationWithoutAnotherRevisionOrEvent() {
        fixture.service.publish(request("123", 1), "test");
        var duplicate = fixture.service.publish(request("123", 1), "test");
        assertThat(duplicate.getTargetVersion()).isEqualTo(2L);
        assertThat(fixture.versionRows).hasSize(1);
        assertThat(fixture.messages).hasSize(1);
        assertThatThrownBy(() -> fixture.service.publish(request("123", 2), "test"))
                .hasMessageContaining("different publication");
    }

    @Test void aNewDesiredPublicationSupersedesWithoutWaitingForPreviousReplicaReceipts() {
        fixture.service.publish(request("123", 1), "test");
        fixture.service.publish(request("124", 2), "test");
        assertThat(fixture.item.getPublishedVersion()).isEqualTo(3L);
        assertThat(fixture.taskRows).hasSize(2);
        assertThat(fixture.messages).hasSize(2);
        assertThat(fixture.versionRows).containsKeys(2L, 3L);
    }

    @Test void pointerCasOrRuleValidationFailureCannotQueueAnIntent() {
        when(fixture.items.advancePublishedVersion(any(), any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> fixture.service.publish(request("123", 1), "test"))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(fixture.taskRows).isEmpty();
        assertThat(fixture.messages).isEmpty();
        fixture.item.setTenantId(102L);
        assertThatThrownBy(() -> fixture.service.publish(request("124", 1), "test"))
                .hasMessage("config item not found");
    }

    @Test void aStagedDeleteOnlyPublishesADisabledNewRevisionRetainingRestartBody() {
        fixture.item.setDeleted(true).setEnabled(false);
        var request = request("123", 1);
        request.setContent(fixture.item.getContent());
        fixture.service.publish(request, "test");
        var fixed = fixture.manifests.materialize(fixture.versionRows.get(2L), Fixture.SCOPE).baseline();
        assertThat(fixed.getEnabled()).isFalse();
        assertThat(fixed.getHotSeed()).isEmpty();
        assertThat(fixed.getRestartValues()).containsKey("feature.value");
        assertThat(fixture.item.getDeleted()).isTrue();
        assertThat(fixture.item.getPublishedVersion()).isEqualTo(2L);
    }

    public static DdcPublishRequest request(String changeId, long expected) {
        var request = new DdcPublishRequest();
        request.setChangeId(changeId); request.setBizCode("reader"); request.setEnv("test"); request.setAppCode("app");
        request.setResourceName("application.yml"); request.setFormat("YAML"); request.setContent("feature: {value: true}\n");
        request.setExpectedVersion(expected);
        return request;
    }

    public static final class Fixture {
        static final DdcConfigScopeVO SCOPE = new DdcConfigScopeVO("101", "reader", "test", "app", "application.yml");
        public final DdcConfigItemRepository items = mock(DdcConfigItemRepository.class);
        public final DdcConfigVersionRepository versions = mock(DdcConfigVersionRepository.class);
        final DdcPublishTaskRepository tasks = mock(DdcPublishTaskRepository.class);
        final DdcOperationLogRepository audit = mock(DdcOperationLogRepository.class);
        final TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        final DdcTenantRegistry tenants = mock(DdcTenantRegistry.class);
        public final DdcConfigManifestFactory manifests = new DdcConfigManifestFactory(new ObjectMapper());
        final Map<Long,DdcConfigVersionBO> versionRows = new LinkedHashMap<>();
        final Map<String,DdcPublishTaskBO> taskRows = new LinkedHashMap<>();
        final List<OutboxMessage> messages = new ArrayList<>();
        public final DdcConfigItemBO item = DdcConfigItemBO.builder().id(441L).tenantId(101L).publicId("config-1")
                .bizCode("reader").env("test").appCode("app").resourceName("application.yml").format("YAML")
                .content("feature: {value: false}\n").enabled(true).deleted(false).currentVersion(1L).publishedVersion(1L).version(0L).build();
        public final DdcPublishService service;
        final TransactionTemplate transactions;
        final DdcPendingPublishDispatcher dispatcher;
        final DdcAdminProperties properties = new DdcAdminProperties();
        final DdcConfigManifestFactory.PublicationPolicyResolver policies;
        public Fixture() {
            when(tenants.requireEnabledTenant(101L)).thenReturn(new DdcTenantBindingDTO(101L,"registered",true));
            when(items.findForPublishByBizCodeAndEnvAndAppCodeAndResourceName(any(),any(),any(),any())).thenReturn(Optional.of(item));
            when(items.update(any())).thenAnswer(call -> call.getArgument(0));
            when(items.advancePublishedVersion(any(),any(),any(),any())).thenAnswer(call -> {
                if (!Objects.equals(item.getPublishedVersion(),call.getArgument(1))) return 0;
                item.setPublishedVersion(call.getArgument(2)); return 1;
            });
            when(versions.insert(any())).thenAnswer(call -> { DdcConfigVersionBO row=call.getArgument(0); versionRows.put(row.getConfigRevision(),row); return row; });
            when(versions.findByConfigItemIdAndRevision(any(),any())).thenAnswer(call -> Optional.ofNullable(versionRows.get(call.getArgument(1))));
            when(tasks.findByChangeId(any())).thenAnswer(call -> Optional.ofNullable(taskRows.get(call.getArgument(0))));
            when(tasks.findForUpdateByChangeId(any())).thenAnswer(call -> Optional.ofNullable(taskRows.get(call.getArgument(0))));
            when(tasks.insert(any())).thenAnswer(call -> { DdcPublishTaskBO row=call.getArgument(0); row.setId(500L+taskRows.size()).setVersion(0L); taskRows.put(row.getChangeId(),row); return row; });
            when(outbox.enqueue(any())).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                OutboxMessage message=call.getArgument(0); messages.add(message);
                return new OutboxReceipt(message.messageId(),message.idempotencyKey(),true);
            });
            var manager = new AbstractPlatformTransactionManager() {
                DdcConfigItemBO previous;
                Map<Long,DdcConfigVersionBO> oldVersions;
                Map<String,DdcPublishTaskBO> oldTasks;
                List<OutboxMessage> oldMessages;
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object ignored,TransactionDefinition definition) {
                    previous=new DdcConfigItemBO(); BeanUtils.copyProperties(item,previous);
                    oldVersions=new LinkedHashMap<>(versionRows); oldTasks=new LinkedHashMap<>(taskRows); oldMessages=new ArrayList<>(messages);
                }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) {
                    BeanUtils.copyProperties(previous,item);
                    versionRows.clear();versionRows.putAll(oldVersions);taskRows.clear();taskRows.putAll(oldTasks);messages.clear();messages.addAll(oldMessages);
                }
            };
            var validation = new ValidationUtils(Validation.buildDefaultValidatorFactory().getValidator());
            dispatcher = new DdcPendingPublishDispatcher(new DdcConfigDispatchEventConverterImpl(), outbox,
                    new DdcConfigRolloutOutboxRouteDTO("tianshu.config-rollout","1"),validation);
            policies = (tenant,scope) -> new DdcConfigManifestFactory.PublicationPolicyVO(
                    scope,List.of(),List.of(new DdcConfigBaselineVO.TargetBindingVO("binding-a","7","cluster-test","reader-apps",
                    "Deployment","reader","deployment-uid","application")),null,Map.of(),Map.of());
            transactions = new TransactionTemplate(manager);
            service = createService(transactions);
        }
        DdcPublishService createService(TransactionTemplate transaction) {
            return new DdcPublishService(items,versions,tasks,audit,dispatcher,new PublishFailureRecorder(tasks),properties,
                    new DdcYamlConfigValidator(properties.getMaxConfigBytes()),transaction,Clock.systemUTC(),manifests,policies,tenants);
        }
    }
}
