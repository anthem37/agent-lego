package com.agentlego.backend.mcp.support;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

/**
 * 本机 MCP 调用 LOCAL 内置成功时，把 {@link io.agentscope.core.message.ToolResultBlock} 折叠为 MCP {@code CallToolResult} 文本。
 */
public final class McpPlatformResponses {

    private McpPlatformResponses() {
    }

    public static McpSchema.CallToolResult fromToolResult(ToolResultBlock block) {
        if (block == null) {
            return McpSchema.CallToolResult.builder().addTextContent("").build();
        }
        List<ContentBlock> out = block.getOutput();
        if (out == null || out.isEmpty()) {
            return McpSchema.CallToolResult.builder().addTextContent("").build();
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : out) {
            if (b instanceof TextBlock tb) {
                String t = tb.getText();
                if (t != null) {
                    sb.append(t);
                }
            } else {
                sb.append(String.valueOf(b));
            }
        }
        return McpSchema.CallToolResult.builder().addTextContent(sb.toString()).build();
    }
}
