package com.agentlego.backend.tool.http;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.schema.ToolOutputSchemaDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 {@link com.agentlego.backend.tool.domain.ToolType#HTTP} 工具注册为 AgentScope {@link AgentTool}。
 */
public final class HttpProxyAgentTool implements AgentTool {

    private static final int MAX_RESPONSE_CHARS = 256 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

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

    public HttpProxyAgentTool(ToolAggregate aggregate, ObjectMapper objectMapper) {
        Objects.requireNonNull(aggregate, "aggregate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
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

    private static boolean wantsEntity(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
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
        try {
            String resolvedUrl = spec.resolveUrl(input);
            SsrUrlGuard.validate(resolvedUrl);
            URI uri = URI.create(resolvedUrl);

            String method = spec.getMethod();
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT);

            for (Map.Entry<String, String> h : spec.getHeaders().entrySet()) {
                rb.header(h.getKey(), h.getValue());
            }

            byte[] bodyBytes = null;
            if (spec.isSendJsonBody() && wantsEntity(method)) {
                if (!spec.getHeaders().keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"))) {
                    rb.header("Content-Type", "application/json; charset=UTF-8");
                }
                try {
                    bodyBytes = objectMapper.writeValueAsBytes(input);
                } catch (JsonProcessingException e) {
                    return ToolResultBlock.error("Failed to serialize JSON body: " + e.getMessage());
                }
            }

            rb.method(method, bodyBytes == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(bodyBytes));

            HttpResponse<String> resp = HTTP_CLIENT.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body() == null ? "" : resp.body();
            if (body.length() > MAX_RESPONSE_CHARS) {
                body = body.substring(0, MAX_RESPONSE_CHARS) + "\n...[truncated]";
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("statusCode", resp.statusCode());
            meta.put("url", resolvedUrl);
            meta.put("method", method);
            List<String> contentType = resp.headers().map().getOrDefault("content-type", List.of());
            if (!contentType.isEmpty()) {
                meta.put("contentType", contentType.get(0));
            }
            return ToolResultBlock.of(
                    param.getToolUseBlock().getId(),
                    param.getToolUseBlock().getName(),
                    io.agentscope.core.message.TextBlock.builder().text(body).build(),
                    meta
            );
        } catch (ApiException e) {
            return ToolResultBlock.error(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResultBlock.error("HTTP request interrupted");
        } catch (Exception e) {
            return ToolResultBlock.error("HTTP request failed: " + e.getMessage());
        }
    }
}
