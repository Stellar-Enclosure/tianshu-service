package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.tianshu.admin.dao.DdcI18nPageMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcI18nPageConverter;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nPagePO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import java.util.Optional;

@Repository("ddcI18nPageRepository")
@RequiredArgsConstructor
public class DdcI18nPageRepositoryImpl extends EgonColaRepository<DdcI18nPageMapper, DdcI18nPagePO>
        implements DdcI18nPageRepository {
    @Qualifier("ddcI18nPageMapper") private final DdcI18nPageMapper mapper;
    @Qualifier("ddcI18nPageConverterImpl") private final DdcI18nPageConverter converter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;

    @Override public DdcI18nPageMapper getBaseMapper() { return mapper; }
    @Override protected EgonColaMybatisPlusProperties getProperties() { return properties; }

    @Override
    public Optional<DdcI18nPageBO> findById(Long id) {
        return Optional.ofNullable(mapper.selectPageById(EgonColaTenantIdProvider.currentTenantId(), id))
                .map(converter::toTarget);
    }

    @Override public Optional<DdcI18nPageBO> lockById(Long id) {
        return Optional.ofNullable(mapper.lockById(EgonColaTenantIdProvider.currentTenantId(), id)).map(converter::toTarget);
    }

    @Override
    public Optional<DdcI18nPageBO> findByPageCode(DdcI18nScope scope, String pageCode) {
        return Optional.ofNullable(mapper.selectByPageCode(EgonColaTenantIdProvider.currentTenantId(), scope, pageCode))
                .map(converter::toTarget);
    }

    @Override
    public DdcPageResult<DdcI18nPageBO> search(DdcI18nScope scope, PageQuery query) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        long offset = Math.multiplyExact((long) query.pageNo() - 1, query.pageSize());
        return DdcPageResult.<DdcI18nPageBO>builder().records(mapper.selectPage(tenantId, scope, offset, query.pageSize())
                .stream().map(converter::toTarget).toList()).total(mapper.countPage(tenantId, scope))
                .pageNo(query.pageNo()).pageSize(query.pageSize()).build();
    }

    @Override
    public void lockScope(DdcI18nScope scope) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        mapper.lockBiz(tenantId, scope);
        mapper.lockNamespace(tenantId, scope);
        mapper.lockEnv(tenantId, scope);
        mapper.lockApp(tenantId, scope);
        mapper.lockBinding(tenantId, scope);
    }

    @Override
    public DdcI18nPageBO insert(DdcI18nPageBO page) {
        DdcI18nPagePO row = converter.toSource(page);
        if (!save(row)) throw new IllegalStateException("I18N_PAGE_INSERT_FAILED");
        return converter.toTarget(row);
    }

    @Override
    public Optional<DdcI18nPageBO> update(DdcI18nPageBO page) {
        DdcI18nPagePO row = converter.toSource(page);
        if (!updateById(row)) return Optional.empty();
        return Optional.of(converter.toTarget(row));
    }
}
