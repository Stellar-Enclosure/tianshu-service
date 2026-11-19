package top.egon.cola.component.tianshu.admin.security.rpc;

import io.grpc.ServerInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.rpc.provider.server.RpcProviderExceptionMapper;
import top.egon.cola.platform.tianquan.shoubing.starter.security.rpc.IdpRpcAdmissionIdentityResolver;

/**
 * 将 Tianshu RPC 认证与异常映射接入中立 RPC Provider 扩展点。
 * / Connects Tianshu RPC authentication and error mapping to neutral provider hooks.
 */
@Configuration(proxyBeanMethods = false)
public class DdcRpcSecurityConfiguration {

    /** Keeps legacy HMAC methods guarded while requiring verified native identity for SI execution. */
    @Bean(name = "ddcRpcServerInterceptor")
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public ServerInterceptor ddcRpcServerInterceptor(
            @Qualifier("egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties") DdcAdminProperties properties,
            @Qualifier("ddcHmacCredentialRegistry")
            DdcHmacCredentialRegistry credentialRegistry,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenantRegistry,
            @Qualifier("ddcTenantContextExecutor")
            DdcTenantContextExecutor tenantContextExecutor,
            @Qualifier("ddcNonceStore") ObjectProvider<DdcNonceStore> nonceStore,
            @Qualifier("idpRpcAdmissionIdentityResolver") ObjectProvider<IdpRpcAdmissionIdentityResolver> nativeIdentities) {
        return new DdcRpcServerInterceptor(
                properties,
                credentialRegistry,
                tenantRegistry,
                tenantContextExecutor,
                nonceStore.getIfAvailable(),
                nativeIdentities
        );
    }

    /** Preserves direct legacy construction without granting native SI execution authority. */
    public ServerInterceptor ddcRpcServerInterceptor(DdcAdminProperties properties,
            DdcHmacCredentialRegistry credentialRegistry, DdcTenantRegistry tenantRegistry,
            DdcTenantContextExecutor tenantContextExecutor, ObjectProvider<DdcNonceStore> nonceStore) {
        return new DdcRpcServerInterceptor(properties, credentialRegistry, tenantRegistry,
                tenantContextExecutor, nonceStore.getIfAvailable());
    }

    /** 创建 Admin 领域异常 Mapper。 / Creates the Admin domain exception mapper. */
    @Bean(name = "ddcRpcProviderExceptionMapper")
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public RpcProviderExceptionMapper ddcRpcProviderExceptionMapper() {
        return new DdcRpcProviderExceptionMapper();
    }
}
