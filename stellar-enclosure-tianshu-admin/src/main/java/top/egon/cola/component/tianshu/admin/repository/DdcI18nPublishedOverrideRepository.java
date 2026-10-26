package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPublishedOverrideBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nOverrideEntryBO;
import java.util.List;
import java.util.Optional;

public interface DdcI18nPublishedOverrideRepository {
    Optional<DdcI18nPublishedOverrideBO> findByOperation(Long catalogScopeId, Long pageId, String operationId);
    List<DdcI18nOverrideEntryBO> findApplicableEntries(Long catalogScopeId, Long projectionId, String pageCode, long offset, int limit);
    DdcI18nPublishedOverrideBO insert(DdcI18nPublishedOverrideBO row);
}
