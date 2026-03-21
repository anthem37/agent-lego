package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JacksonHolder;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.mcp.client.McpClientRegistry;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.http.HttpProxyAgentTool;
import com.agentlego.backend.tool.http.HttpToolRequestExecutor;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.mcp.McpProxyAgentTool;
import com.agentlego.backend.tool.workflow.WorkflowProxyAgentTool;
import com.agentlego.backend.workflow.application.service.WorkflowApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * 工具执行服务。
 * <p>
 * 作用：
 * - 把平台侧的“工具定义/工具权限”映射到运行时 Toolkit；
 * - 按类型执行工具并返回标准工具结果块。
 */
@Service
public class ToolExecutionService {
    private final WorkflowApplicationService workflowApplicationService;
    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;
    private final McpClientRegistry mcpClientRegistry;
    private final HttpToolRequestExecutor httpToolRequestExecutor;
    private final Duration httpCallTimeout;

    /**
     * @param workflowApplicationService 使用 {@link Lazy} 避免与 {@link com.agentlego.backend.agent.application.service.AgentApplicationService} 形成构造器循环依赖。
     */
    public ToolExecutionService(
            @Lazy WorkflowApplicationService workflowApplicationService,
            LocalBuiltinToolCatalog localBuiltinToolCatalog,
            McpClientRegistry mcpClientRegistry,
            HttpToolRequestExecutor httpToolRequestExecutor,
            @Value("${agentlego.tool.http-call-timeout-seconds:120}") int httpCallTimeoutSeconds
    ) {
        this.workflowApplicationService = workflowApplicationService;
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
        this.mcpClientRegistry = mcpClientRegistry;
        this.httpToolRequestExecutor = Objects.requireNonNull(httpToolRequestExecutor, "httpToolRequestExecutor");
        int sec = Math.max(5, Math.min(httpCallTimeoutSeconds, 600));
        this.httpCallTimeout = Duration.ofSeconds(sec);
    }

    /**
     * 构建一个只包含指定工具集合的 Toolkit，供智能体运行时挂载使用。
     */
    public Toolkit buildToolkitForToolIds(List<ToolAggregate> tools) {
        Set<String> seenNames = new HashSet<>();
        for (ToolAggregate t : tools) {
            String n = t.getName();
            if (n != null && !n.isBlank() && !seenNames.add(n)) {
                throw new ApiException(
                        "TOOLKIT_DUPLICATE_NAME",
                        "挂载的工具列表中存在重名「" + n.trim()
                                + "」，与运行时工具注册（以工具名为键）不兼容。请调整工具名称或智能体 toolIds。",
                        HttpStatus.CONFLICT
                );
            }
        }

        Toolkit toolkit = new Toolkit();

        for (ToolAggregate t : tools) {
            switch (t.getToolType()) {
                case LOCAL -> toolkit.registerTool(resolveLocalTool(t.getName()));
                case HTTP -> toolkit.registerAgentTool(
                        new HttpProxyAgentTool(t, JacksonHolder.INSTANCE, httpToolRequestExecutor)
                );
                case MCP -> toolkit.registerAgentTool(new McpProxyAgentTool(t, mcpClientRegistry));
                case WORKFLOW -> toolkit.registerAgentTool(
                        new WorkflowProxyAgentTool(t, workflowApplicationService, JacksonHolder.INSTANCE)
                );
            }
        }

        return toolkit;
    }

    /**
     * 根据已注册工具聚合根执行一次调用（用于 test-call 等）。
     */
    public Mono<ToolResultBlock> executeTool(ToolAggregate aggregate, Map<String, Object> input) {
        Objects.requireNonNull(aggregate, "aggregate");
        Map<String, Object> in = input == null ? Map.of() : input;
        return switch (aggregate.getToolType()) {
            case LOCAL -> executeLocalTool(aggregate.getName(), in);
            case HTTP -> executeHttpTool(aggregate, in);
            case MCP -> executeMcpTool(aggregate, in);
            case WORKFLOW -> executeWorkflowTool(aggregate, in);
        };
    }

