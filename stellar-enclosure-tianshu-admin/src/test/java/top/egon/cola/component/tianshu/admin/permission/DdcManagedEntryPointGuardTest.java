package top.egon.cola.component.tianshu.admin.permission;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcAiResourceRpcProvider;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcConfigRpcProvider;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcManagementRpcProvider;
import top.egon.cola.component.tianshu.admin.rpc.provider.DdcRegistryRpcProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** The actual protocol adapters reject direct calls before any collaborator can be reached. */
class DdcManagedEntryPointGuardTest {
    static final Map<Class<?>, Set<String>> INTERNAL_RPC = Map.of(
            DdcRegistryRpcProvider.class, Set.of("registerService", "heartbeatService", "deregisterService", "getServiceInstances", "getServices"),
            DdcConfigRpcProvider.class, Set.of("registerConfigClient", "heartbeatConfigClient", "offlineConfigClient", "pullConfig", "acknowledgePublish"),
            DdcManagementRpcProvider.class, Set.of("getBiz", "listBizs", "getApp", "listApps", "findConfig", "upsertConfig", "deleteConfig",
                    "publishConfig", "getPublishTask", "retryPublishTask", "getConfigClients", "revokeResourceAdmission",
                    "getScopeBindings", "getServiceKeys", "getInstances"),
            DdcAiResourceRpcProvider.class, Set.of("getPrompt", "getSkill", "reportUsage", "getVersionHead", "replaceSkillDraft", "publishSkillDraft"));

    @Test
    void everyInternalRpcEntryRejectsAnAbsentAuthenticatedServicePrincipal() throws Exception {
        int covered = 0;
        for (var entry : INTERNAL_RPC.entrySet()) {
            Object provider = mock(entry.getKey(), CALLS_REAL_METHODS);
            Set<String> actual = new TreeSet<>();
            for (Method method : entry.getKey().getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) continue;
                actual.add(method.getName());
                assertThat(method.getParameterCount()).isEqualTo(1);
                Object request = method.getParameterTypes()[0].getMethod("getDefaultInstance").invoke(null);
                var invocation = assertThrows(InvocationTargetException.class, () -> method.invoke(provider, request));
                Throwable failure = invocation.getCause();
                if (failure instanceof StatusRuntimeException status) {
                    assertThat(status.getStatus().getCode()).isIn(Status.Code.UNAUTHENTICATED, Status.Code.PERMISSION_DENIED);
                } else {
                    assertThat(failure).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("principal");
                }
                covered++;
            }
            assertThat(actual).containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
        assertThat(covered).isEqualTo(31);
    }
}
