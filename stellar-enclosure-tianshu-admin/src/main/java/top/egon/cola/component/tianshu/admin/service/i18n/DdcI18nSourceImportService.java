package top.egon.cola.component.tianshu.admin.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nImportReceiptBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceMessageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceVersionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcMetadataScopeBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcOperationLogBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcResourceMetadataProjectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportGrantBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportIdentityBO;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nImportReceiptRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceMessageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nSourceVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcMetadataScopeRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcResourceMetadataProjectionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcSourceImportGrantRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifestCanonicalizer;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionRequirement;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportReceipt;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/** Registers immutable candidate metadata; human translation pages and activation pointers are not dependencies. */
@Service("ddcI18nSourceImportService")
@RequiredArgsConstructor
public class DdcI18nSourceImportService {
    @Qualifier("ddcMetadataScopeRepository") private final DdcMetadataScopeRepository scopes;
    @Qualifier("ddcSourceImportGrantRepository") private final DdcSourceImportGrantRepository grants;
    @Qualifier("ddcI18nSourceVersionRepository") private final DdcI18nSourceVersionRepository sources;
    @Qualifier("ddcResourceMetadataProjectionRepository") private final DdcResourceMetadataProjectionRepository projections;
    @Qualifier("ddcI18nSourceMessageRepository") private final DdcI18nSourceMessageRepository messages;
    @Qualifier("ddcI18nImportReceiptRepository") private final DdcI18nImportReceiptRepository receipts;
    @Qualifier("ddcOperationLogRepository") private final DdcOperationLogRepository operationLogs;
    @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor contexts;
    @Qualifier("ddcMetadataTransactionTemplate") private final TransactionTemplate transaction;
    @Qualifier("ddcPublishClock") private final Clock clock;
    @Qualifier("ddcI18nSourceImportJson") private final DdcI18nSourceImportJson json;
    private final PermissionManifestCanonicalizer canonicalizer = new PermissionManifestCanonicalizer();

    public SourceImportReceipt importSource(DdcSourceImportIdentityBO identity, SourceImportRequest request) {
        Objects.requireNonNull(request, "source import request");
        requireTenant(identity, request.scope());
        validatePages(request.manifest());
        String manifest = canonicalizer.canonicalize(request.manifest());
        DdcTenantContextDTO context = context(identity);
        return contexts.call(context, () -> Objects.requireNonNull(transaction.execute(status -> register(identity, request, manifest))));
    }

    public SourceImportReceipt receipt(DdcSourceImportIdentityBO identity, CatalogScope scope, String producerId, String operationId) {
        requireTenant(identity, scope);
        if (operationId == null || !UUID.fromString(operationId).toString().equals(operationId)) {
            throw new IllegalArgumentException("operationId must be a canonical UUID");
        }
        if (producerId == null || producerId.isBlank() || producerId.length() > 128) throw new IllegalArgumentException("Invalid producerId");
        return contexts.call(context(identity), () -> Objects.requireNonNull(transaction.execute(status -> {
            authorizeAndLock(identity, scope, producerId);
            DdcI18nImportReceiptBO stored = receipts.findByOperation(Long.valueOf(scope.catalogScopeId()), producerId, operationId)
                    .orElseThrow(() -> new CommonException(56422, "TIANSHU_METADATA_RECEIPT_NOT_FOUND", "Source import receipt not found"));
            SourceImportReceipt result = storedReceipt(stored);
            if (!result.scope().equals(scope) || !result.producerId().equals(producerId) || !result.operationId().equals(operationId)) {
                throw new IllegalStateException("TIANSHU_METADATA_RECEIPT_IDENTITY_MISMATCH");
            }
            return result;
        })));
    }

