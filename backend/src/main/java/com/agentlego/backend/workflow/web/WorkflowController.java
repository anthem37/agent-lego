package com.agentlego.backend.workflow.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.workflow.application.dto.*;
import com.agentlego.backend.workflow.application.service.WorkflowApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP：工作流定义、触发运行、查询 run。
 */
@RestController
@RequestMapping
public class WorkflowController {

    private final WorkflowApplicationService service;

    public WorkflowController(WorkflowApplicationService service) {
        this.service = service;
    }

    @PostMapping("/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateWorkflowRequest req) {
        return ApiResponse.created(service.createWorkflow(req));
    }

    @GetMapping("/workflows/{id}")
    public ApiResponse<WorkflowDto> get(@PathVariable("id") String id) {
        return ApiResponse.ok(service.getWorkflow(id));
    }

    @PostMapping("/workflows/{id}/runs")
    public ApiResponse<RunWorkflowResponse> run(
            @PathVariable("id") String workflowId,
            @Valid @RequestBody RunWorkflowRequest req
    ) {
        return ApiResponse.ok(service.runWorkflow(workflowId, req));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<WorkflowRunDto> getRun(@PathVariable("runId") String runId) {
        return ApiResponse.ok(service.getRun(runId));
    }
}
