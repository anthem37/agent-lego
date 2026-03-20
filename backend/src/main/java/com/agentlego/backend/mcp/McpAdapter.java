package com.agentlego.backend.mcp;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.application.ToolExecutionService;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 适配：对外暴露本机 MCP Server（SSE），以及构建连接外部 MCP 的 Client。
 */
@Service
public class McpAdapter {

    private static final Duration LOCAL_TOOL_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 构建本进程 MCP Server：工具列表与 {@link LocalBuiltinToolCatalog} 中的 LOCAL 内置一致，执行走 {@link ToolExecutionService#executeLocalTool}。
     */
    public PlatformMcpServerBundle buildPlatformMcpServerBundle(
            ObjectMapper objectMapper,
            String sseEndpointBase,
            ToolExecutionService toolExecutionService,
            LocalBuiltinToolCatalog catalog
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(sseEndpointBase, "sseEndpointBase");
        Objects.requireNonNull(toolExecutionService, "toolExecutionService");
        Objects.requireNonNull(catalog, "catalog");

        WebMvcSseServerTransportProvider transport =
                new WebMvcSseServerTransportProvider(objectMapper, sseEndpointBase);

        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();

        McpSyncServer server = McpServer.sync(transport)
                .capabilities(capabilities)
                .build();

        for (LocalBuiltinToolMetaDto meta : catalog.listMeta()) {
            String toolName = meta.getName();
            McpSchema.JsonSchema inputSchema = McpJsonSchemas.fromBuiltinParams(meta.getInputParameters());
            server.addTool(
                    McpServerFeatures.SyncToolSpecification.builder()
                            .tool(McpSchema.Tool.builder()
                                    .name(toolName)
                                    .description(meta.getDescription() != null && !meta.getDescription().isBlank()
                                            ? meta.getDescription()
                                            : toolName)
                                    .inputSchema(inputSchema)
                                    .build())
                            .callHandler((McpSyncServerExchange exchange, McpSchema.CallToolRequest req) ->
                                    handleLocalBuiltinTool(toolExecutionService, toolName, exchange, req))
                            .build()
            );
        }

        server.notifyToolsListChanged();
        return new PlatformMcpServerBundle(transport, server);
    }

    private McpSchema.CallToolResult handleLocalBuiltinTool(
            ToolExecutionService toolExecutionService,
            String toolName,
            McpSyncServerExchange exchange,
            McpSchema.CallToolRequest req
    ) {
        Map<String, Object> args = req.arguments() == null ? new HashMap<>() : new HashMap<>(req.arguments());
        try {
            var block = toolExecutionService.executeLocalTool(toolName, args).block(LOCAL_TOOL_TIMEOUT);
            return McpPlatformResponses.fromToolResult(block);
        } catch (ApiException e) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(Objects.toString(e.getMessage(), e.getCode()))
                    .isError(true)
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(e.toString())
                    .isError(true)
                    .build();
        }
    }

    public Mono<McpClientWrapper> buildMcpClient(String clientName, String sseEndpoint) {
        return McpClientBuilder.create(clientName)
                .sseTransport(sseEndpoint)
                .buildAsync();
    }
}
