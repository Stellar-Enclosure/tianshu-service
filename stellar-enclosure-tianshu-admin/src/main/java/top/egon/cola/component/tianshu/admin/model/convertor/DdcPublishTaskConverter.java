package top.egon.cola.component.tianshu.admin.model.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcPublishTaskPO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcPublishTaskConverter extends BaseConverter<DdcPublishTaskPO, DdcPublishTaskBO> {
    @Override
    @Mapping(target = "platformEvidence", source = "platformEvidence", qualifiedByName = "copyPlatformEvidence")
    DdcPublishTaskBO toTarget(DdcPublishTaskPO source);

    @Override
    @Mapping(target = "platformEvidence", source = "platformEvidence", qualifiedByName = "copyPlatformEvidence")
    DdcPublishTaskPO toSource(DdcPublishTaskBO target);

    @Named("copyPlatformEvidence")
    default JsonNode copyPlatformEvidence(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }
}
