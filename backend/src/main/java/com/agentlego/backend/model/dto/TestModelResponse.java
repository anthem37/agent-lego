package com.agentlego.backend.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型测试结果（聊天 / 嵌入）。
 * <p>
 * {@link #message} / {@link #raw} 保留兼容旧前端；新字段提供更细粒度信息。
 */
@Data
@NoArgsConstructor
public class TestModelResponse {

    /**
     * CHAT | EMBEDDING
     */
    private String testType;
    /**
     * OK | EMPTY | ERROR
     */
    private String status;
    /**
     * 端到端耗时（毫秒）
     */
    private Long latencyMs;
    /**
     * 聊天：采集到的流式 chunk 数量
     */
    private Integer streamChunks;
    /**
     * 实际送往上游的提示文本（可能截断展示）
     */
    private String promptUsed;
    /**
     * 聊天：本次 GenerateOptions.maxTokens
     */
    private Integer maxTokensUsed;
    /**
     * 主文案
     */
    private String message;
    /**
     * 原始/汇总文本（聊天多为模型回复拼接）
     */
    private String raw;
    /**
     * 嵌入：向量维度
     */
    private Integer embeddingDimension;
    /**
     * 嵌入：向量前若干维预览，如 "[0.12, -0.03, …]"
     */
    private String embeddingPreview;

    /**
     * 兼容旧构造：仅 message + raw。
     */
    public TestModelResponse(String message, String raw) {
        this.testType = "CHAT";
        this.status = "OK";
        this.message = message;
        this.raw = raw;
    }
}
