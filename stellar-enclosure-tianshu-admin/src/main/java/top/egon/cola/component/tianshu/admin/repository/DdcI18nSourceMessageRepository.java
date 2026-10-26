package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceMessageBO;
import java.util.List;

public interface DdcI18nSourceMessageRepository {
    List<DdcI18nSourceMessageBO> findBySource(Long catalogScopeId, Long sourceId);
    List<DdcI18nSourceMessageBO> findByActivationPage(Long catalogScopeId, Long projectionId, String pageCode, boolean includeCommon, long offset, int limit);
    void insertAll(List<DdcI18nSourceMessageBO> rows);
}
