package com.agentlego.backend.eval;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.eval.application.EvaluationApplicationService;
import com.agentlego.backend.eval.application.dto.RunEvaluationDto;
import com.agentlego.backend.eval.application.dto.RunEvaluationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EvaluationController.class)
@Import(GlobalExceptionHandler.class)
class EvaluationControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvaluationApplicationService evaluationApplicationService;

    @Test
    void createEvaluation_missingCases_shouldReturn400() throws Exception {
        mockMvc.perform(post("/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":"a1","modelId":"m1","name":"e1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createEvaluation_ok_shouldReturnCreated() throws Exception {
        when(evaluationApplicationService.createEvaluation(any())).thenReturn("e1");

        mockMvc.perform(post("/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":"a1","modelId":"m1","name":"e1","cases":[{"input":"i","expectedOutput":"o"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data").value("e1"));
    }

    @Test
    void runEvaluation_ok_shouldReturnRunId() throws Exception {
        RunEvaluationResponse resp = new RunEvaluationResponse();
        resp.setRunId("r1");
        resp.setStatus("RUNNING");
        when(evaluationApplicationService.runEvaluation("e1")).thenReturn(resp);

        mockMvc.perform(post("/evaluations/e1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.runId").value("r1"));
    }

    @Test
    void getRun_ok_shouldReturnDto() throws Exception {
        RunEvaluationDto dto = new RunEvaluationDto();
        dto.setId("r1");
        dto.setEvaluationId("e1");
        dto.setStatus("SUCCEEDED");
        dto.setMetrics(Map.of("accuracy", 1.0));
        dto.setTrace(Map.of());
        dto.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(evaluationApplicationService.getRun("r1")).thenReturn(dto);

        mockMvc.perform(get("/evaluations/runs/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("r1"))
                .andExpect(jsonPath("$.data.metrics.accuracy").value(1.0));
    }
}

