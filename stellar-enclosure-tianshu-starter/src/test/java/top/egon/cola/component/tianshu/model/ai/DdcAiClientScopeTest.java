package top.egon.cola.component.tianshu.model.ai;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcAiClientScopeTest {

    @Test
    void scopeRequiresPositiveTenantAndExactBusinessEnvironmentAndApplication() {
        assertThat(new DdcAiClientScope(20, "xingyuan", "test", "tianji"))
                .isEqualTo(new DdcAiClientScope(20, "xingyuan", "test", "tianji"));
        assertThatThrownBy(() -> new DdcAiClientScope(0, "xingyuan", "test", "tianji"))
                .isInstanceOf(IllegalArgumentException.class);
        for (String value : new String[] {"", " test", "test\n", "a".repeat(129)}) {
            assertThatThrownBy(() -> new DdcAiClientScope(20, value, "test", "tianji"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void legacyFunctionalPortsKeepGetButDoNotInventTransportScope() {
        DdcPromptClient prompt = query -> new DdcPromptResult().setResourceId(query.resourceId());
        DdcSkillClient skill = query -> new DdcSkillResult().setResourceId(query.resourceId());
        var query = new DdcAiResourceQuery("prompts", "review", 2L, 10L);
        assertThat(prompt.get(query).getResourceId()).isEqualTo(10L);
        assertThat(skill.get(query).getResourceId()).isEqualTo(10L);
        assertThatThrownBy(prompt::scope).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(skill::scope).isInstanceOf(IllegalStateException.class);
    }
}
