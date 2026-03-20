package com.agentlego.backend.eval.application;

import com.agentlego.backend.agent.application.AgentApplicationService;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.eval.application.dto.CreateEvaluationRequest;
import com.agentlego.backend.eval.application.dto.EvalCaseDto;
import com.agentlego.backend.eval.application.dto.RunEvaluationResponse;
import com.agentlego.backend.eval.domain.EvaluationAggregate;
import com.agentlego.backend.eval.domain.EvaluationRepository;
import com.agentlego.backend.eval.domain.EvaluationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EvaluationApplicationService 单元测试。
 * <p>
 * 说明：
 * - 通过注入同步 Executor（Runnable::run）让异步评测逻辑可重复（deterministic）；
 * - 覆盖 create、run（正常/空 cases/not found）等核心分支。
 */
@ExtendWith(MockitoExtension.class)
class EvaluationApplicationServiceTest {

    private final Executor syncExecutor = Runnable::run;
    @Mock
    private EvaluationRepository evaluationRepository;
    @Mock
    private EvaluationRunRepository evaluationRunRepository;
    @Mock
    private AgentApplicationService agentApplicationService;

    private static EvalCaseDto caseDto(String input, String expectedOutput) {
        EvalCaseDto c = new EvalCaseDto();
        c.setInput(input);
        c.setExpectedOutput(expectedOutput);
        return c;
    }

    private static RunAgentResponse runAgentResp(String output) {
        RunAgentResponse resp = new RunAgentResponse();
        resp.setOutput(output);
        return resp;
    }

    @Test
    void createEvaluation_shouldPersistAgentAndModelAndCases() {
        // package-private testing constructor injection
        EvaluationApplicationService service = new EvaluationApplicationService(
                evaluationRepository,
                evaluationRunRepository,
                agentApplicationService,
                syncExecutor
        );

        CreateEvaluationRequest req = new CreateEvaluationRequest();
        req.setAgentId("agent1");
        req.setModelId("model1");
        req.setName("eval1");
        req.setCases(List.of(
                caseDto("in1", "out1"),
                caseDto("in2", "out2")
        ));

        when(evaluationRepository.save(any())).thenReturn("eval-run-id");

        String id = service.createEvaluation(req);
        assertEquals("eval-run-id", id);

        ArgumentCaptor<EvaluationAggregate> captor = ArgumentCaptor.forClass(EvaluationAggregate.class);
        verify(evaluationRepository).save(captor.capture());
        EvaluationAggregate saved = captor.getValue();
        assertEquals("agent1", saved.getAgentId());
        assertEquals("eval1", saved.getName());
        assertNotNull(saved.getConfig());
        assertEquals("model1", saved.getConfig().get("modelId"));
        assertNotNull(saved.getConfig().get("cases"));
        assertEquals(2, ((List<?>) saved.getConfig().get("cases")).size());
    }

    @Test
    void runEvaluation_shouldComputeAccuracyAndTrace() {
        EvaluationApplicationService service = new EvaluationApplicationService(
                evaluationRepository,
                evaluationRunRepository,
                agentApplicationService,
                syncExecutor
        );

        when(evaluationRunRepository.createRun(eq("e1"), anyMap())).thenReturn("r1");
        doNothing().when(evaluationRunRepository).markRunning("r1");

        EvaluationAggregate eval = new EvaluationAggregate();
        eval.setAgentId("agent1");
        eval.setConfig(Map.of(
                "modelId", "modelX",
                "cases", List.of(
                        Map.of("input", "i1", "expectedOutput", "a1"),
                        Map.of("input", "i2", "expectedOutput", "wrong")
                )
        ));

        when(evaluationRepository.findById("e1")).thenReturn(Optional.of(eval));

        when(agentApplicationService.runAgent(eq("agent1"), any(RunAgentRequest.class)))
                .thenReturn(runAgentResp("a1"))  // i1 expected a1 -> pass
                .thenReturn(runAgentResp("a2")); // i2 expected wrong -> fail

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metricsCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> traceCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);

        RunEvaluationResponse resp = service.runEvaluation("e1");
        assertEquals("r1", resp.getRunId());
        assertEquals("RUNNING", resp.getStatus());

        verify(evaluationRunRepository).markSucceeded(eq("r1"), metricsCaptor.capture(), traceCaptor.capture());
        assertEquals(0.5, (double) metricsCaptor.getValue().get("accuracy"));
        assertEquals(1, metricsCaptor.getValue().get("passed"));
        assertEquals(2, metricsCaptor.getValue().get("total"));

        Object casesObj = traceCaptor.getValue().get("cases");
        assertTrue(casesObj instanceof List);
        List<?> cases = (List<?>) casesObj;
        assertEquals(2, cases.size());
    }

    @Test
    void runEvaluation_emptyCases_shouldReturnZeroAccuracy() {
        EvaluationApplicationService service = new EvaluationApplicationService(
                evaluationRepository,
                evaluationRunRepository,
                agentApplicationService,
                syncExecutor
        );

        when(evaluationRunRepository.createRun(eq("e1"), anyMap())).thenReturn("r1");
        EvaluationAggregate eval = new EvaluationAggregate();
        eval.setAgentId("agent1");
        eval.setConfig(Map.of(
                "modelId", "modelX",
                "cases", List.of()
        ));
        when(evaluationRepository.findById("e1")).thenReturn(Optional.of(eval));

        RunEvaluationResponse resp = service.runEvaluation("e1");
        assertEquals("r1", resp.getRunId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metricsCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(evaluationRunRepository).markSucceeded(eq("r1"), metricsCaptor.capture(), anyMap());
        assertEquals(0.0, (double) metricsCaptor.getValue().get("accuracy"));
        assertEquals(0, metricsCaptor.getValue().get("passed"));
        assertEquals(0, metricsCaptor.getValue().get("total"));
    }

    @Test
    void runEvaluation_notFound_shouldMarkFailed() {
        EvaluationApplicationService service = new EvaluationApplicationService(
                evaluationRepository,
                evaluationRunRepository,
                agentApplicationService,
                syncExecutor
        );

        when(evaluationRunRepository.createRun(eq("e1"), anyMap())).thenReturn("r1");
        when(evaluationRepository.findById("e1")).thenReturn(Optional.empty());

        service.runEvaluation("e1");

        verify(evaluationRunRepository).markFailed(eq("r1"), anyString());
    }
}

