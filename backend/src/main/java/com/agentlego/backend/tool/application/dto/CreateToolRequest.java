package com.agentlego.backend.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
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
     * 工具名称（name），用于在 agent/toolkit 中引用。
     */
    @NotBlank
    private String name;

    /**
     * 工具定义（JSON object），由 toolType 决定其结构。
     */
    private Map<String, Object> definition;
}

