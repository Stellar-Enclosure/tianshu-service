package top.egon.cola.component.tianshu.admin.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component("ddcAdminTransportSecurityValidator")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public final class DdcAdminTransportSecurityValidator
        implements ApplicationRunner {

    private static final String PREFIX =
            "egon.cola.component.tianshu.admin.transport-security";

    @Qualifier("environment")
    @lombok.NonNull
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        validate(environment, PREFIX);
    }

    public static void validate(Environment environment, String prefix) {
        String mode = required(environment, prefix + ".mode")
                .toUpperCase(Locale.ROOT);
        if (!"KUBERNETES_PLAINTEXT".equals(mode) && !"DEVELOPMENT_PLAINTEXT".equals(mode)) {
            throw new IllegalStateException(prefix + ".mode must be KUBERNETES_PLAINTEXT or DEVELOPMENT_PLAINTEXT");
        }
        if ("DEVELOPMENT_PLAINTEXT".equals(mode)
                && !environment.matchesProfiles("local | test")) {
            throw new IllegalStateException("DEVELOPMENT_PLAINTEXT requires the local or test profile");
        }
        if (environment.getProperty("server.ssl.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Tianshu internal application listeners require server.ssl.enabled=false");
        }
        if (!"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy", "none"))) {
            throw new IllegalStateException("Tianshu internal application listeners do not trust forwarded headers");
        }
    }

    private static String required(
            Environment environment,
            String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required");
        }
        return value.trim();
    }
}
