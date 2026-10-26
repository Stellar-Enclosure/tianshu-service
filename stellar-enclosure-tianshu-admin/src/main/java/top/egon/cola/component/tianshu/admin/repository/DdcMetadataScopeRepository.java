package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import java.util.Optional;

public interface DdcMetadataScopeRepository {
    Optional<DdcMetadataScopeBO> findByCatalogScopeId(Long catalogScopeId);
    Optional<DdcMetadataScopeBO> lockEnabledTarget(Long catalogScopeId);
    DdcMetadataScopeBO insert(DdcMetadataScopeBO command);
}
