package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigBootBindingBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigBootBindingPO;

/** Maps only binding facts; persistence owns technical identity and audit fields on creation. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcConfigBootBindingConverter extends BaseConverter<DdcConfigBootBindingPO, DdcConfigBootBindingBO> {
    @Override DdcConfigBootBindingBO toTarget(DdcConfigBootBindingPO source);
    @Override DdcConfigBootBindingPO toSource(DdcConfigBootBindingBO source);
}
