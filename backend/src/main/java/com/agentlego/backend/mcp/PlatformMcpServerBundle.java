package com.agentlego.backend.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * 本进程对外暴露的 MCP Server：SSE 传输 + 同步 Server 实例。
 */
public record PlatformMcpServerBundle(
        WebMvcSseServerTransportProvider transport,
        McpSyncServer server
) {
    public RouterFunction<ServerResponse> routerFunction() {
        return transport.getRouterFunction();
    }
}
