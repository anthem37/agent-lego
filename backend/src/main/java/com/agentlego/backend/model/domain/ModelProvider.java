package com.agentlego.backend.model.domain;

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
    DASHSCOPE("DASHSCOPE", List.of(
            "temperature", "topP", "topK", "maxTokens", "seed",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
    )),
    OPENAI("OPENAI", List.of(
            "temperature", "topP", "maxTokens", "maxCompletionTokens", "seed",
            "endpointPath",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
    )),
    ANTHROPIC("ANTHROPIC", List.of(
            "temperature", "topP", "maxTokens", "seed",
            "additionalHeaders", "additionalBodyParams", "additionalQueryParams"
    ));

    private final String code;
    private final List<String> supportedConfigKeys;

    ModelProvider(String code, List<String> supportedConfigKeys) {
        this.code = code;
        this.supportedConfigKeys = supportedConfigKeys;
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

