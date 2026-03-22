package com.agentlego.backend.memorypolicy.application.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class MemoryPolicyDto {
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
    /**
     * 绑定该策略的智能体数量（列表/详情聚合，非表内冗余列）。
     */
    private Integer referencingAgentCount;
    private Instant createdAt;
    private Instant updatedAt;
}
