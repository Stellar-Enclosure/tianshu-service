package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier.ApprovalVO;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier.PublicationScopeVO;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** A deployment-pinned, immutable allowlist. Neither publication nor expiry checks read disk or use the network. */
@Configuration(value = "ddcSiMcpPublicationApprovalConfiguration", proxyBeanMethods = false)
public class DdcSiMcpPublicationApprovalConfiguration {
    public static final String PREFIX = "egon.cola.component.tianshu.admin.mcp-publication";
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SPEC_BYTES = 1024 * 1024;
    private static final Set<String> SOURCES = Set.of("INTERNAL_SERVICE", "REMOTE_HTTPS", "STDIO_ARTIFACT", "COMPOSED_API");
    private static final List<String> SOURCE_FIELDS = List.of("schemaVersion", "sourceType", "protocolVersion", "transport",
            "endpoint", "command", "args", "env", "headers", "credentialRef", "serviceBinding", "artifact", "apiMapping");

    @Bean("ddcSiMcpPublicationVerifier")
    DdcSiMcpPublicationVerifier ddcSiMcpPublicationVerifier(@Qualifier("environment") Environment environment,
            @Qualifier("jacksonObjectMapper") ObjectMapper json, @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants,
            @Qualifier("ddcPublishClock") Clock clock,
            @Qualifier("ddcSiMcpContentStrategy") DdcSiContentStrategy canonicalizer) {
        String file = environment.getProperty(PREFIX + ".approvals-file", "");
        String sha = environment.getProperty(PREFIX + ".approvals-sha256", "");
        if (file.isBlank() && sha.isBlank()) return registry(Map.of(), tenants, strict(json), clock, canonicalizer);
        return load(Path.of(file), sha, tenants, json, clock, canonicalizer);
    }

