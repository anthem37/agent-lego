package com.agentlego.backend.workflow.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工作流聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - definition 以 JSON object 形式承载工作流定义（steps/mode/单 agent 等）；
 * - 后续可演进为强类型的 DAG/状态机定义。
 */
@Data
public class WorkflowAggregate {
    /**
     * 工作流 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 工作流名称。
     */
    private String name;
    /**
     * 工作流定义（JSON object）。
     */
    private Map<String, Object> definition;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

