package com.agentlego.backend.eval.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 评测运行聚合根（Aggregate Root）。
 * <p>
 * 字段说明：
 * - input：触发运行时的输入信息（例如 trigger=manual）
 * - metrics：指标（例如 accuracy/passed/total）
 * - trace：过程追踪（每个 case 的 input/expected/actual/passed）
 */
@Data
public class EvaluationRunAggregate {
    /**
     * 评测运行 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 关联的评测 ID。
     */
    private String evaluationId;
    /**
     * 运行状态。
     */
    private EvaluationRunStatus status;
    /**
     * 失败原因（仅 FAILED 时有值）。
     */
    private String error;
    /**
     * 运行输入（JSON object）。
     */
    private Map<String, Object> input;
    /**
     * 运行指标（JSON object）。
     */
    private Map<String, Object> metrics;
    /**
     * 运行 trace（JSON object）。
     */
    private Map<String, Object> trace;
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

