package com.agentlego.backend.mcp.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 作为 MCP Client 连外部 URL 时的策略；{@link #strictSsrf} 与 HTTP 工具 SSRF 一致（联调可关）。
 */
@Data
@ConfigurationProperties(prefix = "agentlego.mcp.client")
public class McpClientProperties {

    private boolean strictSsrf = false;
}
