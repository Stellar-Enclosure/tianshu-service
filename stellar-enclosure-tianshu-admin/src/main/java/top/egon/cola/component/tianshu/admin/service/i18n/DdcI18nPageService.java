package top.egon.cola.component.tianshu.admin.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogSelectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nPublishedOverrideBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcOperationLogBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceMessageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nOverrideEntryBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nPublishCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPageSummary;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPublishResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPublishedOverrideRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceMessageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Human draft and publication orchestration; source imports never call this service. */
@Service("ddcI18nPageService")
@RequiredArgsConstructor
public class DdcI18nPageService {
    private static final int SOURCE_PAGE_SIZE = 64;
    @Qualifier("ddcI18nPageRepository") private final DdcI18nPageRepository repository;
    @Qualifier("ddcI18nScopeValidator") private final DdcI18nScopeValidator scopes;
    @Qualifier("ddcI18nValidation") private final DdcI18nValidation validation;
    @Qualifier("ddcOperationLogRepository") private final DdcOperationLogRepository operationLogs;
    @Qualifier("ddcCatalogActivationService") private final DdcCatalogActivationService activations;
    @Qualifier("ddcI18nSourceMessageRepository") private final DdcI18nSourceMessageRepository messages;
    @Qualifier("ddcI18nPublishedOverrideRepository") private final DdcI18nPublishedOverrideRepository publications;
    @Qualifier("ddcMetadataScopeRepository") private final DdcMetadataScopeRepository metadataScopes;
    @Qualifier("ddcI18nBundleAssembler") private final DdcI18nBundleAssembler assembler;
    @Qualifier("ddcMetadataTransactionTemplate") private final TransactionTemplate transaction;
    @Qualifier("ddcMetadataReadTransactionTemplate") private final TransactionTemplate readTransaction;
    @Qualifier("ddcPublishClock") private final Clock clock;

    public DdcI18nPageResult create(DdcI18nCreateCommand command) {
        DdcI18nScope scope = new DdcI18nScope(command.bizCode(), command.namespaceCode(), command.env(), command.appCode());
        scopes.require(scope, DdcAdminCapability.WRITE);
        validation.key(command.pageCode()); validation.name(command.name());
        var selection = activations.resolve(target(scope, command.catalogScopeId()), command.catalogActivationVersion());
        var source = sources(selection, command.pageCode(), false);
        String draft = validation.overrides(command.defaultLocale(), command.translations(), source);
        String reviewed = validation.reviewedSources(command.reviewedSourceHashes(), source);
        return Objects.requireNonNull(transaction.execute(status -> {
            lockTarget(selection.descriptor().scope());
            Long namespaceId = scopes.require(scope, DdcAdminCapability.WRITE);
            if (repository.findByPageCode(scope, command.pageCode()).isPresent()) {
                throw new CommonException(56403, "TIANSHU_I18N_PAGE_EXISTS", "Page code already exists in this scope");
            }
            var page = repository.insert(DdcI18nPageBO.builder().bizCode(scope.bizCode()).namespaceId(namespaceId)
                    .namespaceCode(scope.namespaceCode()).envCode(scope.env()).appCode(scope.appCode()).pageCode(command.pageCode())
                    .name(command.name()).defaultLocale(command.defaultLocale()).draftTranslations(draft)
                    .catalogScopeId(Long.valueOf(command.catalogScopeId())).draftActivationVersion(Long.valueOf(command.catalogActivationVersion()))
                    .draftSourceHashes(reviewed).build());
            audit(page, "I18N_CREATE");
            return result(page, selection, source);
        }));
    }

    public DdcI18nPageResult get(Long id) {
        var page = requirePage(id);
        return get(id, decimal(page.getDraftActivationVersion()));
    }

    public DdcI18nPageResult get(Long id, String activationVersion) {
        var page = requirePage(id);
        assertScope(page, DdcAdminCapability.READ);
        var selection = activations.resolve(target(page), activationVersion);
        return Objects.requireNonNull(readTransaction.execute(status -> {
            var current = requirePage(id); assertScope(current, DdcAdminCapability.READ);
            return result(current, selection, sources(selection, current.getPageCode(), true));
        }));
    }

