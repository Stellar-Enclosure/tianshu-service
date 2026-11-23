package top.egon.cola.component.tianshu.admin.model.convertor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPO;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository.PageRow;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogDetailVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogSummaryVO;
import top.egon.cola.component.tianshu.admin.outbox.DdcOperationLogOutboxAdapter;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Separate generated projections prevent a list query from loading the detail persistence model. */
public interface DdcOperationLogViewConverter {
    @Mapper(componentModel = "spring", implementationName = "DdcOperationLogSummaryViewConverterImpl",
            unmappedTargetPolicy = ReportingPolicy.ERROR)
    interface Summary extends BaseForwardConverter<PageRow, DdcOperationLogSummaryVO> {
        @Override
        @BeanMapping(ignoreByDefault = true)
        @Mapping(target = "eventId", source = "id")
        @Mapping(target = "operationId", source = "operationId")
        @Mapping(target = "occurredAt", source = "occurredAt")
        @Mapping(target = "sourceService", source = "sourceService")
        @Mapping(target = "moduleCode", source = "moduleCode")
        @Mapping(target = "actionCode", source = "actionCode")
        @Mapping(target = "resourceType", source = "resourceType")
        @Mapping(target = "resourceId", source = "resourceId")
        @Mapping(target = "actorType", source = "actorType")
        @Mapping(target = "actorId", source = "actorId")
        @Mapping(target = "actorName", source = "actorName")
        @Mapping(target = "phase", source = "phase")
        @Mapping(target = "outcome", source = "outcome")
        @Mapping(target = "routePattern", source = "routePattern")
        DdcOperationLogSummaryVO toTarget(PageRow source);
    }

    @Mapper(componentModel = "spring", implementationName = "DdcOperationLogDetailViewConverterImpl",
            unmappedTargetPolicy = ReportingPolicy.ERROR)
    interface Detail extends BaseForwardConverter<OperationLogPO, DdcOperationLogDetailVO> {
        @Override
        @BeanMapping(ignoreByDefault = true)
        @Mapping(target = "eventId", source = "id")
        @Mapping(target = "operationId", source = "operationId")
        @Mapping(target = "schemaVersion", source = "schemaVersion")
        @Mapping(target = "sourceService", source = "sourceService")
        @Mapping(target = "moduleCode", source = "moduleCode")
        @Mapping(target = "occurredAt", source = "occurredAt")
        @Mapping(target = "recordedAt", source = "createTime")
        @Mapping(target = "actionCode", source = "actionCode")
        @Mapping(target = "resourceType", source = "resourceType")
        @Mapping(target = "resourceId", source = "resourceId")
        @Mapping(target = "bizCode", source = "bizCode")
        @Mapping(target = "namespaceCode", source = "namespaceCode")
        @Mapping(target = "env", source = "env")
        @Mapping(target = "appCode", source = "appCode")
        @Mapping(target = "actorType", source = "actorType")
        @Mapping(target = "actorId", source = "actorId")
        @Mapping(target = "actorName", source = "actorName")
        @Mapping(target = "triggerType", source = "triggerType")
        @Mapping(target = "phase", source = "phase")
        @Mapping(target = "outcome", source = "outcome")
        @Mapping(target = "reasonCode", source = "reasonCode")
        @Mapping(target = "httpMethod", source = "httpMethod")
        @Mapping(target = "requestUrl", source = "requestUrl")
        @Mapping(target = "routePattern", source = "routePattern")
        @Mapping(target = "handlerMethod", source = "handlerMethod")
        @Mapping(target = "traceId", source = "traceId")
        @Mapping(target = "requestId", source = "requestId")
        @Mapping(target = "durationMs", source = "durationMs")
        @Mapping(target = "details", source = ".", qualifiedByName = "registeredDetails")
        DdcOperationLogDetailVO toTarget(OperationLogPO source);

        /** Schema/action fences apply on read even when a legacy or corrupt row contains additional JSON. */
        @Named("registeredDetails")
        default ObjectNode registeredDetails(OperationLogPO source) {
            ObjectNode projected = JsonNodeFactory.instance.objectNode();
            if (source == null || !Integer.valueOf(1).equals(source.getSchemaVersion()) || source.getDetails() == null) return projected;
            var action = DdcOperationLogOutboxAdapter.actions().get(source.getActionCode());
            if (action == null || !action.moduleCode().equals(source.getModuleCode())
                    || !action.resourceType().equals(source.getResourceType())) return projected;
            for (String field : action.detailFields()) {
                JsonNode value = source.getDetails().get(field);
                if (value == null || value.isNull()) continue;
                if (action.integerFields().contains(field)) {
                    if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0
                            || ("attempt".equals(field) && value.intValue() < 1)) continue;
                } else if (!safeText(field, value)) continue;
                projected.set(field, value.deepCopy());
            }
            return projected.toString().getBytes(StandardCharsets.UTF_8).length <= 4096
                    ? projected : JsonNodeFactory.instance.objectNode();
        }

        private static boolean safeText(String field, JsonNode value) {
            if (!value.isTextual() || value.textValue().isBlank()) return false;
            String text = value.textValue();
            if (Set.of("publishStatus", "publishStage").contains(field)) {
                return Set.of("SUCCESS", "PLATFORM_APPLIED", "FAILED", "TIMEOUT", "SUPERSEDED", "PARTIAL_SUCCESS", "UNKNOWN").contains(text);
            }
            if ("range".equals(field)) return Set.of("PAGE", "FILTERED").contains(text);
            if ("type".equals(field)) return Set.of("PERSONAL", "SYSTEM").contains(text);
            if (Set.of("revision", "targetRevision", "pageId", "publishedVersion", "templateId", "version", "catalogScopeId", "changeId").contains(field)) {
                boolean zeroAllowed = Set.of("revision", "targetRevision", "publishedVersion", "version").contains(field);
                if (!text.matches(zeroAllowed ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}")) return false;
                try { Long.parseLong(text); } catch (NumberFormatException invalid) { return false; }
            }
            return true;
        }
    }
}
