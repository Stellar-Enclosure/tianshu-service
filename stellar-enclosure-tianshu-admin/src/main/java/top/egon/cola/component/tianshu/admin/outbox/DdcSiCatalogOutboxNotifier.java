package top.egon.cola.component.tianshu.admin.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.search.DdcSiSemanticIndexService;
import top.egon.cola.component.tianshu.model.si.DdcSiPublicationEvent;
import top.egon.cola.component.tianshu.model.tenant.DdcTenantMessageDTO;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;

import java.time.Clock;
import java.util.UUID;

/**
 * 在写事务内可靠写入 {@code EVENT-001} Outbox 记录，并仅于事务提交后递增 Redis 作用域版本号及调度异步语义索引。
 */
@Slf4j
@Component("ddcSiCatalogOutboxNotifier")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiCatalogOutboxNotifier {

    private static final String SI_CATALOG_CHANNEL = "tianshu.si-catalog";
    private static final String SI_SCHEMA_VERSION = "1";

    @Qualifier("transactionalOutbox")
    private final TransactionalOutbox transactionalOutbox;

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    private final ObjectProvider<DdcSiSemanticIndexService> semanticIndexServiceProvider;

    private final ApplicationEventPublisher eventPublisher;

    @Qualifier("jacksonObjectMapper")
    private final ObjectMapper objectMapper;

    @Qualifier("ddcPublishClock")
    private final Clock clock;

    /**
     * 在当前数据库事务内记录 Outbox 消息，并发布事务后置信号。
     */
    public void enqueueAndScheduleAfterCommit(DdcSiPublicationEvent.EventType eventType,
                                              DdcSiResourceBO resource,
                                              DdcSiVersionBO version) {
        String eventId = "si-" + UUID.randomUUID().toString().replace("-", "");
        Long tenantId = resource.getTenantId();
        Integer activeVer = version != null && version.getVersionNo() != null
                ? version.getVersionNo().intValue()
                : (resource.getCurrentVersionNo() == null ? null : resource.getCurrentVersionNo().intValue());

        DdcSiPublicationEvent event = DdcSiPublicationEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .tenantId(tenantId)
                .bizCode(resource.getBizCode())
                .namespaceCode(resource.getNamespaceCode() == null ? "default" : resource.getNamespaceCode())
                .envCode(resource.getEnvCode())
                .kind(resource.getKind())
                .resourceKey(resource.getResourceKey())
                .activeVersionNo(activeVer)
                .catalogRevision(version != null ? version.getVersionNo() : resource.getLastVersionNo())
                .occurredAt(clock.instant())
                .build();

        String namespaceCode = resource.getNamespaceCode() == null || resource.getNamespaceCode().isBlank()
                ? "default"
                : resource.getNamespaceCode();
        String destination = DdcRedisKeys.siCatalogVersionKey(tenantId, resource.getBizCode(), namespaceCode, resource.getEnvCode());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new DdcTenantMessageDTO<>(tenantId, event));
        } catch (Exception failure) {
            throw new IllegalStateException("SI_CATALOG_EVENT_SERIALIZE_FAILED", failure);
        }

        transactionalOutbox.enqueue(OutboxMessage.builder()
                .messageId(eventId)
                .idempotencyKey(eventId)
                .channel(SI_CATALOG_CHANNEL)
                .destination(destination)
                .payload(payload)
                .contentType("application/json")
                .schemaVersion(SI_SCHEMA_VERSION)
                .header("tenantId", Long.toString(tenantId))
                .header("eventType", eventType.getCode())
                .build());

        Long versionId = version != null ? version.getId() : resource.getCurrentVersionId();
        boolean reindexEligible = versionId != null
                && (eventType == DdcSiPublicationEvent.EventType.PUBLISHED
                || eventType == DdcSiPublicationEvent.EventType.PROMOTED);
        SiAfterCommitSignal signal = new SiAfterCommitSignal(
                eventId, tenantId, resource.getBizCode(), namespaceCode, resource.getEnvCode(),
                resource.getId(), reindexEligible ? versionId : null);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(signal);
        } else {
            onCatalogChanged(signal);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCatalogChanged(SiAfterCommitSignal signal) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                String key = DdcRedisKeys.siCatalogVersionKey(
                        signal.tenantId, signal.bizCode, signal.namespaceCode, signal.envCode);
                redis.opsForValue().increment(key);
            } catch (RuntimeException failure) {
                log.warn("Failed to bump SI catalog version key after commit eventId={} tenantId={} env={}",
                        signal.eventId, signal.tenantId, signal.envCode, failure);
            }
        }
        if (signal.versionIdToReindex != null) {
            DdcSiSemanticIndexService indexService = semanticIndexServiceProvider.getIfAvailable();
            if (indexService != null) {
                indexService.scheduleReindex(signal.tenantId, signal.resourceId, signal.versionIdToReindex);
            }
        }
    }

    /** 事务提交后执行的轻量信号载荷。 */
    public static final class SiAfterCommitSignal {
        private final String eventId;
        private final Long tenantId;
        private final String bizCode;
        private final String namespaceCode;
        private final String envCode;
        private final Long resourceId;
        private final Long versionIdToReindex;

        public SiAfterCommitSignal(String eventId, Long tenantId, String bizCode, String namespaceCode,
                                   String envCode, Long resourceId, Long versionIdToReindex) {
            this.eventId = eventId;
            this.tenantId = tenantId;
            this.bizCode = bizCode;
            this.namespaceCode = namespaceCode;
            this.envCode = envCode;
            this.resourceId = resourceId;
            this.versionIdToReindex = versionIdToReindex;
        }
    }
}
