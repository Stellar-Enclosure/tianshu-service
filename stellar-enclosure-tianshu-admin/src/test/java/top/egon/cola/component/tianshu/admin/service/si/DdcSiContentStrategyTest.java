package top.egon.cola.component.tianshu.admin.service.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiMcpSpecBO;
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

    private static final String VERSIONED_STDIO = """
            {
              "schemaVersion": "mcp-server-v2",
              "sourceType": "STDIO_ARTIFACT",
              "protocolVersion": "2025-11-25",
              "transport": "STDIO",
              "command": "/app/weather",
              "args": ["--format", "json"],
              "credentialRef": "vault://si/weather",
              "capabilityFingerprint": "%s",
              "artifact": {"reference": "oci://registry.example.test/mcp/weather", "digestSha256": "%s"},
              "tools": [{
                "toolId": "weather.lookup",
                "name": "lookup",
                "description": "Look up weather",
                "inputSchema": {"type": "object"},
                "outputSchema": {"type": "object", "properties": {"temperature": {"type": "number"}}},
                "requiredPermissions": ["weather:forecast:read"],
                "riskLevel": "LOW",
                "idempotent": true,
                "enabled": false
              }]
            }
            """.formatted("a".repeat(64), "b".repeat(64));

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
    void approvedMcpShapePreservesTypedToolSchemasIdentityAndCommandArguments() throws Exception {
        var normalized = mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, VERSIONED_STDIO);
        var document = objectMapper.readTree(normalized.getNormalizedJson());
        var typed = objectMapper.readValue(normalized.getNormalizedJson(), DdcSiMcpSpecBO.class);
        var projected = objectMapper.valueToTree(typed);

        assertThat(projected.path("protocolVersion")).isEqualTo(document.path("protocolVersion"));
        assertThat(projected.path("args")).isEqualTo(document.path("args"));
        assertThat(projected.path("credentialRef")).isEqualTo(document.path("credentialRef"));
        assertThat(projected.path("capabilityFingerprint")).isEqualTo(document.path("capabilityFingerprint"));
        assertThat(projected.path("artifact")).isEqualTo(document.path("artifact"));
        assertThat(projected.path("tools")).isEqualTo(document.path("tools"));
        assertThat(normalized.getDigestSha256()).isEqualTo(
                mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, normalized.getNormalizedJson()).getDigestSha256());
        assertThat(mcpStrategy.extractSearchableText(DdcSiKindEnum.MCP, normalized.getNormalizedJson(), "Weather", "")).contains("lookup");
    }

    @Test
    void canonicalMcpRejectsMixedSourcesUnknownToolFieldsAndCoercedTypes() {
        for (String invalid : List.of(
                VERSIONED_STDIO.replace("\"command\":", "\"endpoint\":\"https://mcp.example.test/weather\",\"command\":"),
                VERSIONED_STDIO.replace("\"outputSchema\":", "\"unexpectedToolField\":true,\"outputSchema\":"),
                VERSIONED_STDIO.replace("\"enabled\": false", "\"enabled\": \"false\""),
                VERSIONED_STDIO.replace("vault://si/weather", "ordinary-literal-with-no-reference-scheme"),
                VERSIONED_STDIO.replace("b".repeat(64), "unpinned-artifact"),
                VERSIONED_STDIO.replace("2025-11-25", "unrecognized"))) {
            assertThatThrownBy(() -> mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, invalid))
                    .isInstanceOf(CommonException.class)
                    .satisfies(failure -> assertThat(((CommonException) failure).getCode()).isEqualTo(56422));
        }
    }

    @Test
    void mcpServiceEndpointMatchesItsExactBindingWhileRemoteHttpIsRejected() {
        String internal = """
                {
                  "schemaVersion": "mcp-server-v2",
                  "sourceType": "INTERNAL_SERVICE",
                  "protocolVersion": "2025-11-25",
                  "transport": "STREAMABLE_HTTP",
                  "endpoint": "http://weather.tools.svc.cluster.local:8080/mcp",
                  "capabilityFingerprint": "%s",
                  "serviceBinding": {"bindingId": "weather-mcp", "revision": "7",
                                     "serviceName": "weather", "namespace": "tools", "port": 8080},
                  "tools": []
                }
                """.formatted("c".repeat(64));

        assertThat(mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, internal).getDigestSha256()).hasSize(64);
        assertThatThrownBy(() -> mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP,
                internal.replace("weather.tools.svc.cluster.local", "other.tools.svc.cluster.local")))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("Service binding");
        String remote = """
                {"schemaVersion":"mcp-server-v2","sourceType":"REMOTE_HTTPS","protocolVersion":"2025-11-25",
                 "transport":"STREAMABLE_HTTP","endpoint":"http://mcp.example.test/weather","capabilityFingerprint":"%s","tools":[]}
                """.formatted("d".repeat(64));
        assertThatThrownBy(() -> mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, remote))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void composedMcpPinsMappingIdentityAndTypedReferencesWithoutCoercion() throws Exception {
        String composed = """
                {
                  "schemaVersion":"mcp-server-v2","sourceType":"COMPOSED_API",
                  "protocolVersion":"2025-11-25","transport":"STREAMABLE_HTTP",
                  "endpoint":"http://mcp-access.tools.svc.cluster.local/mcp",
                  "capabilityFingerprint":"%s",
                  "serviceBinding":{"bindingId":"mcp-access","revision":"8","serviceName":"mcp-access","namespace":"tools","port":80},
                  "apiMapping":{"gatewayGroupId":"9007199254740991","publicationId":"9007199254740993",
                                "schemaVersion":"yuheng-mcp-mapping-seed-v1","contentSha256":"%s","artifactSha256":"%s"},
                  "dependencies":[{"kind":"PROMPT","key":"weather-prompt","resourceId":"71","versionId":"72","versionNo":"3","digestSha256":"%s"}],
                  "apps":[{"appKey":"weather-ui","resourceUri":"ui://weather/index.html",
                           "artifact":{"reference":"artifact://weather-ui/8","digestSha256":"%s"}}],
                  "resources":[{"uri":"resource://weather/forecast","name":"forecast","mimeType":"application/json","digestSha256":"%s"}],
                  "remoteMounts":[{"mountId":"alerts","providerKey":"alert-service","namespacePrefix":"alerts","capabilityFingerprint":"%s"}],
                  "tools":[]
                }
                """.formatted("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64), "f".repeat(64), "1".repeat(64));

        var normalized = mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, composed);
        var spec = objectMapper.readValue(normalized.getNormalizedJson(), DdcSiMcpSpecBO.class);
        assertThat(spec.getApiMapping().publicationId()).isEqualTo("9007199254740993");
        assertThat(spec.getDependencies()).singleElement().satisfies(reference -> assertThat(reference.versionId()).isEqualTo("72"));
        assertThat(mcpStrategy.extractReferences(DdcSiKindEnum.MCP, normalized.getNormalizedJson()))
                .singleElement().satisfies(reference -> {
                    assertThat(reference.getTargetType()).isEqualTo(DdcSiReferenceTargetTypeEnum.PROMPT);
                    assertThat(reference.getTargetKey()).isEqualTo("weather-prompt");
                    assertThat(reference.getPinnedVersionNo()).isEqualTo(3L);
                    assertThat(reference.getExpectedIdentity()).isEqualTo(new DdcSiReferenceBO.ExpectedIdentityVO(
                            71L, 72L, 3L, "d".repeat(64), false, null));
                });
        String businessPrompt = composed.replace("\"kind\":\"PROMPT\"", "\"ownerAppCode\":null,\"kind\":\"PROMPT\"");
        var businessReference = mcpStrategy.extractReferences(DdcSiKindEnum.MCP,
                mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP, businessPrompt).getNormalizedJson()).getFirst();
        assertThat(businessReference.getExpectedIdentity().ownerPresent()).isTrue();
        assertThat(businessReference.getExpectedIdentity().ownerAppCode()).isNull();
        assertThat(spec.getApps()).singleElement().satisfies(app -> assertThat(app.artifact().reference()).isEqualTo("artifact://weather-ui/8"));
        assertThat(spec.getResources()).singleElement().satisfies(resource -> assertThat(resource.digestSha256()).isEqualTo("f".repeat(64)));
        assertThat(spec.getRemoteMounts()).singleElement().satisfies(mount -> assertThat(mount.capabilityFingerprint()).isEqualTo("1".repeat(64)));
        assertThatThrownBy(() -> mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP,
                composed.replace("\"9007199254740993\"", "9007199254740993")))
                .isInstanceOf(CommonException.class).hasMessage("invalid versioned MCP specJson structure");
        var publication = mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP,
                composed.replace("yuheng-mcp-mapping-seed-v1", "yuheng-mcp-mapping-publication-v1"));
        assertThat(publication.getDigestSha256()).isNotEqualTo(normalized.getDigestSha256());
        assertThatThrownBy(() -> mcpStrategy.validateAndNormalize(DdcSiKindEnum.MCP,
                composed.replace("yuheng-mcp-mapping-seed-v1", "mcp-api-mapping-v1")))
                .isInstanceOf(CommonException.class).hasMessageContaining("approved API mapping publication");
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
