package com.agentlego.backend.eval;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.eval.application.EvaluationApplicationService;
import com.agentlego.backend.eval.application.dto.CreateEvaluationRequest;
import com.agentlego.backend.eval.application.dto.RunEvaluationDto;
import com.agentlego.backend.eval.application.dto.RunEvaluationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 评测管理 API（Controller）。
 * <p>
 * 提供能力：
 * - 创建评测（cases + agentId + modelId）
 * - 触发评测运行（异步执行，先返回 RUNNING）
 * - 查询评测运行状态与结果（metrics/trace）
 */
@RestController
@RequestMapping("/evaluations")
public class EvaluationController {

    /**
     * 评测应用服务（Application Service）。
     */
    private final EvaluationApplicationService service;

    public EvaluationController(EvaluationApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateEvaluationRequest req) {
        return ApiResponse.created(service.createEvaluation(req));
    }

    @PostMapping("/{id}/runs")
    public ApiResponse<RunEvaluationResponse> run(@PathVariable("id") String evaluationId) {
        return ApiResponse.ok(service.runEvaluation(evaluationId));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<RunEvaluationDto> getRun(@PathVariable("runId") String runId) {
        return ApiResponse.ok(service.getRun(runId));
    }
}

