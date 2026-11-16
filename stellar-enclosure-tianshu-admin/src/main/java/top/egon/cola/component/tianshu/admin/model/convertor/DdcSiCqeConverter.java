package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.query.DdcSiPageQuery;
import top.egon.cola.component.tianshu.admin.model.query.DdcSiSemanticSearchRequestQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiBindingResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiEnvMatrixResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiReferenceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiResourceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiSemanticSearchResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcSiVersionResult;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;

import java.util.List;

/** MapStruct converter between REST CQE inputs, domain BOs, and HTTP Result VOs. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface DdcSiCqeConverter {

    @BeanMapping(ignoreByDefault = false)
    DdcSiResourceResult toResourceResult(DdcSiResourceBO source);

    List<DdcSiResourceResult> toResourceResults(List<DdcSiResourceBO> sources);

    @BeanMapping(ignoreByDefault = false)
    DdcSiVersionResult toVersionResult(DdcSiVersionBO source);

    List<DdcSiVersionResult> toVersionResults(List<DdcSiVersionBO> sources);

    @BeanMapping(ignoreByDefault = false)
    DdcSiBindingResult toBindingResult(DdcSiBindingBO source);

    @BeanMapping(ignoreByDefault = false)
    DdcSiReferenceResult toReferenceResult(DdcSiReferenceBO source);

    List<DdcSiReferenceResult> toReferenceResults(List<DdcSiReferenceBO> sources);

    @Mapping(target = "versionId", source = "id")
    @Mapping(target = "activeVersionNo", source = "versionNo")
    @Mapping(target = "published", expression = "java(source != null && source.getVersionNo() != null)")
    DdcSiEnvMatrixResult toEnvMatrixResult(DdcSiVersionBO source);

    List<DdcSiEnvMatrixResult> toEnvMatrixResults(List<DdcSiVersionBO> sources);

    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "includeDeprecated", constant = "false")
    @Mapping(target = "labels", ignore = true)
    DdcSiSemanticSearchQuery toSemanticSearchQuery(DdcSiSemanticSearchRequestQuery query, Long tenantId);

    default PageQuery toPageQuery(DdcSiPageQuery query) {
        return new PageQuery(query.getPageNo(), query.getPageSize());
    }

    default DdcPageResult<DdcSiResourceResult> toResourcePage(DdcPageResult<DdcSiResourceBO> page) {
        return DdcPageResult.<DdcSiResourceResult>builder()
                .records(toResourceResults(page.getRecords()))
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .build();
    }

    default DdcPageResult<DdcSiVersionResult> toVersionPage(DdcPageResult<DdcSiVersionBO> page) {
        return DdcPageResult.<DdcSiVersionResult>builder()
                .records(toVersionResults(page.getRecords()))
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .build();
    }

    default DdcSiSemanticSearchResult toSearchResult(List<DdcSiSearchHit> hits) {
        List<DdcSiSearchHit> safeHits = hits == null ? List.of() : List.copyOf(hits);
        boolean degraded = !safeHits.isEmpty() && safeHits.stream().allMatch(DdcSiSearchHit::isDegradedToFtsOnly);
        return new DdcSiSemanticSearchResult(degraded, safeHits.size(), safeHits);
    }
}
