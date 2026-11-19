package top.egon.cola.component.tianshu.autoconfigure;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties;
import top.egon.cola.component.tianshu.autoconfigure.properties.DdcTenantProperties;
import top.egon.cola.component.tianshu.redis.DdcRedisClientFactory;

/**
 * 仅为显式 AI 工作区订阅装配 Tianshu Redisson 客户端。 /
 * Configures a Tianshu Redisson client only for an explicit AI workspace subscription.
 */
@AutoConfiguration(before = DdcAutoConfiguration.class)
@EnableConfigurationProperties({DdcProperties.class, DdcTenantProperties.class})
public class DdcRedisAutoConfiguration {

    /**
     * 在显式启用 AI Skill 工作区订阅时创建唯一客户端。 /
     * Creates the sole client only when AI Skill workspace subscription is explicitly enabled.
     *
     * @param properties Tianshu 属性 / Tianshu properties
     * @return 共享 Redisson 客户端 / shared Redisson client
     */
    @Bean(name = "ddcRedissonClient", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "ddcRedissonClient")
    @ConditionalOnProperty(prefix = "egon.cola.component.tianshu.ai.skill.workspace",
            name = "subscription-enabled", havingValue = "true")
    public RedissonClient ddcRedissonClient(
            @Qualifier("egon.cola.component.tianshu-top.egon.cola.component.tianshu.autoconfigure.properties.DdcProperties")
            DdcProperties properties,
            @Qualifier("egon.cola.component.tianshu-top.egon.cola.component.tianshu.autoconfigure.properties.DdcTenantProperties")
            DdcTenantProperties tenantProperties) {
        DdcProperties.Redis redis = properties.getRedis();
        tenantProperties.requireTenantId();
        return DdcRedisClientFactory.create(
                redis.getMode(),
                redis.getNodes(),
                redis.getMasterName(),
                redis.getHost(),
                redis.getPort(),
                redis.getPassword(),
                tenantProperties.requireRedisUsername(),
                redis.getDatabase()
        );
    }
}
