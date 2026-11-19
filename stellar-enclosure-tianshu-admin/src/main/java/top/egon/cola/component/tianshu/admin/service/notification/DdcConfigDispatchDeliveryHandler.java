package top.egon.cola.component.tianshu.admin.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.outbox.common.exception.OutboxValidationException;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutRequestedEvent;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.publish.DdcNativeConfigRolloutService;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Only native schema is executable. Old config-dispatch/DLQ records remain archived. */
@Slf4j
@Component("ddcConfigDispatchDeliveryHandler")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigDispatchDeliveryHandler implements DeliveryHandler {
    @Qualifier("jacksonObjectMapper") private final ObjectMapper json;
    @Qualifier("egonColaValidationUtils") private final ValidationUtils validation;
    @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;
    @Qualifier("ddcTenantContextExecutor") private final DdcTenantContextExecutor contexts;
    @Qualifier("ddcNativeConfigRolloutService") private final ObjectProvider<DdcNativeConfigRolloutService> rollouts;
    @Qualifier("ddcConfigRolloutOutboxRoute") private final DdcConfigRolloutOutboxRouteDTO route;

    @Override public String channel() { return route.channel(); }
    @Override public void validateDestination(String destination) { route.validateDestination(destination); }

    @Override public DeliveryResult deliver(DeliveryContext context) {
        final DdcConfigRolloutRequestedEvent event;
        try { event = parse(context); }
        catch (java.io.IOException | ConstraintViolationException | IllegalArgumentException | OutboxValidationException invalid) {
            return DeliveryResult.permanentFailure("TIANSHU_CONFIG_EVENT_INVALID", "invalid native config event");
        }
        final DdcTenantContextDTO trusted;
        try {
            var binding = tenants.requireEnabledTenant(Long.parseLong(event.getTenantId()));
            trusted = new DdcTenantContextDTO(binding.getTenantId(), binding.getIdentityTenantId(), "system:outbox:" + event.getEventId());
        } catch (IllegalArgumentException invalid) {
            return DeliveryResult.permanentFailure("TIANSHU_CONFIG_TENANT_INVALID", "tenant is not enabled");
        }
        try {
            boolean handled = contexts.call(trusted, () -> rollouts.getObject().reconcileNative(event.getChangeId(),
                    Long.parseLong(event.getConfigId()), Long.parseLong(event.getRevision()), event.getAttempt(), event.getEventId()));
            return handled ? DeliveryResult.success() : DeliveryResult.permanentFailure("TIANSHU_CONFIG_ATTEMPT_STALE", "native task is no longer current");
        } catch (RuntimeException unavailable) {
            log.warn("Native configuration reconciliation is unavailable; eventId={}, failureType={}",
                    event.getEventId(), unavailable.getClass().getSimpleName());
            return DeliveryResult.retryableFailure("TIANSHU_CONFIG_ROLLOUT_RETRY", "native configuration reconciliation unavailable");
        }
    }

    private DdcConfigRolloutRequestedEvent parse(DeliveryContext context) throws java.io.IOException {
        if (context == null || !channel().equals(context.channel()) || !"application/json".equals(context.contentType())
                || !route.schemaVersion().equals(context.schemaVersion()) || context.payload() == null
                || context.payload().getBytes(StandardCharsets.UTF_8).length > 16384) { throw invalid(); }
        route.validateDestination(context.destination());
        var tree = DdcConfigCanonicalJson.parse(context.payload().getBytes(StandardCharsets.UTF_8));
        if (!tree.isObject() || tree.size() != 7) { throw invalid(); }
        for (String field : java.util.List.of("schemaVersion", "eventId", "tenantId", "configId", "revision", "changeId")) {
            if (!tree.path(field).isTextual()) { throw invalid(); }
        }
        if (!tree.path("attempt").isIntegralNumber() || !tree.path("attempt").canConvertToInt()) { throw invalid(); }
        var event = json.treeToValue(tree, DdcConfigRolloutRequestedEvent.class);
        validation.validate(event);
        if (!event.isValid() || !Objects.equals(context.messageId(), event.getEventId())
                || !Objects.equals(context.destination(), route.destination(event))
                || !Objects.equals(context.headers().get("tenantId"), event.getTenantId())
                || !Objects.equals(context.headers().get("eventType"), route.eventType())) { throw invalid(); }
        return event;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANSHU_CONFIG_EVENT_INVALID"); }
}
