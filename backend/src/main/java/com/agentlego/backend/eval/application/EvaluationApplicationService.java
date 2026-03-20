package com.agentlego.backend.eval.application;

import com.agentlego.backend.agent.application.AgentApplicationService;
import com.agentlego.backend.agent.application.AgentRunRequests;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.eval.application.assembler.EvaluationAssembler;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.eval.application.dto.CreateEvaluationRequest;
import com.agentlego.backend.eval.application.dto.RunEvaluationDto;
import com.agentlego.backend.eval.application.dto.RunEvaluationResponse;
import com.agentlego.backend.eval.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 评测应用服务（Application Service）。
 * <p>
 * 当前版本的指标说明：
 * - accuracy：使用 exact match（去除首尾空白后）作为通过条件；
 * - trace：以结构化 Map 保存每个 case 的输入/期望/实际/是否通过，便于后续扩展为 LLM-as-judge。
 * <p>
 * 注意：此处的“评测运行”采用异步执行，API 先返回 RUNNING，结束后写回 metrics/trace。
 */
@Service
public class EvaluationApplicationService {
    private static final String CONFIG_MODEL_ID = "modelId";
    private static final String CONFIG_CASES = "cases";
    private static final String CASE_INPUT = "input";
    private static final String CASE_EXPECTED_OUTPUT = "expectedOutput";

    /**
     * 评测定义仓库（读写 platform_evaluations）。
     */
    private final EvaluationRepository evaluationRepository;
    /**
     * 评测运行仓库（读写 platform_evaluation_runs）。
     */
    private final EvaluationRunRepository evaluationRunRepository;
    /**
     * 智能体执行入口：评测时复用 agent 的执行链路。
     */
    private final AgentApplicationService agentApplicationService;
    /**
     * 异步执行器：生产默认 ForkJoinPool；测试可注入同步 executor。
     */
    private final Executor executor;

    @Autowired
    public EvaluationApplicationService(
            EvaluationRepository evaluationRepository,
            EvaluationRunRepository evaluationRunRepository,
            AgentApplicationService agentApplicationService
    ) {
        this.evaluationRepository = evaluationRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.executor = ForkJoinPool.commonPool();
    }

    /**
     * 测试构造器：允许注入 Executor，使异步逻辑可控/可重复（deterministic）。
     */
    EvaluationApplicationService(
            EvaluationRepository evaluationRepository,
            EvaluationRunRepository evaluationRunRepository,
            AgentApplicationService agentApplicationService,
            Executor executor
    ) {
        this.evaluationRepository = evaluationRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.executor = executor;
    }

    public String createEvaluation(CreateEvaluationRequest req) {
        Map<String, Object> config = new HashMap<>();
        config.put("modelId", req.getModelId());
        config.put("cases", req.getCases());

        EvaluationAggregate agg = new EvaluationAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setAgentId(req.getAgentId());
        agg.setName(req.getName());
        agg.setConfig(config);
        agg.setCreatedAt(Instant.now());

        return evaluationRepository.save(agg);
    }

    public RunEvaluationResponse runEvaluation(String evaluationId) {
        String runId = evaluationRunRepository.createRun(evaluationId, Map.of("trigger", "manual"));
        evaluationRunRepository.markRunning(runId);

        CompletableFuture.runAsync(() -> executeEvaluationRun(runId, evaluationId), executor);

        RunEvaluationResponse resp = new RunEvaluationResponse();
        resp.setRunId(runId);
        resp.setStatus(EvaluationRunStatus.RUNNING.name());
        return resp;
    }

    /**
     * 执行一次评测运行，并将 metrics/trace 写回仓库。
     * <p>
     * 说明：
     * - 该方法内部捕获所有异常，保证最终会落库为 FAILED（避免“永远 RUNNING”）。
     * - 当前指标为 exact match（忽略首尾空白），后续可扩展为 LLM-as-judge。
     */
    private void executeEvaluationRun(String runId, String evaluationId) {
        try {
            EvaluationAggregate eval = evaluationRepository.findById(evaluationId)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "评测未找到", HttpStatus.NOT_FOUND));

            String agentId = eval.getAgentId();
            Map<String, Object> config = eval.getConfig() == null ? Map.of() : eval.getConfig();
            String modelId = JsonMaps.getString(config, CONFIG_MODEL_ID, "");

            List<Map<String, Object>> casesRaw = JsonMaps.getListOfMaps(config, CONFIG_CASES);

            EvaluationRunResult result = runCases(agentId, modelId, casesRaw);
            evaluationRunRepository.markSucceeded(runId, result.metrics(), result.trace());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            evaluationRunRepository.markFailed(runId, msg);
        }
    }

    private EvaluationRunResult runCases(String agentId, String modelId, List<Map<String, Object>> casesRaw) {
        int total = casesRaw.size();
        int passed = 0;
        java.util.ArrayList<Map<String, Object>> traceCases = new java.util.ArrayList<>();

        for (Map<String, Object> c : casesRaw) {
            String input = JsonMaps.getString(c, CASE_INPUT, "");
            String expected = JsonMaps.getString(c, CASE_EXPECTED_OUTPUT, null);

            String actual = callAgent(agentId, modelId, input);
            boolean ok = expected != null && actual != null && actual.trim().equals(expected.trim());
            if (ok) {
                passed++;
            }
            traceCases.add(buildCaseTrace(input, expected, actual, ok));
        }

        double accuracy = total == 0 ? 0.0 : (double) passed / (double) total;
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("accuracy", accuracy);
        metrics.put("passed", passed);
        metrics.put("total", total);

        Map<String, Object> trace = new HashMap<>();
        trace.put("cases", traceCases);
        return new EvaluationRunResult(metrics, trace);
    }

    private String callAgent(String agentId, String modelId, String input) {
        RunAgentResponse agentResp = agentApplicationService.runAgent(agentId, AgentRunRequests.of(modelId, input));
        return agentResp == null ? null : agentResp.getOutput();
    }

    private Map<String, Object> buildCaseTrace(String input, String expected, String actual, boolean passed) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("input", input);
        trace.put("expectedOutput", expected);
        trace.put("actualOutput", actual);
        trace.put("passed", passed);
        return trace;
    }

    public RunEvaluationDto getRun(String runId) {
        EvaluationRunAggregate run = evaluationRunRepository.findById(runId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "运行记录未找到", HttpStatus.NOT_FOUND));
        return EvaluationAssembler.toRunDto(run);
    }
}

