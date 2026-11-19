package top.egon.cola.component.tianshu.autoconfigure;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class DdcRedisAutoConfigurationTest {

    @Test
    void supportedSdkClasspathContainsOnlyExplicitRedisRuntime() {
        var loader = DdcRedisAutoConfigurationTest.class.getClassLoader();
        assertThat(loader.getResource("org/redisson/api/RedissonClient.class")).isNotNull();
        assertThat(loader.getResource("org/redisson/spring/starter/RedissonAutoConfigurationV2.class")).isNull();
        assertThat(loader.getResource("org/redisson/spring/starter/RedissonAutoConfigurationV4.class")).isNull();
        assertThat(loader.getResource("org/springframework/data/redis/core/RedisTemplate.class")).isNull();
        assertThat(loader.getResource("org/redisson/spring/data/connection/RedissonConnectionFactory.class")).isNull();
    }

    @Test
    void retiredConfigurationAndRegistrySwitchesCannotCreateRedisClient() {
        RedissonClient client = mock(RedissonClient.class);

        try (MockedStatic<Redisson> redisson = mockStatic(Redisson.class)) {
            redisson.when(() -> Redisson.create(any(Config.class)))
                    .thenReturn(client);
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DdcRedisAutoConfiguration.class))
                    .withPropertyValues(
                            "egon.cola.component.tianshu.enabled=true",
                            "egon.cola.component.tianshu.redis.enabled=true",
                            "egon.cola.component.tianshu.tenant-id=42",
                            "egon.cola.component.tianshu.redis.username=tenant-42",
                            "egon.cola.component.tianshu.registry.enabled=true"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed().doesNotHaveBean(RedissonClient.class);
                    });

            redisson.verify(
                    () -> Redisson.create(any(Config.class)),
                    times(0)
            );
        }
    }

    @Test
    void createsClientWhenOnlyAiWorkspaceSubscriptionEnabled() {
        RedissonClient client = mock(RedissonClient.class);

        try (MockedStatic<Redisson> redisson = mockStatic(Redisson.class)) {
            redisson.when(() -> Redisson.create(any(Config.class))).thenReturn(client);
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DdcRedisAutoConfiguration.class))
                    .withPropertyValues(
                            "egon.cola.component.tianshu.tenant-id=42",
                            "egon.cola.component.tianshu.redis.username=tenant-42",
                            "egon.cola.component.tianshu.ai.skill.workspace.subscription-enabled=true",
                            "egon.cola.component.tianshu.delivery.enabled=true"
                    )
                    .run(context -> assertThat(context.getBean("ddcRedissonClient")).isSameAs(client));
        }
    }

    @Test
    void createsNoClientWhenNoRedisConsumerIsEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DdcRedisAutoConfiguration.class))
                .withPropertyValues("egon.cola.component.tianshu.ai.skill.workspace.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean("ddcRedissonClient"));
    }
    @Test
    void workspaceSubscriptionRetainsDedicatedClientAndRequiresTenantAuthority() {
        var client = mock(RedissonClient.class);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DdcRedisAutoConfiguration.class))
                .withBean("ddcRedissonClient", RedissonClient.class, () -> client)
                .withPropertyValues("egon.cola.component.tianshu.ai.skill.workspace.subscription-enabled=true")
                .run(context -> assertThat(context).hasNotFailed().getBean("ddcRedissonClient").isSameAs(client));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DdcRedisAutoConfiguration.class))
                .withPropertyValues("egon.cola.component.tianshu.ai.skill.workspace.subscription-enabled=true",
                        "egon.cola.component.tianshu.redis.username=tenant-42")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "egon.cola.component.tianshu.tenant-id must be a positive Long");
                });
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DdcRedisAutoConfiguration.class))
                .withPropertyValues("egon.cola.component.tianshu.ai.skill.workspace.subscription-enabled=true",
                        "egon.cola.component.tianshu.tenant-id=42")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "egon.cola.component.tianshu.redis.username is required when Redis is enabled");
                });
    }

}
