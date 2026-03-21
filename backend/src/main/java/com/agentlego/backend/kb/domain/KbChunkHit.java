package com.agentlego.backend.kb.domain;

import lombok.Data;

/**
 * 检索命中的分片；similarity 为 pgvector 余弦导出的相似度（约等于 1 - cosine_distance）。
 */
@Data
public class KbChunkHit {
    private String id;
    private String documentId;
    private String content;
    private Double similarity;
}
