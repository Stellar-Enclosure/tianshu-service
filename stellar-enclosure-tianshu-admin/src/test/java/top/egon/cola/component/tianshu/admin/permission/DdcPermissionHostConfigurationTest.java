package top.egon.cola.component.tianshu.admin.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class DdcPermissionHostConfigurationTest {
    @Test void hostScopeAndTheTwoNativeExceptionsCannotFallBackToLegacyAuthorization() {
        var loader = new YamlPropertiesFactoryBean();
        loader.setResources(new ClassPathResource("application.yml"));
        var properties = loader.getObject();
        String prefix = "egon.cola.platform.tianquan.jianshen.";
        assertThat(properties.getProperty(prefix + "system-code")).isEqualTo("tianshu");
        assertThat(properties.getProperty(prefix + "runtime.catalog-scopes[0].business-code")).isEqualTo("xingyuan");
        assertThat(properties.getProperty(prefix + "runtime.catalog-scopes[0].application-code")).isEqualTo("tianshu");
        assertThat(properties.getProperty(prefix + "runtime.bootstrap-service-resource-keys[0]")).isEqualTo("tianshu.api.i18nSourceImport.importSource");
        assertThat(properties.getProperty(prefix + "runtime.bootstrap-service-resource-keys[1]")).isEqualTo("tianshu.api.i18nSourceImport.receipt");
        assertThat(properties.getProperty(prefix + "runtime.bootstrap-service-resource-keys[2]")).isNull();
        assertThat(properties.getProperty(prefix + "runtime.anonymous-resource-keys[0]")).isEqualTo("tianshu.api.adminUiI18n.publicBundle");
        assertThat(properties.getProperty(prefix + "runtime.anonymous-resource-keys[1]")).isNull();
        assertThat(properties.getProperty(prefix + "user-scope-policy.enabled")).isEqualTo("true");
        assertThat(properties.getProperty(prefix + "user-scope-policy.permission-scopes.TIANSHU_AI_PUBLISH[0]")).isEqualTo("TIANSHU_AI_WRITE");
        assertThat(properties).doesNotContainKeys(prefix + "runtime.redis-enabled",
                "egon.cola.platform.tianquan.shoubing.revocation.primary-endpoint",
                "egon.cola.platform.tianquan.shoubing.audit.stream");
    }
}
