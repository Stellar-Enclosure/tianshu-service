package top.egon.cola.component.tianshu.admin.service.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceEnvAppBindingBO;
import top.egon.cola.component.tianshu.admin.repository.DdcAiResourceRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceEnvAppBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceRepository;
import top.egon.cola.component.tianshu.admin.security.ai.DdcAiServiceCallerTestFixture;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.admin.service.ai.validation.DdcAiScopeValidator;
import top.egon.cola.component.tianshu.admin.service.ai.validation.DdcAiSystemActor;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcAiScopeValidatorAuthorityTest {

    private static final DdcAiScopeDTO SCOPE = new DdcAiScopeDTO("retail", "prod-space", "prod", "assistant-app");
    private static final long NAMESPACE_ID = 71L;

    private final DdcScopeGate scopeGate = mock(DdcScopeGate.class);
    private final DdcAiResourceRepository resourceRepository = mock(DdcAiResourceRepository.class);
    private final DdcNamespaceRepository namespaceRepository = mock(DdcNamespaceRepository.class);
    private final DdcNamespaceEnvAppBindingRepository bindingRepository = mock(DdcNamespaceEnvAppBindingRepository.class);
    private final DdcAiScopeValidator validator = new DdcAiScopeValidator(
            scopeGate, resourceRepository, namespaceRepository, bindingRepository, org.mockito.Mockito.mock(top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard.class));

    @BeforeEach
    void enabledScope() {
        SecurityContextHolder.clearContext();
        MDC.put("tenantId", "42");
        when(namespaceRepository.findByBizCodeAndNamespaceCode(SCOPE.bizCode(), SCOPE.namespaceCode()))
                .thenReturn(Optional.of(DdcNamespaceBO.builder().id(NAMESPACE_ID).publicId("ns-public")
                        .bizCode(SCOPE.bizCode()).namespaceCode(SCOPE.namespaceCode()).enabled(true).build()));
        when(bindingRepository.findAll(SCOPE.bizCode(), SCOPE.namespaceCode(), SCOPE.env(), SCOPE.appCode()))
                .thenReturn(List.of(DdcNamespaceEnvAppBindingBO.builder().enabled(true).build()));
    }

    @AfterEach
    void clear() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "READ,READ,true", "READ,REPORT,false",
            "REPORT,REPORT,true", "REPORT,READ,false",
            "WRITE,WRITE,true", "WRITE,PUBLISH,false",
            "PUBLISH,PUBLISH,true", "PUBLISH,WRITE,false"})
    void verifiedServiceRequiresTheMatchingApprovedOperation(String permission, String allowed, boolean permitted) {
        Runnable call = () -> invoke(permission);
        if (permitted) {
            assertThatCode(() -> withService(Operation.valueOf(allowed), SCOPE, call)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> withService(Operation.valueOf(allowed), SCOPE, call))
                    .isInstanceOfSatisfying(CommonException.class, failure -> assertThat(failure.getCode()).isEqualTo(56307));
        }
    }

    @Test
    void reportIsReadOnlyAndDoesNotLockScopeParents() {
        withService(Operation.REPORT, SCOPE, () -> validator.assertCanReport(resource()));

        verify(resourceRepository, never()).lockScopeParentsForShare(any());
    }

    @Test
    void writeStillLocksScopeParents() {
        withService(Operation.WRITE, SCOPE, () -> validator.assertCanWrite(resource()));

        verify(resourceRepository).lockScopeParentsForShare(SCOPE);
    }

    @Test
    void systemActorIsAdmittedOnlyInsideATenantContext() {
        assertThatCode(() -> DdcAiSystemActor.bind(DdcAiSystemActor.OPTIMIZER, () -> {
            validator.assertCanPublish(resource());
            return null;
        })).doesNotThrowAnyException();

        MDC.remove("tenantId");
        assertThatThrownBy(() -> DdcAiSystemActor.bind(DdcAiSystemActor.JUDGE, () -> {
            validator.assertCanRead(resource());
            return null;
        })).isInstanceOf(CommonException.class);
        assertThat(DdcAiSystemActor.current()).isEmpty();
    }

    @Test
    void exactPlacementCannotBeBroadenedByANamespaceOrOwnerPattern() {
        for (DdcAiScopeDTO allowed : List.of(
                new DdcAiScopeDTO("retail", "other-space", "prod", "assistant-app"),
                new DdcAiScopeDTO("retail", "prod-*", "prod", "assistant-app"),
                new DdcAiScopeDTO("retail", "prod-space", "prod", "other-owner"))) {
            assertThatThrownBy(() -> withService(Operation.REPORT, allowed, () -> validator.assertCanReport(resource())))
                    .isInstanceOf(CommonException.class);
        }
    }

    @Test
    void aTenantMdcAloneNeverAuthorizesAnyBusinessOperation() {
        for (String operation : List.of("READ", "WRITE", "PUBLISH", "REPORT")) {
            assertThatThrownBy(() -> invoke(operation)).isInstanceOf(CommonException.class);
        }
    }

    @Test
    void systemActorRejectsMalformedIdentity() {
        assertThatThrownBy(() -> DdcAiSystemActor.bind("user:alice", () -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void invoke(String permission) {
        Consumer<DdcAiResourceBO> action = switch (permission) {
            case "READ" -> validator::assertCanRead;
            case "REPORT" -> validator::assertCanReport;
            case "WRITE" -> validator::assertCanWrite;
            default -> validator::assertCanPublish;
        };
        action.accept(resource());
    }

    private static DdcAiResourceBO resource() {
        return DdcAiResourceBO.builder().bizCode(SCOPE.bizCode()).namespaceId(NAMESPACE_ID)
                .namespaceCode(SCOPE.namespaceCode()).envCode(SCOPE.env()).appCode(SCOPE.appCode()).build();
    }

    private static void withService(Operation allowed, DdcAiScopeDTO placement, Runnable action) {
        DdcAiServiceCallerTestFixture.call(allowed, placement, 42L, "runtime", "secret-v1",
                java.time.Instant.parse("2026-11-19T00:00:00Z"), () -> { action.run(); return null; });
    }
}
