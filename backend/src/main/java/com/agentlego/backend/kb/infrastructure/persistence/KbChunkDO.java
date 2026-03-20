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
}

