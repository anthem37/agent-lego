package com.agentlego.backend.runtime;

import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.*;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Platform-level wrapper around AgentScope.
 * <p>
 * Note: AgentScope Agent instances are stateful; we build a new instance per request.
 */
@Component
public class AgentRuntime {

    public Mono<Msg> call(AgentDefinition agentDef, String userText, Toolkit toolkit) {
        Objects.requireNonNull(agentDef, "agentDef");
        Objects.requireNonNull(userText, "userText");

        ReActAgent agent = buildAgent(agentDef, toolkit);

        Msg userMsg = Msg.builder()
                .name("user")
                .textContent(userText)
                .build();

        return agent.call(userMsg);
    }

    public Flux<Event> stream(AgentDefinition agentDef, String userText, Toolkit toolkit) {
        Objects.requireNonNull(agentDef, "agentDef");
        Objects.requireNonNull(userText, "userText");

        ReActAgent agent = buildAgent(agentDef, toolkit);

        Msg userMsg = Msg.builder()
                .name("user")
                .textContent(userText)
                .build();

        return agent.stream(userMsg);
    }

    private ReActAgent buildAgent(AgentDefinition agentDef, Toolkit toolkit) {
        ModelDefinition modelDef = Objects.requireNonNull(agentDef.model(), "agentDef.model");

        Toolkit effectiveToolkit = (toolkit != null) ? toolkit : new Toolkit();
        InMemoryMemory memory = new InMemoryMemory();

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(agentDef.name())
                .sysPrompt(agentDef.systemPrompt())
                .model(toChatModel(modelDef))
                .toolkit(effectiveToolkit)
                .memory(memory);

        if (agentDef.maxIters() != null) {
            builder.maxIters(agentDef.maxIters());
        }

        return builder.build();
    }

    private Model toChatModel(ModelDefinition modelDef) {
        String provider = safeTrimUpper(modelDef.provider());
        String apiKey = resolveApiKey(provider, modelDef.apiKey(), modelDef.config());
        String baseUrl = safeTrim(modelDef.baseUrl());
        GenerateOptions defaultOptions = buildGenerateOptions(modelDef.config());

        if ("DASHSCOPE".equals(provider)) {
            if (apiKey.isBlank()) {
                throw new IllegalArgumentException("DASHSCOPE apiKey is required");
            }
            DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelDef.modelName())
                    .defaultOptions(defaultOptions);
            if (!baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            return builder.build();
        }

        if ("OPENAI".equals(provider)) {
            if (apiKey.isBlank()) {
                throw new IllegalArgumentException("OPENAI apiKey is required");
            }
            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelDef.modelName())
                    .generateOptions(defaultOptions);
            if (!baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            String endpointPath = getString(modelDef.config(), "endpointPath");
            if (!endpointPath.isBlank()) {
                builder.endpointPath(endpointPath);
            }
            return builder.build();
        }

        if ("ANTHROPIC".equals(provider)) {
            if (apiKey.isBlank()) {
                throw new IllegalArgumentException("ANTHROPIC apiKey is required");
            }
            AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelDef.modelName())
                    .defaultOptions(defaultOptions);
            if (!baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            return builder.build();
        }

        throw new IllegalArgumentException("Unsupported model provider: " + modelDef.provider());
    }

    private GenerateOptions buildGenerateOptions(Map<String, Object> config) {
        Map<String, Object> safe = config == null ? Map.of() : config;
        GenerateOptions.Builder builder = GenerateOptions.builder();

        Double temperature = getDouble(safe, "temperature");
        if (temperature != null) {
            builder.temperature(temperature);
        }
        Double topP = getDouble(safe, "topP");
        if (topP != null) {
            builder.topP(topP);
        }
        Integer topK = getInt(safe, "topK");
        if (topK != null) {
            builder.topK(topK);
        }
        Integer maxTokens = getInt(safe, "maxTokens");
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        Integer maxCompletionTokens = getInt(safe, "maxCompletionTokens");
        if (maxCompletionTokens != null) {
            builder.maxCompletionTokens(maxCompletionTokens);
        }
        Long seed = getLong(safe, "seed");
        if (seed != null) {
            builder.seed(seed);
        }

        Map<String, String> additionalHeaders = getStringMap(safe, "additionalHeaders");
        if (!additionalHeaders.isEmpty()) {
            builder.additionalHeaders(additionalHeaders);
        }
        Map<String, Object> additionalBodyParams = getObjectMap(safe, "additionalBodyParams");
        if (!additionalBodyParams.isEmpty()) {
            builder.additionalBodyParams(additionalBodyParams);
        }
        Map<String, String> additionalQueryParams = getStringMap(safe, "additionalQueryParams");
        if (!additionalQueryParams.isEmpty()) {
            builder.additionalQueryParams(additionalQueryParams);
        }

        return builder.build();
    }

    private String resolveApiKey(String provider, String explicitApiKey, Map<String, Object> config) {
        String key = safeTrim(explicitApiKey);
        if (!key.isBlank()) {
            return key;
        }
        String configApiKey = getString(config, "apiKey");
        if (!configApiKey.isBlank()) {
            return configApiKey;
        }
        if ("DASHSCOPE".equals(provider)) {
            return safeTrim(System.getenv("DASHSCOPE_API_KEY"));
        }
        if ("OPENAI".equals(provider)) {
            return safeTrim(System.getenv("OPENAI_API_KEY"));
        }
        if ("ANTHROPIC".equals(provider)) {
            return safeTrim(System.getenv("ANTHROPIC_API_KEY"));
        }
        return "";
    }

    private String safeTrimUpper(String value) {
        return safeTrim(value).toUpperCase();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, String> getStringMap(Map<String, Object> map, String key) {
        Map<String, Object> raw = getObjectMap(map, key);
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        raw.forEach((k, v) -> {
            if (k != null && v != null) {
                result.put(String.valueOf(k), String.valueOf(v));
            }
        });
        return result;
    }

    private Map<String, Object> getObjectMap(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return Map.of();
        }
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}

