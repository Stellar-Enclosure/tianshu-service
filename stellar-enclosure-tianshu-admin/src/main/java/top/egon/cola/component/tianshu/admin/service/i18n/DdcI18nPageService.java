package top.egon.cola.component.tianshu.admin.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcOperationLogBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nPublishCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPageSummary;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service("ddcI18nPageService")
@RequiredArgsConstructor
public class DdcI18nPageService {
    @Qualifier("ddcI18nPageRepository") private final DdcI18nPageRepository repository;
    @Qualifier("ddcI18nScopeValidator") private final DdcI18nScopeValidator scopes;
    @Qualifier("ddcI18nValidation") private final DdcI18nValidation validation;
    @Qualifier("ddcOperationLogRepository") private final DdcOperationLogRepository operationLogs;

    @Transactional
    public DdcI18nPageResult create(DdcI18nCreateCommand command) {
        DdcI18nScope scope = new DdcI18nScope(command.bizCode(), command.namespaceCode(), command.env(), command.appCode());
        Long namespaceId = scopes.require(scope, DdcAdminCapability.WRITE);
        validation.key(command.pageCode());
        validation.name(command.name());
        String draft = validation.translations(command.defaultLocale(), command.translations(), false);
        DdcI18nPageBO page = DdcI18nPageBO.builder().bizCode(scope.bizCode()).namespaceId(namespaceId)
                .namespaceCode(scope.namespaceCode()).envCode(scope.env()).appCode(scope.appCode())
                .pageCode(command.pageCode()).name(command.name()).defaultLocale(command.defaultLocale())
                .draftTranslations(draft).build();
        try {
            page = repository.insert(page);
        } catch (DuplicateKeyException duplicate) {
            throw new CommonException(56403, "TIANSHU_I18N_PAGE_EXISTS", "Page code already exists in this scope");
        }
        audit(page, "I18N_CREATE");
        return result(page);
    }

    public DdcI18nPageResult get(Long id) {
        DdcI18nPageBO page = requirePage(id);
        assertScope(page, DdcAdminCapability.READ);
        return result(page);
    }

    public DdcPageResult<DdcI18nPageSummary> page(DdcI18nScope scope, PageQuery query) {
        scopes.require(scope, DdcAdminCapability.READ);
        if (query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100) throw DdcI18nValidation.invalid("Invalid paging");
        var page = repository.search(scope, query);
        return DdcPageResult.<DdcI18nPageSummary>builder().records(page.getRecords().stream().map(row ->
                new DdcI18nPageSummary(decimal(row.getId()), row.getBizCode(), row.getNamespaceCode(), row.getEnvCode(),
                        row.getAppCode(), row.getPageCode(), row.getName(), row.getDefaultLocale(),
                        decimal(row.getVersion()), decimal(row.getPublishedVersion()))).toList())
                .pageNo(page.getPageNo()).pageSize(page.getPageSize()).total(page.getTotal()).build();
    }

    @Transactional
    public DdcI18nPageResult replaceDraft(Long id, DdcI18nDraftCommand command) {
        DdcI18nPageBO page = requirePage(id);
        assertScope(page, DdcAdminCapability.WRITE);
        revision(page, command.expectedRevision());
        validation.name(command.name());
        String draft = validation.translations(command.defaultLocale(), command.translations(), false);
        page.setName(command.name()).setDefaultLocale(command.defaultLocale()).setDraftTranslations(draft);
        page = repository.update(page).orElseThrow(DdcI18nPageService::conflict);
        audit(page, "I18N_SAVE_DRAFT");
        return result(page);
    }

    @Transactional
    public DdcI18nPageResult publish(Long id, DdcI18nPublishCommand command) {
        DdcI18nPageBO page = requirePage(id);
        assertScope(page, DdcAdminCapability.PUBLISH);
        revision(page, command.expectedRevision());
        validation.translations(page.getDefaultLocale(), validation.read(page.getDraftTranslations()), true);
        page.setPublishedTranslations(page.getDraftTranslations()).setPublishedDefaultLocale(page.getDefaultLocale())
                .setPublishedVersion(page.getPublishedVersion() == null ? 1 : Math.addExact(page.getPublishedVersion(), 1));
        page = repository.update(page).orElseThrow(DdcI18nPageService::conflict);
        audit(page, "I18N_PUBLISH");
        return result(page);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DdcI18nBundleResult bundle(DdcI18nScope scope, String pageCode, String locale) {
        scopes.require(scope, DdcAdminCapability.I18N_READ);
        validation.key(pageCode);
        validation.locale(locale);
        DdcI18nPageBO page = repository.findByPageCode(scope, pageCode).filter(this::published)
                .orElseThrow(DdcI18nPageService::missing);
        DdcI18nPageBO common = "common".equals(pageCode) ? null
                : repository.findByPageCode(scope, "common").filter(this::published).orElse(null);
        Map<String, String> messages = new LinkedHashMap<>();
        if (common != null) merge(messages, common, locale);
        merge(messages, page, locale);
        return new DdcI18nBundleResult(pageCode, locale, page.getPublishedDefaultLocale(),
                bundleVersion(scope, page, common, locale), java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(messages)));
    }

