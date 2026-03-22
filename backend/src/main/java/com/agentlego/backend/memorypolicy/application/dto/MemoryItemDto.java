package com.agentlego.backend.memorypolicy.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class MemoryItemDto {
    private String id;
    private String policyId;
    private String content;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
