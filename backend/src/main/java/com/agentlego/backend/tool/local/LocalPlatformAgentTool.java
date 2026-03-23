package com.agentlego.backend.tool.local;

import com.agentlego.backend.tool.schema.ToolOutputSchemaDescription;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

/**
 * 将平台 {@code ToolAggregate.definition}（模型描述、自定义 outputSchema 等）叠加到进程内 {@code @Tool} 的
 * {@link AgentTool} 上，使智能体运行时可见的 description 与控制台配置一致；调用仍委托给内置实现。
 */
public final class LocalPlatformAgentTool implements AgentTool {

    private final AgentTool delegate;
    private final Map<String, Object> definition;
    private final String description;

    public LocalPlatformAgentTool(AgentTool delegate, Map<String, Object> definition) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.definition = definition == null ? Map.of() : definition;
        this.description = buildDescription(delegate, this.definition);
    }

    private static String buildDescription(AgentTool delegate, Map<String, Object> def) {
        StringBuilder sb = new StringBuilder();
        Object d = def.get("description");
        if (d != null && !String.valueOf(d).isBlank()) {
            sb.append(String.valueOf(d).trim());
        } else {
            sb.append(delegate.getDescription() != null ? delegate.getDescription() : "");
        }
        ToolOutputSchemaDescription.appendToDescription(sb, def.get("outputSchema"));
        return sb.toString();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 若控制台配置了 {@code definition.parameters} / {@code inputSchema}，则优先作为模型可见入参说明；
     * 否则沿用内置实现的 schema。
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getParameters() {
        Object p = definition.get("parameters");
        if (p instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Object schema = definition.get("inputSchema");
        if (schema instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return delegate.getParameters();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return delegate.callAsync(param);
    }
}
