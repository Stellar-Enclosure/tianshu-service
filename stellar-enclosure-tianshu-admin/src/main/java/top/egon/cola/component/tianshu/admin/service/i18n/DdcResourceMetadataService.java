package top.egon.cola.component.tianshu.admin.service.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcResourceMetadataResult;
import top.egon.cola.component.tianshu.admin.repository.DdcResourceMetadataProjectionRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.DefinitionLifecycle;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only resource and permission view of the exact source set selected by RBAC. */
@Service("ddcResourceMetadataService")
@RequiredArgsConstructor
public class DdcResourceMetadataService {
    @Qualifier("ddcI18nScopeValidator") private final DdcI18nScopeValidator scopes;
    @Qualifier("ddcCatalogActivationService") private final DdcCatalogActivationService activations;
    @Qualifier("ddcResourceMetadataProjectionRepository") private final DdcResourceMetadataProjectionRepository repository;
    @Qualifier("ddcMetadataReadTransactionTemplate") private final TransactionTemplate transaction;
    private final ObjectMapper mapper = new ObjectMapper();

    public DdcPageResult<DdcResourceMetadataResult> search(DdcI18nScope scope, String catalogScopeId, String activationVersion,
            DdcResourceMetadataQuery filter, PageQuery page) {
        scopes.require(scope, DdcAdminCapability.READ);
        if (page.pageNo() < 1 || page.pageSize() < 1 || page.pageSize() > 100) throw DdcI18nValidation.invalid("Invalid paging");
        if (filter.resourceType() != null) ResourceType.valueOf(filter.resourceType());
        if (filter.lifecycle() != null) DefinitionLifecycle.valueOf(filter.lifecycle());
        if (filter.metadataKind() != null && !List.of("RESOURCE", "PERMISSION").contains(filter.metadataKind())) throw DdcI18nValidation.invalid("Invalid metadata kind");
        for (String text : new String[]{filter.producerId(), filter.permissionCode(), filter.query()}) {
            if (text != null && (text.isBlank() || text.length() > 200 || text.chars().anyMatch(Character::isISOControl))) throw DdcI18nValidation.invalid("Invalid metadata filter");
        }
        var boundedFilter = new DdcResourceMetadataQuery(filter.producerId(), filter.metadataKind(), filter.resourceType(), filter.lifecycle(),
                filter.permissionCode(), filter.query() == null ? null : filter.query().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
        CatalogScope target = new CatalogScope(catalogScopeId, EgonColaTenantIdProvider.currentTenantId().toString(), scope.bizCode(), scope.appCode(), scope.namespaceCode(), scope.env());
        var selection = activations.resolve(target, activationVersion);
        return Objects.requireNonNull(transaction.execute(status -> {
            scopes.require(scope, DdcAdminCapability.READ);
            var result = repository.search(Long.valueOf(catalogScopeId), selection.projectionId(), boundedFilter, page);
            long bytes = 0;
            for (var row : result.getRecords()) {
                for (String json : new String[]{row.getTargetJson(), row.getPolicyJson(), row.getI18nRefsJson(), row.getReferencedPermissionCodesJson()}) {
                    bytes += json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                }
                if (bytes > DdcI18nBundleAssembler.MAX_RESPONSE_BYTES) throw DdcI18nBundleAssembler.responseTooLarge();
            }
            var rows = result.getRecords().stream().map(row -> {
                var producer = selection.members().stream().filter(member -> member.producerId().equals(row.getProducerId())
                        && member.sourceId().equals(row.getSourceId()) && member.sourceVersion().equals(row.getSourceVersion())).findFirst()
                        .orElseThrow(DdcCatalogActivationClient::unavailable);
                if (!Long.valueOf(target.tenantId()).equals(row.getTenantId()) || !Long.valueOf(catalogScopeId).equals(row.getCatalogScopeId())) throw DdcCatalogActivationClient.unavailable();
                return new DdcResourceMetadataResult(target, activationVersion, producer.producerId(), producer.producerVersion(), producer.sourceVersion(),
                        row.getMetadataKind(), row.getDefinitionKey(), decimal(row.getResourceId()), decimal(row.getPermissionId()), row.getResourceType(), row.getDefinitionLifecycle(),
                        row.getPageCode(), row.getLabelKey(), row.getDescriptionKey(), object(row.getTargetJson()), object(row.getPolicyJson()), object(row.getI18nRefsJson()), codes(row.getReferencedPermissionCodesJson()));
            }).toList();
            var response = DdcPageResult.<DdcResourceMetadataResult>builder().records(rows).pageNo(page.pageNo()).pageSize(page.pageSize()).total(result.getTotal()).build();
            try {
                if (mapper.writeValueAsBytes(response).length > DdcI18nBundleAssembler.MAX_RESPONSE_BYTES) throw DdcI18nBundleAssembler.responseTooLarge();
            } catch (IOException invalid) { throw new IllegalStateException("TIANSHU_METADATA_PROJECTION_INVALID", invalid); }
            return response;
        }));
    }

    private Map<String, Object> object(String json) {
        try { return mapper.readValue(json, new TypeReference<Map<String, Object>>() { }); }
        catch (IOException invalid) { throw new IllegalStateException("TIANSHU_METADATA_PROJECTION_INVALID", invalid); }
    }
    private List<String> codes(String json) {
        try { return List.copyOf(mapper.readValue(json, new TypeReference<List<String>>() { })); }
        catch (IOException invalid) { throw new IllegalStateException("TIANSHU_METADATA_PROJECTION_INVALID", invalid); }
    }
    private String decimal(Long value) { return value == null ? null : value.toString(); }
}
