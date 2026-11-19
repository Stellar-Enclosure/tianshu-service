package top.egon.cola.component.tianshu.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.config.DdcTransportSliceTestConfiguration;
import top.egon.cola.component.tianshu.admin.controller.register.DdcInstanceController;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.PodObservationVO;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.WorkloadViewVO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcInstanceWorkloadConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcInstanceWorkloadConverterImpl;
import top.egon.cola.component.tianshu.admin.model.vo.DdcInstanceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.service.lease.DdcInstanceAdminService;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({DdcTransportSliceTestConfiguration.class, DdcInstanceControllerTest.WorkloadProjectionConfiguration.class})
@WebMvcTest(DdcInstanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class DdcInstanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "ddcInstanceAdminService")
    private DdcInstanceAdminService instanceAdminService;

    @TestConfiguration(proxyBeanMethods = false)
    static class WorkloadProjectionConfiguration {
        @Bean("ddcInstanceWorkloadConverterImpl")
        DdcInstanceWorkloadConverter workloadConverter() {
            return new DdcInstanceWorkloadConverterImpl();
        }
    }

    @Test
    void pagesArchivedLeaseHistorySeparately() throws Exception {
        DdcInstanceResult instance = DdcInstanceResult.builder()
                .publicId("row-1").instanceId("yuheng-1").build();
        when(instanceAdminService.historyPage(
                eq("infra"), eq("prod"), eq("yuheng"),
                any(PageQuery.class))).thenReturn(DdcPageResult.<DdcInstanceResult>builder()
                .records(List.of(instance)).total(2).pageNo(1).pageSize(10).build());

        mockMvc.perform(get("/api/v1/tianshu/instances/history/page")
                        .param("bizCode", "infra")
                        .param("env", "prod")
                        .param("appCode", "yuheng")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].id").value("row-1"))
                .andExpect(jsonPath("$.records[0].publicId").doesNotExist())
                .andExpect(jsonPath("$.records[0].instanceId")
                        .value("yuheng-1"))
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.page.pageNo").value(1))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void pagesObservedWorkloadsWithoutLeakingInternalEvidence() throws Exception {
        var target = new TargetBindingVO("binding-1", "1", "cluster-1", "platform",
                "Deployment", "tianshu", "workload-uid", "admin");
        var observedAt = Instant.parse("2026-11-19T08:00:00Z");
        var pod = new PodObservationVO("pod-uid", "tianshu-pod", "Running", true,
                "admin", "containerd://internal", 1, observedAt);
        var current = new WorkloadViewVO(target, StatusEnum.FRESH, null, observedAt, observedAt.plusSeconds(15),
                null, "workload-uid", 4, 4, 1, List.of(pod), List.of(),
                Map.of("internal-annotation", "value"), Map.of("private-config-map", "id"));
        when(instanceAdminService.page(eq("infra"), eq("prod"), eq("tianshu"), any(PageQuery.class)))
                .thenReturn(DdcPageResult.<WorkloadViewVO>builder()
                        .records(List.of(current)).total(1).pageNo(1).pageSize(10).build());

        mockMvc.perform(get("/api/v1/tianshu/instances/page")
                        .param("bizCode", "infra").param("env", "prod").param("appCode", "tianshu")
                        .param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].targetBindingId").value("binding-1"))
                .andExpect(jsonPath("$.records[0].status").value("FRESH"))
                .andExpect(jsonPath("$.records[0].observedAt").value(observedAt.toString()))
                .andExpect(jsonPath("$.records[0].desiredReplicas").value(1))
                .andExpect(jsonPath("$.records[0].pods[0].podUid").value("pod-uid"))
                .andExpect(jsonPath("$.records[0].pods[0].ready").value(true))
                .andExpect(jsonPath("$.records[0].pods[0].containerId").doesNotExist())
                .andExpect(jsonPath("$.records[0].templateAnnotations").doesNotExist())
                .andExpect(jsonPath("$.records[0].configMapReferences").doesNotExist())
                .andExpect(jsonPath("$.records[0].leaseId").doesNotExist());
    }
}
