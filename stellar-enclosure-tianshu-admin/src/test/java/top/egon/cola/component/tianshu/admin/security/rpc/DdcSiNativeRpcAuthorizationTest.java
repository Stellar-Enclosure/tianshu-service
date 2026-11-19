package top.egon.cola.component.tianshu.admin.security.rpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ListSiCatalogRequest;
import top.egon.cola.component.tianshu.admin.config.DdcSiNativeRpcProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcAdmissionIdentityResolver;

import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DdcSiNativeRpcAuthorizationTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenant");
    private static final URI RESOURCE = URI.create("https://tianshu.example/si");

    @Test
    void emptyDeploymentBindingsNeverCreateNativeAuthority() {
        var gate = gate(new DdcSiNativeRpcProperties(), new IdpStarterProperties(), tenants(true));
        assertThatThrownBy(() -> gate.authorize(DdcSiNativeRpcAuthorization.LIST,
                ListSiCatalogRequest.getDefaultInstance(), 1001, "ai", "core", "prod", null))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        failure -> assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void readOnlyBindingsRequireNoRegistrationWritePolicyOrGrant() {
        var properties = properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("core"),
                DdcSiNativeRpcAuthorization.RESOLVE, Set.of("core"), DdcSiNativeRpcAuthorization.SEARCH, Set.of("core")));
        var identity = identity();
        assertThat(identity.getRpc().getProvider().getMethods()).doesNotContainKey(DdcSiNativeRpcAuthorization.REGISTER);
        assertThatCode(() -> gate(properties, identity, tenants(true))).doesNotThrowAnyException();
        properties.getBindings().getFirst().getMethods().put(DdcSiNativeRpcAuthorization.REGISTER, Set.of("core"));
        invalid(properties, identity);
    }

    @Test
    void unknownMethodsWildcardsAndDuplicateCallerTuplesFailStartup() {
        invalid(properties(Map.of("another.Service/Read", Set.of("core"))), identity());
        invalid(properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("*"))), identity());
        invalid(properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("core?"))), identity());
        invalid(properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of())), identity());
        var duplicate = properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("core")));
        duplicate.getBindings().add(duplicate.getBindings().getFirst());
        invalid(duplicate, identity());
    }

    @Test
    void namespacePrivilegesRequireTheSameStrictNativeMethodResourceAndScope() {
        for (var policy : List.of(
                readPolicy().setRequired(false),
                readPolicy().setPrincipalKind(IdpStarterProperties.RpcPrincipalKindEnum.USER_OR_SERVICE),
                readPolicy().setScopeContext(ServiceTokenContext.PLATFORM),
                readPolicy().setExpectedResourceUri(URI.create("https://other.example/si")),
                readPolicy().setRequiredScopes(Set.of()))) {
            var identity = identity();
            identity.getRpc().getProvider().getMethods().put(DdcSiNativeRpcAuthorization.LIST, policy);
            invalid(properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("core"))), identity);
        }
        var missing = identity();
        missing.getRpc().getProvider().getMethods().clear();
        invalid(properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("core"))), missing);
    }

    @Test
    void callerAuthorityRequiresAnEnabledLocalTenantMapping() {
        var properties = properties(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("core")));
        assertThatThrownBy(() -> gate(properties, identity(), tenants(false)))
                .isInstanceOf(IllegalStateException.class).hasMessage("TIANSHU_SI_NATIVE_BINDING_INVALID");
        properties.getBindings().getFirst().setIssuer(URI.create("http://idp.example/tenant"));
        invalid(properties, identity());
    }

    @Test
    void realSpringBinderPreservesExactNativeMethodKeysAndRejectsUnknownProperties() {
        String prefix = DdcSiNativeRpcProperties.PREFIX;
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(prefix + ".bindings[0].issuer=" + ISSUER,
                        prefix + ".bindings[0].resource-uri=" + RESOURCE,
                        prefix + ".bindings[0].identity-tenant-id=90001",
                        prefix + ".bindings[0].client-id=runtime-client",
                        prefix + ".bindings[0].source-biz-code=ai", prefix + ".bindings[0].source-app-code=tianji",
                        prefix + ".bindings[0].source-environment=prod",
                        prefix + ".bindings[0].methods[" + DdcSiNativeRpcAuthorization.LIST + "][0]=core");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DdcSiNativeRpcProperties.class).getBindings().getFirst().getMethods())
                    .containsExactlyEntriesOf(Map.of(DdcSiNativeRpcAuthorization.LIST, Set.of("core")));
        });
        runner.withPropertyValues(prefix + ".unapproved-default=true")
                .run(context -> assertThat(context).hasFailed());
    }

    private void invalid(DdcSiNativeRpcProperties properties, IdpStarterProperties identity) {
        assertThatThrownBy(() -> gate(properties, identity, tenants(true)))
                .isInstanceOf(IllegalStateException.class).hasMessage("TIANSHU_SI_NATIVE_BINDING_INVALID");
    }

    private static DdcSiNativeRpcAuthorization gate(DdcSiNativeRpcProperties properties, IdpStarterProperties identity,
            DdcTenantRegistry tenants) {
        var beans = new StaticListableBeanFactory(Map.of("idpStarterProperties", identity,
                "idpRpcAdmissionIdentityResolver", new IdpRpcAdmissionIdentityResolver()));
        return new DdcSiNativeRpcAuthorization(properties, beans.getBeanProvider(IdpStarterProperties.class),
                beans.getBeanProvider(IdpRpcAdmissionIdentityResolver.class), tenants, Clock.systemUTC());
    }

    private static DdcSiNativeRpcProperties properties(Map<String, Set<String>> methods) {
        var binding = new DdcSiNativeRpcProperties.BindingDTO().setIssuer(ISSUER).setResourceUri(RESOURCE)
                .setIdentityTenantId("90001").setClientId("runtime-client").setSourceBizCode("ai")
                .setSourceAppCode("tianji").setSourceEnvironment("prod").setMethods(new LinkedHashMap<>(methods));
        return new DdcSiNativeRpcProperties().setBindings(new ArrayList<>(List.of(binding)));
    }

    private static DdcTenantRegistry tenants(boolean enabled) {
        var tenants = mock(DdcTenantRegistry.class);
        when(tenants.requireByIdentityTenantId("90001")).thenReturn(new DdcTenantBindingDTO(1001L, "90001", enabled));
        return tenants;
    }

    private static IdpStarterProperties identity() {
        var identity = new IdpStarterProperties();
        identity.getRpc().setEnabled(true);
        identity.getRpc().getProvider().setEnabled(true);
        identity.getRpc().getProvider().setMethods(new LinkedHashMap<>(Map.of(
                DdcSiNativeRpcAuthorization.LIST, readPolicy(), DdcSiNativeRpcAuthorization.RESOLVE, readPolicy(),
                DdcSiNativeRpcAuthorization.SEARCH, readPolicy())));
        return identity;
    }

    private static IdpStarterProperties.RpcProviderMethodProperties readPolicy() {
        return new IdpStarterProperties.RpcProviderMethodProperties().setRequired(true)
                .setPrincipalKind(IdpStarterProperties.RpcPrincipalKindEnum.SERVICE).setScopeContext(ServiceTokenContext.TENANT)
                .setExpectedResourceUri(RESOURCE).setRequiredScopes(Set.of("si:read"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DdcSiNativeRpcProperties.class)
    static class PropertiesConfiguration { }
}
