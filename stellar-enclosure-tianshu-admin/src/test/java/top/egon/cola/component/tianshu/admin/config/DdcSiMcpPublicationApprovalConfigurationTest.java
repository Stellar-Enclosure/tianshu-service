package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier.PublicationScopeVO;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiMcpContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DdcSiMcpPublicationApprovalConfigurationTest {
    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final DdcSiContentStrategy CANONICAL = new DdcSiMcpContentStrategy(JSON);
    private static final String TOOL_ID = digest("orders\0orders.read".getBytes(StandardCharsets.UTF_8));
    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final PublicationScopeVO SCOPE = new PublicationScopeVO("101", "biz", "tools", "prod", "owner", "orders", "9001");
    private final DdcTenantRegistry tenants = mock(DdcTenantRegistry.class);
    @TempDir Path directory;

    @Test
    void approvalPinsCanonicalMcpAndFullScopeWithExpiration() throws Exception {
        var clock = new MutableClock(); var spec = nativeSpec(); var normalized = normalized(spec);
        var verifier = load(document(spec, null), clock);
        var approval = verifier.requireApproved(SCOPE, normalized.getNormalizedJson(), normalized.getDigestSha256());
        assertThat(approval.approvalId()).isEqualTo("approval-1");
        assertThat(approval.canonicalSpecSha256()).isEqualTo(normalized.getDigestSha256());
        assertThat(approval.validUntil()).isEqualTo(NOW.plusSeconds(60));
        for (var scope : List.of(new PublicationScopeVO("102", "biz", "tools", "prod", "owner", "orders", "9001"),
                new PublicationScopeVO("101", "other", "tools", "prod", "owner", "orders", "9001"),
                new PublicationScopeVO("101", "biz", "other", "prod", "owner", "orders", "9001"),
                new PublicationScopeVO("101", "biz", "tools", "test", "owner", "orders", "9001"),
                new PublicationScopeVO("101", "biz", "tools", "prod", "other", "orders", "9001"),
                new PublicationScopeVO("101", "biz", "tools", "prod", "owner", "other", "9001"),
                new PublicationScopeVO("101", "biz", "tools", "prod", "owner", "orders", "9002"))) {
            assertThatThrownBy(() -> verifier.requireApproved(scope, normalized.getNormalizedJson(), normalized.getDigestSha256()))
                    .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_UNVERIFIED");
        }
        clock.now = NOW.plusSeconds(60);
        assertThatThrownBy(() -> verifier.requireApproved(SCOPE, normalized.getNormalizedJson(), normalized.getDigestSha256()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_UNVERIFIED");
    }

    @Test
    void absenceIsDefaultClosedAndPartialOrWrongPinsRefuseStartup() throws Exception {
        var config = new DdcSiMcpPublicationApprovalConfiguration(); var spec = normalized(nativeSpec());
        var closed = config.ddcSiMcpPublicationVerifier(new MockEnvironment(), JSON, tenants, Clock.fixed(NOW, ZoneOffset.UTC), CANONICAL);
        assertThatThrownBy(() -> closed.requireApproved(SCOPE, spec.getNormalizedJson(), spec.getDigestSha256()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_UNVERIFIED");
        var file = write(document(nativeSpec(), null));
        assertThatThrownBy(() -> DdcSiMcpPublicationApprovalConfiguration.load(file, "0".repeat(64), tenants, JSON, Clock.fixed(NOW, ZoneOffset.UTC), CANONICAL))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_HASH_MISMATCH");
        assertThatThrownBy(() -> config.ddcSiMcpPublicationVerifier(new MockEnvironment().withProperty(
                DdcSiMcpPublicationApprovalConfiguration.PREFIX + ".approvals-file", file.toString()), JSON, tenants, Clock.fixed(NOW, ZoneOffset.UTC), CANONICAL))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_PIN_REQUIRED");
    }

    @Test
    void exactBodyApprovalDoesNotSilentlyAuthorizeEvenTighteningEdits() throws Exception {
        var spec = nativeSpec(); var verifier = load(document(spec, null), new MutableClock());
        ((ObjectNode) spec.path("tools").get(0)).put("enabled", false);
        var changed = normalized(spec);
        assertThatThrownBy(() -> verifier.requireApproved(SCOPE, changed.getNormalizedJson(), changed.getDigestSha256()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_UNVERIFIED");
        var valid = normalized(nativeSpec());
        assertThatThrownBy(() -> verifier.requireApproved(SCOPE, valid.getNormalizedJson(), "b".repeat(64)))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_UNVERIFIED");
        String pretty = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(nativeSpec());
        assertThatThrownBy(() -> verifier.requireApproved(SCOPE, pretty, digest(pretty.getBytes(StandardCharsets.UTF_8))))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_NON_CANONICAL");
    }

    @Test
    void registrationAndAllRuntimeQueriesAreImmutableAndDoNotRereadTheFile() throws Exception {
        var spec = nativeSpec(); var file = write(document(spec, null));
        var verifier = DdcSiMcpPublicationApprovalConfiguration.load(file, digest(Files.readAllBytes(file)), tenants, JSON, Clock.fixed(NOW, ZoneOffset.UTC), CANONICAL);
        Files.writeString(file, "{}"); Files.delete(file);
        var normalized = normalized(spec);
        assertThat(verifier.requireApproved(SCOPE, normalized.getNormalizedJson(), normalized.getDigestSha256()).approvalId()).isEqualTo("approval-1");
    }

    @Test
    void nativeCapabilityEvidenceMustAgreeEvenWhenTheExactBodyDigestIsApproved() throws Exception {
        for (String field : List.of("sourceType", "protocolVersion", "sourceDescriptorSha256", "capabilityFingerprint")) {
            var document = document(nativeSpec(), null); var evidence = (ObjectNode) document.path("approvals").get(0).path("sourceEvidence");
            evidence.put(field, switch (field) { case "sourceType" -> "REMOTE_HTTPS"; case "protocolVersion" -> "2025-03-26"; default -> "b".repeat(64); });
            var verifier = load(document, new MutableClock()); var spec = normalized(nativeSpec());
            assertThatThrownBy(() -> verifier.requireApproved(SCOPE, spec.getNormalizedJson(), spec.getDigestSha256()))
                    .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_UNVERIFIED");
        }
    }

    @Test
    void strictFileParsingRejectsUnknownDuplicateCoercedAndAmbiguousFacts() throws Exception {
        var document = document(nativeSpec(), null);
        ((ObjectNode) document.path("approvals").get(0)).put("unknown", true);
        var unknown = document;
        assertThatThrownBy(() -> load(unknown, new MutableClock())).isInstanceOf(IllegalStateException.class);
        document = document(nativeSpec(), null); ((ObjectNode) document.path("approvals").get(0).path("scope")).put("tenantId", 101);
        var numeric = document;
        assertThatThrownBy(() -> load(numeric, new MutableClock())).isInstanceOf(IllegalStateException.class);
        document = document(nativeSpec(), null); var repeated = document.path("approvals").get(0).deepCopy();
        ((ObjectNode) repeated).put("approvalId", "approval-2"); ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("approvals")).add(repeated);
        var ambiguous = document;
        assertThatThrownBy(() -> load(ambiguous, new MutableClock())).hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_AMBIGUOUS");
        for (String raw : List.of("{\"schemaVersion\":\"1\",\"schemaVersion\":\"1\",\"approvals\":[]}", "{} {}")) {
            var file = directory.resolve("strict.json"); Files.writeString(file, raw);
            assertThatThrownBy(() -> DdcSiMcpPublicationApprovalConfiguration.load(file, digest(Files.readAllBytes(file)), tenants, JSON, Clock.fixed(NOW, ZoneOffset.UTC), CANONICAL))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void composedSeedUsesItsOwnPublicationIdentityAndActualRegisteredResourceTuple() throws Exception {
        var seed = seed(); var spec = composed(seed); var verifier = load(document(spec, seed), new MutableClock()); var normalized = normalized(spec);
        assertThat(verifier.requireApproved(SCOPE, normalized.getNormalizedJson(), normalized.getDigestSha256()).approvalId()).isEqualTo("approval-1");
        var missing = composed(seed); missing.remove("apiMapping");
        var missingVerifier = load(document(missing, seed), new MutableClock()); var missingBody = normalized(missing);
        assertThatThrownBy(() -> missingVerifier.requireApproved(SCOPE, missingBody.getNormalizedJson(), missingBody.getDigestSha256()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
        var wrong = (ObjectNode) spec.path("apiMapping"); wrong.remove("publicationId"); wrong.put("releaseId", "7001");
        var forged = normalized(spec);
        var alteredApproval = document(spec, seed);
        var alteredVerifier = load(alteredApproval, new MutableClock());
        assertThatThrownBy(() -> alteredVerifier.requireApproved(SCOPE, forged.getNormalizedJson(), forged.getDigestSha256())).isInstanceOf(IllegalStateException.class);
        var substituted = seed(); ((ObjectNode) substituted.path("registeredSi")).put("resourceId", "9002"); sealSeed(substituted);
        assertThatThrownBy(() -> load(document(composed(substituted), substituted), new MutableClock()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
    }

    @Test
    void composedEvidenceRejectsTamperedExportsAndOwnerMismatchWithoutFakingASiVersion() throws Exception {
        var seed = seed(); ((ObjectNode) seed.path("mappings").get(0).path("tool")).put("enabled", false);
        var tampered = seed;
        assertThatThrownBy(() -> load(document(composed(tampered), tampered), new MutableClock()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
        seed = seed(); seed.put("ownerTenantId", "102"); sealSeed(seed); var foreign = seed;
        assertThatThrownBy(() -> load(document(composed(foreign), foreign), new MutableClock()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
        var malformed = seed(); ((ObjectNode) malformed.path("registeredSi")).put("versionNo", "0"); sealSeed(malformed);
        assertThatThrownBy(() -> load(document(composed(malformed), malformed), new MutableClock())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void composedToolsCannotWeakenTheActualExportEvenWithAnExplicitBodyApproval() throws Exception {
        var base = seed(); ((ObjectNode) base.path("mappings").get(0).path("tool")).put("riskLevel", "HIGH"); sealSeed(base);
        var valid = composed(base); ((ObjectNode) valid.path("tools").get(0)).put("riskLevel", "HIGH");
        List<java.util.function.Consumer<ObjectNode>> changes = List.of(
                tool -> tool.putArray("requiredPermissions"), tool -> tool.put("riskLevel", "LOW"),
                tool -> tool.put("toolId", "0".repeat(64)), tool -> tool.put("name", "renamed"),
                tool -> tool.putObject("inputSchema").put("type", "array"),
                tool -> tool.putObject("outputSchema").put("type", "array"));
        for (var change : changes) {
            var changed = valid.deepCopy(); change.accept((ObjectNode) changed.path("tools").get(0));
            var verifier = load(document(changed, base), new MutableClock()); var body = normalized(changed);
            assertThatThrownBy(() -> verifier.requireApproved(SCOPE, body.getNormalizedJson(), body.getDigestSha256()))
                    .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_TOOL_POLICY_UNVERIFIED");
        }
        var restricted = valid.deepCopy(); var tool = (ObjectNode) restricted.path("tools").get(0);
        tool.put("riskLevel", "CRITICAL").put("enabled", false).put("idempotent", false);
        ((com.fasterxml.jackson.databind.node.ArrayNode) tool.path("requiredPermissions")).add("orders:audit");
        var verifier = load(document(restricted, base), new MutableClock()); var body = normalized(restricted);
        assertThat(verifier.requireApproved(SCOPE, body.getNormalizedJson(), body.getDigestSha256())).isNotNull();
    }

    @Test
    void composedToolCannotReenableOrClaimIdempotenceAndRejectsMalformedSchemaStrings() throws Exception {
        for (String flag : List.of("enabled", "idempotent")) {
            var seed = seed(); ((ObjectNode) seed.path("mappings").get(0).path("tool")).put(flag, false); sealSeed(seed);
            var spec = composed(seed); var verifier = load(document(spec, seed), new MutableClock()); var body = normalized(spec);
            assertThatThrownBy(() -> verifier.requireApproved(SCOPE, body.getNormalizedJson(), body.getDigestSha256()))
                    .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_TOOL_POLICY_UNVERIFIED");
        }
        var seed = seed(); ((ObjectNode) seed.path("mappings").get(0).path("tool")).put("inputSchema", "{\"type\":\"object\",\"type\":\"array\"}"); sealSeed(seed);
        var spec = composed(seed); var verifier = load(document(spec, seed), new MutableClock()); var body = normalized(spec);
        assertThatThrownBy(() -> verifier.requireApproved(SCOPE, body.getNormalizedJson(), body.getDigestSha256()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_TOOL_POLICY_UNVERIFIED");
    }

    @Test
    void independentOperatorExporterVectorPreservesThreeDigestsAndSharedOwnerIdentity() throws Exception {
        // The production Yuheng seed emitter generated this synthetic vector.
        byte[] bytes = new ClassPathResource("mcp/mapping-seed-canonical.json").getContentAsByteArray();
        assertThat(digest(bytes)).isEqualTo("5b97745f7309b43ce4647d848b26431033e8b951b86daea94dbcf3cc8ce1d24f");
        JsonNode seed = JSON.readTree(bytes); var spec = composed(seed);
        var sourceTool = seed.path("mappings").get(0).path("tool"); var tool = JSON.createObjectNode();
        for (String field : List.of("toolId", "name", "requiredPermissions", "riskLevel", "enabled", "idempotent")) tool.set(field, sourceTool.get(field));
        tool.set("inputSchema", JSON.readTree(sourceTool.path("inputSchema").textValue()));
        tool.set("outputSchema", JSON.readTree(sourceTool.path("outputSchema").textValue())); spec.putArray("tools").add(tool);
        var shared = new PublicationScopeVO("7", "commerce", "default", "test", "", "orders", "201");
        var document = document(spec, seed); ((ObjectNode) document.path("approvals").get(0)).set("scope", JSON.valueToTree(shared));
        verifyThroughProductionBeans(spec, document, shared);
        assertThat(spec.path("apiMapping").path("contentSha256").textValue()).isEqualTo("894311a776358e9998595a4c0ed9b3ee78a3c246c0ad8bbb86776853dd2724c9");
        assertThat(spec.path("apiMapping").path("artifactSha256").textValue()).isEqualTo("a212021a658c70fd9baaf6b3d4f63a35379e07582ab188e1ad0d9ae392757fb5");
    }

    @Test
    void boundPublicationPinsOnlyItsSelectedSiAndKeepsOriginalReleaseProvenanceSeparate() throws Exception {
        var proof = publicationProof(); var spec = composed(proof); var verifier = load(document(spec, proof), new MutableClock());
        var body = normalized(spec);
        assertThat(verifier.requireApproved(SCOPE, body.getNormalizedJson(), body.getDigestSha256())).isNotNull();
        assertThat(spec.path("apiMapping").path("artifactSha256").textValue())
                .isNotEqualTo(proof.path("sourceRelease").path("artifactSha256").textValue());
        assertThat(spec.path("apiMapping").path("contentSha256").textValue())
                .isNotEqualTo(proof.path("sourceRelease").path("mappingContentSha256").textValue());
        var rawNative = JSON.createObjectNode(); rawNative.putObject("canonicalSnapshot");
        rawNative.putObject("activationContent"); rawNative.putObject("chunkManifest");
        assertThatThrownBy(() -> load(document(spec, rawNative), new MutableClock())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void projectedProofRejectsEverySelectedVersionAndRealmMismatchEvenWithRecomputedDigests() throws Exception {
        for (String field : List.of("resourceId", "versionId", "versionNo", "digestSha256", "bizCode", "namespaceCode", "envCode", "resourceKey", "ownerAppCode")) {
            var proof = publicationProof(); var selected = (ObjectNode) proof.path("mappings").get(0).path("siServer");
            selected.put(field, switch (field) { case "resourceId", "versionId", "versionNo" -> "9999"; case "digestSha256" -> "0".repeat(64); default -> "different"; });
            sealPublication(proof);
            assertThatThrownBy(() -> load(document(composed(proof), proof), new MutableClock()))
                    .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
        }
        var wrongSource = publicationProof(); ((ObjectNode) wrongSource.path("sourceRelease")).put("releaseId", "8001"); sealPublication(wrongSource);
        assertThatThrownBy(() -> load(document(composed(wrongSource), wrongSource), new MutableClock()))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
        var raw = publicationProof(); raw.putObject("sourceContent").put("arbitrary", "not-exportable"); sealPublication(raw);
        assertThatThrownBy(() -> load(document(composed(raw), raw), new MutableClock())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void originalWholeReleaseAndSectionDigestsCannotReplaceTheProjectedPointerDigests() throws Exception {
        for (String field : List.of("contentSha256", "artifactSha256")) {
            var proof = publicationProof(); var spec = composed(proof);
            ((ObjectNode) spec.path("apiMapping")).put(field, proof.path("sourceRelease").path(
                    field.equals("contentSha256") ? "mappingContentSha256" : "artifactSha256").textValue());
            var verifier = load(document(spec, proof), new MutableClock()); var body = normalized(spec);
            assertThatThrownBy(() -> verifier.requireApproved(SCOPE, body.getNormalizedJson(), body.getDigestSha256()))
                    .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
        }
    }

    @Test
    void sharedPublishedOwnerIsExplicitAtTheRootAndMayBeAbsentInTheImmutableEntry() throws Exception {
        var proof = publicationProof(); ((ObjectNode) proof.path("publishedSi")).putNull("ownerAppCode");
        ((ObjectNode) proof.path("mappings").get(0).path("siServer")).remove("ownerAppCode"); sealPublication(proof);
        var shared = new PublicationScopeVO("101", "biz", "tools", "prod", "", "orders", "9001");
        var spec = composed(proof); var document = document(spec, proof); ((ObjectNode) document.path("approvals").get(0)).set("scope", JSON.valueToTree(shared));
        var verifier = load(document, new MutableClock()); var body = normalized(spec);
        assertThat(verifier.requireApproved(shared, body.getNormalizedJson(), body.getDigestSha256())).isNotNull();
        ((ObjectNode) proof.path("publishedSi")).remove("ownerAppCode"); sealPublication(proof);
        var missingOwner = document(composed(proof), proof); ((ObjectNode) missingOwner.path("approvals").get(0)).set("scope", JSON.valueToTree(shared));
        assertThatThrownBy(() -> load(missingOwner, new MutableClock())).hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_MAPPING_UNVERIFIED");
    }

    @Test
    void independentBoundEmitterVectorConfirmsProjectedDigestsAndDoesNotLoadRawNativeContent() throws Exception {
        // The production Yuheng publication evidence emitter generated this synthetic vector.
        byte[] bytes = new ClassPathResource("mcp/mapping-publication-canonical.json").getContentAsByteArray();
        assertThat(digest(bytes)).isEqualTo("f16225ee00b53be57b226cf83ac7ed121a6c69642d0716085e1cc0e4bfe61436");
        JsonNode proof = JSON.readTree(bytes); var spec = composed(proof);
        var original = proof.path("mappings").get(0).path("tool"); var tool = JSON.createObjectNode();
        for (String field : List.of("toolId", "name", "requiredPermissions", "riskLevel", "enabled", "idempotent")) tool.set(field, original.get(field));
        tool.set("inputSchema", JSON.readTree(original.path("inputSchema").textValue()));
        tool.set("outputSchema", JSON.readTree(original.path("outputSchema").textValue())); spec.putArray("tools").add(tool);
        var target = new PublicationScopeVO("7", "commerce", "default", "test", "", "orders", "201");
        var document = document(spec, proof); ((ObjectNode) document.path("approvals").get(0)).set("scope", JSON.valueToTree(target));
        verifyThroughProductionBeans(spec, document, target);
        assertThat(proof.path("contentSha256").textValue()).isEqualTo("6123dcde4b13145840a7f5699c07f4f6a63db853859cde457fb5f90db88d293e");
        assertThat(proof.path("artifactSha256").textValue()).isEqualTo("2c54d55f3ae4d4067774c46c4378cd7ff2d05f9306a81d5cf0242da92a2acc63");
        assertThat(proof.path("sourceRelease").path("artifactSha256").textValue()).isEqualTo("34a2c3a6c1333f1f9b35c447b46ed81bf78b1bb91f3fa6d3ddff0cf5450438ba");
        assertThat(proof.path("sourceRelease").path("mappingContentSha256").textValue()).isEqualTo("0734a534012825e8414c480e33b011368cbcf02cc090610ae2be166fa1b705bd");
        assertThat(proof.has("sourceContent")).isFalse();
    }

    private static ObjectNode publicationProof() {
        var mappings = seed().path("mappings").deepCopy(); var mapping = (ObjectNode) mappings.get(0);
        mapping.put("dependencyClosureDigest", "e".repeat(64));
        var published = JSON.createObjectNode().put("resourceId", "9001").put("versionId", "9002").put("versionNo", "2")
                .put("digestSha256", "f".repeat(64)).put("bizCode", "biz").put("namespaceCode", "tools")
                .put("envCode", "prod").put("ownerAppCode", "owner").put("resourceKey", "orders");
        mapping.set("siServer", published.deepCopy());
        var proof = JSON.createObjectNode().put("schemaVersion", "yuheng-mcp-mapping-publication-v1").put("publicationId", "8000")
                .put("ownerTenantId", "101").put("gatewayGroupId", "7002");
        proof.putObject("sourceRelease").put("releaseId", "8000").put("artifactSha256", "1".repeat(64)).put("mappingContentSha256", "2".repeat(64));
        proof.set("publishedSi", published); proof.set("mappings", mappings); sealPublication(proof); return proof;
    }

    private static void sealPublication(ObjectNode proof) {
        var payload = JSON.createObjectNode();
        for (String field : List.of("schemaVersion", "ownerTenantId", "gatewayGroupId", "publishedSi", "mappings")) payload.set(field, proof.get(field));
        proof.put("contentSha256", digest(sorted(payload).toString().getBytes(StandardCharsets.UTF_8)));
        var artifact = proof.deepCopy(); artifact.remove("artifactSha256");
        proof.put("artifactSha256", digest(sorted(artifact).toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void revokedLocalTenantIsCheckedAgainAndFutureObservationIsNotApproval() throws Exception {
        var spec = nativeSpec(); var verifier = load(document(spec, null), new MutableClock()); var body = normalized(spec);
        org.mockito.Mockito.when(tenants.requireEnabledTenant(101L)).thenThrow(new IllegalStateException("TENANT_DISABLED"));
        assertThatThrownBy(() -> verifier.requireApproved(SCOPE, body.getNormalizedJson(), body.getDigestSha256())).hasMessage("TENANT_DISABLED");
        org.mockito.Mockito.reset(tenants);
        var future = document(spec, null); ((ObjectNode) future.path("approvals").get(0).path("sourceEvidence")).put("observedAt", NOW.plusSeconds(1).toString());
        assertThatThrownBy(() -> load(future, new MutableClock())).hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_FILE_INVALID");
    }

    @Test
    void explicitlyPinnedOversizeFileIsRejectedBeforeParsing() throws Exception {
        var path = directory.resolve("oversize.json");
        byte[] bytes = new byte[8 * 1024 * 1024 + 1]; java.util.Arrays.fill(bytes, (byte) ' '); Files.write(path, bytes);
        assertThatThrownBy(() -> DdcSiMcpPublicationApprovalConfiguration.load(path, digest(bytes), tenants, JSON, Clock.fixed(NOW, ZoneOffset.UTC), CANONICAL))
                .hasMessage("TIANSHU_MCP_PUBLICATION_APPROVAL_HASH_MISMATCH");
    }

    private ObjectNode document(JsonNode spec, JsonNode mapping) {
        var root = JSON.createObjectNode().put("schemaVersion", "1"); var entry = root.putArray("approvals").addObject();
        entry.put("approvalId", "approval-1"); entry.set("scope", JSON.valueToTree(SCOPE)); entry.put("validUntil", NOW.plusSeconds(60).toString());
        entry.put("canonicalSpecSha256", normalized(spec).getDigestSha256());
        entry.putObject("sourceEvidence").put("sourceType", spec.path("sourceType").textValue())
                .put("protocolVersion", spec.path("protocolVersion").textValue())
                .put("sourceDescriptorSha256", DdcSiMcpPublicationApprovalConfiguration.sourceDescriptorSha256(spec, JSON, CANONICAL))
                .put("capabilityFingerprint", spec.path("capabilityFingerprint").textValue()).put("observedAt", NOW.minusSeconds(5).toString());
        if (mapping != null) entry.set("apiMappingEvidence", mapping.deepCopy());
        return root;
    }
    private DdcSiMcpPublicationVerifier load(JsonNode document, Clock clock) throws Exception {
        var file = write(document); return DdcSiMcpPublicationApprovalConfiguration.load(file, digest(Files.readAllBytes(file)), tenants, JSON, clock, CANONICAL);
    }

    private void verifyThroughProductionBeans(JsonNode spec, JsonNode document, PublicationScopeVO scope) throws Exception {
        Path approvalsFile = write(document);
        String prefix = DdcSiMcpPublicationApprovalConfiguration.PREFIX;
        new ApplicationContextRunner()
                .withUserConfiguration(DdcSiMcpPublicationApprovalConfiguration.class, DdcSiMcpContentStrategy.class)
                .withBean("jacksonObjectMapper", ObjectMapper.class, () -> JSON)
                .withBean("ddcTenantRegistry", DdcTenantRegistry.class, () -> tenants)
                .withBean("ddcPublishClock", Clock.class, MutableClock::new)
                .withPropertyValues(prefix + ".approvals-file=" + approvalsFile,
                        prefix + ".approvals-sha256=" + digest(Files.readAllBytes(approvalsFile)))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var strategy = context.getBean("ddcSiMcpContentStrategy", DdcSiContentStrategy.class);
                    var body = strategy.validateAndNormalize(DdcSiKindEnum.MCP, spec.toString());
                    var verifier = context.getBean("ddcSiMcpPublicationVerifier", DdcSiMcpPublicationVerifier.class);
                    assertThat(verifier.requireApproved(scope, body.getNormalizedJson(), body.getDigestSha256())
                            .canonicalSpecSha256()).isEqualTo(body.getDigestSha256());
                });
    }

    private Path write(JsonNode document) throws Exception { var path = directory.resolve("approvals.json"); Files.writeString(path, document.toString()); return path; }
    private static DdcSiContentStrategy.NormalizedContent normalized(JsonNode spec) { return CANONICAL.canonicalizeJson(JSON, spec); }
    private static ObjectNode nativeSpec() {
        var root = JSON.createObjectNode().put("schemaVersion", "mcp-server-v2").put("sourceType", "INTERNAL_SERVICE")
                .put("protocolVersion", "2025-11-25").put("transport", "STREAMABLE_HTTP").put("capabilityFingerprint", "a".repeat(64))
                .put("endpoint", "http://orders-mcp.tools.svc.cluster.local/mcp");
        root.putObject("serviceBinding").put("bindingId", "orders-mcp").put("revision", "1")
                .put("serviceName", "orders-mcp").put("namespace", "tools").put("port", 80);
        var tool = root.putArray("tools").addObject().put("toolId", TOOL_ID).put("name", "readOrders")
                .put("enabled", true).put("idempotent", true).put("riskLevel", "LOW");
        tool.putObject("inputSchema").put("type", "object"); tool.putObject("outputSchema").put("type", "object"); tool.putArray("requiredPermissions").add("orders:read");
        return root;
    }
    private static ObjectNode composed(JsonNode seed) {
        var root = nativeSpec().put("sourceType", "COMPOSED_API");
        root.putObject("apiMapping").put("gatewayGroupId", seed.path("gatewayGroupId").textValue())
                .put("publicationId", seed.path("publicationId").textValue()).put("schemaVersion", seed.path("schemaVersion").textValue())
                .put("contentSha256", seed.path("contentSha256").textValue()).put("artifactSha256", seed.path("artifactSha256").textValue());
        return root;
    }
    private static ObjectNode seed() {
        var seed = JSON.createObjectNode().put("schemaVersion", "yuheng-mcp-mapping-seed-v1").put("publicationId", "7001")
                .put("ownerTenantId", "101").put("gatewayGroupId", "7002").put("sourceDraftRevision", "2").put("frozenAt", NOW.minusSeconds(10).toString());
        seed.putObject("registeredSi").put("registrationId", "registered-mcp").put("resourceId", "9001").put("bizCode", "biz")
                .put("namespaceCode", "tools").put("envCode", "prod").put("ownerAppCode", "owner").put("resourceKey", "orders")
                .put("kind", "MCP").put("registrationRevision", "1").put("registrationDescriptorSha256", "d".repeat(64));
        var entry = seed.putArray("mappings").addObject().put("toolId", TOOL_ID);
        entry.putObject("sourceOperation").put("operationId", "8001").put("operationKey", "orders.read").put("definitionDigest", "e".repeat(64));
        var tool = entry.putObject("tool").put("toolId", TOOL_ID).put("operationId", "8001").put("name", "readOrders")
                .put("serverCode", "orders").put("sourceType", "LOCAL_OPERATION").put("operationProtocol", "HTTP")
                .put("inputSchema", "{\"type\":\"object\"}").put("outputSchema", "{\"type\":\"object\"}")
                .put("riskLevel", "LOW").put("enabled", true).put("idempotent", true);
        tool.putObject("annotations"); tool.putArray("requiredPermissions").add("orders:read"); sealSeed(seed); return seed;
    }
    private static void sealSeed(ObjectNode seed) {
        var payload = JSON.createObjectNode(); payload.set("schemaVersion", seed.get("schemaVersion")); payload.set("registeredSi", seed.get("registeredSi")); payload.set("mappings", seed.get("mappings"));
        seed.put("contentSha256", digest(sorted(payload).toString().getBytes(StandardCharsets.UTF_8)));
        var artifact = seed.deepCopy(); artifact.remove("artifactSha256"); seed.put("artifactSha256", digest(sorted(artifact).toString().getBytes(StandardCharsets.UTF_8)));
    }
    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) { var map = new TreeMap<String, JsonNode>(); node.fields().forEachRemaining(field -> map.put(field.getKey(), field.getValue())); var copy = JSON.createObjectNode(); map.forEach((name,value) -> copy.set(name, sorted(value))); return copy; }
        if (node.isArray()) { var copy = JSON.createArrayNode(); node.forEach(value -> copy.add(sorted(value))); return copy; } return node.deepCopy();
    }
    private static String digest(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception impossible) { throw new AssertionError(impossible); } }
    private static final class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException(); return this; }
        @Override public Instant instant() { return now; }
    }
}
