package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProducerBO;
import java.util.List;
import java.util.Optional;

public interface DdcCatalogActivationProducerRepository {
    List<DdcCatalogActivationProducerBO> findByProjection(Long catalogScopeId, Long projectionId);
    long countSelectionConflicts(Long catalogScopeId, Long projectionId);
    void insertAll(List<DdcCatalogActivationProducerBO> rows);
}
