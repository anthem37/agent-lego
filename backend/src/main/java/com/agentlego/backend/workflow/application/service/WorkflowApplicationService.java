package com.agentlego.backend.workflow.application.service;

import com.agentlego.backend.agent.application.AgentRunRequests;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.common.Throwables;
import com.agentlego.backend.workflow.application.dto.*;
import com.agentlego.backend.workflow.application.mapper.WorkflowDtoMapper;
import com.agentlego.backend.workflow.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 工作流用例编排：落库 run、异步/同步执行、写回结果；定义暂为 jsonb Map（steps/mode 或 agentId/modelId）。
 */
@Service
public class WorkflowApplicationService {
    private static final String DEF_STEPS = "steps";
    private static final String DEF_MODE = "mode";
    private static final String DEF_AGENT_ID = "agentId";
    private static final String DEF_MODEL_ID = "modelId";
    private static final String MODE_SEQUENTIAL = "sequential";
    private static final String MODE_PARALLEL = "parallel";

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final AgentApplicationService agentApplicationService;
    private final Executor executor;
    private final WorkflowDtoMapper workflowDtoMapper;

    @Autowired
    public WorkflowApplicationService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            AgentApplicationService agentApplicationService,
            WorkflowDtoMapper workflowDtoMapper
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.workflowDtoMapper = workflowDtoMapper;
        this.executor = ForkJoinPool.commonPool();
    }

    /**
     * 测试用：可注入同步 Executor。
     */
    WorkflowApplicationService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            AgentApplicationService agentApplicationService,
            Executor executor,
            WorkflowDtoMapper workflowDtoMapper
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.executor = executor;
        this.workflowDtoMapper = workflowDtoMapper;
    }

    public String createWorkflow(CreateWorkflowRequest req) {
        if (req.getDefinition() == null) {
            req.setDefinition(Map.of());
        }
        WorkflowAggregate agg = new WorkflowAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setName(req.getName());
        agg.setDefinition(req.getDefinition());
        agg.setCreatedAt(Instant.now());
        return workflowRepository.save(agg);
    }

    public WorkflowDto getWorkflow(String workflowId) {
        WorkflowAggregate agg = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工作流未找到", HttpStatus.NOT_FOUND));
        return workflowDtoMapper.toWorkflowDto(agg);
    }

    public RunWorkflowResponse runWorkflow(String workflowId, RunWorkflowRequest req) {
        // 先落库创建 run 记录，再异步执行，保证“可追踪/可查询”。
        // 这样即使执行过程中失败，也能通过 runId 定位到错误原因与执行输入。
        String idempotencyKey = null;
        Map<String, Object> input = Map.of("input", req.getInput());

        String runId = workflowRunRepository.createRun(workflowId, input, idempotencyKey);
        workflowRunRepository.markRunning(runId);

        CompletableFuture.runAsync(() -> executeWorkflowRun(runId, workflowId, req), executor);

        RunWorkflowResponse resp = new RunWorkflowResponse();
        resp.setRunId(runId);
        resp.setStatus(WorkflowRunStatus.RUNNING.name());
        return resp;
    }

    /**
     * 同步执行并落库（工具调用等需立即结果）；与 {@link #runWorkflow} 异步触发相对。
     */
    public Map<String, Object> runWorkflowSynchronously(String workflowId, RunWorkflowRequest req) {
        String runId = workflowRunRepository.createRun(workflowId, Map.of("input", req.getInput()), null);
        workflowRunRepository.markRunning(runId);
        try {
            Map<String, Object> output = computeWorkflowOutputOrThrow(workflowId, req);
            workflowRunRepository.markSucceeded(runId, output);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("runId", runId);
            body.put("status", WorkflowRunStatus.SUCCEEDED.name());
            body.put("output", output);
            return body;
        } catch (Exception e) {
            String msg = Throwables.messageOrSimpleName(e);
            workflowRunRepository.markFailed(runId, msg);
            if (e instanceof ApiException ae) {
                throw ae;
            }
            throw new ApiException("WORKFLOW_RUN_FAILED", "工作流执行失败：" + msg, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 异步路径：捕获异常并 markFailed，避免状态无法落库。
     */
    private void executeWorkflowRun(String runId, String workflowId, RunWorkflowRequest req) {
        try {
            Map<String, Object> output = computeWorkflowOutputOrThrow(workflowId, req);
            workflowRunRepository.markSucceeded(runId, output);
        } catch (Exception e) {
            workflowRunRepository.markFailed(runId, Throwables.messageOrSimpleName(e));
        }
    }

    private Map<String, Object> computeWorkflowOutputOrThrow(String workflowId, RunWorkflowRequest req) {
        WorkflowAggregate workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工作流未找到", HttpStatus.NOT_FOUND));
        Map<String, Object> def = workflow.getDefinition() == null ? Map.of() : workflow.getDefinition();
        return buildWorkflowOutput(def, req);
    }

    /**
     * multi-agent：`steps`+`mode`；单智能体：`agentId`+`modelId`。
     */
    private Map<String, Object> buildWorkflowOutput(Map<String, Object> def, RunWorkflowRequest req) {
        List<Map<String, Object>> steps = JsonMaps.getListOfMaps(def, DEF_STEPS);

        if (!steps.isEmpty()) {
            return runMultiAgentSteps(def, steps, req);
        }
        return runSingleAgent(def, req);
    }

    private Map<String, Object> runMultiAgentSteps(Map<String, Object> def, List<Map<String, Object>> steps, RunWorkflowRequest req) {
        String mode = JsonMaps.getString(def, DEF_MODE, MODE_SEQUENTIAL);
        if (MODE_PARALLEL.equalsIgnoreCase(mode)) {
            return runParallel(steps, req);
        }
        return runSequential(steps, req);
    }

    private Map<String, Object> runParallel(List<Map<String, Object>> steps, RunWorkflowRequest req) {
        List<CompletableFuture<String>> futures = steps.stream()
                .map(step -> CompletableFuture.supplyAsync(() -> runOneStep(step, req.getInput()), executor))
                .toList();
        List<String> outputs = futures.stream().map(CompletableFuture::join).toList();
        return Map.of("outputs", outputs);
    }

    private Map<String, Object> runSequential(List<Map<String, Object>> steps, RunWorkflowRequest req) {
        String currentInput = req.getInput();
        java.util.ArrayList<String> outputs = new java.util.ArrayList<>();
        for (Map<String, Object> step : steps) {
            currentInput = runOneStep(step, currentInput);
            outputs.add(currentInput);
        }
        return Map.of("outputs", outputs);
    }

    private Map<String, Object> runSingleAgent(Map<String, Object> def, RunWorkflowRequest req) {
        String agentId = JsonMaps.getString(def, DEF_AGENT_ID, "");
        String modelId = JsonMaps.getString(def, DEF_MODEL_ID, "");
        if (agentId.isBlank() || modelId.isBlank()) {
            throw new ApiException("INVALID_WORKFLOW_DEF", "definition 必须包含 steps[] 或 agentId/modelId", HttpStatus.BAD_REQUEST);
        }
        RunAgentResponse agentResp = agentApplicationService.runAgent(agentId, AgentRunRequests.of(modelId, req.getInput()));
        return Map.of("output", agentResp.getOutput());
    }

    private String runOneStep(Map<String, Object> step, String input) {
        String agentId = JsonMaps.getString(step, DEF_AGENT_ID, "");
        String modelId = JsonMaps.getString(step, DEF_MODEL_ID, "");
        return agentApplicationService.runAgent(agentId, AgentRunRequests.of(modelId, input)).getOutput();
    }

    public WorkflowRunDto getRun(String runId) {
        WorkflowRunAggregate run = workflowRunRepository.findById(runId);
        if (run == null) {
            throw new ApiException("NOT_FOUND", "运行记录未找到", HttpStatus.NOT_FOUND);
        }
        return workflowDtoMapper.toRunDto(run);
    }
}

