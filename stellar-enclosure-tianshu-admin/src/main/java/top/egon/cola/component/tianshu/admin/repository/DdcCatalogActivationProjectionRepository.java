package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProjectionBO;
import java.util.List;
import java.util.Optional;

public interface DdcCatalogActivationProjectionRepository {
    Optional<DdcCatalogActivationProjectionBO> findByVersion(Long catalogScopeId, Long activationVersion);
    DdcCatalogActivationProjectionBO insert(DdcCatalogActivationProjectionBO row);
}
