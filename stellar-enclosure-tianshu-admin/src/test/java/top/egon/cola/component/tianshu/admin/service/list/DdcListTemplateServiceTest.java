package top.egon.cola.component.tianshu.admin.service.list;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.controller.list.DdcListTemplateController;
import top.egon.cola.component.tianshu.admin.dao.DdcListTemplateMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcListTemplateBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcListTemplateCommand;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRecorder;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.component.tianshu.admin.security.management.DdcListDelegationVerifier;
import top.egon.cola.component.tianshu.api.list.ListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionRequirement;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.UserPermissionScopePolicy;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.net.URI;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcListTemplateServiceTest {
    private static final String ISSUER = "https://identity.example/tenants/20";
    private static final String PAGE = "tianshu.page.namespaces";
    private static final Set<String> GRANTS = Set.of("TIANSHU_READ", "TIANSHU_NAMESPACE_TEMPLATE_PERSONAL");
    private final DdcListTemplateMapper mapper = mock(DdcListTemplateMapper.class);
    private final DdcMetadataScopeRepository scopes = mock(DdcMetadataScopeRepository.class);
    private final OperationLogRecorder audit = mock(OperationLogRecorder.class);
    private final DdcScopeGate scopeGate = mock(DdcScopeGate.class);
    private final DdcListMetadataService metadata = mock(DdcListMetadataService.class);
    private final EgonColaIdentifierGenerator ids = mock(EgonColaIdentifierGenerator.class);
    private final CurrentRbac3User users = mock(CurrentRbac3User.class);
    private final PermissionExecutionGuard execution = mock(PermissionExecutionGuard.class);
    private final PermissionFieldBindingGuard fields = mock(PermissionFieldBindingGuard.class);
    private final PermissionPolicyRegistry policies = mock(PermissionPolicyRegistry.class);
    private final Rbac3StarterProperties properties = new Rbac3StarterProperties();
    private final DdcListTemplateService service = new DdcListTemplateService(mapper, scopes, audit, scopeGate, metadata, ids, users, execution, fields, policies,
            List.of(DdcNamespaceListDefinition.BINDING));
    private final Rbac3UserDetails user = mock(Rbac3UserDetails.class);
    private final SystemAuthorizationSnapshot snapshot = mock(SystemAuthorizationSnapshot.class);
    private final CatalogScope scope = new CatalogScope("10", "20", "xingyuan", "tianshu", "default", "test");

    @BeforeEach void setup() throws Exception {
        MDC.put("tenantId", "20");
        var request = new MockHttpServletRequest();
        var method = DdcListTemplateController.class.getMethod("savePersonal", String.class, String.class, DdcListTemplateCommand.class);
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler", new HandlerMethod(mock(DdcListTemplateController.class), method));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var view = new EffectivePermissionSnapshot(ISSUER, "alice", scope, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianshu", List.of("7"), List.of("7")),
                GRANTS.stream().sorted().toList(), Instant.now().plusSeconds(60));
        when(users.require()).thenReturn(user);
        when(user.snapshot()).thenReturn(snapshot);
        when(user.permissions()).thenReturn(GRANTS);
        when(snapshot.permissionSnapshot()).thenReturn(view);
        when(execution.require(anyString())).thenReturn(user);
        var scopePolicy = new UserPermissionScopePolicy(properties.getUserScopePolicy());
        when(execution.userScopePolicy()).thenReturn(scopePolicy);
        when(fields.userScopePolicy()).thenReturn(scopePolicy);
        when(metadata.resolveLocal(eq(snapshot), any(), any(), anySet())).thenReturn(DdcNamespaceListDefinition.DEFINITION);
        var target = DdcMetadataScopeBO.builder().tenantId(20L).catalogScopeId(10L).bizCode("xingyuan")
                .appCode("tianshu").namespaceCode("default").envCode("test").build();
        when(scopes.lockEnabledTarget(10L)).thenReturn(Optional.of(target));
        when(scopes.findByCatalogScopeId(10L)).thenReturn(Optional.of(target));
        when(mapper.selectAccessible(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());
        when(policies.require(anyString(), eq(ResourceType.API))).thenAnswer(call -> api(call.getArgument(0)));
        when(policies.require(anyString(), eq(ResourceType.FIELD))).thenAnswer(call -> field(call.getArgument(0)));
    }

    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); MDC.clear(); }

    @Test void templateManagementCapabilitiesFollowTheCurrentUserPermissions() {
        var view = service.load(PAGE, "main", "1", false);
        assertThat(view.mayManageSystem()).isFalse();
        assertThat(view.mayManagePersonal()).isTrue();
    }

    @Test void aPersonalEditKeepsPreviouslySelectedFieldsThatAreNowForbidden() throws Exception {
        var row = personalRow("alice");
        when(mapper.lockById(20L, 10L, PAGE, "main", 123L)).thenReturn(row);
        when(mapper.selectColumns(20L, 10L, 123L)).thenReturn(List.of("namespace", "description"));
        when(mapper.updateTemplate(any(), eq(4L))).thenReturn(1);
        when(mapper.insertColumn(anyLong(), anyLong(), anyLong(), anyString(), anyInt())).thenReturn(1);
        var result = service.save(PAGE, "main", "1", DdcNamespaceListDefinition.DEFINITION.digest(), "PERSONAL",
                "123", "4", "Mine", List.of("bizCode"), false);
        assertThat(result.columnKeys()).containsExactly("bizCode");
        verify(mapper).insertColumn(20L, 10L, 123L, "bizCode", 0);
        verify(mapper).insertColumn(20L, 10L, 123L, "description", 1);
        verify(mapper, never()).insertColumn(20L, 10L, 123L, "namespace", 2);
        verify(audit).recordInTransaction(argThat(event ->
                "TIANSHU_LIST_TEMPLATE_UPDATE".equals(event.getActionCode())
                        && event.getDetails().path("templateId").asText().equals("123")),
                argThat(method -> method.getName().equals("save")));
    }

    @Test void aUserCannotSubmitAForbiddenColumnOrEditAnotherUsersTemplate() throws Exception {
        var row = personalRow("alice");
        when(mapper.lockById(20L, 10L, PAGE, "main", 123L)).thenReturn(row);
        when(mapper.selectColumns(20L, 10L, 123L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.save(PAGE, "main", "1", DdcNamespaceListDefinition.DEFINITION.digest(), "PERSONAL",
                "123", "4", "Mine", List.of("description"), false)).isInstanceOf(IllegalArgumentException.class);
        when(mapper.lockById(20L, 10L, PAGE, "main", 123L)).thenReturn(personalRow("bob"));
        assertThatThrownBy(() -> service.save(PAGE, "main", "1", DdcNamespaceListDefinition.DEFINITION.digest(), "PERSONAL",
                "123", "4", "Mine", List.of("bizCode"), false)).isInstanceOf(CommonException.class);
        verify(mapper, never()).updateTemplate(any(), any());
    }

    @Test void staleTemplateVersionCannotOverwriteAnotherEdit() throws Exception {
        when(mapper.lockById(20L, 10L, PAGE, "main", 123L)).thenReturn(personalRow("alice"));
        assertThatThrownBy(() -> service.save(PAGE, "main", "1", DdcNamespaceListDefinition.DEFINITION.digest(), "PERSONAL",
                "123", "3", "Mine", List.of("bizCode"), false))
                .isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56404));
        verify(mapper, never()).updateTemplate(any(), any());
    }

    @Test void inconsistentApiAndFieldScopePoliciesCannotSaveATemplate() {
        var different = new Rbac3StarterProperties.UserScopePolicyProperties().setEnabled(true)
                .setPermissionScopes(Map.of("TIANSHU_READ", Set.of("TIANSHU_READ")));
        when(fields.userScopePolicy()).thenReturn(new UserPermissionScopePolicy(different));
        assertThatThrownBy(() -> service.save(PAGE, "main", "1", DdcNamespaceListDefinition.DEFINITION.digest(), "PERSONAL",
                null, null, "Mine", List.of("bizCode"), false))
                .isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56404));
        verify(mapper, never()).insertTemplate(any());
    }

    @Test void delegatedPersonalTemplateUsesTheVerifiedUserAndRejectsAnUnreadableField() {
        var remoteScope = new CatalogScope("11", "20", "trade", "shop", "default", "test");
        var remoteView = new EffectivePermissionSnapshot(ISSUER, "alice", remoteScope, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("shop", List.of("7"), List.of("7")), List.of("shop:read"), Instant.now().plusSeconds(60));
        var remoteSnapshot = mock(SystemAuthorizationSnapshot.class); when(remoteSnapshot.permissionSnapshot()).thenReturn(remoteView);
        var target = DdcMetadataScopeBO.builder().tenantId(20L).catalogScopeId(11L).bizCode("trade").appCode("shop")
                .namespaceCode("default").envCode("test").build();
        when(scopes.lockEnabledTarget(11L)).thenReturn(Optional.of(target));
        var definition = new ListDefinition("shop.page.list", "main", List.of(
                new ListDefinition.Column("id", List.of("shop.field.id"), "shop.list", "field.id", true, true),
                new ListDefinition.Column("secret", List.of("shop.field.secret"), "shop.list", "field.secret", false, true)));
        var binding = new ListDefinitionBinding(definition, "shop.component.list", "shop.api.list", "shop.api.system", "shop.api.personal");
        when(metadata.resolveDelegated(eq(remoteSnapshot), eq(binding), any(), anySet())).thenReturn(definition);
        when(metadata.delegatedApiAllowed(eq(remoteSnapshot), anyString(), any(), anySet())).thenReturn(true);
        when(metadata.delegatedFieldReadability(eq(remoteSnapshot), anySet(), any(), anySet()))
                .thenReturn(Map.of("shop.field.id", true, "shop.field.secret", false));
        var now = Instant.now();
        var servicePrincipal = new ServiceIdentityPrincipal("client", "20", "client", "token", URI.create("https://metadata.example"),
                1, Set.of(DdcListDelegationVerifier.REQUIRED_SCOPE), "trade", "shop", "test", "credential", now.minusSeconds(1), now.plusSeconds(60), "app-id", ServiceTokenContext.TENANT);
        var verified = new DdcListDelegationVerifier.VerifiedDelegation(servicePrincipal, URI.create(ISSUER), remoteSnapshot, Set.of("shop:read"));
        var request = new ListDelegationRequest(binding, ListDelegationRequest.Operation.SAVE_PERSONAL, "1", "2", "3", List.of("7"),
                Map.of("shop:read", Set.of("shop:read")), null, null, "Mine", List.of("secret"), false, false);
        assertThatThrownBy(() -> service.executeDelegated(verified, request, () -> verified)).isInstanceOf(IllegalArgumentException.class);
        verify(mapper, never()).insertTemplate(any());
    }

    private DdcListTemplateBO personalRow(String subject) throws Exception {
        var row = new DdcListTemplateBO(); row.setId(123L); row.setTenantId(20L); row.setCatalogScopeId(10L);
        row.setPageResourceKey(PAGE); row.setListKey("main"); row.setTemplateType("PERSONAL"); row.setOwnerIssuer(ISSUER);
        row.setOwnerSubject(subject); row.setOwnerDigest(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((ISSUER + "\u0000" + subject).getBytes(StandardCharsets.UTF_8))));
        row.setTemplateName("Mine"); row.setDefaultTemplate(false); row.setVersion(4L); return row;
    }

    private PermissionManifest.ResourceDefinition api(String key) {
        String code = key.endsWith("savePersonal") ? "TIANSHU_NAMESPACE_TEMPLATE_PERSONAL"
                : key.endsWith("saveSystem") ? "TIANSHU_NAMESPACE_TEMPLATE_SYSTEM" : "TIANSHU_READ";
        return new PermissionManifest.ResourceDefinition(key, ResourceType.API, Map.of("path", "/test"), ResourceAccess.PROTECTED,
                new PermissionRequirement(PermissionMode.ALL, List.of(code)), null, List.of(PermissionManifest.PrincipalType.USER), List.of(),
                new PermissionManifest.I18nReference("test", "name"), null);
    }
    private PermissionManifest.ResourceDefinition field(String key) {
        var rule = key.endsWith(".description") ? null : new PermissionRequirement(PermissionMode.ALL, List.of("TIANSHU_READ"));
        return new PermissionManifest.ResourceDefinition(key, ResourceType.FIELD, Map.of("dto", "Test", "property", "field"), ResourceAccess.PROTECTED,
                null, new PermissionManifest.FieldPolicy(PermissionManifest.JsonFieldType.STRING, rule, null, null, null),
                List.of(PermissionManifest.PrincipalType.USER), List.of(), new PermissionManifest.I18nReference("test", "name"), null);
    }
}
