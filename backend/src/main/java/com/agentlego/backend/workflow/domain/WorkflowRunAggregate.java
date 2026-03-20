package com.agentlego.backend.workflow.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工作流运行聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - input/output 以 JSON object 保存运行入参/产出，便于多 agent 输出结构扩展；
 * - status 用于表示运行状态（PENDING/RUNNING/SUCCEEDED/FAILED）。
 */
@Data
public class WorkflowRunAggregate {
    /**
     * 运行 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 关联的工作流 ID。
     */
    private String workflowId;
    /**
     * 运行状态。
     */
    private WorkflowRunStatus status;
    /**
     * 失败原因（仅 FAILED 时有值）。
     */
    private String error;
    /**
     * 运行输入（JSON object）。
     */
    private Map<String, Object> input;
    /**
     * 运行输出（JSON object）。
     */
    private Map<String, Object> output;
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

