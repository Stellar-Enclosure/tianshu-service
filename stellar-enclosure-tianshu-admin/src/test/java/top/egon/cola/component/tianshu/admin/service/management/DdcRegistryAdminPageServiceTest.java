package top.egon.cola.component.tianshu.admin.service.management;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceCatalog;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceInstance;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceKey;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceQuery;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import top.egon.cola.component.common.core.exception.CommonException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DdcRegistryAdminPageServiceTest {

    private final DdcManagementFacade facade = mock(DdcManagementFacade.class);

    private final DdcRegistryAdminPageService service =
            new DdcRegistryAdminPageService(facade);

    private final DdcManagementServiceQuery query =
            new DdcManagementServiceQuery(
                    "infra", null, "prod", "yuheng",
                    null, null, null, null, null
            );

    @Test
    void pagesSortedServiceKeysWithoutChangingCatalog() {
        DdcManagementServiceKey a = service("yuheng-a", "svc-a");
        DdcManagementServiceKey b = service("yuheng-b", "svc-b");
        when(facade.getServiceKeys(query)).thenReturn(
                new DdcManagementServiceCatalog(
                        9L, Instant.EPOCH, List.of(b, a)
                )
        );

        DdcPageResult<DdcManagementServiceKey> page = service.pageServices(
                query, new PageQuery(1, 1)
        );

        assertThat(page.getRecords()).containsExactly(a);
        assertThat(page.getTotal()).isEqualTo(2);
        verify(facade).getServiceKeys(query);
    }

    @Test
    void pagesInstancesInStableStatusAndEndpointOrder() {
        DdcManagementServiceKey key = service("yuheng", "svc");
        DdcManagementServiceInstance a = instance(
                "instance-a", null, 19000, "UNKNOWN"
        );
        DdcManagementServiceInstance b = instance(
                "instance-b", null, 19001, "UNKNOWN"
        );
        DdcManagementServiceInstance c = instance(
                "instance-c", null, 19000, "UNKNOWN"
        );
        when(facade.getInstances(query)).thenReturn(
                new DdcManagementServiceSnapshot(
                        key, 10L, Instant.EPOCH, List.of(c, b, a)
                )
        );

        DdcPageResult<DdcManagementServiceInstance> page = service.pageInstances(
                query, new PageQuery(1, 2)
        );

        assertThat(page.getRecords()).containsExactly(a, c);
        assertThat(page.getTotal()).isEqualTo(3);
        verify(facade).getInstances(query);
    }

    @Test
    void pagesTheExplicitBindingAndNeverConvertsAnObservationFailureToEmptySuccess() {
        when(facade.getInstances(query, "binding-a")).thenReturn(
                new DdcManagementServiceSnapshot(service("yuheng", "binding-a"), 0L, Instant.EPOCH, List.of()));
        assertThat(service.pageInstances(query, new PageQuery(1, 10), "binding-a").getRecords()).isEmpty();
        verify(facade).getInstances(query, "binding-a");
        var failure = new CommonException(503, "TIANSHU_SERVICE_OBSERVATION_UNAVAILABLE", "unavailable", true, null);
        when(facade.getServiceKeys(query)).thenThrow(failure);
        assertThatThrownBy(() -> service.pageServices(query, new PageQuery(1, 10))).isSameAs(failure);
    }

    private DdcManagementServiceKey service(
            String appCode,
            String serviceId
    ) {
        return new DdcManagementServiceKey(
                "infra", "prod", appCode, serviceId,
                "RPC_PROVIDER", "EchoService", "default", "1.0.0", "grpc"
        );
    }

    private DdcManagementServiceInstance instance(
            String instanceId,
            String host,
            int port,
            String status
    ) {
        return new DdcManagementServiceInstance(
                instanceId, null, host, port, false,
                Map.of("observationSource", "KUBERNETES"), status, null, null, null
        );
    }
}
