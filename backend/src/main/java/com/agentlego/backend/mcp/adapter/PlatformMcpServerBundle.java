package com.agentlego.backend.mcp.adapter;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

public record PlatformMcpServerBundle(
        WebMvcSseServerTransportProvider transport,
        McpSyncServer server
) {
    public RouterFunction<ServerResponse> routerFunction() {
        return transport.getRouterFunction();
    }
}
