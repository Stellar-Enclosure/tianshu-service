package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportGrantBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSourceImportGrantPO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcSourceImportGrantConverter extends BaseConverter<DdcSourceImportGrantPO, DdcSourceImportGrantBO> {
}
