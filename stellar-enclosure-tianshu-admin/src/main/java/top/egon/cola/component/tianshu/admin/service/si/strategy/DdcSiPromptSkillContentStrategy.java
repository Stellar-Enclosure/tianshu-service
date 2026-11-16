package top.egon.cola.component.tianshu.admin.service.si.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Prompt 与 Skill 资源兼容性内容校验及语义文本摘要策略。 */
@Slf4j
@Component("ddcSiPromptSkillContentStrategy")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiPromptSkillContentStrategy implements DdcSiContentStrategy {

    private static final int MAX_SPEC_BYTES = 262_144;

    @Qualifier("jacksonObjectMapper")
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(DdcSiKindEnum kind) {
        return kind == DdcSiKindEnum.PROMPT || kind == DdcSiKindEnum.SKILL;
    }

    @Override
    public NormalizedContent validateAndNormalize(DdcSiKindEnum kind, String rawSpecJson) {
        if (rawSpecJson == null || rawSpecJson.isBlank()) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_CONTENT_INVALID",
                    kind + " specJson must not be blank");
        }
        if (rawSpecJson.getBytes(StandardCharsets.UTF_8).length > MAX_SPEC_BYTES) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_CONTENT_INVALID",
                    kind + " specJson exceeds max bytes limit");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawSpecJson);
        } catch (Exception failure) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_CONTENT_INVALID",
                    kind + " specJson is not valid JSON", failure);
        }
        if (kind == DdcSiKindEnum.PROMPT) {
            String promptText = root.path("promptText").asText(null);
            if (promptText == null || promptText.isBlank()) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_PROMPT_INVALID",
                        "$.promptText must not be blank");
            }
            assertBalancedPlaceholders(promptText);
        } else if (!root.isObject() || root.isEmpty()) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_SKILL_INVALID",
                    "skill specJson must be a non-empty JSON object");
        }
        return canonicalizeJson(objectMapper, root);
    }

    @Override
    public List<DdcSiReferenceBO> extractReferences(DdcSiKindEnum kind, String normalizedSpecJson) {
        return List.of();
    }

    @Override
    public String extractSearchableText(DdcSiKindEnum kind, String normalizedSpecJson, String displayName, String description) {
        StringBuilder sb = new StringBuilder();
        if (displayName != null && !displayName.isBlank()) {
            sb.append(displayName.trim()).append(' ');
        }
        if (description != null && !description.isBlank()) {
            sb.append(description.trim()).append(' ');
        }
        try {
            JsonNode root = objectMapper.readTree(normalizedSpecJson);
            root.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    sb.append(entry.getValue().asText()).append(' ');
                }
            });
        } catch (Exception ignored) {
            // Fall back to metadata text only
        }
        return sb.toString().trim();
    }

    private static void assertBalancedPlaceholders(String promptText) {
        int index = 0;
        while (index < promptText.length()) {
            int open = promptText.indexOf("{{", index);
            int close = promptText.indexOf("}}", index);
            if (open < 0 && close < 0) {
                break;
            }
            if (close >= 0 && (open < 0 || close < open)) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_PROMPT_INVALID",
                        "unmatched closing placeholder '}}' in $.promptText");
            }
            int matchingClose = promptText.indexOf("}}", open + 2);
            if (matchingClose < 0) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_PROMPT_INVALID",
                        "unclosed placeholder '{{' in $.promptText");
            }
            String token = promptText.substring(open + 2, matchingClose).trim();
            if (token.isEmpty() || token.contains("{{")) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_PROMPT_INVALID",
                        "malformed placeholder in $.promptText");
            }
            index = matchingClose + 2;
        }
    }
}
