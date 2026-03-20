package com.agentlego.backend.model.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

/**
 * 模型数据对象（Data Object, DO）。
 * <p>
 * 说明：
 * - 与数据库表字段一一对应；
 * - configJson 为 JSON 序列化结果（jsonb/varchar 存储由 SQL 决定）。
 */
@Data
public class ModelDO {
    /**
     * 主键 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 模型提供方（provider）。
     */
    private String provider;
    /**
     * provider-specific 的模型标识（model key）。
     */
    private String modelKey;
    /**
     * 配置实例显示名称（同一 provider+modelKey 可多份配置，靠此字段区分）。
     */
    private String name;
    /**
     * 备注说明（可选）。
     */
    private String description;
    /**
     * 默认推理参数 JSON（序列化后的字符串）。
     */
    private String configJson;
    /**
     * API Key（密钥字段，后续应存密文/引用）。
     */
    private String apiKeyCipher;
    /**
     * 模型服务 base URL（可选）。
     */
    private String baseUrl;
    /**
     * 创建时间（通常由 DB 默认值生成）。
     */
    private Instant createdAt;
}

