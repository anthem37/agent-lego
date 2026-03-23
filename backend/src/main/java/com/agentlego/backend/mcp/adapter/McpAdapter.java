package com.agentlego.backend.mcp.adapter;

import cn.hutool.core.util.StrUtil;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 双角色适配：
 * <ul>
 *   <li><b>本机 Server</b>：{@link #buildPlatformMcpServerBundle} 搭 SSE、注册 LOCAL 内置工具；{@link #syncLocalBuiltinTools} 在暴露策略变更时对齐 tools/list。</li>
 *   <li><b>外连 Client</b>：{@link #buildMcpClient} 供运行时按需创建（与 Registry 配合）。</li>
 * </ul>
 */
@Service
public class McpAdapter {

    private static final Duration LOCAL_TOOL_TIMEOUT = Duration.ofSeconds(30);

    private static String normalizeMcpBasePath(String path) {
        String p = StrUtil.trim(StrUtil.blankToDefault(path, null));
        if (StrUtil.isEmpty(p)) {
            throw new IllegalArgumentException("sseEndpointBase must not be blank");
        }
        if (!StrUtil.startWith(p, StrUtil.SLASH)) {
            p = StrUtil.SLASH + p;
        }
        while (StrUtil.length(p) > 1 && StrUtil.endWith(p, StrUtil.SLASH)) {
            p = StrUtil.removeSuffix(p, StrUtil.SLASH);
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
     * 与 {@link #buildPlatformMcpServerBundle} 启动态对齐：按 {@code exposedMetas} 做差集增删，并 {@code notifyToolsListChanged}。
     * <p>移除顺序任意；新增顺序与 {@code exposedMetas} 列表一致（避免 HashSet 无序）。
     */
    public void syncLocalBuiltinTools(
            McpSyncServer server,
            ToolExecutionService toolExecutionService,
            List<LocalBuiltinToolMetaDto> exposedMetas
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(toolExecutionService, "toolExecutionService");
        Objects.requireNonNull(exposedMetas, "exposedMetas");

        Set<String> desired = exposedMetas.stream().map(m -> m.getName().trim()).collect(Collectors.toSet());
        Set<String> current = server.listTools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());

        current.stream().filter(n -> !desired.contains(n)).forEach(server::removeTool);
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
                                .description(StrUtil.isNotBlank(meta.getDescription())
                                        ? meta.getDescription().trim()
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