    private void merge(Map<String, String> target, DdcI18nPageBO page, String locale) {
        var translations = validation.read(page.getPublishedTranslations());
        target.putAll(translations.getOrDefault(page.getPublishedDefaultLocale(), Map.of()));
        target.putAll(translations.getOrDefault(locale, Map.of()));
    }

    private String bundleVersion(DdcI18nScope scope, DdcI18nPageBO page, DdcI18nPageBO common, String locale) {
        // Length-prefix each identity component to keep scope boundaries unambiguous.
        StringBuilder identity = new StringBuilder("i18n-v1");
        for (String value : new String[]{decimal(EgonColaTenantIdProvider.currentTenantId()), scope.bizCode(),
                scope.namespaceCode(), scope.env(), scope.appCode(), page.getPageCode(), locale,
                decimal(page.getId()), decimal(page.getPublishedVersion()),
                common == null ? "" : decimal(common.getId()), common == null ? "" : decimal(common.getPublishedVersion())}) {
            identity.append(':').append(value.length()).append(':').append(value);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private boolean published(DdcI18nPageBO page) {
        return page.getPublishedVersion() != null && page.getPublishedTranslations() != null;
    }

    private void assertScope(DdcI18nPageBO page, DdcAdminCapability capability) {
        Long namespaceId = scopes.require(new DdcI18nScope(page.getBizCode(), page.getNamespaceCode(),
                page.getEnvCode(), page.getAppCode()), capability);
        if (!Objects.equals(namespaceId, page.getNamespaceId())) throw missing();
    }

    private DdcI18nPageBO requirePage(Long id) {
        if (id == null || id <= 0) throw DdcI18nValidation.invalid("Invalid page id");
        return repository.findById(id).orElseThrow(DdcI18nPageService::missing);
    }

    private void revision(DdcI18nPageBO page, String expected) {
        if (expected == null || !expected.matches("^(0|[1-9][0-9]{0,18})$")) throw DdcI18nValidation.invalid("Invalid revision");
        try {
            if (!Objects.equals(page.getVersion(), Long.valueOf(expected))) throw conflict();
        } catch (NumberFormatException invalid) {
            throw DdcI18nValidation.invalid("Invalid revision");
        }
    }

    private DdcI18nPageResult result(DdcI18nPageBO page) {
        return new DdcI18nPageResult(decimal(page.getId()), page.getBizCode(), page.getNamespaceCode(), page.getEnvCode(),
                page.getAppCode(), page.getPageCode(), page.getName(), page.getDefaultLocale(),
                validation.read(page.getDraftTranslations()), decimal(page.getVersion()), decimal(page.getPublishedVersion()));
    }

    private void audit(DdcI18nPageBO page, String operation) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        operationLogs.insert(DdcOperationLogBO.builder().bizCode(page.getBizCode()).appCode(page.getAppCode())
                .env(page.getEnvCode()).namespace(page.getNamespaceCode()).resourceName(page.getPageCode())
                .operationType(operation).operator(authentication.getName())
                .operationContent("pageId=" + page.getId() + ", revision=" + page.getVersion()
                        + ", publishedVersion=" + page.getPublishedVersion()).createdAt(LocalDateTime.now()).build());
    }

    private static String decimal(Long value) { return value == null ? null : Long.toString(value); }
    private static CommonException conflict() {
        return new CommonException(56404, "TIANSHU_I18N_REVISION_CONFLICT", "The page changed; reload before saving or publishing");
    }
    private static CommonException missing() {
        return new CommonException(56402, "TIANSHU_I18N_PAGE_NOT_FOUND", "Page or published language bundle not found");
    }
}
