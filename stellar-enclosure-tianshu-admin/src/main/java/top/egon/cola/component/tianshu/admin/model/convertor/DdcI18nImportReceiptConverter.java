package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nImportReceiptBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcI18nImportReceiptPO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcI18nImportReceiptConverter extends BaseConverter<DdcI18nImportReceiptPO, DdcI18nImportReceiptBO> {
}
