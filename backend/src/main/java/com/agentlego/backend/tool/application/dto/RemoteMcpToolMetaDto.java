package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 外部 MCP Server {@code tools/list} 中单条工具元数据（供前端展示与批量导入）。
 */
@Data
@Builder
public class RemoteMcpToolMetaDto {

    private String name;
    private String description;
    /**
     * 远端 JSON Schema（object），可能为 null。
     */
    private Map<String, Object> inputSchema;
}
