package top.egon.cola.component.tianshu.admin.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigWorkloadObservationRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishTaskQueryService.WorkloadObservationReader;

/** Admin reads committed observer facts through its tenant context without owning a Kubernetes observation token. */
@Configuration(value = "ddcConfigWorkloadObservationReadConfiguration", proxyBeanMethods = false)
public class DdcConfigWorkloadObservationReadConfiguration {
    @Bean("ddcWorkloadObservationReader")
    WorkloadObservationReader ddcWorkloadObservationReader(
            @Qualifier("ddcConfigWorkloadObservationRepository") DdcConfigWorkloadObservationRepository repository,
            @Qualifier("ddcTenantRegistry") DdcTenantRegistry tenants) {
        return target -> {
            long owner = EgonColaTenantIdProvider.currentTenantId();
            var registered = tenants.requireEnabledTenant(owner);
            var trusted = new DdcTenantContextDTO(owner, registered.getIdentityTenantId(), MDC.get("userId"));
            return repository.findCurrent(trusted, target).orElseThrow(
                    () -> new IllegalStateException("TIANSHU_CONFIG_WORKLOAD_OBSERVATION_UNAVAILABLE"));
        };
    }
}
