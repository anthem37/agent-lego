package com.agentlego.backend.workflow.application;

import com.agentlego.backend.agent.application.AgentApplicationService;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.workflow.application.dto.*;
import com.agentlego.backend.workflow.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 工作流应用服务（Application Service）。
 * <p>
 * 设计取舍：
 * - 当前实现是“轻量版 Runner”：先落库创建 run，再异步执行写回结果；
 * - definition 的结构以 Map 形式存入 jsonb，先保证灵活性，后续再引入强类型定义（DAG/状态机）；
 * - multi-agent 的能力在这里以最小方式接入（steps + mode），后续会扩展到 Router/A2A 委派。
 */
@Service
public class WorkflowApplicationService {
    private static final String DEF_STEPS = "steps";
    private static final String DEF_MODE = "mode";
    private static final String DEF_AGENT_ID = "agentId";
    private static final String DEF_MODEL_ID = "modelId";
    private static final String MODE_SEQUENTIAL = "sequential";
    private static final String MODE_PARALLEL = "parallel";

    /**
     * 工作流定义仓库（读写 platform_workflows）。
     */
    private final WorkflowRepository workflowRepository;
    /**
     * 工作流运行仓库（读写 platform_workflow_runs）。
     */
    private final WorkflowRunRepository workflowRunRepository;
    /**
     * 智能体执行入口（当前复用平台本地 agent runtime）。
     */
    private final AgentApplicationService agentApplicationService;
    /**
     * 异步执行器：生产默认 ForkJoinPool；测试可注入同步 executor。
     */
    private final Executor executor;

    @Autowired
    public WorkflowApplicationService(WorkflowRepository workflowRepository, WorkflowRunRepository workflowRunRepository, AgentApplicationService agentApplicationService) {
        this.workflowRepository = workflowRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.executor = ForkJoinPool.commonPool();
    }

    /**
     * 测试专用构造器：允许注入 Executor，让异步逻辑在单测里可控（例如用 Runnable::run 同步执行）。
     */
    WorkflowApplicationService(WorkflowRepository workflowRepository, WorkflowRunRepository workflowRunRepository, AgentApplicationService agentApplicationService, Executor executor) {
        this.workflowRepository = workflowRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.executor = executor;
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
        WorkflowAggregate agg = workflowRepository.findById(workflowId).orElseThrow(() -> new ApiException("NOT_FOUND", "workflow not found", HttpStatus.NOT_FOUND));
        WorkflowDto dto = new WorkflowDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setDefinition(agg.getDefinition());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
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
     * 执行一次工作流运行，并将结果写回仓库。
     * <p>
     * 说明：该方法内部捕获所有异常，避免异步线程异常导致运行状态无法落库。
     */
    private void executeWorkflowRun(String runId, String workflowId, RunWorkflowRequest req) {
        try {
            WorkflowAggregate workflow = workflowRepository.findById(workflowId)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "workflow not found", HttpStatus.NOT_FOUND));

            Map<String, Object> def = workflow.getDefinition() == null ? Map.of() : workflow.getDefinition();
            Map<String, Object> output = buildWorkflowOutput(def, req);
            workflowRunRepository.markSucceeded(runId, output);
        } catch (Exception e) {
            String msg = (e.getMessage() == null) ? e.getClass().getSimpleName() : e.getMessage();
            workflowRunRepository.markFailed(runId, msg);
        }
    }

    /**
     * 根据工作流定义计算输出。
     * <p>
     * 定义支持：
     * - multi-agent：definition.steps + definition.mode
     * - single-agent（兼容历史）：definition.agentId + definition.modelId
     */
    private Map<String, Object> buildWorkflowOutput(Map<String, Object> def, RunWorkflowRequest req) {
        // 统一 key 为 String，避免来自 JSON 的 key 类型不一致导致后续取值异常。
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
        // 并行模式：所有 step 使用相同的初始 input，最终按 steps 顺序收集 outputs。
        List<CompletableFuture<String>> futures = steps.stream()
                .map(step -> CompletableFuture.supplyAsync(() -> runOneStep(step, req.getInput()), executor))
                .toList();
        List<String> outputs = futures.stream().map(CompletableFuture::join).toList();
        return Map.of("outputs", outputs);
    }

    private Map<String, Object> runSequential(List<Map<String, Object>> steps, RunWorkflowRequest req) {
        // 串行模式：上一步的输出作为下一步的输入，适合“分解-执行-汇总”类链路。
        String currentInput = req.getInput();
        java.util.ArrayList<String> outputs = new java.util.ArrayList<>();
        for (Map<String, Object> step : steps) {
            currentInput = runOneStep(step, currentInput);
            outputs.add(currentInput);
        }
        return Map.of("outputs", outputs);
    }

    private Map<String, Object> runSingleAgent(Map<String, Object> def, RunWorkflowRequest req) {
        // 单智能体模式：兼容历史定义（agentId/modelId）。
        String agentId = JsonMaps.getString(def, DEF_AGENT_ID, "");
        String modelId = JsonMaps.getString(def, DEF_MODEL_ID, "");
        if (agentId.isBlank() || modelId.isBlank()) {
            throw new ApiException("INVALID_WORKFLOW_DEF", "definition must contain either steps[] or agentId/modelId", HttpStatus.BAD_REQUEST);
        }
        RunAgentRequest agentReq = new RunAgentRequest();
        agentReq.setModelId(modelId);
        agentReq.setInput(req.getInput());
        RunAgentResponse agentResp = agentApplicationService.runAgent(agentId, agentReq);
        return Map.of("output", agentResp.getOutput());
    }

    private String runOneStep(Map<String, Object> step, String input) {
        String agentId = JsonMaps.getString(step, DEF_AGENT_ID, "");
        String modelId = JsonMaps.getString(step, DEF_MODEL_ID, "");
        RunAgentRequest agentReq = new RunAgentRequest();
        agentReq.setModelId(modelId);
        agentReq.setInput(input);
        return agentApplicationService.runAgent(agentId, agentReq).getOutput();
    }

    public WorkflowRunDto getRun(String runId) {
        WorkflowRunAggregate run = workflowRunRepository.findById(runId);
        if (run == null) {
            throw new ApiException("NOT_FOUND", "run not found", HttpStatus.NOT_FOUND);
        }
        WorkflowRunDto dto = new WorkflowRunDto();
        dto.setId(run.getId());
        dto.setWorkflowId(run.getWorkflowId());
        dto.setStatus(run.getStatus() == null ? null : run.getStatus().name());
        dto.setInput(run.getInput());
        dto.setOutput(run.getOutput());
        dto.setError(run.getError());
        dto.setStartedAt(run.getStartedAt());
        dto.setFinishedAt(run.getFinishedAt());
        dto.setCreatedAt(run.getCreatedAt());
        return dto;
    }
}

