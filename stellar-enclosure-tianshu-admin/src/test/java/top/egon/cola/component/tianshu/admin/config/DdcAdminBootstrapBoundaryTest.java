package top.egon.cola.component.tianshu.admin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.util.ClassUtils;
import top.egon.cola.component.rpc.config.EgonRpcProperties;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real ConfigData bootstrap without database, OAuth or listener initialization. */
class DdcAdminBootstrapBoundaryTest {

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "si-catalog"})
    void localBootstrapPreservesBuildMetadataWithoutRemoteConfigOrSelfRegistration(String profile, @TempDir Path directory) {
        try (var context = new SpringApplicationBuilder(BootstrapConfiguration.class).web(WebApplicationType.NONE)
                .registerShutdownHook(false).logStartupInfo(false).properties(Map.of("spring.main.banner-mode", "off"))
                .run("--spring.profiles.active=" + profile, "--TIANSHU_SELF_REGISTRATION_ENABLED=true",
                        "--TIANSHU_RESOURCE_URI=https://resource.example/tianshu", "--TIANSHU_SI_CATALOG_READ_SCOPE=registered:si:read",
                        "--UNIFIED_XINGYUAN_RUNTIME_DIR=" + directory)) {
            var environment = context.getEnvironment();
            var imports = Binder.get(environment).bind("spring.config.import", Bindable.listOf(String.class)).get();
            assertThat(imports).contains("classpath:META-INF/egon-cola-tianshu.properties", "classpath:egon-tianshu-persistence.yml")
                    .noneMatch(value -> value.startsWith("tianshu:") || value.startsWith("optional:tianshu:"));
            assertThat(environment.getProperty("egon.cola.component.tianshu.enabled", Boolean.class, false)).isFalse();
            assertThat(environment.getProperty("egon.cola.component.tianshu.registry.enabled", Boolean.class, false)).isFalse();
            assertThat(environment.getProperty("egon.cola.component.tianshu.registry.http.enabled", Boolean.class, false)).isFalse();
            assertThat(environment.containsProperty("egon.cola.component.tianshu.rpc.target")).isFalse();
            String version = environment.getRequiredProperty("info.app.version");
            assertThat(version).isNotBlank().doesNotContain("${", "@");
            assertThat(context.getBean(InfoEndpoint.class).info())
                    .containsEntry("app", Map.of("name", "stellar-enclosure-tianshu-admin", "version", version));
            assertThat(context.getBean(EgonRpcProperties.class).getProvider().isEnabled()).isEqualTo(!"test".equals(profile));
        }
    }

    @Test
    void deliveredOpenApiMetadataDoesNotBringTheRetiredHttpRegistrationRuntime() {
        assertThat(ClassUtils.isPresent("top.egon.cola.component.tianshu.http.registration.DdcHttpRegistrationRuntime",
                getClass().getClassLoader())).isFalse();
    }

    @TestConfiguration(value = "ddcAdminBootstrapBoundaryTestConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(EgonRpcProperties.class)
    @Import({InfoContributorAutoConfiguration.class, InfoEndpointAutoConfiguration.class})
    static class BootstrapConfiguration { }
}
