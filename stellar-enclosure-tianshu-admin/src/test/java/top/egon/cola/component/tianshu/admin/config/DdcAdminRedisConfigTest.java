package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.component.tianshu.admin.repository.DdcRedisRepository;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DdcAdminRedisConfigTest {
    @Test void retainsIndependentCacheWithoutAnyConfigurationLeaseProducer() {
        RedissonClient redis = mock(RedissonClient.class);
        new ApplicationContextRunner().withUserConfiguration(DdcAdminRedisConfig.class)
                .withBean("ddcAdminRedissonClient", RedissonClient.class, () -> redis)
                .withBean("jacksonObjectMapper", ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DdcRedisRepository.class);
                    assertThat(context.getBean("redissonClient")).isSameAs(redis);
                    assertThat(context).doesNotHaveBean("ddcConfigLeaseRedisRepository")
                            .doesNotHaveBean("ddcConfigLeaseService").doesNotHaveBean("ddcLeaseExpiryScanner")
                            .doesNotHaveBean("ddcResourceAdmissionRevocationService")
                            .doesNotHaveBean("ddcServiceRegistryRedisRepository")
                            .doesNotHaveBean("ddcServiceRegistryService")
                            .doesNotHaveBean("ddcNotificationPublisher")
                            .doesNotHaveBean("ddcRegistryEventDeliveryHandler")
                            .doesNotHaveBean("ddcRegistrationCredentialVerifier")
                            .doesNotHaveBean("ddcNonceStore");
                });
    }

    @Test void anExistingIndependentCacheDoesNotEnableTheRetiredLeaseStack() {
        RedissonClient supplied = mock(RedissonClient.class);
        new ApplicationContextRunner().withUserConfiguration(DdcAdminRedisConfig.class)
                .withPropertyValues("egon.cola.component.tianshu.admin.redis.enabled=false")
                .withBean("redissonClient", RedissonClient.class, () -> supplied)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("redissonClient")).isSameAs(supplied);
                    assertThat(context).doesNotHaveBean("ddcAdminRedissonClient")
                            .doesNotHaveBean("ddcConfigLeaseRedisRepository").doesNotHaveBean("ddcConfigLeaseService")
                            .doesNotHaveBean("ddcLeaseExpiryScanner");
                });
    }

    @Test void redisConfigurationDeclaresNoLeaseSchedulerOrConfigurationLeaseFactories() {
        assertThat(Arrays.stream(DdcAdminRedisConfig.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("ddcConfigLeaseRedisRepository", "ddcConfigLeaseService", "ddcLeaseExpiryScanner",
                        "ddcServiceRegistryRedisRepository", "ddcServiceRegistryService", "ddcRegistrationCredentialVerifier");
    }
}
