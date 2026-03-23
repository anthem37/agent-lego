package com.agentlego.backend.mcp.adapter;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.mcp.support.McpJsonSchemas;
import com.agentlego.backend.mcp.support.McpPlatformResponses;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
            List<LocalBuiltinToolMetaDto> localToolsForMcp
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(sseEndpointBase, "sseEndpointBase");
        Objects.requireNonNull(toolExecutionService, "toolExecutionService");
        Objects.requireNonNull(localToolsForMcp, "localToolsForMcp");

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

        for (LocalBuiltinToolMetaDto meta : localToolsForMcp) {
            addLocalBuiltinTool(server, toolExecutionService, meta);
        }

        server.notifyToolsListChanged();
        return new PlatformMcpServerBundle(transport, server);
    }

    /**
     * 与 {@link #buildPlatformMcpServerBundle} 启动时注册的工具列表对齐：按当前应暴露的内置元数据增删 MCP tools，
     * 并通知已连接的客户端刷新 tools/list（例如用户变更「内置工具是否暴露到 MCP」后）。
     */
    public void syncLocalBuiltinTools(
            McpSyncServer server,
            ToolExecutionService toolExecutionService,
            List<LocalBuiltinToolMetaDto> exposedMetas
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(toolExecutionService, "toolExecutionService");
        Objects.requireNonNull(exposedMetas, "exposedMetas");

        Set<String> desired = exposedMetas.stream()
                .map(m -> m.getName().trim())
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> current = server.listTools().stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toCollection(HashSet::new));

        for (String name : current) {
            if (!desired.contains(name)) {
                server.removeTool(name);
            }
        }
        for (LocalBuiltinToolMetaDto meta : exposedMetas) {
            String toolName = meta.getName().trim();
            if (!current.contains(toolName)) {
                addLocalBuiltinTool(server, toolExecutionService, meta);
            }
        }
        server.notifyToolsListChanged();
    }

    private void addLocalBuiltinTool(
            McpSyncServer server,
            ToolExecutionService toolExecutionService,
            LocalBuiltinToolMetaDto meta
    ) {
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
