package top.egon.cola.component.tianshu.admin.controller;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DdcAiInternalControllerTest {
    @Test void internalAiOperationsHaveAnActualDedicatedReceiver() throws Exception {
        Class<?> receiver = Class.forName("top.egon.cola.component.tianshu.admin.internal.controller.DdcAiInternalController");
        assertThat(receiver.getAnnotation(org.springframework.web.bind.annotation.RestController.class)).isNotNull();
    }
}
