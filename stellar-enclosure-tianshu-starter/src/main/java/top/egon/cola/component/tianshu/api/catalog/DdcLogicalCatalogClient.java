package top.egon.cola.component.tianshu.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped logical metadata queries for authenticated platform clients. */
public interface DdcLogicalCatalogClient {

    Optional<DdcManagementBiz> findBusiness(@NotBlank @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String id);

    List<DdcManagementBiz> listBusinesses(@Nullable @Size(max = DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH) String keyword);

    Optional<DdcManagementApp> findApplication(@NotBlank @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String id);

    List<DdcManagementApp> listApplications(@Nullable @Size(max = DdcLogicalCatalogContract.MAX_ID_LENGTH) String businessId,
            @Nullable @Size(max = DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH) String keyword);
}
