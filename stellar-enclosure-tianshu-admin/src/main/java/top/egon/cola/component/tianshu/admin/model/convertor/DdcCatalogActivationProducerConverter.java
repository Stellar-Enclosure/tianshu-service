package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogActivationProducerBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcCatalogActivationProducerPO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcCatalogActivationProducerConverter extends BaseConverter<DdcCatalogActivationProducerPO, DdcCatalogActivationProducerBO> {
}
