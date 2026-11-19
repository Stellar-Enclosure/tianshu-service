package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.dao.DdcPublishAckMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishAckConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishAckRepository;

import java.util.List;

/** Read-only archive projection through the existing guarded MP Mapper; no generic CRUD capability is exposed. */
@Slf4j
@Repository("ddcPublishAckRepository")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Validated
public class DdcPublishAckRepositoryImpl implements DdcPublishAckRepository {
    @Qualifier("ddcPublishAckMapper") private final DdcPublishAckMapper mapper;
    @Qualifier("ddcPublishAckConverterImpl") private final DdcPublishAckConverter converter;

    @Override
    public List<DdcPublishAckBO> findByChangeId(String changeId) {
        return converter.toTargetList(mapper.selectByChangeId(EgonColaTenantIdProvider.currentTenantId(), changeId));
    }
}
