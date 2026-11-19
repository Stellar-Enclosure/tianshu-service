package top.egon.cola.component.tianshu.admin.service.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TEST-008：验证 Agent 有向无环图（自环/间接环/深度超限）、目标环境已发布检查，
 * 以及归档前来自上游 Agent 或 Tianji Flowable 流程与活跃绑定的阻断校验。
 */
@ExtendWith(MockitoExtension.class)
class DdcSiDependencyValidatorTest {

    @Mock
    private DdcSiResourceRepository repository;

    private DdcSiDependencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DdcSiDependencyValidator(repository, new ObjectMapper());
    }

    @Test void verifiedMaterialReusesTheHistoricalBatchAndRefusesOmittedHistoricalEdgesOrChangedSource() throws Exception {
        var json=new ObjectMapper();
        var body="{}";var digest=bodyDigest(body);
        var rootId=new top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO("1","100","301","3",DdcSiKindEnum.AGENT,"prod",digest);
        var childId=new top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO("1","101","302","2",DdcSiKindEnum.PROMPT,"prod",digest);
        var sourceId=new top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO("1","100","300","1",DdcSiKindEnum.AGENT,"prod",digest);
        var manifest=top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.create(rootId,sourceId,List.of(childId),
                List.of(new top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO("301","302")));
        var root=historicalRow(rootId,manifest,json);
        var child=historicalRow(childId,top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.create(childId,null,List.of(),List.of()),json);
        var source=historicalRow(sourceId,top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.create(sourceId,null,List.of(),List.of()),json);
        when(repository.findHistoryVersionsByIds(1L,List.of(300L,302L))).thenReturn(List.of(child,source));
        var verified=validator.requireHistoricalMaterial(root);
        assertThat(verified.versions()).containsOnlyKeys(300L,301L,302L);
        assertThat(verified.versions().get(302L)).isSameAs(child);
        assertThatThrownBy(()->verified.versions().clear()).isInstanceOf(UnsupportedOperationException.class);
        org.mockito.Mockito.verify(repository).findHistoryVersionsByIds(1L,List.of(300L,302L));
        var omittedId=new top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO("1","102","303","1",DdcSiKindEnum.SKILL,"prod",digest);
        child.setDependencyManifestJson(json.valueToTree(top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.create(childId,null,List.of(omittedId),
                List.of(new top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO("302","303")))));
        assertThatThrownBy(()->validator.requireHistoricalMaterial(root)).isInstanceOf(CommonException.class).hasMessageContaining("history");
        child.setDependencyManifestJson(json.valueToTree(top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.create(childId,null,List.of(),List.of())));
        source.setSpecJson("{\"changed\":true}");
        assertThatThrownBy(()->validator.requireHistoricalMaterial(root)).isInstanceOf(CommonException.class).hasMessageContaining("history");
    }
    private static String bodyDigest(String body) {
        return top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy.sha256Hex(body);
    }
    private static DdcSiVersionBO historicalRow(top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO id,
            top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO manifest,ObjectMapper json) {
        return DdcSiVersionBO.builder().id(Long.valueOf(id.versionId())).tenantId(Long.valueOf(id.tenantId())).resourceId(Long.valueOf(id.resourceId()))
                .versionNo(Long.valueOf(id.versionNo())).kind(id.kind()).envCode(id.envCode()).specJson("{}").digestSha256(id.digestSha256())
                .dependencyManifestJson(json.valueToTree(manifest)).build();
    }

    @Test
    void rejectsDirectSelfCycleBeforeLockingOrReservingAVersion() {
        assertThatThrownBy(() -> validator.freezePublication(resource(), new DdcSiVersionBO(), null,
                List.of(edge(DdcSiReferenceTargetTypeEnum.AGENT, "agent-a", DdcSiReferenceTargetTypeEnum.AGENT, "agent-a")),
                List.of())).isInstanceOf(CommonException.class).hasMessageContaining("itself");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void requiresAllDependenciesToBePublishedInTargetScope() {
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "app-a", DdcSiKindEnum.MCP, "github-mcp"))
                .thenReturn(Optional.of(DdcSiResourceBO.builder().id(55L).resourceKey("github-mcp")
                        .lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT).build()));
        assertThatThrownBy(() -> validator.freezePublication(resource(), new DdcSiVersionBO(), null,
                List.of(edge(DdcSiReferenceTargetTypeEnum.AGENT, "agent-a", DdcSiReferenceTargetTypeEnum.MCP, "github-mcp")),
                List.of())).isInstanceOf(CommonException.class).hasMessageContaining("not published");
    }

    private static DdcSiResourceBO resource() {
        return DdcSiResourceBO.builder().id(100L).tenantId(1L).bizCode("xingyuan").namespaceId(10L)
                .envCode("prod").appCode("app-a").kind(DdcSiKindEnum.AGENT).resourceKey("agent-a").build();
    }

    @Test
    void blocksArchiveWhenInboundTianjiProcessOrActiveAppBindingExists() {
        DdcSiResourceBO resource = DdcSiResourceBO.builder()
                .id(100L)
                .tenantId(1L)
                .envCode("prod")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("approval-agent")
                .build();

        when(repository.findBlockingInboundReferences(1L, "prod", DdcSiKindEnum.AGENT, "approval-agent"))
                .thenReturn(List.of(DdcSiReferenceBO.builder()
                        .sourceType(DdcSiReferenceTargetTypeEnum.TIANJI_PROCESS)
                        .sourceKey("loan-approval-flow")
                        .targetType(DdcSiReferenceTargetTypeEnum.AGENT)
                        .targetKey("approval-agent")
                        .build()));
        when(repository.findBindingsByResourceId(1L, 100L)).thenReturn(List.of(
                DdcSiBindingBO.builder().appCode("tianji-agent-server").status(DdcSiBindingStatusEnum.ACTIVE).build()));

        assertThatThrownBy(() -> validator.ensureSafeToArchive(resource))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56409))
                .hasMessageContaining("loan-approval-flow")
                .hasMessageContaining("tianji-agent-server");

        when(repository.findBlockingInboundReferences(1L, "prod", DdcSiKindEnum.AGENT, "approval-agent"))
                .thenReturn(List.of());
        when(repository.findBindingsByResourceId(1L, 100L)).thenReturn(List.of());
        assertThatCode(() -> validator.ensureSafeToArchive(resource)).doesNotThrowAnyException();
    }

    @Test
    void exactIdentityRetainsLongRangeAndRequiresAllPositiveCanonicalCoordinates() {
        var maximum = new DdcSiReferenceBO.ExpectedIdentityVO(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,"a".repeat(64),false,null);
        assertThat(maximum.versionId()).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(()->new DdcSiReferenceBO.ExpectedIdentityVO(null,2L,3L,"a".repeat(64),false,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DdcSiReferenceBO.ExpectedIdentityVO(1L,0L,3L,"a".repeat(64),false,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DdcSiReferenceBO.ExpectedIdentityVO(1L,2L,-1L,"a".repeat(64),false,null)).isInstanceOf(IllegalArgumentException.class);
        for(String digest:List.of("sha256:"+"a".repeat(64),"A".repeat(64),"a".repeat(63))) {
            assertThatThrownBy(()->new DdcSiReferenceBO.ExpectedIdentityVO(1L,2L,3L,digest,false,null)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->new DdcSiReferenceBO.ExpectedIdentityVO(1L,2L,3L,"a".repeat(64),false,"owner")).isInstanceOf(IllegalArgumentException.class);
        for(String owner:List.of("", " owner", "owner ", "owner\n")) {
            assertThatThrownBy(()->new DdcSiReferenceBO.ExpectedIdentityVO(1L,2L,3L,"a".repeat(64),true,owner)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new DdcSiReferenceBO.ExpectedIdentityVO(1L,2L,3L,"a".repeat(64),true,null).ownerPresent()).isTrue();
    }

    @Test
    void persistedReferenceProjectionDoesNotInventOrPersistInternalExpectedIdentity() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext(
                top.egon.cola.component.tianshu.admin.model.convertor.DdcSiResourceConverterImpl.class,
                top.egon.cola.component.tianshu.admin.model.convertor.DdcSiEnumConverterImpl.class)) {
            var converter=context.getBean(top.egon.cola.component.tianshu.admin.model.convertor.DdcSiResourceConverter.class);
            var po=top.egon.cola.component.tianshu.admin.model.po.DdcSiReferencePO.builder().id(1L).tenantId(1L)
                    .sourceType(top.egon.cola.component.tianshu.admin.model.enums.DdcSiReferenceTargetTypePersistenceEnum.AGENT)
                    .sourceKey("source").targetKind(top.egon.cola.component.tianshu.admin.model.enums.DdcAiKindPersistenceEnum.MCP)
                    .targetKey("target").targetVersionNo(3L).envCode("prod").namespaceId(10L).build();
            var restored=converter.toReferenceBo(po);
            assertThat(restored.getExpectedIdentity()).isNull();
            assertThat(restored.getPinnedVersionNo()).isEqualTo(3L);
            restored.setExpectedIdentity(new DdcSiReferenceBO.ExpectedIdentityVO(11L,12L,3L,"a".repeat(64),true,"tools"));
            assertThat(new ObjectMapper().valueToTree(restored).has("expectedIdentity")).isFalse();
            var persistedAgain=converter.toReferencePo(restored);
            assertThat(persistedAgain.getTargetVersionNo()).isEqualTo(3L);
            assertThat(converter.toReferenceBo(persistedAgain).getExpectedIdentity()).isNull();
        }
    }

    private static DdcSiReferenceBO edge(DdcSiReferenceTargetTypeEnum srcType, String srcKey,
                                         DdcSiReferenceTargetTypeEnum targetType, String targetKey) {
        return DdcSiReferenceBO.builder()
                .sourceType(srcType)
                .sourceKey(srcKey)
                .targetType(targetType)
                .targetKey(targetKey)
                .required(true)
                .build();
    }
}
