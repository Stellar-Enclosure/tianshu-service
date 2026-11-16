package top.egon.cola.component.tianshu.admin.service.si;

import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.util.List;
import java.util.Optional;

/** SI 四态治理领域核心业务服务门面接口（覆盖控制面 14 API、gRPC 3 RPC 与天机流程内部同步）。 */
public interface DdcSiResourceService {

    DdcPageResult<DdcSiResourceBO> page(
            Long tenantId, String bizCode, String namespaceCode, String envCode, String appCode,
            DdcSiKindEnum kind, DdcSiLifecycleStatusEnum lifecycleStatus, String keyword, String tag,
            PageQuery pageQuery);

    DdcSiResourceBO get(Long tenantId, Long resourceId);

    DdcSiResourceBO create(
            Long tenantId, String bizCode, String namespaceCode, String envCode, String appCode,
            DdcSiKindEnum kind, String resourceKey, String displayName, String description,
            List<String> tags, DdcSiSourceTypeEnum sourceType, String specJson, String actor);

    DdcSiResourceBO updateDraft(
            Long tenantId, Long resourceId, Long expectedVersion, String displayName,
            String description, List<String> tags, String specJson, String actor);

    DdcSiVersionBO publish(Long tenantId, Long resourceId, Long expectedVersion, String changeNote, String actor);

    DdcSiVersionBO rollback(Long tenantId, Long resourceId, Long expectedVersion, Long targetVersionNo, String changeNote, String actor);

    DdcSiVersionBO promote(Long tenantId, Long sourceResourceId, String targetEnvCode, String changeNote, String actor);

    DdcSiResourceBO deprecate(Long tenantId, Long resourceId, Long expectedVersion, String actor);

    void archive(Long tenantId, Long resourceId, Long expectedVersion, String actor);

    DdcPageResult<DdcSiVersionBO> pageVersions(Long tenantId, Long resourceId, PageQuery pageQuery);

    DdcSiVersionBO getVersion(Long tenantId, Long resourceId, Long versionNo);

    List<DdcSiReferenceBO> listReferences(Long tenantId, Long resourceId, String direction);

    List<DdcSiVersionBO> getEnvMatrix(Long tenantId, String bizCode, String namespaceCode, DdcSiKindEnum kind, String resourceKey);

    DdcSiBindingBO bind(
            Long tenantId, String bizCode, String namespaceCode, String envCode, String appCode,
            DdcSiKindEnum kind, String resourceKey, Long pinnedVersionNo, String actor);

    void unbind(
            Long tenantId, String bizCode, String namespaceCode, String envCode, String appCode,
            DdcSiKindEnum kind, String resourceKey, String actor);

    List<DdcSiSearchHit> hybridSearch(DdcSiSemanticSearchQuery query);

    void triggerReindex(Long tenantId, Long resourceId, Long versionId);

    List<DdcSiResourceDescriptor> listCatalog(DdcSiCatalogQuery query);

    Optional<DdcSiResourceDescriptor> resolveResource(
            Long tenantId, String bizCode, String namespaceCode, String envCode,
            String ownerAppCode, String callerAppCode, DdcSiKindEnum kind, String resourceKey,
            Integer requestedVersionNo);

    DdcSiResourceDescriptor registerManaged(DdcSiSelfRegistrationRequest request, boolean allowOverwriteConsole);

    void syncProcessReferences(
            Long tenantId, String envCode, String processKey,
            List<DdcSiReferenceDescriptor> references, String actor);

    void deleteProcessReferences(Long tenantId, String envCode, String processKey, String actor);
}
