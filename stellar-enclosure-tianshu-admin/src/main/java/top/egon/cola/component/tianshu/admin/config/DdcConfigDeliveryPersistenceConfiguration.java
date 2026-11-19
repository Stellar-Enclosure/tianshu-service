package top.egon.cola.component.tianshu.admin.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.mybatis.routing.EgonColaPhysicalTargetBO;
import top.egon.cola.component.common.mybatis.routing.EgonColaRoutingProfileBO;
import top.egon.cola.component.common.mybatis.routing.EgonColaRoutingProfileBO.PartitionKeyBO;
import top.egon.cola.component.common.mybatis.routing.EgonColaRoutingProfileBO.TableKindEnum;
import top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties;

import java.util.List;
import java.util.Map;

/** Keeps the config boot authority and tenant receipt transactions on the validated physical PRIMARY. */
@Configuration(value = "ddcConfigDeliveryPersistenceConfiguration", proxyBeanMethods = false)
public class DdcConfigDeliveryPersistenceConfiguration {
    private static final String BOOT_TABLE = "ddc_config_boot_binding_t0";
    private static final String RECEIPT_TABLE = "ddc_config_process_receipt";
    private static final String OBSERVATION_TABLE = "ddc_config_workload_observation";
    private static final String VERSION_TABLE = "ddc_config_version";

    @Bean("ddcConfigBootBindingTransactionTemplate")
    public TransactionTemplate ddcConfigBootBindingTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return authoritativeTransaction(transactionManager);
    }

    @Bean("ddcConfigReceiptTransactionTemplate")
    public TransactionTemplate ddcConfigReceiptTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return authoritativeTransaction(transactionManager);
    }

    @Bean("ddcConfigWorkloadObservationTransactionTemplate")
    public TransactionTemplate ddcConfigWorkloadObservationTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return authoritativeTransaction(transactionManager);
    }

    @Bean("ddcNativeRolloutTransactionTemplate")
    public TransactionTemplate ddcNativeRolloutTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return authoritativeTransaction(transactionManager);
    }

    @Bean("ddcConfigDeliveryTopologyGuard")
    public SmartInitializingSingleton ddcConfigDeliveryTopologyGuard(
            @Qualifier("egon.cola.component.mybatis-plus.sharding-top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties")
            EgonColaShardingProperties properties,
            @Qualifier("egonColaRoutingProfiles") Map<String, EgonColaRoutingProfileBO> profiles) {
        return () -> validateRoutes(properties, profiles);
    }

    private static TransactionTemplate authoritativeTransaction(PlatformTransactionManager transactionManager) {
        var template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(8);
        return template;
    }

    private static void validateRoutes(EgonColaShardingProperties properties, Map<String, EgonColaRoutingProfileBO> profiles) {
        if (!properties.isEnabled() || properties.getMode() != EgonColaShardingProperties.ModeEnum.SHARDING
                || properties.getConfigStyle() != EgonColaShardingProperties.ConfigStyleEnum.STRATEGY
                || !"LOCAL".equals(properties.getTransactionDefaultType()) || properties.getDataSources().size() != 1) {
            throw new IllegalStateException("TIANSHU_CONFIG_DELIVERY_SINGLE_PRIMARY_REQUIRED");
        }
        var primary = properties.getDataSources().getFirst();
        if (primary.role() != EgonColaShardingProperties.DataSourceRoleEnum.PRIMARY
                || !primary.name().equals(primary.logicalName()) || !"org.postgresql.Driver".equals(primary.driverClassName())) {
            throw new IllegalStateException("TIANSHU_CONFIG_DELIVERY_SINGLE_PRIMARY_REQUIRED");
        }
        requireRoute(profiles, BOOT_TABLE, BOOT_TABLE, TableKindEnum.SINGLE, primary.logicalName());
        requireRoute(profiles, RECEIPT_TABLE, RECEIPT_TABLE + "_t0", TableKindEnum.TENANT_LEGACY, primary.logicalName());
        requireRoute(profiles, OBSERVATION_TABLE, OBSERVATION_TABLE + "_t0", TableKindEnum.TENANT_LEGACY, primary.logicalName());
        requireRoute(profiles, VERSION_TABLE, VERSION_TABLE + "_t0", TableKindEnum.TENANT_LEGACY, primary.logicalName());
    }

    private static void requireRoute(Map<String, EgonColaRoutingProfileBO> profiles, String logicalTable,
                                     String physicalTable, TableKindEnum kind, String primary) {
        var profile = profiles.get(logicalTable);
        var expected = List.of(new EgonColaPhysicalTargetBO(primary, "public", physicalTable));
        var node = new PartitionKeyBO(0, 0);
        if (profile == null || profile.kind() != kind || profile.tenantSlotCount() != 1
                || profile.secondaryBucketCount() != 1 || !Map.of(node, expected).equals(profile.actualNodes())) {
            throw new IllegalStateException("TIANSHU_CONFIG_DELIVERY_ROUTE_MISMATCH: " + logicalTable);
        }
    }
}
