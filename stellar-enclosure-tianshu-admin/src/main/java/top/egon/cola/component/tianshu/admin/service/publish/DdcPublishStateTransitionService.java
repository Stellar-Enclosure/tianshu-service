package top.egon.cola.component.tianshu.admin.service.publish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckSummaryBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.enums.PublishStatus;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigResourceKey;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishAckRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.model.config.DdcAckStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

@Service("ddcPublishStateTransitionService")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcPublishStateTransitionService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            PublishStatus.PENDING.name(),
            PublishStatus.PUBLISHING.name(),
            PublishStatus.UNKNOWN.name()
    );

    private static final List<String> DISPATCHABLE_STATUSES = List.of(
            PublishStatus.PENDING.name(),
            PublishStatus.PUBLISHING.name(),
            PublishStatus.UNKNOWN.name()
    );

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            PublishStatus.SUCCESS.name(),
            PublishStatus.FAILED.name(),
            PublishStatus.TIMEOUT.name(),
            PublishStatus.UNKNOWN.name()
    );

    @Qualifier("ddcPublishTaskRepository") private final DdcPublishTaskRepository taskRepository;

    @Qualifier("ddcConfigItemRepository") private final DdcConfigItemRepository configItemRepository;

    @Qualifier("ddcPublishAckRepository") private final DdcPublishAckRepository ackRepository;

    @Qualifier("publishResourceLockRegistry") private final PublishResourceLockRegistry resourceRegistry;

    @Qualifier("publishCompletionWaiterRegistry") private final PublishCompletionWaiterRegistry waiterRegistry;

    @Qualifier("ddcPublishClock") private final Clock clock;

    public DdcPublishStateTransitionService(
            DdcPublishTaskRepository taskRepository,
            @Qualifier("ddcConfigItemRepository") DdcConfigItemRepository configItemRepository,
            DdcPublishAckRepository ackRepository,
            PublishResourceLockRegistry resourceRegistry,
            PublishCompletionWaiterRegistry waiterRegistry) {
        this(
                taskRepository,
                configItemRepository,
                ackRepository,
                resourceRegistry,
                waiterRegistry,
                Clock.systemUTC()
        );
    }

    @Transactional
    public DdcPublishTaskBO markPublishing(String changeId) {
        return markPublishing(requiredTask(changeId));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public DdcPublishTaskBO markPublishing(DdcPublishTaskBO task) {
        requireArchivedMode(task);
        LocalDateTime now = now();
        int changed = taskRepository.transitionToPublishing(
                task,
                DISPATCHABLE_STATUSES,
                PublishStatus.PENDING.name(),
                PublishStatus.PUBLISHING.name(),
                now
        );
        if (changed != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("publish task changed before dispatch");
        }
        if (!PublishStatus.PENDING.name().equals(task.getStatus())) {
            task.setAttemptCount(Math.addExact(task.getAttemptCount() == null ? 0 : task.getAttemptCount(), 1));
        }
        return task.setStatus(PublishStatus.PUBLISHING.name()).setDispatchedAt(now).setUpdatedAt(now)
                .setCompletedAt(null).setFailureStage(null).setErrorMessage(null)
                .setVersion(Math.addExact(task.getVersion(), 1L));
    }

    @Transactional
    public DdcPublishTaskBO refreshAfterAck(String changeId) {
        DdcPublishTaskBO task = requiredTask(changeId);
        if (isTerminal(task)) {
            if (!taskRepository.fenceObservedVersion(task)) {
                throw new org.springframework.dao.OptimisticLockingFailureException("publish task changed");
            }
            return task;
        }
        return refreshAfterAck(task, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public DdcPublishTaskBO refreshAfterAck(DdcPublishTaskBO task, DdcPublishAckBO acknowledged) {
        requireArchivedMode(task);
        List<DdcPublishAckBO> targets = new ArrayList<>(ackRepository.findByChangeId(task.getChangeId()));
        if (acknowledged != null) {
            // The replica cannot yet see this transaction's ACK write.
            targets.removeIf(value -> Objects.equals(value.getId(), acknowledged.getId()));
            targets.add(acknowledged);
        }
        int success = count(targets, DdcAckStatus.SUCCESS.name());
        int failed = count(targets, DdcAckStatus.FAILED.name());
        int ignored = count(targets, DdcAckStatus.IGNORED.name());
        int timeout = count(targets, DdcAckStatus.TIMEOUT.name());
        LocalDateTime changedAt = now();
        DdcPublishAckSummaryBO summary;
        if (failed > 0) {
            summary = new DdcPublishAckSummaryBO(targets.size(), success, failed, ignored, timeout,
                    PublishStatus.FAILED.name(), "ACK", "one or more publish targets failed", "ANY");
        } else if (task.getTargetCount() != null && success == targets.size() && success == task.getTargetCount()) {
            summary = new DdcPublishAckSummaryBO(targets.size(), success, failed, ignored, timeout,
                    PublishStatus.SUCCESS.name(), null, null, "PUBLISHED");
            if (taskRepository.updateAckSummary(task, summary, changedAt) == 1) {
                return applyAckSummary(task, summary, changedAt);
            }
            summary = new DdcPublishAckSummaryBO(targets.size(), success, failed, ignored, timeout,
                    null, null, null, "UNPUBLISHED");
        } else {
            summary = new DdcPublishAckSummaryBO(targets.size(), success, failed, ignored, timeout,
                    null, null, null, "ANY");
        }
        if (taskRepository.updateAckSummary(task, summary, changedAt) != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("publish ACK summary changed after the replica read");
        }
        return applyAckSummary(task, summary, changedAt);
    }

    private DdcPublishTaskBO applyAckSummary(DdcPublishTaskBO task, DdcPublishAckSummaryBO summary, LocalDateTime changedAt) {
        task.setAckCount(summary.success()).setFailedCount(summary.failed()).setIgnoredCount(summary.ignored())
                .setTimeoutCount(summary.timeout()).setUpdatedAt(changedAt).setVersion(Math.addExact(task.getVersion(), 1L));
        if (summary.terminalStatus() != null) {
            task.setStatus(summary.terminalStatus()).setCompletedAt(changedAt)
                    .setFailureStage(summary.failureStage()).setErrorMessage(summary.errorMessage());
            notifyTerminalAfterCommit(task);
        } else {
            signalAfterCommit(task.getChangeId());
        }
        return task;
    }

    @Transactional
    public DdcPublishTaskBO fail(String changeId,
                                     String failureStage,
                                     String errorMessage) {
        DdcPublishTaskBO task = requiredTask(changeId);
        return fail(task, failureStage, errorMessage);
    }

    @Transactional
    public DdcPublishTaskBO fail(DdcPublishTaskBO task, String failureStage, String errorMessage) {
        requireArchivedMode(task);
        transitionToTerminal(task, PublishStatus.FAILED, failureStage, errorMessage);
        return task;
    }

    @Transactional
    public DdcPublishTaskBO timeout(String changeId,
                                        String errorMessage) {
        return timeout(requiredTask(changeId), errorMessage);
    }

    @Transactional
    public DdcPublishTaskBO timeout(DdcPublishTaskBO task, String errorMessage) {
        requireArchivedMode(task);
        if (!taskRepository.fenceObservedVersion(task)) {
            throw new org.springframework.dao.OptimisticLockingFailureException("publish task changed before timeout");
        }
        task.setVersion(Math.addExact(task.getVersion(), 1L));
        if (!ACTIVE_STATUSES.contains(task.getStatus())) return task;
        LocalDateTime changedAt = now();
        List<DdcPublishAckBO> targets = new ArrayList<>(ackRepository.findByChangeId(task.getChangeId()));
        if (task.getTargetCount() == null || targets.size() != task.getTargetCount()) {
            throw new org.springframework.dao.TransientDataAccessResourceException("publish targets are not yet visible together");
        }
        for (DdcPublishAckBO target : targets) {
            if (target.getAckStatus() == null) {
                target.setAckStatus(DdcAckStatus.TIMEOUT.name()).setAckAt(changedAt);
                ackRepository.update(target);
            }
        }
        DdcPublishAckSummaryBO summary = new DdcPublishAckSummaryBO(targets.size(),
                count(targets, DdcAckStatus.SUCCESS.name()), count(targets, DdcAckStatus.FAILED.name()),
                count(targets, DdcAckStatus.IGNORED.name()), count(targets, DdcAckStatus.TIMEOUT.name()),
                PublishStatus.TIMEOUT.name(), "ACK_TIMEOUT", errorMessage, "ANY");
        if (taskRepository.updateAckSummary(task, summary, changedAt) != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException("publish acknowledgements changed before timeout");
        }
        return applyAckSummary(task, summary, changedAt);
    }

    @Transactional
    public DdcPublishTaskBO unknown(String changeId,
                                        String errorMessage) {
        return unknown(changeId, "ADMIN_RESTART", errorMessage);
    }

    @Transactional
    public DdcPublishTaskBO unknown(String changeId,
                                        String failureStage,
                                        String errorMessage) {
        DdcPublishTaskBO task = requiredTask(changeId);
        transitionToTerminal(task, PublishStatus.UNKNOWN, failureStage, errorMessage);
        return task;
    }

    @Transactional
    public DdcPublishTaskBO markTargetLeaseExpired(
            String changeId,
            List<String> retryableStatuses) {
        return markTargetLeaseExpired(requiredTask(changeId), retryableStatuses);
    }

    @Transactional
    public DdcPublishTaskBO markTargetLeaseExpired(DdcPublishTaskBO task, List<String> retryableStatuses) {
        requireArchivedMode(task);
        LocalDateTime changedAt = now();
        int changed = taskRepository.markRetryLeaseExpired(
                task,
                PublishStatus.FAILED.name(),
                changedAt,
                "TARGET_VALIDATION",
                "TIANSHU_TARGET_LEASE_EXPIRED",
                retryableStatuses
        );
        if (changed != 1) throw new org.springframework.dao.OptimisticLockingFailureException("publish task changed before lease validation");
        task.setStatus(PublishStatus.FAILED.name()).setCompletedAt(changedAt).setUpdatedAt(changedAt)
                .setFailureStage("TARGET_VALIDATION").setErrorMessage("TIANSHU_TARGET_LEASE_EXPIRED")
                .setVersion(Math.addExact(task.getVersion(), 1L));
        notifyTerminalAfterCommit(task);
        return task;
    }

    public boolean isTerminal(DdcPublishTaskBO task) {
        return task != null && TERMINAL_STATUSES.contains(task.getStatus());
    }

    public void releaseIfTerminal(DdcPublishTaskBO task) {
        if (isTerminal(task)) {
            notifyTerminal(task);
        }
    }

    private boolean transitionToTerminal(DdcPublishTaskBO task,
                                         PublishStatus status,
                                         String failureStage,
                                         String errorMessage) {
        LocalDateTime now = now();
        if (!ACTIVE_STATUSES.contains(task.getStatus())) return false;
        int changed = taskRepository.transitionToTerminal(
                task,
                status.name(),
                now,
                failureStage,
                errorMessage,
                ACTIVE_STATUSES
        );
        if (changed == 1) {
            task.setStatus(status.name()).setCompletedAt(now).setUpdatedAt(now)
                    .setFailureStage(failureStage).setErrorMessage(errorMessage)
                    .setVersion(Math.addExact(task.getVersion(), 1L));
            notifyTerminalAfterCommit(task);
            return true;
        }
        throw new org.springframework.dao.OptimisticLockingFailureException("publish task changed before terminal transition");
    }

    private void updateCounters(String changeId, LocalDateTime now) {
        List<DdcPublishAckBO> targets = ackRepository.findByChangeId(changeId);
        taskRepository.updateCounters(
                changeId,
                count(targets, DdcAckStatus.SUCCESS.name()),
                count(targets, DdcAckStatus.FAILED.name()),
                count(targets, DdcAckStatus.IGNORED.name()),
                count(targets, DdcAckStatus.TIMEOUT.name()),
                now
        );
    }

    private int count(List<DdcPublishAckBO> targets, String status) {
        return (int) targets.stream()
                .filter(target -> status.equals(target.getAckStatus()))
                .count();
    }

    private boolean isPublished(DdcPublishTaskBO task) {
        return configItemRepository.findByPublicId(task.getConfigId())
                .map(config -> config.getPublishedVersion() != null
                        && config.getPublishedVersion().equals(task.getTargetVersion()))
                .orElse(false);
    }

    private DdcPublishTaskBO requiredTask(String changeId) {
        DdcPublishTaskBO task = taskRepository.findByChangeId(changeId)
                .orElseThrow(() -> new IllegalStateException(
                        "publish task not found: " + changeId
                ));
        requireArchivedMode(task);
        return task;
    }

    private void requireArchivedMode(DdcPublishTaskBO task) {
        if (task.getPlatformEvidence() != null) {
            log.debug("Ignored legacy state transition for native configuration. changeId={}", task.getChangeId());
            throw new IllegalArgumentException("TIANSHU_LEGACY_CONFIG_TRANSITION_FORBIDDEN");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void signalAfterCommit(String changeId) {
        afterCommit(() -> waiterRegistry.signal(changeId));
    }

    private void notifyTerminalAfterCommit(DdcPublishTaskBO task) {
        afterCommit(() -> notifyTerminal(task));
    }

    private void notifyTerminal(DdcPublishTaskBO task) {
        waiterRegistry.signal(task.getChangeId());
        resourceRegistry.release(resourceKey(task), task.getChangeId());
    }

    private DdcConfigResourceKey resourceKey(DdcPublishTaskBO task) {
        return new DdcConfigResourceKey(
                task.getBizCode(),
                task.getEnv(),
                task.getAppCode(),
                task.getResourceName()
        );
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }
}
