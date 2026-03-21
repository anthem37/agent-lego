package com.agentlego.backend.kb.infrastructure.persistence;

import lombok.Data;

/**
 * 仅用于向量检索 SQL 映射；字段与 {@code searchByCosineSimilarity} 投影一致。
 */
@Data
public class KbChunkDO {
    private String id;
    private String documentId;
    private String content;
    private Double similarity;
}
