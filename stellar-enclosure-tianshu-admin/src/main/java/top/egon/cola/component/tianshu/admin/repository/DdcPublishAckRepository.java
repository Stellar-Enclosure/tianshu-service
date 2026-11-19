package top.egon.cola.component.tianshu.admin.repository;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckBO;
import java.util.List;

/** Tenant-scoped archive access. Old ACK rows never establish current process application. */
@Validated
public interface DdcPublishAckRepository {
    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcPublishAckBO> findByChangeId(
            @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 64, groups = EgonColaModelValidationGroups.Query.class) String changeId);
}
