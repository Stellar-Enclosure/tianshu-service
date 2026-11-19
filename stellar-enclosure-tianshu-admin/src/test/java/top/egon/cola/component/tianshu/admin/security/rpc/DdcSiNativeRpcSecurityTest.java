package top.egon.cola.component.tianshu.admin.security.rpc;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.MetadataUtils;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.rpc.context.invocation.RpcMetadataKeys;
import top.egon.cola.component.rpc.config.EgonRpcProperties;
import top.egon.cola.component.rpc.contract.validation.RpcContractValidator;
import top.egon.cola.component.rpc.provider.binding.RpcProviderBinding;
import top.egon.cola.component.rpc.provider.binding.RpcProviderMethodRegistry;
import top.egon.cola.component.rpc.provider.lifecycle.RpcProviderAvailabilityRegistry;
import top.egon.cola.component.rpc.provider.server.RpcServerServiceDefinitionFactory;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.mapping.DdcSiProtoMapperImpl;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcSiResourceRpc;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcSiResourceRpcProvider;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.security.AccessTokenVerification;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpJwtVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.UserAccessTokenVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.security.domain.TokenAuditProofBO;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcAdmissionIdentityResolver;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcBearerServerInterceptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exercises the production guard bean and native dispatcher, rather than generated bindService(). */
class DdcSiNativeRpcSecurityTest {

    private static final String METHOD = DdcSiResourceGrpcServiceGrpc.getResolveSiResourceMethod().getFullMethodName();
    private static final URI ISSUER = URI.create("https://idp.example/tenants/90001");
    private static final URI RESOURCE = URI.create("https://tianshu.example/si");
    private static final String READ_SCOPE = "TIANSHU_SI_READ";
    private static final Instant NOW = Instant.now();

    @Test
    void verifiedServiceTenantReadsFixedHistoryThroughBothProductionGuardsWithoutHmac() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        when(resources.resolveExecutionResource(1001L, "ai", "core", "prod", "orders", "yuheng-reader",
                DdcSiKindEnum.MCP, "orders-tools", 12L)).thenReturn(Optional.of(descriptor()));

