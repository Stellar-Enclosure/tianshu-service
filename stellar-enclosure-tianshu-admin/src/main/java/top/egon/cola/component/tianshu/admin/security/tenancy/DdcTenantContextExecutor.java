package top.egon.cola.component.tianshu.admin.security.tenancy;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.mybatis.extension.EgonColaTechnicalTenantScope;
import top.egon.cola.component.tianshu.admin.dao.DdcConfigBootBindingMapper;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
@Component("ddcTenantContextExecutor")
public class DdcTenantContextExecutor {

    public Scope bind(DdcTenantContextDTO context) {
        Objects.requireNonNull(context, "context");
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("tenantId", Long.toString(context.tenantId()));
            MDC.put("userId", context.actor());
        } catch (RuntimeException failure) {
            restore(previous);
            throw failure;
        }
        return () -> restore(previous);
    }

    public void run(DdcTenantContextDTO context, Runnable action) {
        Objects.requireNonNull(action, "action");
        try (Scope ignored = bind(context)) {
            action.run();
        }
    }

    public <T> T call(DdcTenantContextDTO context, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        try (Scope ignored = bind(context)) {
            return action.get();
        }
    }

    /** The only fixed-zero entry here; it cannot select an arbitrary Mapper or replace the caller's actor. */
    public <T> T withinConfigBootBinding(DdcTenantContextDTO trustedContext, Supplier<T> action) {
        Objects.requireNonNull(trustedContext, "trustedContext");
        Objects.requireNonNull(action, "action");
        if (!Long.toString(trustedContext.tenantId()).equals(MDC.get("tenantId"))
                || !trustedContext.actor().equals(MDC.get("userId"))) {
            throw new IllegalStateException("TENANT_CONTEXT_MISMATCH");
        }
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("tenantId", "0");
            return EgonColaTechnicalTenantScope.execute(DdcConfigBootBindingMapper.class, action);
        } finally { restore(previous); }
    }

    private void restore(Map<String, String> previous) {
        if (previous == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
