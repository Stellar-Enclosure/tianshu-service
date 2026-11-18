package top.egon.cola.component.tianshu.admin.dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.batch.BatchSqlSession;
import org.apache.ibatis.executor.BatchResult;
import java.io.Serializable;
import java.util.Collection;
import java.util.function.BiPredicate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import java.time.Instant;
import java.util.List;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigProcessReceiptPO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.TargetQueryVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigProcessReceiptRepository.IncarnationVO;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;

@Mapper
@Validated
public interface DdcConfigProcessReceiptMapper extends EgonColaMapper<DdcConfigProcessReceiptPO> {
    DdcConfigProcessReceiptPO selectExact(@Param("key") DdcConfigProcessReceiptPO key);
    DdcConfigProcessReceiptPO selectExactForUpdate(@Param("key") DdcConfigProcessReceiptPO key);
    String selectManifest(@Param("tenantId") Long tenantId,@Param("configId") Long configId,@Param("configRevision") Long configRevision);
    int insertIfAbsent(@Param("row") DdcConfigProcessReceiptPO row);
    int advance(@Param("row") DdcConfigProcessReceiptPO row,@Param("expectedVersion") Long expectedVersion,@Param("expectedSeq") Long expectedSeq);
    List<DdcConfigProcessReceiptPO> selectCurrentByTarget(@Param("tenantId") Long tenantId,@Param("target") TargetQueryVO target,
            @Param("incarnations") List<IncarnationVO> incarnations,@Param("limit") int limit);
    List<DdcConfigProcessReceiptPO> selectHistory(@Param("tenantId") Long tenantId,@Param("afterReceivedAt") Instant afterReceivedAt,
            @Param("afterId") Long afterId,@Param("limit") int limit);

    @Override default int insert(DdcConfigProcessReceiptPO entity) { throw exactOperationRequired(); }
    @Override default int updateById(DdcConfigProcessReceiptPO entity) { throw exactOperationRequired(); }
    @Override default int update(DdcConfigProcessReceiptPO entity, Wrapper<DdcConfigProcessReceiptPO> wrapper) { throw exactOperationRequired(); }
    @Override default int deleteById(DdcConfigProcessReceiptPO entity) { throw exactOperationRequired(); }
    @Override default int deleteById(Serializable id) { throw exactOperationRequired(); }
    @Override default int deleteById(Object entity, boolean useFill) { throw exactOperationRequired(); }
    @Override default int delete(Wrapper<DdcConfigProcessReceiptPO> wrapper) { throw exactOperationRequired(); }
    @Override default int deleteByIds(Collection<?> ids) { throw exactOperationRequired(); }
    @Override default int deleteByIds(Collection<?> ids, boolean useFill) { throw exactOperationRequired(); }
    @Override default int deleteBatchIds(Collection<?> ids) { throw exactOperationRequired(); }
    @Override default List<BatchResult> insert(Collection<DdcConfigProcessReceiptPO> entities, int batchSize) { throw exactOperationRequired(); }
    @Override default List<BatchResult> updateById(Collection<DdcConfigProcessReceiptPO> entities, int batchSize) { throw exactOperationRequired(); }
    @Override default List<BatchResult> insertOrUpdate(Collection<DdcConfigProcessReceiptPO> entities,
            BiPredicate<BatchSqlSession, DdcConfigProcessReceiptPO> predicate, int batchSize) { throw exactOperationRequired(); }
    @Override default int deleteVersionedById(DdcConfigProcessReceiptPO entity) { throw exactOperationRequired(); }
    @Override default int restoreVersionedById(DdcConfigProcessReceiptPO entity) { throw exactOperationRequired(); }
    @Override default DdcConfigProcessReceiptPO selectActiveById(Serializable id) { throw exactOperationRequired(); }
    @Override default List<DdcConfigProcessReceiptPO> selectActiveByIds(Collection<? extends Serializable> ids) { throw exactOperationRequired(); }

    private static UnsupportedOperationException exactOperationRequired() {
        return new UnsupportedOperationException("CONFIG_RECEIPT_EXACT_OPERATION_REQUIRED");
    }
}
