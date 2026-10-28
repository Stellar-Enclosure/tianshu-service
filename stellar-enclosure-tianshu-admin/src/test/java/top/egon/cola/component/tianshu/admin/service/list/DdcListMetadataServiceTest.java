package top.egon.cola.component.tianshu.admin.service.list;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.repository.DdcResourceMetadataProjectionRepository;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcCatalogActivationService;
import top.egon.cola.component.tianshu.admin.dao.DdcListDefinitionMapper;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogSelectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListDefinitionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcResourceMetadataProjectionBO;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ActivationDescriptor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifestCanonicalizer;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionRequirement;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcListMetadataServiceTest {
    private final DdcCatalogActivationService activations = mock(DdcCatalogActivationService.class);
    private final DdcResourceMetadataProjectionRepository resources = mock(DdcResourceMetadataProjectionRepository.class);
    private final DdcListDefinitionMapper definitions = mock(DdcListDefinitionMapper.class);
    private final DdcListMetadataService service = new DdcListMetadataService(activations, resources, definitions,
            mock(DdcMetadataScopeRepository.class), mock(EgonColaIdentifierGenerator.class));
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void registeredColumnsReconstructTheBusinessDefinitionAndItsStableDigest() throws Exception {
        var definition = DdcNamespaceListDefinition.DEFINITION;
        var columns = definition.columns();
        var encoded = java.util.stream.IntStream.range(0, columns.size()).mapToObj(index -> {
            var column = columns.get(index);
            return String.format(java.util.Locale.ROOT, "%03d|%s|%s|%s|%s|1|1", index, column.columnKey(),
                    column.fieldKeys().getFirst(), column.labelPage(), column.labelKey());
        }).toList();
        String digest = "8da5e9b35f8eb402f60741cfa236359d85bed300914f3c6f8477789857fdd9c3";
        assertThat(definition.digest()).isEqualTo(digest);
        String json = mapper.writeValueAsString(Map.of("kind", "LIST", "pageResourceKey", definition.pageResourceKey(),
                "listKey", definition.listKey(), "definitionDigest", digest, "columns", encoded,
                "pageApiKey", "tianshu.api.namespace.page", "systemTemplateApiKey", "tianshu.api.listTemplate.saveSystem",
                "personalTemplateApiKey", "tianshu.api.listTemplate.savePersonal", "scopePolicyDigest", "d4dd701a17eb06971ddf50cc4d4cee369a7f0e9822fd42ef4bc41334b3d9d5d7"));
        assertThat(service.parse(json)).isEqualTo(definition);
        String changed = json.replace("000|bizCode", "001|bizCode");
        assertThatThrownBy(() -> service.parse(changed)).hasMessage("TIANSHU_LIST_METADATA_UNAVAILABLE");
    }

    @Test void rejectsAListWhoseDeclaredDigestDoesNotMatchItsColumns() throws Exception {
        String json = mapper.writeValueAsString(Map.of("kind", "LIST", "pageResourceKey", "order.page.list", "listKey", "main",
                "definitionDigest", "0".repeat(64), "columns", List.of("000|id|order.field.id|order.list|field.id|1|1"),
                "pageApiKey", "order.api.list", "systemTemplateApiKey", "order.api.template.system",
                "personalTemplateApiKey", "order.api.template.personal", "scopePolicyDigest", "d4dd701a17eb06971ddf50cc4d4cee369a7f0e9822fd42ef4bc41334b3d9d5d7"));
        assertThatThrownBy(() -> service.parse(json)).hasMessage("TIANSHU_LIST_METADATA_UNAVAILABLE");
    }

    @Test void activeProducerAndCachedDefinitionMustMatchTheHostAndCatalog() throws Exception {
        var local = DdcNamespaceListDefinition.DEFINITION;
        var columns = java.util.stream.IntStream.range(0, local.columns().size()).mapToObj(index -> {
            var column = local.columns().get(index);
            return String.format(java.util.Locale.ROOT, "%03d|%s|%s|%s|%s|1|1", index, column.columnKey(),
                    column.fieldKeys().getFirst(), column.labelPage(), column.labelKey());
        }).toList();
        Map<String, Object> target = Map.of("kind", "LIST", "pageResourceKey", local.pageResourceKey(),
                "listKey", local.listKey(), "definitionDigest", local.digest(), "columns", columns,
                "pageApiKey", "tianshu.api.namespace.page", "systemTemplateApiKey", "tianshu.api.listTemplate.saveSystem",
                "personalTemplateApiKey", "tianshu.api.listTemplate.savePersonal", "scopePolicyDigest", "d4dd701a17eb06971ddf50cc4d4cee369a7f0e9822fd42ef4bc41334b3d9d5d7");
        String json = CanonicalJson.write(target);
        var scope = new CatalogScope("10", "20", "xingyuan", "tianshu", "default", "test");
        var view = new EffectivePermissionSnapshot("https://identity.example/tenants/20", "alice", scope, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianshu", List.of("7"), List.of("7")),
                List.of("TIANSHU_READ"), Instant.now().plusSeconds(60));
        var snapshot = mock(SystemAuthorizationSnapshot.class);
        when(snapshot.permissionSnapshot()).thenReturn(view);
        when(snapshot.permissions()).thenReturn(Set.of("TIANSHU_READ"));
        when(snapshot.resourceCodes()).thenReturn(Set.of(local.pageResourceKey(), "tianshu.component.namespaces.main"));
        var descriptor = mock(ActivationDescriptor.class);
        var producer = mock(ActivationDescriptor.ProducerVersion.class);
        when(producer.artifactDigest()).thenReturn("a".repeat(64));
        when(descriptor.requireProducer("tianshu-web", "7")).thenReturn(producer);
        when(activations.resolve(scope, "1")).thenReturn(new DdcCatalogSelectionBO(99L, descriptor,
                List.of(new DdcCatalogSelectionBO.Member("tianshu-web", "7", "b".repeat(64), 44L))));
        var row = DdcResourceMetadataProjectionBO.builder().tenantId(20L).catalogScopeId(10L).sourceId(44L)
                .producerId("tianshu-web").sourceVersion("b".repeat(64)).resourceType("COMPONENT")
                .definitionLifecycle("ACTIVE").targetJson(json).policyJson(CanonicalJson.write(Map.of(
                        "key", "tianshu.component.namespaces.main", "type", "COMPONENT", "target", target,
                        "access", "PROTECTED", "lifecycle", "ACTIVE", "allowedPrincipals", List.of("USER"),
                        "oauthScopes", List.of(), "requirement", Map.of("mode", "ALL", "codes", List.of("TIANSHU_READ")),
                        "i18n", Map.of("pageCode", "tianshu.web.permissions", "labelKey", "name")))).build();
        when(resources.findActivatedResource(10L, 99L, "tianshu.component.namespaces.main")).thenReturn(List.of(row));
        var page = new PermissionManifest.ResourceDefinition(local.pageResourceKey(), ResourceType.PAGE, Map.of("route", "/namespaces"),
                ResourceAccess.PROTECTED, new PermissionRequirement(PermissionMode.ALL, List.of("TIANSHU_READ")), null,
                List.of(PermissionManifest.PrincipalType.USER), List.of(), new PermissionManifest.I18nReference("tianshu.web.permissions", "name"), null);
        var pageRow = DdcResourceMetadataProjectionBO.builder().tenantId(20L).catalogScopeId(10L).sourceId(44L)
                .producerId("tianshu-web").sourceVersion("b".repeat(64)).resourceType("PAGE").definitionLifecycle("ACTIVE")
                .targetJson(CanonicalJson.write(page.target())).policyJson(CanonicalJson.write(new PermissionManifestCanonicalizer().resourceMap(page))).build();
        when(resources.findActivatedResource(10L, 99L, local.pageResourceKey())).thenReturn(List.of(pageRow));
        var cached = new DdcListDefinitionBO(); cached.setTenantId(20L); cached.setCatalogScopeId(10L);
        cached.setActivationVersion(1L); cached.setProducerId("tianshu-web"); cached.setProducerVersion(7L);
        cached.setPageResourceKey(local.pageResourceKey()); cached.setListKey(local.listKey());
        cached.setArtifactDigest("a".repeat(64)); cached.setDefinitionDigest(local.digest()); cached.setDefinitionJson(json);
        when(definitions.find(20L, 10L, 1L, local.pageResourceKey(), local.listKey())).thenReturn(cached);
        var localPolicy = new Rbac3StarterProperties.UserScopePolicyProperties();
        assertThat(service.resolveLocal(snapshot, DdcNamespaceListDefinition.BINDING, localPolicy, Set.of())).isEqualTo(local);
        var forgedPolicy = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true)
                .setPermissionScopes(Map.of("TIANSHU_READ", Set.of("scope:read")));
        assertThatThrownBy(() -> service.resolveDelegated(snapshot, DdcNamespaceListDefinition.BINDING, forgedPolicy, Set.of("scope:read")))
                .hasMessage("TIANSHU_LIST_METADATA_UNAVAILABLE");
        when(snapshot.permissions()).thenReturn(Set.of());
        assertThatThrownBy(() -> service.resolveLocal(snapshot, DdcNamespaceListDefinition.BINDING, localPolicy, Set.of())).hasMessage("TIANSHU_LIST_METADATA_UNAVAILABLE");
        when(snapshot.permissions()).thenReturn(Set.of("TIANSHU_READ"));
        cached.setArtifactDigest("c".repeat(64));
        assertThatThrownBy(() -> service.resolveLocal(snapshot, DdcNamespaceListDefinition.BINDING, localPolicy, Set.of())).hasMessage("TIANSHU_LIST_METADATA_UNAVAILABLE");
        when(snapshot.resourceCodes()).thenReturn(Set.of(local.pageResourceKey()));
        assertThatThrownBy(() -> service.resolveLocal(snapshot, DdcNamespaceListDefinition.BINDING, localPolicy, Set.of())).hasMessage("TIANSHU_LIST_METADATA_UNAVAILABLE");
    }

    @Test void delegatedFieldVisibilityUsesTheVerifiedUsersScopesAndActiveFieldPolicy() {
        var scope = new CatalogScope("10", "20", "trade", "shop", "default", "test");
        var view = new EffectivePermissionSnapshot("https://idp.example/tenants/20", "alice", scope, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("shop", List.of("7"), List.of("7")),
                List.of("shop:phone:plain", "shop:read"), Instant.now().plusSeconds(60));
        var snapshot = mock(SystemAuthorizationSnapshot.class);
        when(snapshot.permissionSnapshot()).thenReturn(view);
        when(snapshot.permissions()).thenReturn(Set.of("shop:phone:plain", "shop:read"));
        when(snapshot.resourceCodes()).thenReturn(Set.of("shop.field.phone"));
        var descriptor = mock(ActivationDescriptor.class);
        when(activations.resolve(scope, "1")).thenReturn(new DdcCatalogSelectionBO(99L, descriptor,
                List.of(new DdcCatalogSelectionBO.Member("shop-java", "4", "b".repeat(64), 44L))));
        var field = new PermissionManifest.ResourceDefinition("shop.field.phone", ResourceType.FIELD,
                Map.of("dto", "shop.Customer", "property", "phone"), ResourceAccess.PROTECTED, null,
                new PermissionManifest.FieldPolicy(PermissionManifest.JsonFieldType.STRING,
                        new PermissionRequirement(PermissionMode.ALL, List.of("shop:phone:plain")),
                        new PermissionRequirement(PermissionMode.ALL, List.of("shop:read")), null, "PHONE"),
                List.of(PermissionManifest.PrincipalType.USER), List.of(), new PermissionManifest.I18nReference("shop.list", "field.phone"), null);
        var row = DdcResourceMetadataProjectionBO.builder().tenantId(20L).catalogScopeId(10L).sourceId(44L)
                .producerId("shop-java").sourceVersion("b".repeat(64)).definitionKey("shop.field.phone").resourceType("FIELD").definitionLifecycle("ACTIVE")
                .targetJson(CanonicalJson.write(field.target())).policyJson(CanonicalJson.write(new PermissionManifestCanonicalizer().resourceMap(field))).build();
        when(resources.findActivatedResource(10L, 99L, "shop.field.phone")).thenReturn(List.of(row));
        var policy = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true)
                .setPermissionScopes(Map.of("shop:phone:plain", Set.of("scope:plain"), "shop:read", Set.of("scope:read")));
        assertThat(service.delegatedFieldReadable(snapshot, "shop.field.phone", policy, Set.of("scope:read"))).isTrue();
        assertThat(service.delegatedFieldReadable(snapshot, "shop.field.phone", policy, Set.of())).isFalse();
        when(resources.findActivatedResources(10L, 99L, List.of("shop.field.phone"))).thenReturn(List.of(row));
        assertThat(service.delegatedFieldReadability(snapshot, Set.of("shop.field.phone", "shop.field.disabled"), policy, Set.of("scope:read")))
                .containsEntry("shop.field.phone", true).containsEntry("shop.field.disabled", false);
        when(resources.findActivatedResources(10L, 99L, List.of("shop.field.phone"))).thenReturn(List.of());
        assertThatThrownBy(() -> service.delegatedFieldReadability(snapshot, Set.of("shop.field.phone"), policy, Set.of("scope:read")))
                .hasMessage("TIANSHU_LIST_METADATA_UNAVAILABLE");
    }
}
