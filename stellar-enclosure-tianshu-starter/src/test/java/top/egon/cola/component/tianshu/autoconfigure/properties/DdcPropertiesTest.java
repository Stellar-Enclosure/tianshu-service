package top.egon.cola.component.tianshu.autoconfigure.properties;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DdcPropertiesTest {

    @Test
    void machineTransportPropertiesAreOwnedByTheRpcAdapter() {
        assertThat(Arrays.stream(DdcProperties.class.getDeclaredClasses())
                .map(Class::getSimpleName))
                .doesNotContain("Admin");
        assertThat(Arrays.stream(DdcProperties.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("getAdmin");
    }

    @Test
    void retiredInstanceAndReconciliationPropertiesAreNotExported() {
        assertThat(Arrays.stream(DdcProperties.class.getDeclaredClasses()).map(Class::getSimpleName))
                .doesNotContain("Instance", "Consistency");
        assertThat(Arrays.stream(DdcProperties.class.getDeclaredMethods()).map(method -> method.getName()))
                .doesNotContain("getInstance", "setInstance", "getConsistency", "setConsistency");
    }
}
