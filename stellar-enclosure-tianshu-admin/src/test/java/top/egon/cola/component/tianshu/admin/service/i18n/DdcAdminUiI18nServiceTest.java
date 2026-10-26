package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.config.DdcAdminUiI18nProperties;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcAdminUiI18nServiceTest {
    private final DdcAdminUiI18nProperties properties = new DdcAdminUiI18nProperties();
    private final DdcAdminUiI18nCatalog catalog = mock(DdcAdminUiI18nCatalog.class);
    private final DdcI18nPageRepository repository = mock(DdcI18nPageRepository.class);
    private final DdcI18nScopeValidator scopes = mock(DdcI18nScopeValidator.class);
    private final DdcI18nPageService pages = mock(DdcI18nPageService.class);
    private final DdcAdminUiI18nService service = new DdcAdminUiI18nService(properties, catalog, repository, scopes, new DdcI18nValidation(), pages);

    @Test void managementBundleUsesTheSameExplicitActivationPath() {
        var bundle = new DdcI18nBundleResult("tianshu.bizs", "en-US", "zh-CN", "version", Map.of("title", "Source"));
        when(pages.bundle(properties.scope(), "501", "9", "tianshu.bizs", "en-US")).thenReturn(bundle);
        assertThat(service.bundle("501", "9", "tianshu.bizs", "en-US")).isSameAs(bundle);
        verify(scopes).requirePermission(DdcAdminCapability.READ); verifyNoInteractions(catalog, repository);
    }

    @Test void legacyInitializationNeverCreatesOrPublishesCodeDefaults() {
        assertThatThrownBy(service::initialize).isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56433));
        verifyNoInteractions(pages, repository, catalog);
        assertThatThrownBy(() -> service.bundle("tianshu.bizs", "en-US")).isInstanceOf(CommonException.class);
    }

    @Test void bootstrapDoesNotAdvertiseTheRemovedInitializationAction() {
        when(catalog.pages()).thenReturn(List.of());
        assertThat(service.bootstrap().canInitialize()).isFalse();
        verify(pages, never()).create(any()); verify(pages, never()).publish(anyLong(), any());
    }

    @Test void deniedManagementPermissionPreventsAnyBundleRead() {
        doThrow(new CommonException(56407, "denied", "denied")).when(scopes).requirePermission(DdcAdminCapability.READ);
        assertThatThrownBy(() -> service.bundle("501", "9", "tianshu.bizs", "en-US")).isInstanceOf(CommonException.class);
        verifyNoInteractions(pages, repository, catalog);
    }
}
