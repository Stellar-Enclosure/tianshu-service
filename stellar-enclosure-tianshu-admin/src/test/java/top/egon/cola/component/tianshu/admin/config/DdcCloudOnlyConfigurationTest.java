package top.egon.cola.component.tianshu.admin.config;

import com.google.protobuf.Empty;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.rpc.annotation.EgonRpcProvider;
import top.egon.cola.component.rpc.contract.validation.RpcContractValidator;
import top.egon.cola.component.rpc.provider.binding.RpcProviderBeanScanner;
import top.egon.cola.component.rpc.provider.lifecycle.RpcProviderAvailabilityRegistry;
import top.egon.cola.component.rpc.provider.server.RpcServerServiceDefinitionFactory;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.DdcSiResourceGrpcServiceGrpc;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceRequest;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.ResolveSiResourceResponse;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcSiResourceRpcProvider;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DdcCloudOnlyConfigurationTest {

    private static final String PROVIDER_PACKAGE = DdcSiResourceRpcProvider.class.getPackageName();

    @Test
    void discoversSiProviderWithoutRetiredConfigRegistryOrManagementProviders() {
        assertThat(providerCandidates())
                .extracting(BeanDefinition::getBeanClassName)
                .contains(DdcSiResourceRpcProvider.class.getName())
                .doesNotContain(
                        PROVIDER_PACKAGE + ".DdcConfigRpcProvider",
                        PROVIDER_PACKAGE + ".DdcRegistryRpcProvider",
                        PROVIDER_PACKAGE + ".DdcManagementRpcProvider");
    }

    @Test
    void retiredMethodsAreUnimplementedWhileSiIsBoundOnTheSameH2cPort() throws Exception {
        try (var context = new GenericApplicationContext();
             var validation = Validation.buildDefaultValidatorFactory()) {
            for (BeanDefinition candidate : providerCandidates()) {
                registerProvider(context, Class.forName(candidate.getBeanClassName()));
            }
            context.refresh();
            var si = context.getBean(DdcSiResourceRpcProvider.class);
            when(si.resolveSiResource(any(ResolveSiResourceRequest.class)))
                    .thenReturn(ResolveSiResourceResponse.getDefaultInstance());

            var validator = new RpcContractValidator(new ValidationUtils(validation.getValidator()));
            var registry = new RpcProviderBeanScanner(context, validator).scan();
            var availability = new RpcProviderAvailabilityRegistry();
            registry.providers().forEach(provider -> availability.available(provider.serviceIdentity()));
            var definitions = new RpcServerServiceDefinitionFactory(availability, List.of()).create(registry);
            var address = new InetSocketAddress("127.0.0.1", 0);
            var builder = NettyServerBuilder.forAddress(address);
            definitions.forEach(builder::addService);
            var server = builder.build().start();
            try {
                var channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext().disableRetry().disableServiceConfigLookUp().build();
                try {
                    assertThat(DdcSiResourceGrpcServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(3, TimeUnit.SECONDS)
                            .resolveSiResource(ResolveSiResourceRequest.getDefaultInstance()))
                            .isEqualTo(ResolveSiResourceResponse.getDefaultInstance());
                    for (String method : List.of("egon.tianshu.v1.DdcConfigRuntimeService/PullConfig",
                            "egon.tianshu.v1.DdcServiceRegistryService/RegisterService",
                            "egon.tianshu.v1.DdcManagementService/GetBiz")) {
                        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(channel, unaryMethod(method),
                                CallOptions.DEFAULT.withDeadlineAfter(3, TimeUnit.SECONDS), Empty.getDefaultInstance()))
                                .as(method)
                                .isInstanceOfSatisfying(StatusRuntimeException.class, failure ->
                                        assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED));
                    }
                } finally {
                    channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
                }
            } finally {
                server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            }
        }
    }

    private Set<BeanDefinition> providerCandidates() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EgonRpcProvider.class));
        return scanner.findCandidateComponents(PROVIDER_PACKAGE);
    }

    private <T> void registerProvider(GenericApplicationContext context, Class<T> provider) {
        context.registerBean(provider, () -> mock(provider));
    }

    private MethodDescriptor<Empty, Empty> unaryMethod(String method) {
        return MethodDescriptor.<Empty, Empty>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(method)
                .setRequestMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                .build();
    }
}
