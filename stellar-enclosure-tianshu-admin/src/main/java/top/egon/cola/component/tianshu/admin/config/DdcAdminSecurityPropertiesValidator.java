package top.egon.cola.component.tianshu.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Validates the Admin HTTP identity and registration configuration. */
@Slf4j
@Component("ddcAdminSecurityPropertiesValidator")
@EnableConfigurationProperties(DdcAdminProperties.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public final class DdcAdminSecurityPropertiesValidator {

    @NonNull
    @Qualifier("egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties")
    private final DdcAdminProperties properties;

    @PostConstruct
    public void validate() {
        var security = properties.getSecurity();
        if (!security.isLocalDev()) {
            required(security.getJwt().getIssuer(), "Tianshu Admin JWT issuer is required");
            required(security.getJwt().getAudience(), "Tianshu Admin JWT audience is required");
        }
        var registration = properties.getRegistration();
        if (registration == null) {
            throw new IllegalStateException("Tianshu registration settings are required");
        }
        required(registration.getRequiredScope(), "Tianshu registration scope is required");
        if (registration.getResourceUri() != null && !registration.getResourceUri().isAbsolute()) {
            throw new IllegalStateException("Tianshu registration Resource URI must be absolute");
        }
        log.debug("Validated Tianshu Admin security configuration; localDev={}", security.isLocalDev());
    }

    private static void required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
