package top.egon.cola.component.tianshu.admin.security.tenancy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("ddcTenantRegistry")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcTenantRegistry {

    @Qualifier("egon.cola.component.tianshu.admin.tenancy-top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantAdminProperties")
    private final DdcTenantAdminProperties tenantProperties;

    private Map<String, TenantBinding> bindingsByIdentity = Map.of();
    private Map<Long, TenantBinding> bindingsById = Map.of();

    @PostConstruct
    public void initialize() {
        List<DdcTenantBindingDTO> configuredBindings = requiredList(
                tenantProperties.getBindings(), "bindings");
        LinkedHashMap<String, TenantBinding> identityMap = new LinkedHashMap<>();
        LinkedHashMap<Long, TenantBinding> idMap = new LinkedHashMap<>();
        for (DdcTenantBindingDTO value : configuredBindings) {
            if (value == null || value.getTenantId() == null || value.getTenantId() <= 0
                    || value.getIdentityTenantId() == null
                    || value.getIdentityTenantId().isBlank()
                    || "*".equals(value.getIdentityTenantId().trim())) {
                throw new IllegalStateException("Invalid Tianshu tenant binding");
            }
            TenantBinding binding = new TenantBinding(
                    value.getTenantId(), value.getIdentityTenantId().trim(), value.isEnabled());
            if (identityMap.putIfAbsent(binding.identityTenantId(), binding) != null
                    || idMap.putIfAbsent(binding.tenantId(), binding) != null) {
                throw new IllegalStateException("Duplicate Tianshu tenant binding");
            }
        }
        bindingsByIdentity = Map.copyOf(identityMap);
        bindingsById = Map.copyOf(idMap);
        log.info("Initialized Tianshu tenant bindings: tenants={}", bindingsById.size());
    }

    public DdcTenantBindingDTO requireByIdentityTenantId(String identityTenantId) {
        TenantBinding binding = identityTenantId == null
                ? null
                : bindingsByIdentity.get(identityTenantId);
        if (binding == null || !binding.enabled()) {
            throw new IllegalArgumentException("TENANT_IDENTITY_UNREGISTERED");
        }
        return bindingDTO(binding);
    }

    public DdcTenantBindingDTO requireEnabledTenant(Long tenantId) {
        TenantBinding binding = tenantId == null ? null : bindingsById.get(tenantId);
        if (binding == null || !binding.enabled()) {
            throw new IllegalArgumentException("TENANT_ID_UNREGISTERED");
        }
        return bindingDTO(binding);
    }

    public DdcTenantContextDTO resolveUserContext(String identityTenantId, String subject) {
        DdcTenantBindingDTO binding = requireByIdentityTenantId(identityTenantId);
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("TENANT_ACTOR_REQUIRED");
        }
        return new DdcTenantContextDTO(
                binding.getTenantId(), binding.getIdentityTenantId(), "user:" + subject.trim());
    }

    private static <T> List<T> requiredList(
            List<T> values,
            String name) {
        if (values == null) {
            throw new IllegalStateException("Tianshu tenant " + name + " must not be null");
        }
        return List.copyOf(values);
    }

    private static DdcTenantBindingDTO bindingDTO(TenantBinding binding) {
        return DdcTenantBindingDTO.builder()
                .tenantId(binding.tenantId())
                .identityTenantId(binding.identityTenantId())
                .enabled(binding.enabled())
                .build();
    }

    private record TenantBinding(long tenantId, String identityTenantId, boolean enabled) {
    }
}
