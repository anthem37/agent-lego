package com.agentlego.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建模型请求 DTO。
 * <p>
 * 说明：
 * - provider/modelKey 用于定位具体模型；
 * - config 用于承载 provider-specific 的默认推理参数（例如 temperature、max_tokens 等）。
 */
@Data
public class CreateModelRequest {

    /**
     * 模型提供方（provider），例如 "DASHSCOPE"。
     */
    @NotBlank
    private String provider;

    /**
     * provider 内部的模型标识（provider-specific model name）。
     */
    @NotBlank
    private String modelKey;

    /**
     * 调用模型的 API Key（可选）。
     * <p>
     * 注意：当前版本直接存储明文仅用于最小可用（MVP）；后续应替换为加密/密钥托管方案。
     */
    private String apiKey;

    /**
     * 模型服务的 base URL（可选）。
     * <p>
     * 例如 OpenAI-compatible 网关地址，便于对接私有化/代理网关。
     */
    private String baseUrl;

    /**
     * 默认推理参数（provider-specific），例如 temperature、top_p、max_tokens 等。
     */
    private Map<String, Object> config;
}

