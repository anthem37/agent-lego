package com.agentlego.backend.mcp.adapter;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * 一次构建产物：SSE transport + {@link McpSyncServer}，供 Web 路由注册与运行时 tools 同步共用。
 */
public record PlatformMcpServerBundle(
        WebMvcSseServerTransportProvider transport,
        McpSyncServer server
) {
    public RouterFunction<ServerResponse> routerFunction() {
        return transport.getRouterFunction();
    }
}
