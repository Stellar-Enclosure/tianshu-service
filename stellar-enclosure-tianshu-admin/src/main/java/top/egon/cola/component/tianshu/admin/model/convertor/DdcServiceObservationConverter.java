package top.egon.cola.component.tianshu.admin.model.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcServiceObservationBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcServiceObservationPO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcServiceObservationConverter extends BaseConverter<DdcServiceObservationPO,DdcServiceObservationBO> {
    @Override @Mapping(target = "viewJson", source = "viewJson", qualifiedByName = "copyServiceObservationJson")
    DdcServiceObservationBO toTarget(DdcServiceObservationPO source);
    @Override @Mapping(target = "viewJson", source = "viewJson", qualifiedByName = "copyServiceObservationJson")
    DdcServiceObservationPO toSource(DdcServiceObservationBO source);
    @Named("copyServiceObservationJson")
    default JsonNode copyServiceObservationJson(JsonNode value) { return value == null ? null : value.deepCopy(); }
}
