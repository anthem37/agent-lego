package com.agentlego.backend.kb.application.dto;

import lombok.Data;

@Data
public class KbRetrievePreviewRequest {
    private String query;
    private Integer topK;
    private Double scoreThreshold;
    /**
     * 为 true 时，对每条命中分片按文档绑定做与 RAG 一致的后处理（无会话工具出参）。
     */
    private Boolean renderSnippets;
}
