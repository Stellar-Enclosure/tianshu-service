package top.egon.cola.component.tianshu.admin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigQueryRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishTaskQueryRequest;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionJsonMapperFactory;
import top.egon.cola.platform.tianquan.jianshen.starter.field.SubmittedFieldSet;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionContext;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps compiled field policies at the HTTP boundary, including Spring's query-object binder. */
@Configuration(proxyBeanMethods = false)
@lombok.RequiredArgsConstructor
public class DdcPermissionWebConfiguration implements WebMvcConfigurer {
    private static final Map<Class<?>, Set<String>> QUERY_BINDINGS = Map.of(
            DdcConfigQueryRequest.class, Set.of("bizCode", "namespaceCode", "env", "appCode", "includeDeleted"),
            DdcPublishTaskQueryRequest.class, Set.of("bizCode", "env", "appCode", "status", "changeId"));

    private final PermissionJsonMapperFactory permissionFields;
    private final PermissionExecutionGuard execution;
    private final PermissionFieldBindingGuard binding;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (var converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setObjectMapper(permissionFields.forTransport(jackson.getObjectMapper()));
            }
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod method)) return true;
                boolean paged = java.util.Arrays.stream(method.getMethodParameters())
                        .anyMatch(parameter -> parameter.getParameterType() == PageQuery.class);
                for (var parameter : method.getMethodParameters()) {
                    Set<String> writable = QUERY_BINDINGS.get(parameter.getParameterType());
                    if (writable == null) continue;
                    execution.require(PermissionExecutionContext.currentApiKey());
                    var supplied = new LinkedHashSet<>(request.getParameterMap().keySet());
                    if (paged) supplied.removeAll(Set.of("pageNo", "pageSize"));
                    binding.require(parameter.getParameterType(), new SubmittedFieldSet(supplied), writable);
                }
                return true;
            }
        });
    }
}
