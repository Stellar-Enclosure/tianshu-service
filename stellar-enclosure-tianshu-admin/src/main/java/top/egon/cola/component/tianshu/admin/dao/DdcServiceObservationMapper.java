package top.egon.cola.component.tianshu.admin.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.batch.BatchSqlSession;
import org.apache.ibatis.executor.BatchResult;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcServiceObservationPO;

/** Exact PRIMARY-scoped statements; no Kubernetes requests occur inside the transaction. */
@Mapper
@Validated
public interface DdcServiceObservationMapper extends EgonColaMapper<DdcServiceObservationPO> {
    DdcServiceObservationPO selectActive(@Param("key") @NotNull @Valid TargetKeyVO key);
    DdcServiceObservationPO selectActiveForUpdate(@Param("key") @NotNull @Valid TargetKeyVO key);
    int insertIfAbsent(@Param("row") @NotNull @Valid DdcServiceObservationPO row);
    int advance(@Param("row") @NotNull @Valid DdcServiceObservationPO row, @Param("expectedVersion") long expectedVersion,
                @Param("expectedEpoch") long expectedEpoch, @Param("expectedSeq") long expectedSeq);

    /** Selects the one revision before comparing its immutable physical binding fingerprint. */
    record TargetKeyVO(@NotNull @Positive Long tenantId, @NotBlank @Size(max = 128) String bindingId,
                       @Positive long bindingRevision) {
        public TargetKeyVO {
            if (tenantId == null || tenantId <= 0 || bindingRevision <= 0
                    || bindingId == null || !bindingId.matches("[!-~]{1,128}")) {
                throw new IllegalArgumentException("TIANSHU_SERVICE_OBSERVATION_QUERY_INVALID");
            }
        }
    }

    @Override default int insert(DdcServiceObservationPO entity) { throw exactOperationRequired(); }
    @Override default int updateById(DdcServiceObservationPO entity) { throw exactOperationRequired(); }
    @Override default int update(DdcServiceObservationPO entity, Wrapper<DdcServiceObservationPO> wrapper) { throw exactOperationRequired(); }
    @Override default int deleteById(DdcServiceObservationPO entity) { throw exactOperationRequired(); }
    @Override default int deleteById(Serializable id) { throw exactOperationRequired(); }
    @Override default int deleteById(Object entity, boolean useFill) { throw exactOperationRequired(); }
    @Override default int delete(Wrapper<DdcServiceObservationPO> wrapper) { throw exactOperationRequired(); }
    @Override default int deleteByIds(Collection<?> ids) { throw exactOperationRequired(); }
    @Override default int deleteByIds(Collection<?> ids, boolean useFill) { throw exactOperationRequired(); }
    @Override default int deleteBatchIds(Collection<?> ids) { throw exactOperationRequired(); }
    @Override default List<BatchResult> insert(Collection<DdcServiceObservationPO> entities, int batchSize) { throw exactOperationRequired(); }
    @Override default List<BatchResult> updateById(Collection<DdcServiceObservationPO> entities, int batchSize) { throw exactOperationRequired(); }
    @Override default List<BatchResult> insertOrUpdate(Collection<DdcServiceObservationPO> entities,
            BiPredicate<BatchSqlSession,DdcServiceObservationPO> predicate, int batchSize) { throw exactOperationRequired(); }
    @Override default int deleteVersionedById(DdcServiceObservationPO entity) { throw exactOperationRequired(); }
    @Override default int restoreVersionedById(DdcServiceObservationPO entity) { throw exactOperationRequired(); }
    @Override default DdcServiceObservationPO selectActiveById(Serializable id) { throw exactOperationRequired(); }
    @Override default List<DdcServiceObservationPO> selectActiveByIds(Collection<? extends Serializable> ids) { throw exactOperationRequired(); }

    private static UnsupportedOperationException exactOperationRequired() {
        return new UnsupportedOperationException("TIANSHU_SERVICE_OBSERVATION_EXACT_OPERATION_REQUIRED");
    }
}
