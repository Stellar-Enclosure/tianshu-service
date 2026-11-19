package top.egon.cola.component.tianshu.admin.repository;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckSummaryBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.model.config.DdcPublishTarget;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Validated
public interface DdcPublishTaskRepository {

    @Validated(EgonColaModelValidationGroups.Query.class)
    Optional<DdcPublishTaskBO> findByChangeId(@NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 64, groups = EgonColaModelValidationGroups.Query.class) String changeId);

    @Validated(EgonColaModelValidationGroups.Query.class)
    Optional<DdcPublishTaskBO> findForUpdateByChangeId(@NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 64, groups = EgonColaModelValidationGroups.Query.class) String changeId);

    int updateNativeEvidence(@NotNull DdcPublishTaskBO task, @NotNull JsonNode evidence,
            @NotNull String nativeStatus, String failureStage, @NotNull LocalDateTime changedAt);

    int resetNativeForRetry(@NotNull DdcPublishTaskBO task, String operator, @NotNull LocalDateTime changedAt);

    @Validated(EgonColaModelValidationGroups.Update.class)
    boolean fenceObservedVersion(@NotNull DdcPublishTaskBO task);

    boolean fenceDispatchTargets(@NotNull DdcPublishTaskBO task, @NotNull List<DdcPublishTarget> targets);

    int updateAckSummary(@NotNull DdcPublishTaskBO task, @NotNull DdcPublishAckSummaryBO summary, @NotNull LocalDateTime updatedAt);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcPublishTaskBO> findAll();

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcPublishTaskBO> findByStatusIn(Collection<String> statuses);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcPublishTaskBO> findByStatusInAndUpdatedAtBefore(Collection<String> statuses, LocalDateTime updatedAt);

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcPageResult<DdcPublishTaskBO> search(String bizCode, String env, String appCode, String status,
            String changeId, PageQuery pageQuery);

    @Validated(EgonColaModelValidationGroups.Update.class)
    int claimStaleForRecovery(DdcPublishTaskBO task, Collection<String> activeStatuses,
            LocalDateTime staleBefore, LocalDateTime claimedAt);

    @Validated(EgonColaModelValidationGroups.Query.class)
    Optional<DdcPublishTaskBO> findFirstByBizCodeAndEnvAndAppCodeAndResourceNameAndStatusIn(
            String bizCode, String env, String appCode, String resourceName, Collection<String> statuses);

    @Validated(EgonColaModelValidationGroups.Update.class)
    int transitionToPublishing(DdcPublishTaskBO task, Collection<String> dispatchableStatuses,
            String pendingStatus, String publishingStatus, LocalDateTime dispatchedAt);

    @Validated(EgonColaModelValidationGroups.Update.class)
    int updateCounters(String changeId, int ackCount, int failedCount, int ignoredCount,
            int timeoutCount, LocalDateTime updatedAt);

    @Validated(EgonColaModelValidationGroups.Update.class)
    int transitionToTerminal(DdcPublishTaskBO task, String terminalStatus, LocalDateTime completedAt,
            String failureStage, String errorMessage, Collection<String> activeStatuses);

    @Validated(EgonColaModelValidationGroups.Update.class)
    int resetForRetry(DdcPublishTaskBO task, String pendingStatus, String operator,
            LocalDateTime updatedAt, Collection<String> retryableStatuses);

    @Validated(EgonColaModelValidationGroups.Update.class)
    int markRetryLeaseExpired(DdcPublishTaskBO task, String failedStatus, LocalDateTime completedAt,
            String failureStage, String errorMessage, Collection<String> retryableStatuses);

    @Validated(EgonColaModelValidationGroups.Insert.class)
    DdcPublishTaskBO insert(@Valid @NotNull(groups = EgonColaModelValidationGroups.Insert.class) DdcPublishTaskBO command);

    @Validated(EgonColaModelValidationGroups.Update.class)
    DdcPublishTaskBO update(@Valid @NotNull(groups = EgonColaModelValidationGroups.Update.class) DdcPublishTaskBO command);
}
