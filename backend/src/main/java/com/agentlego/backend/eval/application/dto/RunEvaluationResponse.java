package com.agentlego.backend.eval.application.dto;

import lombok.Data;

/**
 * 运行评测响应 DTO。
 * <p>
 * 说明：触发运行后先返回 RUNNING，最终 metrics/trace 通过 runId 查询。
 */
@Data
public class RunEvaluationResponse {
    /**
     * 评测运行 ID。
     */
    private String runId;
    /**
     * 运行状态（PENDING/RUNNING/SUCCEEDED/FAILED）。
     */
    private String status;
}

