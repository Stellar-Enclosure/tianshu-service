package top.egon.cola.component.tianshu.admin.service.si.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiAgentSpecBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 原子化 Agent 定义校验策略：禁止编排图字段，校验运行时限制、IO Schema 与引用边。 */
@Slf4j
@Component("ddcSiAgentContentStrategy")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiAgentContentStrategy implements DdcSiContentStrategy {

    private static final Set<String> FORBIDDEN_ORCHESTRATION_KEYS = Set.of(
            "nodes", "edges", "bpmnxml", "transitions", "flowable");
    private static final Set<String> VALID_JSON_SCHEMA_TYPES = Set.of(
            "object", "array", "string", "number", "integer", "boolean", "null");

    @Qualifier("jacksonObjectMapper")
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(DdcSiKindEnum kind) {
        return kind == DdcSiKindEnum.AGENT;
    }

    @Override
    public NormalizedContent validateAndNormalize(DdcSiKindEnum kind, String rawSpecJson) {
        if (rawSpecJson == null || rawSpecJson.isBlank()) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SPEC_INVALID",
                    "agent specJson must not be blank");
        }
        JsonNode root;
        DdcSiAgentSpecBO spec;
        try {
            root = objectMapper.readTree(rawSpecJson);
            assertNoOrchestrationKeys(root, "$");
            spec = objectMapper.treeToValue(root, DdcSiAgentSpecBO.class);
        } catch (CommonException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SPEC_INVALID",
                    "invalid atomic agent specJson structure", failure);
        }

        if (spec == null || spec.getModelKey() == null || spec.getModelKey().isBlank()) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SPEC_INVALID",
                    "$.modelKey must not be blank");
        }
        if (spec.getRuntime() != null) {
            var rt = spec.getRuntime();
            if (rt.getMaxSteps() != null && (rt.getMaxSteps() < 1 || rt.getMaxSteps() > 100)) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SPEC_INVALID",
                        "$.runtime.maxSteps must be between 1 and 100");
            }
            if (rt.getTimeoutSeconds() != null && (rt.getTimeoutSeconds() < 1 || rt.getTimeoutSeconds() > 3600)) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SPEC_INVALID",
                        "$.runtime.timeoutSeconds must be between 1 and 3600");
            }
            if (rt.getTemperature() != null && (rt.getTemperature() < 0.0d || rt.getTemperature() > 2.0d)) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SPEC_INVALID",
                        "$.runtime.temperature must be between 0.0 and 2.0");
            }
        }
        validateSchemaObject("$.inputSchema", spec.getInputSchema());
        validateSchemaObject("$.outputSchema", spec.getOutputSchema());
        return canonicalizeJson(objectMapper, root);
    }

    @Override
    public List<DdcSiReferenceBO> extractReferences(DdcSiKindEnum kind, String normalizedSpecJson) {
        try {
            DdcSiAgentSpecBO spec = objectMapper.readValue(normalizedSpecJson, DdcSiAgentSpecBO.class);
            List<DdcSiReferenceBO> refs = new ArrayList<>();
            appendRefs(refs, spec.getPromptKeys(), DdcSiReferenceTargetTypeEnum.PROMPT, DdcSiKindEnum.PROMPT);
            appendRefs(refs, spec.getSkillKeys(), DdcSiReferenceTargetTypeEnum.SKILL, DdcSiKindEnum.SKILL);
            appendRefs(refs, spec.getMcpServerKeys(), DdcSiReferenceTargetTypeEnum.MCP, DdcSiKindEnum.MCP);
            appendRefs(refs, spec.getSubAgentKeys(), DdcSiReferenceTargetTypeEnum.AGENT, DdcSiKindEnum.AGENT);
            return refs;
        } catch (Exception failure) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SPEC_INVALID",
                    "failed to extract references from agent spec", failure);
        }
    }

    @Override
    public String extractSearchableText(DdcSiKindEnum kind, String normalizedSpecJson, String displayName, String description) {
        try {
            DdcSiAgentSpecBO spec = objectMapper.readValue(normalizedSpecJson, DdcSiAgentSpecBO.class);
            StringBuilder sb = new StringBuilder();
            appendToken(sb, displayName);
            appendToken(sb, description);
            appendToken(sb, spec.getModelKey());
            appendToken(sb, spec.getSystemInstruction());
            spec.getCapabilities().forEach(item -> appendToken(sb, item));
            spec.getPromptKeys().forEach(item -> appendToken(sb, item));
            spec.getSkillKeys().forEach(item -> appendToken(sb, item));
            spec.getMcpServerKeys().forEach(item -> appendToken(sb, item));
            spec.getSubAgentKeys().forEach(item -> appendToken(sb, item));
            return sb.toString().trim();
        } catch (Exception ignored) {
            return (displayName == null ? "" : displayName) + " " + (description == null ? "" : description);
        }
    }

    private void assertNoOrchestrationKeys(JsonNode node, String path) {
        if (node == null || !node.isContainerNode()) {
            return;
        }
        if (node.isObject()) {
            var it = node.fields();
            while (it.hasNext()) {
                var entry = it.next();
                String childPath = path + "." + entry.getKey();
                if (FORBIDDEN_ORCHESTRATION_KEYS.contains(entry.getKey().toLowerCase())) {
                    throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_ORCHESTRATION_FORBIDDEN",
                            "workflow orchestration field forbidden in atomic Agent at " + childPath + " (key=" + entry.getKey() + ")");
                }
                assertNoOrchestrationKeys(entry.getValue(), childPath);
            }
        } else {
            for (int i = 0; i < node.size(); i++) {
                assertNoOrchestrationKeys(node.get(i), path + "[" + i + "]");
            }
        }
    }

    private static void validateSchemaObject(String path, Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Object typeObj = schema.get("type");
        if (typeObj != null && (!(typeObj instanceof String typeStr)
                || !VALID_JSON_SCHEMA_TYPES.contains(typeStr))) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_AGENT_SCHEMA_INVALID",
                    "invalid JSON schema type at " + path + ".type");
        }
    }

    private static void appendRefs(List<DdcSiReferenceBO> out, List<String> keys,
                                   DdcSiReferenceTargetTypeEnum refType, DdcSiKindEnum targetKind) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                out.add(DdcSiReferenceBO.builder()
                        .targetType(refType)
                        .targetKind(targetKind)
                        .targetKey(key.trim())
                        .required(true)
                        .build());
            }
        }
    }

    private static void appendToken(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(value.trim());
        }
    }
}
