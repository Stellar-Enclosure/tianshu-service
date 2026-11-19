package top.egon.cola.component.tianshu.ai.properties;

import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;

/** Explicit immutable-at-startup SERVICE identity and target scope; no URI-derived identity or default grant. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Validated
@ConfigurationProperties(prefix = DdcAiHttpProperties.PREFIX, ignoreUnknownFields = false)
public class DdcAiHttpProperties {
    public static final String PREFIX = "egon.cola.component.tianshu.ai.http";
    public static final String BEAN_NAME = PREFIX + "-"
        + "top.egon.cola.component.tianshu.ai.properties.DdcAiHttpProperties";

    private boolean enabled;
    private URI serviceOrigin;
    private URI issuer;
    private URI resourceUri;
    private String registrationId;
    private String appId;
    private String identityTenantId;
    private Long tenantId;
    private String bizCode;
    private String env;
    private String appCode;
    private Duration timeout = Duration.ofSeconds(5);
    private Set<CapabilityEnum> capabilities = Set.of();
    private Map<Operation, String> operationScopes = Map.of();

    @AssertTrue(message = "AI_HTTP_DEPLOYMENT_BINDING_REQUIRED")
    public boolean isBindingValid() {
        if (!enabled)
            return true;
        try {
            if (serviceOrigin == null || !canonical(issuer) || !canonical(resourceUri) || !coordinate(registrationId)
                || !coordinate(appId) || !coordinate(identityTenantId) || !coordinate(bizCode) || !coordinate(env)
                || !coordinate(appCode) || tenantId == null || timeout == null
                || timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0
                || capabilities == null || capabilities.isEmpty()
                || capabilities.stream().anyMatch(java.util.Objects::isNull) || operationScopes == null)
                return false;
            new DdcAiClientScope(tenantId, bizCode, env, appCode);
            Set<Operation> required = EnumSet.noneOf(Operation.class);
            if (readEnabled())
                required.add(Operation.READ);
            if (capabilities.contains(CapabilityEnum.AUTHORING)) {
                required.add(Operation.WRITE);
                required.add(Operation.PUBLISH);
            }
            if (capabilities.contains(CapabilityEnum.FEEDBACK))
                required.add(Operation.REPORT);
            return operationScopes.keySet().equals(required)
                && operationScopes.values().stream().allMatch(
                    scope -> scope != null && scope.matches("[A-Za-z0-9:_./-]{1,128}"));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    public boolean readEnabled() {
        return capabilities != null
            && (capabilities.contains(CapabilityEnum.READ) || capabilities.contains(CapabilityEnum.AUTHORING));
    }

    private static boolean coordinate(String value) {
        return value != null && !value.isBlank() && value.equals(value.strip()) && value.length() <= 256
            && !value.contains("*") && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static boolean canonical(URI uri) {
        return uri != null && "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null
            && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.normalize().equals(uri)
            && uri.getPort() != 0 && uri.getPort() <= 65_535;
    }

    /** AUTHORING includes reads, but requires all three separate, explicit operation scopes. */
    public enum CapabilityEnum { READ, AUTHORING, FEEDBACK }

    @Override
    public String toString() {
        return "DdcAiHttpProperties[enabled=" + enabled + "]";
    }
}
