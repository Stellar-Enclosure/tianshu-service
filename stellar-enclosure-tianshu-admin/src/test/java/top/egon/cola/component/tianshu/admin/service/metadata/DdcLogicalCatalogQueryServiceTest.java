package top.egon.cola.component.tianshu.admin.service.metadata;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAppBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcBizBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcLogicalCatalogConverter;
import top.egon.cola.component.tianshu.admin.repository.DdcAppRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcBizRepository;
import top.egon.cola.component.tianshu.api.catalog.DdcLogicalCatalogContract;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DdcLogicalCatalogQueryServiceTest {

    @Mock
    private DdcBizRepository businesses;
    @Mock
    private DdcAppRepository applications;

    private DdcLogicalCatalogQueryService service;

    @BeforeEach
    void setUp() {
        service = new DdcLogicalCatalogQueryService(businesses, applications,
                Mappers.getMapper(DdcLogicalCatalogConverter.class));
    }

    @Test
    void exposesDisabledParentAndExactPublicIdentityWithoutLegacyFacade() {
        when(applications.findByPublicId("app-1")).thenReturn(Optional.of(application("app-1", "orders", true)));
        when(businesses.findByBizCode("orders")).thenReturn(Optional.of(business("biz-1", "orders", false)));

        assertThat(service.findApplication("app-1")).contains(
                new DdcManagementApp("app-1", "biz-1", "orders", "console", "Console", true, false));
        verify(applications).findByPublicId("app-1");
    }

    @Test
    void businessLookupUsesExactOpaquePublicIdAndKeepsDisabledState() {
        when(businesses.findByPublicId(" legacy-id "))
                .thenReturn(Optional.of(business(" legacy-id ", "orders", false)));

        assertThat(service.findBusiness(" legacy-id "))
                .contains(new DdcManagementBiz(" legacy-id ", "orders", "Orders", false));
        assertThat(service.findBusiness("missing")).isEmpty();
        verify(businesses).findByPublicId(" legacy-id ");
        verify(businesses, never()).findByBizCode(anyString());
        verifyNoInteractions(applications);
    }

    @Test
    void fullBusinessListHasNoImplicitPageOrEnabledFilter() {
        var rows = IntStream.range(0, 257).mapToObj(index -> business("biz-" + index, "orders-" + index,
                index % 2 == 0)).toList();
        when(businesses.list("order", null)).thenReturn(rows);

        var result = service.listBusinesses("  order  ");

        assertThat(result).hasSize(257);
        assertThat(result.get(1).enabled()).isFalse();
        assertThat(result.getLast().id()).isEqualTo("biz-256");
        verify(businesses).list("order", null);
        verifyNoInteractions(applications);
    }

    @Test
    void applicationListUsesPublicBusinessIdAndIncludesDisabledChildrenAndParent() {
        when(businesses.findByPublicId("biz-1")).thenReturn(Optional.of(business("biz-1", "orders", false)));
        when(applications.list("orders", "console", null)).thenReturn(List.of(
                application("app-1", "orders", false), application("app-2", "orders", true)));

        assertThat(service.listApplications("biz-1", " console ")).containsExactly(
                new DdcManagementApp("app-1", "biz-1", "orders", "console", "Console", false, false),
                new DdcManagementApp("app-2", "biz-1", "orders", "console", "Console", true, false));
        verify(applications).list("orders", "console", null);
        verify(businesses, never()).findByBizCode(anyString());
    }

    @Test
    void unfilteredApplicationListPreservesEachParentsOwnIdentityAndStatus() {
        when(applications.list(null, null, null)).thenReturn(List.of(
                application("app-1", "orders", true), application("app-2", "billing", false),
                application("app-3", "orders", false)));
        when(businesses.findByBizCode("orders")).thenReturn(Optional.of(business("biz-1", "orders", false)));
        when(businesses.findByBizCode("billing")).thenReturn(Optional.of(business("biz-2", "billing", true)));

        assertThat(service.listApplications(null, " ")).containsExactly(
                new DdcManagementApp("app-1", "biz-1", "orders", "console", "Console", true, false),
                new DdcManagementApp("app-2", "biz-2", "billing", "console", "Console", false, true),
                new DdcManagementApp("app-3", "biz-1", "orders", "console", "Console", false, false));
        verify(businesses).findByBizCode("orders");
        verify(businesses).findByBizCode("billing");
    }

    @Test
    void genuinelyMissingApplicationOrSelectedBusinessStaysAbsent() {
        assertThat(service.findApplication("missing-app")).isEmpty();
        assertThat(service.listApplications("missing-biz", null)).isEmpty();
        verify(applications).findByPublicId("missing-app");
        verify(businesses).findByPublicId("missing-biz");
    }

    @Test
    void repositoryFailureIsPropagatedRatherThanConvertedIntoNotFound() {
        var unavailable = new TransientDataAccessResourceException("synthetic-unavailable");
        when(businesses.list(null, null)).thenThrow(unavailable);
        assertThatThrownBy(() -> service.listBusinesses(null)).isSameAs(unavailable);

        var domainFailure = new DdcAdminException(DdcErrorStatus.INTERNAL_FAILURE);
        when(applications.findByPublicId("app-1")).thenReturn(Optional.of(application("app-1", "orders", true)));
        when(businesses.findByBizCode("orders")).thenThrow(domainFailure);
        assertThatThrownBy(() -> service.findApplication("app-1")).isSameAs(domainFailure);
    }

    @Test
    void existingApplicationCannotFabricateMissingOrMismatchedBusinessOwnership() {
        when(applications.findByPublicId("app-1")).thenReturn(Optional.of(application("app-1", "orders", true)));
        assertThatThrownBy(() -> service.findApplication("app-1"))
                .isInstanceOfSatisfying(DdcAdminException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(DdcErrorStatus.INTERNAL_FAILURE.getStatus());
                    assertThat(failure.getMessage()).doesNotContain("app-1", "orders");
                });

        when(businesses.findByPublicId("biz-1")).thenReturn(Optional.of(business("biz-1", "orders", false)));
        when(applications.list("orders", null, null)).thenReturn(List.of(application("app-2", "billing", true)));
        assertThatThrownBy(() -> service.listApplications("biz-1", null))
                .isInstanceOfSatisfying(DdcAdminException.class, failure ->
                        assertThat(failure.getStatus()).isEqualTo(DdcErrorStatus.INTERNAL_FAILURE.getStatus()));
    }

    @Test
    void springValidationAndNamedConstructorInjectionAreRealBoundaries() throws Exception {
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var factory = new ProxyFactory(service);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new MethodValidationInterceptor(validation.getValidator()));
            var validated = (DdcLogicalCatalogQueryService) factory.getProxy();

            assertThatThrownBy(() -> validated.findBusiness(" ")).isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> validated.findApplication("x".repeat(DdcLogicalCatalogContract.MAX_ID_LENGTH + 1)))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> validated.listApplications("x".repeat(DdcLogicalCatalogContract.MAX_ID_LENGTH + 1), null))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> validated.listBusinesses("x".repeat(DdcLogicalCatalogContract.MAX_KEYWORD_LENGTH + 1)))
                    .isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(businesses, applications);
        }
        var constructor = DdcLogicalCatalogQueryService.class.getConstructor(
                DdcBizRepository.class, DdcAppRepository.class, DdcLogicalCatalogConverter.class);
        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
        assertThat(List.of(constructor.getParameters()).stream().map(parameter ->
                parameter.getAnnotation(Qualifier.class).value()).toList()).containsExactly(
                "ddcBizRepository", "ddcAppRepository", "ddcLogicalCatalogConverterImpl");
        assertThat(DdcLogicalCatalogQueryService.class.getAnnotation(Service.class).value())
                .isEqualTo("ddcLogicalCatalogQueryService");
    }

    private static DdcBizBO business(String id, String bizCode, boolean enabled) {
        return DdcBizBO.builder().publicId(id).bizCode(bizCode).bizName("Orders").enabled(enabled).build();
    }

    private static DdcAppBO application(String id, String bizCode, boolean enabled) {
        return DdcAppBO.builder().publicId(id).bizCode(bizCode).appCode("console").appName("Console")
                .enabled(enabled).build();
    }
}
