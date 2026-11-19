package top.egon.cola.component.tianshu.admin.service.si.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiResourceConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.outbox.DdcSiCatalogOutboxNotifier;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.admin.search.DdcSiSemanticIndexService;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiDependencyValidator;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiLifecycleTransitionPolicy;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiMcpPublicationVerifier.PublicationScopeVO;
import top.egon.cola.component.tianshu.admin.service.si.DdcSiResourceService;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy.NormalizedContent;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiPublicationEvent.EventType;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/**
 * SI 统一治理服务核心编排实现：执行短事务落库、乐观锁保护、Outbox 投递与异构资源生命周期流转。
 */
@Slf4j
@Service("ddcSiResourceService")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiResourceServiceImpl implements DdcSiResourceService {

    private static final int CODE_NOT_FOUND = 56404;
    private static final int CODE_CONFLICT = 56409;

    @Qualifier("ddcSiResourceRepository")
    private final DdcSiResourceRepository repository;

    private final List<DdcSiContentStrategy> strategies;

    @Qualifier("ddcSiDependencyValidator")
    private final DdcSiDependencyValidator dependencyValidator;

    @Qualifier("ddcSiLifecycleTransitionPolicy")
    private final DdcSiLifecycleTransitionPolicy transitionPolicy;

    @Qualifier("ddcSiCatalogOutboxNotifier")
    private final DdcSiCatalogOutboxNotifier outboxNotifier;

    @Qualifier("ddcSiSemanticIndexService")
    private final DdcSiSemanticIndexService semanticIndexService;

    @Qualifier("ddcSiResourceConverterImpl")
    private final DdcSiResourceConverter converter;

    @Qualifier("ddcPublishTransactionTemplate")
    private final TransactionTemplate transactionTemplate;

    @Qualifier("ddcPublishClock")
    private final Clock clock;

    @Qualifier("ddcSiMcpPublicationVerifier")
    private final DdcSiMcpPublicationVerifier mcpPublicationVerifier;

    @Override
    public DdcPageResult<DdcSiResourceBO> page(Long tenantId, String bizCode, String namespaceCode, String envCode,
                                               String appCode, DdcSiKindEnum kind,
                                               DdcSiLifecycleStatusEnum lifecycleStatus,
                                               String keyword, String tag, PageQuery pageQuery) {
        Long namespaceId = namespaceCode == null || namespaceCode.isBlank()
                ? null
                : repository.resolveNamespaceId(tenantId, bizCode, namespaceCode);
        return repository.searchPage(tenantId, bizCode, namespaceId, envCode, appCode,
                kind, lifecycleStatus, keyword, tag, pageQuery);
    }

    @Override
    public DdcSiResourceBO get(Long tenantId, Long resourceId) {
        return repository.findProjectedById(tenantId, resourceId)
                .orElseThrow(() -> notFound(resourceId));
    }

    @Override
    public DdcSiResourceBO create(Long tenantId, String bizCode, String namespaceCode, String envCode, String appCode,
                                  DdcSiKindEnum kind, String resourceKey, String displayName, String description,
                                  List<String> tags, DdcSiSourceTypeEnum sourceType, String specJson, String actor) {
        DdcSiContentStrategy strategy = selectStrategy(kind);
        NormalizedContent normalized = strategy.validateAndNormalize(kind, specJson);
        String searchable = strategy.extractSearchableText(kind, normalized.getNormalizedJson(), displayName, description);

        return transactionTemplate.execute(status -> {
            Long namespaceId = repository.resolveNamespaceId(tenantId, bizCode, namespaceCode);
            Instant now = clock.instant();
            DdcSiResourceBO inserted = repository.insert(DdcSiResourceBO.builder()
                    .tenantId(tenantId)
                    .createUserId(actor)
                    .updateUserId(actor)
                    .createTime(now)
                    .updateTime(now)
                    .version(0L)
                    .bizCode(bizCode)
                    .namespaceId(namespaceId)
                    .namespaceCode(namespaceCode)
                    .envCode(envCode)
                    .appCode(appCode)
                    .kind(kind)
                    .resourceKey(resourceKey)
                    .displayName(displayName)
                    .description(description == null ? "" : description)
                    .tags(tags == null ? Collections.emptyList() : List.copyOf(tags))
                    .lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT)
                    .sourceType(sourceType == null ? DdcSiSourceTypeEnum.CONSOLE : sourceType)
                    .specJson(normalized.getNormalizedJson())
                    .contentSha256(normalized.getDigestSha256())
                    .lastVersionNo(0L)
                    .build());
            inserted.setNamespaceCode(namespaceCode);
            repository.updateSearchTsv(tenantId, inserted.getId(), searchable);
            return inserted;
        });
    }

    @Override
    public DdcSiResourceBO updateDraft(Long tenantId, Long resourceId, Long expectedVersion, String displayName,
                                       String description, List<String> tags, String specJson, String actor) {
        DdcSiResourceBO existing = repository.findProjectedById(tenantId, resourceId)
                .orElseThrow(() -> notFound(resourceId));
        transitionPolicy.assertCanEditDraft(existing.getLifecycleStatus());
        DdcSiContentStrategy strategy = selectStrategy(existing.getKind());
        NormalizedContent normalized = strategy.validateAndNormalize(existing.getKind(), specJson);
        String searchable = strategy.extractSearchableText(
                existing.getKind(), normalized.getNormalizedJson(), displayName, description);

        return transactionTemplate.execute(status -> {
            DdcSiResourceBO locked = repository.findActiveForUpdateById(tenantId, resourceId)
                    .orElseThrow(() -> notFound(resourceId));
            transitionPolicy.assertCanEditDraft(locked.getLifecycleStatus());
            assertVersionMatch(locked.getVersion(), expectedVersion);

            locked.setDisplayName(displayName)
                    .setDescription(description == null ? "" : description)
                    .setTags(tags == null ? Collections.emptyList() : List.copyOf(tags))
                    .setSpecJson(normalized.getNormalizedJson())
                    .setContentSha256(normalized.getDigestSha256())
                    .setUpdateUserId(actor)
                    .setUpdateTime(clock.instant());
            DdcSiResourceBO updated = repository.updateVersioned(locked)
                    .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent draft update conflict"));
            repository.updateSearchTsv(tenantId, resourceId, searchable);
            return updated;
        });
    }

    @Override
    public DdcSiVersionBO publish(Long tenantId, Long resourceId, Long expectedVersion, String changeNote, String actor) {
        return transactionTemplate.execute(status -> {
            DdcSiResourceBO resource = repository.findProjectedById(tenantId, resourceId)
                    .orElseThrow(() -> notFound(resourceId));
            transitionPolicy.assertCanTransition(resource.getLifecycleStatus(), DdcSiLifecycleStatusEnum.PUBLISHED);
            assertVersionMatch(resource.getVersion(), expectedVersion);

            DdcSiContentStrategy strategy = selectStrategy(resource.getKind());
            NormalizedContent normalized = strategy.validateAndNormalize(resource.getKind(), resource.getSpecJson());
            requireMcpApproval(resource, normalized.getNormalizedJson(), normalized.getDigestSha256());
            var outgoing = strategy.extractReferences(resource.getKind(), normalized.getNormalizedJson());
            Instant now = clock.instant();
            var candidate = publication(resource, normalized.getNormalizedJson(), normalized.getDigestSha256(),
                    changeNote == null ? "" : changeNote, actor, now);
            dependencyValidator.freezePublication(resource, candidate, null, outgoing, List.of());
            return commitPublication(resource, candidate, outgoing, EventType.PUBLISHED, actor, now);
        });
    }

    @Override
    public DdcSiVersionBO rollback(Long tenantId, Long resourceId, Long expectedVersion, Long targetVersionNo,
                                   String changeNote, String actor) {
        return transactionTemplate.execute(status -> {
            DdcSiResourceBO locked = repository.findActiveForUpdateById(tenantId, resourceId)
                    .orElseThrow(() -> notFound(resourceId));
            transitionPolicy.assertCanTransition(locked.getLifecycleStatus(), DdcSiLifecycleStatusEnum.PUBLISHED);
            assertVersionMatch(locked.getVersion(), expectedVersion);

            var prior = exactVersion(locked, targetVersionNo);
            var manifest = dependencyValidator.requireHistorical(prior);
            requireMcpApproval(locked, prior.getSpecJson(), prior.getDigestSha256());
            Instant now = clock.instant();
            var candidate = publication(locked, prior.getSpecJson(), prior.getDigestSha256(),
                    changeNote == null ? "rollback to version " + targetVersionNo : changeNote, actor, now)
                    .setSourceEnvCode(prior.getEnvCode()).setSourceVersionNo(prior.getVersionNo());
            dependencyValidator.freezeRollback(candidate, manifest);
            // The editable reference projection is recreated from the historical body only.
            var outgoing = selectStrategy(locked.getKind()).extractReferences(locked.getKind(), prior.getSpecJson());
            return commitPublication(locked, candidate, outgoing, EventType.ROLLED_BACK, actor, now);
        });
    }

    @Override
    public DdcSiVersionBO promote(Long tenantId, Long sourceResourceId, String targetEnvCode, String changeNote, String actor) {
        return transactionTemplate.execute(status -> {
            DdcSiResourceBO source = repository.findProjectedById(tenantId, sourceResourceId)
                    .orElseThrow(() -> notFound(sourceResourceId));
            transitionPolicy.assertCanPromote(source.getEnvCode(), targetEnvCode);
            if (!source.getLifecycleStatus().isQueryableByRuntime()
                    || source.getCurrentVersionNo() == null || source.getCurrentVersionId() == null) {
                throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_NOT_PUBLISHED",
                        "source SI resource has no active published snapshot to promote");
            }
            var sourceVer = exactVersion(source, source.getCurrentVersionNo());
            if (!source.getCurrentVersionId().equals(sourceVer.getId())) {
                throw DdcSiDependencyValidator.unavailable();
            }
            var sourceManifest = dependencyValidator.requireHistorical(sourceVer);
            DdcSiContentStrategy strategy = selectStrategy(source.getKind());
            var outgoing = strategy.extractReferences(source.getKind(), sourceVer.getSpecJson());

            Instant now = clock.instant();
            var existingTarget = repository.findByBusinessKey(tenantId, source.getBizCode(), source.getNamespaceId(),
                    targetEnvCode, source.getAppCode(), source.getKind(), source.getResourceKey());
            if (source.getKind() == DdcSiKindEnum.MCP && existingTarget.isEmpty()) {
                throw mcpRegistrationRequired();
            }
            DdcSiResourceBO target = existingTarget.orElseGet(() -> repository.insert(DdcSiResourceBO.builder()
                            .tenantId(tenantId)
                            .createUserId(actor)
                            .updateUserId(actor)
                            .createTime(now)
                            .updateTime(now)
                            .version(0L)
                            .bizCode(source.getBizCode())
                            .namespaceId(source.getNamespaceId())
                            .namespaceCode(source.getNamespaceCode())
                            .envCode(targetEnvCode)
                            .appCode(source.getAppCode())
                            .kind(source.getKind())
                            .resourceKey(source.getResourceKey())
                            .displayName(source.getDisplayName())
                            .description(source.getDescription())
                            .tags(source.getTags())
                            .lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT)
                            .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                            .specJson(sourceVer.getSpecJson())
                            .contentSha256(sourceVer.getDigestSha256())
                            .lastVersionNo(0L)
                            .build()));

            transitionPolicy.assertCanTransition(target.getLifecycleStatus(), DdcSiLifecycleStatusEnum.PUBLISHED);
            target.setNamespaceCode(source.getNamespaceCode());
            requireMcpApproval(target, sourceVer.getSpecJson(), sourceVer.getDigestSha256());
            var candidate = publication(target, sourceVer.getSpecJson(), sourceVer.getDigestSha256(),
                    changeNote == null ? "promote from " + source.getEnvCode() : changeNote, actor, now)
                    .setSourceEnvCode(source.getEnvCode()).setSourceVersionNo(sourceVer.getVersionNo());
            dependencyValidator.freezePublication(target, candidate, sourceManifest.root(), outgoing, List.of(source));
            target.setDisplayName(source.getDisplayName())
                    .setDescription(source.getDescription())
                    .setTags(source.getTags());
            return commitPublication(target, candidate, outgoing, EventType.PROMOTED, actor, now);
        });
    }

    @Override
    public DdcSiResourceBO deprecate(Long tenantId, Long resourceId, Long expectedVersion, String actor) {
        return transitionLifecycle(tenantId, resourceId, expectedVersion, DdcSiLifecycleStatusEnum.DEPRECATED, EventType.DEPRECATED, actor);
    }

    @Override
    public void archive(Long tenantId, Long resourceId, Long expectedVersion, String actor) {
        transactionTemplate.executeWithoutResult(status -> {
            DdcSiResourceBO locked = repository.findActiveForUpdateById(tenantId, resourceId)
                    .orElseThrow(() -> notFound(resourceId));
            transitionPolicy.assertCanTransition(locked.getLifecycleStatus(), DdcSiLifecycleStatusEnum.ARCHIVED);
            assertVersionMatch(locked.getVersion(), expectedVersion);
            dependencyValidator.ensureSafeToArchive(locked);

            Instant now = clock.instant();
            locked.setLifecycleStatus(DdcSiLifecycleStatusEnum.ARCHIVED)
                    .setUpdateUserId(actor)
                    .setUpdateTime(now);
            DdcSiResourceBO archived = repository.updateVersioned(locked)
                    .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent archive conflict"));
            outboxNotifier.enqueueAndScheduleAfterCommit(EventType.ARCHIVED, archived, null);
        });
    }

    @Override
    public DdcPageResult<DdcSiVersionBO> pageVersions(Long tenantId, Long resourceId, PageQuery pageQuery) {
        repository.findActiveById(tenantId, resourceId).orElseThrow(() -> notFound(resourceId));
        return repository.pageVersions(tenantId, resourceId, pageQuery);
    }

    @Override
    public List<DdcSiVersionBO> getEnvMatrix(Long tenantId, String bizCode, String namespaceCode,
                                             DdcSiKindEnum kind, String resourceKey) {
        Long namespaceId = repository.resolveNamespaceId(tenantId, bizCode, namespaceCode);
        return repository.findEnvMatrix(tenantId, bizCode, namespaceId, kind, resourceKey);
    }

    @Override
    public DdcSiBindingBO bind(Long tenantId, String bizCode, String namespaceCode, String envCode, String appCode,
                               DdcSiKindEnum kind, String resourceKey, Long pinnedVersionNo, String actor) {
        return transactionTemplate.execute(status -> {
            Long namespaceId = repository.resolveNamespaceId(tenantId, bizCode, namespaceCode);
            DdcSiResourceBO resource = findInScopeByKey(tenantId, bizCode, namespaceId, envCode, appCode, kind, resourceKey);
            if (resource.getLifecycleStatus() == DdcSiLifecycleStatusEnum.ARCHIVED) {
                throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_ARCHIVED_IMMUTABLE", "cannot bind archived SI resource");
            }
            if (pinnedVersionNo != null) {
                repository.findVersionByResourceAndNo(tenantId, resource.getId(), pinnedVersionNo)
                        .orElseThrow(() -> new CommonException(CODE_NOT_FOUND, "TIANSHU_SI_VERSION_NOT_FOUND",
                                "pinned version not found: " + pinnedVersionNo));
            }
            DdcSiBindingBO saved = repository.upsertBinding(DdcSiBindingBO.builder()
                    .tenantId(tenantId)
                    .bizCode(bizCode)
                    .namespaceId(namespaceId)
                    .envCode(envCode)
                    .appCode(appCode)
                    .kind(kind)
                    .resourceId(resource.getId())
                    .resourceKey(resourceKey)
                    .pinnedVersionNo(pinnedVersionNo)
                    .status(DdcSiBindingStatusEnum.ACTIVE)
                    .build(), actor, clock.instant());
            outboxNotifier.enqueueAndScheduleAfterCommit(EventType.BOUND, resource, null);
            return saved;
        });
    }

    @Override
    public void unbind(Long tenantId, String bizCode, String namespaceCode, String envCode, String appCode,
                       DdcSiKindEnum kind, String resourceKey, String actor) {
        transactionTemplate.executeWithoutResult(status -> {
            Long namespaceId = repository.resolveNamespaceId(tenantId, bizCode, namespaceCode);
            repository.deactivateBinding(tenantId, bizCode, namespaceId, envCode, appCode,
                    kind, resourceKey, actor, clock.instant());
            repository.findByBusinessKey(tenantId, bizCode, namespaceId, envCode, appCode, kind, resourceKey)
                    .ifPresent(res -> outboxNotifier.enqueueAndScheduleAfterCommit(EventType.UNBOUND, res, null));
        });
    }

    @Override
    public List<DdcSiSearchHit> hybridSearch(DdcSiSemanticSearchQuery query) {
        return semanticIndexService.hybridSearch(query);
    }

    @Override
    public DdcSiVersionBO getVersion(Long tenantId, Long resourceId, Long versionNo) {
        get(tenantId, resourceId);
        return repository.findHistoryVersionByResourceAndNo(tenantId, resourceId, versionNo)
                .orElseThrow(() -> new CommonException(CODE_NOT_FOUND, "TIANSHU_SI_VERSION_NOT_FOUND",
                        "SI version not found: " + resourceId + "@" + versionNo));
    }

    @Override
    public List<DdcSiReferenceBO> listReferences(Long tenantId, Long resourceId, String direction) {
        DdcSiResourceBO resource = get(tenantId, resourceId);
        if ("INBOUND".equalsIgnoreCase(direction)) {
            return repository.findBlockingInboundReferences(
                    tenantId, resource.getEnvCode(), resource.getKind(), resource.getResourceKey());
        }
        DdcSiReferenceTargetTypeEnum sourceType = switch (resource.getKind()) {
            case AGENT -> DdcSiReferenceTargetTypeEnum.AGENT;
            case SKILL -> DdcSiReferenceTargetTypeEnum.SKILL;
            case PROMPT -> DdcSiReferenceTargetTypeEnum.PROMPT;
            case MCP -> DdcSiReferenceTargetTypeEnum.MCP;
        };
        return repository.findReferencesBySource(
                tenantId, resource.getEnvCode(), sourceType, resource.getResourceKey());
    }

    @Override
    public void triggerReindex(Long tenantId, Long resourceId, Long versionId) {
        DdcSiResourceBO resource = get(tenantId, resourceId);
        Long targetVerId = versionId != null ? versionId : resource.getCurrentVersionId();
        if (targetVerId != null) {
            semanticIndexService.scheduleReindex(tenantId, resourceId, targetVerId);
        }
    }

    @Override
    public List<DdcSiResourceDescriptor> listCatalog(DdcSiCatalogQuery query) {
        Long namespaceId = query.getNamespaceCode() == null || query.getNamespaceCode().isBlank()
                ? null
                : repository.resolveNamespaceId(query.getTenantId(), query.getBizCode(), query.getNamespaceCode());
        int pageNo = Math.max(1, query.getPageNo());
        int pageSize = Math.min(Math.max(1, query.getPageSize()), 200);
        DdcPageResult<DdcSiResourceBO> page = repository.searchCatalog(
                query.getTenantId(), query.getBizCode(), namespaceId, query.getEnvCode(), query.getAppCode(),
                query.getKind(), query.isIncludeDeprecated(), query.getKeyword(), null,
                new PageQuery(pageNo, pageSize));
        return page.getRecords().stream()
                .map(converter::toCatalogDescriptor)
                .toList();
    }

    @Override
    public Optional<DdcSiResourceDescriptor> resolveResource(Long tenantId, String bizCode, String namespaceCode,
                                                             String envCode, String ownerAppCode, String callerAppCode,
                                                             DdcSiKindEnum kind, String resourceKey,
                                                             Integer requestedVersionNo) {
        String owner = ownerAppCode == null || ownerAppCode.isBlank() ? callerAppCode : ownerAppCode;
        if (owner != null && owner.isBlank()) {
            owner = null;
        }
        return resolveSelected(tenantId, bizCode, namespaceCode, envCode, owner, callerAppCode,
                kind, resourceKey, requestedVersionNo == null ? null : requestedVersionNo.longValue(), false);
    }

    @Override
    public Optional<DdcSiResourceDescriptor> resolveExecutionResource(Long tenantId, String bizCode, String namespaceCode,
                                                                      String envCode, String ownerAppCode, String callerAppCode,
                                                                      DdcSiKindEnum kind, String resourceKey, Long requestedVersionNo) {
        return resolveSelected(tenantId, bizCode, namespaceCode, envCode, ownerAppCode, callerAppCode,
                kind, resourceKey, requestedVersionNo, true);
    }

    private Optional<DdcSiResourceDescriptor> resolveSelected(Long tenantId, String bizCode, String namespaceCode,
                                                              String envCode, String ownerAppCode, String callerAppCode,
                                                              DdcSiKindEnum kind, String resourceKey, Long requestedVersionNo,
                                                              boolean execution) {
        Long namespaceId = repository.resolveNamespaceId(tenantId, bizCode, namespaceCode);
        String storedOwner = ownerAppCode == null ? "" : ownerAppCode;
        var found = repository.findByBusinessKey(tenantId, bizCode, namespaceId, envCode, storedOwner, kind, resourceKey);
        if (found.isEmpty() || !found.get().getLifecycleStatus().isQueryableByRuntime()) {
            return Optional.empty();
        }
        var resource = found.get().setNamespaceCode(namespaceCode);
        var binding = callerAppCode == null || callerAppCode.isBlank() ? Optional.<DdcSiBindingBO>empty()
                : repository.findActiveBinding(tenantId, bizCode, namespaceId, envCode, callerAppCode, kind, resourceKey)
                        .filter(candidate -> DdcSiBindingStatusEnum.ACTIVE == candidate.getStatus()
                                && resource.getId().equals(candidate.getResourceId()));
        if (execution && (callerAppCode == null || callerAppCode.isBlank()
                || ownerAppCode != null && !ownerAppCode.equals(callerAppCode) && binding.isEmpty())) {
            throw new CommonException(56403, "TIANSHU_SI_SCOPE_DENIED",
                    "SERVICE caller is not authorized for this SI resource");
        }
        Long selected = requestedVersionNo != null ? requestedVersionNo
                : binding.map(DdcSiBindingBO::getPinnedVersionNo).orElse(null);
        var version = selected == null ? exactCurrentVersion(resource) : exactVersion(resource, selected);
        var manifest = dependencyValidator.requireHistorical(version);
        return Optional.of(execution ? converter.toExecutionDescriptor(resource, version, manifest)
                : converter.toCatalogDescriptor(resource, version));
    }

    @Override
    public DdcSiResourceDescriptor registerManaged(DdcSiSelfRegistrationRequest request, boolean allowOverwriteConsole) {
        DdcSiContentStrategy strategy = selectStrategy(request.getKind());
        NormalizedContent normalized = strategy.validateAndNormalize(request.getKind(), request.getSpecJson());
        String searchable = strategy.extractSearchableText(
                request.getKind(), normalized.getNormalizedJson(), request.getDisplayName(), request.getDescription());

        return transactionTemplate.execute(status -> {
            Long namespaceId = repository.resolveNamespaceId(
                    request.getTenantId(), request.getBizCode(), request.getNamespaceCode());
            Optional<DdcSiResourceBO> existingOpt = repository.findByBusinessKey(
                    request.getTenantId(), request.getBizCode(), namespaceId,
                    request.getEnvCode(), request.getServiceName(), request.getKind(), request.getResourceKey());
            if (request.getKind() == DdcSiKindEnum.MCP && request.isAutoPublishIfNew() && existingOpt.isEmpty()) {
                throw mcpRegistrationRequired();
            }

            if (existingOpt.isPresent()) {
                DdcSiResourceBO existing = existingOpt.get().setNamespaceCode(request.getNamespaceCode());
                if (normalized.getDigestSha256().equals(existing.getContentSha256())) {
                    return converter.toDescriptor(existing, null, List.of());
                }
                if (existing.getSourceType() == DdcSiSourceTypeEnum.CONSOLE
                        && existing.getLifecycleStatus() == DdcSiLifecycleStatusEnum.PUBLISHED
                        && !allowOverwriteConsole) {
                    log.info("Preserving console-published SI resource against auto-registration overwrite resourceKey={}",
                            existing.getResourceKey());
                    return converter.toDescriptor(existing, null, List.of());
                }
            }

            Instant now = clock.instant();
            DdcSiResourceBO resource;
            if (existingOpt.isEmpty()) {
                resource = repository.insert(DdcSiResourceBO.builder()
                        .tenantId(request.getTenantId())
                        .createUserId("system:auto-register")
                        .updateUserId("system:auto-register")
                        .createTime(now)
                        .updateTime(now)
                        .version(0L)
                        .bizCode(request.getBizCode())
                        .namespaceId(namespaceId)
                        .namespaceCode(request.getNamespaceCode())
                        .envCode(request.getEnvCode())
                        .appCode(request.getServiceName())
                        .kind(request.getKind())
                        .resourceKey(request.getResourceKey())
                        .displayName(request.getDisplayName())
                        .description(request.getDescription() == null ? "" : request.getDescription())
                        .tags(request.getLabels() == null ? List.of() : List.copyOf(request.getLabels().keySet()))
                        .lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT)
                        .sourceType(DdcSiSourceTypeEnum.AUTO_REPORTED)
                        .specJson(normalized.getNormalizedJson())
                        .contentSha256(normalized.getDigestSha256())
                        .lastVersionNo(0L)
                        .build());
            } else {
                resource = existingOpt.orElseThrow();
            }
            resource.setNamespaceCode(request.getNamespaceCode());

            final String actor = "system:auto-register";
            if (request.isAutoPublishIfNew()) {
                transitionPolicy.assertCanTransition(resource.getLifecycleStatus(), DdcSiLifecycleStatusEnum.PUBLISHED);
                requireMcpApproval(resource, normalized.getNormalizedJson(), normalized.getDigestSha256());
                var outgoing = strategy.extractReferences(resource.getKind(), normalized.getNormalizedJson());
                var candidate = publication(resource, normalized.getNormalizedJson(), normalized.getDigestSha256(),
                        "auto-registered", actor, now);
                dependencyValidator.freezePublication(resource, candidate, null, outgoing, List.of());
                resource.setDisplayName(request.getDisplayName())
                        .setDescription(request.getDescription() == null ? "" : request.getDescription());
                var version = commitPublication(resource, candidate, outgoing, EventType.PUBLISHED, actor, now);
                return converter.toDescriptor(resource, version, List.of());
            }
            if (existingOpt.isPresent()) {
                dependencyValidator.lockCurrentPointers(resource.getTenantId(), List.of(resource));
                resource.setDisplayName(request.getDisplayName())
                        .setDescription(request.getDescription() == null ? "" : request.getDescription())
                        .setSpecJson(normalized.getNormalizedJson())
                        .setContentSha256(normalized.getDigestSha256())
                        .setUpdateUserId(actor)
                        .setUpdateTime(now);
                resource = repository.updateVersioned(resource)
                        .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT",
                                "concurrent auto-registration conflict"));
            }
            repository.updateSearchTsv(request.getTenantId(), resource.getId(), searchable);
            return converter.toDescriptor(resource, null, List.of());
        });
    }

    @Override
    public void syncProcessReferences(Long tenantId, String envCode, String processKey,
                                      List<DdcSiReferenceDescriptor> references, String actor) {
        transactionTemplate.executeWithoutResult(status -> {
            List<DdcSiReferenceBO> refBos = references == null ? List.of() : references.stream()
                    .map(desc -> DdcSiReferenceBO.builder()
                            .targetType(desc.getTargetType())
                            .targetKey(desc.getTargetKey())
                            .pinnedVersionNo(desc.getPinnedVersionNo() == null ? null : desc.getPinnedVersionNo().longValue())
                            .required(desc.isRequired())
                            .roleHint(desc.getRoleHint())
                            .build())
                    .toList();
            repository.replaceProcessReferences(tenantId, envCode, processKey, refBos, actor, clock.instant());
        });
    }

    @Override
    public void deleteProcessReferences(Long tenantId, String envCode, String processKey, String actor) {
        transactionTemplate.executeWithoutResult(status ->
                repository.deleteProcessReferences(tenantId, envCode, processKey, actor, clock.instant()));
    }

    private DdcSiVersionBO exactVersion(DdcSiResourceBO resource, Long versionNo) {
        if (versionNo == null || versionNo <= 0) {
            throw DdcSiDependencyValidator.unavailable();
        }
        var version = repository.findHistoryVersionByResourceAndNo(resource.getTenantId(), resource.getId(), versionNo)
                .orElseThrow(DdcSiDependencyValidator::unavailable);
        assertVersionOwner(resource, version);
        if (!versionNo.equals(version.getVersionNo())) {
            throw DdcSiDependencyValidator.unavailable();
        }
        return version;
    }

    private DdcSiVersionBO exactCurrentVersion(DdcSiResourceBO resource) {
        Long currentId = resource.getCurrentVersionId();
        if (currentId == null) {
            throw DdcSiDependencyValidator.unavailable();
        }
        var versions = repository.findHistoryVersionsByIds(resource.getTenantId(), List.of(currentId));
        if (versions.size() != 1 || !currentId.equals(versions.getFirst().getId())) {
            throw DdcSiDependencyValidator.unavailable();
        }
        var version = versions.getFirst();
        assertVersionOwner(resource, version);
        return version;
    }

    private static void assertVersionOwner(DdcSiResourceBO resource, DdcSiVersionBO version) {
        if (!Objects.equals(resource.getTenantId(), version.getTenantId())
                || !Objects.equals(resource.getId(), version.getResourceId())
                || resource.getKind() != version.getKind()
                || !Objects.equals(resource.getEnvCode(), version.getEnvCode())) {
            throw DdcSiDependencyValidator.unavailable();
        }
    }

    private void requireMcpApproval(DdcSiResourceBO resource, String body, String digest) {
        if (resource.getKind() != DdcSiKindEnum.MCP) {
            return;
        }
        if (resource.getId() == null || resource.getId() <= 0) {
            throw mcpRegistrationRequired();
        }
        try {
            var scope = new PublicationScopeVO(Objects.toString(resource.getTenantId(), ""), resource.getBizCode(),
                    resource.getNamespaceCode(), resource.getEnvCode(), Objects.toString(resource.getAppCode(), ""),
                    resource.getResourceKey(), resource.getId().toString());
            var approval = mcpPublicationVerifier.requireApproved(scope, body, digest);
            if (approval == null || !Objects.equals(digest, approval.canonicalSpecSha256())
                    || approval.validUntil() == null || !clock.instant().isBefore(approval.validUntil())) {
                throw mcpApprovalRequired();
            }
            log.debug("Validated immutable MCP publication scope resourceId={} approvalId={}", resource.getId(), approval.approvalId());
        } catch (IllegalArgumentException | IllegalStateException denied) {
            throw mcpApprovalRequired();
        }
    }

    private static CommonException mcpRegistrationRequired() {
        return new CommonException(CODE_CONFLICT, "TIANSHU_SI_MCP_REGISTRATION_REQUIRED",
                "Register the target MCP resource before requesting publication");
    }

    private static CommonException mcpApprovalRequired() {
        return new CommonException(DdcSiContentStrategy.CODE_VALIDATION_FAILED, "TIANSHU_SI_MCP_PUBLICATION_UNAPPROVED",
                "Publication requires an approved MCP schema and immutable evidence");
    }

    private static DdcSiVersionBO publication(DdcSiResourceBO resource, String body, String digest,
                                              String note, String actor, Instant now) {
        return DdcSiVersionBO.builder().tenantId(resource.getTenantId()).resourceId(resource.getId())
                .kind(resource.getKind()).envCode(resource.getEnvCode()).specJson(body).digestSha256(digest)
                .version(0L).changeNote(note).indexStatus("PENDING").publishedTime(now)
                .createUserId(actor).updateUserId(actor).createTime(now).updateTime(now).build();
    }

    private DdcSiVersionBO commitPublication(DdcSiResourceBO resource, DdcSiVersionBO candidate,
                                            List<DdcSiReferenceBO> references, EventType event, String actor, Instant now) {
        var version = repository.insertVersion(candidate);
        repository.replaceReferences(resource.getTenantId(), resource.getEnvCode(), toSourceType(resource.getKind()),
                resource.getResourceKey(), resource.getId(), version.getVersionNo(), resource.getNamespaceId(),
                references, actor, now);
        resource.setLifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                .setLastVersionNo(version.getVersionNo()).setCurrentVersionNo(version.getVersionNo())
                .setCurrentVersionId(version.getId()).setPublishedTime(now)
                .setSpecJson(version.getSpecJson()).setContentSha256(version.getDigestSha256())
                .setUpdateUserId(actor).setUpdateTime(now);
        var published = repository.updateVersioned(resource)
                .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent publication conflict"));
        repository.updateSearchTsv(resource.getTenantId(), resource.getId(), selectStrategy(resource.getKind())
                .extractSearchableText(resource.getKind(), version.getSpecJson(), resource.getDisplayName(), resource.getDescription()));
        outboxNotifier.enqueueAndScheduleAfterCommit(event, published, version);
        return version;
    }

    private DdcSiResourceBO transitionLifecycle(Long tenantId, Long resourceId, Long expectedVersion,
                                                DdcSiLifecycleStatusEnum target, EventType eventType, String actor) {
        return transactionTemplate.execute(status -> {
            DdcSiResourceBO locked = repository.findActiveForUpdateById(tenantId, resourceId)
                    .orElseThrow(() -> notFound(resourceId));
            transitionPolicy.assertCanTransition(locked.getLifecycleStatus(), target);
            assertVersionMatch(locked.getVersion(), expectedVersion);

            locked.setLifecycleStatus(target)
                    .setUpdateUserId(actor)
                    .setUpdateTime(clock.instant());
            DdcSiResourceBO updated = repository.updateVersioned(locked)
                    .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent lifecycle transition conflict"));
            outboxNotifier.enqueueAndScheduleAfterCommit(eventType, updated, null);
            return updated;
        });
    }

    private DdcSiResourceBO findInScopeByKey(Long tenantId, String bizCode, Long namespaceId, String envCode,
                                             String appCode, DdcSiKindEnum kind, String resourceKey) {
        if (appCode != null && !appCode.isBlank()) {
            Optional<DdcSiResourceBO> exact = repository.findByBusinessKey(
                    tenantId, bizCode, namespaceId, envCode, appCode, kind, resourceKey);
            if (exact.isPresent()) {
                return exact.get();
            }
        }
        return repository.searchPage(tenantId, bizCode, namespaceId, envCode, null, kind, null,
                        resourceKey, null, new PageQuery(1, 10)).getRecords().stream()
                .filter(r -> resourceKey.equals(r.getResourceKey()))
                .findFirst()
                .orElseThrow(() -> new CommonException(CODE_NOT_FOUND, "TIANSHU_SI_NOT_FOUND",
                        "SI resource not found in scope: " + kind + "/" + resourceKey));
    }

    private DdcSiContentStrategy selectStrategy(DdcSiKindEnum kind) {
        return strategies.stream()
                .filter(s -> s.supports(kind))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported SI kind=" + kind));
    }

    private static DdcSiReferenceTargetTypeEnum toSourceType(DdcSiKindEnum kind) {
        return switch (kind) {
            case AGENT -> DdcSiReferenceTargetTypeEnum.AGENT;
            case MCP -> DdcSiReferenceTargetTypeEnum.MCP;
            case PROMPT -> DdcSiReferenceTargetTypeEnum.PROMPT;
            case SKILL -> DdcSiReferenceTargetTypeEnum.SKILL;
        };
    }

    private static void assertVersionMatch(Long actualVersion, Long expectedVersion) {
        if (expectedVersion != null && actualVersion != null && !actualVersion.equals(expectedVersion)) {
            throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT",
                    "optimistic version mismatch: expected=" + expectedVersion + ", actual=" + actualVersion);
        }
    }

    private static CommonException notFound(Long resourceId) {
        return new CommonException(CODE_NOT_FOUND, "TIANSHU_SI_NOT_FOUND",
                "SI resource not found: " + resourceId);
    }
}
