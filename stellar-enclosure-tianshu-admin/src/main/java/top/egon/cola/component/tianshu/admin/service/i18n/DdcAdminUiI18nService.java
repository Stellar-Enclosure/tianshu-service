package top.egon.cola.component.tianshu.admin.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.config.DdcAdminUiI18nProperties;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAdminUiI18nBootstrap;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAdminUiI18nInitializeResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;

import java.util.LinkedHashSet;
import java.util.List;

@Service("ddcAdminUiI18nService")
@RequiredArgsConstructor
public class DdcAdminUiI18nService {
    private final DdcAdminUiI18nProperties properties;
    @Qualifier("ddcAdminUiI18nCatalog") private final DdcAdminUiI18nCatalog catalog;
    @Qualifier("ddcI18nPageRepository") private final DdcI18nPageRepository repository;
    @Qualifier("ddcI18nScopeValidator") private final DdcI18nScopeValidator scopes;
    @Qualifier("ddcI18nValidation") private final DdcI18nValidation validation;
    @Qualifier("ddcI18nPageService") private final DdcI18nPageService pages;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DdcAdminUiI18nBootstrap bootstrap() {
        scopes.requirePermission(DdcAdminCapability.READ);
        DdcI18nScope scope = properties.scope();
        boolean available = scopeAvailable(scope);
        LinkedHashSet<String> locales = new LinkedHashSet<>(List.of(DdcAdminUiI18nCatalog.DEFAULT_LOCALE, "en-US"));
        for (var page : catalog.pages()) {
            locales.addAll(page.translations().keySet());
            if (available) {
                DdcI18nPageBO published = published(scope, page.pageCode());
                if (published != null) locales.addAll(validation.read(published.getPublishedTranslations()).keySet());
            }
        }
        return new DdcAdminUiI18nBootstrap(scope, DdcAdminUiI18nCatalog.DEFAULT_LOCALE, List.copyOf(locales),
                catalog.pages().stream().map(page -> new DdcAdminUiI18nBootstrap.Page(page.pageCode(), page.name())).toList(),
                false);
    }

    public DdcI18nBundleResult bundle(String pageCode, String locale) {
        throw DdcI18nValidation.invalid("An explicit catalog scope and activation version are required");
    }

    public DdcI18nBundleResult bundle(String catalogScopeId, String activationVersion, String pageCode, String locale) {
        scopes.requirePermission(DdcAdminCapability.READ);
        return pages.bundle(properties.scope(), catalogScopeId, activationVersion, pageCode, locale);
    }

    public DdcAdminUiI18nInitializeResult initialize() {
        scopes.requirePermission(DdcAdminCapability.READ);
        throw new CommonException(56433, "TIANSHU_I18N_MANIFEST_REQUIRED", "Console defaults must be imported and activated through the standard producer manifest");
    }

    private boolean scopeAvailable(DdcI18nScope scope) {
        try {
            scopes.require(scope, DdcAdminCapability.READ);
            return true;
        } catch (CommonException unavailable) {
            // Missing/disabled metadata must not prevent administrators from entering the console to repair it.
            if (unavailable.getCode() == 56406 || unavailable.getCode() == 56042) return false;
            throw unavailable;
        }
    }

    private DdcI18nPageBO published(DdcI18nScope scope, String pageCode) {
        return repository.findByPageCode(scope, pageCode)
                .filter(page -> page.getPublishedVersion() != null && page.getPublishedTranslations() != null).orElse(null);
    }

}
