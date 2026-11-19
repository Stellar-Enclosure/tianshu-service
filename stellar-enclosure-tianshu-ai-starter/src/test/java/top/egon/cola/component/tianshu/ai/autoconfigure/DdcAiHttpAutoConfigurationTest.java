package top.egon.cola.component.tianshu.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.component.tianshu.ai.http.DdcAiHttpClient;
import top.egon.cola.component.tianshu.api.client.DdcAiFeedbackClient;
import top.egon.cola.component.tianshu.api.client.DdcAiSyncClient;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;

class DdcAiHttpAutoConfigurationTest {
    private static final String PREFIX = "egon.cola.component.tianshu.ai.http.";
    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DdcAiHttpAutoConfiguration.class));

    @Test
    void disabledByDefaultWithoutAnyIdentityClientOrPorts() {
        runner.run(context
            -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(DdcAiHttpClient.class)
                .doesNotHaveBean(DdcPromptClient.class)
                .doesNotHaveBean(DdcSkillClient.class)
                .doesNotHaveBean(DdcAiSyncClient.class)
                .doesNotHaveBean(DdcAiFeedbackClient.class));
    }

    @Test
    void enablingWithoutExplicitDeploymentBindingFailsClosed() {
        configured().withPropertyValues(PREFIX + "enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void readCapabilityRegistersOnlyTheThreeReadCapablePorts() {
        bound("READ", "read").run(context -> {
            assertThat(context)
                .hasNotFailed()
                .hasSingleBean(DdcAiHttpClient.class)
                .hasSingleBean(DdcPromptClient.class)
                .hasSingleBean(DdcSkillClient.class)
                .hasSingleBean(DdcAiSyncClient.class)
                .doesNotHaveBean(DdcAiFeedbackClient.class);
            assertThat(context.getBean(DdcPromptClient.class).scope().appCode()).isEqualTo("orders");
        });
    }

    @Test
    void feedbackAloneDoesNotInstallReadOrAuthoringPorts() {
        bound("FEEDBACK", "report")
            .run(context
                -> assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(DdcAiFeedbackClient.class)
                    .doesNotHaveBean(DdcPromptClient.class)
                    .doesNotHaveBean(DdcSkillClient.class)
                    .doesNotHaveBean(DdcAiSyncClient.class));
    }

    @Test
    void authoringRequiresAllThreeExplicitGrantsWithoutDefaultingPublishAuthority() {
        bound("AUTHORING", "read", "write").run(context -> assertThat(context).hasFailed());
        bound("AUTHORING", "read", "write", "publish")
            .run(context
                -> assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(DdcPromptClient.class)
                    .hasSingleBean(DdcSkillClient.class)
                    .hasSingleBean(DdcAiSyncClient.class)
                    .doesNotHaveBean(DdcAiFeedbackClient.class));
    }

    @Test
    void allCapabilitiesShareOneTransportAndRetainAnExplicitCustomPort() {
        DdcPromptClient custom = mock(DdcPromptClient.class);
        bound("READ,AUTHORING,FEEDBACK", "read", "write", "publish", "report")
            .withBean("customPromptClient", DdcPromptClient.class, () -> custom)
            .run(context -> {
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(DdcAiHttpClient.class)
                    .hasSingleBean(DdcPromptClient.class)
                    .hasSingleBean(DdcSkillClient.class)
                    .hasSingleBean(DdcAiSyncClient.class)
                    .hasSingleBean(DdcAiFeedbackClient.class);
                assertThat(context.getBean(DdcPromptClient.class)).isSameAs(custom);
                assertThat(context).doesNotHaveBean("ddcPromptHttpClient");
            });
    }

    @Test
    void unknownOrUnneededGrantCannotSilentlyBroadenConfiguredCapabilities() {
        bound("READ", "read", "publish").run(context -> assertThat(context).hasFailed());
        bound("READ", "read")
            .withPropertyValues(PREFIX + "target=http://unapproved.invalid")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void absenceOfFeedbackCapabilityKeepsTheExistingReporterPrerequisiteFailure() {
        bound("READ", "read")
            .withConfiguration(AutoConfigurations.of(DdcAiStarterAutoConfiguration.class))
            .withPropertyValues("egon.cola.component.tianshu.ai.feedback.enabled=true",
                "egon.cola.component.tianshu.prompt.enabled=true")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseMessage(
                        "egon.cola.component.tianshu.ai.feedback.enabled requires a DdcAiFeedbackClient adapter");
            });
    }

    private ApplicationContextRunner configured() {
        return runner.withBean("jacksonObjectMapper", ObjectMapper.class, ObjectMapper::new)
            .withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> mock(IdpServiceOAuth2Client.class));
    }

    private ApplicationContextRunner bound(String capabilities, String... operations) {
        var values = new java.util.ArrayList<>(Arrays.asList(PREFIX + "enabled=true",
            PREFIX + "service-origin=http://tianshu-admin.platform-system.svc.cluster.local:8080",
            PREFIX + "issuer=https://idp.example.invalid/tenant-a",
            PREFIX + "resource-uri=https://resource.example.invalid/tianshu",
            PREFIX + "registration-id=registered-ai-client", PREFIX + "app-id=source-app-id",
            PREFIX + "identity-tenant-id=identity-tenant-a", PREFIX + "tenant-id=7", PREFIX + "biz-code=retail",
            PREFIX + "env=prod", PREFIX + "app-code=orders", PREFIX + "capabilities=" + capabilities));
        for (String operation : operations)
            values.add(PREFIX + "operation-scopes." + operation + "=fixture:ai:" + operation);
        return configured().withPropertyValues(values.toArray(String[] ::new));
    }
}
