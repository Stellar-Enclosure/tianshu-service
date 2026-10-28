package top.egon.cola.component.tianshu.admin.service.list;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.DataScopeDecision;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;

/** Namespace rows have no organization ownership mapping; narrower rules must not be silently ignored. */
@Component("ddcNamespaceDataScopeGuard")
@RequiredArgsConstructor
public class DdcNamespaceDataScopeGuard {
    private final CurrentRbac3User users;

    public void requirePage() { require(users.require().snapshot(), false); }

    public void require(SystemAuthorizationSnapshot snapshot, boolean exporting) {
        check(snapshot.dataScopes().get(DdcPermissionCodes.READ));
        if (exporting) check(snapshot.dataScopes().get(DdcPermissionCodes.NAMESPACE_EXPORT));
    }

    private void check(DataScopeDecision scope) {
        if (scope != null && (scope.decision() != Decision.ALLOW || !scope.allInTenant())) {
            throw new AccessDeniedException("NAMESPACE_DATA_SCOPE_UNSUPPORTED");
        }
    }
}
