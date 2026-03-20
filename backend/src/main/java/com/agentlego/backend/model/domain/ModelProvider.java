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
     * 与 AgentScope Chat 模型及 {@code GenerateOptions} 对齐的可序列化 config 键。
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
     * 文本嵌入（embedding）模型配置。
     * <p>
     * 当前后端尚未把这些配置真正接入 embedding 运行时；但模型管理已允许你保存 embedding 模型配置，
     * 并校验 config key 白名单。
     */
    OPENAI_TEXT_EMBEDDING("OPENAI_TEXT_EMBEDDING", List.of(
            "dimensions",
            "encodingFormat",
            "endpointPath",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
    ), false),
    DASHSCOPE_TEXT_EMBEDDING("DASHSCOPE_TEXT_EMBEDDING", List.of(
            "dimensions",
            "encodingFormat",
            "endpointPath",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
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

