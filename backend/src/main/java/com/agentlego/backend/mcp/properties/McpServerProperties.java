package com.agentlego.backend.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentlego.mcp.server")
public class McpServerProperties {

    private boolean enabled = true;
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
