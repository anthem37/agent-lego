package com.agentlego.backend.model.application.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 模型列表项 DTO（轻量字段，避免列表接口携带大块 config）。
 */
@Data
public class ModelSummaryDto {

    private String id;
    /**
     * 配置实例显示名称。
     */
    private String name;
    /**
     * 备注（可选，列表可截断展示）。
     */
    private String description;
    private String provider;
    private String modelKey;
    private String baseUrl;
    /**
     * 由 config 推导的短摘要，便于区分同模型多套配置。
     */
    private String configSummary;
    /**
     * 与 {@link com.agentlego.backend.model.domain.ModelProvider#isChatProvider()} 一致：
     * {@code false} 表示文本嵌入类配置，仅用于向量化等，不可绑定智能体推理。
     */
    private Boolean chatProvider;
    private Instant createdAt;
}
