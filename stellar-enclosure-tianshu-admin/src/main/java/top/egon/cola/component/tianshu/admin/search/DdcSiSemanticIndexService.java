package top.egon.cola.component.tianshu.admin.search;

import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;

import java.util.List;

/** SI 语义分片异步建索引与 RRF 混合检索领域服务契约。 */
public interface DdcSiSemanticIndexService {

    /** 异步调度指定版本快照的文本切分与向量重构（通常由 Outbox 提交后触发）。 */
    void scheduleReindex(Long tenantId, Long resourceId, Long versionId);

    /** 同步执行指定版本快照的分片向量化入库（供异步任务及管理员手动重建调用）。 */
    void reindexVersionSync(Long tenantId, Long resourceId, Long versionId);

    /** 执行 RRF（pgvector HNSW + PostgreSQL tsvector GIN）混合检索，模型异常或超时自动降级至纯 FTS。 */
    List<DdcSiSearchHit> hybridSearch(DdcSiSemanticSearchQuery query);
}
