package top.egon.cola.component.tianshu.admin.repository;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;

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

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcPublishTaskBO> findAll();

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcPageResult<DdcPublishTaskBO> search(String bizCode, String env, String appCode, String status,
            String changeId, PageQuery pageQuery);

    @Validated(EgonColaModelValidationGroups.Query.class)
    Optional<DdcPublishTaskBO> findFirstByBizCodeAndEnvAndAppCodeAndResourceNameAndStatusIn(
            String bizCode, String env, String appCode, String resourceName, Collection<String> statuses);
    @Validated(EgonColaModelValidationGroups.Insert.class)
    DdcPublishTaskBO insert(@Valid @NotNull(groups = EgonColaModelValidationGroups.Insert.class) DdcPublishTaskBO command);
}
