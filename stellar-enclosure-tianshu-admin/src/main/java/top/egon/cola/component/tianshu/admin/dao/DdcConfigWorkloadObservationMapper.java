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
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigWorkloadObservationPO;

/** Exact PRIMARY-scoped statements; no Kubernetes requests occur inside the transaction. */
@Mapper
@Validated
public interface DdcConfigWorkloadObservationMapper extends EgonColaMapper<DdcConfigWorkloadObservationPO> {
    DdcConfigWorkloadObservationPO selectActive(@Param("key") @NotNull @Valid TargetKeyVO key);
    DdcConfigWorkloadObservationPO selectActiveForUpdate(@Param("key") DdcConfigWorkloadObservationPO key);
    int insertIfAbsent(@Param("row") DdcConfigWorkloadObservationPO row);
    int advance(@Param("row") DdcConfigWorkloadObservationPO row, @Param("expectedVersion") long expectedVersion,
                @Param("expectedEpoch") long expectedEpoch, @Param("expectedSeq") long expectedSeq);

    /** Read predicates have no payload/audit requirements; persisted observation rows retain their full constraints. */
    record TargetKeyVO(@NotNull @Positive Long tenantId, @NotBlank @Size(max = 128) String targetBindingId,
                       @Positive long targetRevision, @NotBlank @Size(max = 128) String clusterId,
                       @NotBlank @Size(max = 63) String k8sNamespace, @NotBlank @Size(max = 128) String workloadUid) {
        public TargetKeyVO {
            if (tenantId == null || tenantId <= 0 || targetRevision <= 0
                    || targetBindingId == null || !targetBindingId.matches("[!-~]{1,128}")
                    || clusterId == null || !clusterId.matches("[!-~]{1,128}")
                    || k8sNamespace == null || !k8sNamespace.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
                    || workloadUid == null || !workloadUid.matches("[!-~]{1,128}")) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_OBSERVATION_QUERY_INVALID");
            }
        }
    }

    @Override default int insert(DdcConfigWorkloadObservationPO entity) { throw exactOperationRequired(); }
    @Override default int updateById(DdcConfigWorkloadObservationPO entity) { throw exactOperationRequired(); }
    @Override default int update(DdcConfigWorkloadObservationPO entity, Wrapper<DdcConfigWorkloadObservationPO> wrapper) { throw exactOperationRequired(); }
    @Override default int deleteById(DdcConfigWorkloadObservationPO entity) { throw exactOperationRequired(); }
    @Override default int deleteById(Serializable id) { throw exactOperationRequired(); }
    @Override default int deleteById(Object entity, boolean useFill) { throw exactOperationRequired(); }
    @Override default int delete(Wrapper<DdcConfigWorkloadObservationPO> wrapper) { throw exactOperationRequired(); }
    @Override default int deleteByIds(Collection<?> ids) { throw exactOperationRequired(); }
    @Override default int deleteByIds(Collection<?> ids, boolean useFill) { throw exactOperationRequired(); }
    @Override default int deleteBatchIds(Collection<?> ids) { throw exactOperationRequired(); }
    @Override default List<BatchResult> insert(Collection<DdcConfigWorkloadObservationPO> entities, int batchSize) { throw exactOperationRequired(); }
    @Override default List<BatchResult> updateById(Collection<DdcConfigWorkloadObservationPO> entities, int batchSize) { throw exactOperationRequired(); }
    @Override default List<BatchResult> insertOrUpdate(Collection<DdcConfigWorkloadObservationPO> entities,
            BiPredicate<BatchSqlSession,DdcConfigWorkloadObservationPO> predicate, int batchSize) { throw exactOperationRequired(); }
    @Override default int deleteVersionedById(DdcConfigWorkloadObservationPO entity) { throw exactOperationRequired(); }
    @Override default int restoreVersionedById(DdcConfigWorkloadObservationPO entity) { throw exactOperationRequired(); }
    @Override default DdcConfigWorkloadObservationPO selectActiveById(Serializable id) { throw exactOperationRequired(); }
    @Override default List<DdcConfigWorkloadObservationPO> selectActiveByIds(Collection<? extends Serializable> ids) { throw exactOperationRequired(); }

    private static UnsupportedOperationException exactOperationRequired() {
        return new UnsupportedOperationException("TIANSHU_CONFIG_OBSERVATION_EXACT_OPERATION_REQUIRED");
    }
}
