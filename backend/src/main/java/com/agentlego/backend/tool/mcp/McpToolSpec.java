package com.agentlego.backend.tool.mcp;

import com.agentlego.backend.api.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 平台 {@code ToolType.MCP} 的 definition 约定：连接外部 MCP Server（SSE）。
 */
public final class McpToolSpec {

    public static final String KEY_ENDPOINT = "endpoint";
    /**
     * 远端 MCP 上的工具名；省略时默认与平台工具 {@code name} 一致。
     */
    public static final String KEY_MCP_TOOL_NAME = "mcpToolName";

    private McpToolSpec() {
    }

    public static void validateDefinition(Map<String, Object> definition) {
        String ep = readEndpoint(definition);
        if (ep == null || ep.isBlank()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "MCP 工具需要 definition.endpoint（外部 MCP Server 的 SSE URL）",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public static String readEndpoint(Map<String, Object> definition) {
        if (definition == null) {
            return null;
        }
        Object v = definition.get(KEY_ENDPOINT);
        return v == null ? null : String.valueOf(v).trim();
    }

    public static String readRemoteToolName(Map<String, Object> definition, String aggregateToolName) {
        if (definition == null) {
            return aggregateToolName;
        }
        Object v = definition.get(KEY_MCP_TOOL_NAME);
        if (v == null || String.valueOf(v).isBlank()) {
            return aggregateToolName;
        }
        return String.valueOf(v).trim();
    }
}
