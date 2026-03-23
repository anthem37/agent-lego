package com.agentlego.backend.eval.application.service;

import com.agentlego.backend.agent.application.AgentRunRequests;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.common.Throwables;
import com.agentlego.backend.eval.application.dto.CreateEvaluationRequest;
import com.agentlego.backend.eval.application.dto.RunEvaluationDto;
import com.agentlego.backend.eval.application.dto.RunEvaluationResponse;
import com.agentlego.backend.eval.application.mapper.EvaluationRunDtoMapper;
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
 * 评测用例编排：异步运行、accuracy 为 exact match；trace 为结构化 Map，可扩展 LLM-as-judge。
 */
@Service
public class EvaluationApplicationService {
    private static final String CONFIG_MODEL_ID = "modelId";
    private static final String CONFIG_MEMORY_NAMESPACE = "memoryNamespace";
    private static final String CONFIG_CASES = "cases";
    private static final String CASE_INPUT = "input";
    private static final String CASE_EXPECTED_OUTPUT = "expectedOutput";

    private final EvaluationRepository evaluationRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final AgentApplicationService agentApplicationService;
    private final Executor executor;
    private final EvaluationRunDtoMapper evaluationRunDtoMapper;

    @Autowired
    public EvaluationApplicationService(
            EvaluationRepository evaluationRepository,
            EvaluationRunRepository evaluationRunRepository,
            AgentApplicationService agentApplicationService,
            EvaluationRunDtoMapper evaluationRunDtoMapper
    ) {
        this.evaluationRepository = evaluationRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.evaluationRunDtoMapper = evaluationRunDtoMapper;
        this.executor = ForkJoinPool.commonPool();
    }

    /**
     * 测试用：可注入同步 Executor。
     */
    EvaluationApplicationService(
            EvaluationRepository evaluationRepository,
            EvaluationRunRepository evaluationRunRepository,
            AgentApplicationService agentApplicationService,
            Executor executor,
            EvaluationRunDtoMapper evaluationRunDtoMapper
    ) {
        this.evaluationRepository = evaluationRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.agentApplicationService = agentApplicationService;
        this.executor = executor;
        this.evaluationRunDtoMapper = evaluationRunDtoMapper;
    }

    public String createEvaluation(CreateEvaluationRequest req) {
        Map<String, Object> config = new HashMap<>();
        config.put("modelId", req.getModelId());
        config.put("cases", req.getCases());
        if (req.getMemoryNamespace() != null && !req.getMemoryNamespace().isBlank()) {
            config.put(CONFIG_MEMORY_NAMESPACE, req.getMemoryNamespace().trim());
        }

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
     * 捕获异常并 markFailed，避免异步线程失败后状态卡在 RUNNING。
     */
    private void executeEvaluationRun(String runId, String evaluationId) {
        try {
            EvaluationAggregate eval = evaluationRepository.findById(evaluationId)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "评测未找到", HttpStatus.NOT_FOUND));

            String agentId = eval.getAgentId();
            Map<String, Object> config = eval.getConfig() == null ? Map.of() : eval.getConfig();
            String modelId = JsonMaps.getString(config, CONFIG_MODEL_ID, "");
            String memoryNs = JsonMaps.getString(config, CONFIG_MEMORY_NAMESPACE, null);

            List<Map<String, Object>> casesRaw = JsonMaps.getListOfMaps(config, CONFIG_CASES);

            EvaluationRunResult result = runCases(agentId, modelId, casesRaw, memoryNs);
            evaluationRunRepository.markSucceeded(runId, result.metrics(), result.trace());
        } catch (Exception e) {
            evaluationRunRepository.markFailed(runId, Throwables.messageOrSimpleName(e));
        }
    }

    private EvaluationRunResult runCases(
            String agentId,
            String modelId,
            List<Map<String, Object>> casesRaw,
            String memoryNamespace
    ) {
        int total = casesRaw.size();
        int passed = 0;
        java.util.ArrayList<Map<String, Object>> traceCases = new java.util.ArrayList<>();

        for (Map<String, Object> c : casesRaw) {
            String input = JsonMaps.getString(c, CASE_INPUT, "");
            String expected = JsonMaps.getString(c, CASE_EXPECTED_OUTPUT, null);

            String actual = callAgent(agentId, modelId, input, memoryNamespace);
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

    private String callAgent(String agentId, String modelId, String input, String memoryNamespace) {
        RunAgentResponse agentResp = agentApplicationService.runAgent(
                agentId,
                AgentRunRequests.of(modelId, input, memoryNamespace)
        );
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
        return evaluationRunDtoMapper.toRunDto(run);
    }
}

