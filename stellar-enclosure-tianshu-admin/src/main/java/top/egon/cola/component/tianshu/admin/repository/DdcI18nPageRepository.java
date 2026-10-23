package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import java.util.Optional;

public interface DdcI18nPageRepository {
    Optional<DdcI18nPageBO> findById(Long id);
    Optional<DdcI18nPageBO> findByPageCode(DdcI18nScope scope, String pageCode);
    DdcPageResult<DdcI18nPageBO> search(DdcI18nScope scope, PageQuery query);
    void lockScope(DdcI18nScope scope);
    DdcI18nPageBO insert(DdcI18nPageBO page);
    Optional<DdcI18nPageBO> update(DdcI18nPageBO page);
}
