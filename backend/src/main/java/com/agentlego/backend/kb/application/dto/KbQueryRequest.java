package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 检索请求：须指定 {@link #baseId} 或 {@link #kbKey} 之一（智能体侧通常使用 kbKey）。
 */
@Data
public class KbQueryRequest {
    /**
     * 知识库主键
     */
    private String baseId;
    /**
     * 知识库绑定键（与 baseId 二选一）
     */
    private String kbKey;

    @NotBlank
    private String queryText;

    @Min(1)
    @Max(100)
    private int topK = 5;

    /**
     * 向量检索（embedding）模型 ID（platform_models.id）。
     * <p>
     * - 为空：回退使用旧的 `ilike` 关键词检索；
     * - 不为空：使用该 embedding 模型对 kb_chunks.embedding 进行向量相似度召回（必要时会懒加载补齐 embedding）。
     */
    private String embeddingModelId;
}