    private SourceImportReceipt register(DdcSourceImportIdentityBO identity, SourceImportRequest request, String manifest) {
        Long scopeId = Long.valueOf(request.scope().catalogScopeId());
        DdcSourceImportGrantBO grant = authorizeAndLock(identity, request.scope(), request.producerId());
        var previous = receipts.findByOperation(scopeId, request.producerId(), request.operationId());
        if (previous.isPresent()) {
            if (!request.requestDigest().equals(previous.get().getRequestDigest())) throw conflict();
            SourceImportReceipt receipt = storedReceipt(previous.get());
            if (!receipt.matches(request)) throw new IllegalStateException("TIANSHU_METADATA_RECEIPT_CONTENT_MISMATCH");
            return receipt;
        }

        DdcI18nSourceVersionBO source = sources.findByVersion(scopeId, request.producerId(), request.sourceVersion()).orElse(null);
        if (source == null) {
            source = sources.insert(DdcI18nSourceVersionBO.builder().catalogScopeId(scopeId).producerId(request.producerId())
                    .schemaVersion(request.manifest().schemaVersion()).sourceVersion(request.sourceVersion())
                    .manifestDigest(request.digests().manifestDigest()).policyDigest(request.digests().policyDigest())
                    .metadataDigest(request.digests().metadataDigest()).projectionDigest(request.projectionDigest())
                    .canonicalManifestJson(manifest).resourceIdMapJson(CanonicalJson.write(request.resourceIds()))
                    .permissionIdMapJson(CanonicalJson.write(request.permissionIds())).resourceCount(request.resourceIds().size())
                    .permissionCount(request.permissionIds().size()).messageCount(request.manifest().messages().size()).build());
            projections.insertAll(projections(request, source.getId()));
            messages.insertAll(messages(request, source.getId()));
        } else {
            if (!request.projectionDigest().equals(source.getProjectionDigest()) || !manifest.equals(source.getCanonicalManifestJson())
                    || !request.digests().policyDigest().equals(source.getPolicyDigest())
                    || !request.digests().metadataDigest().equals(source.getMetadataDigest())
                    || !CanonicalJson.write(request.resourceIds()).equals(source.getResourceIdMapJson())
                    || !CanonicalJson.write(request.permissionIds()).equals(source.getPermissionIdMapJson())) throw conflict();
        }

        Long receiptId = receipts.nextReceiptId();
        var importedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        SourceImportReceipt receipt = new SourceImportReceipt(receiptId.toString(), request.operationId(), request.scope(),
                request.producerId(), request.producerVersion(), request.sourceVersion(), request.digests(), request.projectionDigest(),
                request.resourceIds().size(), request.permissionIds().size(), request.manifest().messages().size(), importedAt);
        receipts.insert(DdcI18nImportReceiptBO.builder().id(receiptId).catalogScopeId(scopeId).operationId(request.operationId())
                .producerId(request.producerId()).producerVersion(Long.valueOf(request.producerVersion())).sourceId(source.getId())
                .sourceVersion(request.sourceVersion()).manifestDigest(request.digests().manifestDigest())
                .policyDigest(request.digests().policyDigest()).metadataDigest(request.digests().metadataDigest())
                .requestDigest(request.requestDigest()).grantId(grant.getId()).grantVersion(grant.getVersion())
                .publisherIssuer(identity.issuer().toString()).publisherClientId(identity.principal().clientId())
                .responseJson(json.writeReceipt(receipt)).build());
        operationLogs.insert(DdcOperationLogBO.builder().bizCode(request.scope().businessCode()).appCode(request.scope().applicationCode())
                .env(request.scope().environment()).namespace(request.scope().namespaceCode()).resourceName(request.producerId())
                .operationType("METADATA_SOURCE_IMPORT").operator("service-grant:" + grant.getId())
                .operationContent(CanonicalJson.write(Map.of("operationId", request.operationId(), "catalogScopeId", request.scope().catalogScopeId(),
                        "producerVersion", request.producerVersion(), "sourceVersion", request.sourceVersion(), "receiptId", receipt.receiptId(),
                        "grantId", grant.getId().toString(), "grantVersion", grant.getVersion().toString(), "publisher", identity.principal().clientId())))
                .createdAt(LocalDateTime.ofInstant(importedAt, ZoneOffset.UTC)).build());
        return receipt;
    }

