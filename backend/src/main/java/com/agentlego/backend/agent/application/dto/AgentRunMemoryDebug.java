package com.agentlego.backend.agent.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 试运行时记忆侧可观测信息（与模型最终输出独立，便于联调闭环）。
 */
@Data
public class AgentRunMemoryDebug {
    private String memoryPolicyId;
    private String memoryPolicyName;
    private String ownerScope;
    private String retrievalMode;
    private String writeMode;
    /**
     * 与本次 run 请求一致；未传则为 null。
     */
    private String memoryNamespace;
    /**
     * ASSISTANT_SUMMARY 写回时实际采用的粗略摘要字符上限（策略 {@code rough_summary_max_chars} 解析后，与 {@link com.agentlego.backend.memorypolicy.support.MemoryRoughSummary#resolveMaxChars} 一致）。
     * 便于联调；与是否本次发生写回无关。
     */
    private Integer roughSummaryMaxCharsResolved;
    /**
     * 与策略 DTO 中 {@code implementationWarnings} 同源：未完全实现能力的说明。
     */
    private List<String> implementationWarnings;
    /**
     * 关键词预览命中列表的排序方式：非空查询为 {@code TRGM_WORD_SIMILARITY}，否则为 {@code RECENCY}。
     */
    private String keywordPreviewSort;
    private Integer previewHitCount;
    /**
     * 与当前用户输入、策略 topK 一致的关键词检索预览（截断）。
     */
    private String previewText;
}
