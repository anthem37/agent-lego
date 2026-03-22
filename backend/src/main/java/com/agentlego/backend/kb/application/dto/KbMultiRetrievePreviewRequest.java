package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 与智能体绑定多集合 RAG 一致：多集合联合向量/全文检索与 RRF 融合（集合须使用相同 embedding 模型）。
 */
@Data
public class KbMultiRetrievePreviewRequest {
    /**
     * 至少 1 个、建议与 knowledge_base_policy.collectionIds 一致
     */
    private List<String> collectionIds;
    private String query;
    private Integer topK;
    private Double scoreThreshold;
    /**
     * 为 true 时，对每条命中分片按文档绑定做与 RAG 一致的后处理（无会话工具出参）。
     */
    private Boolean renderSnippets;
}
