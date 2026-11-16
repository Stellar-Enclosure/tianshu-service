package top.egon.cola.component.tianshu.ai.si;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DdcSiSelfRegistrationRunnerTest {

    @Test
    void collectsContributorsComputesDeterministicSpecHashAndFailsOpenWhenConfigured() throws Exception {
        DdcSiCatalogClient catalogClient = mock(DdcSiCatalogClient.class);
        List<DdcSiSelfRegistrationRequest> captured = new ArrayList<>();
        doAnswer(inv -> {
            DdcSiSelfRegistrationRequest req = inv.getArgument(0);
            captured.add(req);
            return DdcSiResourceDescriptor.builder()
                    .tenantId(req.getTenantId()).bizCode(req.getBizCode())
                    .namespaceCode(req.getNamespaceCode()).envCode(req.getEnvCode())
                    .resourceId(910001L).kind(req.getKind()).resourceKey(req.getResourceKey())
                    .status(DdcSiLifecycleStatusEnum.DRAFT).sourceType(DdcSiSourceTypeEnum.AUTO_REPORTED)
                    .build();
        }).when(catalogClient).registerManagedResource(any());

        DdcSiLocalResourceContributor contributor = () -> List.of(
                DdcSiSelfRegistrationRequest.builder()
                        .tenantId(1001L).bizCode("ai").namespaceCode("core").envCode("dev")
                        .serviceName("tianji-agent").instanceId("inst-1")
                        .kind(DdcSiKindEnum.AGENT).resourceKey("order-agent")
                        .displayName("Order Agent")
                        .specJson("{\"b\":2,\"a\":1}")
                        .build()
        );
        DdcSiClientProperties properties = new DdcSiClientProperties()
                .setEnabled(true)
                .setTenantId(1001L)
                .setBizCode("ai")
                .setNamespaceCode("core")
                .setEnvCode("dev")
                .setAppCode("tianji-agent")
                .setSelfRegistrationMaxAttempts(2)
                .setSelfRegistrationBackoff(Duration.ofMillis(5));

        DdcSiSelfRegistrationRunner runner = new DdcSiSelfRegistrationRunner(
                catalogClient, properties, List.of(contributor));
        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getSpecHash()).matches("^[0-9a-f]{64}$");
        assertThat(captured.get(0).getSpecHash()).isEqualTo(captured.get(1).getSpecHash());

        // Transient transport failure retries and then fails open without throwing
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(inv -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("tianshu rpc offline");
        }).when(catalogClient).registerManagedResource(any());
        assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThat(attempts.get()).isEqualTo(2);

        // INVALID_ARGUMENT stops immediately without retrying
        AtomicInteger invalidAttempts = new AtomicInteger();
        doAnswer(inv -> {
            invalidAttempts.incrementAndGet();
            throw new IllegalArgumentException("bad spec");
        }).when(catalogClient).registerManagedResource(any());
        assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThat(invalidAttempts.get()).isEqualTo(1);
        verify(catalogClient, times(5)).registerManagedResource(any());
    }
}
