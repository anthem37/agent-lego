package com.agentlego.backend.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本服务作为 MCP Client 连接外部 Server 时的行为配置。
 */
@ConfigurationProperties(prefix = "agentlego.mcp.client")
public class McpClientProperties {

    /**
     * 为 {@code true} 时，发现/批量导入前对 {@code endpoint} 执行与 HTTP 工具相同的 SSRF 校验（禁止回环、内网解析等）。
     * 本地开发连 {@code 127.0.0.1} 时需保持 {@code false}（默认）。
     */
    private boolean strictSsrf = false;

    public boolean isStrictSsrf() {
        return strictSsrf;
    }

    public void setStrictSsrf(boolean strictSsrf) {
        this.strictSsrf = strictSsrf;
    }
}
