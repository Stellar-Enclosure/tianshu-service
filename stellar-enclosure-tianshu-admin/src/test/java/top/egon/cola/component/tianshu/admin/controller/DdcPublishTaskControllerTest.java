package top.egon.cola.component.tianshu.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.controller.config.DdcPublishTaskController;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishTaskQueryRequest;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcPublishTaskResultConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishResultVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublishTaskResult;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishTaskQueryService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.context.annotation.Import(top.egon.cola.component.tianshu.admin.config.DdcTransportSliceTestConfiguration.class)
@WebMvcTest(DdcPublishTaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class DdcPublishTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DdcPublishTaskRepository taskRepository;

    @MockitoBean
    private DdcPublishService publishService;

    @MockitoBean
    private DdcPublishTaskQueryService publishTaskQueryService;

    @MockitoBean
    private DdcPublishTaskResultConverter resultConverter;

    @Test
    void pagesPublishTasks() throws Exception {
        DdcPublishTaskBO task = DdcPublishTaskBO.builder().publicId("task-1")
                .changeId("change-1").status("FAILED").build();
        DdcPublishTaskResult result = DdcPublishTaskResult.builder()
                .publicId("task-1").changeId("change-1").status("FAILED").build();
        when(publishTaskQueryService.pageResults(
                any(DdcPublishTaskQueryRequest.class),
                any(PageQuery.class)
        )).thenReturn(DdcPageResult.<DdcPublishTaskResult>builder()
                .records(List.of(result)).total(11).pageNo(1).pageSize(10).build());

        mockMvc.perform(get("/api/v1/tianshu/publish-tasks/page")
                        .param("bizCode", "infra")
                        .param("env", "prod")
                        .param("appCode", "yuheng")
                        .param("status", "FAILED")
                        .param("changeId", "change")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].id").value("task-1"))
                .andExpect(jsonPath("$.records[0].publicId").doesNotExist())
                .andExpect(jsonPath("$.records[0].changeId").value("change-1"))
                .andExpect(jsonPath("$.page.total").value(11))
                .andExpect(jsonPath("$.page.pageNo").value(1))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void retryUsesOriginalChangeId() throws Exception {
        DdcPublishResultVO result = new DdcPublishResultVO();
        result.setChangeId("change-1");
        result.setStatus("SUCCESS");
        when(publishService.retry("change-1")).thenReturn(result);

        mockMvc.perform(post("/api/v1/tianshu/publish-tasks/change-1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.changeId").value("change-1"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
    @Test
    void listAndDetailUseTheSameReadOnlyEvidenceProjection() throws Exception {
        var projected = DdcPublishTaskResult.builder().publicId("task-current").changeId("change-current")
                .historyOnly(false).platformState("APPLIED").rolloutState("PENDING")
                .processState("PUBLISHED_WAITING_FOR_CONSUMERS").desiredRevision("43").build();
        when(publishTaskQueryService.listResults()).thenReturn(List.of(projected));
        when(publishTaskQueryService.detailResult("change-current")).thenReturn(java.util.Optional.of(projected));
        mockMvc.perform(get("/api/v1/tianshu/publish-tasks"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].processState").value("PUBLISHED_WAITING_FOR_CONSUMERS"));
        mockMvc.perform(get("/api/v1/tianshu/publish-tasks/change-current"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.desiredRevision").value("43"))
                .andExpect(jsonPath("$.data.platformState").value("APPLIED"));
        org.mockito.Mockito.verifyNoInteractions(taskRepository, resultConverter, publishService);
    }

}
