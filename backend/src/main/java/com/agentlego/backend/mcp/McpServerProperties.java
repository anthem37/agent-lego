package com.agentlego.backend.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本服务作为 MCP Server 对外暴露时的配置。
 */
@ConfigurationProperties(prefix = "agentlego.mcp.server")
public class McpServerProperties {

    /**
     * 是否注册 SSE 路由并启动 MCP Server。
     */
    private boolean enabled = true;

    /**
     * MCP WebMvc 传输的基础路径：{@code GET {ssePath}} 为 SSE；
     * {@code POST {ssePath}/message?sessionId=...} 为 JSON-RPC 消息（与 {@code WebMvcSseServerTransportProvider} 四参注册方式一致）。
     * 例如 {@code /mcp}。
     */
    private String ssePath = "/mcp";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSsePath() {
        return ssePath;
    }

    public void setSsePath(String ssePath) {
        this.ssePath = ssePath;
    }
}
