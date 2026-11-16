package top.egon.cola.component.tianshu.admin.service.si.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
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

/**
 * SI 统一治理服务核心编排实现：执行短事务落库、乐观锁保护、Outbox 投递与异构资源生命周期流转。
 */
@Slf4j
@Service("ddcSiResourceService")
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
            DdcSiResourceBO locked = repository.findActiveForUpdateById(tenantId, resourceId)
                    .orElseThrow(() -> notFound(resourceId));
            transitionPolicy.assertCanTransition(locked.getLifecycleStatus(), DdcSiLifecycleStatusEnum.PUBLISHED);
            assertVersionMatch(locked.getVersion(), expectedVersion);

            DdcSiContentStrategy strategy = selectStrategy(locked.getKind());
            NormalizedContent normalized = strategy.validateAndNormalize(locked.getKind(), locked.getSpecJson());
            List<DdcSiReferenceBO> outgoing = strategy.extractReferences(locked.getKind(), normalized.getNormalizedJson());
            dependencyValidator.validateAcyclicAndPublishedTargets(
                    tenantId, locked.getBizCode(), locked.getNamespaceId(), locked.getEnvCode(), locked.getAppCode(),
                    locked.getKind(), locked.getResourceKey(), outgoing);

            long nextVersionNo = repository.allocateNextVersionNo(tenantId, resourceId);
            Instant now = clock.instant();
            DdcSiVersionBO createdVersion = repository.insertVersion(DdcSiVersionBO.builder()
                    .tenantId(tenantId)
                    .createUserId(actor)
                    .updateUserId(actor)
                    .createTime(now)
                    .updateTime(now)
                    .version(0L)
                    .resourceId(resourceId)
                    .versionNo(nextVersionNo)
                    .kind(locked.getKind())
                    .specJson(normalized.getNormalizedJson())
                    .digestSha256(normalized.getDigestSha256())
                    .changeNote(changeNote == null ? "" : changeNote)
                    .indexStatus("PENDING")
                    .publishedTime(now)
                    .envCode(locked.getEnvCode())
                    .build());

            repository.replaceReferences(tenantId, locked.getEnvCode(), toSourceType(locked.getKind()),
                    locked.getResourceKey(), resourceId, nextVersionNo, locked.getNamespaceId(), outgoing, actor, now);

            locked.setLifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                    .setLastVersionNo(nextVersionNo)
                    .setCurrentVersionId(createdVersion.getId())
                    .setCurrentVersionNo(nextVersionNo)
                    .setPublishedTime(now)
                    .setContentSha256(normalized.getDigestSha256())
                    .setSpecJson(normalized.getNormalizedJson())
                    .setUpdateUserId(actor)
                    .setUpdateTime(now);
            DdcSiResourceBO updated = repository.updateVersioned(locked)
                    .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent publish conflict"));
            repository.updateSearchTsv(tenantId, resourceId, strategy.extractSearchableText(
                    updated.getKind(), updated.getSpecJson(), updated.getDisplayName(), updated.getDescription()));
            outboxNotifier.enqueueAndScheduleAfterCommit(EventType.PUBLISHED, updated, createdVersion);
            return repository.findVersionByResourceAndNo(tenantId, resourceId, nextVersionNo).orElse(createdVersion);
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

            DdcSiVersionBO target = repository.findVersionByResourceAndNo(tenantId, resourceId, targetVersionNo)
                    .orElseThrow(() -> new CommonException(CODE_NOT_FOUND, "TIANSHU_SI_VERSION_NOT_FOUND",
                            "target version not found: " + targetVersionNo));

            DdcSiContentStrategy strategy = selectStrategy(locked.getKind());
            List<DdcSiReferenceBO> outgoing = strategy.extractReferences(locked.getKind(), target.getSpecJson());
            dependencyValidator.validateAcyclicAndPublishedTargets(
                    tenantId, locked.getBizCode(), locked.getNamespaceId(), locked.getEnvCode(), locked.getAppCode(),
                    locked.getKind(), locked.getResourceKey(), outgoing);

            Instant now = clock.instant();
            repository.replaceReferences(tenantId, locked.getEnvCode(), toSourceType(locked.getKind()),
                    locked.getResourceKey(), resourceId, target.getVersionNo(), locked.getNamespaceId(), outgoing, actor, now);

            locked.setLifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                    .setCurrentVersionId(target.getId())
                    .setCurrentVersionNo(target.getVersionNo())
                    .setSpecJson(target.getSpecJson())
                    .setContentSha256(target.getDigestSha256())
                    .setPublishedTime(now)
                    .setUpdateUserId(actor)
                    .setUpdateTime(now);
            DdcSiResourceBO updated = repository.updateVersioned(locked)
                    .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent rollback conflict"));
            outboxNotifier.enqueueAndScheduleAfterCommit(EventType.ROLLED_BACK, updated, target);
            return target;
        });
    }

    @Override
    public DdcSiVersionBO promote(Long tenantId, Long sourceResourceId, String targetEnvCode, String changeNote, String actor) {
        return transactionTemplate.execute(status -> {
            DdcSiResourceBO source = repository.findProjectedById(tenantId, sourceResourceId)
                    .orElseThrow(() -> notFound(sourceResourceId));
            transitionPolicy.assertCanPromote(source.getEnvCode(), targetEnvCode);
            if (source.getCurrentVersionNo() == null || source.getCurrentVersionId() == null) {
                throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_NOT_PUBLISHED",
                        "source SI resource has no active published snapshot to promote");
            }
            DdcSiVersionBO sourceVer = repository.findVersionById(tenantId, source.getCurrentVersionId())
                    .orElseThrow(() -> notFound(sourceResourceId));

            DdcSiContentStrategy strategy = selectStrategy(source.getKind());
            List<DdcSiReferenceBO> outgoing = strategy.extractReferences(source.getKind(), sourceVer.getSpecJson());
            dependencyValidator.validateAcyclicAndPublishedTargets(
                    tenantId, source.getBizCode(), source.getNamespaceId(), targetEnvCode, source.getAppCode(),
                    source.getKind(), source.getResourceKey(), outgoing);

            Instant now = clock.instant();
            DdcSiResourceBO target = repository.findByBusinessKey(tenantId, source.getBizCode(), source.getNamespaceId(),
                            targetEnvCode, source.getAppCode(), source.getKind(), source.getResourceKey())
                    .orElseGet(() -> repository.insert(DdcSiResourceBO.builder()
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
            long nextVerNo = repository.allocateNextVersionNo(tenantId, target.getId());
            DdcSiVersionBO promotedVer = repository.insertVersion(DdcSiVersionBO.builder()
                    .tenantId(tenantId)
                    .createUserId(actor)
                    .updateUserId(actor)
                    .createTime(now)
                    .updateTime(now)
                    .version(0L)
                    .resourceId(target.getId())
                    .versionNo(nextVerNo)
                    .kind(source.getKind())
                    .specJson(sourceVer.getSpecJson())
                    .digestSha256(sourceVer.getDigestSha256())
                    .sourceEnvCode(source.getEnvCode())
                    .sourceVersionNo(sourceVer.getVersionNo())
                    .changeNote(changeNote == null ? "promote from " + source.getEnvCode() : changeNote)
                    .indexStatus("PENDING")
                    .publishedTime(now)
                    .envCode(targetEnvCode)
                    .build());

            repository.replaceReferences(tenantId, targetEnvCode, toSourceType(target.getKind()),
                    target.getResourceKey(), target.getId(), nextVerNo, target.getNamespaceId(), outgoing, actor, now);

            target.setDisplayName(source.getDisplayName())
                    .setDescription(source.getDescription())
                    .setTags(source.getTags())
                    .setLifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                    .setLastVersionNo(nextVerNo)
                    .setCurrentVersionId(promotedVer.getId())
                    .setCurrentVersionNo(nextVerNo)
                    .setPublishedTime(now)
                    .setSpecJson(sourceVer.getSpecJson())
                    .setContentSha256(sourceVer.getDigestSha256())
                    .setUpdateUserId(actor)
                    .setUpdateTime(now);
            DdcSiResourceBO updatedTarget = repository.updateVersioned(target)
                    .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent promotion conflict"));
            repository.updateSearchTsv(tenantId, updatedTarget.getId(), strategy.extractSearchableText(
                    updatedTarget.getKind(), updatedTarget.getSpecJson(), updatedTarget.getDisplayName(), updatedTarget.getDescription()));
            outboxNotifier.enqueueAndScheduleAfterCommit(EventType.PROMOTED, updatedTarget, promotedVer);
            return promotedVer;
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
                .map(res -> converter.toDescriptor(res, null, List.of()))
                .toList();
    }

    @Override
    public Optional<DdcSiResourceDescriptor> resolveResource(Long tenantId, String bizCode, String namespaceCode,
                                                             String envCode, String ownerAppCode, String callerAppCode,
                                                             DdcSiKindEnum kind, String resourceKey,
                                                             Integer requestedVersionNo) {
        Long namespaceId = repository.resolveNamespaceId(tenantId, bizCode, namespaceCode);
        String effectiveOwnerApp = ownerAppCode == null || ownerAppCode.isBlank() ? callerAppCode : ownerAppCode;
        Optional<DdcSiResourceBO> resOpt = repository.findByBusinessKey(
                tenantId, bizCode, namespaceId, envCode, effectiveOwnerApp, kind, resourceKey);
        if (resOpt.isEmpty()) {
            return Optional.empty();
        }
        DdcSiResourceBO resource = resOpt.get();
        if (!resource.getLifecycleStatus().isQueryableByRuntime()) {
            return Optional.empty();
        }

        Long chosenVersionNo = requestedVersionNo != null ? requestedVersionNo.longValue() : null;
        if (chosenVersionNo == null && callerAppCode != null && !callerAppCode.isBlank()) {
            chosenVersionNo = repository.findActiveBinding(
                            tenantId, bizCode, namespaceId, envCode, callerAppCode, kind, resourceKey)
                    .map(DdcSiBindingBO::getPinnedVersionNo)
                    .orElse(null);
        }
        if (chosenVersionNo == null) {
            chosenVersionNo = resource.getCurrentVersionNo();
        }
        DdcSiVersionBO resolvedVersion = chosenVersionNo == null
                ? null
                : repository.findVersionByResourceAndNo(tenantId, resource.getId(), chosenVersionNo).orElse(null);
        List<DdcSiReferenceBO> refs = repository.findReferencesBySource(
                tenantId, envCode, toSourceType(kind), resourceKey);
        return Optional.of(converter.toDescriptor(resource, resolvedVersion, refs));
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

            if (existingOpt.isPresent()) {
                DdcSiResourceBO existing = existingOpt.get();
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
                DdcSiResourceBO existing = existingOpt.get();
                existing.setDisplayName(request.getDisplayName())
                        .setDescription(request.getDescription() == null ? "" : request.getDescription())
                        .setSpecJson(normalized.getNormalizedJson())
                        .setContentSha256(normalized.getDigestSha256())
                        .setUpdateUserId("system:auto-register")
                        .setUpdateTime(now);
                resource = repository.updateVersioned(existing)
                        .orElseThrow(() -> new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT",
                                "concurrent auto-registration conflict"));
            }
            repository.updateSearchTsv(request.getTenantId(), resource.getId(), searchable);

            if (request.isAutoPublishIfNew()) {
                long nextVer = repository.allocateNextVersionNo(request.getTenantId(), resource.getId());
                DdcSiVersionBO ver = repository.insertVersion(DdcSiVersionBO.builder()
                        .tenantId(request.getTenantId())
                        .createUserId("system:auto-register")
                        .updateUserId("system:auto-register")
                        .createTime(now)
                        .updateTime(now)
                        .version(0L)
                        .resourceId(resource.getId())
                        .versionNo(nextVer)
                        .kind(resource.getKind())
                        .specJson(normalized.getNormalizedJson())
                        .digestSha256(normalized.getDigestSha256())
                        .changeNote("auto-registered")
                        .indexStatus("PENDING")
                        .publishedTime(now)
                        .envCode(resource.getEnvCode())
                        .build());
                resource.setLifecycleStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                        .setLastVersionNo(nextVer)
                        .setCurrentVersionId(ver.getId())
                        .setCurrentVersionNo(nextVer)
                        .setPublishedTime(now);
                DdcSiResourceBO published = repository.updateVersioned(resource).orElse(resource);
                outboxNotifier.enqueueAndScheduleAfterCommit(EventType.PUBLISHED, published, ver);
                return converter.toDescriptor(published, ver, List.of());
            }
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
