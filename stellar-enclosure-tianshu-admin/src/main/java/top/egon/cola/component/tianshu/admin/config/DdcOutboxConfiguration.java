package top.egon.cola.component.tianshu.admin.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.outbox.common.exception.OutboxValidationException;
import top.egon.cola.component.tianshu.admin.service.config.DdcYamlConfigValidator;
import top.egon.cola.component.tianshu.model.event.DdcConfigDispatchPreparedEvent;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;

import java.time.Clock;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.Accessors;

@Configuration(value = "ddcOutboxConfiguration", proxyBeanMethods = false)
public class DdcOutboxConfiguration {

    @Bean("ddcConfigRolloutOutboxRoute")
    public DdcConfigRolloutOutboxRouteDTO ddcConfigRolloutOutboxRoute() {
        return new DdcConfigRolloutOutboxRouteDTO("tianshu.config-rollout", "1");
    }

    public record DdcConfigRolloutOutboxRouteDTO(String channel, String schemaVersion) {
        private static final Pattern DESTINATION = Pattern.compile("^tianshu-config/[1-9][0-9]{0,18}/[1-9][0-9]{0,18}$");
        public DdcConfigRolloutOutboxRouteDTO {
            if (!"tianshu.config-rollout".equals(channel) || !"1".equals(schemaVersion)) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_ROLLOUT_ROUTE_INVALID");
            }
        }
        public String eventType() { return "TIANSHU_CONFIG_ROLLOUT_REQUESTED"; }
        public String destination(DdcConfigRolloutRequestedEvent event) {
            String destination = "tianshu-config/" + event.getTenantId() + "/" + event.getConfigId();
            validateDestination(destination);
            return destination;
        }
        public void validateDestination(String destination) {
            if (destination == null || !DESTINATION.matcher(destination).matches()) {
                throw new OutboxValidationException("TIANSHU_CONFIG_ROLLOUT_DESTINATION_INVALID");
            }
        }
    }

    /** A wake-up carries only the fixed database identity; it never embeds credentials or configuration values. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    public static class DdcConfigRolloutRequestedEvent {
        @NotBlank private String schemaVersion;
        @NotBlank private String eventId;
        @NotBlank private String tenantId;
        @NotBlank private String configId;
        @NotBlank private String revision;
        @NotBlank private String changeId;
        @NotNull @Min(1) private Integer attempt;

        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isValid() {
            return "1".equals(schemaVersion) && decimal(tenantId) && decimal(configId) && decimal(revision)
                    && decimal(changeId) && attempt != null && attempt >= 1
                    && eventId(tenantId, configId, revision, changeId, attempt).equals(eventId);
        }
        public static String eventId(String tenantId, String configId, String revision, String changeId, int attempt) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                        ("tianshu-config-rollout:1\n" + tenantId + "\n" + configId + "\n" + revision + "\n"
                                + changeId + "\n" + attempt).getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        private static boolean decimal(String value) {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) { return false; }
            try { return Long.parseLong(value) > 0; } catch (NumberFormatException invalid) { return false; }
        }
        @JsonAnySetter public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("TIANSHU_CONFIG_ROLLOUT_FIELD_UNKNOWN");
        }
        @Override public String toString() { return "ConfigRollout[eventId=" + eventId + "]"; }
    }

    @Bean("ddcPublishTransactionTemplate")
    public TransactionTemplate ddcPublishTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean("ddcMetadataTransactionTemplate")
    public TransactionTemplate ddcMetadataTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(30);
        return transaction;
    }

    @Bean("ddcMetadataReadTransactionTemplate")
    public TransactionTemplate ddcMetadataReadTransactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(30);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }

    @Bean({"ddcPublishClock", "egonColaMybatisPlusClock"})
    public Clock ddcPublishClock() {
        return Clock.systemUTC();
    }

    @Bean("ddcYamlConfigValidator")
    public DdcYamlConfigValidator ddcYamlConfigValidator(
            @Qualifier("egon.cola.component.tianshu.admin-top.egon.cola.component.tianshu.admin.config.DdcAdminProperties")
            DdcAdminProperties properties) {
        return new DdcYamlConfigValidator(properties.getMaxConfigBytes());
    }

    @Bean("ddcAiPublicationOutboxRoute")
    public DdcAiPublicationOutboxRouteDTO ddcAiPublicationOutboxRoute() {
        return new DdcAiPublicationOutboxRouteDTO("tianshu.ai-publication", "1");
    }

    /**
     * EVENT-103 的 Outbox 路由：固定通道、schema 与租户 AI Topic 目的地。
     * / Outbox route of EVENT-103: fixed channel, schema and tenant AI-topic destination.
     */
    public record DdcAiPublicationOutboxRouteDTO(String channel, String schemaVersion) {

        public static final Pattern TOPIC_PATTERN = Pattern.compile(
                "^tianshu:v4:tenant:[1-9][0-9]*:\\{[a-f0-9]{64}}:ai:topic$");

        public DdcAiPublicationOutboxRouteDTO {
            if (!"tianshu.ai-publication".equals(channel) || !"1".equals(schemaVersion)) {
                throw new IllegalArgumentException("Invalid Tianshu ai-publication Outbox route");
            }
        }

        public String eventType() {
            return "TIANSHU_AI_PUBLICATION";
        }

        public String destination(Long tenantId, String bizCode, String env, String appCode) {
            String destination = DdcRedisKeys.aiTopic(tenantId, bizCode, env, appCode);
            if (!TOPIC_PATTERN.matcher(destination).matches()) {
                throw new IllegalStateException("AI_TOPIC_DESTINATION_INVALID");
            }
            return destination;
        }
    }

    @Bean("ddcConfigDispatchOutboxRoute")
    public DdcConfigDispatchOutboxRouteDTO ddcConfigDispatchOutboxRoute() {
        return new DdcConfigDispatchOutboxRouteDTO("tianshu.config-dispatch", "1");
    }

    public record DdcConfigDispatchOutboxRouteDTO(String channel, String schemaVersion) {

        private static final Pattern TOPIC_PATTERN = Pattern.compile(
                "^tianshu:v4:tenant:[1-9][0-9]*:\\{[a-f0-9]{64}}:topic$");

        public DdcConfigDispatchOutboxRouteDTO {
            if (!"tianshu.config-dispatch".equals(channel) || !"1".equals(schemaVersion)) {
                throw new IllegalArgumentException("Invalid Tianshu config-dispatch Outbox route");
            }
        }

        public String eventType() {
            return "TIANSHU_CONFIG_DISPATCH";
        }

        public String destination(DdcConfigDispatchPreparedEvent event) {
            if (event == null || event.getMessage() == null) {
                throw new IllegalArgumentException("Prepared config event is required for destination");
            }
            return DdcRedisKeys.topic(
                    event.getTenantId(),
                    event.getMessage().getBizCode(),
                    event.getMessage().getEnv(),
                    event.getMessage().getAppCode());
        }

        public boolean matchesDestination(DdcConfigDispatchPreparedEvent event, String destination) {
            return destination != null
                    && TOPIC_PATTERN.matcher(destination).matches()
                    && destination.equals(destination(event));
        }

        public void validateDestination(String destination) {
            if (destination == null || !TOPIC_PATTERN.matcher(destination).matches()) {
                throw new OutboxValidationException("Invalid Tianshu config dispatch destination");
            }
        }
    }
}
