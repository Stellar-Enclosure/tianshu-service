package top.egon.cola.component.tianshu.admin.service.publish;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcOperationLogBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishRequest;
import top.egon.cola.component.tianshu.admin.model.enums.ChangeType;
import top.egon.cola.component.tianshu.admin.model.enums.PublishMode;
import top.egon.cola.component.tianshu.admin.model.enums.PublishStatus;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishResultVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.config.DdcYamlConfigValidator;
import top.egon.cola.component.tianshu.format.DdcChecksum;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Native configuration intake: one local transaction, no network delivery or process-ACK wait. */
@Service("ddcPublishService")
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcPublishService {
    @Qualifier("ddcConfigItemRepository") private final DdcConfigItemRepository items;
    @Qualifier("ddcConfigVersionRepository") private final DdcConfigVersionRepository versions;
    @Qualifier("ddcPublishTaskRepository") private final DdcPublishTaskRepository tasks;
    @Qualifier("ddcOperationLogRepository") private final DdcOperationLogRepository audit;
    @Qualifier("ddcPendingPublishDispatcher") private final DdcPendingPublishDispatcher dispatcher;
    @Qualifier("publishFailureRecorder") private final PublishFailureRecorder recovery;
    @Qualifier("egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties")
    private final DdcAdminProperties properties;
    @Qualifier("ddcYamlConfigValidator") private final DdcYamlConfigValidator yaml;
    @Qualifier("ddcPublishTransactionTemplate") private final TransactionTemplate transaction;
    @Qualifier("ddcPublishClock") private final Clock clock;
    @Qualifier("ddcConfigManifestFactory") private final DdcConfigManifestFactory manifests;
    @Qualifier("ddcConfigPublicationPolicyResolver") private final DdcConfigManifestFactory.PublicationPolicyResolver policies;
    @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;

    public DdcPublishResultVO publish(@Valid DdcPublishRequest request, String operator) {
        validate(request);
        var tenant = currentTenant();
        long timeout = timeout(request.getTimeoutMs());
        try {
            var result = transaction.execute(status -> prepare(tenant, request, timeout, operator));
            if (result == null) { throw new DdcAdminException("TIANSHU_CONFIG_PUBLICATION_UNAVAILABLE"); }
            return DdcPublishResultVO.from(result);
        } catch (RuntimeException uncertain) {
            // REQUIRES_NEW reads the committed primary outcome; no guessed failure can overwrite a committed task.
            var committed = recovery.findCommitted(request.getChangeId());
            if (committed.isPresent()) {
                validateReplay(tenant, committed.get(), request, timeout);
                return DdcPublishResultVO.from(committed.get());
            }
            throw uncertain;
        }
    }

    private DdcPublishTaskBO prepare(DdcTenantContextDTO tenant, DdcPublishRequest request, long timeout, String operator) {
        var config = items.findForPublishByBizCodeAndEnvAndAppCodeAndResourceName(request.getBizCode(),
                request.getEnv(), request.getAppCode(), request.getResourceName())
                .orElseThrow(() -> new DdcAdminException("config item not found"));
        requireOwned(tenant, config);
        var existing = tasks.findByChangeId(request.getChangeId());
        if (existing.isPresent()) {
            validateReplay(tenant, existing.get(), request, timeout);
            return existing.get();
        }
        if (!Objects.equals(config.getCurrentVersion(), request.getExpectedVersion())
                || !Objects.equals(config.getFormat(), request.getFormat()) || config.getEnabled() == null) {
            throw new DdcAdminException("config version or format changed");
        }
        // A staged delete is an explicit disable and cannot be reactivated through a publish body.
        boolean enabled = !Boolean.TRUE.equals(config.getDeleted()) && config.getEnabled();
        if (Boolean.TRUE.equals(config.getDeleted()) && !Objects.equals(config.getContent(), request.getContent())) {
            throw new DdcAdminException("disabled config must retain its fixed content");
        }
        long revision = Math.addExact(config.getCurrentVersion(), 1L);
        var scope = scope(tenant, config);
        var frozen = manifests.freeze(tenant, config, revision, request.getContent(), enabled, policies.require(tenant, scope));
        Long previousPublished = config.getPublishedVersion();
        String previousBody = config.getContent();
        config.setContent(request.getContent()).setCurrentVersion(revision).setUpdatedAt(now());
        items.update(config);
        versions.insert(DdcConfigVersionBO.builder().tenantId(tenant.tenantId()).configItemId(config.getId())
                .configId(config.getPublicId()).bizCode(config.getBizCode()).appCode(config.getAppCode()).env(config.getEnv())
                .resourceName(config.getResourceName()).configRevision(revision).oldContent(previousBody)
                .newContent(request.getContent()).manifestJson(frozen.manifest()).format(config.getFormat())
                .changeType(ChangeType.UPDATE.name()).changeReason("publish config").operator(operator).createdAt(now()).build());
        var evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("schemaVersion", 1).putArray("targets");
        var task = tasks.insert(DdcPublishTaskBO.builder().tenantId(tenant.tenantId()).changeId(request.getChangeId())
                .configItemId(config.getId()).configId(config.getPublicId()).bizCode(config.getBizCode())
                .appCode(config.getAppCode()).env(config.getEnv()).resourceName(config.getResourceName()).targetVersion(revision)
                .publishMode(PublishMode.ASYNC.name()).platformEvidence(evidence)
                .resourceChecksum(DdcChecksum.resource(request.getResourceName(), request.getFormat(), request.getContent()))
                .attemptCount(1).status(PublishStatus.PENDING.name()).targetCount(0).ackCount(0).failedCount(0)
                .ignoredCount(0).timeoutCount(0).timeoutMs(timeout).operator(operator).createdAt(now()).updatedAt(now()).build());
        if (items.advancePublishedVersion(config.getPublicId(), previousPublished, revision, now()) != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("native published pointer changed");
        }
        audit.insert(DdcOperationLogBO.builder().tenantId(tenant.tenantId()).bizCode(config.getBizCode())
                .appCode(config.getAppCode()).env(config.getEnv()).resourceName(config.getResourceName())
                .operationType("PUBLISH").operator(operator).operationContent(request.getChangeId()).createdAt(now()).build());
        return dispatcher.enqueueNative(task);
    }

    public DdcPublishResultVO retry(String changeId) { return retry(changeId, null); }

    public DdcPublishResultVO retry(String changeId, String operator) {
        if (changeId == null || changeId.isBlank()) { throw new DdcAdminException("changeId is required"); }
        var tenant = currentTenant();
        var task = transaction.execute(status -> {
            var hint = tasks.findByChangeId(changeId).orElseThrow(() -> new DdcAdminException("publish task not found"));
            var config = items.findForPublishByBizCodeAndEnvAndAppCodeAndResourceName(hint.getBizCode(), hint.getEnv(),
                    hint.getAppCode(), hint.getResourceName()).orElseThrow(() -> new DdcAdminException("config item not found"));
            requireOwned(tenant, config);
            var current = tasks.findForUpdateByChangeId(changeId).orElseThrow(() -> new DdcAdminException("publish task not found"));
            if (!DdcPendingPublishDispatcher.isNative(current) || !Objects.equals(current.getTenantId(), tenant.tenantId())
                    || !Objects.equals(current.getConfigItemId(), config.getId())
                    || !Objects.equals(config.getPublishedVersion(), current.getTargetVersion())
                    || !List.of("FAILED", "TIMEOUT", "UNKNOWN", "PARTIAL_SUCCESS").contains(current.getStatus())) {
                throw new DdcAdminException("native publish task is not current and retryable");
            }
            materialize(tenant, current);
            if (tasks.resetNativeForRetry(current, operator, now()) != 1) {
                throw new org.springframework.dao.OptimisticLockingFailureException("native retry changed");
            }
            current.setAttemptCount(Math.addExact(current.getAttemptCount(), 1)).setStatus("PENDING")
                    .setVersion(Math.addExact(current.getVersion(), 1L)).setUpdatedAt(now());
            return dispatcher.enqueueNative(current);
        });
        if (task == null) { throw new DdcAdminException("TIANSHU_CONFIG_PUBLICATION_UNAVAILABLE"); }
        return DdcPublishResultVO.from(task);
    }

    private void validateReplay(DdcTenantContextDTO tenant, DdcPublishTaskBO task, DdcPublishRequest request, long timeout) {
        if (!DdcPendingPublishDispatcher.isNative(task) || !Objects.equals(task.getTenantId(), tenant.tenantId())
                || !Objects.equals(task.getBizCode(), request.getBizCode()) || !Objects.equals(task.getEnv(), request.getEnv())
                || !Objects.equals(task.getAppCode(), request.getAppCode()) || !Objects.equals(task.getResourceName(), request.getResourceName())
                || !Objects.equals(task.getTimeoutMs(), timeout) || task.getTargetVersion() == null
                || task.getTargetVersion() != Math.addExact(request.getExpectedVersion(), 1L)
                || !Objects.equals(task.getResourceChecksum(), DdcChecksum.resource(request.getResourceName(), request.getFormat(), request.getContent()))) {
            throw new DdcAdminException("changeId already belongs to a different publication");
        }
        materialize(tenant, task);
    }

    private void materialize(DdcTenantContextDTO tenant, DdcPublishTaskBO task) {
        var version = versions.findByConfigItemIdAndRevision(task.getConfigItemId(), task.getTargetVersion())
                .orElseThrow(() -> new DdcAdminException("fixed config version is unavailable"));
        manifests.materialize(version, new DdcConfigScopeVO(Long.toString(tenant.tenantId()), task.getBizCode(),
                task.getEnv(), task.getAppCode(), task.getResourceName()));
    }

    private DdcTenantContextDTO currentTenant() {
        try {
            if (tenants == null) { throw new IllegalArgumentException(); }
            String value = MDC.get("tenantId");
            long id = Long.parseLong(value);
            if (!Long.toString(id).equals(value)) { throw new IllegalArgumentException(); }
            var registered = tenants.requireEnabledTenant(id);
            return new DdcTenantContextDTO(id, registered.getIdentityTenantId(), MDC.get("userId"));
        } catch (RuntimeException unavailable) { throw new DdcAdminException("TIANSHU_TRUSTED_TENANT_REQUIRED"); }
    }
    private static void requireOwned(DdcTenantContextDTO tenant, DdcConfigItemBO item) {
        if (!Objects.equals(tenant.tenantId(), item.getTenantId()) || item.getId() == null || item.getId() <= 0
                || item.getDeletedAt() != null) { throw new DdcAdminException("config item not found"); }
    }
    private static DdcConfigScopeVO scope(DdcTenantContextDTO tenant, DdcConfigItemBO item) {
        return new DdcConfigScopeVO(Long.toString(tenant.tenantId()), item.getBizCode(), item.getEnv(), item.getAppCode(), item.getResourceName());
    }
    private void validate(DdcPublishRequest request) {
        if (request == null || request.getChangeId() == null || !request.getChangeId().matches("[1-9][0-9]{0,18}")
                || request.getExpectedVersion() == null || request.getExpectedVersion() <= 0) {
            throw new DdcAdminException("valid changeId and expectedVersion are required");
        }
        try { Long.parseLong(request.getChangeId()); } catch (NumberFormatException invalid) { throw new DdcAdminException("invalid changeId"); }
        if (request.getBizCode() == null || request.getBizCode().isBlank() || request.getEnv() == null || request.getEnv().isBlank()
                || request.getAppCode() == null || request.getAppCode().isBlank() || !"application.yml".equals(request.getResourceName())) {
            throw new DdcAdminException("native configuration scope is required");
        }
        yaml.validate(request.getResourceName(), request.getFormat(), request.getContent());
    }
    private long timeout(Long requested) {
        long value = requested == null ? properties.getPublish().getDefaultTimeoutMs() : requested;
        if (value <= 0 || value > properties.getPublish().getMaxTimeoutMs()) { throw new DdcAdminException("invalid timeoutMs"); }
        return value;
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
