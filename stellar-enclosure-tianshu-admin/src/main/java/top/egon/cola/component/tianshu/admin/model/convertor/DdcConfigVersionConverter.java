package top.egon.cola.component.tianshu.admin.model.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcConfigVersionPO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVersionVO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcConfigVersionConverter extends BaseConverter<DdcConfigVersionPO, DdcConfigVersionBO> {

    @Override
    @Mapping(target = "manifestJson", source = "manifestJson", qualifiedByName = "copyConfigManifestJson")
    @Mapping(target = "configItemId", source = "configId")
    @Mapping(target = "configId", source = "configPublicId")
    DdcConfigVersionBO toTarget(DdcConfigVersionPO source);

    @Override
    @Mapping(target = "manifestJson", source = "manifestJson", qualifiedByName = "copyConfigManifestJson")
    @Mapping(target = "configId", source = "configItemId")
    @Mapping(target = "configPublicId", ignore = true)
    DdcConfigVersionPO toSource(DdcConfigVersionBO source);

    @Named("copyConfigManifestJson")
    default JsonNode copyConfigManifestJson(JsonNode value) { return value == null ? null : value.deepCopy(); }

    @Mapping(target = "id", source = "publicId")
    @Mapping(target = "version", source = "configRevision")
    DdcConfigVersionVO toVersionVO(DdcConfigVersionBO source);
}
