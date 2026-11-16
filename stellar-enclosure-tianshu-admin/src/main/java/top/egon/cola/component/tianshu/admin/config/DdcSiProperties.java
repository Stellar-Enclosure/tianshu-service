package top.egon.cola.component.tianshu.admin.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** Strongly-typed SI governance properties bound via ConfigurationProperties. */
@Data
@Validated
@ConfigurationProperties(prefix = "egon.cola.component.tianshu.si")
public class DdcSiProperties {

    @Min(1)
    @Max(4096)
    private int vectorDimensions = 1536;

    @Min(1)
    @Max(200)
    private int rrfK = 60;

    @NotNull
    private Duration embeddingTimeout = Duration.ofMillis(1500);

    private boolean semanticEnabled = true;

    private boolean autoPublishOnSelfRegister = false;

    @NotEmpty
    private List<String> allowedPromotionTransitions = List.of("dev->test", "test->staging", "staging->prod");

    @NotNull
    private IndexExecutorProperties indexExecutor = new IndexExecutorProperties();

    @Data
    public static class IndexExecutorProperties {
        @Min(1)
        @Max(32)
        private int corePoolSize = 2;

        @Min(1)
        @Max(64)
        private int maxPoolSize = 8;

        @Min(8)
        @Max(4096)
        private int queueCapacity = 256;

        @NotNull
        private Duration awaitTermination = Duration.ofSeconds(10);
    }
}
