package top.egon.cola.component.tianshu.admin.integration;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import top.egon.cola.component.tianshu.admin.DynamicConfigCenterAdminApplication;
import top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAppBO;
import top.egon.cola.component.tianshu.admin.repository.DdcAppRepository;
import top.egon.cola.component.tianshu.admin.security.rpc.DdcNonceStore;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcBizService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DdcActiveActiveAdminIT {

    private static final String APP_CODE = "active-active-app";
    private static final String ACCESS_KEY = "active-active-access";
    private static final String SECRET = "active-active-secret";
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(1L, "active-active-it", "test:active-active");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    private ConfigurableApplicationContext nodeA;
    private ConfigurableApplicationContext nodeB;

    @BeforeAll
    void startTwoAdminNodes() throws IOException {
        initializeManagedPersistenceSchema();
        nodeA = node("a");
        nodeB = node("b");
        LocalDateTime now = LocalDateTime.now();
        DdcAppBO app = DdcAppBO.builder().bizCode("default").appCode(APP_CODE)
                .appName("Active Active Test").enabled(true).createdAt(now).updatedAt(now).build();
        nodeA.getBean(DdcTenantContextExecutor.class).run(TENANT,
                () -> nodeA.getBean(DdcAppRepository.class).insert(app));
    }

    private void initializeManagedPersistenceSchema() throws IOException {
        String previousUrl = System.getProperty("TIANSHU_JDBC_URL");
        String previousUsername = System.getProperty("TIANSHU_DB_USERNAME");
        String previousPassword = System.getProperty("TIANSHU_DB_PASSWORD");
        try {
            System.setProperty("TIANSHU_JDBC_URL", POSTGRES.getJdbcUrl());
            System.setProperty("TIANSHU_DB_USERNAME", POSTGRES.getUsername());
            System.setProperty("TIANSHU_DB_PASSWORD", POSTGRES.getPassword());
            DdcPersistenceMigrationCli.main(new String[]{"initialize"});
        } finally {
            restoreSystemProperty("TIANSHU_JDBC_URL", previousUrl);
            restoreSystemProperty("TIANSHU_DB_USERNAME", previousUsername);
            restoreSystemProperty("TIANSHU_DB_PASSWORD", previousPassword);
        }
    }

    private void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    @AfterAll
    void stopTwoAdminNodes() {
        if (nodeB != null) {
            nodeB.close();
        }
        if (nodeA != null) {
            nodeA.close();
        }
    }

    @Test
    void remainingSignedAdaptersShareNonceAuthorityAcrossReplicas() {
        String nonce = UUID.randomUUID().toString();
        var storeA = nodeA.getBean("ddcNonceStore", DdcNonceStore.class);
        var storeB = nodeB.getBean("ddcNonceStore", DdcNonceStore.class);
        assertThat(storeA.markIfAbsent("active-active", nonce, Duration.ofSeconds(30))).isTrue();
        assertThat(storeB.markIfAbsent("active-active", nonce, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void metadataScopeCacheExpiresAcrossReplicas() {
        var scopeB = nodeB.getBean(DdcScopeGate.class);
        var contextsB = nodeB.getBean(DdcTenantContextExecutor.class);
        Runnable check = () -> contextsB.run(TENANT, () -> scopeB.assertPhysicalEnabled("default", APP_CODE, "dev"));
        var contextsA = nodeA.getBean(DdcTenantContextExecutor.class);
        var bizA = nodeA.getBean(DdcBizService.class);
        check.run();
        contextsA.run(TENANT, () -> bizA.setEnabled("default", false));
        try {
            assertThatCode(check::run).doesNotThrowAnyException();
            await().atMost(Duration.ofSeconds(7)).untilAsserted(() ->
                    assertThatThrownBy(check::run).isInstanceOf(RuntimeException.class));
        } finally {
            contextsA.run(TENANT, () -> bizA.setEnabled("default", true));
            scopeB.invalidate("biz:default");
        }
    }

    private ConfigurableApplicationContext node(String id) {
        return new SpringApplicationBuilder(DynamicConfigCenterAdminApplication.class)
                .profiles("active-active")
                .run(nodeArguments(id));
    }

    private String[] nodeArguments(String id) {
        return new String[]{
                "--spring.application.name=tianshu-active-active-" + id,
                "--server.port=0",
                "--spring.jmx.enabled=false",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--egon.cola.platform.tianquan.shoubing.enabled=false",
                "--egon.cola.platform.tianquan.jianshen.enabled=false",
                "--egon.cola.component.tianshu.enabled=false",
                "--egon.cola.component.tianshu.registry.enabled=false",
                "--egon.cola.component.tianshu.admin.security.local-dev=true",
                "--egon.cola.component.tianshu.admin.redis.host=" + REDIS.getHost(),
                "--egon.cola.component.tianshu.admin.redis.port=" + REDIS.getMappedPort(6379),
                "--egon.cola.component.tianshu.admin.rpc.signature-enabled=true",
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].credential-id=active-active",
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].access-key=" + ACCESS_KEY,
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].secret=" + SECRET,
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].client-type=*",
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].app-code-patterns[0]=*",
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].env-patterns[0]=*",
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].biz-code-patterns[0]=*",
                "--egon.cola.component.tianshu.admin.rpc.credentials[0].allowed-operations[0]=*",
                "--egon.cola.component.tianshu.admin.tenancy.bindings[0].tenant-id=1",
                "--egon.cola.component.tianshu.admin.tenancy.bindings[0].identity-tenant-id=active-active-it",
                "--egon.cola.component.tianshu.admin.tenancy.bindings[0].enabled=true",
                "--egon.cola.component.tianshu.admin.tenancy.credential-bindings[0].credential-id=active-active",
                "--egon.cola.component.tianshu.admin.tenancy.credential-bindings[0].tenant-id=1",
                "--egon.cola.component.tianshu.admin.tenancy.credential-bindings[0].namespace-patterns[0]=*",
                "--egon.cola.component.rpc.enabled=false"
        };
    }
}
