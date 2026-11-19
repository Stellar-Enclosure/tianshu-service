package top.egon.cola.component.tianshu.admin.service.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcBizBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcLogicalCatalogConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcAppRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcBizRepository;
import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only logical catalog; its caller establishes the verified tenant scope before entering. */
@Slf4j
@Service("ddcLogicalCatalogQueryService")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcLogicalCatalogQueryService {

    @NonNull
    @Qualifier("ddcBizRepository")
    private final DdcBizRepository businesses;
    @NonNull
    @Qualifier("ddcAppRepository")
    private final DdcAppRepository applications;
    @NonNull
    @Qualifier("ddcLogicalCatalogConverterImpl")
    private final DdcLogicalCatalogConverter converter;

    public Optional<DdcManagementBiz> findBusiness(
            @NotBlank @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String id) {
        return businesses.findByPublicId(id).map(converter::toTarget);
    }

    public List<DdcManagementBiz> listBusinesses(
            @Nullable @Size(max = DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH) String keyword) {
        return businesses.list(optionalKeyword(keyword), null).stream().map(converter::toTarget).toList();
    }

    public Optional<DdcManagementApp> findApplication(
            @NotBlank @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String id) {
        return applications.findByPublicId(id).map(application -> converter.toApplication(application,
                requireParent(application.getBizCode())));
    }

    public List<DdcManagementApp> listApplications(
            @Nullable @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String businessId,
            @Nullable @Size(max = DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH) String keyword) {
        String bizCode = null;
        Map<String, DdcBizBO> parents = new HashMap<>();
        if (businessId != null) {
            var selected = businesses.findByPublicId(businessId);
            if (selected.isEmpty()) {
                return List.of();
            }
            bizCode = selected.get().getBizCode();
            if (bizCode == null) {
                throw inconsistent();
            }
            parents.put(bizCode, selected.get());
        }
        String selectedBizCode = bizCode;
        return applications.list(bizCode, optionalKeyword(keyword), null).stream().map(application -> {
            if (application.getBizCode() == null
                    || selectedBizCode != null && !selectedBizCode.equals(application.getBizCode())) {
                throw inconsistent();
            }
            DdcBizBO parent = parents.computeIfAbsent(application.getBizCode(),
                    this::requireParent);
            return converter.toApplication(application, parent);
        }).toList();
    }

    private DdcBizBO requireParent(@Nullable String bizCode) {
        if (bizCode == null) {
            throw inconsistent();
        }
        DdcBizBO parent = businesses.findByBizCode(bizCode).orElseThrow(DdcLogicalCatalogQueryService::inconsistent);
        if (!bizCode.equals(parent.getBizCode())) {
            throw inconsistent();
        }
        return parent;
    }

    @Nullable
    private static String optionalKeyword(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static DdcAdminException inconsistent() {
        return new DdcAdminException(DdcErrorStatus.INTERNAL_FAILURE);
    }
}
