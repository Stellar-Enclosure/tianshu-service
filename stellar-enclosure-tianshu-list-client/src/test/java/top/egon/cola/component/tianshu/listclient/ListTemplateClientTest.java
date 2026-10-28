package top.egon.cola.component.tianshu.listclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.egon.cola.component.tianshu.api.list.ListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.UserPermissionScopePolicy;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListTemplateClientTest {
    private final ListDefinition definition = new ListDefinition("shop.page.list", "main", List.of(
            new ListDefinition.Column("id", List.of("shop.field.id"), "shop.list", "field.id", true, true)));
    private final ListDefinitionBinding binding = new ListDefinitionBinding(definition, "shop.component.list",
            "shop.api.list", "shop.api.template.system", "shop.api.template.personal");
    private final PermissionExecutionGuard guard = mock(PermissionExecutionGuard.class);
    private final PermissionFieldBindingGuard fields = mock(PermissionFieldBindingGuard.class);
    private final Rbac3StarterProperties properties = new Rbac3StarterProperties();
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test void sendsOnlyTheCurrentVerifiedUserProofAndScopePolicy() throws Exception {
        var user = user("2"); when(guard.require("shop.api.list")).thenReturn(user);
        fieldOwned();
        policy(true);
        var servlet = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(servlet, "verified-user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
        AtomicReference<ListDelegationRequest> sent = new AtomicReference<>(); AtomicReference<String> tenant = new AtomicReference<>();
        var client = new ListTemplateClient(URI.create("https://metadata.example/internal/v1/tianshu/list-delegation"),
                value -> {tenant.set(value); return "service-token";}, guard, fields, mapper, Duration.ofSeconds(2),
                (uri, service, proof, body, timeout) -> {
                    assertThat(service).isEqualTo("service-token"); assertThat(proof).isEqualTo("verified-user-token");
                    sent.set(mapper.readValue(body, ListDelegationRequest.class)); return response();
                });
        var result = client.execute(binding, ListDelegationRequest.Operation.LOAD, null, null, null, null, null, false);
        assertThat(tenant).hasValue("20");
        assertThat(sent.get().binding()).isEqualTo(binding);
        assertThat(sent.get().permissionScopes()).containsEntry("shop:read", Set.of("shop:read"));
        assertThat(result.definitionDigest()).isEqualTo(definition.digest());
        assertThat(result.mayManageSystem()).isFalse();
        assertThat(result.mayManagePersonal()).isTrue();
        verify(guard, times(2)).require("shop.api.list");
    }

    @Test void refusesAResponseIfTheUsersAuthorizationChangedDuringTheCall() throws Exception {
        var first = user("2"); var changed = user("8");
        when(guard.require("shop.api.list")).thenReturn(first, changed);
        fieldOwned();
        policy(true);
        var servlet = new MockHttpServletRequest(); VerifiedUserTokenCarrier.set(servlet, "verified-user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servlet));
        var client = new ListTemplateClient(URI.create("https://metadata.example/internal/v1/tianshu/list-delegation"),
                value -> "service-token", guard, fields, mapper, Duration.ofSeconds(2), (uri, service, proof, body, timeout) -> response());
        assertThatThrownBy(() -> client.execute(binding, ListDelegationRequest.Operation.LOAD, null, null, null, null, null, false))
                .isInstanceOf(SecurityException.class).hasMessage("LIST_DELEGATION_VIEW_CHANGED");
    }

    @Test void refusesDelegationWithoutTheHostUserScopePolicy() {
        var current = user("2"); when(guard.require("shop.api.list")).thenReturn(current);
        fieldOwned();
        policy(false);
        var client = new ListTemplateClient(URI.create("https://metadata.example/internal/v1/tianshu/list-delegation"),
                value -> "service-token", guard, fields, mapper, Duration.ofSeconds(2), (uri, service, proof, body, timeout) -> {
                    throw new AssertionError("must not dispatch");
                });
        assertThatThrownBy(() -> client.execute(binding, ListDelegationRequest.Operation.LOAD, null, null, null, null, null, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("LIST_DELEGATION_USER_SCOPE_POLICY_REQUIRED");
    }

    @Test void refusesAFieldOutsideTheBusinessHostsCompiledManifest() {
        var current = user("2"); when(guard.require("shop.api.list")).thenReturn(current);
        policy(true);
        var client = new ListTemplateClient(URI.create("https://metadata.example/internal/v1/tianshu/list-delegation"),
                value -> "service-token", guard, fields, mapper, Duration.ofSeconds(2), (uri, service, proof, body, timeout) -> {
                    throw new AssertionError("must not dispatch");
                });
        assertThatThrownBy(() -> client.execute(binding, ListDelegationRequest.Operation.LOAD, null, null, null, null, null, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("LIST_DELEGATION_FIELD_NOT_OWNED");
    }

    @Test void refusesDifferentApiAndFieldScopePolicies() {
        var current = user("2"); when(guard.require("shop.api.list")).thenReturn(current);
        fieldOwned(); policy(true);
        when(fields.userScopePolicy()).thenReturn(new UserPermissionScopePolicy(new Rbac3StarterProperties.UserScopePolicyProperties()));
        var client = new ListTemplateClient(URI.create("https://metadata.example/internal/v1/tianshu/list-delegation"),
                value -> "service-token", guard, fields, mapper, Duration.ofSeconds(2), (uri, service, proof, body, timeout) -> {
                    throw new AssertionError("must not dispatch");
                });
        assertThatThrownBy(() -> client.execute(binding, ListDelegationRequest.Operation.LOAD, null, null, null, null, null, false))
                .isInstanceOf(IllegalStateException.class).hasMessage("LIST_DELEGATION_SCOPE_POLICY_MISMATCH");
    }

    private ListTemplateClient.Response response() {
        String body = "{\"success\":true,\"data\":{\"definitionDigest\":\"" + definition.digest()
                + "\",\"catalogVersion\":\"1\",\"columns\":[],\"templates\":[],\"selectedTemplateId\":null,\"visibleColumns\":[],\"mayManageSystem\":false,\"mayManagePersonal\":true,\"savedTemplate\":null}}";
        return new ListTemplateClient.Response(200, body.getBytes(StandardCharsets.UTF_8));
    }
    private void fieldOwned() {
        var field = mock(PermissionManifest.ResourceDefinition.class); when(field.type()).thenReturn(ResourceType.FIELD);
        when(guard.resource("shop.field.id", ResourceType.FIELD)).thenReturn(field);
    }
    private void policy(boolean enabled) {
        properties.getUserScopePolicy().setEnabled(enabled).setPermissionScopes(Map.of("shop:read", Set.of("shop:read")));
        var policy = new UserPermissionScopePolicy(properties.getUserScopePolicy());
        when(guard.userScopePolicy()).thenReturn(policy); when(fields.userScopePolicy()).thenReturn(policy);
    }
    private Rbac3UserDetails user(String authVersion) {
        var view = new EffectivePermissionSnapshot("https://idp.example/tenants/20", "alice",
                new CatalogScope("10", "20", "trade", "shop", "default", "test"), "1", authVersion, "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("shop", List.of("7"), List.of("7")),
                List.of("shop:read"), Instant.now().plusSeconds(60));
        var snapshot = mock(SystemAuthorizationSnapshot.class); when(snapshot.permissionSnapshot()).thenReturn(view);
        var user = mock(Rbac3UserDetails.class); when(user.snapshot()).thenReturn(snapshot); return user;
    }
}
