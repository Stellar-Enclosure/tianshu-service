package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.bo.DdcInstanceBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import java.util.List;
import java.util.Optional;

/** Read-only legacy telemetry. It must never be used to establish current process availability. */
@Validated
public interface DdcInstanceRepository {
    @Validated(EgonColaModelValidationGroups.Query.class)
    Optional<DdcInstanceBO> findHistoricalByInstanceId(@NotBlank(groups = EgonColaModelValidationGroups.Query.class) String instanceId);
    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcInstanceBO> findHistoricalByScope(String bizCode, String env, String appCode);
    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcPageResult<DdcInstanceBO> findHistoricalByScope(String bizCode, String env, String appCode, PageQuery pageQuery);
}
