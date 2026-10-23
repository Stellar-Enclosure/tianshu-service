package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import java.util.List;

public record DdcAdminUiI18nBootstrap(DdcI18nScope scope, String defaultLocale, List<String> locales,
                                    List<Page> pages, boolean canInitialize) {
    public record Page(String pageCode, String name) { }
}
