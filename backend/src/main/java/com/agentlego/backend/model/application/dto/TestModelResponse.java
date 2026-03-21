package com.agentlego.backend.model.application.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型测试结果（聊天 / 嵌入）。
 * <p>
 * 聊天测试成功时：{@link #message} 为简短状态摘要；{@link #raw} 为模型完整拼接文本。
 * 仍保留两字段以兼容只读其一的旧调用方。
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
     * 状态/摘要文案（错误说明、空结果提示，或聊天成功时的短摘要）
     */
    private String message;
    /**
     * 完整输出：聊天为模型回复全文；嵌入为小数预览字符串等
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
