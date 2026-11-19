package top.egon.cola.component.tianshu.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DdcTestDependencyBoundaryTest {
    @Test
    void sampleUsesOnlyTheCurrentSdkAndNoRetiredTransportOrAdminImplementation() throws Exception {
        Path basedir = Path.of(System.getProperty("basedir"));
        String pom = Files.readString(basedir.resolve("pom.xml"));
        assertThat(pom).doesNotContain("<artifactId>stellar-enclosure-tianshu-admin</artifactId>",
            "<artifactId>stellar-enclosure-component-rpc-tianshu-adapter</artifactId>");
        String configuration = Files.readString(basedir.resolve("src/main/resources/application.yml"));
        assertThat(configuration)
            .doesNotContain("TIANSHU_RPC_", "TIANSHU_REGISTRY_", "TIANSHU_REDIS_", "lease-seconds",
                "heartbeat-interval-seconds", "optional:tianshu:");
        for (String resource :
            java.util.List.of("top/egon/cola/component/tianshu/admin/service/config/DdcConfigService.class",
                "top/egon/cola/component/rpc/tianshu/client/DdcRpcClientFactory.class",
                "top/egon/cola/component/tianshu/http/registration/DdcHttpRegistrationRuntime.class",
                "top/egon/cola/component/tianshu/service/lifecycle/DdcRuntimeCoordinator.class")) {
            assertThat(getClass().getClassLoader().getResource(resource)).as(resource).isNull();
        }
        try (var files = Files.walk(basedir.resolve("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file))
                    .doesNotContain(
                        ".tianshu.admin.", ".rpc.tianshu.", "DdcRuntimeCoordinator", "DdcServiceRegistryClient");
            }
        }
    }
}
