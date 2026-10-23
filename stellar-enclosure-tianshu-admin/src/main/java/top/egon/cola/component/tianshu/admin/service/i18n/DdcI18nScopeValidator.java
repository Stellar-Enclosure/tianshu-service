package top.egon.cola.component.tianshu.admin.service.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.repository.DdcI18nPageRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceEnvAppBindingRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;

@Component("ddcI18nScopeValidator")
@RequiredArgsConstructor
public class DdcI18nScopeValidator {
    @Qualifier("ddcScopeGate") private final DdcScopeGate scopeGate;
    @Qualifier("ddcI18nPageRepository") private final DdcI18nPageRepository repository;
    @Qualifier("ddcNamespaceRepository") private final DdcNamespaceRepository namespaces;
    @Qualifier("ddcNamespaceEnvAppBindingRepository") private final DdcNamespaceEnvAppBindingRepository bindings;

    public Long require(DdcI18nScope scope, DdcAdminCapability capability) {
        requirePermission(capability);
        validateScope(scope);
        if (capability == DdcAdminCapability.WRITE || capability == DdcAdminCapability.PUBLISH) {
            repository.lockScope(scope);
        }
        scopeGate.assertEnabledFresh(scope.bizCode(), scope.appCode(), scope.env(), scope.namespaceCode());
        var namespace = namespaces.findByBizCodeAndNamespaceCode(scope.bizCode(), scope.namespaceCode())
                .orElseThrow(DdcI18nScopeValidator::missingScope);
        if (bindings.findAll(scope.bizCode(), scope.namespaceCode(), scope.env(), scope.appCode()).stream()
                .noneMatch(binding -> Boolean.TRUE.equals(binding.getEnabled()))) throw missingScope();
        return namespace.getId();
    }

    public void requirePermission(DdcAdminCapability capability) {
        if (!hasPermission(capability)) {
            throw new CommonException(56407, "TIANSHU_I18N_SCOPE_FORBIDDEN", "Page language permission is required");
        }
        EgonColaTenantIdProvider.currentTenantId();
    }

    public boolean hasPermission(DdcAdminCapability capability) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Rbac3UserDetails
                && (authorized(authentication, capability) || capability == DdcAdminCapability.I18N_READ
                && authorized(authentication, DdcAdminCapability.READ));
    }

    private boolean authorized(Authentication authentication, DdcAdminCapability capability) {
        return authentication.getAuthorities().stream().anyMatch(a -> ("SCOPE_TIANSHU_" + capability.name()).equals(a.getAuthority()))
                && authentication.getAuthorities().stream().anyMatch(a -> capability.authority().equals(a.getAuthority())
                || DdcAdminCapability.ALL.authority().equals(a.getAuthority()));
    }

    private void validateScope(DdcI18nScope scope) {
        if (scope == null) throw DdcI18nValidation.invalid("Scope is required");
        check(scope.bizCode(), 128);
        check(scope.namespaceCode(), 128);
        check(scope.env(), 32);
        check(scope.appCode(), 128);
    }

    private void check(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw DdcI18nValidation.invalid("Invalid scope");
        }
    }

    private static CommonException missingScope() {
        return new CommonException(56406, "TIANSHU_I18N_SCOPE_NOT_FOUND", "Page language scope is unavailable");
    }
}
