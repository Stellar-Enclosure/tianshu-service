package top.egon.cola.component.tianshu.ai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import top.egon.cola.component.tianshu.ai.http.DdcAiHttpClient;
import top.egon.cola.component.tianshu.ai.properties.DdcAiHttpProperties;
import top.egon.cola.component.tianshu.ai.properties.DdcAiHttpProperties.CapabilityEnum;
import top.egon.cola.component.tianshu.api.client.DdcAiFeedbackClient;
import top.egon.cola.component.tianshu.api.client.DdcAiSyncClient;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;

/** Optional native ports are installed before the existing resource, workspace and authoring consumers. */
@Slf4j
@AutoConfiguration(
    afterName = "top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterAutoConfiguration",
    beforeName = {"top.egon.cola.component.tianshu.autoconfigure.DdcAiResourceAutoConfiguration",
        "top.egon.cola.component.tianshu.ai.autoconfigure.DdcAiStarterAutoConfiguration"})
@EnableConfigurationProperties(DdcAiHttpProperties.class)
@ConditionalOnProperty(prefix = DdcAiHttpProperties.PREFIX, name = "enabled", havingValue = "true")
public class DdcAiHttpAutoConfiguration {
    @Bean(name = "ddcAiHttpValidator", destroyMethod = "close")
    LocalValidatorFactoryBean ddcAiHttpValidator() {
        return new LocalValidatorFactoryBean();
    }

    @Bean(name = "ddcAiHttpClient", destroyMethod = "close")
    DdcAiHttpClient ddcAiHttpClient(@Qualifier(DdcAiHttpProperties.BEAN_NAME) DdcAiHttpProperties properties,
        @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
        @Qualifier("jacksonObjectMapper") ObjectMapper json,
        @Qualifier("ddcAiHttpValidator") LocalValidatorFactoryBean validator) {
        return new DdcAiHttpClient(properties, oauth, json, validator);
    }

    @Bean("ddcPromptHttpClient")
    @Conditional(ReadCondition.class)
    @ConditionalOnMissingBean(DdcPromptClient.class)
    DdcPromptClient ddcPromptHttpClient(@Qualifier("ddcAiHttpClient") DdcAiHttpClient client) {
        return client.promptClient();
    }

    @Bean("ddcSkillHttpClient")
    @Conditional(ReadCondition.class)
    @ConditionalOnMissingBean(DdcSkillClient.class)
    DdcSkillClient ddcSkillHttpClient(@Qualifier("ddcAiHttpClient") DdcAiHttpClient client) {
        return client.skillClient();
    }

    @Bean("ddcAiSyncHttpClient")
    @Conditional(ReadCondition.class)
    @ConditionalOnMissingBean(DdcAiSyncClient.class)
    DdcAiSyncClient ddcAiSyncHttpClient(@Qualifier("ddcAiHttpClient") DdcAiHttpClient client) {
        return client.syncClient();
    }

    @Bean("ddcAiFeedbackHttpClient")
    @Conditional(FeedbackCondition.class)
    @ConditionalOnMissingBean(DdcAiFeedbackClient.class)
    DdcAiFeedbackClient ddcAiFeedbackHttpClient(@Qualifier("ddcAiHttpClient") DdcAiHttpClient client) {
        return client.feedbackClient();
    }

    static final class ReadCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var capabilities = capabilities(context);
            return capabilities.contains(CapabilityEnum.READ) || capabilities.contains(CapabilityEnum.AUTHORING);
        }
    }

    static final class FeedbackCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return capabilities(context).contains(CapabilityEnum.FEEDBACK);
        }
    }

    private static Set<CapabilityEnum> capabilities(ConditionContext context) {
        return Binder.get(context.getEnvironment())
            .bind(DdcAiHttpProperties.PREFIX + ".capabilities", Bindable.setOf(CapabilityEnum.class))
            .orElse(Set.of());
    }
}
