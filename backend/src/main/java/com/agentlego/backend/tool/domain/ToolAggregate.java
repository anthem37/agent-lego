package com.agentlego.backend.tool.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - toolType：LOCAL / MCP / HTTP / WORKFLOW 等；
 * - definition 为工具 schema/定义（JSON object），由不同类型工具决定其结构。
 */
@Data
public class ToolAggregate {
    /**
     * 工具 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 工具类型。
     */
    private ToolType toolType;
    /**
     * 语义分类：查询 / 操作等（与 toolType 正交）。
     */
    private ToolCategory toolCategory = ToolCategory.ACTION;
    /**
     * 工具名称（name），用于在 agent/toolkit 中引用。
     */
    private String name;
    /**
     * 展示名/中文名（可选），用于管理端与知识库展示。
     */
    private String displayLabel;
    /**
     * 平台侧工具说明（可选），给人阅读；与 {@code definition} 内模型描述可并存。
     */
    private String description;
    /**
     * 工具定义（JSON object）。
     */
    private Map<String, Object> definition;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

