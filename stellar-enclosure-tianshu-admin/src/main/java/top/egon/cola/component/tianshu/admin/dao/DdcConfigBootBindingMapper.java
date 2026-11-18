package top.egon.cola.component.tianshu.admin.dao;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.batch.BatchSqlSession;
import org.apache.ibatis.executor.BatchResult;
import java.util.function.BiPredicate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.extension.EgonColaExplicitTenantScopeMapper;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigBootBindingPO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** Exact SINGLE-table capability; the XML exposes immutable creation and keyed reads only. */
@Mapper
@Validated
@InterceptorIgnore(tenantLine = "true")
@EgonColaExplicitTenantScopeMapper(technicalModel = DdcConfigBootBindingPO.class,
        technicalTable = "public.ddc_config_boot_binding_t0")
public interface DdcConfigBootBindingMapper extends EgonColaMapper<DdcConfigBootBindingPO> {

    int insertIfAbsent(@Param("record") DdcConfigBootBindingPO record);
    DdcConfigBootBindingPO findByIncarnation(@Param("clusterId") String clusterId, @Param("podUid") String podUid,
            @Param("containerName") String containerName, @Param("containerId") String containerId);

    @Override default int insert(DdcConfigBootBindingPO entity) { throw immutable(); }
    @Override default int updateById(DdcConfigBootBindingPO entity) { throw immutable(); }
    @Override default int update(DdcConfigBootBindingPO entity, Wrapper<DdcConfigBootBindingPO> wrapper) { throw immutable(); }
    @Override default int deleteById(DdcConfigBootBindingPO entity) { throw immutable(); }
    @Override default int deleteById(Serializable id) { throw immutable(); }
    @Override default int deleteById(Object entity, boolean useFill) { throw immutable(); }
    @Override default int delete(Wrapper<DdcConfigBootBindingPO> wrapper) { throw immutable(); }
    @Override default int deleteByIds(Collection<?> ids) { throw immutable(); }
    @Override default int deleteByIds(Collection<?> ids, boolean useFill) { throw immutable(); }
    @Override default int deleteBatchIds(Collection<?> ids) { throw immutable(); }
    // MP batch helpers execute statement IDs directly rather than invoking the single-row overrides above.
    @Override default List<BatchResult> insert(Collection<DdcConfigBootBindingPO> entities, int batchSize) { throw immutable(); }
    @Override default List<BatchResult> updateById(Collection<DdcConfigBootBindingPO> entities, int batchSize) { throw immutable(); }
    @Override default List<BatchResult> insertOrUpdate(Collection<DdcConfigBootBindingPO> entities,
            BiPredicate<BatchSqlSession, DdcConfigBootBindingPO> predicate, int batchSize) { throw immutable(); }
    @Override default int deleteVersionedById(DdcConfigBootBindingPO entity) { throw immutable(); }
    @Override default int restoreVersionedById(DdcConfigBootBindingPO entity) { throw immutable(); }
    @Override default DdcConfigBootBindingPO selectActiveById(Serializable id) { throw immutable(); }
    @Override default List<DdcConfigBootBindingPO> selectActiveByIds(Collection<? extends Serializable> ids) { throw immutable(); }

    private static UnsupportedOperationException immutable() {
        return new UnsupportedOperationException("TIANSHU_CONFIG_BOOT_BINDING_EXACT_OPERATION_REQUIRED");
    }
}
