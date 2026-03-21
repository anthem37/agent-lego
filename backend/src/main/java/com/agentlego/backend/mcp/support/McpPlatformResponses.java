package com.agentlego.backend.mcp.support;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

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