    private DdcSourceImportGrantBO authorizeAndLock(DdcSourceImportIdentityBO identity, CatalogScope target, String producerId) {
        Long scopeId = Long.valueOf(target.catalogScopeId());
        String bindingDigest = identity.bindingDigest();
        // Reject ungranted targets before taking their serialization lock, then recheck under the grant lock.
        requireGrant(grants.findEnabled(scopeId, producerId, bindingDigest).orElseThrow(DdcI18nSourceImportService::forbidden), identity, target, producerId);
        DdcMetadataScopeBO scope = scopes.lockEnabledTarget(scopeId).orElseThrow(DdcI18nSourceImportService::forbidden);
        CatalogScope actual = new CatalogScope(scope.getCatalogScopeId().toString(), scope.getTenantId().toString(), scope.getBizCode(),
                scope.getAppCode(), scope.getNamespaceCode(), scope.getEnvCode());
        if (!target.equals(actual)) throw forbidden();
        DdcSourceImportGrantBO grant = grants.lockEnabled(scopeId, producerId, bindingDigest).orElseThrow(DdcI18nSourceImportService::forbidden);
        requireGrant(grant, identity, target, producerId);
        return grant;
    }

    private void requireGrant(DdcSourceImportGrantBO grant, DdcSourceImportIdentityBO identity, CatalogScope target, String producerId) {
        if (!Boolean.TRUE.equals(grant.getEnabled()) || !identity.tenantId().equals(grant.getTenantId())
                || !Long.valueOf(target.catalogScopeId()).equals(grant.getCatalogScopeId()) || !producerId.equals(grant.getProducerId())
                || !identity.issuer().toString().equals(grant.getIssuerUri()) || !identity.principal().clientId().equals(grant.getClientId())
                || !identity.principal().resourceUri().toString().equals(grant.getResourceUri())
                || !identity.bindingDigest().equals(grant.getPrincipalBindingDigest()) || grant.getVersion() == null || grant.getVersion() < 0) {
            throw forbidden();
        }
    }

    private SourceImportReceipt storedReceipt(DdcI18nImportReceiptBO stored) {
        try {
            SourceImportReceipt receipt = json.readReceipt(stored.getResponseJson());
            if (!stored.getId().toString().equals(receipt.receiptId()) || !stored.getSourceVersion().equals(receipt.sourceVersion())
                    || !stored.getProducerVersion().toString().equals(receipt.producerVersion())) {
                throw new IllegalArgumentException("Stored receipt binding mismatch");
            }
            return receipt;
        } catch (IllegalArgumentException corrupt) {
            throw new IllegalStateException("TIANSHU_METADATA_STORED_RECEIPT_INVALID", corrupt);
        }
    }

