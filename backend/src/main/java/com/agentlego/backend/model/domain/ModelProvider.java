package com.agentlego.backend.model.domain;

import lombok.Getter;

import java.util.List;
import java.util.Locale;

/**
 * 模型提供方枚举。
 * <p>
 * 说明：
 * - 用于统一 provider 名称、以及前端可用的能力提示；
 * - 这里的能力描述是“平台侧约定”，不强绑定具体厂商全部参数，避免过度耦合。
 */
public enum ModelProvider {
    /**
     * 与聊天模型及生成参数（采样、流式、工具选择等）对齐的可序列化 config 键。
     */
    DASHSCOPE("DASHSCOPE", List.of(
            "temperature", "topP", "topK", "maxTokens", "seed",
            "frequencyPenalty", "presencePenalty",
            "thinkingBudget", "reasoningEffort",
            "stream",
            "executionConfig", "toolChoice",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
    )),
    OPENAI("OPENAI", List.of(
            "temperature", "topP", "maxTokens", "maxCompletionTokens", "seed",
            "frequencyPenalty", "presencePenalty",
            "thinkingBudget", "reasoningEffort",
            "stream",
            "executionConfig", "toolChoice",
            "endpointPath",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
    )),
    ANTHROPIC("ANTHROPIC", List.of(
            "temperature", "topP", "maxTokens", "maxCompletionTokens", "seed",
            "frequencyPenalty", "presencePenalty",
            "thinkingBudget", "reasoningEffort",
            "stream",
            "executionConfig", "toolChoice",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
    )),
    /**
     * 文本嵌入模型：与 OpenAI 兼容文本嵌入能力一致——仅
     * {@code dimensions}、执行超时/重试（config 的 {@code executionConfig}）、
     * 以及平台侧的 {@code baseUrl} / {@code apiKey}；无 GenerateOptions / toolChoice / 采样参数。
     * <p>
     * 说明：OpenAI 嵌入实现固定使用 float 向量，平台不再暴露 {@code encodingFormat}；自定义路径/附加头请通过网关 baseUrl 或 SDK 扩展解决。
     */
    OPENAI_TEXT_EMBEDDING("OPENAI_TEXT_EMBEDDING", List.of(
            "dimensions",
            "executionConfig"
    ), false),
    /**
     * 与通义文本嵌入能力一致：{@code dimensions} + {@code executionConfig} + {@code baseUrl}/{@code apiKey}。
     */
    DASHSCOPE_TEXT_EMBEDDING("DASHSCOPE_TEXT_EMBEDDING", List.of(
            "dimensions",
            "executionConfig"
    ), false);

    private final String code;
    private final List<String> supportedConfigKeys;
    /**
     * -- GETTER --
     * 是否为可做连通性测试的聊天模型（embedding 模型返回 false）。
     */
    @Getter
    private final boolean chatProvider;

    ModelProvider(String code, List<String> supportedConfigKeys) {
        this(code, supportedConfigKeys, !code.endsWith("_EMBEDDING"));
    }

    ModelProvider(String code, List<String> supportedConfigKeys, boolean chatProvider) {
        this.code = code;
        this.supportedConfigKeys = supportedConfigKeys;
        this.chatProvider = chatProvider;
    }

    public static ModelProvider from(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        for (ModelProvider p : values()) {
            if (p.code.equals(normalized)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unsupported provider: " + provider);
    }

    public String code() {
        return code;
    }

    public List<String> supportedConfigKeys() {
        return supportedConfigKeys;
    }
}

