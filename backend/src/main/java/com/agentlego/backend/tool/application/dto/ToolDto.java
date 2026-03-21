package com.agentlego.backend.tool.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 工具 DTO。
 * <p>
 * 说明：用于 API 输出（不包含 definitionJson 等持久化细节）。
 */
@Data
public class ToolDto {
    /**
     * 工具 ID。
     */
    private String id;
    /**
     * 工具类型字符串。
     */
    private String toolType;
    /**
     * 语义分类：QUERY | ACTION。
     */
    private String toolCategory;
    /**
     * 工具名称。
     */
    private String name;
    /**
     * 展示名/中文名（可选）。
     */
    private String displayLabel;
    /**
     * 平台侧工具说明（可选）。
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

