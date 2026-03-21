package com.agentlego.backend.tool.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 工具数据对象（Data Object, DO）。
 * <p>
 * 说明：
 * - toolType 在数据库侧以字符串保存（如 LOCAL/MCP/HTTP/WORKFLOW）；
 * - definitionJson 为工具定义 JSON（序列化后的字符串）。
 */
@Data
public class ToolDO {
    /**
     * 主键 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 工具类型字符串。
     */
    private String toolType;
    /**
     * 语义分类：QUERY / ACTION。
     */
    private String toolCategory;
    /**
     * 工具名称（name）。
     */
    private String name;
    /**
     * 展示名/中文名（可选）。
     */
    private String displayLabel;
    /**
     * 平台侧说明（可选）。
     */
    private String description;
    /**
     * 工具定义 JSON（序列化后的字符串）。
     */
    private String definitionJson;
    /**
     * 创建时间（通常由 DB 默认值生成）。
     */
    private Instant createdAt;
}

