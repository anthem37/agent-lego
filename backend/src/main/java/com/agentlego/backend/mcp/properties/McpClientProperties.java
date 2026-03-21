package com.agentlego.backend.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentlego.mcp.client")
public class McpClientProperties {

    private boolean strictSsrf = false;

    public boolean isStrictSsrf() {
        return strictSsrf;
    }

    public void setStrictSsrf(boolean strictSsrf) {
        this.strictSsrf = strictSsrf;
    }
}
