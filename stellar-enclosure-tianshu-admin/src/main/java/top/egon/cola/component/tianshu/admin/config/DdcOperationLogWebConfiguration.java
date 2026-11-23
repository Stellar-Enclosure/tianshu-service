package top.egon.cola.component.tianshu.admin.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogContextDTO;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.component.tianshu.admin.outbox.DdcOperationLogOutboxAdapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Request context already guarded by the authenticated Tianshu tenant boundary. */
@Configuration(value = "ddcOperationLogWebConfiguration", proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({DdcOperationLogOutboxAdapter.ProducerConfiguration.class,
        DdcOperationLogOutboxAdapter.AdminConsumptionConfiguration.class})
public class DdcOperationLogWebConfiguration {

    private static final String ORIGIN = "egon.cola.component.tianshu.admin.operation-log.http-origin";
    private static final String TARGET_ENABLED = "egon.cola.component.mybatis-plus.operation-log.target.enabled";
    private static final String ENABLED = "egon.cola.component.mybatis-plus.operation-log.enabled";
    private static final Pattern SAFE_TEMPLATE = Pattern.compile("(/[A-Za-z0-9._~-]+|/\\{[A-Za-z][A-Za-z0-9]*})+/?");
    private static final Pattern PUBLIC_PREFIX = Pattern.compile("(/[A-Za-z0-9._~-]+)*/?");
    private static final Pattern PUBLIC_ID = Pattern.compile("[1-9][0-9]{0,18}|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final Set<String> PUBLIC_PATH_KEYS = Set.of("id", "eventId", "pageId", "configId", "namespaceId", "templateId");

    @Bean("operationLogContextProvider")
    Supplier<OperationLogContextDTO> operationLogContextProvider(Environment environment) {
        if (!environment.getProperty(ENABLED, Boolean.class, false)) {
            return () -> snapshot(null);
        }
        if (!environment.getProperty(TARGET_ENABLED, Boolean.class, false)) {
            throw new IllegalStateException("TIANSHU_OPERATION_LOG_TARGET_NOT_READY");
        }
        URI origin = trustedOrigin(environment.getRequiredProperty(ORIGIN));
        return () -> snapshot(origin);
    }

    private static OperationLogContextDTO snapshot(URI origin) {
        Long tenantId = EgonColaTenantIdProvider.currentTenantId();
        String actor = MDC.get("userId");
        String actorType = actorType(actor);
        String trace = TraceContext.current().map(TraceContext::traceId)
                .filter(value -> value.matches("[A-Za-z0-9._-]{1,64}")).orElse(null);
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new OperationLogContextDTO(tenantId, actorType, actor, null, "JOB",
                    null, null, null, null, trace, null);
        }

        HttpServletRequest request = attributes.getRequest();
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pattern = route == null ? null : route.toString();
        if (origin == null || pattern == null || pattern.length() > 512 || !SAFE_TEMPLATE.matcher(pattern).matches()) {
            throw new IllegalStateException("TIANSHU_OPERATION_LOG_ROUTE_INVALID");
        }
        String path = reviewedPath(request, pattern);
        return new OperationLogContextDTO(tenantId, actorType, actor, null, "HTTP",
                request.getMethod(), publicUrl(origin, path), pattern,
                null, trace, null);
    }

    private static String reviewedPath(HttpServletRequest request, String pattern) {
        String path = pattern;
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
            for (String key : PUBLIC_PATH_KEYS) {
                if (variables.get(key) instanceof String id && PUBLIC_ID.matcher(id).matches()) {
                    path = path.replace("{" + key + "}", id);
                }
            }
        }
        return !path.contains("{") && request.getRequestURI().equals(request.getContextPath() + path) ? path : pattern;
    }

    private static URI trustedOrigin(String configured) {
        URI origin;
        try {
            origin = URI.create(configured);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("TIANSHU_OPERATION_LOG_ORIGIN_INVALID");
        }
        if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                || origin.getHost() == null || origin.getUserInfo() != null
                || origin.getQuery() != null || origin.getFragment() != null
                || !PUBLIC_PREFIX.matcher(origin.getRawPath()).matches()
                || !origin.normalize().getRawPath().equals(origin.getRawPath())) {
            throw new IllegalArgumentException("TIANSHU_OPERATION_LOG_ORIGIN_INVALID");
        }
        return origin;
    }

    private static String publicUrl(URI origin, String path) {
        String prefix = origin.getPath();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        try {
            return new URI(origin.getScheme(), null, origin.getHost(), origin.getPort(), prefix + path, null, null).toASCIIString();
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("TIANSHU_OPERATION_LOG_ROUTE_INVALID", failure);
        }
    }

    private static String actorType(String actor) {
        if (actor == null || actor.indexOf(':') < 0 || actor.indexOf(':') == actor.length() - 1 || actor.length() > 512
                || actor.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("TIANSHU_OPERATION_LOG_ACTOR_INVALID");
        }
        return switch (actor.substring(0, actor.indexOf(':'))) {
            case "user" -> "USER";
            case "service" -> "SERVICE";
            case "system" -> "SYSTEM";
            default -> throw new IllegalStateException("TIANSHU_OPERATION_LOG_ACTOR_INVALID");
        };
    }
}
