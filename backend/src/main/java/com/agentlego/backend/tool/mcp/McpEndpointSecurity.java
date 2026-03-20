package com.agentlego.backend.tool.mcp;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.http.SsrUrlGuard;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * MCP SSE endpoint URL 校验：可选严格 SSRF（与 {@link SsrUrlGuard} 一致）或宽松（允许回环/内网，便于本地联调）。
 */
public final class McpEndpointSecurity {

    private McpEndpointSecurity() {
    }

    /**
     * @param strictSsrf {@code true} 时等同 HTTP 工具 SSRF 策略
     */
    public static void validateEndpoint(String urlString, boolean strictSsrf) {
        if (strictSsrf) {
            SsrUrlGuard.validate(urlString);
            return;
        }
        relaxedValidate(urlString);
    }

    private static void relaxedValidate(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "MCP endpoint is blank", HttpStatus.BAD_REQUEST);
        }
        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", "invalid MCP endpoint url: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
            throw new ApiException("VALIDATION_ERROR", "MCP endpoint must be http or https", HttpStatus.BAD_REQUEST);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "MCP endpoint must include a host", HttpStatus.BAD_REQUEST);
        }
        String h = host.toLowerCase();
        if ("169.254.169.254".equals(h) || h.endsWith(".local") || h.contains("metadata.google.internal")) {
            throw new ApiException("VALIDATION_ERROR", "Target host is not allowed: " + host, HttpStatus.BAD_REQUEST);
        }
    }
}
