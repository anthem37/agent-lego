package com.agentlego.backend.model.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 模型 DTO。
 * <p>
 * 说明：用于 API 输出（不暴露 apiKeyCipher 等敏感字段）。
 */
@Data
public class ModelDto {
    /**
     * 模型 ID。
     */
    private String id;
    /**
     * 配置实例显示名称。
     */
    private String name;
    /**
     * 备注（可选）。
     */
    private String description;
    /**
     * 模型提供方（provider）。
     */
    private String provider;
    /**
     * provider-specific 的模型标识（model key）。
     */
    private String modelKey;
    /**
     * 默认推理参数（JSON object）。
     */
    private Map<String, Object> config;
    /**
     * 模型服务 base URL（可选）。
     */
    private String baseUrl;
    /**
     * 创建时间。
     */
    private Instant createdAt;

    /**
     * 是否已配置 API Key（不返回密钥内容，仅用于前端提示）。
     */
    private Boolean apiKeyConfigured;
}

