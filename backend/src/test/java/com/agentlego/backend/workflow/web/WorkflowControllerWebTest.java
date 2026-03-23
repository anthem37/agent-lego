package com.agentlego.backend.workflow.web;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.workflow.application.dto.RunWorkflowResponse;
import com.agentlego.backend.workflow.application.dto.WorkflowRunDto;
import com.agentlego.backend.workflow.application.service.WorkflowApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkflowController.class)
@Import(GlobalExceptionHandler.class)
class WorkflowControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowApplicationService workflowApplicationService;

    @Test
    void createWorkflow_missingName_shouldReturn400() throws Exception {
        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definition":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createWorkflow_ok_shouldReturnCreated() throws Exception {
        when(workflowApplicationService.createWorkflow(any())).thenReturn("w1");

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"wf1","definition":{"agentId":"a1","modelId":"m1"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data").value("w1"));
    }

    @Test
    void runWorkflow_ok_shouldReturnRunId() throws Exception {
        RunWorkflowResponse resp = new RunWorkflowResponse();
        resp.setRunId("r1");
        resp.setStatus("RUNNING");

        when(workflowApplicationService.runWorkflow(eq("w1"), any())).thenReturn(resp);

        mockMvc.perform(post("/workflows/w1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":"hi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.runId").value("r1"));
    }

    @Test
    void getRun_ok_shouldReturnRun() throws Exception {
        WorkflowRunDto dto = new WorkflowRunDto();
        dto.setId("r1");
        dto.setWorkflowId("w1");
        dto.setStatus("SUCCEEDED");
        dto.setInput(Map.of("input", "hi"));
        dto.setOutput(Map.of("output", "ok"));
        dto.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(workflowApplicationService.getRun("r1")).thenReturn(dto);

        mockMvc.perform(get("/runs/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("r1"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }
}

