package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.dao.DdcInstanceMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcInstanceBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcInstanceConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcInstanceRepository;
import java.util.List;
import java.util.Optional;

/** Selects archived rows through guarded MyBatis. No inherited MP CRUD write API is exposed. */
@Slf4j
@Repository("ddcInstanceRepository")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Validated
public class DdcInstanceRepositoryImpl implements DdcInstanceRepository {
    @Qualifier("ddcInstanceMapper") private final DdcInstanceMapper mapper;
    @Qualifier("ddcInstanceConverterImpl") private final DdcInstanceConverter converter;

    @Override public Optional<DdcInstanceBO> findHistoricalByInstanceId(String instanceId) {
        return Optional.ofNullable(mapper.selectByInstanceId(tenantId(), instanceId)).map(converter::toTarget);
    }
    @Override public List<DdcInstanceBO> findHistoricalByScope(String bizCode, String env, String appCode) {
        return converter.toTargetList(mapper.selectByScope(tenantId(), bizCode, env, appCode));
    }
    @Override public DdcPageResult<DdcInstanceBO> findHistoricalByScope(
            String bizCode, String env, String appCode, PageQuery query) {
        PageQuery page = query == null ? PageQuery.defaultPage() : query;
        Long tenant = tenantId();
        List<DdcInstanceBO> records = converter.toTargetList(mapper.selectPage(tenant, bizCode, env, appCode,
                page.offset(), page.pageSize()));
        return DdcPageResult.<DdcInstanceBO>builder().records(records)
                .total(mapper.countPage(tenant, bizCode, env, appCode))
                .pageNo(page.pageNo()).pageSize(page.pageSize()).build();
    }
    private static Long tenantId() {
        Long tenant = EgonColaTenantIdProvider.currentTenantId();
        if (tenant == null || tenant <= 0) throw new IllegalStateException("TENANT_ID_MUST_BE_POSITIVE");
        return tenant;
    }
}
