package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceEnvAppBindingBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceEnvAppBindingRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcI18nScopeValidatorTest {
    private static final DdcI18nScope SCOPE = new DdcI18nScope("retail", "main", "prod", "shop");
    private final DdcScopeGate gate = mock(DdcScopeGate.class);
    private final DdcI18nPageRepository pages = mock(DdcI18nPageRepository.class);
    private final DdcNamespaceRepository namespaces = mock(DdcNamespaceRepository.class);
    private final DdcNamespaceEnvAppBindingRepository bindings = mock(DdcNamespaceEnvAppBindingRepository.class);
    private final org.springframework.transaction.support.TransactionTemplate transaction = new org.springframework.transaction.support.TransactionTemplate(new DdcMetadataTransactionTestFixture());
    private final DdcI18nScopeValidator validator = new DdcI18nScopeValidator(gate, pages, namespaces, bindings, org.mockito.Mockito.mock(top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard.class));

    @BeforeEach void setup() {
        MDC.put("tenantId", "101");
        var controller = top.egon.cola.component.tianshu.admin.controller.i18n.DdcI18nPageController.class;
        var method = Arrays.stream(controller.getDeclaredMethods()).filter(value -> value.getName().equals("bundle")).findFirst().orElseThrow();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new org.springframework.web.method.HandlerMethod(mock(controller), method));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        when(namespaces.findByBizCodeAndNamespaceCode("retail", "main"))
                .thenReturn(Optional.of(DdcNamespaceBO.builder().id(7L).enabled(true).build()));
        when(bindings.findAll("retail", "main", "prod", "shop"))
                .thenReturn(List.of(DdcNamespaceEnvAppBindingBO.builder().enabled(true).build()));
    }
    @AfterEach void cleanup() { MDC.clear(); SecurityContextHolder.clearContext(); org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

    @Test void independentConsumerRequiresScopeAndCapabilityAndCannotReadDrafts() {
        authentication("SCOPE_TIANSHU_I18N_READ", "CAP_TIANSHU_I18N_READ");
        assertThat(validator.require(SCOPE, DdcAdminCapability.I18N_READ)).isEqualTo(7L);
        assertThatThrownBy(() -> validator.require(SCOPE, DdcAdminCapability.READ)).isInstanceOf(CommonException.class);
        authentication("CAP_TIANSHU_I18N_READ");
        assertThatThrownBy(() -> validator.require(SCOPE, DdcAdminCapability.I18N_READ)).isInstanceOf(CommonException.class);
        authentication("SCOPE_TIANSHU_I18N_READ");
        assertThatThrownBy(() -> validator.require(SCOPE, DdcAdminCapability.I18N_READ)).isInstanceOf(CommonException.class);
    }

    @Test void existingAdminReaderCanConsumeAndPublisherLocksScope() {
        authentication("SCOPE_TIANSHU_READ", "CAP_TIANSHU_READ");
        assertThat(validator.require(SCOPE, DdcAdminCapability.I18N_READ)).isEqualTo(7L);
        verifyNoInteractions(pages);
        verify(gate).assertEnabledFresh("retail", "shop", "prod", "main");
        verify(gate, never()).assertEnabled(anyString(), anyString(), anyString(), anyString());
        verify(gate, never()).invalidate(anyString());
        authentication("SCOPE_TIANSHU_PUBLISH", "CAP_TIANSHU_PUBLISH");
        transaction.execute(status -> validator.require(SCOPE, DdcAdminCapability.PUBLISH));
        verify(pages).lockScope(SCOPE);
    }

    @ParameterizedTest
    @EnumSource(value = DdcAdminCapability.class, names = {"WRITE", "PUBLISH"})
    void writesLockParentsBeforeCheckingFreshEnabledState(DdcAdminCapability capability) {
        authentication("SCOPE_TIANSHU_" + capability.name(), capability.authority());

        transaction.execute(status -> validator.require(SCOPE, capability));

        var ordered = inOrder(pages, gate, namespaces, bindings);
        ordered.verify(pages).lockScope(SCOPE);
        ordered.verify(gate).assertEnabledFresh("retail", "shop", "prod", "main");
        ordered.verify(namespaces).findByBizCodeAndNamespaceCode("retail", "main");
        ordered.verify(bindings).findAll("retail", "main", "prod", "shop");
        verify(gate, never()).assertEnabled(anyString(), anyString(), anyString(), anyString());
        verify(gate, never()).invalidate(anyString());
    }

    @Test void freshScopeRejectionStopsPublicationScopeResolution() {
        authentication("SCOPE_TIANSHU_PUBLISH", "CAP_TIANSHU_PUBLISH");
        doThrow(new CommonException(top.egon.cola.component.tianshu.error.DdcErrorStatus.SCOPE_DISABLED))
                .when(gate).assertEnabledFresh("retail", "shop", "prod", "main");

        assertThatThrownBy(() -> transaction.execute(status -> validator.require(SCOPE, DdcAdminCapability.PUBLISH)))
                .isInstanceOf(CommonException.class);
        verify(pages).lockScope(SCOPE);
        verifyNoInteractions(namespaces, bindings);
    }

    @Test void missingTenantBindingOrDisabledScopeCannotBeConsumed() {
        authentication("SCOPE_TIANSHU_READ", "CAP_TIANSHU_READ");
        when(bindings.findAll("retail", "main", "prod", "shop")).thenReturn(List.of());
        assertThatThrownBy(() -> validator.require(SCOPE, DdcAdminCapability.I18N_READ)).isInstanceOf(CommonException.class);
        MDC.remove("tenantId");
        assertThatThrownBy(() -> validator.require(SCOPE, DdcAdminCapability.I18N_READ)).isInstanceOf(IllegalStateException.class);
    }

    @Test void preflightChecksFreshFactsWithoutTakingAnAutocommitWriteLock() {
        authentication("SCOPE_TIANSHU_WRITE", "CAP_TIANSHU_WRITE");
        assertThat(validator.require(SCOPE, DdcAdminCapability.WRITE)).isEqualTo(7L);
        verifyNoInteractions(pages);
        verify(gate).assertEnabledFresh("retail", "shop", "prod", "main");
    }

    private void authentication(String... authorities) {
        var user = mock(Rbac3UserDetails.class);
        when(user.hasPermission(anyString())).thenAnswer(call -> Arrays.asList(authorities).contains("CAP_" + call.getArgument(0)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, "", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
