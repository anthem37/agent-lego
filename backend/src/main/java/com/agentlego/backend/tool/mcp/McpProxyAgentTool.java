package com.agentlego.backend.tool.mcp;

import com.agentlego.backend.mcp.McpClientRegistry;
import com.agentlego.backend.tool.domain.ToolAggregate;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.mcp.McpContentConverter;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

/**
 * 将平台 MCP 工具（definition.endpoint + 可选 mcpToolName）桥接为 AgentScope {@link AgentTool}。
 */
public final class McpProxyAgentTool implements AgentTool {

    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(3);
    private static final Map<String, Object> LOOSE_PARAMETERS = Map.of(
            "type", "object",
            "additionalProperties", true,
            "description", "Arguments passed to the remote MCP tools/call (JSON object)."
    );

    private final String platformName;
    private final String remoteToolName;
    private final String sseEndpoint;
    private final String description;
    private final Map<String, Object> definition;
    private final McpClientRegistry registry;

    private volatile Map<String, Object> parametersResolved;

    public McpProxyAgentTool(
            ToolAggregate aggregate,
            McpClientRegistry registry
    ) {
        Objects.requireNonNull(aggregate, "aggregate");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.platformName = aggregate.getName();
        this.definition = aggregate.getDefinition() == null ? Map.of() : aggregate.getDefinition();
        this.sseEndpoint = Objects.requireNonNull(McpToolSpec.readEndpoint(definition), "endpoint");
        this.remoteToolName = McpToolSpec.readRemoteToolName(definition, aggregate.getName());
        this.description = resolveDescription(definition, sseEndpoint, remoteToolName);
    }

    private static String resolveDescription(Map<String, Object> def, String endpoint, String remoteName) {
        Object d = def.get("description");
        if (d != null && !String.valueOf(d).isBlank()) {
            return String.valueOf(d).trim();
        }
        return "MCP tool → " + remoteName + " @ " + endpoint;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getParameters() {
        if (parametersResolved != null) {
            return parametersResolved;
        }
        synchronized (this) {
            if (parametersResolved != null) {
                return parametersResolved;
            }
            Object p = definition.get("parameters");
            if (p instanceof Map<?, ?> m) {
                parametersResolved = (Map<String, Object>) m;
                return parametersResolved;
            }
            Object schema = definition.get("inputSchema");
            if (schema instanceof Map<?, ?> m) {
                parametersResolved = (Map<String, Object>) m;
                return parametersResolved;
            }
            parametersResolved = loadParametersFromRemote();
            return parametersResolved;
        }
    }

    private Map<String, Object> loadParametersFromRemote() {
        try {
            McpSchema.Tool tool = findRemoteTool(remoteToolName);
            if (tool == null || tool.inputSchema() == null) {
                return LOOSE_PARAMETERS;
            }
            List<String> req = tool.inputSchema().required() == null ? List.of() : tool.inputSchema().required();
            Set<String> required = new HashSet<>(req);
            return McpTool.convertMcpSchemaToParameters(tool.inputSchema(), required);
        } catch (Exception e) {
            return LOOSE_PARAMETERS;
        }
    }

    private McpSchema.Tool findRemoteTool(String name) {
        List<McpSchema.Tool> tools = registry.listRemoteTools(sseEndpoint);
        return tools.stream().filter(t -> name.equals(t.name())).findFirst().orElse(null);
    }

    @Override
    public String getName() {
        return platformName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam toolCallParam) {
        Map<String, Object> input = toolCallParam.getInput() == null ? Map.of() : toolCallParam.getInput();
        return Mono.fromCallable(() -> registry.getOrCreate(sseEndpoint))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(client -> client.callTool(remoteToolName, input))
                .map(McpContentConverter::convertCallToolResult)
                .timeout(CALL_TIMEOUT);
    }
}
