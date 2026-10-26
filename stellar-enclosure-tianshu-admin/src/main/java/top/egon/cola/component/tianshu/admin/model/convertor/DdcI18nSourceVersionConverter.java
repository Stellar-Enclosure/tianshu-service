package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceVersionBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nSourceVersionPO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcI18nSourceVersionConverter extends BaseConverter<DdcI18nSourceVersionPO, DdcI18nSourceVersionBO> {
}
