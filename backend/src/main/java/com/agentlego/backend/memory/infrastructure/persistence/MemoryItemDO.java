package com.agentlego.backend.memory.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class MemoryItemDO {
    private String id;
    private String ownerScope;
    private String content;
    private String metadataJson;
    private String embeddingJson;
    private Instant createdAt;
}

