package com.agentlego.backend.workflow.application.dto;

import lombok.Data;

/**
 * 运行工作流响应 DTO。
 * <p>
 * 说明：触发运行后通常先返回 RUNNING，后续通过 runId 查询最终结果。
 */
@Data
public class RunWorkflowResponse {
    /**
     * 运行 ID。
     */
    private String runId;
    /**
     * 运行状态（PENDING/RUNNING/SUCCEEDED/FAILED）。
     */
    private String status;
}