    public DdcI18nPageResult sourcePage(DdcI18nScope scope, String catalogScopeId, String activationVersion, String pageCode) {
        scopes.require(scope, DdcAdminCapability.READ);
        var selection = activations.resolve(target(scope, catalogScopeId), activationVersion);
        return Objects.requireNonNull(readTransaction.execute(status -> {
            scopes.require(scope, DdcAdminCapability.READ);
            var source = sources(selection, pageCode, false);
            var page = repository.findByPageCode(scope, pageCode).orElseGet(() -> DdcI18nPageBO.builder().bizCode(scope.bizCode())
                    .namespaceCode(scope.namespaceCode()).envCode(scope.env()).appCode(scope.appCode()).pageCode(pageCode).name(pageCode)
                    .catalogScopeId(Long.valueOf(catalogScopeId)).draftActivationVersion(Long.valueOf(activationVersion))
                    .defaultLocale(source.values().iterator().next().defaultLocale()).draftTranslations("{}").draftSourceHashes("{}").build());
            return result(page, selection, source);
        }));
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

    public DdcI18nPageResult replaceDraft(Long id, DdcI18nDraftCommand command) {
        var prior = requirePage(id); assertScope(prior, DdcAdminCapability.WRITE);
        var selection = activations.resolve(target(prior), command.catalogActivationVersion());
        var source = sources(selection, prior.getPageCode(), false);
        validation.name(command.name());
        String draft = validation.overrides(command.defaultLocale(), command.translations(), source);
        String reviewed = validation.reviewedSources(command.reviewedSourceHashes(), source);
        return Objects.requireNonNull(transaction.execute(status -> {
            lockTarget(target(prior));
            var page = repository.lockById(id).orElseThrow(DdcI18nPageService::missing);
            assertScope(page, DdcAdminCapability.WRITE); revision(page, command.expectedRevision());
            page.setName(command.name()).setDefaultLocale(command.defaultLocale()).setDraftTranslations(draft)
                    .setDraftActivationVersion(Long.valueOf(command.catalogActivationVersion())).setDraftSourceHashes(reviewed);
            page = repository.update(page).orElseThrow(DdcI18nPageService::conflict);
            audit(page, "I18N_SAVE_DRAFT");
            return result(page, selection, source);
        }));
    }

    public DdcI18nPublishResult publish(Long id, DdcI18nPublishCommand command) {
        operation(command.operationId());
        var prior = requirePage(id); assertScope(prior, DdcAdminCapability.PUBLISH);
        String requestDigest = CanonicalJson.digest(Map.of("catalogScopeId", prior.getCatalogScopeId().toString(), "pageId", id.toString(),
                "operationId", command.operationId(), "expectedRevision", Objects.requireNonNull(command.expectedRevision())));
        // Resolving a missing local descriptor can use HTTP, so it always precedes the write transaction.
        var selection = activations.resolve(target(prior), prior.getDraftActivationVersion().toString());
        var source = sources(selection, prior.getPageCode(), false);
        return Objects.requireNonNull(transaction.execute(status -> {
            lockTarget(target(prior));
            var page = repository.lockById(id).orElseThrow(DdcI18nPageService::missing);
            assertScope(page, DdcAdminCapability.PUBLISH);
            var previous = publications.findByOperation(page.getCatalogScopeId(), id, command.operationId());
            if (previous.isPresent()) {
                if (!previous.get().getRequestDigest().equals(requestDigest)) throw conflict();
                return publication(previous.get());
            }
            revision(page, command.expectedRevision());
            if (!Objects.equals(page.getDraftActivationVersion(), prior.getDraftActivationVersion())) throw conflict();
            validation.overrides(page.getDefaultLocale(), validation.read(page.getDraftTranslations()), source);
            validation.reviewedSources(assembler.strings(page.getDraftSourceHashes()), source);
            var publishedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            long publicationRevision = page.getPublishedVersion() == null ? 1 : Math.addExact(page.getPublishedVersion(), 1);
            var snapshot = publications.insert(DdcI18nPublishedOverrideBO.builder().catalogScopeId(page.getCatalogScopeId()).pageId(id)
                    .pageCode(page.getPageCode()).publicationRevision(publicationRevision).draftRevision(page.getVersion())
                    .basedOnActivationVersion(page.getDraftActivationVersion()).publishOperationId(command.operationId()).requestDigest(requestDigest)
                    .defaultLocale(page.getDefaultLocale()).translationsJson(page.getDraftTranslations()).reviewedSourceHashesJson(page.getDraftSourceHashes())
                    .publishedAt(publishedAt).build());
            page.setCurrentOverrideId(snapshot.getId()).setPublishedVersion(publicationRevision).setPublishedDraftRevision(snapshot.getDraftRevision())
                    .setPublishedDefaultLocale(snapshot.getDefaultLocale()).setPublishedTranslations(snapshot.getTranslationsJson())
                    .setPublishedSourceHashes(snapshot.getReviewedSourceHashesJson()).setPublishedOperationId(snapshot.getPublishOperationId()).setPublishedAt(publishedAt);
            page = repository.update(page).orElseThrow(DdcI18nPageService::conflict);
            audit(page, "I18N_PUBLISH");
            return publication(snapshot);
        }));
    }

    public DdcI18nPublishResult publication(Long id, String operationId) {
        operation(operationId);
        var page = requirePage(id); assertScope(page, DdcAdminCapability.READ);
        return publication(publications.findByOperation(page.getCatalogScopeId(), id, operationId).orElseThrow(DdcI18nPageService::missing));
    }

    public DdcI18nBundleResult bundle(DdcI18nScope scope, String pageCode, String locale) {
        throw DdcI18nValidation.invalid("An explicit catalog scope and activation version are required");
    }

    public DdcI18nBundleResult bundle(DdcI18nScope scope, String catalogScopeId, String activationVersion, String pageCode, String locale) {
        scopes.require(scope, DdcAdminCapability.I18N_READ); validation.key(pageCode); validation.locale(locale);
        var selection = activations.resolve(target(scope, catalogScopeId), activationVersion);
        return Objects.requireNonNull(readTransaction.execute(status -> {
            scopes.require(scope, DdcAdminCapability.I18N_READ);
            var rows = sourceRows(selection, pageCode, true);
            var overrides = overrideRows(selection, pageCode, rows.size());
            Map<String, String> revisions = new TreeMap<>();
            for (String code : "common".equals(pageCode) ? List.of(pageCode) : List.of(pageCode, "common")) {
                var page = repository.findByPageCode(scope, code).orElse(null);
                if (page != null && !Long.valueOf(catalogScopeId).equals(page.getCatalogScopeId())) throw DdcCatalogActivationClient.unavailable();
                revisions.put(code, page == null || page.getPublishedVersion() == null ? "0" : page.getPublishedVersion().toString());
            }
            return assembler.assemble(selection.descriptor(), pageCode, locale, rows, overrides, revisions);
        }));
    }

    private Map<String, PermissionManifest.Message> sources(DdcCatalogSelectionBO selection, String pageCode, boolean allowRemoved) {
        var rows = sourceRows(selection, pageCode, false);
        if (allowRemoved && rows.stream().noneMatch(row -> row.getPageCode().equals(pageCode))) return Map.of();
        return assembler.sources(selection.descriptor(), pageCode, rows);
    }

    private DdcI18nPageResult result(DdcI18nPageBO page, DdcCatalogSelectionBO selection, Map<String, PermissionManifest.Message> source) {
        Map<String, Map<String, String>> defaults = new TreeMap<>();
        Map<String, String> hashes = new TreeMap<>();
        source.forEach((key, message) -> {
            hashes.put(key, I18nSourceRules.sourceHash(message));
            message.defaults().forEach((locale, text) -> defaults.computeIfAbsent(locale, ignored -> new TreeMap<>()).put(key, text));
        });
        Map<String, String> reviewed = assembler.strings(page.getDraftSourceHashes());
        List<DdcI18nBundleResult.Diagnostic> diagnostics = new ArrayList<>();
        reviewed.forEach((key, hash) -> {
            if (!hashes.containsKey(key)) diagnostics.add(new DdcI18nBundleResult.Diagnostic(page.getPageCode(), key, "SOURCE_REMOVED"));
            else if (!hash.equals(hashes.get(key))) diagnostics.add(new DdcI18nBundleResult.Diagnostic(page.getPageCode(), key, "SOURCE_CHANGED_REVIEW_REQUIRED"));
        });
        hashes.forEach((key, hash) -> {
            if (!reviewed.containsKey(key)) diagnostics.add(new DdcI18nBundleResult.Diagnostic(page.getPageCode(), key, "SOURCE_NOT_REVIEWED"));
        });
        translationConflicts(page.getPageCode(), validation.read(page.getDraftTranslations()), source, "DRAFT_PLACEHOLDER_CONFLICT", diagnostics);
        if (page.getPublishedTranslations() != null) {
            translationConflicts(page.getPageCode(), validation.read(page.getPublishedTranslations()), source, "PUBLISHED_PLACEHOLDER_CONFLICT", diagnostics);
        }
        return assembler.boundedResponse(new DdcI18nPageResult(decimal(page.getId()), page.getBizCode(), page.getNamespaceCode(), page.getEnvCode(), page.getAppCode(),
                page.getPageCode(), page.getName(), page.getDefaultLocale(), validation.read(page.getDraftTranslations()), decimal(page.getVersion()),
                decimal(page.getPublishedVersion()), selection.descriptor().scope(), selection.descriptor().activationVersion(), decimal(page.getDraftActivationVersion()),
                Map.copyOf(defaults), Map.copyOf(hashes), reviewed, page.getPublishedTranslations() == null ? Map.of() : validation.read(page.getPublishedTranslations()),
                page.getPublishedSourceHashes() == null ? Map.of() : assembler.strings(page.getPublishedSourceHashes()), List.copyOf(diagnostics)));
    }

    private List<DdcI18nSourceMessageBO> sourceRows(DdcCatalogSelectionBO selection, String pageCode, boolean includeCommon) {
        List<DdcI18nSourceMessageBO> result = new ArrayList<>();
        long bytes = 0;
        for (long offset = 0; ; offset += SOURCE_PAGE_SIZE) {
            var batch = messages.findByActivationPage(Long.valueOf(selection.descriptor().scope().catalogScopeId()), selection.projectionId(), pageCode, includeCommon, offset, SOURCE_PAGE_SIZE);
            for (var row : batch) {
                bytes += utf8(row.getDefaultsJson()) + utf8(row.getPageCode()) + utf8(row.getMessageKey()) + 256;
                if (bytes > DdcI18nBundleAssembler.MAX_RESPONSE_BYTES) throw DdcI18nBundleAssembler.responseTooLarge();
                result.add(row);
            }
            if (batch.size() < SOURCE_PAGE_SIZE) return List.copyOf(result);
        }
    }

    private List<DdcI18nOverrideEntryBO> overrideRows(DdcCatalogSelectionBO selection, String pageCode, int sourceCount) {
        List<DdcI18nOverrideEntryBO> result = new ArrayList<>();
        long bytes = 0;
        for (long offset = 0; offset < sourceCount; offset += SOURCE_PAGE_SIZE) {
            var batch = publications.findApplicableEntries(Long.valueOf(selection.descriptor().scope().catalogScopeId()), selection.projectionId(), pageCode, offset, SOURCE_PAGE_SIZE);
            for (var row : batch) {
                if (row.getPublicationRevision() == null) continue;
                bytes += utf8(row.getTranslationsJson()) + utf8(row.getPageCode()) + utf8(row.getMessageKey()) + 128;
                if (bytes > DdcI18nBundleAssembler.MAX_RESPONSE_BYTES) throw DdcI18nBundleAssembler.responseTooLarge();
                result.add(row);
            }
        }
        return List.copyOf(result);
    }

    private int utf8(String value) { return value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; }

    private void translationConflicts(String pageCode, Map<String, Map<String, String>> translations, Map<String, PermissionManifest.Message> source,
            String reason, List<DdcI18nBundleResult.Diagnostic> diagnostics) {
        java.util.Set<String> conflicts = new java.util.TreeSet<>();
        for (var language : translations.values()) {
            language.forEach((key, text) -> {
                var message = source.get(key);
                if (message == null) return;
                try {
                    if (!I18nSourceRules.placeholders(text).equals(I18nSourceRules.placeholders(message.defaults().get(message.defaultLocale())))) conflicts.add(key);
                } catch (IllegalArgumentException invalid) { conflicts.add(key); }
            });
        }
        conflicts.forEach(key -> diagnostics.add(new DdcI18nBundleResult.Diagnostic(pageCode, key, reason)));
    }

    private DdcI18nPublishResult publication(DdcI18nPublishedOverrideBO snapshot) {
        return new DdcI18nPublishResult(snapshot.getPageId().toString(), snapshot.getPublishOperationId(), snapshot.getPublicationRevision().toString(),
                snapshot.getDraftRevision().toString(), snapshot.getBasedOnActivationVersion().toString(), snapshot.getPublishedAt(),
                validation.read(snapshot.getTranslationsJson()), assembler.strings(snapshot.getReviewedSourceHashesJson()));
    }

    private void lockTarget(CatalogScope expected) {
        var actual = metadataScopes.lockEnabledTarget(Long.valueOf(expected.catalogScopeId())).orElseThrow(DdcCatalogActivationClient::unavailable);
        if (!expected.equals(new CatalogScope(actual.getCatalogScopeId().toString(), actual.getTenantId().toString(), actual.getBizCode(),
                actual.getAppCode(), actual.getNamespaceCode(), actual.getEnvCode()))) throw DdcCatalogActivationClient.unavailable();
    }

    private CatalogScope target(DdcI18nScope scope, String catalogScopeId) {
        return new CatalogScope(catalogScopeId, EgonColaTenantIdProvider.currentTenantId().toString(), scope.bizCode(), scope.appCode(), scope.namespaceCode(), scope.env());
    }
    private CatalogScope target(DdcI18nPageBO page) { return target(scope(page), page.getCatalogScopeId().toString()); }
    private DdcI18nScope scope(DdcI18nPageBO page) { return new DdcI18nScope(page.getBizCode(), page.getNamespaceCode(), page.getEnvCode(), page.getAppCode()); }
    private void assertScope(DdcI18nPageBO page, DdcAdminCapability capability) {
        if (!Objects.equals(scopes.require(scope(page), capability), page.getNamespaceId())) throw missing();
    }
    private DdcI18nPageBO requirePage(Long id) {
        if (id == null || id <= 0) throw DdcI18nValidation.invalid("Invalid page id");
        return repository.findById(id).orElseThrow(DdcI18nPageService::missing);
    }
    private void revision(DdcI18nPageBO page, String expected) {
        try {
            if (expected == null || !expected.matches("(0|[1-9][0-9]{0,18})")) throw new NumberFormatException();
            if (!Objects.equals(page.getVersion(), Long.valueOf(expected))) throw conflict();
        } catch (NumberFormatException invalid) { throw DdcI18nValidation.invalid("Invalid revision"); }
    }
    private void operation(String operationId) {
        if (operationId == null || !UUID.fromString(operationId).toString().equals(operationId)) throw DdcI18nValidation.invalid("A canonical publication operation UUID is required");
    }
    private void audit(DdcI18nPageBO page, String operation) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        operationLogs.insert(DdcOperationLogBO.builder().bizCode(page.getBizCode()).appCode(page.getAppCode())
                .env(page.getEnvCode()).namespace(page.getNamespaceCode()).resourceName(page.getPageCode())
                .operationType(operation).operator(authentication.getName()).operationContent("pageId=" + page.getId() + ", revision=" + page.getVersion()
                        + ", publishedVersion=" + page.getPublishedVersion()).createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).build());
    }
    private static String decimal(Long value) { return value == null ? null : Long.toString(value); }
    private static CommonException conflict() { return new CommonException(56404, "TIANSHU_I18N_REVISION_CONFLICT", "The page changed; reload before saving or publishing"); }
    private static CommonException missing() { return new CommonException(56402, "TIANSHU_I18N_PAGE_NOT_FOUND", "Page or published language bundle not found"); }
}
