package com.agentlego.backend.memorypolicy.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class MemoryItemDO {
    private String id;
    private String policyId;
    private String content;
    private String metadataJson;
    private Instant createdAt;
    private Instant updatedAt;
}