        try (var fixture = new NativeFixture(resources, Set.of(READ_SCOPE), true)) {
            var response = fixture.stub(true).resolveSiResource(request());
            assertThat(response.getResource().getResolvedVersionId()).isEqualTo(920001L);
            assertThat(response.getResource().getResolvedVersionNo()).isEqualTo(12L);
            verify(resources).resolveExecutionResource(1001L, "ai", "core", "prod", "orders", "yuheng-reader",
                    DdcSiKindEnum.MCP, "orders-tools", 12L);
            verify(fixture.verifier).verifyAny("verified-service");
        }
    }

    @Test
    void missingNativeIdentityResolverFailsUnavailableBeforeBusinessExecution() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        try (var fixture = new NativeFixture(resources, Set.of(READ_SCOPE), false)) {
            rejected(Status.Code.UNAVAILABLE, () -> fixture.stub(true).resolveSiResource(request()));
            verifyNoInteractions(resources);
        }
    }

    @Test
    void absentBearerOrMissingGrantCannotEnterTheNativeHandler() throws Exception {
        var resources = mock(DdcSiResourceService.class);
        try (var fixture = new NativeFixture(resources, Set.of("another:scope"), true)) {
            rejected(Status.Code.UNAUTHENTICATED, () -> fixture.stub(false).resolveSiResource(request()));
            rejected(Status.Code.PERMISSION_DENIED, () -> fixture.stub(true).resolveSiResource(request()));
            verifyNoInteractions(resources);
        }
    }

    @Test
    void nativeProfileRequiresAnExplicitRegisteredReadGrantAndCanonicalResource() throws Exception {
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var valid = profileRpc(Map.of("TIANSHU_RESOURCE_URI", RESOURCE.toString(), "TIANSHU_SI_CATALOG_READ_SCOPE", READ_SCOPE));
            assertThat(valid.isEnabled()).isTrue();
            assertThat(valid.getProvider().getMethods()).containsOnlyKeys(METHOD);
            assertThat(validators.getValidator().validate(valid)).isEmpty();
            assertThat(valid.getProvider().getMethods().get(METHOD).getPrincipalKind())
                    .isEqualTo(IdpStarterProperties.RpcPrincipalKindEnum.SERVICE);
            assertThat(valid.getProvider().getMethods().get(METHOD).getScopeContext()).isEqualTo(ServiceTokenContext.TENANT);

            var blankScope = profileRpc(Map.of("TIANSHU_RESOURCE_URI", RESOURCE.toString(), "TIANSHU_SI_CATALOG_READ_SCOPE", ""));
            assertThat(validators.getValidator().validate(blankScope)).isNotEmpty();
            assertThatThrownBy(() -> {
                var missingScope = profileRpc(Map.of("TIANSHU_RESOURCE_URI", RESOURCE.toString()));
                new ValidationUtils(validators.getValidator()).validate(missingScope);
            }).isInstanceOf(RuntimeException.class);
        }
    }

    @ParameterizedTest(name = "strict native RPC binding: {0}")
    @ValueSource(strings = {"base", "local", "test", "si-catalog", "local,si-catalog", "test,si-catalog"})
    void mergedShippedProfilesBindTheRealNativeRpcContract(String selectedProfiles) throws Exception {
        String[] profiles = "base".equals(selectedProfiles) ? new String[0] : selectedProfiles.split(",");
        var environment = mergedEnvironment(Map.of("TIANSHU_RESOURCE_URI", RESOURCE.toString(),
                "TIANSHU_SI_CATALOG_READ_SCOPE", READ_SCOPE), profiles);
        new ApplicationContextRunner().withUserConfiguration(NativeRpcPropertiesConfiguration.class)
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().forEach(sources::addLast);
                }).run(context -> {
                    assertThat(context).as(selectedProfiles).hasNotFailed();
                    var properties = context.getBean(EgonRpcProperties.class);
                    assertThat(properties.getTransport()).isEqualTo("h2c");
                    assertThat(properties.getProvider().getPort()).isPositive();
                    assertThat(properties.getProvider().isEnabled()).isEqualTo(!"test".equals(selectedProfiles));
                    assertThat(properties.getConsumer().isEnabled()).isFalse();
                });
    }

    @Configuration(value = "nativeSiRpcPropertiesTestConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(EgonRpcProperties.class)
    static class NativeRpcPropertiesConfiguration { }

    private static IdpStarterProperties.RpcProperties profileRpc(Map<String, Object> deployment) throws Exception {
        return Binder.get(mergedEnvironment(deployment, "si-catalog")).bind("egon.cola.platform.tianquan.shoubing.rpc",
                Bindable.of(IdpStarterProperties.RpcProperties.class)).get();
    }

    private static StandardEnvironment mergedEnvironment(Map<String, Object> deployment, String... profiles) throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        for (var properties : new YamlPropertySourceLoader().load("base", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addFirst(properties);
        }
        for (String profile : profiles) {
            for (var properties : new YamlPropertySourceLoader().load(profile,
                    new ClassPathResource("application-" + profile + ".yml"))) {
                environment.getPropertySources().addFirst(properties);
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource("explicit-test-deployment", deployment));
        return environment;
    }

    private static ResolveSiResourceRequest request() {
        return ResolveSiResourceRequest.newBuilder().setTenantId(1001L).setBizCode("ai").setNamespaceCode("core")
                .setEnvCode("prod").setOwnerAppCode("orders").setCallerAppCode("yuheng-reader")
                .setKind("MCP").setResourceKey("orders-tools").setExactVersionNo(12L).setExecutionRequired(true).build();
    }

    private static DdcSiResourceDescriptor descriptor() throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("{}".getBytes(StandardCharsets.UTF_8)));
        var manifest = DdcSiDependencyManifestVO.create(new DdcSiDependencyManifestVO.VersionIdentityVO("1001", "910001",
                "920001", "12", DdcSiKindEnum.MCP, "prod", digest), null, List.of(), List.of());
        return DdcSiResourceDescriptor.builder().tenantId(1001L).bizCode("ai").namespaceCode("core").envCode("prod")
                .ownerAppCode("orders").resourceId(910001L).kind(DdcSiKindEnum.MCP).resourceKey("orders-tools")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED).resolvedVersionId(920001L).resolvedVersionNo(12L)
                .digestSha256(digest).specJson("{}").dependencyManifest(manifest).build();
    }

    private static void rejected(Status.Code expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(StatusRuntimeException.class,
                failure -> assertThat(failure.getStatus().getCode()).isEqualTo(expected));
    }

    private static final class NativeFixture implements AutoCloseable {
        private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
        private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        private final IdpJwtVerifier verifier = mock(IdpJwtVerifier.class);
        private final Server server;
        private final ManagedChannel channel;

        private NativeFixture(DdcSiResourceService resources, Set<String> scopes, boolean identityAvailable) throws Exception {
            var properties = new DdcAdminProperties();
            var tenancy = new DdcTenantAdminProperties().setBindings(List.of(new DdcTenantBindingDTO(1001L, "90001", true)));
            var tenantRegistry = new DdcTenantRegistry(tenancy, properties);
            tenantRegistry.initialize();
            var tenantContexts = new DdcTenantContextExecutor();
            context.registerBean("egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties",
                    DdcAdminProperties.class, () -> properties);
            context.registerBean("ddcHmacCredentialRegistry", DdcHmacCredentialRegistry.class, () -> new DdcHmacCredentialRegistry(properties));
            context.registerBean("ddcTenantRegistry", DdcTenantRegistry.class, () -> tenantRegistry);
            context.registerBean("ddcTenantContextExecutor", DdcTenantContextExecutor.class, () -> tenantContexts);
            if (identityAvailable) {
                context.registerBean("idpRpcAdmissionIdentityResolver", IdpRpcAdmissionIdentityResolver.class);
            }
            context.register(DdcRpcSecurityConfiguration.class);
            context.refresh();

            var principal = new ServiceIdentityPrincipal("reader-client", "90001", "reader-client", "fixture-jti", RESOURCE,
                    1L, scopes, "ai", "yuheng-reader", "prod", "reader-credential", NOW.minusSeconds(30), NOW.plusSeconds(300),
                    "reader-app", ServiceTokenContext.TENANT);
            var proof = new TokenAuditProofBO(ISSUER, RESOURCE, "a".repeat(64), "b".repeat(64), "c".repeat(64));
            doAnswer(invocation -> new AccessTokenVerification.Valid<>(principal, principal.scopes(), proof, ISSUER))
                    .when(verifier).verifyAny("verified-service");
            var idp = new IdpStarterProperties();
            idp.getRpc().setEnabled(true);
            idp.getRpc().getProvider().setEnabled(true).setMethods(Map.of(METHOD,
                    new IdpStarterProperties.RpcProviderMethodProperties().setRequired(true)
                            .setPrincipalKind(IdpStarterProperties.RpcPrincipalKindEnum.SERVICE).setScopeContext(ServiceTokenContext.TENANT)
                            .setRequiredScopes(Set.of(READ_SCOPE)).setExpectedResourceUri(RESOURCE)));
            var bearer = IdpRpcBearerServerInterceptor.createConfigured(mock(UserAccessTokenVerifier.class), verifier, idp,
                    null, validators.getValidator());
            bearer.validateConfiguration();
            var provider = new DdcSiResourceRpcProvider(resources, new DdcSiProtoMapperImpl(),
                    context.getBeanProvider(IdpRpcAdmissionIdentityResolver.class), tenantRegistry, tenantContexts,
                    Clock.fixed(NOW, ZoneOffset.UTC));
            var contract = new RpcContractValidator(new ValidationUtils(validators.getValidator())).validate(DdcSiResourceRpc.class);
            var binding = new RpcProviderBinding(provider, contract);
            var registry = new RpcProviderMethodRegistry(List.of(binding));
            var availability = new RpcProviderAvailabilityRegistry();
            availability.available(binding.serviceIdentity());
            var definition = new RpcServerServiceDefinitionFactory(availability,
                    List.of(new DdcRpcProviderExceptionMapper())).create(registry).getFirst();
            server = NettyServerBuilder.forAddress(new java.net.InetSocketAddress("127.0.0.1", 0))
                    .addService(definition).intercept(context.getBean("ddcRpcServerInterceptor", io.grpc.ServerInterceptor.class))
                    .intercept(bearer).build().start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext()
                    .disableRetry().disableServiceConfigLookUp().build();
        }

        private DdcSiResourceGrpcServiceGrpc.DdcSiResourceGrpcServiceBlockingStub stub(boolean authorized) {
            var stub = DdcSiResourceGrpcServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS);
            if (!authorized) {
                return stub;
            }
            var headers = new Metadata();
            headers.put(RpcMetadataKeys.AUTHORIZATION, "Bearer verified-service");
            return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        }

        @Override
        public void close() throws InterruptedException {
            try {
                channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            } finally {
                try {
                    server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
                } finally {
                    context.close();
                    validators.close();
                }
            }
        }
    }
}
