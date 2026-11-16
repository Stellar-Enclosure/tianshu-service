package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import top.egon.cola.component.common.core.converter.BaseConverter;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiKindPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiBindingStatusPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiLifecycleStatusPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiReferenceTargetTypePersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.enums.DdcSiSourceTypePersistenceEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

/**
 * SI 五组枚举双向 MapStruct 转换器（独立新建，绝对隔离并保护既有 {@link DdcAiEnumConverter}）。
 */
@Mapper(componentModel = "spring")
public interface DdcSiEnumConverter extends BaseConverter<DdcAiKindPersistenceEnum, DdcSiKindEnum> {

    @Override
    @ValueMapping(source = "AGENT", target = "AGENT")
    @ValueMapping(source = "MCP", target = "MCP")
    @ValueMapping(source = "PROMPT", target = "PROMPT")
    @ValueMapping(source = "SKILL", target = "SKILL")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiKindEnum toTarget(DdcAiKindPersistenceEnum source);

    @Override
    @ValueMapping(source = "AGENT", target = "AGENT")
    @ValueMapping(source = "MCP", target = "MCP")
    @ValueMapping(source = "PROMPT", target = "PROMPT")
    @ValueMapping(source = "SKILL", target = "SKILL")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcAiKindPersistenceEnum toSource(DdcSiKindEnum target);

    @ValueMapping(source = "DRAFT", target = "DRAFT")
    @ValueMapping(source = "PUBLISHED", target = "PUBLISHED")
    @ValueMapping(source = "DEPRECATED", target = "DEPRECATED")
    @ValueMapping(source = "ARCHIVED", target = "ARCHIVED")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiLifecycleStatusEnum toContractLifecycle(DdcSiLifecycleStatusPersistenceEnum source);

    @ValueMapping(source = "DRAFT", target = "DRAFT")
    @ValueMapping(source = "PUBLISHED", target = "PUBLISHED")
    @ValueMapping(source = "DEPRECATED", target = "DEPRECATED")
    @ValueMapping(source = "ARCHIVED", target = "ARCHIVED")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiLifecycleStatusPersistenceEnum toPersistenceLifecycle(DdcSiLifecycleStatusEnum target);

    @ValueMapping(source = "ACTIVE", target = "ACTIVE")
    @ValueMapping(source = "INACTIVE", target = "INACTIVE")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiBindingStatusEnum toContractBindingStatus(DdcSiBindingStatusPersistenceEnum source);

    @ValueMapping(source = "ACTIVE", target = "ACTIVE")
    @ValueMapping(source = "INACTIVE", target = "INACTIVE")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiBindingStatusPersistenceEnum toPersistenceBindingStatus(DdcSiBindingStatusEnum target);

    @ValueMapping(source = "CONSOLE", target = "CONSOLE")
    @ValueMapping(source = "AUTO_REPORTED", target = "AUTO_REPORTED")
    @ValueMapping(source = "GITOPS", target = "GITOPS")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiSourceTypeEnum toContractSourceType(DdcSiSourceTypePersistenceEnum source);

    @ValueMapping(source = "CONSOLE", target = "CONSOLE")
    @ValueMapping(source = "AUTO_REPORTED", target = "AUTO_REPORTED")
    @ValueMapping(source = "GITOPS", target = "GITOPS")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiSourceTypePersistenceEnum toPersistenceSourceType(DdcSiSourceTypeEnum target);

    @ValueMapping(source = "AGENT", target = "AGENT")
    @ValueMapping(source = "MCP", target = "MCP")
    @ValueMapping(source = "PROMPT", target = "PROMPT")
    @ValueMapping(source = "SKILL", target = "SKILL")
    @ValueMapping(source = "TIANJI_PROCESS", target = "TIANJI_PROCESS")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiReferenceTargetTypeEnum toContractReferenceType(DdcSiReferenceTargetTypePersistenceEnum source);

    @ValueMapping(source = "AGENT", target = "AGENT")
    @ValueMapping(source = "MCP", target = "MCP")
    @ValueMapping(source = "PROMPT", target = "PROMPT")
    @ValueMapping(source = "SKILL", target = "SKILL")
    @ValueMapping(source = "TIANJI_PROCESS", target = "TIANJI_PROCESS")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiReferenceTargetTypePersistenceEnum toPersistenceReferenceType(DdcSiReferenceTargetTypeEnum target);

    @ValueMapping(source = "AGENT", target = "AGENT")
    @ValueMapping(source = "MCP", target = "MCP")
    @ValueMapping(source = "PROMPT", target = "PROMPT")
    @ValueMapping(source = "SKILL", target = "SKILL")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcSiReferenceTargetTypeEnum kindToReferenceType(DdcAiKindPersistenceEnum source);

    @ValueMapping(source = "AGENT", target = "AGENT")
    @ValueMapping(source = "MCP", target = "MCP")
    @ValueMapping(source = "PROMPT", target = "PROMPT")
    @ValueMapping(source = "SKILL", target = "SKILL")
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    DdcAiKindPersistenceEnum referenceTypeToKind(DdcSiReferenceTargetTypeEnum source);

    default DdcAiKindPersistenceEnum contractReferenceTypeToKind(DdcSiReferenceTargetTypeEnum source) {
        if (source == null || source == DdcSiReferenceTargetTypeEnum.TIANJI_PROCESS) {
            return null;
        }
        return referenceTypeToKind(source);
    }
}
