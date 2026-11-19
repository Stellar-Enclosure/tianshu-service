package top.egon.cola.component.tianshu.si;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.model.si.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DdcSiExecutionBundleTest {
    @Test void nativeExecutionHasAnExplicitCompleteImmutableMaterialContract() {
        var child = material(2,DdcSiKindEnum.PROMPT,"{\"prompt\":\"private-body\"}","other-owner");
        var root = material(1,DdcSiKindEnum.AGENT,"{}",null);
        var list = new ArrayList<>(List.of(child));
        var manifest = DdcSiDependencyManifestVO.create(root.identity(),null,List.of(child.identity()),
                List.of(new DdcSiDependencyManifestVO.EdgeVO("1","2")));
        var bundle = new DdcSiExecutionBundleVO(root,manifest,list); list.clear();
        assertThat(bundle.nodes()).containsExactly(child);
        assertThatThrownBy(()->bundle.nodes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(child.ownerAppCode()).isEqualTo("other-owner");
        assertThat(bundle.toString()).doesNotContain("private-body");
        assertThat(child.toString()).doesNotContain("private-body");
    }
    @Test void missingExtraDuplicateWrongScopeAndWrongBodyNeverBecomeExecutable() {
        var root=material(1,DdcSiKindEnum.AGENT,"{}",null);
        var child=material(2,DdcSiKindEnum.SKILL,"{}",null);
        var manifest=DdcSiDependencyManifestVO.create(root.identity(),null,List.of(child.identity()),List.of(new DdcSiDependencyManifestVO.EdgeVO("1","2")));
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(root,manifest,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(root,manifest,List.of(child,child))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(root,manifest,List.of(material(3,DdcSiKindEnum.SKILL,"{}",null)))).isInstanceOf(IllegalArgumentException.class);
        var wrongScope=new DdcSiExecutionBundleVO.MaterialVO(child.identity(),"foreign","ns",null,"key","{}");
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(root,manifest,List.of(wrongScope))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DdcSiExecutionBundleVO.MaterialVO(child.identity(),"biz","ns",null,"key","{\"changed\":1}")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void provenanceSourceIsVerifiedIdentityButNotAnExtraExecutableBody() {
        var root=material(1,DdcSiKindEnum.AGENT,"{}",null);
        var source=material(9,DdcSiKindEnum.PROMPT,"{}",null);
        var manifest=DdcSiDependencyManifestVO.create(root.identity(),source.identity(),List.of(),List.of());
        assertThat(new DdcSiExecutionBundleVO(root,manifest,List.of()).nodes()).isEmpty();
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(root,manifest,List.of(source))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundedBodiesRejectMalformedJsonDuplicateKeysAndNonAgentRoots() {
        var root=material(1,DdcSiKindEnum.PROMPT,"{}",null);
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(root,DdcSiDependencyManifestVO.create(root.identity(),null,List.of(),List.of()),List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->material(1,DdcSiKindEnum.AGENT,"{\"x\":1,\"x\":2}",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->material(1,DdcSiKindEnum.AGENT,"[]",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->material(1,DdcSiKindEnum.AGENT,"{\"x\":\""+"a".repeat(1_048_576)+"\"}",null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nodeCountAndTotalMaterialBytesAreHardBoundsWithoutTruncation() {
        var root=material(1,DdcSiKindEnum.AGENT,"{}",null);
        var nodes=java.util.stream.LongStream.rangeClosed(2,258).mapToObj(id->material(id,DdcSiKindEnum.PROMPT,"{}",null)).toList();
        var manifest=DdcSiDependencyManifestVO.create(root.identity(),null,nodes.stream().map(DdcSiExecutionBundleVO.MaterialVO::identity).toList(),
                nodes.stream().map(node->new DdcSiDependencyManifestVO.EdgeVO("1",node.identity().versionId())).toList());
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(root,manifest,nodes)).isInstanceOf(IllegalArgumentException.class);
        var large="{\"text\":\""+"x".repeat(900_000)+"\"}";
        var largeRoot=material(1,DdcSiKindEnum.AGENT,large,null);
        var largeNodes=java.util.stream.LongStream.rangeClosed(2,5).mapToObj(id->material(id,DdcSiKindEnum.PROMPT,large,null)).toList();
        var largeManifest=DdcSiDependencyManifestVO.create(largeRoot.identity(),null,largeNodes.stream().map(DdcSiExecutionBundleVO.MaterialVO::identity).toList(),
                largeNodes.stream().map(node->new DdcSiDependencyManifestVO.EdgeVO("1",node.identity().versionId())).toList());
        assertThatThrownBy(()->new DdcSiExecutionBundleVO(largeRoot,largeManifest,largeNodes)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DdcSiExecutionBundleVO.MaterialVO material(long id,DdcSiKindEnum kind,String body,String owner) {
        try {
            var identity=new DdcSiDependencyManifestVO.VersionIdentityVO("7",Long.toString(100+id),Long.toString(id),"1",kind,"test",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))));
            return new DdcSiExecutionBundleVO.MaterialVO(identity,"biz","ns",owner,"key"+id,body);
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
