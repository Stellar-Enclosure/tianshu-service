package top.egon.cola.component.tianshu.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;

@Getter
@Setter
@ConfigurationProperties(prefix = "egon.cola.component.tianshu.admin.ui-i18n")
public class DdcAdminUiI18nProperties {
    private String bizCode = "xingyuan";
    private String namespaceCode = "default";
    private String env = "local";
    private String appCode = "tianshu";

    public DdcI18nScope scope() {
        return new DdcI18nScope(bizCode, namespaceCode, env, appCode);
    }
}
