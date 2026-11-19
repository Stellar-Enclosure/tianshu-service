package top.egon.cola.component.tianshu.admin.service.si.strategy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiMcpSpecBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiMcpSpecBO.ArtifactReferenceVO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiMcpSpecBO.SourceTypeEnum;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO.ExpectedIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 标准 MCP Server 规格校验策略：校验传输与端点合法性并拦截明文凭据泄露。 */
@Slf4j
@Component("ddcSiMcpContentStrategy")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiMcpContentStrategy implements DdcSiContentStrategy {

    public static final String SCHEMA_VERSION = "mcp-server-v2";
    private static final Set<String> VALID_TRANSPORTS = Set.of("STDIO", "SSE", "STREAMABLE_HTTP");
    private static final Set<String> HTTP_PROTOCOL_VERSIONS = Set.of("2025-11-25", "2026-07-28");
    private static final Set<String> API_MAPPING_SCHEMAS = Set.of(
            "yuheng-mcp-mapping-publication-v1", "yuheng-mcp-mapping-seed-v1");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STABLE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern PERMISSION = Pattern.compile("[a-z][a-z0-9._-]*(?::[A-Za-z0-9._*-]+)+");
    private static final Pattern CREDENTIAL_REFERENCE =
            Pattern.compile("(?:vault|secret)://[A-Za-z0-9][A-Za-z0-9._:/-]{0,509}|\\$\\{[A-Z_][A-Z0-9_]{0,127}}");
    private static final Pattern EXECUTABLE_PATH = Pattern.compile("/?[A-Za-z0-9_][A-Za-z0-9._/-]{0,254}");
    private static final Set<String> VERSIONED_FIELDS = Set.of(
            "sourceType", "capabilityFingerprint", "serviceBinding", "artifact", "apiMapping",
            "dependencies", "resources", "apps", "remoteMounts");
    private static final Set<String> TOOL_FIELDS = Set.of(
            "toolId", "name", "description", "inputSchema", "outputSchema", "requiredPermissions",
            "riskLevel", "idempotent", "enabled");
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
        require(rawSpecJson != null && !rawSpecJson.isBlank(), "mcp specJson must not be blank");
        JsonNode root = parse(rawSpecJson);
        scanForPlaintextSecrets(root, "$");
        if (root.has("schemaVersion")) {
            validateVersioned(root);
        } else {
            require(VERSIONED_FIELDS.stream().noneMatch(root::has), "MCP schemaVersion is required for versioned fields");
            validateLegacy(root);
        }
        return canonicalizeJson(objectMapper, root);
    }

    private JsonNode parse(String json) {
        try {
            JsonNode root = objectMapper.reader()
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
            require(root != null && root.isObject(), "mcp specJson must be an object");
            return root;
        } catch (JsonProcessingException invalid) {
            throw invalid("invalid MCP specJson structure");
        }
    }

    private void validateLegacy(JsonNode root) {
        DdcSiMcpSpecBO spec;
        try {
            spec = objectMapper.treeToValue(root, DdcSiMcpSpecBO.class);
        } catch (JsonProcessingException invalid) {
            throw invalid("invalid MCP specJson structure");
        }
        require(spec.getProtocolVersion() != null && !spec.getProtocolVersion().isBlank(), "$.protocolVersion is required");
        String transport = spec.getTransport() == null ? "" : spec.getTransport().trim().toUpperCase(Locale.ROOT);
        require(VALID_TRANSPORTS.contains(transport), "$.transport must be STDIO, SSE or STREAMABLE_HTTP");
        if ("STDIO".equals(transport)) {
            require(spec.getCommand() != null && !spec.getCommand().isBlank(), "$.command is required when transport=STDIO");
        } else {
            validateHttpEndpoint(spec.getEndpoint());
        }
    }

    private void validateVersioned(JsonNode root) {
        require(SCHEMA_VERSION.equals(root.path("schemaVersion").textValue()), "Unsupported MCP schemaVersion");
        DdcSiMcpSpecBO spec = readVersioned(root);
        require(spec.getSourceType() != null && spec.getTransport() != null && VALID_TRANSPORTS.contains(spec.getTransport()),
                "MCP source and transport are required");
        require(matches(SHA256, spec.getCapabilityFingerprint()), "MCP capability fingerprint must be SHA-256");
        require(spec.getCredentialRef() == null || matches(CREDENTIAL_REFERENCE, spec.getCredentialRef()),
                "MCP credentialRef must identify a credential");
        require(spec.getTimeoutSeconds() == null || (spec.getTimeoutSeconds() > 0 && spec.getTimeoutSeconds() <= 300),
                "MCP timeoutSeconds must be between 1 and 300");
        boolean legacySse = "SSE".equals(spec.getTransport());
        require(spec.getProtocolVersion() != null && (legacySse ? "legacy-2024-sse".equals(spec.getProtocolVersion())
                : HTTP_PROTOCOL_VERSIONS.contains(spec.getProtocolVersion())), "MCP protocolVersion is incompatible with transport");
        switch (spec.getSourceType()) {
            case INTERNAL_SERVICE, COMPOSED_API -> validateService(spec);
            case REMOTE_HTTPS -> validateRemote(spec);
            case STDIO_ARTIFACT -> validateStdio(spec);
        }
        validateTools(root.path("tools"), spec.getTools());
        validateReferences(spec);
    }

    private DdcSiMcpSpecBO readVersioned(JsonNode root) {
        var strict = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strict.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        strict.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        try {
            return strict.treeToValue(root, DdcSiMcpSpecBO.class);
        } catch (JsonProcessingException invalid) {
            throw invalid("invalid versioned MCP specJson structure");
        }
    }

    private static void validateService(DdcSiMcpSpecBO spec) {
        validateNetworkSource(spec);
        var binding = spec.getServiceBinding();
        require(binding != null && matches(STABLE_KEY, binding.bindingId())
                && positiveLong(binding.revision()) && matches(DNS_LABEL, binding.serviceName())
                && matches(DNS_LABEL, binding.namespace()) && binding.port() > 0 && binding.port() <= 65_535,
                "MCP Service binding must include an exact revision, namespace, service and port");
        URI uri = endpoint(spec.getEndpoint());
        String host = binding.serviceName() + "." + binding.namespace() + ".svc.cluster.local";
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        require("http".equals(uri.getScheme()) && host.equals(uri.getHost()) && binding.port() == port,
                "MCP endpoint does not match its Service binding");
        if (spec.getSourceType() == SourceTypeEnum.COMPOSED_API) {
            var mapping = spec.getApiMapping();
            require("STREAMABLE_HTTP".equals(spec.getTransport()) && mapping != null
                    && positiveLong(mapping.gatewayGroupId()) && positiveLong(mapping.publicationId())
                    && mapping.schemaVersion() != null && API_MAPPING_SCHEMAS.contains(mapping.schemaVersion())
                    && matches(SHA256, mapping.contentSha256()) && matches(SHA256, mapping.artifactSha256()),
                    "An exact approved API mapping publication is required for COMPOSED_API");
        } else {
            require(spec.getApiMapping() == null, "API mapping is only valid for COMPOSED_API");
        }
    }

    private static void validateRemote(DdcSiMcpSpecBO spec) {
        validateNetworkSource(spec);
        require(spec.getServiceBinding() == null && spec.getApiMapping() == null, "Remote MCP cannot use a Service binding or API mapping");
        URI uri = endpoint(spec.getEndpoint());
        require("https".equals(uri.getScheme()), "Remote MCP requires HTTPS");
        String host = uri.getHost();
        require(host.contains(".") && !host.matches("[0-9.]+") && !host.endsWith(".svc.cluster.local")
                && !host.endsWith(".localhost"), "Remote MCP requires an approved DNS name");
    }

    private static void validateNetworkSource(DdcSiMcpSpecBO spec) {
        require(!"STDIO".equals(spec.getTransport()) && spec.getCommand() == null && spec.getArtifact() == null
                && spec.getArgs() != null && spec.getArgs().isEmpty() && spec.getEnv() != null && spec.getEnv().isEmpty()
                && spec.getHeaders() != null, "Network MCP cannot include STDIO arguments, environment or artifact");
    }

    private static void validateStdio(DdcSiMcpSpecBO spec) {
        require("STDIO".equals(spec.getTransport()) && spec.getEndpoint() == null && spec.getServiceBinding() == null
                && spec.getApiMapping() == null && spec.getHeaders() != null && spec.getHeaders().isEmpty(),
                "STDIO MCP cannot include a network source");
        require(matches(EXECUTABLE_PATH, spec.getCommand()) && !List.of(spec.getCommand().split("/")).contains(".."),
                "STDIO MCP requires an artifact executable path");
        require(spec.getArgs() != null && spec.getArgs().size() <= 64 && spec.getArgs().stream().noneMatch(arg -> arg == null || arg.length() > 2048)
                && spec.getEnv() != null && spec.getEnv().size() <= 64 && !spec.getEnv().containsValue(null),
                "STDIO MCP arguments or environment are invalid");
        validateArtifact(spec.getArtifact());
    }

    private static void validateArtifact(ArtifactReferenceVO artifact) {
        require(artifact != null && matches(SHA256, artifact.digestSha256()), "An immutable MCP artifact digest is required");
        URI uri = referenceUri(artifact.reference());
        require(Set.of("oci", "artifact", "https").contains(uri.getScheme()), "Unsupported MCP artifact reference");
    }

    private static void validateTools(JsonNode toolNodes, List<DdcSiMcpSpecBO.McpToolItem> tools) {
        require(toolNodes.isArray() && tools != null && tools.size() <= 256, "MCP tools must be a bounded array");
        Set<String> names = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < tools.size(); index++) {
            var tool = tools.get(index);
            var node = toolNodes.get(index);
            require(tool != null && node.isObject() && !node.properties().stream().anyMatch(field -> !TOOL_FIELDS.contains(field.getKey())),
                    "Invalid MCP tool fields");
            require(matches(STABLE_KEY, tool.getToolId()) && ids.add(tool.getToolId())
                    && matches(STABLE_KEY, tool.getName()) && names.add(tool.getName()), "MCP tool IDs and names must be unique");
            require(node.path("inputSchema").isObject() && node.path("outputSchema").isObject(),
                    "MCP tool input and output schemas must be objects");
            require(tool.getRequiredPermissions() != null && tool.getRequiredPermissions().size() <= 64
                    && tool.getRequiredPermissions().stream().allMatch(permission -> matches(PERMISSION, permission))
                    && new HashSet<>(tool.getRequiredPermissions()).size() == tool.getRequiredPermissions().size()
                    && tool.getRiskLevel() != null && node.path("idempotent").isBoolean() && node.path("enabled").isBoolean(),
                    "MCP tool permissions, risk, idempotency and enabled flag are required");
        }
    }

    private static void validateReferences(DdcSiMcpSpecBO spec) {
        require(spec.getDependencies() != null && spec.getDependencies().size() <= 64
                && spec.getResources() != null && spec.getResources().size() <= 128
                && spec.getApps() != null && spec.getApps().size() <= 64
                && spec.getRemoteMounts() != null && spec.getRemoteMounts().size() <= 64, "MCP references exceed the schema bounds");
        Set<List<String>> keys = new HashSet<>();
        for (var dependency : spec.getDependencies()) {
            require(dependency != null && dependency.kind() != null && matches(STABLE_KEY, dependency.key())
                    && positiveLong(dependency.resourceId()) && positiveLong(dependency.versionId())
                    && positiveLong(dependency.versionNo()) && matches(SHA256, dependency.digestSha256())
                    && (dependency.ownerAppCode() == null || matches(STABLE_KEY, dependency.ownerAppCode()))
                    && keys.add(List.of(dependency.kind().getCode(), dependency.key())), "MCP dependencies must be unique and pin exact SI versions");
        }
        Set<String> resourceUris = new HashSet<>();
        for (var resource : spec.getResources()) {
            require(resource != null && resourceUris.add(referenceUri(resource.uri()).toASCIIString())
                    && matches(STABLE_KEY, resource.name()) && matches(SHA256, resource.digestSha256()), "MCP resource reference is invalid");
        }
        Set<String> appKeys = new HashSet<>();
        for (var app : spec.getApps()) {
            require(app != null && matches(STABLE_KEY, app.appKey()) && appKeys.add(app.appKey())
                    && "ui".equals(referenceUri(app.resourceUri()).getScheme()), "MCP app reference is invalid");
            validateArtifact(app.artifact());
        }
        Set<String> mountIds = new HashSet<>();
        Set<String> mountPrefixes = new HashSet<>();
        for (var mount : spec.getRemoteMounts()) {
            require(mount != null && matches(STABLE_KEY, mount.mountId()) && mountIds.add(mount.mountId())
                    && matches(STABLE_KEY, mount.providerKey()) && matches(STABLE_KEY, mount.namespacePrefix())
                    && mountPrefixes.add(mount.namespacePrefix()) && matches(SHA256, mount.capabilityFingerprint()),
                    "MCP remote mount must pin its provider identity, namespace and capability fingerprint");
        }
    }

    private static URI endpoint(String value) {
        URI uri = referenceUri(value);
        require(uri.getHost() != null && uri.getRawPath() != null && uri.getRawPath().startsWith("/")
                && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65_535), "MCP endpoint must name a host and path");
        return uri;
    }

    private static URI referenceUri(String value) {
        require(value != null && !value.isBlank() && value.length() <= 2048, "MCP reference URI is required");
        try {
            URI uri = URI.create(value);
            require(uri.isAbsolute() && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && uri.normalize().equals(uri), "MCP URI must be absolute and credential-free");
            return uri;
        } catch (IllegalArgumentException invalid) {
            throw invalid("MCP reference URI is invalid");
        }
    }

    private static boolean positiveLong(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return false;
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException outOfRange) {
            return false;
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static CommonException invalid(String message) {
        return new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_SPEC_INVALID", message);
    }

    @Override
    public List<DdcSiReferenceBO> extractReferences(DdcSiKindEnum kind, String normalizedSpecJson) {
        JsonNode root = parse(normalizedSpecJson);
        if (!SCHEMA_VERSION.equals(root.path("schemaVersion").textValue())) {
            return List.of();
        }
        var spec = readVersioned(root);
        validateReferences(spec);
        var references = new ArrayList<DdcSiReferenceBO>(spec.getDependencies().size());
        for (int index = 0; index < spec.getDependencies().size(); index++) {
            var dependency = spec.getDependencies().get(index);
            var source = root.path("dependencies").get(index);
            Long versionNo = Long.valueOf(dependency.versionNo());
            var expected = new ExpectedIdentityVO(Long.valueOf(dependency.resourceId()),
                    Long.valueOf(dependency.versionId()), versionNo, dependency.digestSha256(),
                    source.has("ownerAppCode"), dependency.ownerAppCode());
            references.add(DdcSiReferenceBO.builder()
                    .targetType(DdcSiReferenceTargetTypeEnum.fromCode(dependency.kind().getCode()))
                    .targetKind(dependency.kind()).targetKey(dependency.key()).pinnedVersionNo(versionNo)
                    .expectedIdentity(expected).build());
        }
        return List.copyOf(references);
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
