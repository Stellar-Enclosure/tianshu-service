package top.egon.cola.component.tianshu.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DdcOutboxConfigurationTest {

    @Test
    void publicationAndPersistenceUseTheSameNamedUtcClock() {
        new ApplicationContextRunner()
                .withUserConfiguration(DdcOutboxConfiguration.class)
                .withBean("transactionManager", PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class))
                .withBean("egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties",
                        DdcAdminProperties.class, DdcAdminProperties::new)
                .withBean("idpClock", Clock.class, Clock::systemDefaultZone)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Clock publication = context.getBean("ddcPublishClock", Clock.class);
                    assertThat(context.getBean("egonColaMybatisPlusClock", Clock.class)).isSameAs(publication);
                    assertThat(publication.getZone()).isEqualTo(ZoneOffset.UTC);
                    assertThat(context.getBean("idpClock")).isNotSameAs(publication);
                    var route = context.getBean("ddcConfigRolloutOutboxRoute", DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO.class);
                    assertThat(route.channel()).isEqualTo("tianshu.config-rollout");
                    assertThat(route.schemaVersion()).isEqualTo("1");
                    assertThat(route.destination(DdcOutboxConfiguration.DdcConfigRolloutRequestedEvent.builder()
                            .tenantId("101").configId("2001").build())).isEqualTo("tianshu-config/101/2001");
                    assertThatThrownBy(() -> route.validateDestination("tianshu:v4:tenant:101:topic"))
                            .hasMessage("TIANSHU_CONFIG_ROLLOUT_DESTINATION_INVALID");
                });
    }
}
