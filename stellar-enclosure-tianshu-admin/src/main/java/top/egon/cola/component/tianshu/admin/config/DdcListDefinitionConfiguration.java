package top.egon.cola.component.tianshu.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.component.tianshu.admin.service.list.DdcNamespaceListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;

@Configuration(proxyBeanMethods = false)
public class DdcListDefinitionConfiguration {
    @Bean("ddcNamespaceListDefinitionBinding")
    public ListDefinitionBinding namespaceListDefinitionBinding() { return DdcNamespaceListDefinition.BINDING; }
}
