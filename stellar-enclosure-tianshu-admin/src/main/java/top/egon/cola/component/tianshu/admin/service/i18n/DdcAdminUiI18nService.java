package top.egon.cola.component.tianshu.admin.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.config.DdcAdminUiI18nProperties;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nPublishCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAdminUiI18nBootstrap;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAdminUiI18nInitializeResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
                available && scopes.hasPermission(DdcAdminCapability.WRITE) && scopes.hasPermission(DdcAdminCapability.PUBLISH));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DdcI18nBundleResult bundle(String pageCode, String locale) {
        scopes.requirePermission(DdcAdminCapability.READ);
        Map<String, String> messages = catalog.messages(pageCode, locale, true);
        DdcI18nScope scope = properties.scope();
        DdcI18nPageBO common = null;
        DdcI18nPageBO page = null;
        if (scopeAvailable(scope)) {
            common = "common".equals(pageCode) ? null : published(scope, "common");
            page = published(scope, pageCode);
            if (common != null) merge(messages, common, locale);
            if (page != null) merge(messages, page, locale);
        }
        // Published identities and built-in content both participate; draft revisions never invalidate the bundle.
        StringBuilder identity = new StringBuilder("tianshu-admin-i18n-v1");
        for (String part : new String[]{EgonColaTenantIdProvider.currentTenantId().toString(), scope.bizCode(),
                scope.namespaceCode(), scope.env(), scope.appCode(), pageCode, locale,
                catalog.version("common"), catalog.version(pageCode), publishedIdentity(common), publishedIdentity(page)}) {
            identity.append(':').append(part.length()).append(':').append(part);
        }
        return new DdcI18nBundleResult(pageCode, locale,
                page == null ? DdcAdminUiI18nCatalog.DEFAULT_LOCALE : page.getPublishedDefaultLocale(),
                DdcAdminUiI18nCatalog.digest(identity.toString()), Collections.unmodifiableMap(messages));
    }

    @Transactional
    public DdcAdminUiI18nInitializeResult initialize() {
        scopes.requirePermission(DdcAdminCapability.READ);
        scopes.requirePermission(DdcAdminCapability.WRITE);
        scopes.requirePermission(DdcAdminCapability.PUBLISH);
        DdcI18nScope scope = properties.scope();
        scopes.require(scope, DdcAdminCapability.WRITE);
        scopes.require(scope, DdcAdminCapability.PUBLISH);
        int created = 0;
        int skipped = 0;
        for (var page : catalog.pages()) {
            if (repository.findByPageCode(scope, page.pageCode()).isPresent()) {
                skipped++;
                continue;
            }
            var result = pages.create(new DdcI18nCreateCommand(scope.bizCode(), scope.namespaceCode(), scope.env(), scope.appCode(),
                    page.pageCode(), page.name(), page.defaultLocale(), page.translations()));
            pages.publish(Long.valueOf(result.id()), new DdcI18nPublishCommand(result.revision()));
            created++;
        }
        return new DdcAdminUiI18nInitializeResult(created, skipped);
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

    private void merge(Map<String, String> target, DdcI18nPageBO page, String locale) {
        var translations = validation.read(page.getPublishedTranslations());
        target.putAll(translations.getOrDefault(page.getPublishedDefaultLocale(), Map.of()));
        target.putAll(translations.getOrDefault(locale, Map.of()));
    }

    private String publishedIdentity(DdcI18nPageBO page) {
        return page == null ? "" : page.getId() + ":" + page.getPublishedVersion();
    }
}
