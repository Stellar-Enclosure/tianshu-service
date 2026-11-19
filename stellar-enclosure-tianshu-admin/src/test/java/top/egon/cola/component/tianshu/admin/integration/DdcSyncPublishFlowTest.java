package top.egon.cola.component.tianshu.admin.integration;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigBootBindingRepository;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliveryTokenService;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextExecutor;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigSnapshotService;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishPreparationTest;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcSyncPublishFlowTest {
    @Test void nativeIntakeAndDeliveryReadUseTheSameImmutableVersionWithoutLegacyAck() {
        var tenant=new DdcTenantContextDTO(101L,"registered","user:test");
        try(var ignored=new DdcTenantContextExecutor().bind(tenant)) {
            var fixture=new DdcPublishPreparationTest.Fixture();
            fixture.service.publish(DdcPublishPreparationTest.request("123",1),"test");
            when(fixture.items.findByBizCodeAndEnvAndAppCodeAndResourceName("reader","test","app","application.yml"))
                    .thenReturn(Optional.of(fixture.item));
            var boots=mock(DdcConfigBootBindingRepository.class);
            var tokens=mock(DdcConfigDeliveryTokenService.class);
            var snapshots=new DdcConfigSnapshotService(fixture.items,fixture.versions,boots,new DdcTenantContextExecutor(),
                    tokens,Clock.systemUTC(),Duration.ofMinutes(5),fixture.manifests);
            var scope=new DdcConfigScopeVO("101","reader","test","app","application.yml");
            var workload=new DdcConfigWorkloadIdentityVO(tenant,scope,"binding-a",7,"cluster-test","reader-apps",
                    "deployment-uid","pod-uid","application","containerd://fixture","boot-1");
            var fixed=snapshots.requireCurrent(workload);
            assertThat(fixed.snapshot().getReleaseRevision()).isEqualTo("2");
            assertThat(fixed.snapshot().getContentSha256()).isEqualTo(fixed.manifest().path("contentSha256").textValue());
            fixture.service.publish(DdcPublishPreparationTest.request("124",2),"test");
            assertThat(snapshots.requireCurrent(workload).snapshot().getReleaseRevision()).isEqualTo("3");
            verifyNoInteractions(boots,tokens);
        }
    }
}
