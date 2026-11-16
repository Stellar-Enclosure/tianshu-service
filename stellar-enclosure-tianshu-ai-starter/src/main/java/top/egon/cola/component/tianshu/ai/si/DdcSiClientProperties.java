package top.egon.cola.component.tianshu.ai.si;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Configuration properties for downstream SI catalog caching, pub/sub refresh, and self-registration. */
@Data
@Accessors(chain = true)
@Validated
@ConfigurationProperties(prefix = DdcSiClientProperties.PREFIX)
public class DdcSiClientProperties {

    public static final String PREFIX = "egon.cola.component.tianshu.si.client";

    private boolean enabled = false;

    private Long tenantId;

    private String bizCode;

    private String namespaceCode;

    private String envCode = "dev";

    private String appCode;

    @NotNull
    private Duration cacheTtl = Duration.ofSeconds(60);

    @NotNull
    private Duration debounceWindow = Duration.ofSeconds(2);

    private boolean failFastOnStartup = false;

    @Min(1)
    @Max(10)
    private int selfRegistrationMaxAttempts = 3;

    @NotNull
    private Duration selfRegistrationBackoff = Duration.ofMillis(200);
}
