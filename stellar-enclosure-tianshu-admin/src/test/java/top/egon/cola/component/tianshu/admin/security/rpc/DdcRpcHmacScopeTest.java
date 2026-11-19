package top.egon.cola.component.tianshu.admin.security.rpc;

import com.google.protobuf.Empty;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.rpc.tianshu.contract.proto.v1.AiResourceQuery;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcRpcHmacScopeTest {

    private final DdcRpcScopeExtractor extractor =
            new DdcRpcScopeExtractor();

    @Test
    void rejectsRetiredRpcMethodsAtTheScopeBoundary() {
        for (String method : List.of(
                "egon.tianshu.v1.DdcConfigRuntimeService/PullConfig",
                "egon.tianshu.v1.DdcServiceRegistryService/RegisterService",
                "egon.tianshu.v1.DdcManagementService/FindConfig",
                "egon.tianshu.v1.DdcManagementService/PublishConfig",
                "egon.tianshu.v1.DdcManagementService/GetServiceKeys",
                "egon.tianshu.v1.DdcManagementService/GetBiz",
                "egon.tianshu.v1.Unknown/Call")) {
            assertThatThrownBy(() -> extractor.extract(method, Empty.getDefaultInstance()))
                    .as(method).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unknown Tianshu RPC method");
        }
    }

    @Test
    void extractsAiNamespaceScopeAndRejectsAnUnstableVersionSelector() {
        AiResourceQuery fixedVersion = AiResourceQuery.newBuilder()
                .setBizCode("biz-a")
                .setNamespaceCode("tenant-space")
                .setEnv("dev")
                .setAppCode("order-api")
                .setResourceKey("review-order")
                .setResourceId("41")
                .setVersionNo(3L)
                .build();

        assertThat(extractor.extract(
                "egon.tianshu.v1.DdcAiResourceService/GetSkill",
                fixedVersion)).isEqualTo(new DdcRpcScopeExtractor.Scope(
                        "SDK", "order-api", "dev", "biz-a", "tenant-space"));
        assertThatThrownBy(() -> extractor.extract(
                "egon.tianshu.v1.DdcAiResourceService/GetPrompt",
                fixedVersion.toBuilder().clearResourceId().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resourceId");
    }

    @Test
    void servicePrincipalNeverTrustsRequestedOperatorIdentity() {
        DdcServicePrincipal principal = new DdcServicePrincipal(
                "management-a",
                "MANAGEMENT",
                Set.of("app-a"),
                Set.of("dev"),
                Set.of("biz-a"),
                Set.of("MANAGEMENT_CONFIG_WRITE"),
                "app-a",
                "dev",
                "biz-a",
                101L,
                "tenant-a",
                Set.of("orders-*")
        );

        assertThat(principal.auditOperator("claimed-user"))
                .isEqualTo("service:management-a [requested=claimed-user]");
        assertThat(principal.tenantContext().tenantId()).isEqualTo(101L);
        assertThat(principal.namespacePatterns()).contains("orders-*");
        assertThat(principal.auditOperator(null))
                .isEqualTo("service:management-a");
        assertThat(principal.toString()).doesNotContain("secret");

        DdcHmacCredential credential = new DdcHmacCredential(
                "management-a", "access-a", "credential-secret",
                "MANAGEMENT", Set.of("*"), Set.of("*"), Set.of("*"),
                Set.of("*"));
        assertThat(credential.toString())
                .contains("secret=<redacted>")
                .doesNotContain("credential-secret");
    }
}
