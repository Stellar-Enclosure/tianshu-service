package top.egon.cola.component.tianshu.admin.repository;

import top.egon.cola.component.tianshu.admin.model.bo.DdcResourceMetadataProjectionBO;
import java.util.List;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;

public interface DdcResourceMetadataProjectionRepository {
    List<DdcResourceMetadataProjectionBO> findBySource(Long catalogScopeId, Long sourceId);
    DdcPageResult<DdcResourceMetadataProjectionBO> search(Long catalogScopeId, Long projectionId, DdcResourceMetadataQuery filter, PageQuery page);
    void insertAll(List<DdcResourceMetadataProjectionBO> rows);
}
