package top.egon.cola.component.tianshu.admin.service.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiAgentContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy.NormalizedContent;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiMcpContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiPromptSkillContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST-004：验证四类 SI 资源策略的规范化摘要一致性、原子 Agent 禁流程编排检查、
 * JSON Schema 校验以及 MCP 明文凭据泄露拦截。
 */
class DdcSiContentStrategyTest {

    private ObjectMapper objectMapper;
    private DdcSiAgentContentStrategy agentStrategy;
    private DdcSiMcpContentStrategy mcpStrategy;
    private DdcSiPromptSkillContentStrategy promptSkillStrategy;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        agentStrategy = new DdcSiAgentContentStrategy(objectMapper);
        mcpStrategy = new DdcSiMcpContentStrategy(objectMapper);
        promptSkillStrategy = new DdcSiPromptSkillContentStrategy(objectMapper);
    }

    @Test
    void agentSpecProducesDeterministicSha256AndExtractsReferencesAndSearchableText() {
        String specOrderA = """
                {
                  "modelKey": "gpt-6-luna",
                  "runtime": {"maxSteps": 8, "timeoutSeconds": 60, "temperature": 0.2},
                  "promptKeys": ["planner-sys"],
                  "skillKeys": ["code-audit"],
                  "mcpServerKeys": ["github-mcp"],
                  "subAgentKeys": ["critic-agent"],
                  "inputSchema": {"type": "object", "properties": {"goal": {"type": "string"}}},
                  "outputSchema": {"type": "object", "properties": {"answer": {"type": "string"}}}
                }
                """;
        String specOrderB = """
                {
                  "outputSchema": {"properties": {"answer": {"type": "string"}}, "type": "object"},
                  "inputSchema": {"properties": {"goal": {"type": "string"}}, "type": "object"},
                  "subAgentKeys": ["critic-agent"],
                  "mcpServerKeys": ["github-mcp"],
                  "skillKeys": ["code-audit"],
                  "promptKeys": ["planner-sys"],
                  "runtime": {"temperature": 0.2, "timeoutSeconds": 60, "maxSteps": 8},
                  "modelKey": "gpt-6-luna"
                }
                """;

        NormalizedContent first = agentStrategy.validateAndNormalize(DdcSiKindEnum.AGENT, specOrderA);
        NormalizedContent second = agentStrategy.validateAndNormalize(DdcSiKindEnum.AGENT, specOrderB);

        assertThat(first.getDigestSha256()).hasSize(64).isEqualTo(second.getDigestSha256());
        List<DdcSiReferenceBO> refs = agentStrategy.extractReferences(DdcSiKindEnum.AGENT, first.getNormalizedJson());
        assertThat(refs).extracting(DdcSiReferenceBO::getTargetType)
                .containsExactlyInAnyOrder(
                        DdcSiReferenceTargetTypeEnum.PROMPT,
                        DdcSiReferenceTargetTypeEnum.SKILL,
                        DdcSiReferenceTargetTypeEnum.MCP,
                        DdcSiReferenceTargetTypeEnum.AGENT);
        assertThat(agentStrategy.extractSearchableText(DdcSiKindEnum.AGENT, first.getNormalizedJson(), "Planner", "desc"))
                .contains("gpt-6-luna", "github-mcp", "Planner");
    }

    @Test
    void agentStrategyRejectsStaticWorkflowOrchestrationFieldsAndInvalidSchemas() {
        for (String forbiddenKey : List.of("nodes", "edges", "bpmnXml", "transitions", "flowable")) {
            String badJson = """
                    {
                      "modelKey": "gpt-6-luna",
                      "%s": []
                    }
                    """.formatted(forbiddenKey);
            assertThatThrownBy(() -> agentStrategy.validateAndNormalize(DdcSiKindEnum.AGENT, badJson))
                    .isInstanceOf(CommonException.class)
                    .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56422))
                    .hasMessageContaining(forbiddenKey);
        }

        String badSchemaJson = """
                {
                  "modelKey": "gpt-6-luna",
                  "inputSchema": {"type": "not-a-json-schema-type"}
                }
                """;
        assertThatThrownBy(() -> agentStrategy.validateAndNormalize(DdcSiKindEnum.AGENT, badSchemaJson))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56422));
    }

    @Test
    void mcpStrategyValidatesTransportAndRejectsPlaintextSecrets() {
        String validSse = """
                {
                  "protocolVersion": "2025-03-26",
                  "transport": "SSE",
                  "endpoint": "https://mcp.example.com/sse",
                  "credentialRef": "vault://si/github-token",
                  "tools": [{"name": "search_issues", "description": "Search GitHub issues"}]
                }
                """;
        NormalizedContent ok = mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, validSse);
        assertThat(ok.getDigestSha256()).hasSize(64);
        assertThat(mcpStrategy.extractSearchableText(DdcSiKindEnum.MCP, ok.getNormalizedJson(), "GitHub", "tools"))
                .contains("search_issues", "SSE")
                .doesNotContain("vault://");

        String missingStdioCommand = """
                {
                  "protocolVersion": "2025-03-26",
                  "transport": "STDIO"
                }
                """;
        assertThatThrownBy(() -> mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, missingStdioCommand))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56422));

        String secretLeakJson = """
                {
                  "protocolVersion": "2025-03-26",
                  "transport": "SSE",
                  "endpoint": "https://mcp.example.com/sse",
                  "headers": {"Authorization": "Bearer supersecret-token-value"}
                }
                """;
        assertThatThrownBy(() -> mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, secretLeakJson))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56422))
                .hasMessageNotContaining("supersecret-token-value");
    }

    @Test
    void promptAndSkillStrategyValidatesPlaceholdersAndExtractsText() {
        String validPrompt = "{\"promptText\":\"You are {{role}}, help {{user}}.\"}";
        NormalizedContent ok = promptSkillStrategy.validateAndNormalize(DdcSiKindEnum.PROMPT, validPrompt);
        assertThat(ok.getDigestSha256()).hasSize(64);
        assertThat(promptSkillStrategy.extractSearchableText(DdcSiKindEnum.PROMPT, ok.getNormalizedJson(), "Helper", ""))
                .contains("Helper", "role");

        String unbalancedPrompt = "{\"promptText\":\"Broken {{role placeholder\"}";
        assertThatThrownBy(() -> promptSkillStrategy.validateAndNormalize(DdcSiKindEnum.PROMPT, unbalancedPrompt))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56422));

        String validSkill = "{\"kind\":\"SKILL\",\"summary\":\"PDF extraction skill\",\"entrypoint\":\"SKILL.md\"}";
        NormalizedContent okSkill = promptSkillStrategy.validateAndNormalize(DdcSiKindEnum.SKILL, validSkill);
        assertThat(promptSkillStrategy.extractSearchableText(DdcSiKindEnum.SKILL, okSkill.getNormalizedJson(), "PDF", ""))
                .contains("PDF extraction skill");
    }
}
