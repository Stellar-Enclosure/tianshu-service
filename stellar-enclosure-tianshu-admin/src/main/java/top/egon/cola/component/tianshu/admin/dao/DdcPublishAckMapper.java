package top.egon.cola.component.tianshu.admin.dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.batch.BatchSqlSession;
import org.apache.ibatis.executor.BatchResult;
import java.util.function.BiPredicate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcPublishAckPO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Mapper
@Validated
public interface DdcPublishAckMapper extends EgonColaMapper<DdcPublishAckPO> {

    @Override
    default DdcPublishAckPO selectActiveById(Serializable id) {
        return selectActiveById(EgonColaTenantIdProvider.currentTenantId(), internalId(id));
    }

    @Override
    default List<DdcPublishAckPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return selectActiveByIds(EgonColaTenantIdProvider.currentTenantId(), ids.stream().map(DdcPublishAckMapper::internalId).toList());
    }

    private static Long internalId(Serializable id) {
        if (!(id instanceof Long value) || value <= 0) throw new IllegalArgumentException("ID_MUST_BE_POSITIVE_LONG");
        return value;
    }

    DdcPublishAckPO selectActiveById(@Param("tenantId") Long tenantId, @Param("id") Long id);
    List<DdcPublishAckPO> selectActiveByIds(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);
    List<DdcPublishAckPO> selectByChangeId(@Param("tenantId") Long tenantId, @Param("changeId") String changeId);

    @Override default int insert(DdcPublishAckPO entity) { throw readOnly(); }
    @Override default int updateById(DdcPublishAckPO entity) { throw readOnly(); }
    @Override default int update(DdcPublishAckPO entity, Wrapper<DdcPublishAckPO> wrapper) { throw readOnly(); }
    @Override default int deleteById(DdcPublishAckPO entity) { throw readOnly(); }
    @Override default int deleteById(Serializable id) { throw readOnly(); }
    @Override default int deleteById(Object entity, boolean useFill) { throw readOnly(); }
    @Override default int delete(Wrapper<DdcPublishAckPO> wrapper) { throw readOnly(); }
    @Override default int deleteByIds(Collection<?> ids) { throw readOnly(); }
    @Override default int deleteByIds(Collection<?> ids, boolean useFill) { throw readOnly(); }
    @Override default int deleteBatchIds(Collection<?> ids) { throw readOnly(); }
    @Override default List<BatchResult> insert(Collection<DdcPublishAckPO> entities, int batchSize) { throw readOnly(); }
    @Override default List<BatchResult> updateById(Collection<DdcPublishAckPO> entities, int batchSize) { throw readOnly(); }
    @Override default List<BatchResult> insertOrUpdate(Collection<DdcPublishAckPO> entities,
            BiPredicate<BatchSqlSession, DdcPublishAckPO> predicate, int batchSize) { throw readOnly(); }
    @Override default int deleteVersionedById(DdcPublishAckPO entity) { throw readOnly(); }
    @Override default int restoreVersionedById(DdcPublishAckPO entity) { throw readOnly(); }

    private static UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException("TIANSHU_LEGACY_ACK_READ_ONLY");
    }
}