    static DdcSiMcpPublicationVerifier load(Path file, String expectedSha256, DdcTenantRegistry tenants,
                                           ObjectMapper json, Clock clock, DdcSiContentStrategy canonicalizer) {
        Objects.requireNonNull(tenants, "tenants"); Objects.requireNonNull(clock, "clock"); Objects.requireNonNull(canonicalizer, "canonicalizer");
        if (file == null || !file.isAbsolute() || !digestShape(expectedSha256)) throw invalid("PIN_REQUIRED");
        var strict = strict(json);
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length < 2 || bytes.length > MAX_FILE_BYTES || !constantDigest(expectedSha256, sha256(bytes))) {
                throw invalid("HASH_MISMATCH");
            }
            JsonNode root = strict.readTree(bytes);
            fields(root, Set.of("schemaVersion", "approvals")); fixed(root, "schemaVersion", "1");
            var entries = array(root, "approvals");
            if (entries.isEmpty() || entries.size() > 1024) throw invalid("FILE_INVALID");
            var approvals = new HashMap<Key, Entry>(); var ids = new HashSet<String>();
            for (var node : entries) {
                fields(node, Set.of("approvalId", "scope", "validUntil", "canonicalSpecSha256", "sourceEvidence", "apiMappingEvidence"));
                String id = text(node, "approvalId");
                if (!id.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || !ids.add(id)) throw invalid("FILE_INVALID");
                var scope = scope(node.get("scope")); tenants.requireEnabledTenant(Long.valueOf(scope.tenantId()));
                Instant expiry = instant(node, "validUntil"); String specSha = digest(node, "canonicalSpecSha256");
                JsonNode evidence = node.get("sourceEvidence");
                fields(evidence, Set.of("sourceType", "protocolVersion", "sourceDescriptorSha256", "capabilityFingerprint", "observedAt"));
                String source = text(evidence, "sourceType"); if (!SOURCES.contains(source)) throw invalid("FILE_INVALID");
                String protocol = text(evidence, "protocolVersion"); String descriptorSha = digest(evidence, "sourceDescriptorSha256");
                String capability = digest(evidence, "capabilityFingerprint"); Instant observed = instant(evidence, "observedAt");
                if (observed.isAfter(clock.instant()) || !expiry.isAfter(observed)) throw invalid("FILE_INVALID");
                JsonNode mapping = node.get("apiMappingEvidence");
                if ("COMPOSED_API".equals(source)) {
                    if (mapping == null || !mapping.isObject()) throw invalid("MAPPING_UNVERIFIED");
                    validateMappingEvidence(scope, mapping, null, strict);
                } else if (mapping != null && !mapping.isNull()) throw invalid("FILE_INVALID");
                var entry = new Entry(new ApprovalVO(id, expectedSha256, specSha, expiry), source, protocol,
                        descriptorSha, capability, mapping == null ? null : mapping.deepCopy());
                if (approvals.putIfAbsent(new Key(scope, specSha), entry) != null) throw invalid("AMBIGUOUS");
            }
            return registry(Map.copyOf(approvals), tenants, strict, clock, canonicalizer);
        } catch (IOException failure) { throw invalid("UNAVAILABLE"); }
    }

    private static DdcSiMcpPublicationVerifier registry(Map<Key, Entry> approvals, DdcTenantRegistry tenants,
                                                        ObjectMapper json, Clock clock, DdcSiContentStrategy canonicalizer) {
        Map<Key, Entry> frozen = Map.copyOf(approvals);
        return (scope, normalized, digest) -> {
            if (scope == null || normalized == null || !digestShape(digest)) throw invalid("UNVERIFIED");
            tenants.requireEnabledTenant(Long.valueOf(scope.tenantId()));
            byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0 || bytes.length > MAX_SPEC_BYTES || !constantDigest(digest, sha256(bytes))) throw invalid("UNVERIFIED");
            JsonNode spec;
            try { spec = json.readTree(bytes); } catch (IOException failure) { throw invalid("UNVERIFIED"); }
            if (!spec.isObject() || !canonicalizer.canonicalizeJson(json, spec).getNormalizedJson().equals(normalized)) {
                throw invalid("NON_CANONICAL");
            }
            fixed(spec, "schemaVersion", "mcp-server-v2");
            var entry = frozen.get(new Key(scope, digest));
            if (entry == null || !entry.approval().validUntil().isAfter(clock.instant())) throw invalid("UNVERIFIED");
            if (!entry.source().equals(text(spec, "sourceType")) || !entry.protocol().equals(text(spec, "protocolVersion"))
                    || !entry.capability().equals(digest(spec, "capabilityFingerprint"))
                    || !entry.sourceDigest().equals(sourceDescriptorSha256(spec, json, canonicalizer))) throw invalid("UNVERIFIED");
            if ("COMPOSED_API".equals(entry.source())) {
                if (!spec.path("apiMapping").isObject()) throw invalid("MAPPING_UNVERIFIED");
                validateMappingEvidence(scope, entry.mapping(), spec.get("apiMapping"), json);
                verifyToolPolicies(entry.mapping(), spec, json);
            }
            else if (spec.hasNonNull("apiMapping")) throw invalid("MAPPING_UNVERIFIED");
            return entry.approval();
        };
    }

    /** Source evidence is independent from mutable operator edits; exact-body approval additionally pins every Tool policy. */
    static String sourceDescriptorSha256(JsonNode spec, ObjectMapper json, DdcSiContentStrategy canonicalizer) {
        ObjectNode source = json.createObjectNode();
        SOURCE_FIELDS.forEach(name -> { if (spec.has(name)) source.set(name, spec.get(name)); });
        return canonicalizer.canonicalizeJson(json, source).getDigestSha256();
    }

    private static void validateMappingEvidence(PublicationScopeVO scope, JsonNode evidence, JsonNode pointer, ObjectMapper json) {
        if ("yuheng-mcp-mapping-publication-v1".equals(text(evidence, "schemaVersion"))) {
            fields(evidence, Set.of("schemaVersion", "publicationId", "ownerTenantId", "gatewayGroupId", "sourceRelease",
                    "publishedSi", "mappings", "contentSha256", "artifactSha256"));
            owner(scope, evidence);
            String publication = positive(evidence, "publicationId");
            JsonNode sourceRelease = evidence.get("sourceRelease");
            fields(sourceRelease, Set.of("releaseId", "artifactSha256", "mappingContentSha256"));
            if (!publication.equals(positive(sourceRelease, "releaseId"))) throw invalid("MAPPING_UNVERIFIED");
            // These identify the original release. They are not the hashes of this per-SI proof.
            digest(sourceRelease, "artifactSha256");
            digest(sourceRelease, "mappingContentSha256");
            JsonNode published = evidence.get("publishedSi");
            fields(published, Set.of("resourceId", "versionId", "versionNo", "digestSha256", "bizCode", "namespaceCode",
                    "envCode", "ownerAppCode", "resourceKey"));
            if (!published.has("ownerAppCode")) throw invalid("MAPPING_UNVERIFIED");
            positive(published, "versionId"); positive(published, "versionNo"); digest(published, "digestSha256");
            registeredPublished(scope, published);
            JsonNode mappings = array(evidence, "mappings");
            mappingEntries(scope, mappings, false);
            for (var mapping : mappings) {
                JsonNode selected = mapping.get("siServer");
                for (String field : List.of("resourceId", "versionId", "versionNo", "digestSha256", "bizCode", "namespaceCode", "envCode", "resourceKey")) {
                    if (!text(published, field).equals(text(selected, field))) throw invalid("MAPPING_UNVERIFIED");
                }
                if (!nullableOwner(published).equals(nullableOwner(selected))) throw invalid("MAPPING_UNVERIFIED");
            }
            var payload = json.createObjectNode();
            for (String field : List.of("schemaVersion", "ownerTenantId", "gatewayGroupId", "publishedSi", "mappings")) payload.set(field, evidence.get(field));
            String content = digest(evidence, "contentSha256");
            String artifact = digest(evidence, "artifactSha256");
            var document = ((ObjectNode) evidence).deepCopy(); document.remove("artifactSha256");
            if (!content.equals(artifactHash(payload, json)) || !artifact.equals(artifactHash(document, json))) throw invalid("MAPPING_UNVERIFIED");
            if (pointer != null) pointer(pointer, evidence, publication, "yuheng-mcp-mapping-publication-v1", content, artifact);
        } else {
            fields(evidence, Set.of("schemaVersion", "publicationId", "ownerTenantId", "gatewayGroupId", "sourceDraftRevision",
                    "frozenAt", "registeredSi", "mappings", "contentSha256", "artifactSha256"));
            fixed(evidence, "schemaVersion", "yuheng-mcp-mapping-seed-v1"); owner(scope, evidence);
            String publication = positive(evidence, "publicationId"); nonnegative(evidence, "sourceDraftRevision"); instant(evidence, "frozenAt");
            registered(scope, evidence.get("registeredSi"));
            var payload = json.createObjectNode(); payload.set("schemaVersion", evidence.get("schemaVersion"));
            payload.set("registeredSi", evidence.get("registeredSi")); payload.set("mappings", evidence.get("mappings"));
            String content = digest(evidence, "contentSha256"); String artifact = digest(evidence, "artifactSha256");
            var document = ((ObjectNode) evidence).deepCopy(); document.remove("artifactSha256");
            if (!content.equals(artifactHash(payload, json)) || !artifact.equals(artifactHash(document, json))) throw invalid("MAPPING_UNVERIFIED");
            mappingEntries(scope, array(evidence, "mappings"), true);
            if (pointer != null) pointer(pointer, evidence, publication, "yuheng-mcp-mapping-seed-v1", content, artifact);
        }
    }

    private static void mappingEntries(PublicationScopeVO scope, JsonNode mappings, boolean seed) {
        if (mappings.isEmpty() || mappings.size() > 1000) throw invalid("MAPPING_UNVERIFIED");
        String previous = ""; var operations = new HashSet<String>();
        for (var mapping : mappings) {
            fields(mapping, seed ? Set.of("toolId", "sourceOperation", "tool")
                    : Set.of("toolId", "sourceOperation", "siServer", "dependencyClosureDigest", "tool"));
            String id = digest(mapping, "toolId"); if (id.compareTo(previous) <= 0) throw invalid("MAPPING_UNVERIFIED"); previous = id;
            JsonNode operation = mapping.get("sourceOperation");
            fields(operation, Set.of("operationId", "operationKey", "definitionDigest"));
            if (!operations.add(positive(operation, "operationId"))) throw invalid("MAPPING_UNVERIFIED");
            text(operation, "operationKey"); digest(operation, "definitionDigest");
            JsonNode tool = mapping.get("tool");
            fields(tool, Set.of("toolId", "serverCode", "name", "description", "sourceType", "operationId", "operationProtocol",
                    "remoteMountId", "inputSchema", "outputSchema", "annotations", "requiredPermissions", "riskLevel", "idempotent", "enabled"));
            fixed(tool, "sourceType", "LOCAL_OPERATION"); text(tool, "name"); text(tool, "serverCode");
            if (!Set.of("HTTP", "RPC").contains(text(tool, "operationProtocol")) || tool.hasNonNull("remoteMountId")) throw invalid("MAPPING_UNVERIFIED");
            permissions(tool); risk(tool); bool(tool, "idempotent"); bool(tool, "enabled");
            if (seed && (!scope.resourceKey().equals(text(tool, "serverCode"))
                    || !id.equals(sha256((scope.resourceKey() + '\0' + text(operation, "operationKey")).getBytes(StandardCharsets.UTF_8)))
                    || !tool.path("annotations").isObject() || !tool.path("annotations").isEmpty())) throw invalid("MAPPING_UNVERIFIED");
            if (!id.equals(digest(tool, "toolId")) || !positive(operation, "operationId").equals(positive(tool, "operationId"))) throw invalid("MAPPING_UNVERIFIED");
            if (!seed) {
                JsonNode server = mapping.get("siServer");
                fields(server, Set.of("resourceId", "versionId", "versionNo", "digestSha256", "bizCode", "namespaceCode", "envCode", "ownerAppCode", "resourceKey"));
                positive(server, "versionId"); positive(server, "versionNo"); digest(server, "digestSha256");
                registeredPublished(scope, server); digest(mapping, "dependencyClosureDigest");
            }
        }
    }
    private static void verifyToolPolicies(JsonNode evidence, JsonNode spec, ObjectMapper json) {
        JsonNode mappings = evidence.path("mappings");
        var source = new HashMap<String, JsonNode>();
        mappings.forEach(mapping -> source.put(digest(mapping, "toolId"), mapping.get("tool")));
        var used = new HashSet<String>();
        for (var tool : array(spec, "tools")) {
            String id = digest(tool, "toolId"); JsonNode baseline = source.get(id);
            if (baseline == null || !used.add(id) || !text(baseline, "name").equals(text(tool, "name"))) throw invalid("TOOL_POLICY_UNVERIFIED");
            if (!artifactNormalize(schema(baseline, "inputSchema", json), json).equals(artifactNormalize(objectSchema(tool, "inputSchema"), json))
                    || !artifactNormalize(schema(baseline, "outputSchema", json), json).equals(artifactNormalize(objectSchema(tool, "outputSchema"), json))
                    || !permissions(tool).containsAll(permissions(baseline))
                    || risk(tool) < risk(baseline)
                    || bool(tool, "enabled") && !bool(baseline, "enabled")
                    || bool(tool, "idempotent") && !bool(baseline, "idempotent")) throw invalid("TOOL_POLICY_UNVERIFIED");
        }
    }
    private static JsonNode schema(JsonNode tool, String name, ObjectMapper json) {
        try {
            JsonNode value = tool.get(name);
            if (value == null || !value.isTextual() || value.textValue().isBlank()
                    || value.textValue().getBytes(StandardCharsets.UTF_8).length > MAX_SPEC_BYTES) throw invalid("TOOL_POLICY_UNVERIFIED");
            JsonNode parsed = json.readTree(value.textValue());
            if (parsed == null || !parsed.isObject()) throw invalid("TOOL_POLICY_UNVERIFIED");
            return parsed;
        } catch (IOException failure) { throw invalid("TOOL_POLICY_UNVERIFIED"); }
    }
    private static JsonNode objectSchema(JsonNode tool, String name) {
        JsonNode value = tool.get(name); if (value == null || !value.isObject()) throw invalid("TOOL_POLICY_UNVERIFIED"); return value;
    }
    private static Set<String> permissions(JsonNode tool) {
        var result = new HashSet<String>();
        if (array(tool, "requiredPermissions").size() > 200) throw invalid("TOOL_POLICY_UNVERIFIED");
        for (var permission : array(tool, "requiredPermissions")) {
            if (!permission.isTextual() || !permission.textValue().matches("[A-Za-z0-9._:-]{1,128}")
                    || !result.add(permission.textValue())) throw invalid("TOOL_POLICY_UNVERIFIED");
        }
        return result;
    }
    private static int risk(JsonNode tool) {
        return switch (text(tool, "riskLevel")) { case "LOW" -> 0; case "MEDIUM" -> 1; case "HIGH" -> 2; case "CRITICAL" -> 3; default -> throw invalid("TOOL_POLICY_UNVERIFIED"); };
    }
    private static boolean bool(JsonNode tool, String name) {
        JsonNode value = tool.get(name); if (value == null || !value.isBoolean()) throw invalid("TOOL_POLICY_UNVERIFIED"); return value.booleanValue();
    }

    private static void owner(PublicationScopeVO scope, JsonNode node) {
        if (!scope.tenantId().equals(positive(node, "ownerTenantId"))) throw invalid("MAPPING_UNVERIFIED"); positive(node, "gatewayGroupId");
    }
    private static void registered(PublicationScopeVO scope, JsonNode node) {
        fields(node, Set.of("registrationId", "resourceId", "bizCode", "namespaceCode", "envCode", "ownerAppCode", "resourceKey",
                "kind", "registrationRevision", "registrationDescriptorSha256"));
        if (!node.has("ownerAppCode")) throw invalid("MAPPING_UNVERIFIED");
        text(node, "registrationId"); nonnegative(node, "registrationRevision"); digest(node, "registrationDescriptorSha256");
        registeredPublished(scope, node); fixed(node, "kind", "MCP");
    }
    private static void registeredPublished(PublicationScopeVO scope, JsonNode node) {
        if (!scope.resourceId().equals(positive(node, "resourceId")) || !scope.bizCode().equals(text(node, "bizCode"))
                || !scope.namespaceCode().equals(text(node, "namespaceCode")) || !scope.envCode().equals(text(node, "envCode"))
                || !scope.resourceKey().equals(text(node, "resourceKey")) || !scope.ownerAppCode().equals(nullableOwner(node))) throw invalid("MAPPING_UNVERIFIED");
    }
    private static String nullableOwner(JsonNode node) {
        JsonNode owner = node == null ? null : node.get("ownerAppCode");
        if (owner == null || owner.isNull()) return "";
        if (!owner.isTextual() || owner.textValue().isBlank() || !owner.textValue().equals(owner.textValue().strip())
                || owner.textValue().codePoints().anyMatch(Character::isISOControl)) throw invalid("MAPPING_UNVERIFIED");
        return owner.textValue();
    }
    private static void pointer(JsonNode pointer, JsonNode owner, String publication, String schema, String content, String artifact) {
        fields(pointer, Set.of("gatewayGroupId", "publicationId", "schemaVersion", "contentSha256", "artifactSha256"));
        if (!positive(owner, "gatewayGroupId").equals(positive(pointer, "gatewayGroupId"))
                || !publication.equals(positive(pointer, "publicationId")) || !schema.equals(text(pointer, "schemaVersion"))
                || !content.equals(digest(pointer, "contentSha256")) || !artifact.equals(digest(pointer, "artifactSha256"))) throw invalid("MAPPING_UNVERIFIED");
    }
    /** Matches the existing Yuheng artifact normalizer, including equivalent JSON numeric values. */
    static String artifactHash(JsonNode value, ObjectMapper json) { return sha256(artifactNormalize(value, json).toString().getBytes(StandardCharsets.UTF_8)); }
    private static JsonNode artifactNormalize(JsonNode value, ObjectMapper json) {
        if (value == null || value.isMissingNode() || value.isBinary() || value.isPojo()) throw invalid("MAPPING_UNVERIFIED");
        if (value.isObject()) {
            ObjectNode copy = json.createObjectNode();
            var names = new TreeSet<String>();
            value.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> copy.set(name, artifactNormalize(value.get(name), json)));
            return copy;
        }
        if (value.isArray()) {
            var copy = json.createArrayNode();
            value.forEach(item -> copy.add(artifactNormalize(item, json)));
            return copy;
        }
        if (value.isNumber()) {
            if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) throw invalid("MAPPING_UNVERIFIED");
            BigDecimal number = value.decimalValue().stripTrailingZeros();
            return json.getNodeFactory().numberNode(number.signum() == 0 ? BigDecimal.ZERO : number);
        }
        return value.deepCopy();
    }
    private static PublicationScopeVO scope(JsonNode node) {
        fields(node, Set.of("tenantId", "bizCode", "namespaceCode", "envCode", "ownerAppCode", "resourceKey", "resourceId"));
        JsonNode owner = node.get("ownerAppCode"); if (owner == null || !owner.isTextual()) throw invalid("FILE_INVALID");
        return new PublicationScopeVO(positive(node, "tenantId"), text(node, "bizCode"), text(node, "namespaceCode"),
                text(node, "envCode"), owner.textValue(), text(node, "resourceKey"), positive(node, "resourceId"));
    }
    private static ObjectMapper strict(ObjectMapper json) {
        var strict = Objects.requireNonNull(json, "json").copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        strict.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        strict.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(MAX_SPEC_BYTES).maxNumberLength(128).build());
        return strict;
    }
    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject() || node.size() > allowed.size()) throw invalid("FILE_INVALID");
        node.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw invalid("FILE_INVALID"); });
    }
    private static JsonNode array(JsonNode node, String name) { var value = node == null ? null : node.get(name); if (value == null || !value.isArray()) throw invalid("FILE_INVALID"); return value; }
    private static String text(JsonNode node, String name) { var value = node == null ? null : node.get(name); if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 1024 || !value.textValue().equals(value.textValue().strip()) || value.textValue().codePoints().anyMatch(Character::isISOControl)) throw invalid("FILE_INVALID"); return value.textValue(); }
    private static void fixed(JsonNode node, String name, String value) { if (!value.equals(text(node, name))) throw invalid("FILE_INVALID"); }
    private static String positive(JsonNode node, String name) { String value = text(node, name); if (!value.matches("[1-9][0-9]{0,18}")) throw invalid("FILE_INVALID"); try { Long.parseLong(value); } catch (NumberFormatException invalid) { throw invalid("FILE_INVALID"); } return value; }
    private static void nonnegative(JsonNode node, String name) { if (!"0".equals(text(node, name))) positive(node, name); }
    private static String digest(JsonNode node, String name) { String value = text(node, name); if (!digestShape(value)) throw invalid("FILE_INVALID"); return value; }
    private static boolean digestShape(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static Instant instant(JsonNode node, String name) { String value = text(node, name); if (!value.endsWith("Z")) throw invalid("FILE_INVALID"); try { return Instant.parse(value); } catch (DateTimeParseException invalid) { throw invalid("FILE_INVALID"); } }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); } }
    private static boolean constantDigest(String expected, String actual) { return MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(actual)); }
    private static IllegalStateException invalid(String suffix) { return new IllegalStateException("TIANSHU_MCP_PUBLICATION_APPROVAL_" + suffix); }
    private record Key(PublicationScopeVO scope, String digest) { }
    private record Entry(ApprovalVO approval, String source, String protocol, String sourceDigest, String capability, JsonNode mapping) { }
}
