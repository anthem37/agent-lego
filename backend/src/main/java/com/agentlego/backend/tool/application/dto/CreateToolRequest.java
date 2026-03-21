package com.agentlego.backend.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 创建工具请求 DTO。
 * <p>
 * - toolType：工具类型（LOCAL | MCP | HTTP | WORKFLOW）
 * - definition：工具定义（通常是 JSON schema / MCP tool schema 的子集）
 */
@Data
public class CreateToolRequest {

    /**
     * 工具类型：LOCAL | MCP | HTTP | WORKFLOW。
     */
    @NotBlank
    private String toolType;

    /**
     * 可选；语义分类：QUERY（查询）| ACTION（操作，默认）。
     */
    private String toolCategory;

    /**
     * 工具名称（name）：运行时注册键、模型侧工具调用标识；须全平台唯一（大小写不敏感），与表
     * {@code lego_tools} 上索引 {@code ux_lego_tools_name_lower} 及创建/更新服务校验一致。
     */
    @NotBlank
    private String name;

    /**
     * 可选；展示名/中文名（管理端、知识库引用展示）。
     */
    @Size(max = 256)
    private String displayLabel;

    /**
     * 可选；平台侧工具说明（给人读）。
     */
    @Size(max = 4000)
    private String description;

    /**
     * 工具定义（JSON object），由 toolType 决定其结构。
     */
    private Map<String, Object> definition;
}