    private Mono<ToolResultBlock> executeWorkflowTool(ToolAggregate aggregate, Map<String, Object> input) {
        WorkflowProxyAgentTool tool = new WorkflowProxyAgentTool(aggregate, workflowApplicationService, JacksonHolder.INSTANCE);
        String toolUseId = SnowflakeIdGenerator.nextId();
        String contentJson = buildToolUseContentJson(input);
        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                .id(toolUseId)
                .name(aggregate.getName())
                .content(contentJson)
                .input(input)
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(toolUseBlock)
                .input(toolUseBlock.getInput())
                .build();
        return tool.callAsync(param)
                .map(block -> block.withIdAndName(toolUseId, aggregate.getName()))
                .timeout(Duration.ofMinutes(3));
    }

    private Mono<ToolResultBlock> executeMcpTool(ToolAggregate aggregate, Map<String, Object> input) {
        McpProxyAgentTool tool = new McpProxyAgentTool(aggregate, mcpClientRegistry);
        String toolUseId = SnowflakeIdGenerator.nextId();
        String contentJson = buildToolUseContentJson(input);
        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                .id(toolUseId)
                .name(aggregate.getName())
                .content(contentJson)
                .input(input)
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(toolUseBlock)
                .input(toolUseBlock.getInput())
                .build();
        /*
         * McpContentConverter.convertCallToolResult 使用 ToolResultBlock.of(List)，不填 id/name。
         * id/name 语义与 ToolUseBlock 一致：关联本轮 tool 调用（联调 JSON 里应可见）。
         */
        return tool.callAsync(param)
                .map(block -> block.withIdAndName(toolUseId, aggregate.getName()))
                .timeout(Duration.ofMinutes(3));
    }

    private Mono<ToolResultBlock> executeHttpTool(ToolAggregate aggregate, Map<String, Object> input) {
        HttpProxyAgentTool tool = new HttpProxyAgentTool(aggregate, JacksonHolder.INSTANCE, httpToolRequestExecutor);
        String toolUseId = SnowflakeIdGenerator.nextId();
        String contentJson = buildToolUseContentJson(input);
        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                .id(toolUseId)
                .name(aggregate.getName())
                .content(contentJson)
                .input(input)
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(toolUseBlock)
                .input(toolUseBlock.getInput())
                .build();
        return tool.callAsync(param)
                .map(block -> block.withIdAndName(toolUseId, aggregate.getName()))
                .timeout(httpCallTimeout);
    }

    public Mono<ToolResultBlock> executeLocalTool(String toolName, Map<String, Object> input) {
        Objects.requireNonNull(toolName, "toolName");

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(resolveLocalTool(toolName));

        String toolUseId = SnowflakeIdGenerator.nextId();

        String contentJson = buildToolUseContentJson(input);

        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                .id(toolUseId)
                .name(toolName)
                .content(contentJson)
                .input(input == null ? Map.of() : input)
                .build();

        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(toolUseBlock)
                .input(toolUseBlock.getInput())
                .build();

        return toolkit.callTool(param)
                .map(block -> block.withIdAndName(toolUseId, toolName))
                .timeout(Duration.ofSeconds(30));
    }

    /**
     * 构建 ToolUseBlock.content（JSON object string）。
     * <p>
     * 背景（运行时约束）：
     * - 本地工具在做参数校验时，会把 ToolUseBlock.content 作为“主输入”；
     * - 因此这里必须是一个 JSON object（表示工具参数），否则会触发 JSON Schema 校验失败。
     * <p>
     * 示例：echo 工具：{"content":"hello"}
     */
    private String buildToolUseContentJson(Map<String, Object> input) {
        try {
            Map<String, Object> safeInput = (input == null) ? Map.of() : input;
            return JacksonHolder.INSTANCE.writeValueAsString(safeInput);
        } catch (JsonProcessingException e) {
            // 兜底：保持为合法 JSON object，避免整个调用链因序列化异常中断。
            return "{}";
        }
    }

    private Object resolveLocalTool(String toolName) {
        return localBuiltinToolCatalog.newInstance(toolName);
    }
}
