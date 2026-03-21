package com.agentlego.backend.mcp.adapter;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.mcp.support.McpJsonSchemas;
import com.agentlego.backend.mcp.support.McpPlatformResponses;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class McpAdapter {

    private static final Duration LOCAL_TOOL_TIMEOUT = Duration.ofSeconds(30);

    private static String normalizeMcpBasePath(String path) {
        String p = Objects.requireNonNull(path, "sseEndpointBase").trim();
        if (p.isEmpty()) {
            throw new IllegalArgumentException("sseEndpointBase must not be blank");
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

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

        String base = normalizeMcpBasePath(sseEndpointBase);
        WebMvcSseServerTransportProvider transport = WebMvcSseServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .baseUrl("")
                .messageEndpoint(base + "/message")
                .sseEndpoint(base)
                .build();

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
