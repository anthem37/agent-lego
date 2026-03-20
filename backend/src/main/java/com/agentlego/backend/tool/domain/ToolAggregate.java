package com.agentlego.backend.tool.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - toolType 区分 LOCAL 与 MCP；
 * - definition 为工具 schema/定义（JSON object），由不同类型工具决定其结构。
 */
@Data
public class ToolAggregate {
    /**
     * 工具 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 工具类型（LOCAL/MCP）。
     */
    private ToolType toolType;
    /**
     * 工具名称（name），用于在 agent/toolkit 中引用。
     */
    private String name;
    /**
     * 工具定义（JSON object）。
     */
    private Map<String, Object> definition;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

