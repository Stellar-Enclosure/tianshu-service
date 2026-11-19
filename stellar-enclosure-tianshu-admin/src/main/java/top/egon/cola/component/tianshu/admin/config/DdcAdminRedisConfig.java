package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import top.egon.cola.component.tianshu.admin.repository.DdcRedisRepository;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcNonceStore;
import top.egon.cola.component.tianshu.admin.security.rpc.RedisDdcNonceStore;

import top.egon.cola.component.tianshu.redis.DdcRedisClientFactory;


@Configuration
@EnableScheduling
@EnableConfigurationProperties(DdcAdminProperties.class)
public class DdcAdminRedisConfig {

    /** Keeps Common cache on the configuration center's own Redis connection. */
    @Bean(name = "redissonClient", destroyMethod = "")
    @ConditionalOnMissingBean(name = "redissonClient")
    @ConditionalOnProperty(prefix = "egon.cola.component.tianshu.admin.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    RedissonClient applicationCacheRedissonClient(@Qualifier("ddcAdminRedissonClient") RedissonClient client) {
        return client;
    }

    @Bean("ddcAdminRedissonClient")
    @ConditionalOnMissingBean(name = "ddcAdminRedissonClient")
    @ConditionalOnProperty(prefix = "egon.cola.component.tianshu.admin.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedissonClient ddcAdminRedissonClient(DdcAdminProperties properties) {
        DdcAdminProperties.Redis redis = properties.getRedis();
        return DdcRedisClientFactory.create(
                redis.getMode(),
                redis.getNodes(),
                redis.getMasterName(),
                redis.getHost(),
                redis.getPort(),
                redis.getPassword(),
                redis.getDatabase()
        );
    }

    @Bean("ddcRedisRepository")
    @ConditionalOnBean(name = "ddcAdminRedissonClient")
    @ConditionalOnMissingBean
    public DdcRedisRepository ddcRedisRepository(
            @Qualifier("ddcAdminRedissonClient") RedissonClient redissonClient,
            @Qualifier("jacksonObjectMapper") ObjectMapper objectMapper) {
        return new DdcRedisRepository(redissonClient, objectMapper);
    }

    @Bean("ddcNonceStore")
    @ConditionalOnBean(name = "ddcAdminRedissonClient")
    @ConditionalOnMissingBean(DdcNonceStore.class)
    public DdcNonceStore redisDdcNonceStore(
            @Qualifier("ddcAdminRedissonClient")
            RedissonClient redissonClient) {
        return new RedisDdcNonceStore(redissonClient);
    }

}
