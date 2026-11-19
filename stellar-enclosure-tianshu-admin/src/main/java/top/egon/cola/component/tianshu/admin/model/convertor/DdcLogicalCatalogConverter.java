package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAppBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcBizBO;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;

/** One-way logical catalog projections preserving application ownership and disabled facts. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcLogicalCatalogConverter extends BaseForwardConverter<DdcBizBO, DdcManagementBiz> {

    @Override
    @Mapping(target = "id", source = "publicId")
    @Mapping(target = "enabled", expression = "java(Boolean.TRUE.equals(source.getEnabled()))")
    DdcManagementBiz toTarget(DdcBizBO source);

    @Mapping(target = "id", source = "application.publicId")
    @Mapping(target = "businessId", source = "business.publicId")
    @Mapping(target = "bizCode", source = "business.bizCode")
    @Mapping(target = "appCode", source = "application.appCode")
    @Mapping(target = "appName", source = "application.appName")
    @Mapping(target = "enabled", expression = "java(Boolean.TRUE.equals(application.getEnabled()))")
    @Mapping(target = "businessEnabled", expression = "java(Boolean.TRUE.equals(business.getEnabled()))")
    DdcManagementApp toApplication(DdcAppBO application, DdcBizBO business);
}
