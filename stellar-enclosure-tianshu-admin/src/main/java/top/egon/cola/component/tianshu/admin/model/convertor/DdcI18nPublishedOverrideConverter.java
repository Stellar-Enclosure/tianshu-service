package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPublishedOverrideBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nPublishedOverridePO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcI18nPublishedOverrideConverter extends BaseConverter<DdcI18nPublishedOverridePO, DdcI18nPublishedOverrideBO> {
}
