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
    private Instant createdAt;
    /** 检索结果联表填充：所属文档标题 */
    private String documentName;
}

