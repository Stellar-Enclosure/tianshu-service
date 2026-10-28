package top.egon.cola.component.tianshu.admin.service.list;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;

import static org.assertj.core.api.Assertions.assertThat;

class DdcListScopePolicyTest {
    @Test void deployedDefaultScopeMappingMatchesTheActivatedListDeclaration() throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        var policy = Binder.get(environment).bind("egon.cola.platform.tianquan.jianshen.user-scope-policy",
                Bindable.of(Rbac3StarterProperties.UserScopePolicyProperties.class)).orElseThrow(IllegalStateException::new);
        assertThat(ListDefinitionBinding.scopePolicyDigest(policy.isEnabled(), policy.getPermissionScopes()))
                .isEqualTo("d8c4afc7a2fcbf82475f4c18f36904511a4485843e93de7dd94ba71ab8f3b416");
    }
}
