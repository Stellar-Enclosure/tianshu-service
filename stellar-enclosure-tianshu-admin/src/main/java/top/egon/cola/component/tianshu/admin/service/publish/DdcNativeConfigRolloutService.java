package top.egon.cola.component.tianshu.admin.service.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.enums.PublishStatus;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigBaselineReconciler.TargetResultVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Handles technical wakeups without making delivery success mean that a process applied the configuration. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcNativeConfigRolloutService {
    @NonNull @Qualifier("ddcConfigItemRepository") private final DdcConfigItemRepository items;
    @NonNull @Qualifier("ddcConfigVersionRepository") private final DdcConfigVersionRepository versions;
    @NonNull @Qualifier("ddcPublishTaskRepository") private final DdcPublishTaskRepository tasks;
    @NonNull @Qualifier("ddcConfigManifestFactory") private final DdcConfigManifestFactory manifests;
    @NonNull @Qualifier("ddcConfigBaselineReconciler") private final DdcConfigBaselineReconciler baselines;
    @NonNull @Qualifier("ddcNativeRolloutTransactionTemplate") private final TransactionTemplate transactions;
    @NonNull @Qualifier("jacksonObjectMapper") private final ObjectMapper json;
    @NonNull @Qualifier("ddcPublishClock") private final Clock clock;

    public boolean reconcileNative(@NotBlank String changeId, @Positive long configId, @Positive long revision,
            @Positive int attempt, @NotBlank String eventId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TIANSHU_CONFIG_ROLLOUT_TRANSACTION_FORBIDDEN");
        }
        Long owner;
        try {
            owner = EgonColaTenantIdProvider.currentTenantId();
        } catch (IllegalStateException missingAuthority) {
            return false;
        }
        if (owner == null || owner <= 0 || changeId == null || changeId.isBlank() || changeId.length() > 64
                || configId <= 0 || revision <= 0 || attempt <= 0 || eventId == null || eventId.isBlank()
                || eventId.length() > 128) {
            return false;
        }
        WakeupVO expected = new WakeupVO(owner, changeId, configId, revision, attempt);
        Optional<DdcPublishTaskBO> initial = transactions.execute(status -> tasks.findByChangeId(changeId));
        DdcPublishTaskBO task = optional(initial).filter(candidate -> matches(candidate, expected)).orElse(null);
        if (task == null) {
            return false;
        }
        if (!isCurrent(task)) {
            finish(task, expected, task.getPlatformEvidence(), PublishStatus.SUPERSEDED, null);
            return false;
        }

        DdcConfigVersionBO version = optional(transactions.execute(status ->
                versions.findByConfigItemIdAndRevision(configId, revision))).orElse(null);
        var scope = new DdcConfigScopeVO(Long.toString(owner), task.getBizCode(), task.getEnv(),
                task.getAppCode(), task.getResourceName());
        List<TargetResultVO> applied;
        try {
            if (version == null) throw new IllegalArgumentException("TIANSHU_CONFIG_RELEASE_VERSION_MISSING");
            var release = manifests.materialize(version, scope).baseline();
            applied = baselines.reconcile(release, () -> isCurrent(task));
        } catch (IllegalArgumentException invalidArtifact) {
            return finish(task, expected, task.getPlatformEvidence(), PublishStatus.FAILED, "IMMUTABLE_RELEASE")
                    == PublishStatus.FAILED;
        }

        var evidence = json.createObjectNode().put("schemaVersion", 1);
        evidence.set("targets", json.valueToTree(applied));
        boolean pending = applied.stream().anyMatch(target -> "PENDING".equals(target.status()));
        boolean superseded = applied.stream().anyMatch(target -> "SUPERSEDED".equals(target.status()));
        boolean failed = applied.stream().anyMatch(target -> "FAILED".equals(target.status()));
        PublishStatus desiredStatus = superseded ? PublishStatus.SUPERSEDED
                : pending ? PublishStatus.PUBLISHING : failed ? PublishStatus.FAILED : PublishStatus.PLATFORM_APPLIED;
        PublishStatus persisted = finish(task, expected, evidence, desiredStatus, failed ? "WORKLOAD_RECONCILE" : null);
        if (persisted == PublishStatus.PUBLISHING) {
            throw new IllegalStateException("TIANSHU_CONFIG_ROLLOUT_WILL_RETRY");
        }
        log.debug("Committed Tianshu rollout evidence. changeId={} status={}", changeId, persisted);
        return persisted != PublishStatus.SUPERSEDED;
    }

    private boolean isCurrent(DdcPublishTaskBO task) {
        return Boolean.TRUE.equals(transactions.execute(status -> findItem(task, false)
                .filter(item -> task.getConfigItemId().equals(item.getId()) && task.getTargetVersion().equals(item.getPublishedVersion()))
                .isPresent()));
    }

    private PublishStatus finish(DdcPublishTaskBO original, WakeupVO wakeup, JsonNode evidence,
            PublishStatus desiredStatus, String failureStage) {
        return Objects.requireNonNull(transactions.execute(status -> {
            var item = findItem(original, true).orElse(null);
            var currentTask = tasks.findForUpdateByChangeId(wakeup.changeId()).orElse(null);
            if (currentTask == null || !matches(currentTask, wakeup)) {
                return PublishStatus.SUPERSEDED;
            }
            boolean current = item != null && Objects.equals(item.getId(), wakeup.configId())
                    && Objects.equals(item.getPublishedVersion(), wakeup.revision());
            PublishStatus state = current ? desiredStatus : PublishStatus.SUPERSEDED;
            if (current && desiredStatus == PublishStatus.SUPERSEDED) {
                // A foreground observation did not prove the current pointer. Retry rather than settle it as stale.
                throw new OptimisticLockingFailureException("TIANSHU_CONFIG_ROLLOUT_POINTER_CHANGED");
            }
            if (!Objects.equals(original.getVersion(), currentTask.getVersion())) {
                throw new OptimisticLockingFailureException("TIANSHU_CONFIG_ROLLOUT_TASK_CHANGED");
            }
            var finalEvidence = state == PublishStatus.SUPERSEDED ? currentTask.getPlatformEvidence() : evidence;
            if (tasks.updateNativeEvidence(currentTask, finalEvidence, state.name(),
                    state == PublishStatus.SUPERSEDED ? null : failureStage,
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)) != 1) {
                throw new OptimisticLockingFailureException("TIANSHU_CONFIG_ROLLOUT_CAS_FAILED");
            }
            return state;
        }));
    }

    private Optional<DdcConfigItemBO> findItem(DdcPublishTaskBO task, boolean lock) {
        return lock
                ? items.findForPublishByBizCodeAndEnvAndAppCodeAndResourceName(
                        task.getBizCode(), task.getEnv(), task.getAppCode(), task.getResourceName())
                : items.findByBizCodeAndEnvAndAppCodeAndResourceName(
                        task.getBizCode(), task.getEnv(), task.getAppCode(), task.getResourceName());
    }

    private static boolean matches(DdcPublishTaskBO task, WakeupVO expected) {
        var evidence = task.getPlatformEvidence();
        return evidence != null && evidence.isObject() && evidence.path("schemaVersion").isInt()
                && evidence.path("schemaVersion").intValue() == 1 && evidence.path("targets").isArray()
                && Objects.equals(task.getTenantId(), expected.owner())
                && Objects.equals(task.getChangeId(), expected.changeId())
                && Objects.equals(task.getConfigItemId(), expected.configId())
                && Objects.equals(task.getTargetVersion(), expected.revision())
                && Objects.equals(task.getAttemptCount(), expected.attempt())
                && !PublishStatus.SUPERSEDED.name().equals(task.getStatus());
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return Objects.requireNonNull(value, "PRIMARY read returned no result container");
    }

    private record WakeupVO(long owner, String changeId, long configId, long revision, int attempt) { }
}
