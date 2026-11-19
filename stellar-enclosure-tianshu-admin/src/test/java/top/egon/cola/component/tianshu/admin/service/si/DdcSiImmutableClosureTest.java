package top.egon.cola.component.tianshu.admin.service.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcSiImmutableClosureTest {

    private static final Long TENANT = 1L;
    private final DdcSiResourceRepository repository = mock(DdcSiResourceRepository.class);
    private final ObjectMapper json = new ObjectMapper();
    private final DdcSiDependencyValidator validator = new DdcSiDependencyValidator(repository, json);

    @Test
    void publishingWithMcpV4LeavesV1AndItsRollbackOnV3() {
        var agent = resource(20L, "agent-a", DdcSiKindEnum.AGENT, 119L);
        var mcp = resource(10L, "mcp-a", DdcSiKindEnum.MCP, 104L);
        var v3 = leaf(103L, 10L, 3L, DdcSiKindEnum.MCP);
        var historicalIdentity = identity(v3);
        when(repository.findByBusinessKey(TENANT, "ai", 1L, "prod", "owner", DdcSiKindEnum.MCP, "mcp-a"))
                .thenReturn(Optional.of(mcp));
        when(repository.findActiveForUpdateById(TENANT, agent.getId())).thenReturn(Optional.of(agent));
        when(repository.findActiveForUpdateById(TENANT, mcp.getId())).thenReturn(Optional.of(mcp));
        when(repository.findHistoryVersionByResourceAndNo(TENANT, mcp.getId(), 3L)).thenReturn(Optional.of(v3));
        when(repository.findHistoryVersionsByIds(TENANT, List.of(103L))).thenReturn(List.of(v3));
        when(repository.allocateNextVersionNo(TENANT, agent.getId())).thenReturn(1L, 2L, 3L);
        when(repository.allocateVersionId()).thenReturn(120L, 121L, 122L);

        var first = version(null, agent.getId(), null, DdcSiKindEnum.AGENT);
        validator.freezePublication(agent, first, null, List.of(DdcSiReferenceBO.builder()
                .targetKind(DdcSiKindEnum.MCP).targetKey("mcp-a").pinnedVersionNo(3L).build()), List.of());
        var saved = validator.requireHistorical(first);
        assertThat(saved.nodes()).containsExactly(historicalIdentity);
        assertThat(saved.edges()).containsExactly(new EdgeVO("120", "103"));
        verify(repository, never()).findHistoryVersionsByIds(TENANT, List.of(104L));

        var v4 = leaf(104L, 10L, 4L, DdcSiKindEnum.MCP);
        when(repository.findHistoryVersionsByIds(TENANT, List.of(104L))).thenReturn(List.of(v4));
        var second = version(null, agent.getId(), null, DdcSiKindEnum.AGENT);
        validator.freezePublication(agent, second, null, List.of(DdcSiReferenceBO.builder()
                .targetKind(DdcSiKindEnum.MCP).targetKey("mcp-a").build()), List.of());
        assertThat(validator.requireHistorical(second).nodes()).containsExactly(identity(v4));
        assertThat(validator.requireHistorical(first)).isEqualTo(saved);

        var rollback = version(null, agent.getId(), null, DdcSiKindEnum.AGENT);
        validator.freezeRollback(rollback, saved);
        when(repository.findHistoryVersionsByIds(TENANT, List.of(103L, 120L))).thenReturn(List.of(v3, first));
        var reconstructed = validator.requireHistorical(rollback);
        assertThat(reconstructed.source()).isEqualTo(saved.root());
        assertThat(reconstructed.nodes()).containsExactly(historicalIdentity);
        assertThat(reconstructed.edges()).containsExactly(new EdgeVO("122", "103"));

        var lockOrder = inOrder(repository);
        lockOrder.verify(repository).findActiveForUpdateById(TENANT, 10L);
        lockOrder.verify(repository).findActiveForUpdateById(TENANT, 20L);
    }

    @Test
    void rejectsFrozenGraphMissingAnEdgeFromAChildPublication() {
        var leaf = leaf(103L, 10L, 3L, DdcSiKindEnum.MCP);
        var child = version(115L, 15L, 1L, DdcSiKindEnum.AGENT);
        setManifest(child, List.of(identity(leaf)), List.of(new EdgeVO("115", "103")));
        var root = version(120L, 20L, 1L, DdcSiKindEnum.AGENT);
        setManifest(root, List.of(identity(child)), List.of(new EdgeVO("120", "115")));
        when(repository.findHistoryVersionsByIds(TENANT, List.of(115L))).thenReturn(List.of(child));

        assertThatThrownBy(() -> validator.requireHistorical(root)).isInstanceOf(CommonException.class)
                .hasMessageContaining("history is unavailable");
    }

    @Test
    void missingOrTamperedDependencyFailsAndBatchesNeverExceedOneHundred() {
        var root = version(120L, 20L, 1L, DdcSiKindEnum.AGENT);
        List<DdcSiVersionBO> children = LongStream.rangeClosed(200, 300)
                .mapToObj(id -> leaf(id, id, 1L, DdcSiKindEnum.MCP)).toList();
        setManifest(root, children.stream().map(DdcSiImmutableClosureTest::identity).toList(),
                children.stream().map(row -> new EdgeVO("120", row.getId().toString())).toList());
        List<List<Long>> batches = new java.util.ArrayList<>();
        when(repository.findHistoryVersionsByIds(eq(TENANT), anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(1);
            batches.add(List.copyOf(ids));
            return children.stream().filter(row -> ids.contains(row.getId())).toList();
        });

        assertThat(validator.requireHistorical(root).nodes()).hasSize(101);
        assertThat(batches.stream().map(List::size)).containsExactly(100, 1);
        children.getLast().setSpecJson("{\"changed\":true}");
        assertThatThrownBy(() -> validator.requireHistorical(root)).isInstanceOf(CommonException.class)
                .hasMessageContaining("history is unavailable");
    }

    @Test
    void staleCurrentPointerStopsPublicationBeforeAllocatingAFixedIdentity() {
        var snapshot = resource(20L, "agent-a", DdcSiKindEnum.AGENT, 119L);
        var locked = resource(20L, "agent-a", DdcSiKindEnum.AGENT, 120L);
        when(repository.findActiveForUpdateById(TENANT, 20L)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> validator.freezePublication(snapshot, version(null, 20L, null, DdcSiKindEnum.AGENT),
                null, List.of(), List.of())).isInstanceOf(CommonException.class).hasMessageContaining("input changed");
        verify(repository, never()).allocateVersionId();
    }

    @Test
    void exactTuplePinsApprovedHistoryEvenWhenCurrentPointerHasAdvanced() {
        var source = resource(20L, "agent-a", DdcSiKindEnum.AGENT, 119L);
        var target = resource(10L, "mcp-a", DdcSiKindEnum.MCP, 104L);
        var v3 = leaf(103L, 10L, 3L, DdcSiKindEnum.MCP);
        prepareExact(source, target, v3, "owner");
        var candidate = version(null, source.getId(), null, source.getKind());
        var ref = exactReference(10L,103L,3L,v3.getDigestSha256(),false,null);
        validator.freezePublication(source,candidate,null,List.of(ref),List.of());
        var manifest=validator.requireHistorical(candidate);
        assertThat(manifest.nodes()).containsExactly(identity(v3));
        verify(repository,never()).findHistoryVersionsByIds(TENANT,List.of(104L));
        assertThat(v3.getSpecJson()).isEqualTo("{}");
    }

    @Test
    void contradictoryExpectedResourceVersionDigestAndPinsNeverAllocateAnIdentity() {
        for (int dimension=0;dimension<5;dimension++) {
            org.mockito.Mockito.reset(repository);
            var source=resource(20L,"agent-a",DdcSiKindEnum.AGENT,119L);
            var target=resource(10L,"mcp-a",DdcSiKindEnum.MCP,104L);
            var child=leaf(103L,10L,3L,DdcSiKindEnum.MCP);
            prepareExact(source,target,child,"owner");
            var ref=exactReference(dimension==0?11L:10L,dimension==1?999L:103L,3L,
                    dimension==2?"0".repeat(64):child.getDigestSha256(),false,null);
            if (dimension==3) ref.setPinnedVersionNo(4L);
            if (dimension==4) ref.setTargetVersionNo(4L);
            var candidate=version(null,source.getId(),null,source.getKind());
            assertThatThrownBy(()->validator.freezePublication(source,candidate,null,List.of(ref),List.of()))
                    .isInstanceOf(CommonException.class);
            verify(repository,never()).allocateVersionId();
            assertThat(candidate.getDependencyManifestJson()).isNull();
        }
    }

    @Test
    void namedAndBusinessOwnersRequireExactActiveBindingFromTheSourceOwner() {
        for(String owner:java.util.Arrays.asList("other-owner",null)) {
            org.mockito.Mockito.reset(repository);
            var source=resource(20L,"agent-a",DdcSiKindEnum.AGENT,119L);
            var target=resource(10L,"mcp-a",DdcSiKindEnum.MCP,104L).setAppCode(owner==null?"":owner);
            var child=leaf(103L,10L,3L,DdcSiKindEnum.MCP);
            prepareExact(source,target,child,target.getAppCode());
            when(repository.findActiveBinding(TENANT,"ai",1L,"prod","owner",DdcSiKindEnum.MCP,"mcp-a"))
                    .thenReturn(Optional.of(binding(source,target)));
            var candidate=version(null,source.getId(),null,source.getKind());
            validator.freezePublication(source,candidate,null,List.of(exactReference(10L,103L,3L,child.getDigestSha256(),true,owner)),List.of());
            assertThat(validator.requireHistorical(candidate).nodes()).containsExactly(identity(child));
            verify(repository).findByBusinessKey(TENANT,"ai",1L,"prod",target.getAppCode(),DdcSiKindEnum.MCP,"mcp-a");
        }
    }

    @Test
    void missingWrongScopeWrongResourceOrWrongPinBindingCannotAuthorizeCrossOwner() {
        for(int dimension=0;dimension<8;dimension++) {
            org.mockito.Mockito.reset(repository);
            var source=resource(20L,"agent-a",DdcSiKindEnum.AGENT,119L);
            var target=resource(10L,"mcp-a",DdcSiKindEnum.MCP,104L).setAppCode("other-owner");
            var child=leaf(103L,10L,3L,DdcSiKindEnum.MCP);
            prepareExact(source,target,child,"other-owner");
            var binding=binding(source,target);
            if(dimension==1) binding.setTenantId(2L);
            if(dimension==2) binding.setNamespaceId(2L);
            if(dimension==3) binding.setEnvCode("test");
            if(dimension==4) binding.setAppCode("not-source-owner");
            if(dimension==5) binding.setResourceId(999L);
            if(dimension==6) binding.setPinnedVersionNo(4L);
            if(dimension==7) binding.setStatus(top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum.INACTIVE);
            when(repository.findActiveBinding(TENANT,"ai",1L,"prod","owner",DdcSiKindEnum.MCP,"mcp-a"))
                    .thenReturn(dimension==0?Optional.empty():Optional.of(binding));
            var ref=exactReference(10L,103L,3L,child.getDigestSha256(),true,"other-owner");
            assertThatThrownBy(()->validator.freezePublication(source,version(null,20L,null,DdcSiKindEnum.AGENT),null,List.of(ref),List.of()))
                    .isInstanceOf(CommonException.class);
            verify(repository,never()).allocateVersionId();
        }
    }

    @Test
    void businessOwnedSourceNeverAcquiresAnImplicitApplicationCaller() {
        var source=resource(20L,"agent-a",DdcSiKindEnum.AGENT,119L).setAppCode("");
        var target=resource(10L,"mcp-a",DdcSiKindEnum.MCP,104L).setAppCode("other-owner");
        var child=leaf(103L,10L,3L,DdcSiKindEnum.MCP);
        prepareExact(source,target,child,"other-owner");
        var ref=exactReference(10L,103L,3L,child.getDigestSha256(),true,"other-owner");
        assertThatThrownBy(()->validator.freezePublication(source,version(null,20L,null,DdcSiKindEnum.AGENT),null,List.of(ref),List.of()))
                .isInstanceOf(CommonException.class);
        verify(repository,never()).findActiveBinding(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        verify(repository,never()).allocateVersionId();
    }

    @Test
    void sameKeyAndKindAcrossExplicitlyAuthorizedOwnersIsNotASelfCycle() {
        var source=resource(20L,"mcp-a",DdcSiKindEnum.MCP,119L);
        var target=resource(10L,"mcp-a",DdcSiKindEnum.MCP,104L).setAppCode("other-owner");
        var child=leaf(103L,10L,3L,DdcSiKindEnum.MCP);
        prepareExact(source,target,child,"other-owner");
        when(repository.findActiveBinding(TENANT,"ai",1L,"prod","owner",DdcSiKindEnum.MCP,"mcp-a"))
                .thenReturn(Optional.of(binding(source,target)));
        var candidate=version(null,20L,null,DdcSiKindEnum.MCP);
        validator.freezePublication(source,candidate,null,List.of(exactReference(10L,103L,3L,child.getDigestSha256(),true,"other-owner")),List.of());
        assertThat(validator.requireHistorical(candidate).nodes()).containsExactly(identity(child));
    }

    @Test
    void exactIdentityCannotAdoptAResourceReturnedFromAnotherScope() {
        for(int dimension=0;dimension<8;dimension++) {
            org.mockito.Mockito.reset(repository);
            var source=resource(20L,"agent-a",DdcSiKindEnum.AGENT,119L);
            var target=resource(10L,"mcp-a",DdcSiKindEnum.MCP,104L);
            var child=leaf(103L,10L,3L,DdcSiKindEnum.MCP);
            if(dimension==0) target.setTenantId(2L);
            if(dimension==1) target.setBizCode("other");
            if(dimension==2) target.setNamespaceId(2L);
            if(dimension==3) target.setEnvCode("test");
            if(dimension==4) target.setAppCode("other");
            if(dimension==5) target.setKind(DdcSiKindEnum.SKILL);
            if(dimension==6) target.setResourceKey("other");
            if(dimension==7) target.setDeletedAt(java.time.LocalDateTime.of(2026,11,1,0,0));
            prepareExact(source,target,child,"owner");
            var ref=exactReference(10L,103L,3L,child.getDigestSha256(),false,null);
            assertThatThrownBy(()->validator.freezePublication(source,version(null,20L,null,DdcSiKindEnum.AGENT),null,List.of(ref),List.of()))
                    .isInstanceOf(CommonException.class);
            verify(repository,never()).allocateVersionId();
        }
    }

    @Test
    void explicitSameOwnerAndExistingBusinessScopeDoNotInventCrossOwnerGrants() {
        for(String owner:List.of("owner","")) {
            org.mockito.Mockito.reset(repository);
            var source=resource(20L,"agent-a",DdcSiKindEnum.AGENT,119L).setAppCode(owner);
            var target=resource(10L,"mcp-a",DdcSiKindEnum.MCP,104L).setAppCode(owner);
            var child=leaf(103L,10L,3L,DdcSiKindEnum.MCP);
            prepareExact(source,target,child,owner);
            var candidate=version(null,20L,null,DdcSiKindEnum.AGENT);
            validator.freezePublication(source,candidate,null,List.of(exactReference(10L,103L,3L,child.getDigestSha256(),true,owner.isEmpty()?null:owner)),List.of());
            assertThat(validator.requireHistorical(candidate).nodes()).containsExactly(identity(child));
            verify(repository,never()).findActiveBinding(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        }
    }

    private void prepareExact(DdcSiResourceBO source,DdcSiResourceBO target,DdcSiVersionBO child,String lookupOwner) {
        when(repository.findByBusinessKey(TENANT,"ai",1L,"prod",lookupOwner,DdcSiKindEnum.MCP,"mcp-a")).thenReturn(Optional.of(target));
        when(repository.findActiveForUpdateById(TENANT,source.getId())).thenReturn(Optional.of(source));
        when(repository.findActiveForUpdateById(TENANT,target.getId())).thenReturn(Optional.of(target));
        when(repository.findHistoryVersionByResourceAndNo(TENANT,target.getId(),3L)).thenReturn(Optional.of(child));
        when(repository.findHistoryVersionsByIds(TENANT,List.of(103L))).thenReturn(List.of(child));
        when(repository.allocateNextVersionNo(TENANT,source.getId())).thenReturn(1L);
        when(repository.allocateVersionId()).thenReturn(120L);
    }
    private static top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO binding(DdcSiResourceBO source,DdcSiResourceBO target) {
        return top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO.builder().tenantId(TENANT).bizCode("ai").namespaceId(1L)
                .envCode("prod").appCode(source.getAppCode()).kind(target.getKind()).resourceId(target.getId()).resourceKey(target.getResourceKey())
                .status(top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum.ACTIVE).pinnedVersionNo(3L).build();
    }
    private static DdcSiReferenceBO exactReference(Long resourceId,Long versionId,Long versionNo,String digest,boolean ownerPresent,String owner) {
        return DdcSiReferenceBO.builder().targetKind(DdcSiKindEnum.MCP).targetKey("mcp-a")
                .expectedIdentity(new DdcSiReferenceBO.ExpectedIdentityVO(resourceId,versionId,versionNo,digest,ownerPresent,owner)).build();
    }

    private static DdcSiResourceBO resource(Long id, String key, DdcSiKindEnum kind, Long currentVersionId) {
        return DdcSiResourceBO.builder().id(id).tenantId(TENANT).bizCode("ai").namespaceId(1L).envCode("prod")
                .appCode("owner").version(1L).resourceKey(key).kind(kind).currentVersionId(currentVersionId)
                .lifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED).build();
    }

    private DdcSiVersionBO leaf(Long id, Long resourceId, Long no, DdcSiKindEnum kind) {
        var leaf = version(id, resourceId, no, kind);
        setManifest(leaf, List.of(), List.of());
        return leaf;
    }

    private void setManifest(DdcSiVersionBO row, List<VersionIdentityVO> nodes, List<EdgeVO> edges) {
        row.setDependencyManifestJson(json.valueToTree(DdcSiDependencyManifestVO.create(identity(row), null, nodes, edges)));
    }

    private static VersionIdentityVO identity(DdcSiVersionBO row) {
        return new VersionIdentityVO(TENANT.toString(), row.getResourceId().toString(), row.getId().toString(),
                row.getVersionNo().toString(), row.getKind(), row.getEnvCode(), row.getDigestSha256());
    }

    private static DdcSiVersionBO version(Long id, Long resourceId, Long no, DdcSiKindEnum kind) {
        return DdcSiVersionBO.builder().id(id).tenantId(TENANT).resourceId(resourceId).versionNo(no).kind(kind)
                .envCode("prod").specJson("{}").digestSha256(DdcSiContentStrategy.sha256Hex("{}")).build();
    }
}
