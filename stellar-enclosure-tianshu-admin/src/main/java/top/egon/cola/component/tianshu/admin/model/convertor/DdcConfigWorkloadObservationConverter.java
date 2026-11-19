package top.egon.cola.component.tianshu.admin.model.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigWorkloadObservationBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigWorkloadObservationPO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcConfigWorkloadObservationConverter extends BaseConverter<DdcConfigWorkloadObservationPO,DdcConfigWorkloadObservationBO> {
    @Override @Mapping(target = "viewJson", source = "viewJson", qualifiedByName = "copyObservationJson")
    DdcConfigWorkloadObservationBO toTarget(DdcConfigWorkloadObservationPO source);
    @Override @Mapping(target = "viewJson", source = "viewJson", qualifiedByName = "copyObservationJson")
    DdcConfigWorkloadObservationPO toSource(DdcConfigWorkloadObservationBO source);
    @Named("copyObservationJson")
    default JsonNode copyObservationJson(JsonNode value) { return value == null ? null : value.deepCopy(); }
}
