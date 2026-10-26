package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionJsonMapperFactory;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Existing MVC slices isolate their controller contracts; DdcManagedUserRuntimeTest exercises the real policy boundary. */
@TestConfiguration(proxyBeanMethods = false)
public class DdcTransportSliceTestConfiguration {
    @Bean PermissionJsonMapperFactory permissionJsonMapperFactory() {
        var factory = mock(PermissionJsonMapperFactory.class);
        when(factory.forTransport(any(ObjectMapper.class))).thenAnswer(call -> call.getArgument(0, ObjectMapper.class).copy());
        return factory;
    }

    @Bean PermissionExecutionGuard permissionExecutionGuard() { return mock(PermissionExecutionGuard.class); }
    @Bean PermissionFieldBindingGuard permissionFieldBindingGuard() { return mock(PermissionFieldBindingGuard.class); }
}
