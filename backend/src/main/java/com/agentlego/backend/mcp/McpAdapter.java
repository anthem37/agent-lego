package com.agentlego.backend.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

/**
 * MCP 适配器（最小可用版本）。
 * <p>
 * 当前能力：
 * - 构建一个进程内 MCP Server（目前只暴露 echo/now 两个示例工具）
 * - 构建 MCP Client（用于连接外部 MCP Server）
 * <p>
 * 说明：
 * - WebMvc 的 RouterFunction 挂载（对外暴露 SSE endpoint）后续再补齐。
 */
@Service
public class McpAdapter {
    private static final String TOOL_ECHO = "echo";
    private static final String TOOL_NOW = "now";
    private static final String ARG_TEXT = "text";

    public McpSyncServer buildPlatformMcpServer(ObjectMapper objectMapper, String sseEndpointBase) {
        WebMvcSseServerTransportProvider transportProvider =
                new WebMvcSseServerTransportProvider(objectMapper, sseEndpointBase);

        // MCP 规范要求：在 addTool 之前必须显式声明 server capabilities。
        // 这里先仅开启 tools 能力；resources/prompts 等能力后续再逐步接入。
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();

        McpSyncServer server = McpServer.sync(transportProvider)
                .capabilities(capabilities)
                .build();

        server.addTool(
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name(TOOL_ECHO)
                                .description("Echo provided text. Input: {\"text\": string}")
                                .build())
                        .callHandler(this::handleEchoTool)
                        .build()
        );

        server.addTool(
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(McpSchema.Tool.builder()
                                .name(TOOL_NOW)
                                .description("Return server current time. No input.")
                                .build())
                        .callHandler(this::handleNowTool)
                        .build()
        );

        server.notifyToolsListChanged();
        return server;
    }

    public Mono<McpClientWrapper> buildMcpClient(String clientName, String sseEndpoint) {
        // 通过 AgentScope 的 MCP SDK 处理 JSON-RPC + SSE transport 细节。
        return McpClientBuilder.create(clientName)
                .sseTransport(sseEndpoint)
                .buildAsync();
    }

    private McpSchema.CallToolResult handleEchoTool(McpSyncServerExchange exchange, McpSchema.CallToolRequest req) {
        Map<String, Object> args = req.arguments();
        Object text = (args == null) ? null : args.get(ARG_TEXT);
        String out = Objects.toString(text, "");
        return McpSchema.CallToolResult.builder()
                .addTextContent(out)
                .build();
    }

    private McpSchema.CallToolResult handleNowTool(McpSyncServerExchange exchange, McpSchema.CallToolRequest req) {
        String out = java.time.Instant.now().toString();
        return McpSchema.CallToolResult.builder()
                .addTextContent(out)
                .build();
    }
}

