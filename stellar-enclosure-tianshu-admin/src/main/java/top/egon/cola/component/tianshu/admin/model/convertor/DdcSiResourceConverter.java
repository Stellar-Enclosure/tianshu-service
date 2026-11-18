package top.egon.cola.component.tianshu.admin.model.convertor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiBindingPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiReferencePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiResourcePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiVersionPO;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiVersionDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** SI 资源、版本、绑定与依赖边 PO <-> BO <-> 跨仓 Descriptor 双向 MapStruct 转换器。 */
@Mapper(componentModel = "spring", uses = DdcSiEnumConverter.class,
        imports = DdcSiLifecycleStatusEnum.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcSiResourceConverter extends BaseConverter<DdcSiResourcePO, DdcSiResourceBO> {

    ObjectMapper SHARED_JSON = new ObjectMapper();
    TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    @Override
    @Mapping(target = "tags", source = "tagsJson", qualifiedByName = "tagsJsonToList")
    DdcSiResourceBO toTarget(DdcSiResourcePO source);

    @Override
    @Mapping(target = "tagsJson", source = "tags", qualifiedByName = "tagsListToJson")
    DdcSiResourcePO toSource(DdcSiResourceBO target);

    @Mapping(target = "dependencyManifestJson", source = "dependencyManifestJson", qualifiedByName = "copySiDependencyManifestJson")
    DdcSiVersionBO toVersionBo(DdcSiVersionPO source);

    @Mapping(target = "dependencyManifestJson", source = "dependencyManifestJson", qualifiedByName = "copySiDependencyManifestJson")
    DdcSiVersionPO toVersionPo(DdcSiVersionBO target);

    @Named("copySiDependencyManifestJson")
    default JsonNode copySiDependencyManifestJson(JsonNode value) { return value == null ? null : value.deepCopy(); }

    @Mapping(target = "status", source = "bindingStatus")
    DdcSiBindingBO toBindingBo(DdcSiBindingPO source);

    @Mapping(target = "bindingStatus", source = "status")
    DdcSiBindingPO toBindingPo(DdcSiBindingBO target);

    @Mapping(target = "targetType", source = "targetKind")
    @Mapping(target = "pinnedVersionNo", source = "targetVersionNo")
    @Mapping(target = "required", constant = "true")
    @Mapping(target = "roleHint", ignore = true)
    DdcSiReferenceBO toReferenceBo(DdcSiReferencePO source);

    @Mapping(target = "targetKind", source = "targetKind")
    @Mapping(target = "targetVersionNo", expression = "java(target.getTargetVersionNo() != null ? target.getTargetVersionNo() : target.getPinnedVersionNo())")
    DdcSiReferencePO toReferencePo(DdcSiReferenceBO target);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "tenantId", source = "resource.tenantId")
    @Mapping(target = "bizCode", source = "resource.bizCode")
    @Mapping(target = "namespaceCode", source = "resource.namespaceCode")
    @Mapping(target = "envCode", source = "resource.envCode")
    @Mapping(target = "resourceId", source = "resource.id")
    @Mapping(target = "kind", source = "resource.kind")
    @Mapping(target = "resourceKey", source = "resource.resourceKey")
    @Mapping(target = "displayName", source = "resource.displayName")
    @Mapping(target = "description", source = "resource.description")
    @Mapping(target = "status", source = "resource.lifecycleStatus")
    @Mapping(target = "sourceType", source = "resource.sourceType")
    @Mapping(target = "activeVersionNo", expression = "java(resolveActiveVersionInt(resource, resolvedVersion))")
    @Mapping(target = "digestSha256", expression = "java(resolvedVersion != null ? resolvedVersion.getDigestSha256() : resource.getContentSha256())")
    @Mapping(target = "labels", source = "resource.tags", qualifiedByName = "tagsToLabelsMap")
    @Mapping(target = "references", source = "refs")
    @Mapping(target = "specJson", expression = "java(resolvedVersion != null ? resolvedVersion.getSpecJson() : resource.getSpecJson())")
    @Mapping(target = "publishedAt", expression = "java(resolvedVersion != null ? resolvedVersion.getPublishedTime() : resource.getPublishedTime())")
    @Mapping(target = "updatedAt", source = "resource.updateTime")
    DdcSiResourceDescriptor toDescriptor(
            DdcSiResourceBO resource,
            DdcSiVersionBO resolvedVersion,
            List<DdcSiReferenceBO> refs);

    DdcSiReferenceDescriptor toReferenceDescriptor(DdcSiReferenceBO ref);

    @Mapping(target = "versionId", source = "id")
    @Mapping(target = "versionNo", expression = "java(version.getVersionNo() == null ? null : version.getVersionNo().intValue())")
    @Mapping(target = "status", expression = "java(DdcSiLifecycleStatusEnum.PUBLISHED)")
    @Mapping(target = "changeSummary", source = "changeNote")
    @Mapping(target = "sourceVersionNo", expression = "java(version.getSourceVersionNo() == null ? null : version.getSourceVersionNo().intValue())")
    @Mapping(target = "publishedBy", source = "createUserId")
    @Mapping(target = "publishedAt", source = "publishedTime")
    DdcSiVersionDescriptor toVersionDescriptor(DdcSiVersionBO version);

    @Mapping(target = "bindingId", source = "binding.id")
    @Mapping(target = "namespaceCode", source = "namespaceCode")
    @Mapping(target = "pinnedVersionNo", expression = "java(binding.getPinnedVersionNo() == null ? null : binding.getPinnedVersionNo().intValue())")
    @Mapping(target = "bindingStatus", source = "binding.status")
    @Mapping(target = "boundBy", source = "binding.updateUserId")
    @Mapping(target = "updatedAt", source = "binding.boundTime")
    DdcSiBindingDescriptor toBindingDescriptor(DdcSiBindingBO binding, String namespaceCode);

    @Named("tagsJsonToList")
    default List<String> tagsJsonToList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return SHARED_JSON.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @Named("tagsListToJson")
    default String tagsListToJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        try {
            return SHARED_JSON.writeValueAsString(tags);
        } catch (Exception ex) {
            return "[]";
        }
    }

    @Named("tagsToLabelsMap")
    default Map<String, String> tagsToLabelsMap(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                labels.put(tag, "true");
            }
        }
        return labels;
    }

    default Integer resolveActiveVersionInt(DdcSiResourceBO resource, DdcSiVersionBO resolvedVersion) {
        if (resolvedVersion != null && resolvedVersion.getVersionNo() != null) {
            return resolvedVersion.getVersionNo().intValue();
        }
        if (resource != null && resource.getCurrentVersionNo() != null) {
            return resource.getCurrentVersionNo().intValue();
        }
        return null;
    }

    default Integer longToInt(Long value) {
        return value == null ? null : value.intValue();
    }

    default Long intToLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
