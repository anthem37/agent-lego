package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class KbCollectionDto {
    private String id;
    private String name;
    private String description;
    private String embeddingModelId;
    private int embeddingDims;
    private Instant createdAt;
    private Instant updatedAt;
}
