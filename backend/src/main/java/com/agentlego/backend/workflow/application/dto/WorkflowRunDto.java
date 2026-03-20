package com.agentlego.backend.workflow.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工作流运行 DTO。
 * <p>
 * 说明：用于查询运行状态与输出（input/output 为 JSON object）。
 */
@Data
public class WorkflowRunDto {
    /**
     * 运行 ID。
     */
    private String id;
    /**
     * 关联的工作流 ID。
     */
    private String workflowId;
    /**
     * 运行状态字符串。
     */
    private String status;
    /**
     * 运行输入（JSON object）。
     */
    private Map<String, Object> input;
    /**
     * 运行输出（JSON object）。
     */
    private Map<String, Object> output;
    /**
     * 失败原因（仅 FAILED 时有值）。
     */
    private String error;
    /**
     * 开始时间。
     */
    private Instant startedAt;
    /**
     * 结束时间。
     */
    private Instant finishedAt;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

