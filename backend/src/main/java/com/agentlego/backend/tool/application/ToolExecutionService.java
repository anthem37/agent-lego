package com.agentlego.backend.tool.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.local.LocalEchoTool;
import com.agentlego.backend.tool.local.LocalNowTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具执行服务。
 * <p>
 * 作用：
 * - 把平台侧的“工具定义/工具权限”映射到 AgentScope 的 Toolkit；
 * - 执行本地工具（LOCAL），并返回 AgentScope 标准的 ToolResultBlock。
 */
@Service
public class ToolExecutionService {
    private static final String TOOL_ECHO = "echo";
    private static final String TOOL_NOW = "now";

    /**
     * JSON 序列化器（ObjectMapper）。
     * <p>
     * 说明：ObjectMapper 在配置完成后可安全并发使用（thread-safe for read/write operations）。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 构建一个只包含指定工具集合的 Toolkit，供 AgentScope Agent 挂载使用。
     * <p>
     * 约束：
     * - 当前仅支持本地工具（LOCAL）。
     * - MCP 工具后续由 McpAdapter 接入（这里先返回明确的 NOT_IMPLEMENTED）。
     */
    public Toolkit buildToolkitForToolIds(List<ToolAggregate> tools) {
        Toolkit toolkit = new Toolkit();

        for (ToolAggregate t : tools) {
            if (t.getToolType() == ToolType.LOCAL) {
                toolkit.registerTool(resolveLocalTool(t.getName()));
            } else {
                // MCP 工具需要 McpAdapter 支持，这里先保留明确的占位报错，避免静默失败。
                throw new ApiException(
                        "UNSUPPORTED_TOOL_TYPE",
                        "MCP tool execution is not implemented yet: " + t.getName(),
                        HttpStatus.NOT_IMPLEMENTED
                );
            }
        }

        return toolkit;
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
                .timeout(Duration.ofSeconds(30));
    }

    /**
     * 构建 ToolUseBlock.content（JSON object string）。
     * <p>
     * 背景（AgentScope 约束）：
     * - 本地工具在做参数校验时，会把 ToolUseBlock.content 作为“主输入”；
     * - 因此这里必须是一个 JSON object（表示工具参数），否则会触发 JSON Schema 校验失败。
     * <p>
     * 示例：echo 工具：{"content":"hello"}
     */
    private String buildToolUseContentJson(Map<String, Object> input) {
        try {
            Map<String, Object> safeInput = (input == null) ? Map.of() : input;
            return OBJECT_MAPPER.writeValueAsString(safeInput);
        } catch (JsonProcessingException e) {
            // 兜底：保持为合法 JSON object，避免整个调用链因序列化异常中断。
            return "{}";
        }
    }

    private Object resolveLocalTool(String toolName) {
        if (TOOL_ECHO.equalsIgnoreCase(toolName)) {
            return new LocalEchoTool();
        }
        if (TOOL_NOW.equalsIgnoreCase(toolName)) {
            return new LocalNowTool();
        }
        throw new ApiException(
                "UNSUPPORTED_LOCAL_TOOL",
                "Unsupported local tool: " + toolName,
                HttpStatus.BAD_REQUEST
        );
    }
}

