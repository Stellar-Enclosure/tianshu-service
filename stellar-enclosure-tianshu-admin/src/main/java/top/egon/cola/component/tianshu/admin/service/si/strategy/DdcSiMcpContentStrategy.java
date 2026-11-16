package top.egon.cola.component.tianshu.admin.service.si.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiMcpSpecBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 标准 MCP Server 规格校验策略：校验传输与端点合法性并拦截明文凭据泄露。 */
@Slf4j
@Component("ddcSiMcpContentStrategy")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiMcpContentStrategy implements DdcSiContentStrategy {

    private static final Set<String> VALID_TRANSPORTS = Set.of("STDIO", "SSE", "STREAMABLE_HTTP");
    private static final Pattern PLAINTEXT_SECRET_VALUE = Pattern.compile(
            "(?i)(sk-[A-Za-z0-9_-]{8,}|bearer\\s+[A-Za-z0-9._-]{6,}|ghp_[A-Za-z0-9]{10,})");
    private static final Set<String> SENSITIVE_KEY_HINTS = Set.of(
            "password", "secret", "apikey", "api_key", "token", "authorization");

    @Qualifier("jacksonObjectMapper")
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(DdcSiKindEnum kind) {
        return kind == DdcSiKindEnum.MCP;
    }

    @Override
    public NormalizedContent validateAndNormalize(DdcSiKindEnum kind, String rawSpecJson) {
        if (rawSpecJson == null || rawSpecJson.isBlank()) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID",
                    "mcp specJson must not be blank");
        }
        JsonNode root;
        DdcSiMcpSpecBO spec;
        try {
            root = objectMapper.readTree(rawSpecJson);
            scanForPlaintextSecrets(root, "$");
            spec = objectMapper.treeToValue(root, DdcSiMcpSpecBO.class);
        } catch (CommonException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID",
                    "invalid MCP specJson structure", failure);
        }

        if (spec == null || spec.getProtocolVersion() == null || spec.getProtocolVersion().isBlank()) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID",
                    "$.protocolVersion is required");
        }
        String transport = spec.getTransport() == null ? "" : spec.getTransport().trim().toUpperCase(Locale.ROOT);
        if (!VALID_TRANSPORTS.contains(transport)) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID",
                    "$.transport must be STDIO, SSE or STREAMABLE_HTTP");
        }
        if ("STDIO".equals(transport)) {
            if (spec.getCommand() == null || spec.getCommand().isBlank()) {
                throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID",
                        "$.command is required when transport=STDIO");
            }
        } else {
            validateHttpEndpoint(spec.getEndpoint());
        }
        return canonicalizeJson(objectMapper, root);
    }

    @Override
    public List<DdcSiReferenceBO> extractReferences(DdcSiKindEnum kind, String normalizedSpecJson) {
        return List.of();
    }

    @Override
    public String extractSearchableText(DdcSiKindEnum kind, String normalizedSpecJson, String displayName, String description) {
        try {
            DdcSiMcpSpecBO spec = objectMapper.readValue(normalizedSpecJson, DdcSiMcpSpecBO.class);
            StringBuilder sb = new StringBuilder();
            appendToken(sb, displayName);
            appendToken(sb, description);
            appendToken(sb, spec.getTransport());
            appendToken(sb, spec.getProtocolVersion());
            if (spec.getTools() != null) {
                for (DdcSiMcpSpecBO.McpToolItem tool : spec.getTools()) {
                    if (tool != null) {
                        appendToken(sb, tool.getName());
                        appendToken(sb, tool.getDescription());
                    }
                }
            }
            return sb.toString().trim();
        } catch (Exception ignored) {
            return (displayName == null ? "" : displayName) + " " + (description == null ? "" : description);
        }
    }

    private void scanForPlaintextSecrets(JsonNode node, String path) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            var it = node.fields();
            while (it.hasNext()) {
                var entry = it.next();
                String key = entry.getKey();
                String childPath = path + "." + key;
                JsonNode val = entry.getValue();
                if (val.isTextual() && !"credentialRef".equals(key)) {
                    String text = val.asText();
                    if (PLAINTEXT_SECRET_VALUE.matcher(text).find()) {
                        log.warn("Rejected plaintext credential leak in MCP spec path={}", childPath);
                        throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SECRET_LEAK",
                                "plaintext secret value forbidden at " + childPath + "; use credentialRef");
                    }
                    String normalizedKey = key.toLowerCase(Locale.ROOT);
                    if (SENSITIVE_KEY_HINTS.stream().anyMatch(normalizedKey::contains) && !isSafeReference(text)) {
                        log.warn("Rejected unsafe sensitive field value in MCP spec path={}", childPath);
                        throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SECRET_LEAK",
                                "sensitive field requires vault:// or ${ENV_VAR} reference at " + childPath);
                    }
                }
                scanForPlaintextSecrets(val, childPath);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode elem = node.get(i);
                if (elem.isTextual() && PLAINTEXT_SECRET_VALUE.matcher(elem.asText()).find()) {
                    throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SECRET_LEAK",
                            "plaintext secret value forbidden at " + path + "[" + i + "]");
                }
                scanForPlaintextSecrets(elem, path + "[" + i + "]");
            }
        }
    }

    private static boolean isSafeReference(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty()
                || trimmed.startsWith("vault://")
                || trimmed.startsWith("secret://")
                || (trimmed.startsWith("${") && trimmed.endsWith("}"));
    }

    private static void validateHttpEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID",
                    "$.endpoint is required for HTTP/SSE transport");
        }
        try {
            URI uri = URI.create(endpoint.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("invalid endpoint URI");
            }
        } catch (Exception ex) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID",
                    "$.endpoint must be a valid http(s) URL");
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
