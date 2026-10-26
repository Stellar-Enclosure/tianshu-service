package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportGrantBO;
import java.util.Optional;

public interface DdcSourceImportGrantRepository {
    Optional<DdcSourceImportGrantBO> findEnabled(Long catalogScopeId, String producerId, String bindingDigest);
    Optional<DdcSourceImportGrantBO> lockEnabled(Long catalogScopeId, String producerId, String bindingDigest);
    DdcSourceImportGrantBO insert(DdcSourceImportGrantBO command);
}
