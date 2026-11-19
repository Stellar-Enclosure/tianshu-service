package top.egon.cola.component.tianshu.admin.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.outbox.delivery.*;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutOutboxRouteDTO;
import top.egon.cola.component.tianshu.admin.config.DdcOutboxConfiguration.DdcConfigRolloutRequestedEvent;
import top.egon.cola.component.tianshu.admin.security.tenancy.*;
import top.egon.cola.component.tianshu.admin.service.publish.DdcNativeConfigRolloutService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DdcConfigOutboxContractTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final DdcConfigRolloutOutboxRouteDTO route=new DdcConfigRolloutOutboxRouteDTO("tianshu.config-rollout","1");
    private final DdcNativeConfigRolloutService rollout=mock(DdcNativeConfigRolloutService.class);
    private final DdcTenantRegistry tenants=mock(DdcTenantRegistry.class);
    private final ValidationUtils validation=new ValidationUtils(Validation.buildDefaultValidatorFactory().getValidator());

    @Test void nativeWakeRestoresTenantContextAndTechnicalSuccessDoesNotInventProcessSuccess() throws Exception {
        when(tenants.requireEnabledTenant(101L)).thenReturn(new DdcTenantBindingDTO(101L,"registered",true));
        var event=event();
        when(rollout.reconcileNative(eq("123"),eq(441L),eq(42L),eq(1),eq(event.getEventId())))
                .thenAnswer(call -> { assertThat(MDC.get("tenantId")).isEqualTo("101"); return true; });
        MDC.put("tenantId","777");
        try {
            assertThat(handler().deliver(context(event,JSON.writeValueAsString(event),route.channel(),event.getEventId())).kind())
                    .isEqualTo(DeliveryResult.Kind.SUCCESS);
            assertThat(MDC.get("tenantId")).isEqualTo("777");
        } finally { MDC.clear(); }
        verify(rollout).reconcileNative("123",441L,42L,1,event.getEventId());
    }

    @Test void staleTaskIsPermanentButUnavailableObservationIsRetried() throws Exception {
        when(tenants.requireEnabledTenant(101L)).thenReturn(new DdcTenantBindingDTO(101L,"registered",true));
        var event=event();
        when(rollout.reconcileNative(anyString(),anyLong(),anyLong(),anyInt(),anyString())).thenReturn(false);
        var context=context(event,JSON.writeValueAsString(event),route.channel(),event.getEventId());
        assertThat(handler().deliver(context).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        when(rollout.reconcileNative(anyString(),anyLong(),anyLong(),anyInt(),anyString()))
                .thenThrow(new IllegalStateException("sensitive failure must not escape"));
        var unavailable=handler().deliver(context);
        assertThat(unavailable.kind()).isEqualTo(DeliveryResult.Kind.RETRYABLE_FAILURE);
        assertThat(unavailable.toString()).doesNotContain("sensitive failure");
    }

    @Test void oldSchemaWrongEnvelopeDuplicatesAndUnknownFieldsCannotInvokeNativeWriter() throws Exception {
        var event=event();
        String json=JSON.writeValueAsString(event);
        assertThat(JSON.readTree(json).size()).isEqualTo(7);
        assertThat(json).doesNotContain("content", "secret", "message", "valid");
        for (var context: List.of(context(event,json,"tianshu.config-dispatch",event.getEventId()),
                context(event,json,route.channel(),"wrong-event"),
                context(event,json.replace("\"schemaVersion\":\"1\"","\"schemaVersion\":\"1\",\"schemaVersion\":\"1\""),route.channel(),event.getEventId()),
                context(event,json.substring(0,json.length()-1)+",\"content\":\"must-not-run\"}",route.channel(),event.getEventId()),
                context(event,json+" {}",route.channel(),event.getEventId()))) {
            assertThat(handler().deliver(context).kind()).isEqualTo(DeliveryResult.Kind.PERMANENT_FAILURE);
        }
        verifyNoInteractions(rollout,tenants);
    }

    @Test void registeringTheChannelDoesNotInstantiateThePublisherNetworkService() {
        var factory=new DefaultListableBeanFactory();
        factory.registerBeanDefinition("ddcNativeConfigRolloutService",new RootBeanDefinition(DdcNativeConfigRolloutService.class,()->rollout));
        var handler=new DdcConfigDispatchDeliveryHandler(JSON,validation,tenants,new DdcTenantContextExecutor(),
                factory.getBeanProvider(DdcNativeConfigRolloutService.class),route);
        assertThat(new DeliveryHandlerRegistry(List.of(handler)).required(route.channel())).isSameAs(handler);
        assertThat(factory.containsSingleton("ddcNativeConfigRolloutService")).isFalse();
        assertThatThrownBy(() -> new DeliveryHandlerRegistry(List.of(handler)).required("tianshu.config-dispatch"))
                .isInstanceOf(RuntimeException.class);
    }

    private DdcConfigDispatchDeliveryHandler handler() {
        return new DdcConfigDispatchDeliveryHandler(JSON,validation,tenants,new DdcTenantContextExecutor(),
                new StaticListableBeanFactory(Map.of("ddcNativeConfigRolloutService",rollout)).getBeanProvider(DdcNativeConfigRolloutService.class),route);
    }
    private static DdcConfigRolloutRequestedEvent event() {
        return DdcConfigRolloutRequestedEvent.builder().schemaVersion("1").tenantId("101").configId("441")
                .revision("42").changeId("123").attempt(1)
                .eventId(DdcConfigRolloutRequestedEvent.eventId("101","441","42","123",1)).build();
    }
    private DeliveryContext context(DdcConfigRolloutRequestedEvent event,String payload,String channel,String id) {
        return new DeliveryContext(id,channel,route.destination(event),payload,"application/json","1",
                Map.of("tenantId",event.getTenantId(),"eventType",route.eventType()),null,1,10,Instant.now().plusSeconds(60));
    }
}
