package top.egon.cola.component.tianshu.si;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DdcSiDependencyManifestTest {
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder().enable(
            com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final VersionIdentityVO ROOT = identity("1", "100", "30", "7", DdcSiKindEnum.AGENT, "prod", "a");
    private static final VersionIdentityVO FIRST = identity("1", "101", "10", "2", DdcSiKindEnum.MCP, "prod", "b");
    private static final VersionIdentityVO SECOND = identity("1", "102", "20", "3", DdcSiKindEnum.SKILL, "prod", "c");
    private static final VersionIdentityVO SOURCE = identity("1", "99", "29", "6", DdcSiKindEnum.AGENT, "staging", "d");
    @Test
    void exposesExactExecutionIdentityAndTypedImmutableDependencyManifest() throws Exception {
        var manifest = Class.forName("top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO");
        assertThat(manifest.isRecord()).isTrue();
        assertThat(DdcSiResourceDescriptor.class.getMethod("getDependencyManifest").getReturnType()).isEqualTo(manifest);
        assertThat(DdcSiResourceDescriptor.class.getMethod("getResolvedVersionId").getReturnType()).isEqualTo(Long.class);
        assertThat(DdcSiResourceDescriptor.class.getMethod("getResolvedVersionNo").getReturnType()).isEqualTo(Long.class);
        assertThat(DdcSiResourceDescriptor.class.getMethod("getOwnerAppCode").getReturnType()).isEqualTo(String.class);
        assertThat(DdcSiCatalogClient.class.getMethod("resolveExecutionResource", long.class, String.class, String.class,
                String.class, String.class, String.class, DdcSiKindEnum.class, String.class, Long.class).isDefault()).isTrue();
    }

    @Test void canonicalOrderingHasOnePortableDigestAndDefensiveImmutableCollections() throws Exception {
        var nodes = new ArrayList<>(List.of(SECOND, FIRST));
        var edges = new ArrayList<>(List.of(new EdgeVO("30", "20"), new EdgeVO("30", "10"), new EdgeVO("20", "10")));
        var manifest = DdcSiDependencyManifestVO.create(ROOT, SOURCE, nodes, edges);
        nodes.clear(); edges.clear();
        assertThat(manifest.nodes()).containsExactly(FIRST, SECOND);
        assertThat(manifest.edges()).containsExactly(new EdgeVO("20", "10"), new EdgeVO("30", "10"), new EdgeVO("30", "20"));
        assertThat(manifest.closureDigest()).isEqualTo("19802e2bc2ca9de872f9fdba0b09d00378c194c2808f799511732aa823396bf9");
        assertThatThrownBy(() -> manifest.nodes().add(ROOT)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> manifest.edges().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(json.readValue(manifest.canonicalBytes(), DdcSiDependencyManifestVO.class)).isEqualTo(manifest);
        assertThat(DdcSiDependencyManifestVO.computeClosureDigest(ROOT, SOURCE, List.of(FIRST, SECOND), manifest.edges()))
                .isEqualTo(manifest.closureDigest());
        byte[] bytes = manifest.canonicalBytes(); bytes[0] = '!';
        assertThat(manifest.canonicalBytes()[0]).isEqualTo((byte) '{');
    }

    @Test void signedBigintIdentityRemainsDecimalStringsAndRejectsNumericCoercion() throws Exception {
        String maximum = Long.toString(Long.MAX_VALUE);
        var maximumRoot = identity(maximum, maximum, maximum, maximum, DdcSiKindEnum.AGENT, "prod", "a");
        var manifest = DdcSiDependencyManifestVO.create(maximumRoot, null, List.of(), List.of());
        String wire = new String(manifest.canonicalBytes(), StandardCharsets.UTF_8);
        assertThat(wire).contains("\"versionNo\":\"9223372036854775807\"");
        assertThat(json.readValue(wire, DdcSiDependencyManifestVO.class).root()).isEqualTo(maximumRoot);
        for (String field : List.of("tenantId", "resourceId", "versionId", "versionNo")) {
            ObjectNode numeric = (ObjectNode) json.readTree(wire);
            ((ObjectNode) numeric.get("root")).put(field, Long.MAX_VALUE);
            assertThatThrownBy(() -> json.readValue(numeric.toString(), DdcSiDependencyManifestVO.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
        for (String invalid : List.of("0", "01", "+1", "-1", " 1", "1 ", "1.0", "9223372036854775808")) {
            assertThatIllegalArgumentException().isThrownBy(() -> identity("1", "100", "30", invalid, DdcSiKindEnum.AGENT, "prod", "a"));
        }
    }

    @Test void missingManifestAndVerifiedEmptyClosureHaveDifferentMeaning() throws Exception {
        var empty = DdcSiDependencyManifestVO.create(ROOT, null, List.of(), List.of());
        assertThat(empty.source()).isNull();
        assertThat(empty.nodes()).isEmpty(); assertThat(empty.edges()).isEmpty();
        assertThat(new DdcSiResourceDescriptor().getDependencyManifest()).isNull();
        assertThat(json.writeValueAsString(new DdcSiResourceDescriptor()))
                .doesNotContain("ownerAppCode", "resolvedVersionId", "resolvedVersionNo", "dependencyManifest");
        var nonNullMapper = json.copy().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        String nonNullWire = nonNullMapper.writeValueAsString(empty);
        assertThat(nonNullWire).contains("\"source\":null");
        assertThat(nonNullMapper.readValue(nonNullWire, DdcSiDependencyManifestVO.class)).isEqualTo(empty);
        ObjectNode numericEnv = (ObjectNode) json.readTree(empty.canonicalBytes());
        ((ObjectNode) numericEnv.get("root")).put("envCode", 42);
        assertThatThrownBy(() -> json.readValue(numericEnv.toString(), DdcSiDependencyManifestVO.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        for (String field : List.of("nodes", "edges", "source")) {
            ObjectNode missing = (ObjectNode) json.readTree(empty.canonicalBytes()); missing.remove(field);
            assertThatThrownBy(() -> json.readValue(missing.toString(), DdcSiDependencyManifestVO.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, null, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new DdcSiDependencyManifestVO("2", ROOT, null, List.of(), List.of(), empty.closureDigest()));
    }

    @Test void rejectsDuplicateIdentityDanglingOrDisconnectedNodesAndCycles() {
        var edge = new EdgeVO("30", "10");
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(FIRST, FIRST), List.of(edge)));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(ROOT), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(FIRST), List.of(edge, edge)));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(FIRST), List.of(new EdgeVO("30", "99"))));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(FIRST), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(FIRST), List.of(edge, new EdgeVO("10", "30"))));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(), List.of(new EdgeVO("30", "30"))));
    }

    @Test void entireClosureIncludingCopySourceMustShareTenantAndDigest() throws Exception {
        var otherTenant = identity("2", "101", "10", "2", DdcSiKindEnum.MCP, "prod", "b");
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, null, List.of(otherTenant), List.of(new EdgeVO("30", "10"))));
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, otherTenant, List.of(), List.of()));
        var conflictingSource = identity("1", "100", "30", "7", DdcSiKindEnum.AGENT, "prod", "f");
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, conflictingSource, List.of(), List.of()));
        var conflictingSourceVersion = identity("1", "100", "29", "7", DdcSiKindEnum.AGENT, "prod", "a");
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(ROOT, conflictingSourceVersion, List.of(), List.of()));
        var manifest = DdcSiDependencyManifestVO.create(ROOT, SOURCE, List.of(FIRST), List.of(new EdgeVO("30", "10")));
        assertThatIllegalArgumentException().isThrownBy(() -> new DdcSiDependencyManifestVO("1", ROOT, SOURCE, manifest.nodes(), manifest.edges(), "0".repeat(64)));
        ObjectNode altered = (ObjectNode) json.readTree(manifest.canonicalBytes());
        ((ObjectNode) altered.get("nodes").get(0)).put("digestSha256", "f".repeat(64));
        assertThatThrownBy(() -> json.readValue(altered.toString(), DdcSiDependencyManifestVO.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        ObjectNode unknown = (ObjectNode) json.readTree(manifest.canonicalBytes()); unknown.put("token", "must-not-be-accepted");
        assertThatThrownBy(() -> json.readValue(unknown.toString(), DdcSiDependencyManifestVO.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    @Test void onlyOneMebibyteBoundsGraphAndLongValidChainsUseIterativeTraversal() {
        var huge = identity("1", "100", "30", "7", DdcSiKindEnum.AGENT, "x".repeat(1_048_576), "a");
        assertThatIllegalArgumentException().isThrownBy(() -> DdcSiDependencyManifestVO.create(huge, null, List.of(), List.of()));
        var nodes = new ArrayList<VersionIdentityVO>(); var edges = new ArrayList<EdgeVO>(); String from = "30";
        for (int index = 0; index < 1200; index++) {
            String version = Integer.toString(10000 + index);
            nodes.add(identity("1", Integer.toString(1000 + index), version, "1", DdcSiKindEnum.SKILL, "prod", "b"));
            edges.add(new EdgeVO(from, version)); from = version;
        }
        assertThat(DdcSiDependencyManifestVO.create(ROOT, null, nodes, edges).nodes()).hasSize(1200);
    }

    @Test void descriptorValidatesResolvedIdentityWhileLegacyMetadataAndCurrentHeadRemainCompatible() throws Exception {
        var closure = DdcSiDependencyManifestVO.create(ROOT, null, List.of(), List.of());
        var descriptor = DdcSiResourceDescriptor.builder().tenantId(1L).bizCode("trade").namespaceCode("shared")
                .envCode("prod").resourceId(100L).kind(DdcSiKindEnum.AGENT).resourceKey("agent.orders").displayName("Orders")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED).sourceType(DdcSiSourceTypeEnum.CONSOLE).activeVersionNo(9)
                .digestSha256("a".repeat(64)).ownerAppCode(null).resolvedVersionId(30L).resolvedVersionNo(7L).dependencyManifest(closure).build();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(descriptor)).isEmpty();
            assertThat(json.readValue(json.writeValueAsBytes(descriptor), DdcSiResourceDescriptor.class)).isEqualTo(descriptor);
            descriptor.setResolvedVersionNo(8L);
            assertThat(factory.getValidator().validate(descriptor)).isNotEmpty();
            descriptor.setResolvedVersionId(null).setResolvedVersionNo(null).setDependencyManifest(null);
            assertThat(factory.getValidator().validate(descriptor)).isEmpty();
            descriptor.setResolvedVersionId(30L);
            assertThat(factory.getValidator().validate(descriptor)).isNotEmpty();
            String maximum = Long.toString(Long.MAX_VALUE);
            var maximumRoot = identity(maximum, maximum, maximum, maximum, DdcSiKindEnum.AGENT, "prod", "a");
            descriptor.setTenantId(Long.MAX_VALUE).setResourceId(Long.MAX_VALUE).setResolvedVersionId(Long.MAX_VALUE)
                    .setResolvedVersionNo(Long.MAX_VALUE).setDependencyManifest(DdcSiDependencyManifestVO.create(maximumRoot, null, List.of(), List.of()));
            String wire = json.writeValueAsString(descriptor);
            assertThat(wire).contains("\"resolvedVersionId\":\"9223372036854775807\"", "\"resolvedVersionNo\":\"9223372036854775807\"");
            assertThat(json.readTree(wire).get("tenantId").isIntegralNumber()).isTrue();
            assertThat(json.readValue(wire, DdcSiResourceDescriptor.class)).isEqualTo(descriptor);
            assertThat(factory.getValidator().validate(descriptor)).isEmpty();
        }
    }

    @Test void oldCatalogImplementationsNeverSilentlyFallbackForExecution() {
        var legacyCalls = new AtomicInteger();
        DdcSiCatalogClient legacy = new DdcSiCatalogClient() {
            public List<DdcSiResourceDescriptor> listResources(DdcSiCatalogQuery query) { return List.of(); }
            public Optional<DdcSiResourceDescriptor> resolveResource(long tenant, String biz, String namespace, String env,
                    String app, DdcSiKindEnum kind, String key, Integer version) { legacyCalls.incrementAndGet(); return Optional.empty(); }
            public List<DdcSiSearchHit> semanticSearch(DdcSiSemanticSearchQuery query) { return List.of(); }
            public DdcSiResourceDescriptor registerManagedResource(DdcSiSelfRegistrationRequest request) { throw new UnsupportedOperationException(); }
        };
        assertThatThrownBy(() -> legacy.resolveExecutionResource(1, "trade", "shared", "prod", null, "caller-orders",
                DdcSiKindEnum.AGENT, "agent.orders", Long.MAX_VALUE)).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("TIANSHU_SI_EXECUTION_RESOLUTION_UNSUPPORTED");
        assertThat(legacyCalls).hasValue(0);
    }

    private static VersionIdentityVO identity(String tenant, String resource, String version, String no,
            DdcSiKindEnum kind, String env, String digest) {
        return new VersionIdentityVO(tenant, resource, version, no, kind, env, digest.repeat(64));
    }
}
