package com.agentlego.backend.mcp.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本机作为 MCP Server（SSE）暴露；{@link #ssePath} 为挂载根路径（如 {@code /mcp} → SSE 与 {@code /mcp/message}）。
 */
@Data
@ConfigurationProperties(prefix = "agentlego.mcp.server")
public class McpServerProperties {

    private boolean enabled = true;
    private String ssePath = "/mcp";
}
