package top.egon.cola.component.tianshu.admin.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogContextDTO;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcOperationLogWebConfigurationTest {

    private final DdcOperationLogWebConfiguration configuration = new DdcOperationLogWebConfiguration();

    @AfterEach
    void resetContext() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void usesTrustedOriginAndMatchedPublicIdWithNoQueryCredentialsOrHeaders() {
        var request = bindRequest("/api/v1/tianshu/configs/23");
        request.setQueryString("token=private-value");
        request.addHeader("Host", "untrusted.example");
        request.addHeader("Forwarded", "host=evil.example");
        request.addHeader("X-Egon-Tianshu-Tenant", "99");
        request.addHeader("Authorization", "Bearer private-value");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "23"));

        OperationLogContextDTO context = provider("https://tianshu.example").get();

        assertThat(context.tenantId()).isEqualTo(20L);
        assertThat(context.actorId()).isEqualTo("user:alice");
        assertThat(context.requestUrl()).isEqualTo("https://tianshu.example/api/v1/tianshu/configs/23");
        assertThat(context.routePattern()).isEqualTo("/api/v1/tianshu/configs/{id}");
        assertThat(context.httpMethod()).isEqualTo("DELETE");
    }

    @Test
    void fallsBackToTemplateForUnapprovedPathAndPreservesServiceActorWithoutServletFields() {
        var request = bindRequest("/api/v1/tianshu/configs/private-value");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "private-value"));
        var provider = provider("https://tianshu.example");
        assertThat(provider.get().requestUrl()).isEqualTo("https://tianshu.example/api/v1/tianshu/configs/%7Bid%7D");

        RequestContextHolder.resetRequestAttributes();
        MDC.put("tenantId", "21");
        MDC.put("userId", "service:catalog");
        var service = provider.get();
        assertThat(service.tenantId()).isEqualTo(21L);
        assertThat(service.actorType()).isEqualTo("SERVICE");
        assertThat(service.requestUrl()).isNull();
        assertThat(service.httpMethod()).isNull();
    }

    @Test
    void rejectsMissingAuthorityAndInvalidOriginsWithoutIncludingCredentials() {
        bindRequest("/api/v1/tianshu/configs/23");
        MDC.remove("userId");
        assertThatThrownBy(() -> provider("https://tianshu.example").get())
                .hasMessage("TIANSHU_OPERATION_LOG_ACTOR_INVALID");
        assertThatThrownBy(() -> provider("https://name:password@evil.example"))
                .hasMessage("TIANSHU_OPERATION_LOG_ORIGIN_INVALID");
    }

    @Test
    void rejectsAdminCaptureWithoutTheConsumerTargetEnabled() {
        var captureOnly = new MockEnvironment().withProperty("egon.cola.component.mybatis-plus.operation-log.enabled", "true");
        assertThatThrownBy(() -> configuration.operationLogContextProvider(captureOnly))
                .hasMessage("TIANSHU_OPERATION_LOG_TARGET_NOT_READY");
    }

    private Supplier<OperationLogContextDTO> provider(String origin) {
        return configuration.operationLogContextProvider(new MockEnvironment()
                .withProperty("egon.cola.component.mybatis-plus.operation-log.enabled", "true")
                .withProperty("egon.cola.component.mybatis-plus.operation-log.target.enabled", "true")
                .withProperty("egon.cola.component.tianshu.admin.operation-log.http-origin", origin));
    }

    private MockHttpServletRequest bindRequest(String path) {
        MDC.put("tenantId", "20");
        MDC.put("userId", "user:alice");
        var request = new MockHttpServletRequest("DELETE", path);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/tianshu/configs/{id}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