    private List<DdcResourceMetadataProjectionBO> projections(SourceImportRequest request, Long sourceId) {
        List<DdcResourceMetadataProjectionBO> result = new ArrayList<>();
        for (var resource : request.manifest().resources()) {
            TreeSet<String> codes = new TreeSet<>();
            collect(codes, resource.requirement());
            if (resource.fieldPolicy() != null) {
                collect(codes, resource.fieldPolicy().plainRead()); collect(codes, resource.fieldPolicy().maskedRead()); collect(codes, resource.fieldPolicy().write());
            }
            result.add(DdcResourceMetadataProjectionBO.builder().catalogScopeId(Long.valueOf(request.scope().catalogScopeId())).sourceId(sourceId)
                    .producerId(request.producerId()).sourceVersion(request.sourceVersion()).metadataKind("RESOURCE").definitionKey(resource.key())
                    .resourceId(Long.valueOf(request.resourceIds().get(resource.key()))).resourceType(resource.type().name()).definitionLifecycle(resource.lifecycle().name())
                    .pageCode(resource.i18n().pageCode()).labelKey(resource.i18n().labelKey()).descriptionKey(resource.i18n().descriptionKey())
                    .targetJson(CanonicalJson.write(resource.target())).policyJson(CanonicalJson.write(canonicalizer.resourceMap(resource)))
                    .i18nRefsJson(CanonicalJson.write(canonicalizer.i18nMap(resource.i18n()))).referencedPermissionCodesJson(CanonicalJson.write(List.copyOf(codes))).build());
        }
        for (var permission : request.manifest().permissionDefinitions()) {
            result.add(DdcResourceMetadataProjectionBO.builder().catalogScopeId(Long.valueOf(request.scope().catalogScopeId())).sourceId(sourceId)
                    .producerId(request.producerId()).sourceVersion(request.sourceVersion()).metadataKind("PERMISSION").definitionKey(permission.code())
                    .permissionId(Long.valueOf(request.permissionIds().get(permission.code()))).definitionLifecycle(permission.lifecycle().name())
                    .pageCode(permission.i18n().pageCode()).labelKey(permission.i18n().labelKey()).descriptionKey(permission.i18n().descriptionKey())
                    .targetJson("{}").policyJson(CanonicalJson.write(Map.of("code", permission.code(), "meaning", permission.meaning(), "lifecycle", permission.lifecycle().name())))
                    .i18nRefsJson(CanonicalJson.write(canonicalizer.i18nMap(permission.i18n()))).referencedPermissionCodesJson(CanonicalJson.write(List.of(permission.code()))).build());
        }
        return result;
    }

    private List<DdcI18nSourceMessageBO> messages(SourceImportRequest request, Long sourceId) {
        return request.manifest().messages().stream().map(message -> DdcI18nSourceMessageBO.builder()
                .catalogScopeId(Long.valueOf(request.scope().catalogScopeId())).sourceId(sourceId).producerId(request.producerId())
                .sourceVersion(request.sourceVersion()).pageCode(message.pageCode()).messageKey(message.key()).defaultLocale(message.defaultLocale())
                .sourceHash(I18nSourceRules.sourceHash(message)).defaultsJson(CanonicalJson.write(message.defaults()))
                .placeholdersJson(CanonicalJson.write(I18nSourceRules.placeholders(message.defaults().get(message.defaultLocale())))).build()).toList();
    }

    private void collect(TreeSet<String> target, PermissionRequirement requirement) {
        if (requirement != null) requirement.groups().forEach(group -> target.addAll(group.codes()));
    }

    private void validatePages(PermissionManifest manifest) {
        Map<String, String> names = new HashMap<>();
        Map<String, String> locales = new HashMap<>();
        for (var message : manifest.messages()) {
            String oldName = names.putIfAbsent(message.pageCode().toLowerCase(Locale.ROOT), message.pageCode());
            String oldLocale = locales.putIfAbsent(message.pageCode(), message.defaultLocale());
            if (oldName != null && !oldName.equals(message.pageCode()) || oldLocale != null && !oldLocale.equals(message.defaultLocale())) {
                throw new IllegalArgumentException("A source page must have one exact identity and default locale");
            }
        }
    }

    private void requireTenant(DdcSourceImportIdentityBO identity, CatalogScope scope) {
        Objects.requireNonNull(identity, "verified service identity");
        Objects.requireNonNull(scope, "catalog scope");
        if (!identity.tenantId().toString().equals(scope.tenantId())) throw forbidden();
    }

    private DdcTenantContextDTO context(DdcSourceImportIdentityBO identity) {
        return new DdcTenantContextDTO(identity.tenantId(), identity.principal().tenantId(), "service:" + identity.principal().clientId());
    }

    private static AccessDeniedException forbidden() { return new AccessDeniedException("TIANSHU_METADATA_SOURCE_IMPORT_FORBIDDEN"); }
    private static CommonException conflict() { return new CommonException(56421, "TIANSHU_METADATA_IMPORT_CONFLICT", "Source import identity or content changed"); }
}
