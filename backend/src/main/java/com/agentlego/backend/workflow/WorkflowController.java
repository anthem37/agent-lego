package com.agentlego.backend.workflow;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.workflow.application.WorkflowApplicationService;
import com.agentlego.backend.workflow.application.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流管理 API（Controller）。
 * <p>
 * 说明：
 * - /workflows：工作流定义管理
 * - /workflows/{id}/runs：触发一次运行（异步执行，先返回 RUNNING）
 * - /runs/{runId}：查询运行状态与结果
 */
@RestController
@RequestMapping
public class WorkflowController {

    /**
     * 工作流应用服务（Application Service）。
     */
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

