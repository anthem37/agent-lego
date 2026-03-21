package com.agentlego.backend.workflow.application.service;

import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.workflow.application.dto.RunWorkflowRequest;
import com.agentlego.backend.workflow.application.dto.RunWorkflowResponse;
import com.agentlego.backend.workflow.application.mapper.WorkflowDtoMapper;
import com.agentlego.backend.workflow.domain.WorkflowAggregate;
import com.agentlego.backend.workflow.domain.WorkflowRepository;
import com.agentlego.backend.workflow.domain.WorkflowRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowApplicationService 单元测试。
 * <p>
 * 说明：通过注入同步 executor（Runnable::run）使异步逻辑可重复（deterministic）。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowApplicationServiceTest {

    private static final WorkflowDtoMapper WORKFLOW_DTO_MAPPER = Mappers.getMapper(WorkflowDtoMapper.class);
    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkflowRunRepository workflowRunRepository;
    @Mock
    private AgentApplicationService agentApplicationService;

    private static RunWorkflowRequest buildRunReq(String input) {
        RunWorkflowRequest req = new RunWorkflowRequest();
        req.setInput(input);
        return req;
    }

    private static RunAgentResponse buildAgentResp(String output) {
        RunAgentResponse resp = new RunAgentResponse();
        resp.setOutput(output);
        return resp;
    }

    @Test
    void runWorkflow_singleAgentDefinition_shouldMarkSucceededWithOutput() {
        // Use synchronous executor so the async workflow body runs immediately.
        WorkflowApplicationService service = new WorkflowApplicationService(
                workflowRepository,
                workflowRunRepository,
                agentApplicationService,
                Runnable::run,
                WORKFLOW_DTO_MAPPER
        );

        when(workflowRunRepository.createRun(eq("w1"), anyMap(), isNull()))
                .thenReturn("run1");

        WorkflowAggregate workflow = new WorkflowAggregate();
        workflow.setId("w1");
        workflow.setName("wf");
        workflow.setDefinition(Map.of("agentId", "a1", "modelId", "m1"));

        when(workflowRepository.findById("w1")).thenReturn(Optional.of(workflow));

        when(agentApplicationService.runAgent(eq("a1"), any(RunAgentRequest.class)))
                .thenReturn(buildAgentResp("out1"));

        RunWorkflowResponse resp = service.runWorkflow("w1", buildRunReq("input1"));

        assertEquals("run1", resp.getRunId());
        assertEquals("RUNNING", resp.getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> outputCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(workflowRunRepository).markSucceeded(eq("run1"), outputCaptor.capture());
        assertEquals("out1", outputCaptor.getValue().get("output"));
    }

    @Test
    void runWorkflow_multiAgent_sequential_shouldChainInputsAndCollectOutputs() {
        WorkflowApplicationService service = new WorkflowApplicationService(
                workflowRepository,
                workflowRunRepository,
                agentApplicationService,
                Runnable::run,
                WORKFLOW_DTO_MAPPER
        );

        when(workflowRunRepository.createRun(eq("w1"), anyMap(), isNull()))
                .thenReturn("run1");

        List<Map<String, Object>> steps = List.of(
                Map.of("agentId", "a1", "modelId", "m1"),
                Map.of("agentId", "a2", "modelId", "m2")
        );

        WorkflowAggregate workflow = new WorkflowAggregate();
        workflow.setId("w1");
        workflow.setDefinition(Map.of("steps", steps, "mode", "sequential"));
        when(workflowRepository.findById("w1")).thenReturn(Optional.of(workflow));

        when(agentApplicationService.runAgent(eq("a1"), any(RunAgentRequest.class)))
                .thenReturn(buildAgentResp("out1"));
        when(agentApplicationService.runAgent(eq("a2"), any(RunAgentRequest.class)))
                .thenReturn(buildAgentResp("out2"));

        service.runWorkflow("w1", buildRunReq("input0"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> outputCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(workflowRunRepository).markSucceeded(eq("run1"), outputCaptor.capture());
        assertEquals(List.of("out1", "out2"), outputCaptor.getValue().get("outputs"));
    }

    @Test
    void runWorkflow_multiAgent_parallel_shouldExecuteAllStepsAndCollectOutputs() {
        WorkflowApplicationService service = new WorkflowApplicationService(
                workflowRepository,
                workflowRunRepository,
                agentApplicationService,
                Runnable::run,
                WORKFLOW_DTO_MAPPER
        );

        when(workflowRunRepository.createRun(eq("w1"), anyMap(), isNull()))
                .thenReturn("run1");

        List<Map<String, Object>> steps = List.of(
                Map.of("agentId", "a1", "modelId", "m1"),
                Map.of("agentId", "a2", "modelId", "m2")
        );

        WorkflowAggregate workflow = new WorkflowAggregate();
        workflow.setId("w1");
        workflow.setDefinition(Map.of("steps", steps, "mode", "parallel"));
        when(workflowRepository.findById("w1")).thenReturn(Optional.of(workflow));

        when(agentApplicationService.runAgent(eq("a1"), any(RunAgentRequest.class)))
                .thenReturn(buildAgentResp("p1"));
        when(agentApplicationService.runAgent(eq("a2"), any(RunAgentRequest.class)))
                .thenReturn(buildAgentResp("p2"));

        service.runWorkflow("w1", buildRunReq("input0"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> outputCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(workflowRunRepository).markSucceeded(eq("run1"), outputCaptor.capture());
        assertEquals(List.of("p1", "p2"), outputCaptor.getValue().get("outputs"));
    }

    @Test
    void runWorkflow_invalidSingleAgentDefinition_shouldMarkFailed() {
        WorkflowApplicationService service = new WorkflowApplicationService(
                workflowRepository,
                workflowRunRepository,
                agentApplicationService,
                Runnable::run,
                WORKFLOW_DTO_MAPPER
        );

        when(workflowRunRepository.createRun(eq("w1"), anyMap(), isNull()))
                .thenReturn("run1");

        WorkflowAggregate workflow = new WorkflowAggregate();
        workflow.setId("w1");
        workflow.setDefinition(Map.of("agentId", "", "modelId", ""));
        when(workflowRepository.findById("w1")).thenReturn(Optional.of(workflow));

        service.runWorkflow("w1", buildRunReq("input0"));

        verify(workflowRunRepository).markFailed(eq("run1"), anyString());
    }

    @Test
    void runWorkflowSynchronously_singleAgent_shouldReturnBodyAndMarkSucceeded() {
        WorkflowApplicationService service = new WorkflowApplicationService(
                workflowRepository,
                workflowRunRepository,
                agentApplicationService,
                Runnable::run,
                WORKFLOW_DTO_MAPPER
        );

        when(workflowRunRepository.createRun(eq("w1"), anyMap(), isNull())).thenReturn("run-sync-1");

        WorkflowAggregate workflow = new WorkflowAggregate();
        workflow.setId("w1");
        workflow.setDefinition(Map.of("agentId", "a1", "modelId", "m1"));
        when(workflowRepository.findById("w1")).thenReturn(Optional.of(workflow));

        when(agentApplicationService.runAgent(eq("a1"), any(RunAgentRequest.class)))
                .thenReturn(buildAgentResp("sync-out"));

        Map<String, Object> body = service.runWorkflowSynchronously("w1", buildRunReq("hi"));

        assertEquals("run-sync-1", body.get("runId"));
        assertEquals("SUCCEEDED", body.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) body.get("output");
        assertEquals("sync-out", out.get("output"));
        verify(workflowRunRepository).markRunning("run-sync-1");
        verify(workflowRunRepository).markSucceeded(eq("run-sync-1"), anyMap());
    }

    @Test
    void runWorkflowSynchronously_whenWorkflowMissing_shouldMarkFailedAndThrow() {
        WorkflowApplicationService service = new WorkflowApplicationService(
                workflowRepository,
                workflowRunRepository,
                agentApplicationService,
                Runnable::run,
                WORKFLOW_DTO_MAPPER
        );

        when(workflowRunRepository.createRun(eq("missing"), anyMap(), isNull())).thenReturn("run-bad");
        when(workflowRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.runWorkflowSynchronously("missing", buildRunReq("x")));

        verify(workflowRunRepository).markFailed(eq("run-bad"), anyString());
    }
}

