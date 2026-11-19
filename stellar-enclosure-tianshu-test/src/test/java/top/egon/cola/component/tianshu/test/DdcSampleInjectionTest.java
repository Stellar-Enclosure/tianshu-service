package top.egon.cola.component.tianshu.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.component.tianshu.autoconfigure.DdcAutoConfiguration;
import top.egon.cola.component.tianshu.test.service.SampleConfigService;

class DdcSampleInjectionTest {
    private final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdcAutoConfiguration.class))
            .withBean("sampleConfigService", SampleConfigService.class, SampleConfigService::new);

    @Test
    void localModeUsesRealSpringAnnotationDefaultsWithoutAnyTransport() {
        runner.withPropertyValues("egon.cola.component.tianshu.enabled=false").run(context -> {
            assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean("ddcConfigSnapshotCoordinator")
                .doesNotHaveBean("ddcConfigSnapshotHttpClient")
                .doesNotHaveBean("ddcConfigClient")
                .doesNotHaveBean("ddcRuntimeCoordinator")
                .doesNotHaveBean("ddcRedissonClient");
            assertThat(context.getBean(SampleConfigService.class).getDowngradeSwitch()).isFalse();
            assertThat(context.getBean(SampleConfigService.class).getRateLimit()).isEqualTo(100);
        });
    }

    @Test
    void explicitLocalValuesStillUseTheRealComposedValueAnnotation() {
        runner
            .withPropertyValues("egon.cola.component.tianshu.enabled=false", "downgrade-switch=true",
                "order.rate-limit.permits-per-second=175")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SampleConfigService.class).getDowngradeSwitch()).isTrue();
                assertThat(context.getBean(SampleConfigService.class).getRateLimit()).isEqualTo(175);
            });
    }

    @Test
    void enablingTheSdkCannotSilentlyFallBackToOfflineDefaultsWithoutNativeDelivery() {
        runner.withPropertyValues("egon.cola.component.tianshu.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("CONFIG_NATIVE_DELIVERY_REQUIRED");
        });
    }
}
