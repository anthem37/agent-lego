package com.agentlego.backend.eval.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.eval.application.dto.CreateEvaluationRequest;
import com.agentlego.backend.eval.application.dto.RunEvaluationDto;
import com.agentlego.backend.eval.application.dto.RunEvaluationResponse;
import com.agentlego.backend.eval.application.service.EvaluationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP：评测定义、触发运行、查询 run。
 */
@RestController
@RequestMapping("/evaluations")
public class EvaluationController {

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
