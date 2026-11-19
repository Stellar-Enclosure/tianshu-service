package top.egon.cola.component.tianshu.model.si;

import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.HexFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Complete, immutable execution material from one currently authorized native SI resolution. */
public record DdcSiExecutionBundleVO(@NotNull @Valid MaterialVO root, @NotNull @Valid DdcSiDependencyManifestVO manifest,
                                     @NotNull @Size(max = 256) List<@NotNull @Valid MaterialVO> nodes) {
    public static final int MAX_BODY_BYTES = 1_048_576;
    public static final int MAX_NODES = 256;
    public static final int MAX_BUNDLE_BYTES = 4_194_304;

    public DdcSiExecutionBundleVO {
        if (root == null || manifest == null || nodes == null || nodes.size() > MAX_NODES
                || root.identity().kind() != DdcSiKindEnum.AGENT || !root.identity().equals(manifest.root())) throw invalid();
        var expected = new HashMap<String,DdcSiDependencyManifestVO.VersionIdentityVO>();
        for (var identity : manifest.nodes()) if (expected.put(identity.versionId(), identity) != null) throw invalid();
        var actual = new HashSet<String>(); long bytes = utf8(root.specJson());
        for (MaterialVO material : nodes) {
            if (material == null || !actual.add(material.identity().versionId())
                    || !material.identity().equals(expected.get(material.identity().versionId()))
                    || !root.identity().tenantId().equals(material.identity().tenantId())
                    || !root.identity().envCode().equals(material.identity().envCode())
                    || !root.bizCode().equals(material.bizCode()) || !root.namespaceCode().equals(material.namespaceCode())) throw invalid();
            bytes += utf8(material.specJson());
            if (bytes > MAX_BUNDLE_BYTES) throw invalid();
        }
        if (!actual.equals(expected.keySet())) throw invalid();
        nodes = nodes.stream().sorted(Comparator.comparingLong(value -> Long.parseLong(value.identity().versionId()))).toList();
    }

    @Override public String toString() {
        return "DdcSiExecutionBundleVO[root=" + root.identity() + ", closureDigest=" + manifest.closureDigest()
                + ", nodes=" + nodes.size() + "]";
    }

    /** Owner and key come from that resource's authorized identity; its body is exclusively the exact historical version. */
    public record MaterialVO(@NotNull @Valid DdcSiDependencyManifestVO.VersionIdentityVO identity,
                             @NotBlank @Size(max = 256) String bizCode, @NotBlank @Size(max = 256) String namespaceCode,
                             @Nullable @Size(max = 256) String ownerAppCode, @NotBlank @Size(max = 256) String resourceKey,
                             @NotBlank String specJson) {
        public MaterialVO {
            if (identity == null) throw invalid();
            coordinate(bizCode); coordinate(namespaceCode); coordinate(resourceKey);
            if (ownerAppCode != null) coordinate(ownerAppCode);
            if (specJson == null || utf8(specJson) == 0 || utf8(specJson) > MAX_BODY_BYTES) throw invalid();
            byte[] body = specJson.getBytes(StandardCharsets.UTF_8);
            try {
                if (!DdcConfigCanonicalJson.parse(body).isObject() || !identity.digestSha256().equals(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)))) throw invalid();
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        @Override public String toString() { return "MaterialVO[identity=" + identity + "]"; }
    }

    private static void coordinate(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || !value.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)) throw invalid();
    }
    private static int utf8(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("SI_PINNED_HISTORY_UNAVAILABLE"); }
}
