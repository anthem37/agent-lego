package com.agentlego.backend.kb.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class KbChunkDO {
    private String id;
    private String documentId;
    private int chunkIndex;
    private String content;
    private String metadataJson;
    /**
     * 向量化字段：kb_chunks.embedding（jsonb，实际为一个数字数组的 JSON）。
     * <p>
     * 说明：当前采用 Java 侧 cosine 相似度；embeddingJson 会懒加载补齐。
     */
    private String embeddingJson;
    private Instant createdAt;
    /**
     * 检索结果联表填充：所属文档标题
     */
    private String documentName;
}

