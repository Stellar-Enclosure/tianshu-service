package top.egon.cola.component.tianshu.model.si;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** An exact, immutable and tenant-local SI execution closure; absence is never an empty verified graph. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DdcSiDependencyManifestVO(
        @JsonProperty(value = "schemaVersion", required = true)
        @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
        @NotBlank @Pattern(regexp = "1") String schemaVersion,
        @JsonProperty(value = "root", required = true) @NotNull @Valid VersionIdentityVO root,
        @JsonProperty(value = "source", required = true) @Nullable @Valid VersionIdentityVO source,
        @JsonProperty(value = "nodes", required = true) @NotNull List<@NotNull @Valid VersionIdentityVO> nodes,
        @JsonProperty(value = "edges", required = true) @NotNull List<@NotNull @Valid EdgeVO> edges,
        @JsonProperty(value = "closureDigest", required = true)
        @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String closureDigest) {
    private static final int MAX_BYTES = 1_048_576;
    private static final Comparator<VersionIdentityVO> NODE_ORDER = Comparator.comparingLong(value -> Long.parseLong(value.versionId()));
    private static final Comparator<EdgeVO> EDGE_ORDER = Comparator.comparingLong((EdgeVO value) -> Long.parseLong(value.fromVersionId()))
            .thenComparingLong(value -> Long.parseLong(value.toVersionId()));

    @JsonCreator public DdcSiDependencyManifestVO {
        if (!"1".equals(schemaVersion)) throw invalid();
        GraphVO graph = normalize(root, source, nodes, edges);
        nodes = graph.nodes(); edges = graph.edges();
        digestValue(closureDigest);
        ObjectNode payload = payload(root, source, nodes, edges);
        if (!sha256(DdcConfigCanonicalJson.canonicalBytes(payload)).equals(closureDigest)) throw invalid();
        payload.put("closureDigest", closureDigest);
        if (DdcConfigCanonicalJson.canonicalBytes(payload).length > MAX_BYTES) throw invalid();
    }

    public static DdcSiDependencyManifestVO create(VersionIdentityVO root, @Nullable VersionIdentityVO source,
            List<VersionIdentityVO> nodes, List<EdgeVO> edges) {
        return new DdcSiDependencyManifestVO("1", root, source, nodes, edges, computeClosureDigest(root, source, nodes, edges));
    }

    /** JCS over schemaVersion/root/source/nodes/edges only. Input collections have set semantics and canonical order. */
    public static String computeClosureDigest(VersionIdentityVO root, @Nullable VersionIdentityVO source,
            List<VersionIdentityVO> nodes, List<EdgeVO> edges) {
        GraphVO graph = normalize(root, source, nodes, edges);
        byte[] canonical = DdcConfigCanonicalJson.canonicalBytes(payload(root, source, graph.nodes(), graph.edges()));
        if (canonical.length > MAX_BYTES) throw invalid();
        return sha256(canonical);
    }

    /** Includes the verified digest; callers receive a new byte array on every access. */
    public byte[] canonicalBytes() {
        ObjectNode value = payload(root, source, nodes, edges);
        value.put("closureDigest", closureDigest);
        return DdcConfigCanonicalJson.canonicalBytes(value);
    }

    private static GraphVO normalize(VersionIdentityVO root, @Nullable VersionIdentityVO source,
            List<VersionIdentityVO> nodes, List<EdgeVO> edges) {
        if (root == null || nodes == null || edges == null || nodes.stream().anyMatch(java.util.Objects::isNull)
                || edges.stream().anyMatch(java.util.Objects::isNull)
                || source != null && !source.tenantId().equals(root.tenantId())) throw invalid();
        List<VersionIdentityVO> orderedNodes = nodes.stream().sorted(NODE_ORDER).toList();
        List<EdgeVO> orderedEdges = edges.stream().sorted(EDGE_ORDER).toList();
        var identities = new HashMap<String, VersionIdentityVO>();
        var businessVersions = new HashSet<List<String>>();
        identities.put(root.versionId(), root);
        businessVersions.add(List.of(root.resourceId(), root.versionNo()));
        for (var node : orderedNodes) {
            if (!root.tenantId().equals(node.tenantId()) || identities.putIfAbsent(node.versionId(), node) != null
                    || !businessVersions.add(List.of(node.resourceId(), node.versionNo()))) throw invalid();
        }
        if (source != null) {
            for (var identity : identities.values()) {
                if (source.versionId().equals(identity.versionId()) && !source.equals(identity)
                        || source.resourceId().equals(identity.resourceId()) && source.versionNo().equals(identity.versionNo())
                            && !source.versionId().equals(identity.versionId())) throw invalid();
            }
        }
        var adjacency = new HashMap<String, List<String>>();
        var incoming = new HashMap<String, Integer>();
        identities.keySet().forEach(id -> { adjacency.put(id, new ArrayList<>()); incoming.put(id, 0); });
        var uniqueEdges = new HashSet<EdgeVO>();
        for (var edge : orderedEdges) {
            if (!uniqueEdges.add(edge) || !identities.containsKey(edge.fromVersionId()) || !identities.containsKey(edge.toVersionId())) throw invalid();
            adjacency.get(edge.fromVersionId()).add(edge.toVersionId());
            incoming.compute(edge.toVersionId(), (key, count) -> count + 1);
        }
        // Iterative graph traversal avoids stack growth for a long but size-valid dependency chain.
        var reachable = new HashSet<String>();
        var pending = new ArrayDeque<String>(); pending.add(root.versionId());
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (reachable.add(id)) pending.addAll(adjacency.get(id));
        }
        if (reachable.size() != identities.size()) throw invalid();
        incoming.forEach((id, count) -> { if (count == 0) pending.add(id); });
        int removed = 0;
        while (!pending.isEmpty()) {
            String id = pending.removeFirst(); removed++;
            for (String to : adjacency.get(id)) if (incoming.compute(to, (key, count) -> count - 1) == 0) pending.add(to);
        }
        if (removed != identities.size()) throw invalid();
        return new GraphVO(orderedNodes, orderedEdges);
    }

    private static ObjectNode payload(VersionIdentityVO root, @Nullable VersionIdentityVO source,
            List<VersionIdentityVO> nodes, List<EdgeVO> edges) {
        ObjectNode value = JsonNodeFactory.instance.objectNode().put("schemaVersion", "1");
        value.set("root", identity(root));
        if (source == null) value.putNull("source"); else value.set("source", identity(source));
        var nodeArray = value.putArray("nodes"); nodes.forEach(node -> nodeArray.add(identity(node)));
        var edgeArray = value.putArray("edges");
        edges.forEach(edge -> edgeArray.addObject().put("fromVersionId", edge.fromVersionId()).put("toVersionId", edge.toVersionId()));
        return value;
    }

    private static ObjectNode identity(VersionIdentityVO value) {
        return JsonNodeFactory.instance.objectNode().put("tenantId", value.tenantId()).put("resourceId", value.resourceId())
                .put("versionId", value.versionId()).put("versionNo", value.versionNo()).put("kind", value.kind().getCode())
                .put("envCode", value.envCode()).put("digestSha256", value.digestSha256());
    }

    public record VersionIdentityVO(
            @JsonProperty(value = "tenantId", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String tenantId,
            @JsonProperty(value = "resourceId", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String resourceId,
            @JsonProperty(value = "versionId", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
            @JsonProperty(value = "versionNo", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String versionNo,
            @JsonProperty(value = "kind", required = true) @NotNull DdcSiKindEnum kind,
            @JsonProperty(value = "envCode", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class) @NotBlank String envCode,
            @JsonProperty(value = "digestSha256", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String digestSha256) {
        @JsonCreator public VersionIdentityVO {
            decimal(tenantId); decimal(resourceId); decimal(versionId); decimal(versionNo); digestValue(digestSha256);
            if (kind == null || envCode == null || envCode.isBlank() || !envCode.equals(envCode.strip())
                    || envCode.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        }
        @JsonAnySetter private void rejectUnknown(String name, Object value) { throw invalid(); }
    }

    public record EdgeVO(
            @JsonProperty(value = "fromVersionId", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String fromVersionId,
            @JsonProperty(value = "toVersionId", required = true)
            @JsonDeserialize(using = DdcConfigCanonicalJson.WireStringDeserializer.class)
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String toVersionId) {
        @JsonCreator public EdgeVO { decimal(fromVersionId); decimal(toVersionId); }
        @JsonAnySetter private void rejectUnknown(String name, Object value) { throw invalid(); }
    }

    @JsonAnySetter private void rejectUnknown(String name, Object value) { throw invalid(); }
    private static void decimal(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { Long.parseLong(value); } catch (NumberFormatException overflow) { throw invalid(); }
    }
    private static void digestValue(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid(); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANSHU_SI_DEPENDENCY_MANIFEST_INVALID"); }
    private record GraphVO(List<VersionIdentityVO> nodes, List<EdgeVO> edges) {}
}
