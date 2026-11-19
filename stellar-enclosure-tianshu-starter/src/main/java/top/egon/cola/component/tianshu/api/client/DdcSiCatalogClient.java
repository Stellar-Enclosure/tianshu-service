package top.egon.cola.component.tianshu.api.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;

import java.util.List;
import java.util.Optional;

/**
 * Transport-agnostic SI catalog port consumed by runtime coordinators and Tianji services.
 */
public interface DdcSiCatalogClient {

    /**
     * Lists published/resolvable SI descriptors matching the query filter.
     */
    List<DdcSiResourceDescriptor> listResources(DdcSiCatalogQuery query);

    /**
     * Resolves an exact versioned or bound published SI resource; never returns unreleased DRAFT states.
     */
    Optional<DdcSiResourceDescriptor> resolveResource(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            @Nullable String appCode,
            DdcSiKindEnum kind,
            String resourceKey,
            @Nullable Integer pinnedVersionNo);

    /** Resolves one exact execution snapshot with independent owner and verified caller identities. */
    default Optional<DdcSiResourceDescriptor> resolveExecutionResource(
            @Positive long tenantId, @NotBlank String bizCode, @NotBlank String namespaceCode, @NotBlank String envCode,
            @Nullable String ownerAppCode, @NotBlank String callerAppCode, @NotNull DdcSiKindEnum kind,
            @NotBlank String resourceKey, @Nullable @Positive Long pinnedVersionNo) {
        throw new UnsupportedOperationException("TIANSHU_SI_EXECUTION_RESOLUTION_UNSUPPORTED");
    }

    /** Complete AGENT execution material; an unsupported adapter must never substitute metadata or active bodies. */
    default Optional<DdcSiExecutionBundleVO> resolveExecutionBundle(
            @Positive long tenantId, @NotBlank String bizCode, @NotBlank String namespaceCode, @NotBlank String envCode,
            @Nullable String ownerAppCode, @NotBlank String callerAppCode, @NotNull DdcSiKindEnum kind,
            @NotBlank String resourceKey, @Nullable @Positive Long pinnedVersionNo) {
        throw new UnsupportedOperationException("TIANSHU_SI_EXECUTION_BUNDLE_UNSUPPORTED");
    }

    /**
     * Executes hybrid vector + FTS semantic search with transparent FTS fallback semantics.
     */
    List<DdcSiSearchHit> semanticSearch(DdcSiSemanticSearchQuery query);

    /**
     * Registers or updates a service-declared resource idempotently using specHash comparison.
     */
    DdcSiResourceDescriptor registerManagedResource(DdcSiSelfRegistrationRequest request);
}
