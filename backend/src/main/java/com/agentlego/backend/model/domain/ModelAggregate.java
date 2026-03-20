package com.agentlego.backend.model.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 模型聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - provider/modelKey 用于定位具体模型；
 * - apiKeyCipher 当前为“密钥字段占位命名”，现阶段仍可能存储明文（MVP），后续应接入加密/托管。
 */
@Data
public class ModelAggregate {
    /**
     * 模型 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 模型提供方（provider），例如 "DASHSCOPE"。
     */
    private String provider;
    /**
     * provider-specific 的模型标识（model key）。
     */
    private String modelKey;
    /**
     * 配置实例显示名称（人类可读，用于列表与智能体绑定）。
     */
    private String name;
    /**
     * 备注（可选）。
     */
    private String description;
    /**
     * API Key（密钥字段，后续应存密文/引用）。
     */
    private String apiKeyCipher;
    /**
     * 默认推理参数（provider-specific）。
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
}

