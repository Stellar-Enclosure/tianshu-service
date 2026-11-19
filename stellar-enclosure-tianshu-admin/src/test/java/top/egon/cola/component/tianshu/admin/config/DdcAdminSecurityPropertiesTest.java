package top.egon.cola.component.tianshu.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties;

import static org.assertj.core.api.Assertions.assertThat;

class DdcAdminSecurityPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void secureDefaultsRequireExplicitLocalDevelopmentMode() {
        DdcAdminProperties properties = new DdcAdminProperties();

        assertThat(properties.getSecurity().isLocalDev()).isFalse();
        assertThat(DdcAdminCapability.READ.authority())
                .isEqualTo("CAP_TIANSHU_READ");
        assertThat(DdcAdminCapability.ALL.authority())
                .isEqualTo("CAP_*");
    }

    @Test
    void productionModeRequiresJwtIssuerAndAudience() {
        contextRunner
                .withPropertyValues(
                        "egon.cola.component.tianshu.admin.security.local-dev=false"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Tianshu Admin JWT issuer is required"
                            );
                });
    }

    @Test
    void productionSiCatalogProfileStartsWithoutLegacySigningSettings() {
        contextRunner
                .withInitializer(context -> {
                    try {
                        new YamlPropertySourceLoader().load("si-catalog", new ClassPathResource("application-si-catalog.yml"))
                                .forEach(context.getEnvironment().getPropertySources()::addLast);
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                })
                .withPropertyValues(
                        "egon.cola.component.tianshu.admin.security.local-dev=false",
                        "egon.cola.component.tianshu.admin.security.jwt.issuer=https://issuer.example",
                        "egon.cola.component.tianshu.admin.security.jwt.audience=tianshu-admin"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("egon.cola.component.rpc.provider.enabled", Boolean.class))
                            .isTrue();
                });
    }

    @Test
    void explicitLocalDevelopmentModeMayOmitJwtConfiguration() {
        contextRunner
                .withPropertyValues(
                        "egon.cola.component.tianshu.admin.security.local-dev=true"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void registrationScopeMustRemainConfigured() {
        contextRunner
                .withPropertyValues(
                        "egon.cola.component.tianshu.admin.security.local-dev=true",
                        "egon.cola.component.tianshu.admin.registration.required-scope="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Tianshu registration scope is required"
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DdcAdminProperties.class, DdcTenantAdminProperties.class})
    @Import(DdcAdminSecurityPropertiesValidator.class)
    static class TestConfiguration {
    }
}
