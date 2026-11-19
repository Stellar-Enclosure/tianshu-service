package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishTaskResult;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcPublishTaskResultConverter extends BaseForwardConverter<DdcPublishTaskBO, DdcPublishTaskResult> {

    @Override
    @Mapping(target = "publicId", source = "publicId")
    @Mapping(target = "desiredRevision", ignore = true)
    @Mapping(target = "historyOnly", ignore = true)
    @Mapping(target = "platformState", ignore = true)
    @Mapping(target = "rolloutState", ignore = true)
    @Mapping(target = "processState", ignore = true)
    @Mapping(target = "observedAt", ignore = true)
    @Mapping(target = "observationValidUntil", ignore = true)
    @Mapping(target = "reasonCode", ignore = true)
    @Mapping(target = "targets", ignore = true)
    DdcPublishTaskResult toTarget(DdcPublishTaskBO source);
}
