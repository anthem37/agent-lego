package com.agentlego.backend.tool.http;

import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.schema.ToolOutputSchemaDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Objects;

/**
 * 将 {@link com.agentlego.backend.tool.domain.ToolType#HTTP} 工具注册为 AgentScope {@link AgentTool}。
 * <p>
 * 实际 HTTP 访问委托 {@link HttpToolRequestExecutor}（默认 {@link OkHttpHttpToolRequestExecutor}，基于 Square OkHttp），
 * 避免在业务代码中直接使用 JDK {@code HttpClient}。
 */
public final class HttpProxyAgentTool implements AgentTool {

    private static final Map<String, Object> DEFAULT_PARAMETERS = Map.of(
            "type", "object",
            "additionalProperties", true,
            "description", "Arbitrary JSON object; used for URL placeholders {key}, optional JSON body, etc."
    );

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;
    private final HttpToolSpec spec;
    private final ObjectMapper objectMapper;
    private final HttpToolRequestExecutor httpExecutor;

    public HttpProxyAgentTool(
            ToolAggregate aggregate,
            ObjectMapper objectMapper,
            HttpToolRequestExecutor httpExecutor
    ) {
        Objects.requireNonNull(aggregate, "aggregate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpExecutor = Objects.requireNonNull(httpExecutor, "httpExecutor");
        this.name = aggregate.getName();
        Map<String, Object> def = aggregate.getDefinition() == null ? Map.of() : aggregate.getDefinition();
        this.spec = HttpToolSpec.fromDefinition(def);
        this.description = describe(def);
        this.parameters = resolveParameters(def);
    }

    private static String describe(Map<String, Object> def) {
        StringBuilder sb = new StringBuilder();
        Object d = def.get("description");
        if (d != null && !String.valueOf(d).isBlank()) {
            sb.append(String.valueOf(d).trim());
        } else {
            Object url = def.get("url");
            sb.append("HTTP tool → ").append(url == null ? "(no url)" : String.valueOf(url));
        }
        ToolOutputSchemaDescription.appendToDescription(sb, def.get("outputSchema"));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveParameters(Map<String, Object> def) {
        Object p = def.get("parameters");
        if (p instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Object schema = def.get("inputSchema");
        if (schema instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return DEFAULT_PARAMETERS;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        return Mono.fromCallable(() -> invokeHttp(param, input))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock invokeHttp(ToolCallParam param, Map<String, Object> input) {
        HttpToolExecutionResult result = httpExecutor.execute(spec, input, objectMapper);
        return HttpToolResultMapper.toToolResultBlock(param, result);
    }
}
