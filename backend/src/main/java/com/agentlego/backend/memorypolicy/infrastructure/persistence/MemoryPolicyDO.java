package com.agentlego.backend.memorypolicy.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class MemoryPolicyDO {
    private String id;
    private String name;
    private String description;
    private String ownerScope;
    private String strategyKind;
    private String scopeKind;
    private String retrievalMode;
    private Integer topK;
    private String writeMode;
    private String writeBackOnDuplicate;
    private Instant createdAt;
    private Instant updatedAt;
}
