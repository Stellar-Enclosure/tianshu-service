package top.egon.cola.component.tianshu.admin.repository.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties;
import top.egon.cola.component.common.mybatis.extension.EgonColaRepository;
import top.egon.cola.component.common.mybatis.model.EgonColaIdentifierGenerator;
import top.egon.cola.component.tianshu.admin.dao.DdcSiBindingMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiReferenceMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiResourceMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiVersionMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiEnumConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcSiResourceConverter;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiBindingPO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiReferencePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiResourcePO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiVersionPO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SI 资源、版本、绑定与有向引用五表协同仓储实现。 */
@Slf4j
@Repository("ddcSiResourceRepository")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Validated
public class DdcSiResourceRepositoryImpl extends EgonColaRepository<DdcSiResourceMapper, DdcSiResourcePO>
        implements DdcSiResourceRepository {

    private static final int CODE_NOT_FOUND = 56404;
    private static final int CODE_CONFLICT = 56409;

    @Qualifier("ddcSiResourceMapper")
    private final DdcSiResourceMapper resourceMapper;
    @Qualifier("ddcSiVersionMapper")
    private final DdcSiVersionMapper versionMapper;
    @Qualifier("ddcSiBindingMapper")
    private final DdcSiBindingMapper bindingMapper;
    @Qualifier("ddcSiReferenceMapper")
    private final DdcSiReferenceMapper referenceMapper;
    @Qualifier("ddcNamespaceRepository")
    private final DdcNamespaceRepository namespaceRepository;
    @Qualifier("ddcSiResourceConverterImpl")
    private final DdcSiResourceConverter converter;
    @Qualifier("ddcSiEnumConverterImpl")
    private final DdcSiEnumConverter enumConverter;
    @Qualifier("egon.cola.component.mybatis-plus-top.egon.cola.component.common.mybatis.autoconfigure.EgonColaMybatisPlusProperties")
    private final EgonColaMybatisPlusProperties properties;
    @Qualifier("egonColaIdentifierGenerator")
    private final EgonColaIdentifierGenerator identifierGenerator;

    @Override
    public DdcSiResourceMapper getBaseMapper() {
        return resourceMapper;
    }

    @Override
    protected EgonColaMybatisPlusProperties getProperties() {
        return properties;
    }

    @Override
    public Long resolveNamespaceId(Long tenantId, String bizCode, String namespaceCode) {
        return namespaceRepository.findByBizCodeAndNamespaceCode(bizCode, namespaceCode)
                .filter(ns -> ns.getTenantId() == null || ns.getTenantId().equals(tenantId))
                .map(ns -> ns.getId())
                .orElseThrow(() -> new CommonException(CODE_NOT_FOUND, "TIANSHU_SI_NAMESPACE_NOT_FOUND",
                        "namespace not found in tenant scope: " + bizCode + "/" + namespaceCode));
    }

    @Override
    public Optional<DdcSiResourceBO> findActiveById(Long tenantId, Long id) {
        return Optional.ofNullable(resourceMapper.selectActiveById(tenantId, id)).map(converter::toTarget);
    }

    @Override
    public Optional<DdcSiResourceBO> findProjectedById(Long tenantId, Long id) {
        return Optional.ofNullable(resourceMapper.selectProjectedById(tenantId, id)).map(converter::toTarget);
    }

    @Override
    public Optional<DdcSiResourceBO> findActiveForUpdateById(Long tenantId, Long id) {
        return Optional.ofNullable(resourceMapper.selectForUpdateById(tenantId, id)).map(converter::toTarget);
    }

    @Override
    public Optional<DdcSiResourceBO> findByBusinessKey(Long tenantId, String bizCode, Long namespaceId, String envCode,
                                                       String appCode, DdcSiKindEnum kind, String resourceKey) {
        return Optional.ofNullable(resourceMapper.selectByBusinessKey(
                        tenantId, bizCode, namespaceId, envCode, appCode, enumConverter.toSource(kind), resourceKey))
                .map(converter::toTarget);
    }

    @Override
    public DdcPageResult<DdcSiResourceBO> searchPage(Long tenantId, String bizCode, Long namespaceId, String envCode,
                                                     String appCode, DdcSiKindEnum kind,
                                                     DdcSiLifecycleStatusEnum lifecycleStatus,
                                                     String keyword, String tag, PageQuery pageQuery) {
        PageQuery page = pageQuery == null ? PageQuery.defaultPage() : pageQuery;
        long offset = Math.multiplyExact((long) page.pageNo() - 1, page.pageSize());
        var persistedKind = kind == null ? null : enumConverter.toSource(kind);
        var persistedStatus = lifecycleStatus == null ? null : enumConverter.toPersistenceLifecycle(lifecycleStatus);
        List<DdcSiResourceBO> records = resourceMapper.selectPage(
                        tenantId, bizCode, namespaceId, envCode, appCode, persistedKind, persistedStatus,
                        keyword, tag, offset, page.pageSize())
                .stream().map(converter::toTarget).toList();
        long total = resourceMapper.countPage(
                tenantId, bizCode, namespaceId, envCode, appCode, persistedKind, persistedStatus, keyword, tag);
        return DdcPageResult.<DdcSiResourceBO>builder()
                .records(records).total(total).pageNo(page.pageNo()).pageSize(page.pageSize()).build();
    }

    @Override
    public DdcPageResult<DdcSiResourceBO> searchCatalog(Long tenantId, String bizCode, Long namespaceId, String envCode,
                                                        String appCode, DdcSiKindEnum kind, boolean includeDeprecated,
                                                        String keyword, String tag, PageQuery pageQuery) {
        PageQuery page = pageQuery == null ? PageQuery.defaultPage() : pageQuery;
        long offset = Math.multiplyExact((long) page.pageNo() - 1, page.pageSize());
        var persistedKind = kind == null ? null : enumConverter.toSource(kind);
        List<DdcSiResourceBO> records = resourceMapper.selectCatalog(
                        tenantId, bizCode, namespaceId, envCode, appCode, persistedKind, includeDeprecated,
                        keyword, tag, offset, page.pageSize())
                .stream().map(converter::toTarget).toList();
        long total = resourceMapper.countCatalog(
                tenantId, bizCode, namespaceId, envCode, appCode, persistedKind, includeDeprecated, keyword, tag);
        return DdcPageResult.<DdcSiResourceBO>builder()
                .records(records).total(total).pageNo(page.pageNo()).pageSize(page.pageSize()).build();
    }

    @Override
    public DdcSiResourceBO insert(DdcSiResourceBO resource) {
        DdcSiResourcePO po = converter.toSource(resource);
        try {
            if (!save(po)) {
                throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "failed to insert SI resource");
            }
        } catch (DuplicateKeyException dup) {
            throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT",
                    "SI resource already exists in scope", dup);
        }
        return converter.toTarget(po);
    }

    @Override
    public Optional<DdcSiResourceBO> updateVersioned(DdcSiResourceBO resource) {
        DdcSiResourcePO po = converter.toSource(resource);
        int affected = resourceMapper.updateVersionedFields(po);
        if (affected == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(resourceMapper.selectProjectedById(resource.getTenantId(), resource.getId()))
                .map(converter::toTarget);
    }

    @Override
    public void updateSearchTsv(Long tenantId, Long id, String tsvText) {
        resourceMapper.updateSearchTsv(tenantId, id, tsvText);
    }

    @Override
    public boolean softDeleteVersioned(DdcSiResourceBO resource) {
        return removeById(converter.toSource(resource));
    }

    @Override
    public long allocateNextVersionNo(Long tenantId, Long resourceId) {
        Long next = versionMapper.selectNextVersionNo(tenantId, resourceId);
        if (next == null || next <= 0) {
            throw new IllegalStateException("TIANSHU_SI_VERSION_NUMBER_UNAVAILABLE");
        }
        return next;
    }

    @Override
    public long allocateVersionId() {
        Long id = identifierGenerator.nextId(new DdcSiVersionPO());
        if (id == null || id <= 0) {
            throw new IllegalStateException("TIANSHU_SI_VERSION_ID_UNAVAILABLE");
        }
        return id;
    }

    @Override
    public DdcSiVersionBO insertVersion(DdcSiVersionBO version) {
        if (version == null || version.getId() == null || version.getId() <= 0
                || version.getDependencyManifestJson() == null || !version.getDependencyManifestJson().isObject()) {
            throw new IllegalArgumentException("TIANSHU_SI_VERSION_SNAPSHOT_REQUIRED");
        }
        DdcSiVersionPO po = converter.toVersionPo(version);
        try {
            if (versionMapper.insert(po) <= 0) {
                throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "failed to insert SI version snapshot");
            }
        } catch (DuplicateKeyException dup) {
            throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "duplicate SI version number", dup);
        }
        return converter.toVersionBo(po);
    }

    @Override
    public Optional<DdcSiVersionBO> findVersionById(Long tenantId, Long versionId) {
        return Optional.ofNullable(versionMapper.selectActiveById(tenantId, versionId)).map(converter::toVersionBo);
    }

    @Override
    public Optional<DdcSiVersionBO> findVersionByResourceAndNo(Long tenantId, Long resourceId, Long versionNo) {
        return Optional.ofNullable(versionMapper.selectByResourceIdAndVersionNo(tenantId, resourceId, versionNo))
                .map(converter::toVersionBo);
    }

    @Override
    public Optional<DdcSiVersionBO> findHistoryVersionByResourceAndNo(Long tenantId, Long resourceId, Long versionNo) {
        return Optional.ofNullable(versionMapper.selectHistoryByResourceIdAndVersionNo(tenantId, resourceId, versionNo))
                .map(converter::toVersionBo);
    }

    @Override
    public List<DdcSiVersionBO> findHistoryVersionsByIds(Long tenantId, List<Long> versionIds) {
        if (tenantId == null || tenantId <= 0 || versionIds == null || versionIds.size() > 100
                || versionIds.stream().anyMatch(id -> id == null || id <= 0)
                || versionIds.stream().distinct().count() != versionIds.size()) {
            throw new IllegalArgumentException("TIANSHU_SI_HISTORY_BATCH_INVALID");
        }
        if (versionIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = versionIds.stream().sorted().toList();
        return versionMapper.selectHistoryByIds(tenantId, ids).stream().map(converter::toVersionBo).toList();
    }

    @Override
    public DdcPageResult<DdcSiVersionBO> pageVersions(Long tenantId, Long resourceId, PageQuery pageQuery) {
        PageQuery page = pageQuery == null ? PageQuery.defaultPage() : pageQuery;
        long offset = Math.multiplyExact((long) page.pageNo() - 1, page.pageSize());
        List<DdcSiVersionBO> rows = versionMapper.selectPage(tenantId, resourceId, offset, page.pageSize())
                .stream().map(converter::toVersionBo).toList();
        long total = versionMapper.countPage(tenantId, resourceId);
        return DdcPageResult.<DdcSiVersionBO>builder()
                .records(rows).total(total).pageNo(page.pageNo()).pageSize(page.pageSize()).build();
    }

    @Override
    public List<DdcSiVersionBO> findEnvMatrix(Long tenantId, String bizCode, Long namespaceId, DdcSiKindEnum kind, String resourceKey) {
        return versionMapper.selectEnvMatrixRows(tenantId, bizCode, namespaceId, enumConverter.toSource(kind), resourceKey)
                .stream().map(converter::toVersionBo).toList();
    }

    @Override
    public Optional<DdcSiBindingBO> findActiveBinding(Long tenantId, String bizCode, Long namespaceId, String envCode,
                                                      String appCode, DdcSiKindEnum kind, String resourceKey) {
        return Optional.ofNullable(bindingMapper.selectActiveByTarget(
                        tenantId, bizCode, namespaceId, envCode, appCode, enumConverter.toSource(kind), resourceKey))
                .map(converter::toBindingBo);
    }

    @Override
    public List<DdcSiBindingBO> findBindingsByResourceId(Long tenantId, Long resourceId) {
        return bindingMapper.selectByResourceId(tenantId, resourceId).stream().map(converter::toBindingBo).toList();
    }

    @Override
    public DdcSiBindingBO upsertBinding(DdcSiBindingBO binding, String actor, Instant now) {
        bindingMapper.deactivateByTarget(binding.getTenantId(), binding.getBizCode(), binding.getNamespaceId(),
                binding.getEnvCode(), binding.getAppCode(), enumConverter.toSource(binding.getKind()),
                binding.getResourceKey(), actor, now);
        DdcSiBindingPO po = converter.toBindingPo(binding);
        po.setBindingStatus(enumConverter.toPersistenceBindingStatus(DdcSiBindingStatusEnum.ACTIVE));
        po.setBoundTime(now);
        po.setCreateUserId(actor);
        po.setUpdateUserId(actor);
        po.setCreateTime(now);
        po.setUpdateTime(now);
        po.setVersion(0L);
        try {
            bindingMapper.insert(po);
        } catch (DuplicateKeyException dup) {
            throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_CONFLICT", "concurrent binding conflict", dup);
        }
        return converter.toBindingBo(po);
    }

    @Override
    public void deactivateBinding(Long tenantId, String bizCode, Long namespaceId, String envCode, String appCode,
                                  DdcSiKindEnum kind, String resourceKey, String actor, Instant now) {
        bindingMapper.deactivateByTarget(tenantId, bizCode, namespaceId, envCode, appCode,
                enumConverter.toSource(kind), resourceKey, actor, now);
    }

    @Override
    public List<DdcSiReferenceBO> findBlockingInboundReferences(Long tenantId, String envCode,
                                                                DdcSiKindEnum targetKind, String targetKey) {
        return referenceMapper.selectBlockingInboundReferences(
                        tenantId, envCode, enumConverter.toSource(targetKind), targetKey)
                .stream().map(converter::toReferenceBo).toList();
    }

    @Override
    public List<DdcSiReferenceBO> findAgentGraphEdges(Long tenantId, String envCode) {
        return referenceMapper.selectAgentGraphEdges(tenantId, envCode).stream().map(converter::toReferenceBo).toList();
    }

    @Override
    public List<DdcSiReferenceBO> findReferencesBySource(Long tenantId, String envCode,
                                                         DdcSiReferenceTargetTypeEnum sourceType, String sourceKey) {
        return referenceMapper.selectBySource(
                        tenantId, envCode, enumConverter.toPersistenceReferenceType(sourceType), sourceKey)
                .stream().map(converter::toReferenceBo).toList();
    }

    @Override
    public void replaceReferences(Long tenantId, String envCode, DdcSiReferenceTargetTypeEnum sourceType,
                                  String sourceKey, Long sourceResourceId, Long sourceVersionNo, Long namespaceId,
                                  List<DdcSiReferenceBO> references, String actor, Instant now) {
        referenceMapper.deleteBySource(
                tenantId, envCode, enumConverter.toPersistenceReferenceType(sourceType), sourceKey, actor, now);
        insertReferenceRows(tenantId, envCode, sourceType, sourceKey, sourceResourceId, sourceVersionNo,
                namespaceId, references, actor, now);
    }

    @Override
    public void replaceProcessReferences(Long tenantId, String envCode, String processKey,
                                         List<DdcSiReferenceBO> references, String actor, Instant now) {
        referenceMapper.deleteProcessReferences(tenantId, envCode, processKey, actor, now);
        insertReferenceRows(tenantId, envCode, DdcSiReferenceTargetTypeEnum.TIANJI_PROCESS, processKey,
                null, null, null, references, actor, now);
    }

    @Override
    public void deleteProcessReferences(Long tenantId, String envCode, String processKey, String actor, Instant now) {
        referenceMapper.deleteProcessReferences(tenantId, envCode, processKey, actor, now);
    }

    @Override
    public long countByScope(Long tenantId, String envCode, long limit) {
        return resourceMapper.countPage(tenantId, null, null, envCode, null, null, null, null, null);
    }

    private void insertReferenceRows(Long tenantId, String envCode, DdcSiReferenceTargetTypeEnum sourceType,
                                     String sourceKey, Long sourceResourceId, Long sourceVersionNo, Long namespaceId,
                                     List<DdcSiReferenceBO> references, String actor, Instant now) {
        if (references == null || references.isEmpty()) {
            return;
        }
        for (DdcSiReferenceBO ref : references) {
            DdcSiKindEnum targetKind = ref.getTargetKind() != null
                    ? ref.getTargetKind()
                    : toSiKind(ref.getTargetType());
            if (targetKind == null) {
                continue;
            }
            DdcSiReferenceBO enriched = DdcSiReferenceBO.builder()
                    .tenantId(tenantId)
                    .sourceResourceId(sourceResourceId)
                    .sourceVersionNo(sourceVersionNo)
                    .sourceType(sourceType)
                    .sourceKey(sourceKey)
                    .targetType(ref.getTargetType())
                    .targetKind(targetKind)
                    .targetKey(ref.getTargetKey())
                    .targetVersionNo(ref.getTargetVersionNo() != null ? ref.getTargetVersionNo() : ref.getPinnedVersionNo())
                    .envCode(envCode)
                    .namespaceId(namespaceId)
                    .createUserId(actor)
                    .updateUserId(actor)
                    .createTime(now)
                    .updateTime(now)
                    .version(0L)
                    .build();
            DdcSiReferencePO po = converter.toReferencePo(enriched);
            referenceMapper.insert(po);
        }
    }

    private static DdcSiKindEnum toSiKind(DdcSiReferenceTargetTypeEnum targetType) {
        if (targetType == null) {
            return null;
        }
        return switch (targetType) {
            case AGENT -> DdcSiKindEnum.AGENT;
            case MCP -> DdcSiKindEnum.MCP;
            case PROMPT -> DdcSiKindEnum.PROMPT;
            case SKILL -> DdcSiKindEnum.SKILL;
            case TIANJI_PROCESS -> null;
        };
    }
}
