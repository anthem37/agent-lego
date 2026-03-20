package com.agentlego.backend.eval.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 评测聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - agentId 指向被评测的智能体；
 * - config 以 JSON object 承载评测配置（例如 modelId、cases 等），便于后续扩展指标与评测方式。
 */
@Data
public class EvaluationAggregate {
    /**
     * 评测 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 被评测的智能体 ID。
     */
    private String agentId;
    /**
     * 评测名称。
     */
    private String name;
    /**
     * 评测配置（JSON object），例如 modelId、cases。
     */
    private Map<String, Object> config;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

